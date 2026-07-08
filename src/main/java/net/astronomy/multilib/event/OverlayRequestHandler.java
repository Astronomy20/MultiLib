package net.astronomy.multilib.event;

import net.astronomy.multilib.CommonConfig;
import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.event.MultiblockFormedEvent;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.core.matching.MatchResult;
import net.astronomy.multilib.core.matching.PatternMatcher;
import net.astronomy.multilib.core.matching.ShapedMatcher;
import net.astronomy.multilib.core.matching.StructureOrientation;
import net.astronomy.multilib.core.registry.MultiblockAmbiguityResolver;
import net.astronomy.multilib.network.GhostBlockData;
import net.astronomy.multilib.network.OverlayDataPacket;
import net.astronomy.multilib.network.RequestOverlayPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = MultiLib.MODID)
public class OverlayRequestHandler {

    private static final Map<UUID, PlayerOverlayState> PLAYER_STATES = new ConcurrentHashMap<>();

    // The overlay is refreshed live (block place/break while it's open updates colors immediately)
    // instead of only when the player clicks the trigger block again.
    private static final int REFRESH_INTERVAL_TICKS = 10;
    private static int tickCounter = 0;

    /** Sentinel tick count for a "never expires" overlay (see {@link #resolveDurationTicks}) - see that method's javadoc for why this isn't simply {@link Long#MAX_VALUE}. */
    private static final long PERSISTENT_DURATION_TICKS = Long.MAX_VALUE / 2;

    /**
     * @param activationGameTime the level's game time (in ticks, see {@link ServerLevel#getGameTime()})
     *                           when this overlay session was first opened; never updated on
     *                           layer-cycle clicks, only on fresh activation (different core or no
     *                           prior state). Used to auto-disable after the configured duration,
     *                           independently of the live-refresh loop that would otherwise reset the
     *                           client-side timeout on every packet. Game time is used instead of
     *                           wall-clock time so that pausing the game (integrated server) freezes
     *                           the countdown instead of letting it keep expiring in the background.
     */
    private record PlayerOverlayState(BlockPos corePos, int currentMode, String axis, int rotation,
                                      char anchorSymbol, long activationGameTime) {}

    /**
     * The (axis, rotation) a player currently has previewed via the ghost overlay on the given core,
     * if any - used by auto-place to place blocks in the same orientation the player is looking at
     * instead of silently assuming the default upright placement.
     */
    static StructureOrientation.Orientation getActiveOrientation(UUID playerId, BlockPos corePos) {
        PlayerOverlayState state = PLAYER_STATES.get(playerId);
        if (state == null || !state.corePos().equals(corePos)) return null;
        return new StructureOrientation.Orientation(state.axis(), state.rotation());
    }

