package net.astronomy.multilib.api.hud;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Opt-in provider that shows the instance's current block count - most useful on shapeless/
 * variable-size definitions (e.g. an aggregating tank) where "how big is this right now" isn't
 * something the player can otherwise glance at, unlike a fixed-shape structure. Register per-definition:
 * {@code MultiblockHudRegistry.register(definitionId, new StructureSizeHudProvider())}.
 */
public final class StructureSizeHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        int size = ctx.instance().getPositions().size();
        out.accept(new HudEntry.KeyValue(
                Component.translatable("multilib.hud.size"),
                Component.translatable("multilib.hud.size_value", size)));
    }
}
