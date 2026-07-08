package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.process.RecipeProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

/**
 * Opt-in provider that shows the current job progress of a controller's {@link RecipeProcessor}, if the
 * block entity at the instance's core implements {@link HudProcessSource}. MultiLib has no opinion on
 * the dev's block entity layout, so this instanceof-checks the resolved core block entity rather than
 * assuming a fixed field/type. Register per-definition:
 * {@code MultiblockHudRegistry.register(definitionId, new ProcessHudProvider())}.
 */
public final class ProcessHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        BlockPos corePos = ctx.instance().getCorePos().orElse(null);
        if (corePos == null) return;

        BlockEntity be = ctx.level().getBlockEntity(corePos);
        if (!(be instanceof HudProcessSource source)) return;

        source.getHudProcessor().ifPresent(processor -> {
            Component label = switch (processor.getState()) {
                case RUNNING -> Component.translatable("multilib.hud.processing");
                case PAUSED -> Component.translatable("multilib.hud.paused");
                case IDLE -> Component.translatable("multilib.hud.idle");
            };
            out.accept(new HudEntry.Progress(processor.getProgressFraction(), label));
        });
    }
}
