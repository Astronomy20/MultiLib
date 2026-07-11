package net.astronomy.multilib.api.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Opt-in provider that shows the core's coordinates and straight-line distance whenever the player is
 * looking at a non-core member of a formed instance - the common "which block do I click to see the
 * controller's inventory/state" question in a large multiblock (GT/Mekanism-style). Shows nothing when
 * the looked-at block IS the core, or when the definition has no declared core. Register per-definition:
 * {@code MultiblockHudRegistry.register(definitionId, new ControllerLocationHudProvider())}.
 */
public final class ControllerLocationHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        BlockPos corePos = ctx.instance().getCorePos().orElse(null);
        if (corePos == null || corePos.equals(ctx.pos())) return;

        double distance = Math.sqrt(corePos.distSqr(ctx.pos()));
        Component key = Component.translatable("multilib.hud.controller");
        Component value = Component.translatable("multilib.hud.controller_value",
                corePos.getX(), corePos.getY(), corePos.getZ(), String.format("%.1f", distance));
        out.accept(new HudEntry.KeyValue(key, value));
    }
}
