package net.astronomy.multilib.core.devtool;

import net.astronomy.multilib.api.assembly.AssemblyBreakPolicy;
import net.astronomy.multilib.api.assembly.ConnectionType;
import net.astronomy.multilib.api.assembly.StatMerge;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Generates assembly source in all three authoring formats (Java / JSON / KubeJS) from an
 * {@link AssemblyExportSpec}, the assembly-level counterpart of {@link MultiblockDevExporter}. This is
 * the export core of the Fase 12 "dev-tool assembly export": the string generation from a described
 * set of linked member structures. Wiring an in-world multi-select UI on top is a follow-up; the spec
 * can equally be assembled by hand or from a scan.
 */
public final class AssemblyDevExporter {

    private AssemblyDevExporter() {}

    /** A role of the exported assembly. */
    public record RoleSpec(String name, ResourceLocation definition, int min, int max) {}

    /** A connection edge of the exported assembly. {@code radius} is only used for PROXIMITY. */
    public record ConnSpec(String from, String to, ConnectionType type, int radius) {}

    /** Everything needed to emit an assembly definition. */
    public record AssemblyExportSpec(
            ResourceLocation id,
            List<RoleSpec> roles,
            List<ConnSpec> connections,
            Optional<String> masterRole,
            AssemblyBreakPolicy breakPolicy,
            Map<String, StatMerge> aggregateStats) {}

    // ---- Java ----

    public static String toJavaSource(AssemblyExportSpec spec) {
        String className = MultiblockDevExporter.javaClassName(spec.id().getPath()) + "Assembly";
        StringBuilder sb = new StringBuilder();
        sb.append("import net.astronomy.multilib.api.assembly.AssemblyBreakPolicy;\n");
        sb.append("import net.astronomy.multilib.api.assembly.AssemblyBuilder;\n");
        sb.append("import net.astronomy.multilib.api.assembly.ConnectionType;\n");
        sb.append("import net.astronomy.multilib.api.assembly.StatMerge;\n");
        sb.append("import net.minecraft.resources.ResourceLocation;\n\n");
        sb.append("public final class ").append(className).append(" {\n");
        sb.append("    public static void register() {\n");
        sb.append("        AssemblyBuilder.create(").append(javaRl(spec.id())).append(")\n");
        for (RoleSpec r : spec.roles()) {
            sb.append("            .role(\"").append(r.name()).append("\", ").append(javaRl(r.definition()))
              .append(", ").append(r.min()).append(", ").append(r.max()).append(")\n");
        }
        for (ConnSpec c : spec.connections()) {
            if (c.type() == ConnectionType.PROXIMITY) {
                sb.append("            .proximity(\"").append(c.from()).append("\", \"").append(c.to())
                  .append("\", ").append(c.radius()).append(")\n");
            } else {
                sb.append("            .connection(\"").append(c.from()).append("\", \"").append(c.to())
                  .append("\", ConnectionType.").append(c.type().name()).append(")\n");
            }
        }
        spec.masterRole().ifPresent(m -> sb.append("            .masterRole(\"").append(m).append("\")\n"));
        sb.append("            .breakPolicy(AssemblyBreakPolicy.").append(spec.breakPolicy().name()).append(")\n");
        spec.aggregateStats().forEach((k, v) ->
                sb.append("            .aggregateStat(\"").append(k).append("\", StatMerge.").append(v.name()).append(")\n"));
        sb.append("            .build();\n");
        sb.append("    }\n}\n");
        return sb.toString();
    }

    // ---- JSON ----

    public static String toJsonDefinition(AssemblyExportSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"type\": \"multilib:assembly\",\n");
        sb.append("  \"roles\": {\n");
        for (int i = 0; i < spec.roles().size(); i++) {
            RoleSpec r = spec.roles().get(i);
            sb.append("    \"").append(r.name()).append("\": { \"definition\": \"").append(r.definition())
              .append("\", \"min\": ").append(r.min()).append(", \"max\": ").append(r.max()).append(" }");
            sb.append(i < spec.roles().size() - 1 ? ",\n" : "\n");
        }
        sb.append("  },\n");
        sb.append("  \"connections\": [\n");
        for (int i = 0; i < spec.connections().size(); i++) {
            ConnSpec c = spec.connections().get(i);
            sb.append("    { \"from\": \"").append(c.from()).append("\", \"to\": \"").append(c.to())
              .append("\", \"type\": \"").append(c.type().name().toLowerCase()).append("\"");
            if (c.type() == ConnectionType.PROXIMITY) sb.append(", \"radius\": ").append(c.radius());
            sb.append(" }");
            sb.append(i < spec.connections().size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ],\n");
        spec.masterRole().ifPresent(m -> sb.append("  \"master_role\": \"").append(m).append("\",\n"));
        sb.append("  \"break_policy\": \"").append(spec.breakPolicy().name().toLowerCase()).append("\"");
        if (!spec.aggregateStats().isEmpty()) {
            sb.append(",\n  \"aggregate_stats\": {");
            int i = 0;
            for (Map.Entry<String, StatMerge> e : spec.aggregateStats().entrySet()) {
                sb.append(i++ > 0 ? ", " : " ");
                sb.append("\"").append(e.getKey()).append("\": \"").append(e.getValue().name().toLowerCase()).append("\"");
            }
            sb.append(" }\n");
        } else {
            sb.append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    // ---- KubeJS ----

    public static String toKubeJsScript(AssemblyExportSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("MultiblockEvents.assembly(event => {\n");
        sb.append("    event.assembly(\"").append(spec.id()).append("\")\n");
        for (RoleSpec r : spec.roles()) {
            sb.append("        .role(\"").append(r.name()).append("\", \"").append(r.definition())
              .append("\", ").append(r.min()).append(", ").append(r.max()).append(")\n");
        }
        for (ConnSpec c : spec.connections()) {
            if (c.type() == ConnectionType.PROXIMITY) {
                sb.append("        .proximity(\"").append(c.from()).append("\", \"").append(c.to())
                  .append("\", ").append(c.radius()).append(")\n");
            } else {
                sb.append("        .connection(\"").append(c.from()).append("\", \"").append(c.to())
                  .append("\", \"").append(c.type().name()).append("\")\n");
            }
        }
        spec.masterRole().ifPresent(m -> sb.append("        .masterRole(\"").append(m).append("\")\n"));
        sb.append("        .breakPolicy(\"").append(spec.breakPolicy().name()).append("\")\n");
        spec.aggregateStats().forEach((k, v) ->
                sb.append("        .aggregateStat(\"").append(k).append("\", \"").append(v.name()).append("\")\n"));
        sb.append("})\n");
        return sb.toString();
    }

    private static String javaRl(ResourceLocation rl) {
        return "ResourceLocation.fromNamespaceAndPath(\"" + rl.getNamespace() + "\", \"" + rl.getPath() + "\")";
    }
}