    public static void handleRequest(RequestOverlayPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            BlockPos corePos = packet.corePos();

            if (packet.mode() == -1) {
                PLAYER_STATES.remove(player.getUUID());
                PacketDistributor.sendToPlayer(player, new OverlayDataPacket(List.of(), 0, -1, false, 0));
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

            // Re-clicking the same core only cycles through layers/full view - the orientation chosen
            // on the activating click must be kept fixed for as long as the overlay stays open on
            // that core (regardless of which face is clicked to cycle); only a fresh activation
            // (different core, or no overlay active yet) re-derives it.
            String axis;
            int rotation;
            char anchorSymbol;
            if (current != null && current.corePos().equals(corePos)) {
                axis = current.axis();
                rotation = current.rotation();
                anchorSymbol = current.anchorSymbol();
            } else {
                BlockState clickedState = level.getBlockState(corePos);
                anchorSymbol = definition != null ? resolveAnchorSymbol(definition, clickedState) : '\0';

                // Ground truth from whatever's already physically placed around the clicked block beats
                // guessing from player facing/mainFace whenever there's anything to detect from - a
                // partially built structure should preview aligned to the blocks that already exist,
                // not spawn a ghost offset from them just because the player happened to be facing a
                // different way. Tried for every fresh activation now (core or activation symbol alike -
                // detectFromPlacedBlocks is generic over which symbol anchors it, see its own javadoc),
                // not just genuine activation-symbol clicks as before. Only falls through to the
                // face/mainFace-based guess below when nothing's placed yet to detect from (e.g. only the
                // core/activation block itself exists so far).
                StructureOrientation.Orientation detected = definition != null
                        ? StructureOrientation.detectFromPlacedBlocks(level, corePos, definition, anchorSymbol).orElse(null)
                        : null;

                if (detected != null) {
                    axis = detected.axis();
                    rotation = detected.rotation();
                } else {
                    // A core block declaring .mainFace() (BlockDefinitionBuilder) has a meaningful placed
                    // facing of its own (e.g. a furnace-like FACING property) - the preview must stay
                    // pinned to that, ignoring the player's look direction entirely. Otherwise, the face
                    // supplied by the client already encodes the player's facing (see
                    // GhostOverlayInputHandler), so the existing face-based derivation still applies.
                    Direction mainFace = net.astronomy.multilib.core.registry.BlockDefinitionRegistry
                            .get(clickedState.getBlock())
                            .filter(net.astronomy.multilib.api.block.BlockDefinition::hasMainFace)
                            .map(bd -> extractMainFace(clickedState))
                            .orElse(null);
                    Direction effectiveFace = mainFace != null
                            ? mainFace
                            : (packet.faceOrdinal() >= 0 && packet.faceOrdinal() < Direction.values().length
                                    ? Direction.values()[packet.faceOrdinal()] : null);
                    if (effectiveFace != null) {
                        StructureOrientation.Orientation o = StructureOrientation.orientationForFace(definition, effectiveFace);
                        axis = o.axis();
                        rotation = o.rotation();
                    } else {
                        axis = "Y";
                        rotation = 0;
                    }
                }
            }

            // Preserve the original activation time when cycling layers on the same core - the
            // duration timeout must count from when the overlay was first opened, not from the most
            // recent layer-cycle click (which would keep resetting it indefinitely).
            long activationGameTime = (current != null && current.corePos().equals(corePos))
                    ? current.activationGameTime()
                    : level.getGameTime();
            PLAYER_STATES.put(player.getUUID(), new PlayerOverlayState(corePos, nextMode, axis, rotation, anchorSymbol, activationGameTime));
            sendOverlayUpdate(player, level, corePos, definition, nextMode, totalLayers, axis, rotation, anchorSymbol, activationGameTime);
        });
    }

    /**
     * Immediately disables any active ghost overlay anchored on the core that just formed - e.g. the
     * structure was completed by placing its last block while a player had the overlay open on it.
     * Without this, the overlay would just keep showing "0 mismatches" (an empty but still-active
     * preview) until the next {@link #REFRESH_INTERVAL_TICKS} periodic refresh or the player manually
     * closes it, instead of disappearing the instant the structure is actually done.
     */
    @SubscribeEvent
    public static void onMultiblockFormed(MultiblockFormedEvent event) {
        event.getInstance().getCorePos().ifPresent(corePos -> {
            ServerLevel level = event.getLevel();
            for (Map.Entry<UUID, PlayerOverlayState> entry : new ArrayList<>(PLAYER_STATES.entrySet())) {
                if (!entry.getValue().corePos().equals(corePos)) continue;
                PLAYER_STATES.remove(entry.getKey());
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, new OverlayDataPacket(List.of(), 0, -1, false, 0));
                }
            }
        });
    }

    /**
     * Re-sends ghost data for every player with an active overlay every {@link #REFRESH_INTERVAL_TICKS}
     * ticks, so placing/breaking a block updates the colors live instead of requiring another click
     * on the trigger block.
     */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        // This fires for the client's ClientLevel too (Level#getServer() is null there) - only the
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

            // Resolved before the expiry check below (rather than after, as before) since the
            // per-definition duration override (see resolveDurationTicks) needs the definition to know
            // whether this specific structure opted out of the config's default duration.
            MultiblockDefinition tickDefinition = findDefinitionAt(level, corePos);

            // Session duration expired - stop refreshing so the server stops sending packets and the
            // client-side timeout can fire. This is the primary mechanism: the client's own fallback
            // timeout (GhostOverlayState.isActive) would also fire eventually, but only after the
            // server has already stopped sending updates (which reset the client timer each time).
            // Measured in game ticks (not wall-clock time) so pausing the integrated server (single
            // player) freezes the countdown instead of letting it expire while the game is paused.
            long durationTicks = resolveDurationTicks(tickDefinition);
            if (level.getGameTime() - entry.getValue().activationGameTime() > durationTicks) {
                PLAYER_STATES.remove(entry.getKey());
                PacketDistributor.sendToPlayer(player, new OverlayDataPacket(List.of(), 0, -1, false, 0));
                continue;
            }

            // The block that triggered the overlay is gone (broken) - disable instead of refreshing
            // against an empty position.
            if (level.getBlockState(corePos).isAir()) {
                PLAYER_STATES.remove(entry.getKey());
                PacketDistributor.sendToPlayer(player, new OverlayDataPacket(List.of(), 0, -1, false, 0));
                continue;
            }

            int totalLayers = tickDefinition != null ? tickDefinition.getLayerCount() : 1;
            sendOverlayUpdate(player, level, corePos, tickDefinition, entry.getValue().currentMode(), totalLayers,
                    entry.getValue().axis(), entry.getValue().rotation(), entry.getValue().anchorSymbol(),
                    entry.getValue().activationGameTime());
        }
    }

    /**
     * The duration (in ticks) an overlay session for {@code definition} stays active before
     * {@link #onLevelTick} auto-disables it. Definitions never calling
     * {@code MultiblockBuilder#ghostOverlayDuration(int)}/{@code #ghostOverlayPersistent()} (the vast
     * majority) fall through to {@link CommonConfig#GHOST_OVERLAY_DURATION_SECONDS} exactly as before -
     * this override only ever narrows behavior for definitions that explicitly opt in.
     * <p>
     * "Persistent" (from {@code ghostOverlayPersistent()}, stored as -1 seconds) resolves to a very
     * large but finite tick count rather than {@link Long#MAX_VALUE} - the expiry check subtracts
     * {@code activationGameTime} from the current game time, and keeping generous headroom (still
     * millions of years of ticks) avoids any risk of overflow in that subtraction on a very
     * long-running world, unlike a value already sitting at the type's own maximum.
     */
    private static long resolveDurationTicks(MultiblockDefinition definition) {
        if (definition != null) {
            java.util.OptionalInt custom = definition.getGhostOverlayDurationSeconds();
            if (custom.isPresent()) {
                int seconds = custom.getAsInt();
                return seconds < 0 ? PERSISTENT_DURATION_TICKS : seconds * 20L;
            }
        }
        return CommonConfig.GHOST_OVERLAY_DURATION_SECONDS.get() * 20L;
    }

    /**
     * Immediately drops a disconnecting player's overlay state - otherwise the entry lingers in
     * {@link #PLAYER_STATES} until the periodic {@link #onLevelTick} check notices the configured
     * {@link CommonConfig#GHOST_OVERLAY_DURATION_SECONDS} has elapsed, which is bounded but avoidable.
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_STATES.remove(event.getEntity().getUUID());
    }

    /**
     * Reads the core block's own placed facing, for cores declared via
     * {@link net.astronomy.multilib.api.block.BlockDefinitionBuilder#mainFace()}. Tries the common
     * horizontal-only property first (furnaces, chests, etc.), then the full 6-way one (droppers,
     * dispensers, etc.), projecting a vertical facing onto the nearest horizontal direction since the
     * overlay's orientation system only has horizontal yaw rotations. Returns null if the block has
     * neither property (declaring .mainFace() on such a block is a no-op, falling back to player facing).
     */
    private static Direction extractMainFace(BlockState state) {
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        }
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
            Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
            return facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : facing;
        }
        return null;
    }

    private static void sendOverlayUpdate(ServerPlayer player, ServerLevel level, BlockPos corePos,
                                           MultiblockDefinition definition, int mode, int totalLayers,
                                           String axis, int rotation, char anchorSymbol, long activationGameTime) {
        List<GhostBlockData> ghostData = calculateGhostBlocks(level, corePos, definition, mode, axis, rotation, anchorSymbol);
        if (ghostData == null) {
            // null (as opposed to an empty list) signals the structure is already fully formed -
            // disable outright instead of leaving an "active but empty" overlay. This is a defensive
            // fallback alongside onMultiblockFormed's immediate disable, for cases that path doesn't
            // cover (e.g. the overlay is (re)activated by clicking a core that's already formed).
            PLAYER_STATES.remove(player.getUUID());
            PacketDistributor.sendToPlayer(player, new OverlayDataPacket(List.of(), 0, -1, false, 0));
            return;
        }
        // No longer gated per-definition (the old ghostOverlayDebug() flag) - see MultiblockBuilder
        // #ghostOverlay's javadoc for why restricting this diagnostic to specific definitions never
        // added anything a dev actually wanted less of.
        boolean debugTiming = CommonConfig.DEV_MODE.get();
        // Ticks (not wall-clock) so the displayed countdown freezes along with the rest of the timer
        // while the integrated server is paused (see activationGameTime's javadoc above).
        long durationTicks = resolveDurationTicks(definition);
        long elapsedTicks = level.getGameTime() - activationGameTime;
        // -1 is the client's "never expires" sentinel (see GhostOverlayState's debug print) - skips
        // the ceil() math entirely for a persistent overlay rather than reporting some enormous but
        // technically-finite number of "remaining seconds" that would never mean anything to a dev.
        int remainingSeconds = durationTicks == PERSISTENT_DURATION_TICKS
                ? -1
                : (int) Math.max(0, Math.ceil((durationTicks - elapsedTicks) / 20.0));
        PacketDistributor.sendToPlayer(player, new OverlayDataPacket(ghostData, totalLayers, mode, debugTiming, remainingSeconds));
    }

    // Previewable from either the core or the activation block (see GhostOverlayInputHandler, which
    // gates the client-side trigger the same way) - a structure that splits the two symbols still
    // gets a preview no matter which one the player actually has in hand/placed first. Filtered on
    // isGhostOverlayEnabled() too, defensively: a client running an older/different build could still
    // send a request for a definition that opted out, and this must refuse it rather than preview
    // anyway. Ambiguity (2+ eligible candidates for this exact block) is resolved by
    // MultiblockAmbiguityResolver - priority order by default, or a MultiblockPreferenceTracker
    // override if one was set (see MultiLibAPI#setPreferredDefinition / core.preference).
    private static MultiblockDefinition findDefinitionAt(ServerLevel level, BlockPos pos) {
        return MultiblockAmbiguityResolver.resolve(level, pos,
                        (def, state) -> def.isGhostOverlayEnabled() && def.matchesActivationOrCore(state))
                .orElse(null);
    }

    /**
     * Which declared symbol {@code state} was actually clicked as - the core (the common case, and
     * always preferred when {@code state} matches both, e.g. a structure with no separate activation
     * symbol), or the activation symbol otherwise. {@code findDefinitionAt} already guarantees one of
     * the two matches before this is ever called.
     */
    private static char resolveAnchorSymbol(MultiblockDefinition def, BlockState state) {
        return def.matchesCore(state) ? def.getCoreSymbol() : def.getActivationSymbol();
    }

    /** @return ghost data for the requested view, or null if the structure is already fully formed. */
    private static List<GhostBlockData> calculateGhostBlocks(ServerLevel level, BlockPos corePos,
                                                               MultiblockDefinition definition, int mode,
                                                               String axis, int rotation, char anchorSymbol) {
        if (definition == null || definition.getLayers().isEmpty()) return List.of();

        MatchResult result = PatternMatcher.matches(level, corePos, definition);
        if (result instanceof MatchResult.Success) {
            return null;
        }

        List<GhostBlockData> ghostData = new ArrayList<>();
        List<List<String>> layers = definition.getLayers();
        Map<Character, BlockIngredient> blockMap = definition.getBlockMap();

        // findDefinitionAt/handleRequest already resolved which declared symbol was actually clicked
        // (core or activation - see resolveAnchorSymbol) - anchor on that cell specifically, since
        // findSymbolOrigin needs to know exactly where in the pattern grid corePos sits. The core
        // symbol is kept separately below purely for the "always highlight the core" rendering rule,
        // regardless of which symbol the preview was anchored on.
        char coreSymbol = definition.getCoreSymbol();
        BlockPos origin = StructureOrientation.findSymbolOrigin(corePos, layers, anchorSymbol, axis, rotation);

        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            // mode counts up from the bottom (mode=1 = bottommost, displayed "1/N") while layers[]
            // is declared top-to-bottom (index 0 = topmost) - convert here.
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
                    } else if (!ingredient.matches(level, checkPos, actual)) {
                        // Right block, wrong blockstate property (e.g. facing) vs. an entirely
                        // different block - see BlockIngredient#matchesBlockType.
                        GhostBlockData.Status status = ingredient.matchesBlockType(actual)
                                ? GhostBlockData.Status.WRONG_STATE
                                : GhostBlockData.Status.WRONG;
                        ghostData.add(new GhostBlockData(checkPos, expectedState, status));
                    } else if (alwaysHighlight) {
                        ghostData.add(new GhostBlockData(checkPos, expectedState, GhostBlockData.Status.CORE));
                    }
                }
            }
        }
        return ghostData;
    }

    private static BlockState getRepresentativeState(BlockIngredient ingredient) {
        return ingredient.getRenderState();
    }
}
