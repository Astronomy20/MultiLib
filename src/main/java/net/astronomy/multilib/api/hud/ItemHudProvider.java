package net.astronomy.multilib.api.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.Consumer;

/**
 * Opt-in provider that queries the instance's core position for NeoForge's standard
 * {@link Capabilities.ItemHandler#BLOCK} capability (null side) and, if present, shows an
 * "Items: N / M slots" summary line - not one line per stack, which would make the tooltip
 * unreadably tall for anything but a tiny buffer. Works with any cap-exposing controller, including
 * {@code api/component}'s {@code ItemBufferComponent} once wired up via
 * {@code MultiblockComponentHelper.registerItem}. Register per-definition:
 * {@code MultiblockHudRegistry.register(definitionId, new ItemHudProvider())}.
 */
public final class ItemHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        BlockPos corePos = ctx.instance().getCorePos().orElse(null);
        if (corePos == null) return;

        BlockState state = ctx.level().getBlockState(corePos);
        BlockEntity be = ctx.level().getBlockEntity(corePos);
        IItemHandler handler = Capabilities.ItemHandler.BLOCK.getCapability(ctx.level(), corePos, state, be, null);
        if (handler == null) return;

        int slots = handler.getSlots();
        int filled = 0;
        for (int i = 0; i < slots; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) filled++;
        }

        Component key = Component.translatable("multilib.hud.items");
        Component value = Component.translatable("multilib.hud.items_value", filled, slots);
        out.accept(new HudEntry.KeyValue(key, value));
    }
}
