package net.astronomy.multilib.api.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public abstract class AbstractMultiblockControllerBlock extends AbstractMultiblockPartBlock {

    protected AbstractMultiblockControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            // Controller removed — WorldMultiblockTracker handles cleanup via BlockBreakHandler
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    protected abstract InteractionResult openMenu(Player player, Level level, BlockPos pos, BlockState state);

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AbstractMultiblockControllerBE be) {
            if (be.isFormed()) {
                return openMenu(player, level, pos, state);
            }
        }
        return InteractionResult.PASS;
    }
}
