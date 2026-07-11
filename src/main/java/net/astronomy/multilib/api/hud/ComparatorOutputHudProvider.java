package net.astronomy.multilib.api.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

/**
 * Opt-in provider that shows the resolved core's current comparator output (0-15) as a quick "how full
 * is this on the redstone scale" readout, without needing to actually place a comparator to check. Reads
 * the value from the core's block entity if it implements {@link HudComparatorSource}; shows nothing
 * otherwise. Register per-definition: {@code MultiblockHudRegistry.register(definitionId, new ComparatorOutputHudProvider())}.
 */
public final class ComparatorOutputHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        BlockPos corePos = ctx.instance().getCorePos().orElse(null);
        if (corePos == null) return;

        BlockEntity be = ctx.level().getBlockEntity(corePos);
        if (!(be instanceof HudComparatorSource source)) return;

        int level = Math.max(0, Math.min(15, source.getHudComparatorOutput()));
        out.accept(new HudEntry.KeyValue(
                Component.translatable("multilib.hud.comparator"),
                Component.translatable("multilib.hud.comparator_value", level)));
    }
}
