package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.control.RedstoneControlComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

/**
 * Opt-in provider that shows the current {@link RedstoneControlComponent.RedstoneMode} of a
 * controller, if the block entity at the instance's core implements {@link HudRedstoneSource}. Register
 * per-definition: {@code MultiblockHudRegistry.register(definitionId, new RedstoneControlHudProvider())}.
 */
public final class RedstoneControlHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        BlockPos corePos = ctx.instance().getCorePos().orElse(null);
        if (corePos == null) return;

        BlockEntity be = ctx.level().getBlockEntity(corePos);
        if (!(be instanceof HudRedstoneSource source)) return;

        RedstoneControlComponent redstone = source.getHudRedstoneControl();
        if (redstone == null) return;

        out.accept(new HudEntry.KeyValue(Component.literal("Redstone"), Component.literal(redstone.getMode().name())));
    }
}
