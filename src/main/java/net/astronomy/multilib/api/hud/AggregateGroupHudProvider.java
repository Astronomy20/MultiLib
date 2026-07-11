package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.aggregate.AggregatableBlockEntity;
import net.astronomy.multilib.api.aggregate.AggregateGroup;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Consumer;

/**
 * Opt-in provider that shows the size of the {@code api/aggregate} neighbor-merge group the looked-at
 * member currently belongs to, if its own block entity implements {@link AggregatableBlockEntity} -
 * i.e. a pattern-matched multiblock member that ALSO opts into wall-neighbor aggregation on the side
 * (both mechanisms are independent and can be combined on the same block entity).
 * <p>
 * A pure {@link AggregatableBlockEntity} with no {@code MultiblockDefinition} at all (aggregation used
 * on its own, with no pattern-matched structure) is outside this bridge's scope: {@code api/hud} is
 * keyed off a formed {@code MultiblockInstance} via {@link HudContext}, which a plain aggregating block
 * never has. Register per-definition: {@code MultiblockHudRegistry.register(definitionId, new AggregateGroupHudProvider())}.
 */
public final class AggregateGroupHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        BlockEntity be = ctx.level().getBlockEntity(ctx.pos());
        if (!(be instanceof AggregatableBlockEntity aggregatable)) return;

        AggregateGroup group = aggregatable.getAggregateGroup();
        Component value = group.isSingleton()
                ? Component.translatable("multilib.hud.aggregate_singleton")
                : Component.translatable("multilib.hud.aggregate_value", group.size());
        out.accept(new HudEntry.KeyValue(Component.translatable("multilib.hud.aggregate"), value));
    }
}
