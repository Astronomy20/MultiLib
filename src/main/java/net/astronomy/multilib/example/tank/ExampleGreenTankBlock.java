package net.astronomy.multilib.example.tank;

import net.astronomy.multilib.api.aggregate.AbstractAggregatingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Demo block for {@link ExampleGreenTankBlockEntity} - see that class's javadoc for why this one allows
 * fully abstract/irregular structures (the reference video's behavior) while {@link ExampleRedTankBlock}
 * only allows solid rectangular prisms. Both share the exact same {@link AbstractAggregatingBlock}
 * plumbing; only the {@code AggregationShapePolicy} passed on the block entity side differs.
 */
public class ExampleGreenTankBlock extends AbstractAggregatingBlock {

    public ExampleGreenTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ExampleGreenTankBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        var handler = be.resolveActiveHandler();
        if (FluidUtil.interactWithFluidHandler(player, hand, handler)) {
            return ItemInteractionResult.SUCCESS;
        }
        if (FluidUtil.getFluidHandler(stack).isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean tankFull = handler.getFluidInTank(0).getAmount() >= handler.getTankCapacity(0);
        return tankFull ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.FAIL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExampleGreenTankBlockEntity(ExampleAggregateTankSetup.GREEN_TANK_BE_TYPE, pos, state);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }
}
