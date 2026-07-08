package net.astronomy.multilib.api.hud;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * The only HUD provider registered globally by default (see the static initializer in
 * {@link MultiblockHudRegistry}): shows the definition's display name (via
 * {@code MultiblockDefinition#getNameTranslationKey()}, falling back to its raw id) plus a static
 * "Formed" status line. Every other built-in provider in this package is opt-in.
 * <p>
 * Like every other provider here, this is fully suppressible:
 * {@code MultiblockHudRegistry.setHudEnabled(id, false)} hides this too for that definition -
 * "default-on" only means "the dev doesn't have to register anything to get basic HUD support", not
 * "the dev can't turn it off".
 */
public final class FormedStatusProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        Component name = ctx.definition().getNameTranslationKey()
                .<Component>map(Component::translatable)
                .orElseGet(() -> Component.literal(ctx.definition().getId().toString()));
        out.accept(new HudEntry.Text(name));
        out.accept(new HudEntry.Text(Component.translatable("multilib.hud.formed")));
        // Matches the recipe viewers' title-suffix convention (MultiblockPreviewPanel#multiblockName):
        // any explicitly declared variant name shows, the legacy implicit "default" never does.
        String variant = ctx.instance().getVariant();
        if (!"default".equals(variant)) {
            out.accept(new HudEntry.KeyValue(
                    Component.translatable("multilib.hud.variant"), Component.literal(variant)));
        }
    }
}
