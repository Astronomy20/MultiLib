package net.astronomy.multilib.api.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

/**
 * Opt-in provider that shows the resolved core's current error reason, if the block entity there
 * implements {@link HudErrorSource}. MultiLib has no built-in notion of "why" a structure errored - a
 * validator rejecting formation ({@code MultiblockValidator}) never lets a bad structure form at all, so
 * there is nothing to report after the fact unless the dev's own controller tracks a reason itself (e.g.
 * "no fuel", "overheating") and exposes it here. Shows nothing if the core has no block entity, isn't a
 * {@link HudErrorSource}, or currently has no reason to report. Register per-definition:
 * {@code MultiblockHudRegistry.register(definitionId, new ErrorReasonHudProvider())}.
 */
public final class ErrorReasonHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        BlockPos corePos = ctx.instance().getCorePos().orElse(null);
        if (corePos == null) return;

        BlockEntity be = ctx.level().getBlockEntity(corePos);
        if (!(be instanceof HudErrorSource source)) return;

        source.getHudErrorReason().ifPresent(reason -> out.accept(new HudEntry.KeyValue(
                Component.translatable("multilib.hud.error"), reason)));
    }
}
