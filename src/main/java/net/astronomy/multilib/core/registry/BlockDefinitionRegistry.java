package net.astronomy.multilib.core.registry;

import net.astronomy.multilib.api.block.BlockDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BlockDefinitionRegistry {
    private static final Map<Block, BlockDefinition> DEFINITIONS = new HashMap<>();
    private static final List<Block> IO_PORT_BLOCKS = new ArrayList<>();

    private BlockDefinitionRegistry() {}

    /**
     * Merges with any existing declaration for the same block instead of replacing it outright (see
     * {@link BlockDefinition#mergedWith} for why a plain overwrite here silently broke multiblocks that
     * shared a core/activation block with another one declared independently - most commonly two Java
     * definitions in separate classes, or two datapack files, each unaware of the other).
     */
    public static void register(BlockDefinition definition) {
        BlockDefinition existing = DEFINITIONS.get(definition.getBlock());
        BlockDefinition merged = existing == null ? definition : existing.mergedWith(definition);
        DEFINITIONS.put(definition.getBlock(), merged);
        if (merged.isIoPort() && !IO_PORT_BLOCKS.contains(merged.getBlock())) {
            IO_PORT_BLOCKS.add(merged.getBlock());
        }
    }

    public static Optional<BlockDefinition> get(Block block) {
        return Optional.ofNullable(DEFINITIONS.get(block));
    }

    public static List<Block> getIoPortBlocks() {
        return Collections.unmodifiableList(IO_PORT_BLOCKS);
    }

    /** Returns the single multiblock id this block declares core-of among the given candidates, if any. */
    public static Optional<ResourceLocation> findDeclaredCoreFor(Block block, ResourceLocation multiblockId) {
        BlockDefinition def = DEFINITIONS.get(block);
        if (def != null && def.isCoreOf(multiblockId)) {
            return Optional.of(multiblockId);
        }
        return Optional.empty();
    }
}
