package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.tier.MultiblockTier;
import net.astronomy.multilib.api.tier.MultiblockTierResolution;
import net.astronomy.multilib.api.tier.TierLevel;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Opt-in provider that resolves every tiered symbol's current tier (see {@code api/tier}) against the
 * blocks actually placed in this instance, and emits one {@link HudEntry.KeyValue} line per resolved
 * symbol. Not registered by default - it isn't useful on a definition with no {@code .tier(...)}
 * declarations. The dev registers it per-definition:
 * {@code MultiblockHudRegistry.register(definitionId, new TierHudProvider())}.
 */
public final class TierHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        MultiblockTierResolution resolution = MultiblockTier.get(ctx.level(), ctx.instance(), ctx.definition());
        for (Map.Entry<Character, TierLevel> entry : resolution.tierBySymbol().entrySet()) {
            Component key = Component.translatable("multilib.hud.tier", String.valueOf(entry.getKey()));
            Component value = Component.literal(entry.getValue().name());
            out.accept(new HudEntry.KeyValue(key, value));
        }
    }
}
