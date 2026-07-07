package net.astronomy.multilib.core.devtool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.astronomy.multilib.CommonConfig;
import net.astronomy.multilib.MultiLib;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

/**
 * Resolves the on-disk destination for each of the three dev-block export formats, and the small
 * bit of filesystem scaffolding (parent directories, a generated datapack's {@code pack.mcmeta})
 * needed for the writes to succeed. Pure path/IO logic - no dependency on the scan result itself.
 */
public final class MultiblockDevOutputPaths {

    /** 1.21.1's data pack format, used only when auto-generating {@code pack.mcmeta} for the dev datapack. */
    private static final int PACK_FORMAT = 48;

    /** 1.21.1's resource pack format (different number from {@link #PACK_FORMAT} - data and resource packs version independently), used only when auto-generating {@code pack.mcmeta} for the dev resourcepack. */
    private static final int RESOURCE_PACK_FORMAT = 34;

    private static final Gson GSON = new GsonBuilder().create();

    private MultiblockDevOutputPaths() {}

    /**
     * {@code config/multilib/output/<ClassName>.java} by default, or under
     * {@link CommonConfig#DEVTOOL_JAVA_OUTPUT_DIR} if the developer has overridden it, using
     * {@link MultiblockDevExporter#javaClassName(String)} on the multiblock's path - the GUI's own
     * "Path" field, which is what actually tells two different multiblocks' files apart. The export
     * id's namespace half ({@link CommonConfig#DEVTOOL_NAMESPACE}) is fixed and shared by every export
     * instead (see {@code MultiblockDevPacketHandler#handleExportRequest}), so it wouldn't make a
     * meaningful file name.
     */
    public static Path javaOutputFile(String path) {
        String className = MultiblockDevExporter.javaClassName(path);
        return javaRootDir().resolve(className + ".java");
    }

    /** Base directory {@link #javaOutputFile} resolves a class file name under - also used by {@link MultiblockDevExportLoader} to list every Java export found. */
    public static Path javaRootDir() {
        return resolveConfiguredDir(CommonConfig.DEVTOOL_JAVA_OUTPUT_DIR.get(),
                FMLPaths.CONFIGDIR.get().resolve("multilib").resolve("output"));
    }

    /**
     * {@code <gamedir>/kubejs/server_scripts/<devtoolNamespace>/<path>.js} by default, or
     * under {@link CommonConfig#DEVTOOL_KUBEJS_OUTPUT_DIR} if the developer has overridden it. Named
     * after the path, same reasoning as {@link #javaOutputFile(String)}.
     */
    public static Path kubeJsOutputFile(String path) {
        return kubeJsRootDir().resolve(path + ".js");
    }

    /** Base directory {@link #kubeJsOutputFile} resolves a script file name under - also used by {@link MultiblockDevExportLoader} to list every KubeJS export found. */
    public static Path kubeJsRootDir() {
        return resolveConfiguredDir(CommonConfig.DEVTOOL_KUBEJS_OUTPUT_DIR.get(),
                FMLPaths.GAMEDIR.get().resolve("kubejs").resolve("server_scripts")
                        .resolve(CommonConfig.DEVTOOL_NAMESPACE.get()));
    }

    /**
     * {@code <gamedir>/kubejs} - the *pack root* {@link #langFile} resolves its own {@code assets/}
     * subfolder under, same shape as {@link #javaRootDir()}/{@code jsonRootDir}: KubeJS's actual
     * resourcepack-style assets tree lives at {@code kubejs/assets/<namespace>/...}, one level below this,
     * NOT under {@code server_scripts/<devtoolNamespace>} like the generated script itself - KubeJS reads
     * lang/textures/etc. from that one fixed location regardless of which server_scripts subfolder a
     * script lives in, so a lang file written under {@link #kubeJsRootDir()} instead would silently
     * never be picked up. Returning {@code kubejs} itself here (not {@code kubejs/assets}) is
     * deliberate - {@link #langFile} always appends its own {@code assets/<namespace>/lang/...} suffix,
     * so passing the already-built {@code assets} folder here previously produced a doubled
     * {@code kubejs/assets/assets/<namespace>/...} path that KubeJS never reads.
     */
    public static Path kubeJsAssetsRootDir() {
        return FMLPaths.GAMEDIR.get().resolve("kubejs");
    }

