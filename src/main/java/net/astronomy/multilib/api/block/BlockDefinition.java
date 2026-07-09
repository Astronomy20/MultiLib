package net.astronomy.multilib.api.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.Optional;
import java.util.Set;

/**
 * Block-level metadata registered via {@link BlockDefinitionBuilder}, independent of any single
 * multiblock declaration. Lets a Block declare itself as the core of one or more multiblocks,
 * an IO port, and/or control its drop behavior when a multiblock is dismantled.
 */
public final class BlockDefinition {
    private final Block block;
    private final Set<ResourceLocation> coreOfMultiblocks;
    private final Boolean wallSharingOverride;
    private final boolean ioPort;
    private final boolean dropOriginalOnBreak;
    private final boolean mainFace;

    BlockDefinition(Block block, Set<ResourceLocation> coreOfMultiblocks, Boolean wallSharingOverride,
                    boolean ioPort, boolean dropOriginalOnBreak, boolean mainFace) {
        this.block = block;
        this.coreOfMultiblocks = Set.copyOf(coreOfMultiblocks);
        this.wallSharingOverride = wallSharingOverride;
        this.ioPort = ioPort;
        this.dropOriginalOnBreak = dropOriginalOnBreak;
        this.mainFace = mainFace;
    }

    public Block getBlock() { return block; }
    public Set<ResourceLocation> getCoreOfMultiblocks() { return coreOfMultiblocks; }
    public boolean isCoreOf(ResourceLocation multiblockId) { return coreOfMultiblocks.contains(multiblockId); }
    public boolean declaresCore() { return !coreOfMultiblocks.isEmpty(); }
    public Optional<Boolean> getWallSharingOverride() { return Optional.ofNullable(wallSharingOverride); }
    public boolean isIoPort() { return ioPort; }
    public boolean isDropOriginalOnBreak() { return dropOriginalOnBreak; }

    /**
     * Whether this block, when used as a multiblock's core, has its own meaningful placed facing
     * (e.g. a {@code FACING}/{@code HORIZONTAL_FACING} block-state property) that should pin the
     * ghost overlay/auto-place orientation to the block's actual placement instead of the player's
     * look direction. See {@link BlockDefinitionBuilder#mainFace()}.
     */
    public boolean hasMainFace() { return mainFace; }

    /**
     * Combines this declaration with another one for the same block, unioning {@code coreOfMultiblocks}
     * and OR-ing the boolean flags, instead of one call silently discarding the other. Needed because two
     * independent multiblocks (most commonly two Java definitions in unrelated classes, or two separate
     * datapack files, unaware of each other) can each declare the same shared block as their own core via
     * separate {@code MultiLib.block(sameBlock).core(id).build()} calls - before this merge existed,
     * {@code BlockDefinitionRegistry.register} used a plain {@code Map.put}, so the second declaration
     * replaced the first outright: the first multiblock's {@code coreOfMultiblocks} entry (and its
     * ioPort/dropOriginalOnBreak/mainFace flags) vanished, and if that multiblock relied on this
     * declaration to auto-assign its core symbol (see {@code MultiblockBuilder#resolveAndValidateCore}),
     * it failed core resolution and was silently never registered at all - never appearing in JEI/REI/EMI,
     * with no error visible to whoever owns the block declaration that "won".
     */
    public BlockDefinition mergedWith(BlockDefinition other) {
        Set<ResourceLocation> combinedCore = new java.util.HashSet<>(this.coreOfMultiblocks);
        combinedCore.addAll(other.coreOfMultiblocks);
        Boolean combinedWallSharing = other.wallSharingOverride != null ? other.wallSharingOverride : this.wallSharingOverride;
        return new BlockDefinition(
                this.block, combinedCore, combinedWallSharing,
                this.ioPort || other.ioPort,
                this.dropOriginalOnBreak || other.dropOriginalOnBreak,
                this.mainFace || other.mainFace);
    }
}
