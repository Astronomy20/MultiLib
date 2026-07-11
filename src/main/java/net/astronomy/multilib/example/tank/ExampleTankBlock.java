package net.astronomy.multilib.example.tank;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Test/demo fluid-capable block for the KubeJS-defined {@code multilib:expandable_tank} structure (see
 * {@code expandable_tank.js} and {@link ExampleTankBlockEntity}). Every block of the tank's solid-fill
 * body is one of these, each holding its own real fluid content at a fixed capacity - see the block
 * entity's own javadoc for how those combine into one logical tank once the structure is formed.
 */
public class ExampleTankBlock extends AbstractMultiblockControllerBlock implements EntityBlock {

    public ExampleTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult openMenu(Player player, Level level, BlockPos pos, BlockState state) {
        // No GUI for this demo - just report the combined tank's current contents (see
        // ExampleTankBlockEntity#resolveActiveHandler), mirroring ExampleControllerBlock's own simple
        // chat-message approach. openMenu() is only reached server-side (see
        // AbstractMultiblockControllerBlock#useWithoutItem).
        if (level.getBlockEntity(pos) instanceof ExampleTankBlockEntity be) {
            var handler = be.resolveActiveHandler();
            var fluid = handler.getFluidInTank(0);
            player.sendSystemMessage(Component.literal("[MultiLib] Tank: "
                    + fluid.getAmount() + " / " + handler.getTankCapacity(0) + " mB"
                    + (fluid.isEmpty() ? "" : " of " + fluid.getFluid())));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Bucket-fill/drain support - works from ANY block of the tank, not just one designated position:
     * {@link ExampleTankBlockEntity#resolveActiveHandler()} presents the whole structure's combined
     * total regardless of which specific block is clicked. Confirmed necessary: NeoForge does NOT wire
     * bucket-vs-block-capability interaction automatically just from registering the capability - without
     * this override, buckets do nothing at all against this block.
     * <p>
     * Any item exposing a fluid capability is handled ENTIRELY by us and never falls through to vanilla's
     * own item-use logic - not even when the transfer fails (tank full on fill, nothing to drain).
     * Earlier attempts tried to let a "genuinely full" failure fall through to {@code
     * PASS_TO_DEFAULT_BLOCK_INTERACTION} so the bucket's own default use() would spill into the world -
     * that turned out to be unreliable: vanilla's bucket use() doesn't consistently respect our
     * fail-vs-pass distinction (observed placing/picking up world fluid even on a FAIL result, and even on
     * transfers that had actually just succeeded a moment before), so the world-placement/pickup fallback
     * is not used here at all - a full tank simply rejects the click. If "spill into the world when full"
     * is wanted later, it needs to be implemented as an explicit, self-contained action here (manually
     * placing the fluid block and swapping the item stack ourselves) rather than relying on vanilla's
     * fallback chain.
     * <p>
     * The CLIENT never even attempts the real fill/drain: {@link ExampleTankBlockEntity#resolveActiveHandler()}
     * can only see this one block's own solo tank client-side (the multiblock instance/aggregate lookup is
     * server-only), so any success/failure it computes is unreliable - and empirically, returning FAIL
     * (or PASS) from the client side of this method was NOT enough to stop the bucket's own default
     * world-placement/pickup logic from running anyway (reproduced repeatedly: water kept flooding out
     * into the world even while the server-side fill was succeeding correctly at the very same time).
     * Unconditionally claiming the interaction client-side, before even trying the capability, is the only
     * thing that reliably prevents it. The server (below) remains fully authoritative for whether anything
     * actually happens to the tank or the player's held item; the client just has to not race it.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ExampleTankBlockEntity be)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (FluidUtil.getFluidHandler(stack).isEmpty()) {
            // Not a fluid container at all - nothing for this block to do with it.
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        var handler = be.resolveActiveHandler();
        boolean success = FluidUtil.interactWithFluidHandler(player, hand, handler);
        // TEMP DEBUG - remove once the world-placement bug is confirmed fixed.
        MultiLib.LOGGER.info("[MultiLib DEBUG] pos={} side=SERVER handler={} amount={} capacity={} "
                        + "interactSuccess={}", pos, handler.getClass().getSimpleName(),
                handler.getFluidInTank(0).getAmount(), handler.getTankCapacity(0), success);
        // Consumed either way - success or failure, this is a fluid container interacting with this
        // block's tank, and it's never handed off to vanilla's own bucket logic.
        return success ? ItemInteractionResult.SUCCESS : ItemInteractionResult.FAIL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExampleTankBlockEntity(ExampleTankSetup.TANK_BE_TYPE, pos, state);
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (BlockEntityTicker<T>) AbstractMultiblockControllerBE.createServerTicker();
    }
}
