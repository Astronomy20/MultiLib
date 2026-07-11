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
 * Demo block for {@link ExampleRedTankBlockEntity} - see that class's javadoc for the neighbor-merge
 * mechanism. All the actual aggregation plumbing (neighbor rescans on place/break/neighbor-update) comes
 * for free from {@link AbstractAggregatingBlock}; this class only adds bucket interaction.
 */
public class ExampleRedTankBlock extends AbstractAggregatingBlock {

    public ExampleRedTankBlock(Properties properties) {
        super(properties);
    }

    /**
     * Bucket-fill/drain support - works from ANY block of the merged tank, not just one designated
     * position: {@link ExampleRedTankBlockEntity#resolveActiveHandler()} presents the whole group's
     * combined total regardless of which specific block is clicked. See
     * {@code ExampleTankBlock#useItemOn} for why this override (and its exact fall-through rules) is
     * necessary at all - same reasoning applies here unchanged.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ExampleRedTankBlockEntity be)) {
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
        return new ExampleRedTankBlockEntity(ExampleAggregateTankSetup.RED_TANK_BE_TYPE, pos, state);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }
}