    /** Resolves a config-provided output directory (relative to the game dir, or absolute), or {@code fallback} if blank. */
    private static Path resolveConfiguredDir(String configured, Path fallback) {
        if (configured == null || configured.isBlank()) {
            return fallback;
        }
        Path configuredPath = Path.of(configured);
        return configuredPath.isAbsolute() ? configuredPath : FMLPaths.GAMEDIR.get().resolve(configuredPath);
    }

    /** Result of resolving the JSON export destination: the file path, and which of the two branches applied. */
    public record JsonOutputResult(Path path, boolean isDevSource) {}

    /**
     * Resolves the JSON export destination: always a datapack under the current world's own
     * {@code datapacks} folder -
     * {@code <world folder>/datapacks/<devtoolNamespace>/data/<namespace>/multiblocks/<path>.json} -
     * exactly like a real player testing the export would see it (the scaffold, i.e. {@code pack.mcmeta},
     * is created alongside if it doesn't exist yet - see {@link #ensureWorldDatapackScaffold}), unless
     * {@link CommonConfig#DEVTOOL_JSON_OUTPUT_DIR} is explicitly set, in which case that directory is used
     * as the base instead (still {@code isDevSource=true} in that case, since an explicit override is
     * exactly the "point it at my own source tree" escape hatch this config exists for).
     * <p>
     * {@code namespace} here is {@link CommonConfig#DEVTOOL_NAMESPACE} (fixed, shared by every export),
     * {@code path} is the GUI's own "Path" field (what tells two different multiblocks apart) - see
     * {@code MultiblockDevPacketHandler#handleExportRequest}. This is exactly vanilla's own usual
     * datapack shape - one shared {@code data/<namespace>/} folder, many path-named files inside
     * {@code multiblocks/} - unlike Java/KubeJS's own flat, path-named-file-per-export layout.
     * <p>
     * Note the plural "multiblocks" folder name - that's what
     * {@link net.astronomy.multilib.core.json.MultiblockJsonLoader} actually reads from; a singular
     * "multiblock" folder would silently never be loaded.
     */
    public static JsonOutputResult jsonOutputFile(MinecraftServer server, String namespace, String path) {
        String configured = CommonConfig.DEVTOOL_JSON_OUTPUT_DIR.get();
        boolean isDevSource = configured != null && !configured.isBlank();
        Path base = jsonRootDir(server);
        Path file = base.resolve("data").resolve(namespace).resolve("multiblocks").resolve(path + ".json");
        return new JsonOutputResult(file, isDevSource);
    }

    /**
     * The datapack root JSON multiblocks are written under/read from - either
     * {@link CommonConfig#DEVTOOL_JSON_OUTPUT_DIR} if set, or the current world's own
     * {@code datapacks/<devtoolNamespace>} folder otherwise. Shared by {@link #jsonOutputFile}
     * (writing one multiblock) and {@link MultiblockDevExportLoader} (listing/reading them all back for
     * the dev-block GUI's Load tab).
     */
    public static Path jsonRootDir(MinecraftServer server) {
        String configured = CommonConfig.DEVTOOL_JSON_OUTPUT_DIR.get();
        if (configured != null && !configured.isBlank()) {
            return resolveConfiguredDir(configured, null);
        }
        return server.getWorldPath(LevelResource.ROOT).resolve("datapacks").resolve(CommonConfig.DEVTOOL_NAMESPACE.get());
    }

    /** First-line marker {@link MultiblockDevExporter} embeds in every Java/KubeJS export, used only to detect a path/file collision - see {@link #readExistingExportId}. */
    static final String EXPORT_ID_MARKER_PREFIX = "// multilib-export-id: ";

