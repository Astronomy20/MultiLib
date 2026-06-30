package net.astronomy.multilib.api.block;

import net.astronomy.multilib.core.registry.BlockDefinitionRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Fluent builder for block-level multiblock metadata. Accessed via
 * {@code MultiLibAPI.block(Block)}. Unlike {@link net.astronomy.multilib.api.definition.MultiblockBuilder},
 * this declares properties of the Block itself rather than of a single multiblock structure.
 */
public final class BlockDefinitionBuilder {
    private final Block block;
    private final Set<ResourceLocation> coreOfMultiblocks = new HashSet<>();
    private Boolean wallSharingOverride = null;
    private boolean ioPort = false;
    private boolean dropOriginalOnBreak = false;
    private boolean mainFace = false;

    public BlockDefinitionBuilder(Block block) {
        this.block = block;
    }

    /**
     * Declares this block as the core of one or more multiblocks, by id. When the named
     * multiblock's own builder does not call {@code .core(char)}, the symbol mapped to this block
     * is auto-assigned as the core symbol. If the named multiblock's builder explicitly declares a
     * different block as core, registration of that multiblock fails (logged, game load continues).
     */
    public BlockDefinitionBuilder core(ResourceLocation... multiblockIds) {
        this.coreOfMultiblocks.addAll(Arrays.asList(multiblockIds));
        return this;
    }

    /**
     * Overrides wall sharing for this block when used as a core/activation symbol, which is
     * disabled by default. Has no effect on non-core usages, where the existing priority chain
     * (symbol override > block declaration > definition flag > default) already applies.
     */
    public BlockDefinitionBuilder wallSharing(boolean enabled) {
        this.wallSharingOverride = enabled;
        return this;
    }

    /**
     * Marks this block as an IO port: item/fluid/energy capability requests on it are
     * automatically forwarded to the core block entity of the multiblock it's currently part of.
     */
    public BlockDefinitionBuilder ioPort() {
        this.ioPort = true;
        return this;
    }

    /**
     * When the multiblock this block belongs to is dismantled, this block keeps its block entity
     * data and drops normally instead of being wiped to a clean copy of itself.
     */
    public BlockDefinitionBuilder dropOriginalOnBreak() {
        this.dropOriginalOnBreak = true;
        return this;
    }

    /**
     * Marks this block as having a meaningful placed facing of its own (e.g. a furnace-like block
     * with a {@code FACING}/{@code HORIZONTAL_FACING} property) — when used as a multiblock's core,
     * the ghost overlay/auto-place preview orientation is pinned to the block's actual in-world
     * facing instead of following the player's look direction.
     */
    public BlockDefinitionBuilder mainFace() {
        this.mainFace = true;
        return this;
    }

    public BlockDefinition build() {
        BlockDefinition definition = new BlockDefinition(
                block, coreOfMultiblocks, wallSharingOverride, ioPort, dropOriginalOnBreak, mainFace);
        BlockDefinitionRegistry.register(definition);
        return definition;
    }
}
