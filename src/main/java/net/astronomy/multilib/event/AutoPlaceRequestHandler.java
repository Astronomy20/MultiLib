package net.astronomy.multilib.event;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.core.matching.MatchResult;
import net.astronomy.multilib.core.matching.PatternMatcher;
import net.astronomy.multilib.core.matching.ShapedMatcher;
import net.astronomy.multilib.core.matching.StructureOrientation;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.network.RequestAutoPlacePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AutoPlaceRequestHandler {

    /** A single block still missing from the structure, awaiting placement. */
    record Candidate(BlockPos pos, BlockState state) {}

    /**
     * The client always sends the vanilla {@code ServerboundUseItemOnPacket} for a Ctrl+Right-click
     * too (cancelling the client-side event only skips client-side prediction, not the packet — see
     * {@code MultiPlayerGameMode#useItemOn}), so without this the server would place the held item's
     * block wherever the player is aiming, in addition to the auto-place system's own placement. Since
     * this packet is always sent to the server before that vanilla packet (both queued on the same
     * ordered connection, and this one is sent synchronously earlier in the client's event handler),
     * marking the core position here is guaranteed to be visible to
     * {@link AutoPlaceVanillaPlacementSuppressor} by the time the paired vanilla interact event fires.
     */
    private static final Map<UUID, BlockPos> PENDING_VANILLA_SUPPRESSION = new ConcurrentHashMap<>();

    static boolean consumeVanillaSuppression(UUID playerId, BlockPos pos) {
        BlockPos marked = PENDING_VANILLA_SUPPRESSION.remove(playerId);
        return marked != null && marked.equals(pos);
    }

    /**
     * Places exactly one missing block per request — one Ctrl+Right-click on the core places one
     * block, it does not queue up and finish the whole structure by itself. If the player is holding
     * an item, only a missing position that item can fill is considered, so holding something that
     * isn't part of the structure (an "external" item) places nothing.
     */
    public static void handleRequest(RequestAutoPlacePacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            ServerLevel level = player.serverLevel();
            BlockPos corePos = packet.corePos();

            // Always suppress the paired vanilla placement, even if nothing ends up being auto-placed
            // below (e.g. structure already complete) — the player Ctrl+clicked an auto-place core,
            // so the held item must never fall through to a normal placement on that click.
            PENDING_VANILLA_SUPPRESSION.put(player.getUUID(), corePos);

            MultiblockDefinition definition = findAutoPlaceDefinitionAt(level, corePos);
            if (definition == null) return;
            if (definition.getLayers().isEmpty()) return;

            List<Candidate> candidates = computeCandidates(player, level, corePos, definition);
            if (candidates == null || candidates.isEmpty()) return;

            ItemStack heldStack = player.getMainHandItem();
            Item heldItem = heldStack.isEmpty() ? null : heldStack.getItem();

            Candidate target = null;
            if (packet.hasOverlayTarget()) {
                // The overlay showed this exact position — place there rather than wherever the
                // player's current facing would recompute, so the block lands where the overlay was.
                for (Candidate candidate : candidates) {
                    if (!candidate.pos().equals(packet.overlayTargetPos())) continue;
                    if (heldItem != null && candidate.state().getBlock().asItem() != heldItem) continue;
                    target = candidate;
                    break;
                }
            } else {
                for (Candidate candidate : candidates) {
                    if (heldItem != null && candidate.state().getBlock().asItem() != heldItem) continue;
                    target = candidate;
                    break;
                }
            }
            if (target == null) return;

            if (!tryPlace(player, level, target.pos(), target.state())) return;

            if (PatternMatcher.matches(level, corePos, definition) instanceof MatchResult.Success) {
                BlockActivationHandler.triggerFormationAt(level, corePos);
            }

            // Jump the overlay straight to the next missing position (or clear it once the structure
            // is complete) instead of leaving the just-filled spot displayed until the client's next
            // hover poll.
            AutoPlacePreviewRequestHandler.sendPreviewUpdate(player, level, corePos);
        });
    }

    /**
     * Every missing/empty position of {@code definition}'s pattern that isn't yet filled, in the
     * structure's current orientation (reusing the player's active ghost-overlay orientation if any,
     * else falling back to their facing) — sorted bottom-to-top by layer, then within a layer from the
     * corner nearest the player's left hand to the one farthest on their right, so blocks that need
     * support from below (or an already-placed neighbor) never get placed before it exists.
     *
     * @return {@code null} if the structure is already fully formed.
     */
    static List<Candidate> computeCandidates(ServerPlayer player, ServerLevel level, BlockPos corePos,
                                              MultiblockDefinition definition) {
        if (PatternMatcher.matches(level, corePos, definition) instanceof MatchResult.Success) {
            return null;
        }

        List<List<String>> layers = definition.getLayers();
        Map<Character, BlockIngredient> blockMap = definition.getBlockMap();
        char coreSymbol = definition.getCoreSymbol();
        Set<Character> freeBlockSymbols = definition.getFreeBlocks().keySet();

        String axis;
        int rotation;
        // Physically-placed blocks are ground truth: if part of the structure is already built around
        // the core in some orientation, that's the orientation to keep placing into, regardless of
        // which way the player happens to be facing right now or which orientation a possibly-stale
        // remembered ghost-overlay session was showing. Only fall back to those guesses when nothing
        // beyond the core is actually placed yet in any valid orientation.
        Optional<StructureOrientation.Orientation> detected =
                StructureOrientation.detectFromPlacedBlocks(level, corePos, definition);
        if (detected.isPresent()) {
            axis = detected.get().axis();
            rotation = detected.get().rotation();
        } else {
            StructureOrientation.Orientation active =
                    OverlayRequestHandler.getActiveOrientation(player.getUUID(), corePos);
            if (active != null) {
                axis = active.axis();
                rotation = active.rotation();
            } else {
                StructureOrientation.Orientation fallback =
                        StructureOrientation.orientationForFace(definition, player.getDirection());
                axis = fallback.axis();
                rotation = fallback.rotation();
            }
        }

        BlockPos origin = StructureOrientation.findSymbolOrigin(corePos, layers, coreSymbol, axis, rotation);

        List<Candidate> candidates = new ArrayList<>();
        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            List<String> layer = layers.get(layerIdx);
            int height = layer.size();
            if (height == 0) continue;
            int width = layer.get(0).length();
            int centerX = width / 2;
            int centerZ = height / 2;
            // layers[0] = topmost declared layer, layers[last] = bottommost (ShapedMatcher's convention).
            int relY = (layers.size() - 1) - layerIdx;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < Math.min(width, line.length()); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;
                    if (freeBlockSymbols.contains(symbol)) continue;
                    BlockIngredient ingredient = blockMap.get(symbol);
                    if (ingredient == null) continue;

                    int relX = col - centerX;
                    int relZ = row - centerZ;
                    int[] t = ShapedMatcher.applyTransform(relX, relY, relZ, axis, rotation);
                    BlockPos targetPos = origin.offset(t[0], t[1], t[2]);

                    BlockState actual = level.getBlockState(targetPos);
                    if (!actual.isAir()) continue;

                    BlockState expectedState = getRepresentativeState(ingredient);
                    if (expectedState == null) continue;

                    candidates.add(new Candidate(targetPos, expectedState));
                }
            }
        }

        Direction forward = player.getDirection();
        Direction right = forward.getClockWise();
        candidates.sort(Comparator
                .comparingInt((Candidate c) -> c.pos().getY())
                .thenComparingInt(c -> dot(c.pos(), corePos, forward))
                .thenComparingInt(c -> dot(c.pos(), corePos, right)));

        return candidates;
    }

    /** Signed distance of {@code pos} from {@code reference} along {@code direction}. */
    private static int dot(BlockPos pos, BlockPos reference, Direction direction) {
        return (pos.getX() - reference.getX()) * direction.getStepX()
                + (pos.getZ() - reference.getZ()) * direction.getStepZ();
    }

    private static boolean tryPlace(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (player.isCreative()) {
            level.setBlockAndUpdate(pos, state);
            playPlaceSound(level, pos, state, player);
            return true;
        }

        Item item = state.getBlock().asItem();
        if (item == Items.AIR) return false;

        ItemStack found = null;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                found = stack;
                break;
            }
        }
        if (found == null) return false;

        found.shrink(1);
        level.setBlockAndUpdate(pos, state);
        playPlaceSound(level, pos, state, player);
        return true;
    }

    // Auto-place sets the block directly rather than going through the normal item-use pipeline, so
    // it never gets the vanilla placement sound for free — play it manually (passing null for the
    // player so it's audible to the placer too, matching how e.g. dispensers play placement sounds).
    private static void playPlaceSound(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        SoundType soundType = state.getSoundType(level, pos, player);
        level.playSound(null, pos, soundType.getPlaceSound(), SoundSource.BLOCKS,
                (soundType.getVolume() + 1f) / 2f, soundType.getPitch() * 0.8f);
    }

    // Only definitions opted into autoPlace() and whose core symbol matches the clicked block are
    // considered here, mirroring GhostOverlayInputHandler's trigger detection.
    static MultiblockDefinition findAutoPlaceDefinitionAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        for (MultiblockDefinition def : MultiblockRegistry.getCandidatesFor(state.getBlock())) {
            if (def.isAutoPlace() && def.matchesCore(state)) {
                return def;
            }
        }
        return null;
    }

    static BlockState getRepresentativeState(BlockIngredient ingredient) {
        Set<Block> candidates = ingredient.getCandidateBlocks();
        if (!candidates.isEmpty()) return candidates.iterator().next().defaultBlockState();
        return null;
    }
}