    /**
     * Reads back the {@code namespace:path} id an already-written export was last written for, or
     * {@link Optional#empty()} if the file doesn't exist or wasn't recognized as one of this
     * exporter's own files. Every format's output file name is derived from the path alone (see
     * {@link #javaOutputFile}/{@link #kubeJsOutputFile}/{@link #jsonOutputFile}), so two different
     * multiblocks sharing a path (or the same path re-exported after {@link CommonConfig#DEVTOOL_NAMESPACE}
     * itself changed) would otherwise silently overwrite each other's file - this lets the caller detect
     * that case and refuse the export instead.
     * <p>
     * Java/KubeJS embed the id as a first-line comment ({@link #EXPORT_ID_MARKER_PREFIX}); JSON has no
     * comment syntax, so it's read from the top-level {@code "id"} property instead.
     */
    public static Optional<String> readExistingExportId(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        String fileName = file.getFileName().toString();
        if (fileName.endsWith(".json")) {
            return readJsonExportId(file);
        }
        try {
            String firstLine = Files.lines(file, StandardCharsets.UTF_8).findFirst().orElse("");
            if (firstLine.startsWith(EXPORT_ID_MARKER_PREFIX)) {
                return Optional.of(firstLine.substring(EXPORT_ID_MARKER_PREFIX.length()).trim());
            }
        } catch (IOException ignored) {
            // Unreadable file - treat as "no known owner" rather than blocking the export on an
            // unrelated I/O problem; writeAndRespond's own try/catch surfaces real write failures.
        }
        return Optional.empty();
    }

