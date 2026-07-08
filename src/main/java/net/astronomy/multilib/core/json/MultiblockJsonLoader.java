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
import net.astronomy.multilib.api.event.MultiblockDefinitionsReloadedEvent;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.event.MultiblockLoadErrorNotifier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;
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
        // A definition fixed since the last reload shouldn't keep flashing its old error at every
        // player who joins afterward - see MultiblockLoadErrorNotifier for the join-time delivery.
        MultiblockLoadErrorNotifier.clear();

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
        NeoForge.EVENT_BUS.post(new MultiblockDefinitionsReloadedEvent());
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
            for (String[] rows : parseLayers(obj.getAsJsonArray("layers"))) {
                builder.layer(rows);
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

        // variants: alternative geometries under the same id - each entry maps 1:1 onto
        // MultiblockBuilder#variant (the first entry becomes the primary geometry, later ones become
        // derived definitions tried in declaration order). Top-level "keys" stays the shared key map;
        // a variant's own "keys" adds/overrides for that variant only. Mutually exclusive with a
        // top-level "layers" field - the builder would throw too, but this earlier check gives the
        // datapack author a JSON-flavored message instead of the builder's own wording.
        if (obj.has("variants")) {
            if (obj.has("layers")) {
                throw new IllegalArgumentException(
                        "cannot declare both 'layers' and 'variants' - move all geometry into 'variants'");
            }
            JsonArray variantsArr = obj.getAsJsonArray("variants");
            if (variantsArr.isEmpty()) {
                throw new IllegalArgumentException("'variants' must contain at least one entry");
            }
            for (JsonElement variantElem : variantsArr) {
                if (!variantElem.isJsonObject()) {
                    throw new IllegalArgumentException("each 'variants' entry must be a JSON object");
                }
                JsonObject variantObj = variantElem.getAsJsonObject();
                if (!variantObj.has("name")) {
                    throw new IllegalArgumentException("a 'variants' entry is missing its 'name'");
                }
                String variantName = variantObj.get("name").getAsString();
                if (!variantObj.has("layers")) {
                    throw new IllegalArgumentException("variant '" + variantName + "' is missing its 'layers'");
                }
                java.util.List<String[]> variantLayers = parseLayers(variantObj.getAsJsonArray("layers"));
                if (variantLayers.isEmpty()) {
                    throw new IllegalArgumentException("variant '" + variantName + "' has empty 'layers'");
                }
                builder.variant(variantName, v -> {
                    for (String[] rows : variantLayers) {
                        v.layer(rows);
                    }
                    if (variantObj.has("keys")) {
                        JsonObject variantKeys = variantObj.getAsJsonObject("keys");
                        for (Map.Entry<String, JsonElement> kEntry : variantKeys.entrySet()) {
                            if (kEntry.getKey().length() != 1) continue;
                            char symbol = kEntry.getKey().charAt(0);
                            MultiblockCodecs.BLOCK_INGREDIENT_OBJECT.parse(ops, kEntry.getValue())
                                .resultOrPartial(err -> LOGGER.warn(
                                        "[MultiLib] Bad ingredient for variant '{}' symbol '{}': {}",
                                        variantName, symbol, err))
                                .ifPresent(ing -> v.key(symbol, ing));
                        }
                    }
                });
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

        // auto_place: lets players Ctrl+Right-click the core to place missing blocks from inventory
        if (obj.has("auto_place") && obj.get("auto_place").getAsBoolean()) {
            builder.autoPlace();
        }

        // auto_place_overlay: previews (ghost-overlay style) which missing positions the currently
        // held item could fill via auto_place. Only meaningful alongside auto_place.
        if (obj.has("auto_place_overlay") && obj.get("auto_place_overlay").getAsBoolean()) {
            builder.autoPlaceOverlay();
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

        // formed_property: flips a BooleanProperty of this name true/false on every member block as the
        // structure forms/breaks - see MultiblockBuilder#formedProperty for the same footgun warning
        // that applies here (don't match a pattern ingredient on the same property).
        if (obj.has("formed_property")) {
            builder.formedProperty(obj.get("formed_property").getAsString());
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

    /**
     * The shared "layers" shape, used identically by the top-level field and each "variants" entry:
     * an array of arrays of row strings (multiple layers), or - shorthand for a single-layer
     * structure - a flat array of row strings.
     */
    private static java.util.List<String[]> parseLayers(JsonArray layersArr) {
        java.util.List<String[]> layers = new java.util.ArrayList<>();
        if (layersArr.isEmpty()) return layers;
        if (layersArr.get(0).isJsonArray()) {
            for (JsonElement layerElem : layersArr) {
                JsonArray layerArr = layerElem.getAsJsonArray();
                String[] rows = new String[layerArr.size()];
                for (int i = 0; i < rows.length; i++) {
                    rows[i] = layerArr.get(i).getAsString();
                }
                layers.add(rows);
            }
        } else {
            String[] rows = new String[layersArr.size()];
            for (int i = 0; i < layersArr.size(); i++) {
                rows[i] = layersArr.get(i).getAsString();
            }
            layers.add(rows);
        }
        return layers;
    }
}
