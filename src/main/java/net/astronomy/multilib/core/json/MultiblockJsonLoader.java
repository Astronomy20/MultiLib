package net.astronomy.multilib.core.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.astronomy.multilib.api.definition.MultiblockBuilder;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.Map;

public class MultiblockJsonLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static final String FOLDER = "multiblocks";

    public MultiblockJsonLoader() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> dataMap, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        MultiblockRegistry.clearJsonDefinitions();

        int loaded = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : dataMap.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                MultiblockDefinition def = parseDefinition(id, entry.getValue());
                if (def != null) {
                    MultiblockRegistry.registerJson(def);
                    loaded++;
                }
            } catch (Exception e) {
                LOGGER.warn("[MultiLib] Failed to load multiblock definition {}: {}", id, e.getMessage());
            }
        }
        LOGGER.info("[MultiLib] Loaded {} multiblock definition(s) from datapacks", loaded);
    }

    private MultiblockDefinition parseDefinition(ResourceLocation id, JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Expected JSON object");
        }
        JsonObject obj = json.getAsJsonObject();
        var ops = JsonOps.INSTANCE;

        MultiblockBuilder builder = new MultiblockBuilder().id(id);

        // layers (shaped)
        if (obj.has("layers")) {
            JsonArray layersArr = obj.getAsJsonArray("layers");
            if (!layersArr.isEmpty()) {
                JsonElement firstElem = layersArr.get(0);
                if (firstElem.isJsonArray()) {
                    for (JsonElement layerElem : layersArr) {
                        JsonArray layerArr = layerElem.getAsJsonArray();
                        String[] rows = new String[layerArr.size()];
                        for (int i = 0; i < rows.length; i++) {
                            rows[i] = layerArr.get(i).getAsString();
                        }
                        builder.layers(rows);
                    }
                } else {
                    String[] rows = new String[layersArr.size()];
                    for (int i = 0; i < layersArr.size(); i++) {
                        rows[i] = layersArr.get(i).getAsString();
                    }
                    builder.layers(rows);
                }
            }
        }

        // keys
        if (obj.has("keys")) {
            JsonObject keysObj = obj.getAsJsonObject("keys");
            for (Map.Entry<String, JsonElement> entry : keysObj.entrySet()) {
                if (entry.getKey().length() != 1) continue;
                char symbol = entry.getKey().charAt(0);
                MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.parse(ops, entry.getValue())
                    .resultOrPartial(err -> LOGGER.warn("[MultiLib] Bad ingredient for symbol '{}': {}", symbol, err))
                    .ifPresent(ing -> builder.key(symbol, ing));
            }
        }

        // activation & core
        if (obj.has("activation")) {
            String act = obj.get("activation").getAsString();
            if (act.length() == 1) builder.activation(act.charAt(0));
        }
        if (obj.has("core")) {
            String core = obj.get("core").getAsString();
            if (core.length() == 1) builder.core(core.charAt(0));
        }

        // rotations
        if (obj.has("rotations")) {
            MultiblockCodecs.ROTATION_MODE.parse(ops, obj.get("rotations"))
                .resultOrPartial(err -> LOGGER.warn("[MultiLib] Bad rotations: {}", err))
                .ifPresent(builder::rotations);
        }

        // formation_mode
        if (obj.has("formation_mode")) {
            MultiblockCodecs.FORMATION_MODE.parse(ops, obj.get("formation_mode"))
                .resultOrPartial(err -> LOGGER.warn("[MultiLib] Bad formation_mode: {}", err))
                .ifPresent(builder::formationMode);
        }

        // name: bare key (e.g. "my_structure") → full translation key "multiblock.<namespace>.<name>"
        if (obj.has("name")) {
            builder.name(obj.get("name").getAsString());
        }

        // priority
        if (obj.has("priority")) {
            builder.priority(obj.get("priority").getAsInt());
        }

        // require_air_in_empty_positions
        if (obj.has("require_air_in_empty_positions") && obj.get("require_air_in_empty_positions").getAsBoolean()) {
            builder.requireAirInEmptyPositions();
        }

        // wall_sharing
        if (obj.has("wall_sharing")) {
            builder.wallSharing(obj.get("wall_sharing").getAsBoolean());
        }

        // no_wall_sharing
        if (obj.has("no_wall_sharing")) {
            JsonArray noWs = obj.getAsJsonArray("no_wall_sharing");
            char[] noWsChars = new char[noWs.size()];
            for (int i = 0; i < noWs.size(); i++) {
                String s = noWs.get(i).getAsString();
                if (s.length() == 1) noWsChars[i] = s.charAt(0);
            }
            builder.noWallSharing(noWsChars);
        }

        // optional symbols
        if (obj.has("optional")) {
            JsonArray optArr = obj.getAsJsonArray("optional");
            char[] optChars = new char[optArr.size()];
            for (int i = 0; i < optArr.size(); i++) {
                String s = optArr.get(i).getAsString();
                if (s.length() == 1) optChars[i] = s.charAt(0);
            }
            builder.optional(optChars);
        }

        // shapeless
        if (obj.has("shapeless") && obj.get("shapeless").getAsBoolean()) {
            builder.shapeless();
        }

        // min_size / max_size
        if (obj.has("min_size")) {
            JsonArray sz = obj.getAsJsonArray("min_size");
            if (sz.size() == 3) builder.minSize(sz.get(0).getAsInt(), sz.get(1).getAsInt(), sz.get(2).getAsInt());
        }
        if (obj.has("max_size")) {
            JsonArray sz = obj.getAsJsonArray("max_size");
            if (sz.size() == 3) builder.maxSize(sz.get(0).getAsInt(), sz.get(1).getAsInt(), sz.get(2).getAsInt());
        }

        // shell
        if (obj.has("shell")) {
            MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.parse(ops, obj.get("shell"))
                .resultOrPartial(err -> LOGGER.warn("[MultiLib] Bad shell: {}", err))
                .ifPresent(builder::shell);
        }

        // interior
        if (obj.has("interior")) {
            MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.parse(ops, obj.get("interior"))
                .resultOrPartial(err -> LOGGER.warn("[MultiLib] Bad interior: {}", err))
                .ifPresent(builder::interior);
        }

        // PatternProvider built-in (sphere, cylinder, etc.)
        if (obj.has("pattern")) {
            JsonObject patternObj = obj.getAsJsonObject("pattern");
            if (patternObj.has("type")) {
                MultiblockCodecs.PATTERN_PROVIDER.parse(ops, patternObj)
                    .resultOrPartial(err -> LOGGER.warn("[MultiLib] Bad pattern provider: {}", err))
                    .ifPresent(builder::pattern);
            }
        }

        // on_formed_sound
        if (obj.has("on_formed_sound")) {
            ResourceLocation soundId = ResourceLocation.tryParse(obj.get("on_formed_sound").getAsString());
            if (soundId != null) {
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(soundId).orElse(null);
                if (sound != null) {
                    builder.onFormed(ctx -> ctx.level().playSound(null, ctx.instance().getOrigin(),
                        sound, SoundSource.BLOCKS, 1.0F, 1.0F));
                }
            }
        }

        // icon
        if (obj.has("icon")) {
            ResourceLocation iconId = ResourceLocation.tryParse(obj.get("icon").getAsString());
            if (iconId != null) builder.icon(iconId);
        }

        // on_broken_sound
        if (obj.has("on_broken_sound")) {
            ResourceLocation soundId = ResourceLocation.tryParse(obj.get("on_broken_sound").getAsString());
            if (soundId != null) {
                SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(soundId).orElse(null);
                if (sound != null) {
                    builder.onBroken(ctx -> ctx.level().playSound(null, ctx.instance().getOrigin(),
                        sound, SoundSource.BLOCKS, 1.0F, 1.0F));
                }
            }
        }

        try {
            return builder.buildWithoutRegistering();
        } catch (IllegalStateException e) {
            LOGGER.warn("[MultiLib] Invalid definition {}: {}", id, e.getMessage());
            return null;
        }
    }
}