    private static Optional<String> readJsonExportId(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = GSON.fromJson(reader, JsonElement.class);
            if (element != null && element.isJsonObject() && element.getAsJsonObject().has("id")) {
                return Optional.of(element.getAsJsonObject().get("id").getAsString());
            }
        } catch (IOException | JsonParseException ignored) {
            // Unreadable/malformed file - treat as "no known owner", same reasoning as the Java/KubeJS branch.
        }
        return Optional.empty();
    }

    /** Creates the parent directories of {@code path}, if they don't already exist. */
    public static void ensureParentDirs(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            Files.createDirectories(parent);
        }
    }

    /**
     * {@code <rootDir>/assets/<namespace>/lang/en_us.json} - mirrors a real resourcepack's own
     * {@code assets/<namespace>/lang/<locale>.json} layout exactly, so if this ever gets copied/symlinked
     * into an actual resourcepack (or {@code rootDir} already lives inside one), no further reorganizing
     * is needed. {@code rootDir} is {@link #javaRootDir()} for a Java export, {@link #jsonRootDir} for a
     * JSON export, or {@link #kubeJsAssetsRootDir()} for a KubeJS export - see
     * {@code MultiblockDevPacketHandler}'s own export methods for which is used where.
     */
    public static Path langFile(Path rootDir, String namespace) {
        return rootDir.resolve("assets").resolve(namespace).resolve("lang").resolve("en_us.json");
    }

    /**
     * Reads a single {@code key}'s value back out of {@code langFile} (see {@link #mergeLangEntry}), or
     * {@link Optional#empty()} if the file doesn't exist, is unreadable, or has no such key - used by
     * {@link MultiblockDevExportLoader} to recover a multiblock's Display Name on Load, since (unlike
     * before) the export itself no longer carries that text anywhere - only the translation key, whose
     * value lives exclusively in this file.
     */
    public static Optional<String> readLangValue(Path langFile, String key) {
        if (!Files.isRegularFile(langFile)) return Optional.empty();
        try (Reader reader = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
            JsonElement element = GSON.fromJson(reader, JsonElement.class);
            if (element != null && element.isJsonObject() && element.getAsJsonObject().has(key)) {
                return Optional.of(element.getAsJsonObject().get(key).getAsString());
            }
        } catch (IOException | JsonParseException ignored) {
            // Unreadable/malformed file - treat as "no known value", same reasoning as readExistingExportId.
        }
        return Optional.empty();
    }

    /**
     * Adds/overwrites a single {@code key -> value} entry in {@code langFile}, merging with whatever's
     * already there (creating the file fresh if it doesn't exist yet) - so exporting a second multiblock
     * under the same namespace doesn't clobber the first one's lang entry, the way a plain overwrite
     * would. Writes the player-visible Display Name text a {@code .name(...)} translation key resolves
     * to, since that call only ever sets the key itself, never the actual text (see
     * {@code MultiblockDevExporter#resolveDisplayText}).
     */
    public static void mergeLangEntry(Path langFile, String key, String value) throws IOException {
        JsonObject obj = null;
        if (Files.isRegularFile(langFile)) {
            try (Reader reader = Files.newBufferedReader(langFile, StandardCharsets.UTF_8)) {
                JsonElement existing = GSON.fromJson(reader, JsonElement.class);
                if (existing != null && existing.isJsonObject()) {
                    obj = existing.getAsJsonObject();
                }
            } catch (IOException | JsonParseException ignored) {
                // Malformed/unreadable existing file - fall through and start a fresh one below rather
                // than failing the whole export over a lang-file merge that's secondary to it.
            }
        }
        if (obj == null) {
            obj = new JsonObject();
        }
        obj.addProperty(key, value);
        ensureParentDirs(langFile);
        Files.writeString(langFile, new GsonBuilder().setPrettyPrinting().create().toJson(obj), StandardCharsets.UTF_8);
    }

    /**
     * {@code <outFile without its extension>.lang.json}, next to the export's own main output file - a
     * single-entry, standalone lang snippet a developer can open and copy-paste straight into their own
     * mod's real {@code assets/<theirmodid>/lang/en_us.json} (or a datapack's own companion resourcepack),
     * rather than having to dig through {@link #mergeLangEntry}'s shared, ever-growing dev-tool lang file
     * for just the one entry they actually want. Every export format gets one of these, including JSON -
     * a datapack alone can never carry a translation on its own (vanilla has no server-side/datapack lang
     * mechanism at all - lang is exclusively resourcepack, client-side content), so this is the easiest
     * path to actually giving a JSON-exported multiblock a display name in a real mod/pack.
     */
    public static Path langSnippetFile(Path outFile) {
        String fileName = outFile.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        return outFile.resolveSibling(stem + ".lang.json");
    }

    /** Writes {@link #langSnippetFile}'s content - always a fresh single-entry object, never merged with anything already there (each export's own snippet is self-contained, not cumulative like {@link #mergeLangEntry}'s shared file). */
    public static void writeLangSnippet(Path outFile, String key, String value) throws IOException {
        JsonObject obj = new JsonObject();
        obj.addProperty(key, value);
        Path snippetFile = langSnippetFile(outFile);
        ensureParentDirs(snippetFile);
        Files.writeString(snippetFile, new GsonBuilder().setPrettyPrinting().create().toJson(obj), StandardCharsets.UTF_8);
    }

    /**
     * {@code <gamedir>/resourcepacks/<devtoolNamespace>} - a real client resourcepack root, unlike
     * {@code jsonRootDir}'s own {@code assets/} subfolder, which lives inside the world's *datapack* and
     * is never scanned as a resourcepack source at all client-side. Only the JSON export writes its lang
     * entry here - it's the one format actually meant to be tested live in the current world/session, so
     * it's worth being a real, loadable resourcepack once the developer enables it (or reloads resources)
     * themselves; this dev tool no longer forces that reload automatically (see
     * {@code ClientPacketHandler#handleDevExportResult}'s own comment for why). The Java export
     * deliberately does NOT share this folder - see {@code MultiblockDevPacketHandler#exportJava}'s own
     * comment for why keeping the two separate matters (re-exporting the same {@code namespace:path} as
     * both formats would otherwise silently clobber whichever wrote its lang entry here last). Global
     * (gamedir-level, not per-world), matching vanilla's own {@code resourcepacks/} folder shape - a
     * translation isn't really world-specific data the way a scanned structure's blocks are.
     */
    public static Path devtoolResourcePackRootDir() {
        return FMLPaths.GAMEDIR.get().resolve("resourcepacks").resolve(CommonConfig.DEVTOOL_NAMESPACE.get());
    }

    /**
     * Creates {@code <gamedir>/resourcepacks/<devtoolNamespace>/pack.mcmeta} if it doesn't exist yet, with
     * a minimal description - the client-side counterpart to {@link #ensureWorldDatapackScaffold}, needed
     * for the same reason (a loose resourcepack folder needs a {@code pack.mcmeta} to be recognized as a
     * pack at all). Leaves an existing one untouched.
     */
    public static void ensureDevtoolResourcePackScaffold() throws IOException {
        Path root = devtoolResourcePackRootDir();
        Path packMcmeta = root.resolve("pack.mcmeta");
        if (Files.exists(packMcmeta)) {
            return;
        }

        Files.createDirectories(root);
        String json = "{\n"
                + "  \"pack\": {\n"
                + "    \"pack_format\": " + RESOURCE_PACK_FORMAT + ",\n"
                + "    \"description\": \"MultiLib dev exports\"\n"
                + "  }\n"
                + "}\n";
        Files.writeString(packMcmeta, json, StandardCharsets.UTF_8);
    }

    /**
     * Only relevant on the {@code isDevSource=false} branch: creates
     * {@code <world>/datapacks/<devtoolNamespace>/pack.mcmeta} if it doesn't exist yet, with a
     * minimal description. Leaves an existing datapack untouched.
     */
    public static void ensureWorldDatapackScaffold(MinecraftServer server) throws IOException {
        Path datapackRoot = server.getWorldPath(LevelResource.ROOT).resolve("datapacks").resolve(CommonConfig.DEVTOOL_NAMESPACE.get());
        Path packMcmeta = datapackRoot.resolve("pack.mcmeta");
        if (Files.exists(packMcmeta)) {
            return;
        }

        Files.createDirectories(datapackRoot);
        String json = "{\n"
                + "  \"pack\": {\n"
                + "    \"pack_format\": " + PACK_FORMAT + ",\n"
                + "    \"description\": \"MultiLib dev exports\"\n"
                + "  }\n"
                + "}\n";
        Files.writeString(packMcmeta, json, StandardCharsets.UTF_8);
    }

    /**
     * Writing the JSON file to disk isn't enough on its own: {@code data/<namespace>/multiblocks/*.json}
     * is only ever read by {@code MultiblockJsonLoader} through the currently *active* resource stack, and
     * a brand-new folder under {@code <world>/datapacks/} is never automatically part of that stack for an
     * already-running world - it's merely "available", not "selected", until something enables it (the
     * usual path is a player manually running {@code /reload} after {@code /datapack enable "file/..."});
     * a loose/uncompressed folder datapack is fine on its own (no zip or extra pack.mcmeta needed beyond
     * what {@link #ensureWorldDatapackScaffold} already writes); the missing piece was activation, not
     * packaging. This replicates vanilla's own {@code /reload} command (see
     * {@code net.minecraft.server.commands.ReloadCommand}) - reload the repository, keep every already
     * selected id plus every available id the world config hasn't explicitly disabled (which always
     * includes a first-time export's id, since nothing has ever disabled it), then hand that whole set to
     * {@link MinecraftServer#reloadResources} - so the freshly written export is live immediately, with no
     * manual {@code /reload} or {@code /datapack enable} required, and the enabled-pack list persists into
     * {@code level.dat} the same way {@code /reload} itself does (via {@code reloadResources} calling
     * {@code worldData.setDataConfiguration(...)} internally).
     */
    public static void enableAndReloadWorldDatapack(MinecraftServer server) {
        PackRepository packRepository = server.getPackRepository();
        WorldData worldData = server.getWorldData();
        Collection<String> selected = packRepository.getSelectedIds();
        packRepository.reload();

        Collection<String> newSelected = new ArrayList<>(selected);
        Collection<String> disabled = worldData.getDataConfiguration().dataPacks().getDisabled();
        for (String id : packRepository.getAvailableIds()) {
            if (!disabled.contains(id) && !newSelected.contains(id)) {
                newSelected.add(id);
            }
        }

        server.reloadResources(newSelected).exceptionally(t -> {
            MultiLib.LOGGER.error("[MultiLib] Dev block: failed to reload datapacks after a JSON export", t);
            return null;
        });
    }
}
