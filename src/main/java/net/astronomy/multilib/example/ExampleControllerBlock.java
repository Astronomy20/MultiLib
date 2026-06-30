package net.astronomy.multilib.example;

import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Test/demo controller block — placing it completes the {@code multilib:example} structure
 * (it is both the core and the activation symbol 'C' in {@link ExamplePattern}).
 */
public class ExampleControllerBlock extends AbstractMultiblockControllerBlock implements EntityBlock {

    public ExampleControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult openMenu(Player player, Level level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof ExampleControllerBE be) {
            // openMenu() is only reached server-side (see useWithoutItem below), and
            // Player#displayClientMessage is a no-op on ServerPlayer — only LocalPlayer overrides it.
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "[MultiLib] Example structure formed — state: " + be.getState().getId()));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExampleControllerBE(ExampleSetup.CONTROLLER_BE_TYPE, pos, state);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (BlockEntityTicker<T>) AbstractMultiblockControllerBE.createServerTicker();
    }
}
