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

    public static void register(BlockDefinition definition) {
        DEFINITIONS.put(definition.getBlock(), definition);
        if (definition.isIoPort()) {
            IO_PORT_BLOCKS.add(definition.getBlock());
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
