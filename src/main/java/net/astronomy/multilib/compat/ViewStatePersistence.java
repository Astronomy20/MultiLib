package net.astronomy.multilib.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.astronomy.multilib.MultiLib;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Persists each multiblock's preview rotation/zoom/layer to disk so it survives a full game
 * restart, not just screen open/close within one session (the JEI/REI/EMI adapters' own
 * per-definition {@link MultiblockPreviewPanel.ViewState} maps already handle that in memory).
 * Backed by a small JSON file rather than {@code ModConfigSpec} (TOML), since the key set here is
 * dynamic (one entry per multiblock id, discovered at runtime) instead of a fixed schema.
 */
final class ViewStatePersistence {

    private ViewStatePersistence() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("multilib_preview_rotations.json");
    private static final Type MAP_TYPE = new TypeToken<Map<String, Saved>>() {}.getType();

    private record Saved(float yaw, float pitch, float zoom, Integer layer) {}

    private static final Map<String, Saved> CACHE = load();

    private static Map<String, Saved> load() {
        if (!Files.isRegularFile(FILE)) return new HashMap<>();
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Map<String, Saved> map = GSON.fromJson(r, MAP_TYPE);
            return map != null ? new HashMap<>(map) : new HashMap<>();
        } catch (IOException | JsonSyntaxException e) {
            MultiLib.LOGGER.warn("Failed to load multiblock preview rotation cache, starting fresh", e);
            return new HashMap<>();
        }
    }

    /** Applies a previously-saved rotation/zoom/layer for {@code id} onto a freshly-created state. */
    static void applySaved(ResourceLocation id, MultiblockPreviewPanel.ViewState vs) {
        Saved s = CACHE.get(id.toString());
        if (s == null) return;
        vs.yaw = s.yaw();
        vs.pitch = s.pitch();
        vs.zoom = s.zoom();
        vs.layer = s.layer();
    }

    /** Records the current rotation/zoom/layer for {@code id} and writes the cache to disk. */
    static synchronized void save(ResourceLocation id, MultiblockPreviewPanel.ViewState vs) {
        CACHE.put(id.toString(), new Saved(vs.yaw, vs.pitch, vs.zoom, vs.layer));
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(CACHE, w);
            }
        } catch (IOException e) {
            MultiLib.LOGGER.warn("Failed to save multiblock preview rotation cache", e);
        }
    }
}
