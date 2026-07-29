package net.astronomy.multilib.example.donut;

import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBlock;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Test/demo controller block for {@code multilib:example_donut} (see {@link DonutPattern}) - it is
 * the single landmark voxel that anchors the {@link net.astronomy.multilib.api.pattern.providers.RevolutionProvider}
 * ring, not one of the repeating shell blocks.
 */
public class DonutControllerBlock extends AbstractMultiblockControllerBlock implements EntityBlock {

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public DonutControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any()
                .setValue(AbstractMultiblockPartBlock.MODEL_HIDDEN, false)
                .setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }

    @Override
    protected InteractionResult openMenu(Player player, Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof DonutControllerBE be) {
            player.sendSystemMessage(Component.literal(
                    "[MultiLib] Donut structure formed - state: " + be.getState().getId()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DonutControllerBE(DonutExampleSetup.CONTROLLER_BE_TYPE, pos, state);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (BlockEntityTicker<T>) AbstractMultiblockControllerBE.createServerTicker();
    }
}
