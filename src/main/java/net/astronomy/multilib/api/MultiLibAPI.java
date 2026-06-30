package net.astronomy.multilib.api;

import net.astronomy.multilib.api.block.BlockDefinitionBuilder;
import net.astronomy.multilib.api.definition.MultiblockBuilder;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.definition.WallSharingMode;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
}
