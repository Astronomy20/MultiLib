package net.astronomy.multilib.api.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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
            // Controller removed - WorldMultiblockTracker handles cleanup via BlockBreakHandler
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    protected abstract InteractionResult openMenu(Player player, Level level, BlockPos pos, BlockState state);

    /**
     * {@code useWithoutItem} is vanilla's own fallback name, but it actually runs whenever
     * {@code useItemOn} passed through - i.e. on every right-click that wasn't already consumed by an
     * item-specific interaction, REGARDLESS of what's in the player's hand. Unconditionally opening the
     * menu here (the old behavior) meant a shapeless/growable structure (e.g. {@code expandable_tank})
     * could never be extended by placing more of its own block against an already-formed instance: the
     * click was always hijacked into "open menu" before the block item ever got a chance to place. Held
     * more of the SAME block specifically is the one case unambiguous enough to safely fall through to
     * placement instead - any other held item (or an empty hand) still opens the menu as before.
     */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AbstractMultiblockControllerBE be) {
            if (be.isFormed() && !isHoldingSameBlock(player, state)) {
                return openMenu(player, level, pos, state);
            }
        }
        return InteractionResult.PASS;
    }

    private static boolean isHoldingSameBlock(Player player, BlockState state) {
        return matchesBlock(player.getItemInHand(InteractionHand.MAIN_HAND), state)
                || matchesBlock(player.getItemInHand(InteractionHand.OFF_HAND), state);
    }

    private static boolean matchesBlock(ItemStack stack, BlockState state) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == state.getBlock();
    }
}
