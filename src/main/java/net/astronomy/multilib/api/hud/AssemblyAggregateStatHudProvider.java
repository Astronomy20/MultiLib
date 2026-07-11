package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.assembly.AssemblyDefinition;
import net.astronomy.multilib.api.assembly.AssemblyInstance;
import net.astronomy.multilib.api.assembly.AssemblyStatAggregator;
import net.astronomy.multilib.api.assembly.StatMerge;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.api.tier.MultiblockTier;
import net.astronomy.multilib.core.assembly.AssemblyRegistry;
import net.astronomy.multilib.core.assembly.WorldAssemblyTracker;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Opt-in provider that shows the assembly-level {@code aggregateStat} values (Fase 12) declared on an
 * {@link AssemblyDefinition} - complements {@link AssemblyStatusProvider}'s state/role-count line with
 * the actual numbers those {@link StatMerge} rules compute (e.g. "Power: 480" summed across turbine
 * members). Shows nothing for a member not currently in an assembly, or if the assembly declares no
 * {@code aggregateStat} entries.
 * <p>
 * Each member's own contribution is its {@code api/tier} stats summed across whichever of its symbols
 * declare that key ({@link net.astronomy.multilib.api.tier.MultiblockTierResolution#combinedStats}
 * with {@link Double#sum}) - members with no tier declarations simply contribute nothing. The
 * assembly's own {@link StatMerge} rule is then applied across members, exactly as declared. Register
 * globally: {@code MultiblockHudRegistry.registerGlobal(new AssemblyAggregateStatHudProvider())}.
 */
public final class AssemblyAggregateStatHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        ServerLevel level = ctx.level();
        Optional<AssemblyInstance> assemblyOpt = WorldAssemblyTracker.get(level).getByMember(ctx.instance().getId());
        if (assemblyOpt.isEmpty()) return;
        AssemblyInstance assembly = assemblyOpt.get();

        AssemblyDefinition def = AssemblyRegistry.get(assembly.getDefinitionId()).orElse(null);
        if (def == null || def.getAggregateStats().isEmpty()) return;

        WorldMultiblockTracker memberTracker = WorldMultiblockTracker.get(level);
        List<Map<String, Double>> perMemberStats = new ArrayList<>();
        for (UUID memberId : assembly.allMemberIds()) {
            MultiblockInstance memberInstance = memberTracker.getById(memberId).orElse(null);
            if (memberInstance == null) continue;
            MultiblockDefinition memberDef = MultiblockRegistry.get(memberInstance.getDefinitionId()).orElse(null);
            if (memberDef == null) continue;

            var resolution = MultiblockTier.get(level, memberInstance, memberDef);
            Map<String, Double> stats = new java.util.HashMap<>();
            for (String key : def.getAggregateStats().keySet()) {
                stats.put(key, resolution.combinedStats(key, Double::sum, 0.0));
            }
            perMemberStats.add(stats);
        }

        Map<String, Double> aggregated = AssemblyStatAggregator.aggregate(def, perMemberStats);
        for (Map.Entry<String, Double> entry : aggregated.entrySet()) {
            out.accept(new HudEntry.KeyValue(
                    Component.literal(entry.getKey()), Component.literal(String.valueOf(entry.getValue()))));
        }
    }
}
