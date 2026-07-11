package net.astronomy.multilib.core.assembly;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.astronomy.multilib.api.assembly.AssemblyBreakPolicy;
import net.astronomy.multilib.api.assembly.AssemblyBuilder;
import net.astronomy.multilib.api.assembly.AssemblyDefinition;
import net.astronomy.multilib.api.assembly.ConnectionConstraint;
import net.astronomy.multilib.api.assembly.ConnectionType;
import net.astronomy.multilib.api.assembly.StatMerge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.Map;

/**
 * Datapack loader for {@code data/&lt;namespace&gt;/multilib_assemblies/*.json}. An assembly is pure data
 * (id references + constraints + policies), so it serializes in full — everything the Java builder
 * exposes except callbacks. Registered by {@link AssemblyReloadSetup}.
 */
public class AssemblyJsonLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String FOLDER = "multilib_assemblies";

    public AssemblyJsonLoader() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> dataMap, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        AssemblyRegistry.clearJsonDefinitions();
        int loaded = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : dataMap.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                AssemblyDefinition def = parse(id, entry.getValue().getAsJsonObject());
                AssemblyRegistry.registerJson(def);
                loaded++;
            } catch (Exception e) {
                LOGGER.warn("[MultiLib] Failed to load assembly definition {}: {}", id, e.getMessage());
            }
        }
        LOGGER.info("[MultiLib] Loaded {} assembly definition(s) from datapacks", loaded);
    }

    private static AssemblyDefinition parse(ResourceLocation id, JsonObject json) {
        AssemblyBuilder builder = AssemblyBuilder.create(id);

        JsonObject roles = json.getAsJsonObject("roles");
        if (roles == null) throw new IllegalArgumentException("assembly '" + id + "' has no 'roles'");
        for (Map.Entry<String, JsonElement> e : roles.entrySet()) {
            JsonObject role = e.getValue().getAsJsonObject();
            ResourceLocation defId = parseId(role.get("definition").getAsString(), "role '" + e.getKey() + "' definition");
            int min = role.has("min") ? role.get("min").getAsInt() : 1;
            int max = role.has("max") ? role.get("max").getAsInt() : Math.max(1, min);
            builder.role(e.getKey(), defId, min, max);
        }

        if (json.has("connections")) {
            JsonArray connections = json.getAsJsonArray("connections");
            for (JsonElement el : connections) {
                JsonObject c = el.getAsJsonObject();
                String from = c.get("from").getAsString();
                String to = c.get("to").getAsString();
                ConnectionType type = ConnectionType.valueOf(c.get("type").getAsString().toUpperCase());
                int radius = c.has("radius") ? c.get("radius").getAsInt() : 0;
                boolean required = !c.has("required") || c.get("required").getAsBoolean();
                builder.connection(new ConnectionConstraint(from, to, type, radius, required));
            }
        }

        if (json.has("master_role")) builder.masterRole(json.get("master_role").getAsString());
        if (json.has("break_policy")) {
            builder.breakPolicy(AssemblyBreakPolicy.valueOf(json.get("break_policy").getAsString().toUpperCase()));
        }
        if (json.has("priority")) builder.priority(json.get("priority").getAsInt());

        if (json.has("aggregate_stats")) {
            JsonObject stats = json.getAsJsonObject("aggregate_stats");
            for (Map.Entry<String, JsonElement> e : stats.entrySet()) {
                builder.aggregateStat(e.getKey(), StatMerge.valueOf(e.getValue().getAsString().toUpperCase()));
            }
        }

        // buildWithoutRegister so the loader controls registration (as a JSON source).
        return builder.buildWithoutRegister();
    }

    private static ResourceLocation parseId(String s, String what) {
        ResourceLocation rl = ResourceLocation.tryParse(s);
        if (rl == null) throw new IllegalArgumentException("invalid " + what + ": " + s);
        return rl;
    }
}
