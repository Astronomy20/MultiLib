package net.astronomy.multilib.api.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.function.Consumer;

/**
 * Opt-in provider that queries the instance's core position for NeoForge's standard
 * {@link Capabilities.FluidHandler#BLOCK} capability (null side) and, if present, shows one line per
 * tank ("Fluid" for a single-tank handler, "Tank N" for multiple). Works with any cap-exposing
 * controller, including {@code api/component}'s {@code FluidTankComponent}. Register per-definition:
 * {@code MultiblockHudRegistry.register(definitionId, new FluidHudProvider())}.
 */
public final class FluidHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        BlockPos corePos = ctx.instance().getCorePos().orElse(null);
        if (corePos == null) return;

        BlockState state = ctx.level().getBlockState(corePos);
        BlockEntity be = ctx.level().getBlockEntity(corePos);
        IFluidHandler handler = Capabilities.FluidHandler.BLOCK.getCapability(ctx.level(), corePos, state, be, null);
        if (handler == null) return;

        int tanks = handler.getTanks();
        for (int i = 0; i < tanks; i++) {
            FluidStack stack = handler.getFluidInTank(i);
            Component key = Component.literal(tanks == 1 ? "Fluid" : "Tank " + (i + 1));
            Component value = stack.isEmpty()
                    ? Component.literal("Empty")
                    : Component.literal(stack.getAmount() + " / " + handler.getTankCapacity(i) + " mB ")
                            .append(stack.getHoverName());
            out.accept(new HudEntry.KeyValue(key, value));
        }
    }
}
