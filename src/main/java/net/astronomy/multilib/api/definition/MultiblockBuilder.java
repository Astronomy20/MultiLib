package net.astronomy.multilib.api.definition;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.block.BlockDefinition;
import net.astronomy.multilib.api.callback.MultiblockAmbientCallback;
import net.astronomy.multilib.api.callback.MultiblockBrokenCallback;
import net.astronomy.multilib.api.callback.MultiblockFormedCallback;
import net.astronomy.multilib.api.callback.MultiblockTickCallback;
import net.astronomy.multilib.api.callback.MultiblockValidator;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.astronomy.multilib.core.registry.BlockDefinitionRegistry;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MultiblockBuilder {
    private ResourceLocation id;
    private final Map<Character, BlockIngredient> blockMap = new LinkedHashMap<>();
    private final List<List<String>> layers = new ArrayList<>();
    private RotationMode rotationMode = RotationMode.HORIZONTAL;
    private boolean requireAirInEmptyPositions = false;
    private char activationSymbol = '\0';
    private char coreSymbol = '\0';
    private int priority = 0;

    private FormationMode formationMode = FormationMode.AUTOMATIC;
    private final List<MultiblockFormedCallback> formedCallbacks = new ArrayList<>();
    private final List<MultiblockBrokenCallback> brokenCallbacks = new ArrayList<>();
    private MultiblockTickCallback tickCallback = null;
    private MultiblockAmbientCallback ambientCallback = null;
    private int ambientIntervalTicks = 20;
    private MultiblockValidator validator = null;

    private PatternProvider patternProvider = null;
    private Vec3i boundingBox = Vec3i.ZERO;
    private final Set<Character> optionalSymbols = new HashSet<>();
    private final Set<Integer> optionalLayerIndices = new HashSet<>();
    private final Map<Character, FreeBlockSpec> freeBlocks = new LinkedHashMap<>();
    private boolean shapeless = false;
    private Vec3i shapelessMinSize = Vec3i.ZERO;
    private Vec3i shapelessMaxSize = new Vec3i(64, 64, 64);
    private BlockIngredient shellIngredient = null;
    private final Map<Direction, BlockIngredient> shellFaces = new EnumMap<>(Direction.class);
    private BlockIngredient interiorIngredient = null;
    private final List<ShapelessRequirement> shapelessRequirements = new ArrayList<>();
    private boolean wallSharingEnabled = false;
    private final Map<Character, WallSharingMode> symbolWallSharingOverrides = new HashMap<>();
    private boolean ghostOverlayDebug = false;
    private boolean autoPlace = false;
    private ResourceLocation modelId = null;
    private ResourceLocation iconItem = null;
    private String nameTranslationKey = null;
    private final Set<Character> uniqueSymbols = new HashSet<>();
    private final Set<Character> surfaceOnlySymbols = new HashSet<>();
    private final Set<Character> frameOnlySymbols = new HashSet<>();
    private final Set<Character> insideOnlySymbols = new HashSet<>();
    private final Set<Character> keepVisibleSymbols = new HashSet<>();
    private final Set<AllowedRotation> allowedRotations = new java.util.LinkedHashSet<>();

    public MultiblockBuilder() {}

    public MultiblockBuilder id(ResourceLocation id) {
        this.id = id;
        return this;
    }

    public MultiblockBuilder layers(String... rows) {
        this.layers.add(Arrays.asList(rows));
        return this;
    }

    public MultiblockBuilder key(char symbol, Block block) {
        return key(symbol, BlockIngredient.of(block));
    }

    public MultiblockBuilder key(char symbol, BlockIngredient ingredient) {
        this.blockMap.put(symbol, ingredient);
        return this;
    }

    public MultiblockBuilder key(char symbol, BlockIngredient ingredient, WallSharingMode mode) {
        this.blockMap.put(symbol, ingredient);
        this.symbolWallSharingOverrides.put(symbol, mode);
        return this;
    }

    public MultiblockBuilder rotations(RotationMode mode) {
        this.rotationMode = mode;
        return this;
    }

    /**
     * Grants finer-grained control than {@link #rotations(RotationMode)}: allows the structure to be
     * matched/formed when rotated by the given angle(s) (90/180/270/-90) around the given axis, on top
     * of the always-tried unrotated orientation. Omitting {@code angles} allows all three (90/180/270)
     * around that axis. Can be called multiple times (once per axis, or with multiple angles at once)
     * to allow several axis/angle combinations. Declaring any rotation here takes over from
     * {@link RotationMode}: a Y-axis entry behaves like horizontal matching, an X or Z entry
     * additionally enables matching for the structure tipped onto its side.
     */
    public MultiblockBuilder allowRotation(RotationAxis axis, int... angles) {
        int[] effectiveAngles = angles.length == 0 ? ALL_ANGLES : angles;
        for (int angle : effectiveAngles) allowedRotations.add(new AllowedRotation(axis, angle));
        return this;
    }

    /**
     * Same as {@link #allowRotation(RotationAxis, int...)} but for multiple axes at once, e.g.
     * {@code .allowRotation(new RotationAxis[]{RotationAxis.X, RotationAxis.Z}, 180)} or, to allow
     * every axis and every angle, {@code .allowRotation(RotationAxis.values())}.
     */
    public MultiblockBuilder allowRotation(RotationAxis[] axes, int... angles) {
        for (RotationAxis axis : axes) allowRotation(axis, angles);
        return this;
    }

    /**
     * For when different axes need different angles in one call, e.g.
     * {@code .allowRotation(new AllowedRotation(RotationAxis.X, 90), new AllowedRotation(RotationAxis.Z, 180))}.
     */
    public MultiblockBuilder allowRotation(AllowedRotation... rotations) {
        for (AllowedRotation rotation : rotations) allowedRotations.add(rotation);
        return this;
    }

    private static final int[] ALL_ANGLES = {90, 180, 270};

    public MultiblockBuilder requireAirInEmptyPositions() {
        this.requireAirInEmptyPositions = true;
        return this;
    }

    public MultiblockBuilder activation(char symbol) {
        this.activationSymbol = symbol;
        return this;
    }

    public MultiblockBuilder core(char symbol) {
        this.coreSymbol = symbol;
        if (this.activationSymbol == '\0') {
            this.activationSymbol = symbol;
        }
        return this;
    }

    public MultiblockBuilder priority(int priority) {
        this.priority = priority;
        return this;
    }

    public MultiblockBuilder formationMode(FormationMode mode) {
        this.formationMode = mode;
        return this;
    }

    public MultiblockBuilder onFormed(MultiblockFormedCallback cb) {
        this.formedCallbacks.add(cb);
        return this;
    }

    public MultiblockBuilder onBroken(MultiblockBrokenCallback cb) {
        this.brokenCallbacks.add(cb);
        return this;
    }

    public MultiblockBuilder onTick(MultiblockTickCallback cb) {
        this.tickCallback = cb;
        return this;
    }

    public MultiblockBuilder onAmbient(MultiblockAmbientCallback cb, int intervalTicks) {
        this.ambientCallback = cb;
        this.ambientIntervalTicks = intervalTicks;
        return this;
    }

    public MultiblockBuilder validator(MultiblockValidator validator) {
        this.validator = validator;
        return this;
    }

    public MultiblockBuilder pattern(PatternProvider provider) {
        this.patternProvider = provider;
        return this;
    }

    public MultiblockBuilder boundingBox(int x, int y, int z) {
        this.boundingBox = new Vec3i(x, y, z);
        return this;
    }

    public MultiblockBuilder optional(char... symbols) {
        for (char c : symbols) optionalSymbols.add(c);
        return this;
    }

    public MultiblockBuilder optionalLayer(String... rows) {
        optionalLayerIndices.add(layers.size());
        this.layers.add(Arrays.asList(rows));
        return this;
    }

    public MultiblockBuilder freeBlock(char symbol, BlockIngredient ingredient, int min, int max) {
        this.freeBlocks.put(symbol, new FreeBlockSpec(ingredient, min, max, null));
        return this;
    }

    public MultiblockBuilder freeBlock(char symbol, BlockIngredient ingredient, int min, int max,
                                       List<BlockPos> allowedPositions) {
        this.freeBlocks.put(symbol, new FreeBlockSpec(ingredient, min, max, List.copyOf(allowedPositions)));
        return this;
    }

    public MultiblockBuilder shapeless() {
        this.shapeless = true;
        return this;
    }

    public MultiblockBuilder minSize(int x, int y, int z) {
        this.shapelessMinSize = new Vec3i(x, y, z);
        return this;
    }

    public MultiblockBuilder maxSize(int x, int y, int z) {
        this.shapelessMaxSize = new Vec3i(x, y, z);
        return this;
    }

    public MultiblockBuilder shell(BlockIngredient ingredient) {
        this.shellIngredient = ingredient;
        return this;
    }

    public MultiblockBuilder shellFace(Direction direction, BlockIngredient ingredient) {
        this.shellFaces.put(direction, ingredient);
        return this;
    }

    public MultiblockBuilder interior(BlockIngredient ingredient) {
        this.interiorIngredient = ingredient;
        return this;
    }

    public MultiblockBuilder require(BlockIngredient ingredient, int min, int max) {
        this.shapelessRequirements.add(new ShapelessRequirement(ingredient, min, max));
        return this;
    }

    public MultiblockBuilder wallSharing(boolean enabled) {
        this.wallSharingEnabled = enabled;
        return this;
    }

    public MultiblockBuilder noWallSharing(char... symbols) {
        for (char c : symbols) symbolWallSharingOverrides.put(c, WallSharingMode.DISABLED);
        return this;
    }

    /**
     * Dev-only switch: while enabled, players see a chat debug line with the ghost overlay's
     * render time whenever it's drawn for this structure. Meant to be toggled off before shipping.
     */
    public MultiblockBuilder ghostOverlayDebug() {
        this.ghostOverlayDebug = true;
        return this;
    }

    /**
     * Opts this definition into auto-placement: Ctrl+Right-clicking the core block (when the
     * structure isn't yet formed) auto-places every missing pattern position the player has the
     * matching item for, consuming items from their inventory (skipped in creative).
     */
    public MultiblockBuilder autoPlace() {
        this.autoPlace = true;
        return this;
    }

    /**
     * Associates a Master-Dummy render model with this multiblock: once formed, every block of the
     * structure becomes invisible except the core, which renders this model in its place instead of
     * its own block model. Physics/hitboxes of every block remain unaffected. {@code modelId} is the
     * registry id of a Block whose default-state model is borrowed for the core's render (see
     * {@code MultiblockMasterModelRenderer}). For this to take effect, blocks of the structure must
     * extend {@code AbstractMultiblockPartBlock}/{@code AbstractMultiblockControllerBlock} — arbitrary
     * vanilla/third-party blocks used as pattern symbols can't be made selectively invisible.
     */
    public MultiblockBuilder model(ResourceLocation modelId) {
        this.modelId = modelId;
        return this;
    }

    /** Item shown as the icon for this multiblock in recipe browsers (JEI/REI/EMI). */
    public MultiblockBuilder icon(ResourceLocation itemId) {
        this.iconItem = itemId;
        return this;
    }

    /**
     * Display name for this multiblock (e.g. JEI's recipe-page title). Pass only the bare name
     * key — e.g. {@code "example_multiblock"} — and the full translation key
     * {@code "multiblock.<namespace>.<name>"} is derived automatically at build time, following the
     * same convention as {@code block.<namespace>.<path>} for blocks. Both
     * {@code "multiblock.<ns>.<name>"} and {@code "block.<ns>.<name>"} should be defined in the
     * lang file. If unset, the core/activation block's own name is used as a fallback.
     */
    public MultiblockBuilder name(String name) {
        this.nameTranslationKey = name;
        return this;
    }

    /**
     * Symbols whose matched positions are NOT auto-hidden when {@link #model(ResourceLocation)} is set.
     * The core is always kept visible; use this for IO ports or other interactive blocks that should
     * remain visible while the structure is formed.
     */
    public MultiblockBuilder keepVisible(char... symbols) {
        for (char c : symbols) keepVisibleSymbols.add(c);
        return this;
    }

    /** Each given symbol must occur exactly once in the structure (statically, or via freeBlock min/max=1). */
    public MultiblockBuilder unique(char... symbols) {
        for (char c : symbols) uniqueSymbols.add(c);
        return this;
    }

    /** Each given symbol may only occupy a position on the structure's outer shell. */
    public MultiblockBuilder surfaceOnly(char... symbols) {
        for (char c : symbols) surfaceOnlySymbols.add(c);
        return this;
    }

    /** Each given symbol may only occupy a position on an edge/corner of the structure (on at least two boundary axes). */
    public MultiblockBuilder frameOnly(char... symbols) {
        for (char c : symbols) frameOnlySymbols.add(c);
        return this;
    }

    /** Each given symbol may only occupy a position strictly inside the structure (touching no boundary). */
    public MultiblockBuilder insideOnly(char... symbols) {
        for (char c : symbols) insideOnlySymbols.add(c);
        return this;
    }

    public MultiblockDefinition build() {
        if (id == null) {
            throw new IllegalStateException("MultiblockDefinition must have an id");
        }
        if (layers.isEmpty() && patternProvider == null && !shapeless) {
            throw new IllegalStateException("MultiblockDefinition must have at least one layer, a PatternProvider, or be shapeless");
        }
        // Not a toggleable feature — every multiblock without a .name(...) translation key always
        // gets this warning, so the fallback to the core/activation block's own display name (see
        // MultiblockRecipeCategory.multiblockName) is a visible, intentional choice rather than a
        // silently missing translation that's easy to mistake for a bug.
        String resolvedNameKey = nameTranslationKey != null && !nameTranslationKey.isBlank()
                ? "multiblock." + id.getNamespace() + "." + nameTranslationKey
                : null;
        if (resolvedNameKey == null) {
            MultiLib.LOGGER.warn("[MultiLib] Multiblock '{}' has no display name set via .name(...) — "
                    + "falling back to the core/activation block's own name in JEI/REI/EMI.", id);
        }
        boolean valid = resolveAndValidateCore(id) && validateGeometryConstraints(id);
        Map<Character, FreeBlockSpec> effectiveFreeBlocks = applyUniqueToFreeBlocks();
        MultiblockDefinition definition = new MultiblockDefinition(
                id, blockMap, layers, rotationMode,
                requireAirInEmptyPositions, activationSymbol, coreSymbol, priority,
                formationMode, formedCallbacks, brokenCallbacks,
                tickCallback, ambientCallback, ambientIntervalTicks, validator,
                patternProvider, boundingBox,
                optionalSymbols, optionalLayerIndices,
                effectiveFreeBlocks,
                shapeless, shapelessMinSize, shapelessMaxSize,
                shellIngredient, shellFaces, interiorIngredient, shapelessRequirements,
                wallSharingEnabled, symbolWallSharingOverrides, ghostOverlayDebug,
                modelId, iconItem, resolvedNameKey, uniqueSymbols, surfaceOnlySymbols, frameOnlySymbols, insideOnlySymbols,
                keepVisibleSymbols, autoPlace, allowedRotations
        );
        if (valid) {
            MultiblockRegistry.register(definition);
        } else {
            MultiLib.LOGGER.error("[MultiLib] Multiblock '{}' was NOT registered due to the validation error(s) above. Fix the definition and reload.", id);
        }
        return definition;
    }

    public MultiblockDefinition buildWithoutRegistering() {
        if (id == null) {
            throw new IllegalStateException("MultiblockDefinition must have an id");
        }
        if (layers.isEmpty() && patternProvider == null && !shapeless) {
            throw new IllegalStateException("MultiblockDefinition must have at least one layer, a PatternProvider, or be shapeless");
        }
        String resolvedNameKey = nameTranslationKey != null && !nameTranslationKey.isBlank()
                ? "multiblock." + id.getNamespace() + "." + nameTranslationKey
                : null;
        if (resolvedNameKey == null) {
            MultiLib.LOGGER.warn("[MultiLib] Multiblock '{}' has no display name set via .name(...) — "
                    + "falling back to the core/activation block's own name in JEI/REI/EMI.", id);
        }
        resolveAndValidateCore(id);
        validateGeometryConstraints(id);
        Map<Character, FreeBlockSpec> effectiveFreeBlocks = applyUniqueToFreeBlocks();
        return new MultiblockDefinition(
                id, blockMap, layers, rotationMode,
                requireAirInEmptyPositions, activationSymbol, coreSymbol, priority,
                formationMode, formedCallbacks, brokenCallbacks,
                tickCallback, ambientCallback, ambientIntervalTicks, validator,
                patternProvider, boundingBox,
                optionalSymbols, optionalLayerIndices,
                effectiveFreeBlocks,
                shapeless, shapelessMinSize, shapelessMaxSize,
                shellIngredient, shellFaces, interiorIngredient, shapelessRequirements,
                wallSharingEnabled, symbolWallSharingOverrides, ghostOverlayDebug,
                modelId, iconItem, resolvedNameKey, uniqueSymbols, surfaceOnlySymbols, frameOnlySymbols, insideOnlySymbols,
                keepVisibleSymbols, autoPlace, allowedRotations
        );
    }

    private Map<Character, FreeBlockSpec> applyUniqueToFreeBlocks() {
        if (uniqueSymbols.isEmpty()) return freeBlocks;
        Map<Character, FreeBlockSpec> result = new LinkedHashMap<>(freeBlocks);
        for (char symbol : uniqueSymbols) {
            FreeBlockSpec spec = result.get(symbol);
            if (spec != null) {
                result.put(symbol, new FreeBlockSpec(spec.ingredient(), 1, 1, spec.allowedPositions()));
            }
        }
        return result;
    }

    /**
     * If no block declares itself core of this multiblock id (via {@code MultiLibAPI.block(...).core(id)}),
     * this is a no-op. Otherwise, resolves/validates the multiblock's core symbol against the block-level
     * declaration: auto-assigns the core symbol when the builder didn't set one, or logs a mismatch error
     * (without throwing) when both sides disagree.
     */
    private boolean resolveAndValidateCore(ResourceLocation id) {
        char declaredSymbol = '\0';
        boolean conflict = false;

        for (Map.Entry<Character, BlockIngredient> entry : blockMap.entrySet()) {
            char symbol = entry.getKey();
            for (Block block : entry.getValue().getCandidateBlocks()) {
                Optional<BlockDefinition> bd = BlockDefinitionRegistry.get(block);
                if (bd.isEmpty() || !bd.get().isCoreOf(id)) continue;
                if (declaredSymbol == '\0') {
                    declaredSymbol = symbol;
                } else if (declaredSymbol != symbol) {
                    conflict = true;
                }
            }
        }

        if (conflict) {
            MultiLib.LOGGER.error("[MultiLib] Multiblock '{}': multiple distinct blocks declare themselves as core of this multiblock (via Block-level .core()) under different symbols.", id);
            return false;
        }

        if (declaredSymbol != '\0') {
            if (coreSymbol == '\0') {
                coreSymbol = declaredSymbol;
                if (activationSymbol == '\0') activationSymbol = declaredSymbol;
            } else if (coreSymbol != declaredSymbol) {
                MultiLib.LOGGER.error(
                        "[MultiLib] Multiblock '{}': core symbol declared in the multiblock builder ('{}') does not match the core declared by the block itself ('{}'). Fix one of the two declarations.",
                        id, coreSymbol, declaredSymbol);
                return false;
            }
        }
        return true;
    }

    /** Statically validates unique/surfaceOnly/frameOnly/insideOnly against the textual pattern layers. */
    private boolean validateGeometryConstraints(ResourceLocation id) {
        if (uniqueSymbols.isEmpty() && surfaceOnlySymbols.isEmpty()
                && frameOnlySymbols.isEmpty() && insideOnlySymbols.isEmpty()) {
            return true;
        }

        Map<Character, Integer> counts = new HashMap<>();
        boolean ok = true;
        int layersCount = layers.size();

        for (int yOffset = 0; yOffset < layersCount; yOffset++) {
            List<String> layer = layers.get(yOffset);
            int height = layer.size();
            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                int width = line.length();
                for (int col = 0; col < width; col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;
                    counts.merge(symbol, 1, Integer::sum);

                    boolean xBoundary = col == 0 || col == width - 1;
                    boolean zBoundary = row == 0 || row == height - 1;
                    boolean yBoundary = yOffset == 0 || yOffset == layersCount - 1;
                    int boundaryAxes = (xBoundary ? 1 : 0) + (zBoundary ? 1 : 0) + (yBoundary ? 1 : 0);

                    if (surfaceOnlySymbols.contains(symbol) && boundaryAxes == 0) {
                        MultiLib.LOGGER.error("[MultiLib] Multiblock '{}': symbol '{}' is marked surfaceOnly() but occurs inside the structure (layer {}, row {}, col {}).", id, symbol, yOffset, row, col);
                        ok = false;
                    }
                    if (frameOnlySymbols.contains(symbol) && boundaryAxes < 2) {
                        MultiLib.LOGGER.error("[MultiLib] Multiblock '{}': symbol '{}' is marked frameOnly() but occurs off an edge/corner of the structure (layer {}, row {}, col {}).", id, symbol, yOffset, row, col);
                        ok = false;
                    }
                    if (insideOnlySymbols.contains(symbol) && boundaryAxes > 0) {
                        MultiLib.LOGGER.error("[MultiLib] Multiblock '{}': symbol '{}' is marked insideOnly() but occurs on the structure's boundary (layer {}, row {}, col {}).", id, symbol, yOffset, row, col);
                        ok = false;
                    }
                }
            }
        }

        for (char symbol : uniqueSymbols) {
            if (freeBlocks.containsKey(symbol)) continue; // forced to min=max=1 in applyUniqueToFreeBlocks()
            int count = counts.getOrDefault(symbol, 0);
            if (count != 1) {
                MultiLib.LOGGER.error("[MultiLib] Multiblock '{}': symbol '{}' is marked unique() but occurs {} time(s) in the pattern (expected exactly 1).", id, symbol, count);
                ok = false;
            }
        }
        return ok;
    }
}
