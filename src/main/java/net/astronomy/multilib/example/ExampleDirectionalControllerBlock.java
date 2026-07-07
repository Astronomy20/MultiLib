package net.astronomy.multilib.example;

import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBlock;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Test/demo controller block with a real placed facing (like a furnace), used to exercise the
 * "rigid" {@code mainFace()} ghost-overlay behavior: its {@link net.astronomy.multilib.api.block.BlockDefinition}
 * (see {@link ExampleSetup}) declares {@code .mainFace()}, so the ghost overlay/auto-place preview
 * must always stay pinned to whichever way this block is actually facing in the world, never to the
 * player's look direction - unlike {@link ExampleControllerBlock}, which has no facing of its own and
 * always follows the player.
 */
public class ExampleDirectionalControllerBlock extends AbstractMultiblockControllerBlock implements EntityBlock {

    public ExampleDirectionalControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(AbstractMultiblockPartBlock.MODEL_HIDDEN, false)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult openMenu(Player player, Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof ExampleDirectionalControllerBE be) {
            player.sendSystemMessage(Component.literal(
                    "[MultiLib] Example directional structure formed - state: " + be.getState().getId()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExampleDirectionalControllerBE(ExampleSetup.DIRECTIONAL_CONTROLLER_BE_TYPE, pos, state);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (BlockEntityTicker<T>) AbstractMultiblockControllerBE.createServerTicker();
    }
}
