package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.tier.MultiblockTier;
import net.astronomy.multilib.api.tier.MultiblockTierResolution;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;

/**
 * Opt-in, parametrized provider for a single {@code api/tier} stat key - the generic counterpart to
 * writing a bespoke provider for every custom stat (e.g. "heat", "throughput") a definition declares via
 * {@code .tierStats(...)}. Wraps {@link MultiblockTierResolution#combinedStats}, so the merge rule
 * across symbols is always explicit, exactly like the rest of {@code api/tier} - never guessed.
 * <pre>{@code
 * MultiblockHudRegistry.register(definitionId, StatHudProvider.summing("power", Component.literal("Power")));
 * MultiblockHudRegistry.register(definitionId,
 *         new StatHudProvider("heat_resistance", Component.literal("Heat Resistance"), Math::min, Double.POSITIVE_INFINITY));
 * }</pre>
 */
public final class StatHudProvider implements MultiblockHudProvider {

    private final String key;
    private final Component label;
    private final DoubleBinaryOperator merger;
    private final double identity;

    public StatHudProvider(String key, Component label, DoubleBinaryOperator merger, double identity) {
        this.key = key;
        this.label = label;
        this.merger = merger;
        this.identity = identity;
    }

    /** Convenience for the common case of summing {@code key} across every symbol that declares it. */
    public static StatHudProvider summing(String key, Component label) {
        return new StatHudProvider(key, label, Double::sum, 0.0);
    }

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        MultiblockTierResolution resolution = MultiblockTier.get(ctx.level(), ctx.instance(), ctx.definition());
        double value = resolution.combinedStats(key, merger, identity);
        out.accept(new HudEntry.KeyValue(label, Component.literal(String.valueOf(value))));
    }
}
