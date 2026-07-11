package net.astronomy.multilib.example.tank;

import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.component.FluidTankComponent;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Test/demo controller BlockEntity for the KubeJS-defined {@code multilib:expandable_tank} structure
 * (see {@code expandable_tank.js}) - demonstrates fluid storage on a shapeless multiblock whose size can
 * change at runtime as the player grows it.
 * <p>
 * Every block of the tank's solid-fill body is one of these, and every single one holds a real,
 * independent {@link #tank} at a fixed capacity - nothing is resized, nothing needs transferring when
 * the structure grows or shrinks, since each block's own content is exactly what's saved to (and loaded
 * from) that block's own NBT, same as any other block entity. What DOES scale with the structure is the
 * combined view any interaction sees: see {@link #resolveActiveHandler()} and
 * {@link ExampleTankAggregateFluidHandler}.
 */
public class ExampleTankBlockEntity extends AbstractMultiblockControllerBE {

    private static final int CAPACITY_PER_BLOCK = 1_000;

    public final FluidTankComponent tank = new FluidTankComponent(CAPACITY_PER_BLOCK, null, this::setChanged);

    public ExampleTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    // Referenced by ExampleTankSetup's BlockEntityType.Builder - see ExampleControllerBE#create for
    // why this is a static factory rather than an inline lambda.
    public static ExampleTankBlockEntity create(BlockPos pos, BlockState state) {
        return new ExampleTankBlockEntity(ExampleTankSetup.TANK_BE_TYPE, pos, state);
    }

    @Override
    protected void saveController(CompoundTag tag, HolderLookup.Provider registries) {
        tank.save(tag, registries);
    }

    @Override
    protected void loadController(CompoundTag tag, HolderLookup.Provider registries) {
        tank.load(tag, registries);
    }

    /**
     * The fluid handler this position should expose right now: if it's currently part of a formed
     * {@code multilib:expandable_tank} instance, a fresh {@link ExampleTankAggregateFluidHandler}
     * wrapping every member block's own {@link #tank} - so capacity, current amount, and fill/drain all
     * reflect the WHOLE structure's total regardless of which specific block a bucket or pipe touches.
     * Otherwise (not currently part of a formed instance), just this block's own standalone tank - a
     * single placed-but-unformed block still holds its own fixed capacity on its own.
     */
    public IFluidHandler resolveActiveHandler() {
        var serverLevel = getServerLevel();
        if (serverLevel.isEmpty()) return tank;
        ServerLevel level = serverLevel.get();

        WorldMultiblockTracker tracker = WorldMultiblockTracker.get(level);
        for (MultiblockInstance instance : tracker.getInstancesAt(getBlockPos())) {
            MultiblockDefinition definition = MultiblockRegistry.get(instance.getDefinitionId()).orElse(null);
            if (definition == null) continue;

            List<FluidTankComponent> memberTanks = new ArrayList<>();
            for (BlockPos pos : instance.getPositions()) {
                if (level.getBlockEntity(pos) instanceof ExampleTankBlockEntity be) {
                    memberTanks.add(be.tank);
                }
            }
            if (!memberTanks.isEmpty()) {
                return new ExampleTankAggregateFluidHandler(memberTanks);
            }
        }
        return tank;
    }
}
