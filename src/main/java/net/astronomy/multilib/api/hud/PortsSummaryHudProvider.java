package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.port.AbstractPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Opt-in provider that summarizes every {@code api/port} hatch attached to the instance, one line per
 * distinct port block ("Energy Hatch: 2", "Item Hatch: 1") - useful on large multiblocks with many
 * hatches, where checking each one individually is tedious. Groups by the port block's own display name
 * ({@link net.minecraft.world.level.block.state.BlockState#getBlock()}'s translated name) since
 * {@link AbstractPortBlockEntity} itself carries no separate "port type" label - every port block
 * already has a name, so this needs no extra dev-side wiring. Register per-definition:
 * {@code MultiblockHudRegistry.register(definitionId, new PortsSummaryHudProvider())}.
 */
public final class PortsSummaryHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        // Keyed by the Block singleton (identity-safe, one instance per registered block) rather than
        // its Component name: resolving Component#getString() here would translate server-side using
        // the server's own locale, not the looking player's - every other provider in this package
        // instead hands translatable Components straight through and lets the client resolve them.
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (BlockPos pos : ctx.instance().getPositions()) {
            if (!ctx.level().isLoaded(pos)) continue;
            BlockEntity be = ctx.level().getBlockEntity(pos);
            if (!(be instanceof AbstractPortBlockEntity)) continue;

            counts.merge(ctx.level().getBlockState(pos).getBlock(), 1, Integer::sum);
        }
        for (Map.Entry<Block, Integer> entry : counts.entrySet()) {
            out.accept(new HudEntry.KeyValue(
                    entry.getKey().getName(), Component.literal(String.valueOf(entry.getValue()))));
        }
    }
}
