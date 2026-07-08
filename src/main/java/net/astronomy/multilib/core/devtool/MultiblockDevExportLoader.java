package net.astronomy.multilib.core.devtool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.astronomy.multilib.CommonConfig;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reverse of {@link MultiblockDevExporter} - lists and re-parses exports the dev block has already
 * written, in any of its three formats, for the GUI's Load tab. JSON is parsed directly (it's already
 * structured data); Java/KubeJS are parsed with small line-oriented regexes matching exactly what
 * {@link MultiblockDevExporter#toJavaSource}/{@code #toKubeJsScript} generate - not a general
 * Java/JS parser, just enough to read back the specific {@code .layer(...)}/{@code .key(...)}/
 * {@code .core(...)}/{@code .activation(...)} calls those two methods emit, one per line, in a
 * fixed and known shape.
 * <p>
 * Deliberately its own parser rather than reusing {@link net.astronomy.multilib.core.json.MultiblockJsonLoader}
 * for the JSON case: that class builds a full {@link net.astronomy.multilib.api.definition.MultiblockDefinition}
 * (validators, callbacks, shell, etc.) via a {@code MultiblockBuilder}/{@code HolderLookup} pipeline the
 * dev tool has no use for - all the Load tab needs back is the same static geometry
 * {@link MultiblockScanResult} already holds (layers, keys, core/activation), which is all the
 * dev-block export schema ever writes in the first place.
 */
public final class MultiblockDevExportLoader {

    private static final Gson GSON = new GsonBuilder().create();

    private MultiblockDevExportLoader() {}

    public enum SourceFormat { JAVA, JSON, KUBEJS }

    /**
     * One export found under the configured/default output roots, for the Load tab's list.
     * {@code variantNames} is empty for a plain single-geometry multiblock (the common case - clicking
     * loads it directly); non-empty when it was declared with one or more explicit
     * {@code .variant(...)}/{@code "variants"} entries, in declaration order - the Load tab then expands
     * a sub-list of these names instead of loading immediately, so the developer picks which one.
     */
    public record LoadableMultiblock(SourceFormat format, String namespace, String path, String displayName,
                                      List<String> variantNames) {}

    /** A loaded export's full identity plus its reconstructed scan data. {@code variantName} is empty unless a specific named variant was requested/found. */
    public record LoadedMultiblock(String namespace, String path, String displayName, String variantName, MultiblockScanResult scan) {}

    // ---- Listing ----

    /**
     * Every multiblock the Load tab can offer: this dev tool's own file exports, across all three
     * formats, PLUS every multiblock currently *registered* - hardcoded Java from any mod, JSON
     * datapacks, KubeJS alike (see {@link #listFromRegistry}) - not just this tool's own output. The two
     * sources overlap whenever a dev-tool export is also currently loaded/registered (the common case);
     * registry entries win that overlap (keyed by {@code namespace:path}), since they reflect the live,
     * currently-running definition rather than whatever was last written to disk. Malformed/unrecognized
     * files are skipped rather than failing the whole listing - one bad export shouldn't hide every
     * other one.
     */
    public static List<LoadableMultiblock> list(MinecraftServer server) {
        List<LoadableMultiblock> fileBased = new ArrayList<>();
        listJson(server, fileBased);
        listFlat(MultiblockDevOutputPaths.javaRootDir(), MultiblockDevOutputPaths.javaRootDir(), ".java", SourceFormat.JAVA, fileBased);
        listFlat(MultiblockDevOutputPaths.kubeJsRootDir(), MultiblockDevOutputPaths.kubeJsAssetsRootDir(), ".js", SourceFormat.KUBEJS, fileBased);

        LinkedHashMap<String, LoadableMultiblock> byId = new LinkedHashMap<>();
        for (LoadableMultiblock entry : fileBased) {
            byId.put(entry.namespace() + ":" + entry.path(), entry);
        }
        for (LoadableMultiblock entry : listFromRegistry()) {
            byId.put(entry.namespace() + ":" + entry.path(), entry);
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * Every multiblock currently registered in {@link MultiblockRegistry}, regardless of which mod or
     * mechanism registered it - a live {@link MultiblockDefinition} already holds its own full geometry
     * in memory (layers, symbol->block map, core/activation), so this needs no file access at all, unlike
     * the dev tool's own exports. Skips shapeless/{@code PatternProvider}-based definitions - same
     * restriction {@link net.astronomy.multilib.core.structure.MultiblockStructureExporter} already
     * applies, since neither can be represented as a static layer grid.
     */
    private static List<LoadableMultiblock> listFromRegistry() {
        List<LoadableMultiblock> result = new ArrayList<>();
        for (MultiblockDefinition def : MultiblockRegistry.getAll()) {
            if (def.isShapeless() || def.getPatternProvider().isPresent() || def.getLayers().isEmpty()) continue;
            MultiblockRegistry.Source source = MultiblockRegistry.getSource(def.getId()).orElse(MultiblockRegistry.Source.JAVA);
            SourceFormat format = switch (source) {
                case JAVA -> SourceFormat.JAVA;
                case JSON -> SourceFormat.JSON;
                case KUBEJS -> SourceFormat.KUBEJS;
            };
            // Only a definition actually built through .variant(...) carries a meaningful name list - the
            // legacy no-variant path's getAllVariants() is always just [this] under the implicit "default"
            // name, not worth surfacing as a pickable variant. A lone declared variant (no derived
            // siblings) is still detected here via its own name not being the implicit "default" literal.
            boolean hasExplicitVariants = !def.getVariantDefinitions().isEmpty() || !"default".equals(def.getVariantName());
            List<String> variantNames = hasExplicitVariants
                    ? def.getAllVariants().stream().map(MultiblockDefinition::getVariantName).toList()
                    : List.of();
            result.add(new LoadableMultiblock(format, def.getId().getNamespace(), def.getId().getPath(), resolveDisplayName(def), variantNames));
        }
        return result;
    }

    /**
     * Best-effort Display Name text for a registered {@link MultiblockDefinition} - resolves its
     * auto-derived translation key against whatever mod jar's own bundled
     * {@code assets/<namespace>/lang/en_us.json} happens to be on the classpath (every mod's resources
     * are on the same classloader at runtime, so this works for hardcoded Java multiblocks from any mod,
     * not just this one), falling back to the raw translation key text, then the bare path, if nothing
     * resolves. Not a full localization pipeline (always {@code en_us}, no resourcepack overrides
     * considered) - good enough for the Load tab's own display purposes.
     */
    private static String resolveDisplayName(MultiblockDefinition def) {
        // Despite the name, MultiblockDefinition#getNameTranslationKey() already returns the FULL
        // resolved key ("multiblock." + namespace + "." + bare name) - MultiblockBuilder#build()/
        // buildWithoutRegistering() both do that prefixing themselves before constructing the
        // definition, storing only the final string. Re-prefixing it again here (as an earlier version
        // of this method did) produced a doubled "multiblock.<namespace>.multiblock.<namespace>.<name>".
        Optional<String> key = def.getNameTranslationKey();
        if (key.isEmpty()) return def.getId().getPath();
        return readClasspathLangValue(def.getId().getNamespace(), key.get()).orElse(key.get());
    }

    /** Reads a single lang key's value out of {@code assets/<namespace>/lang/en_us.json} on the classpath (any mod's own bundled resources), or {@link Optional#empty()} if that resource doesn't exist or doesn't contain the key. */
    private static Optional<String> readClasspathLangValue(String namespace, String key) {
        String resourcePath = "assets/" + namespace + "/lang/en_us.json";
        try (InputStream in = MultiblockDevExportLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) return Optional.empty();
            JsonElement element = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonElement.class);
            if (element != null && element.isJsonObject() && element.getAsJsonObject().has(key)) {
                return Optional.of(element.getAsJsonObject().get(key).getAsString());
            }
        } catch (IOException | JsonParseException ignored) {
            // Unreadable/malformed resource - treat as "no known value", same reasoning as readLangValue.
        }
        return Optional.empty();
    }

    /**
     * Walks every {@code data/<namespace>/multiblocks/**}{@code .json} file - normally just one shared
     * namespace folder ({@link CommonConfig#DEVTOOL_NAMESPACE}, fixed), holding many path-named files, but
     * this still walks every namespace folder found rather than assuming exactly one, so exports written
     * while {@link CommonConfig#DEVTOOL_NAMESPACE} held a different value still show up correctly (the
     * file's own name doesn't matter here, only its {@code "id"} content, or the folder/file names as a
     * fallback).
     */
    private static void listJson(MinecraftServer server, List<LoadableMultiblock> result) {
        Path dataDir = MultiblockDevOutputPaths.jsonRootDir(server).resolve("data");
        if (!Files.isDirectory(dataDir)) return;

        try (var namespaces = Files.list(dataDir)) {
            for (Path namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                String folderNamespace = namespaceDir.getFileName().toString();
                Path multiblocksDir = namespaceDir.resolve("multiblocks");
                if (!Files.isDirectory(multiblocksDir)) continue;

                try (var files = Files.walk(multiblocksDir)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                        String fileStem = relativePathId(multiblocksDir, file);
                        JsonObject obj = readJsonObject(file);
                        if (obj == null) continue;

                        String[] idParts = splitId(obj.has("id") ? obj.get("id").getAsString() : null, folderNamespace, fileStem);
                        String displayName = readDisplayName(
                                MultiblockDevOutputPaths.devtoolResourcePackRootDir(), idParts[0], idParts[1]);
                        result.add(new LoadableMultiblock(SourceFormat.JSON, idParts[0], idParts[1], displayName, jsonVariantNames(obj)));
                    }
                } catch (IOException e) {
                    // Skip this namespace's multiblocks on a read failure - other namespaces still list fine.
                }
            }
        } catch (IOException e) {
            // No readable output root yet (nothing exported so far) - an empty list is the correct result.
        }
    }

    /** Ordered variant names declared in a raw dev-tool/datapack JSON's top-level {@code "variants"} array, or empty if absent. */
    private static List<String> jsonVariantNames(JsonObject obj) {
        if (!obj.has("variants") || !obj.get("variants").isJsonArray()) return List.of();
        List<String> names = new ArrayList<>();
        for (var variantElem : obj.getAsJsonArray("variants")) {
            if (!variantElem.isJsonObject() || !variantElem.getAsJsonObject().has("name")) continue;
            names.add(variantElem.getAsJsonObject().get("name").getAsString());
        }
        return names;
    }

    /** {@code multiblocksDir/foo/bar.json -> "foo/bar"}. */
    private static String relativePathId(Path multiblocksDir, Path file) {
        String relative = multiblocksDir.relativize(file).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - ".json".length());
    }

    /** Java/KubeJS share the same flat-directory, marker-driven listing - only the extension and the lang root (see {@link #readDisplayName}) differ. */
    private static void listFlat(Path rootDir, Path langRootDir, String extension, SourceFormat format, List<LoadableMultiblock> result) {
        if (!Files.isDirectory(rootDir)) return;
        try (var files = Files.list(rootDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(extension)).toList()) {
                List<String> lines = readLines(file);
                if (lines == null) continue;
                String[] id = splitId(readMarkerId(lines).orElse(null), null, null);
                if (id[0] == null) continue; // not one of our own exports (or the marker's missing) - skip silently
                String displayName = readDisplayName(langRootDir, id[0], id[1]);
                List<String> variantNames = findVariantName(lines, format == SourceFormat.JAVA)
                        .map(List::of).orElse(List.of());
                result.add(new LoadableMultiblock(format, id[0], id[1], displayName, variantNames));
            }
        } catch (IOException e) {
            // No readable output root yet - an empty list is the correct result.
        }
    }

    /**
     * The Display Name text for {@code namespace:path}, read back from the lang file the export itself
     * wrote to (see {@code MultiblockDevOutputPaths#mergeLangEntry}) - falls back to the path when
     * there's no such entry (lang write failed, or the file was hand-edited/deleted since) - path, not
     * namespace, since path is the per-multiblock unique GUI field and namespace is the same fixed value
     * for every export. The translation key is always literally {@code multiblock.<namespace>.<path>} -
     * this is display text only, never part of the key.
     */
    private static String readDisplayName(Path langRootDir, String namespace, String path) {
        Path langFile = MultiblockDevOutputPaths.langFile(langRootDir, namespace);
        String key = "multiblock." + namespace + "." + path;
        return MultiblockDevOutputPaths.readLangValue(langFile, key).orElse(path);
    }

    // ---- Loading ----

    /**
     * Re-reads one specific multiblock's full geometry, by the same {@code format}/{@code namespace}/
     * {@code path} {@link #list} reported. Tries the live {@link MultiblockRegistry} first (see
     * {@link #loadFromRegistry}) - covers hardcoded Java from any mod, JSON datapacks, and KubeJS alike,
     * with no file access needed at all - and only falls back to this dev tool's own file-export
     * conventions if nothing's currently registered under that exact id. That file fallback only ever
     * makes sense for {@code namespace == } {@link CommonConfig#DEVTOOL_NAMESPACE} (every one of this
     * tool's own exports always uses that fixed namespace - see
     * {@code MultiblockDevPacketHandler#handleExportRequest}) - a foreign namespace that isn't currently
     * registered can't be found any other way, so this returns empty for it rather than incorrectly
     * searching this tool's own namespace folder regardless of what was actually asked for.
     * <p>
     * {@code requestedVariant} picks which of the target's variants to load (see
     * {@link LoadableMultiblock#variantNames()}) - blank loads the primary/only geometry. An unknown name
     * falls back to the primary geometry rather than failing the whole load.
     */
    public static Optional<LoadedMultiblock> load(MinecraftServer server, SourceFormat format, String namespace, String path, String requestedVariant) {
        Optional<LoadedMultiblock> fromRegistry = loadFromRegistry(ResourceLocation.fromNamespaceAndPath(namespace, path), requestedVariant);
        if (fromRegistry.isPresent()) return fromRegistry;

        if (!namespace.equals(CommonConfig.DEVTOOL_NAMESPACE.get())) {
            return Optional.empty();
        }
        return switch (format) {
            case JSON -> loadJson(server, path, requestedVariant);
            case JAVA -> loadScaffold(MultiblockDevOutputPaths.javaOutputFile(path), MultiblockDevOutputPaths.javaRootDir(), format);
            case KUBEJS -> loadScaffold(MultiblockDevOutputPaths.kubeJsOutputFile(path), MultiblockDevOutputPaths.kubeJsAssetsRootDir(), format);
        };
    }

    /**
     * Reconstructs a {@link LoadedMultiblock} directly from {@code id}'s live {@link MultiblockDefinition}
     * in {@link MultiblockRegistry}, if one is currently registered - its layers/symbol-block map/core/
     * activation are already fully known in memory, so unlike every other loading path here, this needs
     * no file I/O at all. Skips shapeless/{@code PatternProvider}-based definitions, same restriction as
     * {@link #listFromRegistry}. {@code requestedVariant} selects which entry of
     * {@link MultiblockDefinition#getAllVariants()} to read geometry from - each variant is a full
     * {@link MultiblockDefinition} in its own right, so this just picks the right one via
     * {@link MultiblockDefinition#getVariant(String)} before extracting exactly as before.
     */
    private static Optional<LoadedMultiblock> loadFromRegistry(ResourceLocation id, String requestedVariant) {
        return MultiblockRegistry.get(id).flatMap(parent -> {
            MultiblockDefinition def = (requestedVariant == null || requestedVariant.isBlank())
                    ? parent : parent.getVariant(requestedVariant).orElse(parent);
            if (def.isShapeless() || def.getPatternProvider().isPresent() || def.getLayers().isEmpty()) {
                return Optional.empty();
            }
            LinkedHashMap<Character, ResourceLocation> symbolToBlock = new LinkedHashMap<>();
            for (Map.Entry<Character, net.astronomy.multilib.api.ingredient.BlockIngredient> entry : def.getBlockMap().entrySet()) {
                var candidates = entry.getValue().getCandidateBlocks();
                if (candidates.isEmpty()) continue;
                Block block = candidates.iterator().next();
                symbolToBlock.put(entry.getKey(), BuiltInRegistries.BLOCK.getKey(block));
            }
            Character core = def.hasCore() ? def.getCoreSymbol() : null;
            Character activation = def.hasActivation() ? def.getActivationSymbol() : null;
            MultiblockScanResult scan = new MultiblockScanResult(def.getLayers(), symbolToBlock, core, activation);
            String resolvedVariant = "default".equals(def.getVariantName()) ? "" : def.getVariantName();
            return Optional.of(new LoadedMultiblock(id.getNamespace(), id.getPath(), resolveDisplayName(parent), resolvedVariant, scan));
        });
    }

    private static Optional<LoadedMultiblock> loadJson(MinecraftServer server, String path, String requestedVariant) {
        String namespace = CommonConfig.DEVTOOL_NAMESPACE.get();
        Path file = MultiblockDevOutputPaths.jsonRootDir(server).resolve("data")
                .resolve(namespace).resolve("multiblocks").resolve(path + ".json");
        JsonObject obj = readJsonObject(file);
        if (obj == null) return Optional.empty();

        List<List<String>> layers = new ArrayList<>();
        String resolvedVariant = "";
        if (obj.has("variants") && obj.get("variants").isJsonArray()) {
            JsonObject variantObj = null;
            for (var variantElem : obj.getAsJsonArray("variants")) {
                if (!variantElem.isJsonObject()) continue;
                JsonObject candidate = variantElem.getAsJsonObject();
                if (variantObj == null) variantObj = candidate; // first entry = fallback/primary
                if (requestedVariant != null && !requestedVariant.isBlank()
                        && candidate.has("name") && requestedVariant.equals(candidate.get("name").getAsString())) {
                    variantObj = candidate;
                    break;
                }
            }
            if (variantObj != null) {
                resolvedVariant = variantObj.has("name") ? variantObj.get("name").getAsString() : "";
                if (variantObj.has("layers")) {
                    for (var layerElem : variantObj.getAsJsonArray("layers")) {
                        JsonArray layerArr = layerElem.getAsJsonArray();
                        List<String> rows = new ArrayList<>(layerArr.size());
                        for (var rowElem : layerArr) rows.add(rowElem.getAsString());
                        layers.add(rows);
                    }
                }
            }
        } else if (obj.has("layers")) {
            for (var layerElem : obj.getAsJsonArray("layers")) {
                JsonArray layerArr = layerElem.getAsJsonArray();
                List<String> rows = new ArrayList<>(layerArr.size());
                for (var rowElem : layerArr) rows.add(rowElem.getAsString());
                layers.add(rows);
            }
        }

        LinkedHashMap<Character, ResourceLocation> symbolToBlock = new LinkedHashMap<>();
        if (obj.has("keys")) {
            JsonObject keys = obj.getAsJsonObject("keys");
            for (var entry : keys.entrySet()) {
                if (entry.getKey().length() != 1) continue;
                if (!entry.getValue().isJsonObject() || !entry.getValue().getAsJsonObject().has("block")) continue;
                ResourceLocation blockId = ResourceLocation.tryParse(
                        entry.getValue().getAsJsonObject().get("block").getAsString());
                if (blockId == null || !BuiltInRegistries.BLOCK.containsKey(blockId)) continue;
                symbolToBlock.put(entry.getKey().charAt(0), blockId);
            }
        }

        Character core = obj.has("core") && obj.get("core").getAsString().length() == 1
                ? obj.get("core").getAsString().charAt(0) : null;
        Character activation = obj.has("activation") && obj.get("activation").getAsString().length() == 1
                ? obj.get("activation").getAsString().charAt(0) : null;

        String[] id = splitId(obj.has("id") ? obj.get("id").getAsString() : null, namespace, path);
        String displayName = readDisplayName(MultiblockDevOutputPaths.devtoolResourcePackRootDir(), id[0], id[1]);

        return Optional.of(new LoadedMultiblock(id[0], id[1], displayName, resolvedVariant,
                new MultiblockScanResult(layers, symbolToBlock, core, activation)));
    }

    private static final Pattern LAYER_JAVA = Pattern.compile("\\.layer\\((.*)\\)\\s*$");
    private static final Pattern LAYER_KUBEJS = Pattern.compile("\\.layer\\((.*)\\)\\s*$");
    private static final Pattern QUOTED_JAVA = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern QUOTED_KUBEJS = Pattern.compile("'((?:[^'\\\\]|\\\\.)*)'");
    private static final Pattern KEY_JAVA = Pattern.compile(
            "\\.key\\('(.)',\\s*BuiltInRegistries\\.BLOCK\\.get\\(ResourceLocation\\.parse\\(\"([^\"]+)\"\\)\\)\\)");
    private static final Pattern KEY_KUBEJS = Pattern.compile(
            "\\.key\\('(.)',\\s*MultiblockUtils\\.block\\('([^']+)'\\)\\)");
    /** Matches KubeJS exports written before {@code MultiblockUtils.block(...)} replaced this fully-qualified form (see {@code MultiblockDevExporter}) - kept so a stale, not-yet-re-exported {@code .js} file still loads instead of silently placing nothing. */
    private static final Pattern KEY_KUBEJS_LEGACY = Pattern.compile(
            "\\.key\\('(.)',\\s*net\\.astronomy\\.multilib\\.api\\.ingredient\\.BlockIngredient\\.parse\\('([^']+)'\\)\\)");
    private static final Pattern CORE = Pattern.compile("\\.core\\('(.)'\\)");
    private static final Pattern ACTIVATION = Pattern.compile("\\.activation\\('(.)'\\)");
    private static final Pattern VARIANT_JAVA = Pattern.compile("\\.variant\\(\"([^\"]+)\"");
    private static final Pattern VARIANT_KUBEJS = Pattern.compile("\\.variant\\('([^']+)'");

    /** The single {@code .variant("name", ...)}/{@code .variant('name', ...)} declaration a dev-tool scaffold export carries, if any - the dev tool only ever exports one geometry at a time, so at most one match is expected. */
    private static Optional<String> findVariantName(List<String> lines, boolean java) {
        Pattern pattern = java ? VARIANT_JAVA : VARIANT_KUBEJS;
        for (String line : lines) {
            Matcher m = pattern.matcher(line);
            if (m.find()) return Optional.of(m.group(1));
        }
        return Optional.empty();
    }

    private static Optional<LoadedMultiblock> loadScaffold(Path file, Path langRootDir, SourceFormat format) {
        List<String> lines = readLines(file);
        if (lines == null) return Optional.empty();

        String[] id = splitId(readMarkerId(lines).orElse(null), null, null);
        if (id[0] == null) return Optional.empty();

        boolean java = format == SourceFormat.JAVA;
        Pattern layerPattern = java ? LAYER_JAVA : LAYER_KUBEJS;
        Pattern quotedPattern = java ? QUOTED_JAVA : QUOTED_KUBEJS;
        Pattern keyPattern = java ? KEY_JAVA : KEY_KUBEJS;

        List<List<String>> layers = new ArrayList<>();
        LinkedHashMap<Character, ResourceLocation> symbolToBlock = new LinkedHashMap<>();
        Character core = null;
        Character activation = null;
        String displayName = readDisplayName(langRootDir, id[0], id[1]);

        for (String line : lines) {
            Matcher layerMatcher = layerPattern.matcher(line);
            if (layerMatcher.find()) {
                List<String> rows = new ArrayList<>();
                Matcher quoted = quotedPattern.matcher(layerMatcher.group(1));
                while (quoted.find()) {
                    rows.add(unescape(quoted.group(1), java));
                }
                if (!rows.isEmpty()) layers.add(rows);
                continue;
            }
            Matcher keyMatcher = keyPattern.matcher(line);
            boolean keyMatched = keyMatcher.find();
            if (!keyMatched && !java) {
                // Fall back to the pre-MultiblockUtils.block(...) syntax - see KEY_KUBEJS_LEGACY.
                keyMatcher = KEY_KUBEJS_LEGACY.matcher(line);
                keyMatched = keyMatcher.find();
            }
            if (keyMatched) {
                ResourceLocation blockId = ResourceLocation.tryParse(keyMatcher.group(2));
                if (blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)) {
                    symbolToBlock.put(keyMatcher.group(1).charAt(0), blockId);
                }
                continue;
            }
            Matcher coreMatcher = CORE.matcher(line);
            if (coreMatcher.find()) {
                core = coreMatcher.group(1).charAt(0);
                continue;
            }
            Matcher activationMatcher = ACTIVATION.matcher(line);
            if (activationMatcher.find()) {
                activation = activationMatcher.group(1).charAt(0);
            }
        }

        String resolvedVariant = findVariantName(lines, java).orElse("");
        return Optional.of(new LoadedMultiblock(id[0], id[1], displayName, resolvedVariant,
                new MultiblockScanResult(layers, symbolToBlock, core, activation)));
    }

    // ---- Shared helpers ----

    private static Optional<String> readMarkerId(List<String> lines) {
        if (lines.isEmpty()) return Optional.empty();
        String first = lines.get(0);
        if (!first.startsWith(MultiblockDevOutputPaths.EXPORT_ID_MARKER_PREFIX)) return Optional.empty();
        return Optional.of(first.substring(MultiblockDevOutputPaths.EXPORT_ID_MARKER_PREFIX.length()).trim());
    }

    /** {@code "ns:path"} -> {@code {ns, path}}, falling back to the given defaults (both, or just the missing half) when {@code id} is null/malformed. */
    private static String[] splitId(String id, String fallbackNamespace, String fallbackPath) {
        if (id != null) {
            int colon = id.indexOf(':');
            if (colon > 0 && colon < id.length() - 1) {
                return new String[] {id.substring(0, colon), id.substring(colon + 1)};
            }
        }
        return new String[] {fallbackNamespace, fallbackPath};
    }

    private static String unescape(String s, boolean java) {
        String quote = java ? "\"" : "'";
        return s.replace("\\\\", "\\").replace("\\" + quote, quote);
    }

    private static List<String> readLines(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private static JsonObject readJsonObject(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = GSON.fromJson(reader, JsonElement.class);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (IOException | JsonParseException e) {
            return null;
        }
    }
}
