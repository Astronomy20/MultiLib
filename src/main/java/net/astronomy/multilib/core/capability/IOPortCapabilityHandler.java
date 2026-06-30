package net.astronomy.multilib.core.capability;

import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.core.registry.BlockDefinitionRegistry;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Makes blocks declared via {@code MultiLibAPI.block(block).ioPort()} forward item/fluid/energy
 * capability requests directly to the core block entity of the multiblock they're currently part
 * of, so the modder never has to write the redirect plumbing by hand.
 */
public final class IOPortCapabilityHandler {

    private IOPortCapabilityHandler() {}

    public static void register(RegisterCapabilitiesEvent event) {
        List<Block> ioPortBlocks = BlockDefinitionRegistry.getIoPortBlocks();
        if (ioPortBlocks.isEmpty()) return;
        Block[] blocks = ioPortBlocks.toArray(new Block[0]);

        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> forward(Capabilities.ItemHandler.BLOCK, level, pos, side), blocks);
        event.registerBlock(Capabilities.FluidHandler.BLOCK,
                (level, pos, state, be, side) -> forward(Capabilities.FluidHandler.BLOCK, level, pos, side), blocks);
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, side) -> forward(Capabilities.EnergyStorage.BLOCK, level, pos, side), blocks);
    }

    private static <T> T forward(BlockCapability<T, Direction> capability, Level level, BlockPos pos, Direction side) {
        if (!(level instanceof ServerLevel serverLevel)) return null;

        Set<MultiblockInstance> instances = WorldMultiblockTracker.get(serverLevel).getInstancesAt(pos);
        for (MultiblockInstance instance : instances) {
            Optional<BlockPos> corePos = instance.getCorePos();
            if (corePos.isEmpty() || corePos.get().equals(pos)) continue;
            T cap = level.getCapability(capability, corePos.get(), side);
            if (cap != null) return cap;
        }
        return null;
    }
}
