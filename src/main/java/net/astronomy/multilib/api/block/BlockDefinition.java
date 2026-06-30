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

    BlockDefinition(Block block, Set<ResourceLocation> coreOfMultiblocks, Boolean wallSharingOverride,
                    boolean ioPort, boolean dropOriginalOnBreak) {
        this.block = block;
        this.coreOfMultiblocks = Set.copyOf(coreOfMultiblocks);
        this.wallSharingOverride = wallSharingOverride;
        this.ioPort = ioPort;
        this.dropOriginalOnBreak = dropOriginalOnBreak;
    }

    public Block getBlock() { return block; }
    public Set<ResourceLocation> getCoreOfMultiblocks() { return coreOfMultiblocks; }
    public boolean isCoreOf(ResourceLocation multiblockId) { return coreOfMultiblocks.contains(multiblockId); }
    public boolean declaresCore() { return !coreOfMultiblocks.isEmpty(); }
    public Optional<Boolean> getWallSharingOverride() { return Optional.ofNullable(wallSharingOverride); }
    public boolean isIoPort() { return ioPort; }
    public boolean isDropOriginalOnBreak() { return dropOriginalOnBreak; }
}
