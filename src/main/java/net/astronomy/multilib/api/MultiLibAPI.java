package net.astronomy.multilib.api;

import net.astronomy.multilib.api.block.BlockDefinitionBuilder;
import net.astronomy.multilib.api.definition.MultiblockBuilder;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.definition.WallSharingMode;
import net.astronomy.multilib.api.state.MultiblockState;
import net.astronomy.multilib.api.state.MultiblockStateRegistry;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.core.tracking.MultiblockProgressionTracker;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MultiLibAPI {

    private static final Map<Block, WallSharingMode> BLOCK_WALL_SHARING = new HashMap<>();

    public static MultiblockBuilder define(ResourceLocation id) {
        return new MultiblockBuilder().id(id);
    }

    /** Entry point for declaring block-level multiblock metadata (core, ioPort, dropOriginalOnBreak, wallSharing). */
    public static BlockDefinitionBuilder block(Block block) {
        return new BlockDefinitionBuilder(block);
    }

    public static Optional<MultiblockDefinition> getDefinition(ResourceLocation id) {
        return MultiblockRegistry.get(id);
    }

    public static Collection<MultiblockDefinition> getAllDefinitions() {
        return MultiblockRegistry.getAll();
    }

    public static void setWallSharingMode(Block block, WallSharingMode mode) {
        BLOCK_WALL_SHARING.put(block, mode);
    }

    public static Optional<WallSharingMode> getRegisteredWallSharingMode(Block block) {
        return Optional.ofNullable(BLOCK_WALL_SHARING.get(block));
    }

    /**
     * True if {@code player} has ever driven an instance of {@code definitionId} to {@code stateId}.
     * If {@code stateId} is null, true if the player has ever formed the structure at least once
     * (equivalent to asking whether IDLE was reached).
     */
    public static boolean hasReachedMultiblockState(UUID player, MinecraftServer server, ResourceLocation definitionId,
                                                      @Nullable ResourceLocation stateId) {
        return MultiblockProgressionTracker.get(server.overworld())
                .hasReached(player, definitionId, stateId);
    }

    /**
     * Convenience overload for callers already holding a {@link ServerPlayer} (e.g. FTB Quests task
     * {@code canSubmit}) so they don't need to fish out a {@link MinecraftServer} themselves.
     */
    public static boolean hasReachedMultiblockState(ServerPlayer player, ResourceLocation definitionId,
                                                      @Nullable ResourceLocation stateId) {
        return hasReachedMultiblockState(player.getUUID(), player.serverLevel().getServer(), definitionId, stateId);
    }

    /**
     * Manually records that {@code player} has driven {@code definitionId} to {@code stateId}.
     * Normal use: automatic, called internally by MultiLib on formation and on every {@code setState()}.
     * Advanced use: a mod developer with a custom condition not representable by a simple
     * {@link MultiblockState} (e.g. "reactor stable for 5 minutes") calls this by hand from their own
     * controller block entity.
     */
    public static void recordMultiblockStateReached(ServerPlayer player, ResourceLocation definitionId,
                                                       ResourceLocation stateId) {
        MultiblockProgressionTracker.get(player.serverLevel().getServer().overworld())
                .recordStateReached(player.getUUID(), definitionId, stateId,
                        player.serverLevel().getServer().overworld().getGameTime());
    }

    /** Passthrough to {@link MultiblockStateRegistry} — the single point from which mod developers register custom states. */
    public static MultiblockState registerMultiblockState(ResourceLocation id) {
        return MultiblockStateRegistry.register(id);
    }

    /** Same as {@link #registerMultiblockState(ResourceLocation)}, with a display name (e.g. shown in the FTB Quests state picker). */
    public static MultiblockState registerMultiblockState(ResourceLocation id, String nameTranslationKey) {
        return MultiblockStateRegistry.register(id, nameTranslationKey);
    }
}
