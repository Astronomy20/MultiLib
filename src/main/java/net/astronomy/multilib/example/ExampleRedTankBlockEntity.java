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
 * Demo neighbor-aggregating fluid tank #1: Create-mod-style. Place several {@code example_red_tank}
 * blocks touching each other and they merge into one bigger logical tank purely because they're
 * adjacent - no JSON pattern, no declared {@code MultiblockDefinition}, nothing pre-registered about
 * their arrangement at all. {@link net.astronomy.multilib.api.aggregate.AbstractAggregatingBlock} (see
 * {@link ExampleRedTankBlock}) drives the neighbor rescans, and
 * {@link AggregationShapePolicies#cuboid} only lets rectangular-prism arrangements actually merge - an
 * L-shape or an offset stack just stays as separate independent 1-block tanks instead.
 * <p>
 * Compare {@link ExampleGreenTankBlockEntity}, which uses the exact same mechanism with
 * {@link AggregationShapePolicies#freeform()} instead - any connected shape at all is allowed to merge.
 * <p>
 * Every block holds its own real, independent {@link #tank} at a fixed per-block capacity - nothing is
 * resized or transferred when the group grows/shrinks, since each block's own content is exactly what's
 * saved to (and loaded from) that block's own NBT. What scales with the group is only the combined view
 * any interaction sees - see {@link #resolveActiveHandler()}.
 */
public class ExampleRedTankBlockEntity extends BlockEntity implements AggregatableBlockEntity, FluidAggregateTank {

    public static final ResourceLocation GROUP_ID = ResourceLocation.fromNamespaceAndPath("multilib", "example_red_tank");
    private static final AggregationShapePolicy SHAPE = AggregationShapePolicies.cuboid(3, 3, 3);
    public static final int CAPACITY_PER_BLOCK = 4_000;

    // Redundant with the cuboid(3, 3, 3) policy itself (which already tops out at 3*3*3 = 27 blocks),
    // but declared explicitly anyway so this reads the same way as ExampleGreenTankBlockEntity's own
    // override - getMaxAggregateSize() is always checked in addition to the shape policy, never a
    // substitute for one.
    private static final int MAX_AGGREGATE_SIZE = 27;

    public final FluidTankComponent tank = new FluidTankComponent(CAPACITY_PER_BLOCK, null, this::onTankChanged);
    private AggregateGroup group;

    public ExampleRedTankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.group = AggregationEngine.singleton(GROUP_ID, pos);
    }

    // Referenced by ExampleAggregateTankSetup's BlockEntityType.Builder.
    public static ExampleRedTankBlockEntity create(BlockPos pos, BlockState state) {
        return new ExampleRedTankBlockEntity(ExampleAggregateTankSetup.RED_TANK_BE_TYPE, pos, state);
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

    // Re-derives this block's group from live world state as soon as it's actually placed in a level -
    // membership is never persisted (see class javadoc), so a freshly loaded/placed block always starts
    // out as a lone singleton until this runs.
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

    /**
     * The fluid handler this position should expose right now: every member of the current group's own
     * {@link #tank}, combined into one logical total via {@link ExampleTankAggregateFluidHandler} - so it
     * doesn't matter which specific block of the merged tank a bucket or pipe touches. A lone (singleton)
     * block just exposes its own standalone tank.
     */
    public IFluidHandler resolveActiveHandler() {
        if (level == null || group.isSingleton()) return tank;
        List<FluidTankComponent> memberTanks = new ArrayList<>();
        for (BlockPos pos : group.members()) {
            if (level.getBlockEntity(pos) instanceof ExampleRedTankBlockEntity be) {
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

    // Synced to the client (not just saved to disk) so the fill-level renderer always has fresh data.
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
