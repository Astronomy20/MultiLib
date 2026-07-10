package net.astronomy.multilib.example;

import net.astronomy.multilib.api.aggregate.AggregatableBlockEntity;
import net.astronomy.multilib.api.aggregate.AggregateGroup;
import net.astronomy.multilib.api.aggregate.AggregationEngine;
import net.astronomy.multilib.api.aggregate.AggregationShapePolicies;
import net.astronomy.multilib.api.aggregate.AggregationShapePolicy;
import net.astronomy.multilib.api.component.FluidTankComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Demo neighbor-aggregating fluid tank #2: the "abstract structure" style from the reference video -
 * the exact same {@link net.astronomy.multilib.api.aggregate.AbstractAggregatingBlock} mechanism as
 * {@link ExampleRedTankBlockEntity}, except with {@link AggregationShapePolicies#freeform()} instead of
 * {@link AggregationShapePolicies#cuboid}: there is no shape constraint at all. A stepped/offset stack,
 * an L-shape, a branching blob - any set of {@code example_green_tank} blocks connected by adjacency
 * merges into one logical tank, because adjacency is the only thing that was ever checked.
 * <p>
 * See {@link ExampleRedTankBlockEntity}'s javadoc for the full per-block-storage/combined-view
 * explanation - it applies here unchanged, only the shape policy differs.
 */
public class ExampleGreenTankBlockEntity extends BlockEntity implements AggregatableBlockEntity, FluidAggregateTank {

    public static final ResourceLocation GROUP_ID = ResourceLocation.fromNamespaceAndPath("multilib", "example_green_tank");
    private static final AggregationShapePolicy SHAPE = AggregationShapePolicies.freeform();
    public static final int CAPACITY_PER_BLOCK = 4_000;

    // freeform() has no shape constraint of its own - getMaxAggregateSize() is the ONLY thing capping
    // how large a group can grow. Kept small (rather than the interface's default 512) so the cap is
    // actually easy to hit and observe while testing: place a 17th connected block and it stays its own
    // singleton instead of joining, exactly like a rejected cuboid merge would for ExampleRedTankBlockEntity.
    private static final int MAX_AGGREGATE_SIZE = 16;

    public final FluidTankComponent tank = new FluidTankComponent(CAPACITY_PER_BLOCK, null, this::onTankChanged);
    private AggregateGroup group;

    public ExampleGreenTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.group = AggregationEngine.singleton(GROUP_ID, pos);
    }

    // Referenced by ExampleAggregateTankSetup's BlockEntityType.Builder.
    public static ExampleGreenTankBlockEntity create(BlockPos pos, BlockState state) {
        return new ExampleGreenTankBlockEntity(ExampleAggregateTankSetup.GREEN_TANK_BE_TYPE, pos, state);
    }

    @Override
    public ResourceLocation getAggregationGroup() {
        return GROUP_ID;
    }

    @Override
    public AggregationShapePolicy getShapePolicy() {
        return SHAPE;
    }

    @Override
    public int getMaxAggregateSize() {
        return MAX_AGGREGATE_SIZE;
    }

    @Override
    public void onAggregateChanged(AggregateGroup newGroup) {
        this.group = newGroup;
        setChanged();
    }

    @Override
    public AggregateGroup getAggregateGroup() {
        return group;
    }

    @Override
    public FluidTankComponent getTank() {
        return tank;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            AggregationEngine.onPlaced(level, worldPosition);
        }
    }

    private void onTankChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public IFluidHandler resolveActiveHandler() {
        if (level == null || group.isSingleton()) return tank;
        List<FluidTankComponent> memberTanks = new ArrayList<>();
        for (BlockPos pos : group.members()) {
            if (level.getBlockEntity(pos) instanceof ExampleGreenTankBlockEntity be) {
                memberTanks.add(be.tank);
            }
        }
        if (memberTanks.isEmpty()) return tank;
        return new ExampleTankAggregateFluidHandler(memberTanks);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.save(tag, registries);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.load(tag, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tank.save(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        tank.load(tag, registries);
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
