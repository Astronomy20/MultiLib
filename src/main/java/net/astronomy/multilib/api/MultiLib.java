package net.astronomy.multilib.api;

import net.astronomy.multilib.api.block.BlockDefinitionBuilder;
import net.astronomy.multilib.api.definition.MultiblockBuilder;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.definition.WallSharingMode;
import net.astronomy.multilib.api.state.MultiblockState;
import net.astronomy.multilib.api.state.MultiblockStateRegistry;
import net.astronomy.multilib.core.registry.MultiblockAmbiguityResolver;
import net.astronomy.multilib.core.registry.MultiblockPreferenceTracker;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.core.registry.WrenchItemRegistry;
import net.astronomy.multilib.core.tracking.MultiblockProgressionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class MultiLib {

    private static final Map<Block, WallSharingMode> BLOCK_WALL_SHARING = new HashMap<>();

    public static MultiblockBuilder define(ResourceLocation id) {
        return new MultiblockBuilder().id(id);
    }

    /** Entry point for declaring block-level multiblock metadata (core, ioPort, dropOriginalOnBreak, wallSharing). */
    public static BlockDefinitionBuilder block(Block block) {
        return new BlockDefinitionBuilder(block);
    }

    /**
     * Registers {@code item} as a wrench: {@code WrenchInteractionHandler} will treat right-clicking
     * any multiblock's activation/core block with it exactly like an {@code IMultiblockWrench}
     * implementation would - a formation attempt, nothing else. No chat message or other feedback is
     * sent by the library; surfacing anything to the player is entirely up to the mod using this. For
     * data-driven/scripted items (e.g. KubeJS) that can't implement a custom Java interface - a
     * hand-written Item subclass should just implement {@link net.astronomy.multilib.api.tool.IMultiblockWrench} instead.
     */
    public static void registerWrenchItem(Item item) {
        WrenchItemRegistry.register(item);
    }

    public static Optional<MultiblockDefinition> getDefinition(ResourceLocation id) {
        return MultiblockRegistry.get(id);
    }

    /**
     * Patches an already-registered definition (Java, JSON, or previously KubeJS-defined) in place:
     * snapshots it into a {@link MultiblockBuilder} via {@link MultiblockDefinition#toBuilder()},
     * lets {@code mutator} adjust it with the same fluent methods used to declare one from scratch,
     * rebuilds it, and swaps it into the registry. Returns {@link Optional#empty()} without calling
     * {@code mutator} if no definition is registered under {@code id}.
     * <p>
     * Deliberately named differently from {@link #define(ResourceLocation)}: that one fails loudly on
     * a duplicate id (protects against accidental overwrites), this one exists specifically to
     * overwrite. If {@code mutator} renames the definition via {@code .id(...)}, only the <em>original</em>
     * {@code id} is removed - the rebuilt definition registers under its new id instead.
     */
    public static Optional<MultiblockDefinition> redefine(ResourceLocation id, Consumer<MultiblockBuilder> mutator) {
        Optional<MultiblockDefinition> existing = MultiblockRegistry.get(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        MultiblockBuilder builder = existing.get().toBuilder();
        mutator.accept(builder);
        MultiblockDefinition rebuilt = builder.buildWithoutRegistering();
        if (rebuilt == null) {
            // Validation failed (already logged by buildWithoutRegistering()) - leave the original
            // definition registered and untouched rather than replacing it with nothing.
            return Optional.empty();
        }
        MultiblockRegistry.replace(id, rebuilt);
        return Optional.of(rebuilt);
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

    /** Passthrough to {@link MultiblockStateRegistry} - the single point from which mod developers register custom states. */
    public static MultiblockState registerMultiblockState(ResourceLocation id) {
        return MultiblockStateRegistry.register(id);
    }

    /** Same as {@link #registerMultiblockState(ResourceLocation)}, with a display name (e.g. shown in the FTB Quests state picker). */
    public static MultiblockState registerMultiblockState(ResourceLocation id, String nameTranslationKey) {
        return MultiblockStateRegistry.register(id, nameTranslationKey);
    }

    /**
     * Binds {@code pos} - and every other position reachable from it through directly adjacent blocks
     * of that exact same type (see {@code MultiblockPreferenceTracker#setForConnectedRegion}) - to
     * {@code definitionId}, for every feature that resolves ambiguous multi-candidate blocks (ghost
     * overlay, auto-place - see {@code core.registry.MultiblockAmbiguityResolver}): when the block at
     * {@code pos} is a valid core/activation symbol for more than one registered definition,
     * {@code definitionId} wins there specifically, instead of whichever definition priority order
     * would otherwise pick. The propagation to same-type neighbors matters whenever that block type is
     * itself the bulk of a structure (e.g. a shapeless definition whose solid-fill body, or a shaped
     * definition whose walls, are all one material) - otherwise only the single clicked position would
     * be resolved, and every other identical block belonging to the same structure would stay
     * ambiguous and could still show a competing definition's ghost. Purely mechanism - MultiLib ships
     * no forced UI for this; a mod can call it directly from its own tool/GUI/command, or see
     * {@code core.preference} for the library's own optional dev-facing wrench built on top of the
     * exact same call.
     * <p>
     * Validates immediately rather than accepting anything: {@code definitionId} must actually be
     * among the candidates {@link MultiblockAmbiguityResolver} would consider for the block currently
     * at {@code pos} (either symbol - core or activation), or this is a no-op returning {@code false}.
     * A binding that becomes stale later (the block changes, the definition is removed) isn't cleaned
     * up eagerly - {@link MultiblockAmbiguityResolver#resolve} already falls back to priority order on
     * its own the next time it's consulted, exactly as if nothing had been set.
     *
     * @return {@code true} if the binding was accepted and stored, {@code false} if {@code definitionId}
     *         isn't presently a valid core/activation candidate for the block at {@code pos}
     */
    public static boolean setPreferredDefinition(ServerLevel level, BlockPos pos, ResourceLocation definitionId) {
        boolean valid = MultiblockAmbiguityResolver
                .candidatesAt(level, pos, (def, state) -> def.matchesActivationOrCore(state))
                .stream().anyMatch(def -> def.getId().equals(definitionId));
        if (!valid) return false;
        MultiblockPreferenceTracker.get(level).setForConnectedRegion(level, pos, definitionId);
        return true;
    }

    /** The definition id currently bound to {@code pos} via {@link #setPreferredDefinition}, if any - empty if never set or already cleared. */
    public static Optional<ResourceLocation> getPreferredDefinition(ServerLevel level, BlockPos pos) {
        return MultiblockPreferenceTracker.get(level).get(pos);
    }

    /** Removes any binding set via {@link #setPreferredDefinition} for {@code pos} - a no-op if none was set. */
    public static void clearPreferredDefinition(ServerLevel level, BlockPos pos) {
        MultiblockPreferenceTracker.get(level).clear(pos);
    }
}
