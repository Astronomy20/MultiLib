package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.definition.AllowedRotation;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.definition.RotationAxis;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.core.matching.MatchResult;
import net.astronomy.multilib.core.matching.PatternMatcher;
import net.astronomy.multilib.core.matching.ShapedMatcher;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.network.GhostBlockData;
import net.astronomy.multilib.network.OverlayDataPacket;
import net.astronomy.multilib.network.RequestOverlayPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = MultiLib.MODID)
public class OverlayRequestHandler {

    private static final Map<UUID, PlayerOverlayState> PLAYER_STATES = new ConcurrentHashMap<>();

    // The overlay is refreshed live (block place/break while it's open updates colors immediately)
    // instead of only when the player clicks the trigger block again.
    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static int tickCounter = 0;

    private record PlayerOverlayState(BlockPos corePos, int currentMode, String axis, int rotation) {}

    public static void handleRequest(RequestOverlayPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockPos corePos = packet.corePos();

            if (packet.mode() == -1) {
                PLAYER_STATES.remove(player.getUUID());
                PacketDistributor.sendToPlayer(player, new OverlayDataPacket(List.of(), 0, -1, false));
                return;
            }

            MultiblockDefinition definition = findDefinitionAt(level, corePos);
            int totalLayers = definition != null ? definition.getLayerCount() : 1;
            PlayerOverlayState current = PLAYER_STATES.get(player.getUUID());
            int nextMode;

            if (current == null || !current.corePos().equals(corePos)) {
                nextMode = 0;
            } else if (current.currentMode() == 0) {
                nextMode = 1;
            } else if (current.currentMode() < totalLayers) {
                nextMode = current.currentMode() + 1;
            } else {
                nextMode = 0;
            }

            // Re-clicking the same core keeps previewing the orientation already chosen for it; a
            // fresh click (different core, or a face supplied this time) re-derives it from the face.
            String axis;
            int rotation;
            if (packet.faceOrdinal() >= 0 && packet.faceOrdinal() < Direction.values().length) {
                int[] ar = orientationForFace(definition, Direction.values()[packet.faceOrdinal()]);
                axis = AXIS_NAMES[ar[0]];
                rotation = ar[1];
            } else if (current != null && current.corePos().equals(corePos)) {
                axis = current.axis();
                rotation = current.rotation();
            } else {
                axis = "Y";
                rotation = 0;
            }

            PLAYER_STATES.put(player.getUUID(), new PlayerOverlayState(corePos, nextMode, axis, rotation));
            sendOverlayUpdate(player, level, corePos, definition, nextMode, totalLayers, axis, rotation);
        });
    }

    /**
     * Re-sends ghost data for every player with an active overlay every {@link #REFRESH_INTERVAL_TICKS}
     * ticks, so placing/breaking a block updates the colors live instead of requiring another click
     * on the trigger block.
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        // This fires for the client's ClientLevel too (Level#getServer() is null there) — only the
        // integrated/dedicated ServerLevel tick is relevant here.
        if (!(event.getLevel() instanceof ServerLevel)) return;
        if (PLAYER_STATES.isEmpty()) return;
        if (++tickCounter < REFRESH_INTERVAL_TICKS) return;
        tickCounter = 0;

        for (Map.Entry<UUID, PlayerOverlayState> entry : new ArrayList<>(PLAYER_STATES.entrySet())) {
            ServerPlayer player = ((ServerLevel) event.getLevel()).getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            ServerLevel level = player.serverLevel();
            BlockPos corePos = entry.getValue().corePos();

            // The block that triggered the overlay is gone (broken) — disable instead of refreshing
            // against an empty position.
            if (level.getBlockState(corePos).isAir()) {
                PLAYER_STATES.remove(entry.getKey());
                PacketDistributor.sendToPlayer(player, new OverlayDataPacket(List.of(), 0, -1, false));
                continue;
            }

            MultiblockDefinition definition = findDefinitionAt(level, corePos);
            int totalLayers = definition != null ? definition.getLayerCount() : 1;
            sendOverlayUpdate(player, level, corePos, definition, entry.getValue().currentMode(), totalLayers,
                    entry.getValue().axis(), entry.getValue().rotation());
        }
    }

    private static final String[] AXIS_NAMES = {"Y", "X", "X_FLIP", "Z", "Z_FLIP"};

    /**
     * Picks which (axis, rotation) the ghost preview should show for a shift-click on the given face
     * of the core. The four horizontal faces always cycle the four Y-axis (yaw) rotations — that
     * works for every definition, rotated or not, since axis=Y rotation=0 is always the baseline.
     * UP keeps the default upright placement. DOWN previews the structure flipped onto its head, but
     * only if the definition actually declared an X/Z-axis rotation via {@code .allowRotation(...)} —
     * otherwise DOWN falls back to the same default as UP, since flipping isn't a valid placement.
     *
     * @return {axisIndex into AXIS_NAMES, rotationStep 0-3}
     */
    private static int[] orientationForFace(MultiblockDefinition definition, Direction face) {
        return switch (face) {
            case SOUTH -> new int[]{0, 0};
            case WEST -> new int[]{0, 1};
            case NORTH -> new int[]{0, 2};
            case EAST -> new int[]{0, 3};
            case DOWN -> {
                if (definition != null) {
                    for (AllowedRotation ar : definition.getAllowedRotations()) {
                        if (ar.axis() == RotationAxis.X) yield new int[]{2, 0}; // X_FLIP
                        if (ar.axis() == RotationAxis.Z) yield new int[]{4, 0}; // Z_FLIP
                    }
                }
                yield new int[]{0, 0};
            }
            default -> new int[]{0, 0}; // UP, or anything else: default upright
        };
    }

    private static void sendOverlayUpdate(ServerPlayer player, ServerLevel level, BlockPos corePos,
                                           MultiblockDefinition definition, int mode, int totalLayers,
                                           String axis, int rotation) {
        List<GhostBlockData> ghostData = calculateGhostBlocks(level, corePos, definition, mode, axis, rotation);
        boolean debugTiming = definition != null && definition.isGhostOverlayDebug();
        PacketDistributor.sendToPlayer(player, new OverlayDataPacket(ghostData, totalLayers, mode, debugTiming));
    }

    // The ghost overlay is only previewable from the core block (see GhostOverlayInputHandler), so
    // only definitions whose core symbol actually matches the clicked block are considered here.
    private static MultiblockDefinition findDefinitionAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        for (MultiblockDefinition def : MultiblockRegistry.getCandidatesFor(state.getBlock())) {
            if (def.matchesCore(state)) {
                return def;
            }
        }
        return null;
    }

    private static List<GhostBlockData> calculateGhostBlocks(ServerLevel level, BlockPos corePos,
                                                               MultiblockDefinition definition, int mode,
                                                               String axis, int rotation) {
        if (definition == null || definition.getLayers().isEmpty()) return List.of();

        MatchResult result = PatternMatcher.matches(level, corePos, definition);
        if (result instanceof MatchResult.Success) {
            return List.of();
        }

        List<GhostBlockData> ghostData = new ArrayList<>();
        List<List<String>> layers = definition.getLayers();
        Map<Character, BlockIngredient> blockMap = definition.getBlockMap();

        // The overlay only ever triggers from the core block (findDefinitionAt already verified
        // that), so anchor directly on the core symbol's cell.
        char coreSymbol = definition.getCoreSymbol();
        BlockPos origin = findSymbolOrigin(corePos, layers, coreSymbol, axis, rotation);

        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            // mode counts up from the bottom (mode=1 = bottommost, displayed "1/N") while layers[]
            // is declared top-to-bottom (index 0 = topmost) — convert here.
            if (mode > 0 && mode <= layers.size() && layerIdx != (layers.size() - mode)) continue;

            List<String> layer = layers.get(layerIdx);
            int height = layer.size();
            if (height == 0) continue;
            int width = layer.get(0).length();
            int centerX = width / 2;
            int centerZ = height / 2;
            // layers[0] = topmost declared layer, layers[last] = bottommost (see findSymbolOrigin);
            // same relY convention ShapedMatcher itself uses, so applyTransform stays self-consistent.
            int relY = (layers.size() - 1) - layerIdx;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < Math.min(width, line.length()); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;
                    BlockIngredient ingredient = blockMap.get(symbol);
                    if (ingredient == null) continue;

                    int relX = col - centerX;
                    int relZ = row - centerZ;
                    int[] t = ShapedMatcher.applyTransform(relX, relY, relZ, axis, rotation);
                    BlockPos checkPos = origin.offset(t[0], t[1], t[2]);

                    BlockState expectedState = getRepresentativeState(ingredient);
                    if (expectedState == null) continue;

                    // Always highlight the core, even when already correct, so its position stands
                    // out at a glance.
                    boolean alwaysHighlight = symbol == coreSymbol;
                    BlockState actual = level.getBlockState(checkPos);
                    if (actual.isAir()) {
                        ghostData.add(new GhostBlockData(checkPos, expectedState, GhostBlockData.Status.MISSING));
                    } else if (!ingredient.matches(actual)) {
                        ghostData.add(new GhostBlockData(checkPos, expectedState, GhostBlockData.Status.WRONG));
                    } else if (alwaysHighlight) {
                        ghostData.add(new GhostBlockData(checkPos, expectedState, GhostBlockData.Status.CORE));
                    }
                }
            }
        }
        return ghostData;
    }

    /**
     * Mirrors how ShapedMatcher derives a match's origin from a symbol's own cell, instead of the
     * layer's geometric center, so the ghost preview lines up with the block that was clicked,
     * wherever in the pattern it sits — for whichever (axis, rotation) is currently being previewed.
     */
    private static BlockPos findSymbolOrigin(BlockPos clickedPos, List<List<String>> layers, char symbol,
                                             String axis, int rotation) {
        int layersCount = layers.size();
        for (int layerIdx = 0; layerIdx < layersCount; layerIdx++) {
            List<String> layer = layers.get(layerIdx);
            int height = layer.size();
            if (height == 0) continue;
            int width = layer.get(0).length();
            int centerX = width / 2;
            int centerZ = height / 2;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < Math.min(width, line.length()); col++) {
                    if (line.charAt(col) != symbol) continue;
                    int relX = col - centerX;
                    // layers[0] is the topmost declared layer (same convention as MultiblockBuilder.layers()
                    // call order), layers[last] the bottommost — independent of ShapedMatcher's own internal
                    // matching coordinates, which don't need this visual ordering to be correct.
                    int relY = (layersCount - 1) - layerIdx;
                    int relZ = row - centerZ;
                    int[] t = ShapedMatcher.applyTransform(relX, relY, relZ, axis, rotation);
                    return clickedPos.offset(-t[0], -t[1], -t[2]);
                }
            }
        }
        // Symbol not found in the pattern — fall back to treating clickedPos as the origin.
        return clickedPos;
    }

    private static BlockState getRepresentativeState(BlockIngredient ingredient) {
        Set<Block> candidates = ingredient.getCandidateBlocks();
        if (!candidates.isEmpty()) return candidates.iterator().next().defaultBlockState();
        return null;
    }
}
