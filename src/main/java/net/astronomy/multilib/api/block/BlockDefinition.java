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
}
