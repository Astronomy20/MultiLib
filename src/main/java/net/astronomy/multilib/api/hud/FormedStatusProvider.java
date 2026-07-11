package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

/**
 * Opt-in provider that shows the definition's display name (via
 * {@code MultiblockDefinition#getNameTranslationKey()}, falling back to its raw id) plus a status line
 * - the live {@link net.astronomy.multilib.api.state.MultiblockState} name if the resolved core block
 * entity is an {@link net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE}, otherwise
 * a generic "Formed" line for coreless structures. Register globally for the same behavior across every
 * definition: {@code MultiblockHudRegistry.registerGlobal(new FormedStatusProvider())}.
 */
public final class FormedStatusProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        Component name = ctx.definition().getNameTranslationKey()
                .<Component>map(Component::translatable)
                .orElseGet(() -> Component.literal(ctx.definition().getId().toString()));
        out.accept(new HudEntry.Text(name));
        out.accept(new HudEntry.Text(statusLine(ctx)));
        // Matches the recipe viewers' title-suffix convention (MultiblockPreviewPanel#multiblockName):
        // any explicitly declared variant name shows, the legacy implicit "default" never does.
        String variant = ctx.instance().getVariant();
        if (!"default".equals(variant)) {
            out.accept(new HudEntry.KeyValue(
                    Component.translatable("multilib.hud.variant"), Component.literal(variant)));
        }
    }

    /**
     * The core's live {@link net.astronomy.multilib.api.state.MultiblockState} name (Idle/Running/Error/
     * any custom state a dev registered), or the generic "Formed" line if there's no core, no block
     * entity there, or the block entity isn't a state-tracking controller.
     */
    private Component statusLine(HudContext ctx) {
        BlockPos corePos = ctx.instance().getCorePos().orElse(null);
        if (corePos != null) {
            BlockEntity be = ctx.level().getBlockEntity(corePos);
            if (be instanceof AbstractMultiblockControllerBE controller) {
                return controller.getState().getNameTranslationKey()
                        .<Component>map(Component::translatable)
                        .orElseGet(() -> Component.literal(controller.getState().getId().toString()));
            }
        }
        return Component.translatable("multilib.hud.formed");
    }
}
