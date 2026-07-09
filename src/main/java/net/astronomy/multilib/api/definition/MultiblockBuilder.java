package net.astronomy.multilib.api.definition;

import net.astronomy.multilib.CommonConfig;
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
import net.astronomy.multilib.event.MultiblockLoadErrorNotifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

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
import java.util.function.Consumer;

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
    // true by default - see MultiblockBuilder#ghostOverlay's javadoc for why (existing baseline
    // behavior, not a new opt-in capability).
    private boolean ghostOverlay = true;
    /** 0 = unset (use {@code CommonConfig.GHOST_OVERLAY_DURATION_SECONDS}), -1 = never expires, positive = override seconds. */
    private int ghostOverlayDurationSeconds = 0;
    private boolean autoPlace = false;
    private boolean autoPlaceOverlay = false;
    private ResourceLocation modelId = null;
    private ResourceLocation iconItem = null;
    private final Set<Character> uniqueSymbols = new HashSet<>();
    private final Set<Character> surfaceOnlySymbols = new HashSet<>();
    private final Set<Character> frameOnlySymbols = new HashSet<>();
    private final Set<Character> insideOnlySymbols = new HashSet<>();
    private final Set<Character> keepVisibleSymbols = new HashSet<>();
    private final Set<AllowedRotation> allowedRotations = new java.util.LinkedHashSet<>();
    private final Map<Character, List<TierSpec>> tierSpecs = new LinkedHashMap<>();
    private String formedProperty = null;

    // F12 step A (multiple pattern variants). Empty = legacy behavior, byte-for-byte unchanged. Each
    // entry is one .variant(...) declaration in call order; the first becomes this definition's own
    // geometry at build() time, every later one becomes a derived MultiblockDefinition - see build().
    private final List<VariantSpec> variantSpecs = new ArrayList<>();

    // Short, fix-first description of why build()/buildWithoutRegistering() last rejected this
    // definition - set by whichever validate*() method failed. Lets a caller that only gets `null`
    // back from buildWithoutRegistering() (JSON/KubeJS) surface a reason of its own instead of just
    // "see the log", without needing to re-derive it. Not meant to accumulate multiple errors: the
    // validate*() chain short-circuits on the first failure, so there's only ever one to report.
    private String lastValidationError;

    // KubeJS silences this (see #silenceDevModeChat()): it already surfaces validation failures
    // through its own console/script error overlay, so broadcasting the same failure to chat too
    // would just show the player the same thing twice. The server log is never silenced.
    private boolean suppressChatNotification = false;

    public MultiblockBuilder() {}

    /**
     * Reconstructs a builder pre-populated with every field of {@code def}, for
     * {@link MultiblockDefinition#toBuilder()}. Package-private and field-direct (not routed through
     * the public fluent methods) so it can't drift out of sync with ambiguous cases the fluent API
     * can't express 1:1 (e.g. a wall-sharing override on a symbol with no matching {@code key()} call).
     * <p>
     * Kept next to {@link MultiblockDefinition}'s constructor deliberately - whenever a field is added
     * there, it must be added here too, or a KubeJS/Java "modify" of an existing definition will
     * silently drop it back to the builder's default.
     */
    static MultiblockBuilder fromDefinition(MultiblockDefinition def) {
        MultiblockBuilder b = new MultiblockBuilder();
        b.id = def.getId();
        // F12 step A: a legacy definition (built without .variant()) copies blockMap/layers straight
        // across as before. A definition built WITH .variant() instead restores just the shared/
        // top-level keys plus the raw per-variant declarations, so re-running build() re-derives the
        // exact same merged layers/keys per variant rather than re-populating b.layers directly (which
        // would trip the "can't mix top-level layer() with variant()" check in build()).
        if (def.getRawVariantSpecs().isEmpty()) {
            b.blockMap.putAll(def.getBlockMap());
            for (List<String> layer : def.getLayers()) {
                b.layers.add(new ArrayList<>(layer));
            }
        } else {
            b.blockMap.putAll(def.getSharedBlockMap());
            b.variantSpecs.addAll(def.getRawVariantSpecs());
        }
        b.rotationMode = def.getRotationMode();
        b.requireAirInEmptyPositions = def.isRequireAirInEmptyPositions();
        b.activationSymbol = def.getActivationSymbol();
        b.coreSymbol = def.getCoreSymbol();
        b.priority = def.getPriority();
        b.formationMode = def.getFormationMode();
        b.formedCallbacks.addAll(def.getFormedCallbacks());
        b.brokenCallbacks.addAll(def.getBrokenCallbacks());
        b.tickCallback = def.getTickCallback().orElse(null);
        b.ambientCallback = def.getAmbientCallback().orElse(null);
        b.ambientIntervalTicks = def.getAmbientIntervalTicks();
        b.validator = def.getValidator().orElse(null);
        b.patternProvider = def.getPatternProvider().orElse(null);
        b.boundingBox = def.getBoundingBox();
        b.optionalSymbols.addAll(def.getOptionalSymbols());
        b.optionalLayerIndices.addAll(def.getOptionalLayerIndices());
        b.freeBlocks.putAll(def.getFreeBlocks());
        b.shapeless = def.isShapeless();
        b.shapelessMinSize = def.getShapelessMinSize();
        b.shapelessMaxSize = def.getShapelessMaxSize();
        b.shellIngredient = def.getShellIngredient().orElse(null);
        b.shellFaces.putAll(def.getShellFaces());
        b.interiorIngredient = def.getInteriorIngredient().orElse(null);
        b.shapelessRequirements.addAll(def.getShapelessRequirements());
        b.wallSharingEnabled = def.isWallSharingEnabled();
        b.symbolWallSharingOverrides.putAll(def.getSymbolWallSharingOverrides());
        b.ghostOverlay = def.isGhostOverlayEnabled();
        b.ghostOverlayDurationSeconds = def.getGhostOverlayDurationSeconds().orElse(0);
        b.autoPlace = def.isAutoPlace();
        b.autoPlaceOverlay = def.isAutoPlaceOverlay();
        b.modelId = def.getModelId().orElse(null);
        b.iconItem = def.getIconItem().orElse(null);
        b.uniqueSymbols.addAll(def.getUniqueSymbols());
        b.surfaceOnlySymbols.addAll(def.getSurfaceOnlySymbols());
        b.frameOnlySymbols.addAll(def.getFrameOnlySymbols());
        b.insideOnlySymbols.addAll(def.getInsideOnlySymbols());
        b.keepVisibleSymbols.addAll(def.getKeepVisibleSymbols());
        b.allowedRotations.addAll(def.getAllowedRotations());
        def.getTierSpecs().forEach((symbol, specs) -> b.tierSpecs.put(symbol, new ArrayList<>(specs)));
        b.formedProperty = def.getFormedProperty().orElse(null);
        return b;
    }

    public MultiblockBuilder id(ResourceLocation id) {
        this.id = id;
        return this;
    }

    /** The id set via {@link #id(ResourceLocation)} so far, or {@code null} if not set yet. */
    @Nullable
    public ResourceLocation getId() {
        return id;
    }

    /**
     * Short, plain-language reason the last {@link #build()}/{@link #buildWithoutRegistering()} call
     * rejected this definition (e.g. "use .activation() instead of .core() for a block used more than
     * once"), or {@code null} if the last attempt succeeded or none was made yet. The full rationale
     * always goes to the server log too - this is deliberately just the one-line fix.
     */
    @Nullable
    public String getLastValidationError() {
        return lastValidationError;
    }

    /**
     * Suppresses the dev-mode chat broadcast for any validation error this build attempt logs - the
     * server log is unaffected. For callers (KubeJS) that already surface validation failures through
     * their own channel, so the player doesn't see the same failure reported twice.
     */
    public MultiblockBuilder silenceDevModeChat() {
        this.suppressChatNotification = true;
        return this;
    }

    /**
     * Adds one horizontal (Y) slice of the structure. The <em>first</em> {@code .layer(...)} call is
     * the <b>top</b> of the structure; each subsequent call stacks one level lower, with the
     * <em>last</em> call being the bottom.
     */
    public MultiblockBuilder layer(String... rows) {
        this.layers.add(Arrays.asList(rows));
        return this;
    }

    /**
     * Declares one named geometry variant (F12 step A): an alternate set of layers - and, optionally,
     * variant-local key overrides - that forms under the same id, sharing every other behavioral field
     * (callbacks, formation mode, rotation config, core/activation symbols, priority, formedProperty,
     * tier specs, validator, ...) with this definition. At {@link #build()}/{@link #buildWithoutRegistering()}
     * time:
     * <ul>
     *   <li>the <em>first</em> declared variant becomes this definition's own geometry - its layers and
     *   merged keys populate exactly the fields the legacy (no-variant) path populates, and its name
     *   becomes {@link MultiblockDefinition#getVariantName()};</li>
     *   <li>every later variant becomes a derived {@link MultiblockDefinition}, reachable via
     *   {@link MultiblockDefinition#getVariantDefinitions()} - never registered under its own entry in
     *   {@code MultiblockRegistry}, since it shares the same id as the parent.</li>
     * </ul>
     * Shared top-level {@link #key(char, BlockIngredient)} calls apply to every variant; a variant's own
     * {@link VariantBuilder#key(char, BlockIngredient)} overrides the shared one for that variant only.
     * <p>
     * Once any {@code variant(...)} is declared, all geometry must live inside {@code variant(...)}
     * blocks: mixing with a top-level {@link #layer(String...)} call on this same builder is rejected at
     * build time, as is an empty or duplicate variant name.
     */
    public MultiblockBuilder variant(String name, Consumer<VariantBuilder> config) {
        VariantBuilder variantBuilder = new VariantBuilder();
        config.accept(variantBuilder);
        variantSpecs.add(variantBuilder.toSpec(name));
        return this;
    }

    public MultiblockBuilder key(char symbol, Block block) {
        return key(symbol, BlockIngredient.of(block));
    }

    /**
     * String overload for scripting (KubeJS and friends): {@code "minecraft:iron_block"} for a single
     * block, or {@code "#forge:storage_blocks/iron"} (note the {@code #}) for a tag - see
     * {@link BlockIngredient#parse} for the exact rules. Gives scripts the same block-vs-tag coverage
     * the JSON datapack format's separate {@code "block"}/{@code "tag"} key fields already have,
     * without needing a Java reference for either.
     */
    public MultiblockBuilder key(char symbol, String blockOrTagId) {
        return key(symbol, BlockIngredient.parse(blockOrTagId));
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

    /**
     * String overload for scripting (KubeJS and friends), so callers never need a Java reference to
     * {@link FormationMode} itself. Same ids as the JSON datapack format - see
     * {@code MultiblockCodecs#FORMATION_MODE}, which this mirrors on purpose: "automatic", "wrench",
     * "automatic_and_wrench", the legacy "manual" alias for "wrench", or any custom id registered via
     * {@link FormationMode#register}. Throws {@link IllegalArgumentException} on an unknown id.
     */
    public MultiblockBuilder formationMode(String id) {
        String resolvedId = id.equalsIgnoreCase("manual") ? "wrench" : id.toLowerCase(java.util.Locale.ROOT);
        FormationMode mode = FormationMode.byId(resolvedId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown formation mode: " + id));
        return formationMode(mode);
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
    /**
     * Whether this definition previews via the ghost overlay at all: right-clicking a core (or
     * activation - see {@code GhostOverlayInputHandler}) block while sneaking shows a translucent
     * preview of what's missing/mismatched. Defaults to {@code true} - this is existing baseline
     * behavior every definition has always had, not a new opt-in feature, so most definitions never
     * need to call this at all. Call {@code .ghostOverlay(false)} to opt a definition out entirely
     * (e.g. one whose formation logic doesn't map to a fixed physical layout a preview could show).
     * <p>
     * The dev-facing debug countdown line (chat message showing how long an overlay session has left)
     * used to be a separate per-definition {@code ghostOverlayDebug()} flag; it's now purely gated on
     * {@code CommonConfig.DEV_MODE} globally instead, since restricting a diagnostic message to
     * specific definitions never added anything a dev actually wanted less of.
     */
    public MultiblockBuilder ghostOverlay(boolean enabled) {
        this.ghostOverlay = enabled;
        return this;
    }

    /**
     * Per-definition override of {@code CommonConfig.GHOST_OVERLAY_DURATION_SECONDS}: this
     * definition's ghost overlay auto-disables after {@code seconds} instead of the configured global
     * duration. Useful for a dev-tooling structure (e.g. a "preview selector" wrench that cycles
     * through candidate definitions sharing a core - see {@link #variant}'s sibling use case) where
     * the developer wants the preview to stay up noticeably longer than a normal player-facing
     * structure's overlay while they iterate on placement.
     * <p>
     * Only affects definitions that opt in - every definition that never calls this (or
     * {@link #ghostOverlayPersistent()}) keeps using the config value exactly as before.
     *
     * @throws IllegalArgumentException if {@code seconds} isn't positive (use
     *         {@link #ghostOverlayPersistent()} for "never expires" instead of a sentinel value here)
     */
    public MultiblockBuilder ghostOverlayDuration(int seconds) {
        if (seconds <= 0) {
            throw new IllegalArgumentException(
                    "ghostOverlayDuration(seconds) requires a positive value, got " + seconds
                            + " - use ghostOverlayPersistent() for a non-expiring overlay instead.");
        }
        this.ghostOverlayDurationSeconds = seconds;
        return this;
    }

    /**
     * This definition's ghost overlay never auto-expires - it stays visible until the player manually
     * closes it (clicking the core again with mode cycled back past the last layer) or the structure
     * actually forms. The intended use is a dev-facing structure the developer wants pinned in-world
     * indefinitely while manually placing blocks to test it, rather than fighting a countdown meant
     * for ordinary player-facing structures. See {@link #ghostOverlayDuration(int)} for a bounded
     * override instead of an unbounded one.
     */
    public MultiblockBuilder ghostOverlayPersistent() {
        this.ghostOverlayDurationSeconds = -1;
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
     * Opts this definition into the auto-place preview overlay: while a player looks at the core
     * block of an unformed structure holding an item that matches one or more missing positions, those
     * positions are highlighted ghost-overlay style (no need to trigger the regular ghost overlay
     * first). Only meaningful combined with {@link #autoPlace()} - the preview promises the block can
     * actually be auto-placed there.
     */
    public MultiblockBuilder autoPlaceOverlay() {
        this.autoPlaceOverlay = true;
        return this;
    }

    /**
     * Associates a Master-Dummy render model with this multiblock: once formed, every block of the
     * structure becomes invisible except the core, which renders this model in its place instead of
     * its own block model. Physics/hitboxes of every block remain unaffected. {@code modelId} is the
     * registry id of a Block whose default-state model is borrowed for the core's render (see
     * {@code MultiblockMasterModelRenderer}). For this to take effect, blocks of the structure must
     * extend {@code AbstractMultiblockPartBlock}/{@code AbstractMultiblockControllerBlock} - arbitrary
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
     * Symbols whose matched positions are NOT auto-hidden when {@link #model(ResourceLocation)} is set.
     * The core is always kept visible; use this for IO ports or other interactive blocks that should
     * remain visible while the structure is formed.
     */
    public MultiblockBuilder keepVisible(char... symbols) {
        for (char c : symbols) keepVisibleSymbols.add(c);
        return this;
    }

    /**
     * Declares one named tier level for {@code symbol}, backed by an explicit set of blocks. Call
     * repeatedly for the same symbol from lowest to highest tier - each call's position in that order
     * becomes its {@link TierSpec#ordinal()} (0 = first/lowest), which is what a caller compares tiers
     * by, not the name itself. See {@code MultiblockTier} for resolving which tier is actually present
     * in a formed instance.
     */
    public MultiblockBuilder tier(char symbol, String name, Block... blocks) {
        List<TierSpec> specs = tierSpecs.computeIfAbsent(symbol, k -> new ArrayList<>());
        specs.add(new TierSpec(name, specs.size(), Set.of(blocks), null));
        return this;
    }

    /**
     * Same as {@link #tier(char, String, Block...)}, but backed by a {@link net.minecraft.tags.TagKey}
     * instead of a fixed block list - so a third-party addon can contribute its own blocks to this tier
     * via datapack (adding to the tag) without this definition needing to know about them ahead of time.
     */
    public MultiblockBuilder tier(char symbol, String name, net.minecraft.tags.TagKey<Block> tag) {
        List<TierSpec> specs = tierSpecs.computeIfAbsent(symbol, k -> new ArrayList<>());
        specs.add(new TierSpec(name, specs.size(), Set.of(), tag));
        return this;
    }

    /**
     * Same as {@link #tier(char, String, Block...)}, but also attaches a stat map to this tier level
     * (the GregTech-coil-style glue between "which tier is placed" and actual machine behavior - e.g.
     * {@code Map.of("speed", 2.0)}). See {@code MultiblockTierResolution} for reading these back once a
     * tier is resolved against a formed instance. If you'd rather declare the tier first and attach
     * stats afterward (e.g. computed separately), use {@link #tierStats(char, String, Map)} instead.
     */
    public MultiblockBuilder tier(char symbol, String name, Map<String, Double> stats, Block... blocks) {
        List<TierSpec> specs = tierSpecs.computeIfAbsent(symbol, k -> new ArrayList<>());
        specs.add(new TierSpec(name, specs.size(), Set.of(blocks), null, stats));
        return this;
    }

    /** Same as {@link #tier(char, String, TagKey)}, but also attaches a stat map - see {@link #tier(char, String, Map, Block...)}. */
    public MultiblockBuilder tier(char symbol, String name, Map<String, Double> stats, net.minecraft.tags.TagKey<Block> tag) {
        List<TierSpec> specs = tierSpecs.computeIfAbsent(symbol, k -> new ArrayList<>());
        specs.add(new TierSpec(name, specs.size(), Set.of(), tag, stats));
        return this;
    }

    /**
     * Attaches (or replaces) the stat map of an already-declared tier level, looked up by
     * {@code symbol} + the exact {@code tierName} passed to the earlier {@code .tier(...)} call - lets a
     * tier be declared once and have its stats filled in/overwritten later (e.g. computed from config),
     * instead of requiring every {@code .tier(...)} call to carry its stats inline via the
     * {@link #tier(char, String, Map, Block...)} overload. Replaces the tier's stat map outright (not a
     * merge) - call once per tier level with the complete map you want it to end up with.
     *
     * @throws IllegalArgumentException if no tier was declared for {@code symbol}, or none of its
     * declared tiers is named {@code tierName} - always a typo/ordering bug worth failing fast on rather
     * than silently doing nothing.
     */
    public MultiblockBuilder tierStats(char symbol, String tierName, Map<String, Double> stats) {
        List<TierSpec> specs = tierSpecs.get(symbol);
        if (specs == null) {
            throw new IllegalArgumentException(
                    "No tier declared for symbol '" + symbol + "' - call .tier(...) before .tierStats(...)");
        }
        for (int i = 0; i < specs.size(); i++) {
            TierSpec spec = specs.get(i);
            if (spec.name().equals(tierName)) {
                specs.set(i, new TierSpec(spec.name(), spec.ordinal(), spec.blocks(), spec.tag(), stats));
                return this;
            }
        }
        throw new IllegalArgumentException(
                "No tier named '" + tierName + "' declared for symbol '" + symbol + "'");
    }

    /**
     * Opts every member block of this structure into a formed/unformed blockstate switch (IE/Mekanism
     * style): on formation, each position whose block declares a {@code BooleanProperty} named
     * {@code propertyName} in its state definition gets that property set {@code true}; on breaking, back
     * to {@code false} (see {@code BlockActivationHandler}/{@code BlockBreakHandler}). {@code propertyName}
     * is a plain string (not a {@code BooleanProperty} reference) so JSON-defined multiblocks can use this
     * too - resolved per-block at formation/break time via that block's own
     * {@code StateDefinition#getProperty(String)}. A block with no property of that name is silently
     * skipped (arbitrary vanilla/third-party blocks used as pattern symbols won't crash). Off by default
     * (empty = feature disabled) - no behavior change for definitions that don't call this.
     * <p>
     * <b>Footgun:</b> if a pattern ingredient matches on the <em>same</em> property this toggles (e.g. a
     * key declared as {@code BlockIngredient.ofState(...)} requiring {@code lit=false}), forming the
     * structure immediately invalidates that ingredient's own match the instant the property flips to
     * {@code true} - the structure can "unform" itself right after forming, or simply never
     * re-validate as formed on the next check. Don't toggle a property that any of this definition's own
     * ingredients also matches on.
     */
    public MultiblockBuilder formedProperty(String propertyName) {
        this.formedProperty = propertyName;
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
        if (layers.isEmpty() && variantSpecs.isEmpty() && patternProvider == null && !shapeless) {
            throw new IllegalStateException("MultiblockDefinition must have at least one layer, a PatternProvider, or be shapeless");
        }
        // Display name key follows the same convention as block.<namespace>.<path> for blocks -
        // derived straight from the mandatory id, so every multiblock always has one with no separate
        // opt-in call needed. Define "multiblock.<ns>.<path>" in the lang file for the JEI/REI/EMI title.
        String resolvedNameKey = "multiblock." + id.getNamespace() + "." + id.getPath();
        BuildResult result = buildDefinitionInternal(resolvedNameKey);
        if (result.valid()) {
            MultiblockRegistry.register(result.definition());
        } else {
            MultiLib.LOGGER.error("[MultiLib] Multiblock '{}' was NOT registered due to the validation error(s) above. Fix the definition and reload.", id);
        }
        return result.definition();
    }

    /**
     * @return {@code null} if validation fails (see {@link #build()}'s {@code valid} checks) - unlike
     * {@code build()}, there's no separate "registered anyway despite being invalid" object to hand
     * back, since callers of this method (JSON/KubeJS, which do their own registration afterward) have
     * no other way to learn a definition was rejected other than getting nothing back.
     */
    @Nullable
    public MultiblockDefinition buildWithoutRegistering() {
        if (id == null) {
            throw new IllegalStateException("MultiblockDefinition must have an id");
        }
        if (layers.isEmpty() && variantSpecs.isEmpty() && patternProvider == null && !shapeless) {
            throw new IllegalStateException("MultiblockDefinition must have at least one layer, a PatternProvider, or be shapeless");
        }
        String resolvedNameKey = "multiblock." + id.getNamespace() + "." + id.getPath();
        BuildResult result = buildDefinitionInternal(resolvedNameKey);
        if (!result.valid()) {
            MultiLib.LOGGER.error("[MultiLib] Multiblock '{}' was NOT built due to the validation error(s) above. Fix the definition and reload.", id);
            return null;
        }
        return result.definition();
    }

    /** Pairing of the built {@link MultiblockDefinition} with whether it passed validation - see {@link #buildDefinitionInternal(String)}. */
    private record BuildResult(boolean valid, MultiblockDefinition definition) {}

    /**
     * Shared construction path for {@link #build()} and {@link #buildWithoutRegistering()} - both need
     * identical resolution/validation/construction logic and only differ in what they do with the
     * result (register + warn-and-return-anyway vs. return {@code null}), so that part is factored out
     * here to keep the F12 step A (variant) branching in exactly one place.
     * <p>
     * With no {@code .variant(...)} declared, this is byte-for-byte the legacy path: one definition
     * built from this builder's own {@code layers}/{@code blockMap}, variant name {@code "default"}, no
     * derived siblings. With {@code .variant(...)} declared, the first variant's layers/merged keys
     * become this definition's own geometry (and variant name), and every later variant becomes a
     * derived {@link MultiblockDefinition} sharing every other field.
     */
    private BuildResult buildDefinitionInternal(String resolvedNameKey) {
        Map<Character, FreeBlockSpec> effectiveFreeBlocks = applyUniqueToFreeBlocks();

        String variantName;
        List<List<String>> effectiveLayers;
        Map<Character, BlockIngredient> effectiveBlockMap;
        Map<Character, BlockIngredient> sharedBlockMapForRecord;
        List<VariantSpec> rawSpecsForRecord;
        List<MultiblockDefinition> derivedVariants;
        boolean valid;

        if (variantSpecs.isEmpty()) {
            variantName = "default";
            effectiveLayers = layers;
            effectiveBlockMap = blockMap;
            sharedBlockMapForRecord = Map.of();
            rawSpecsForRecord = List.of();
            derivedVariants = List.of();
            valid = validateVariantGeometry(id, effectiveLayers, effectiveBlockMap);
        } else {
            if (!layers.isEmpty()) {
                throw new IllegalStateException("Multiblock '" + id + "': cannot mix a top-level .layer(...) "
                        + "call with .variant(...) - declare all geometry inside variant(...) blocks, or none.");
            }
            Set<String> seenNames = new HashSet<>();
            for (VariantSpec spec : variantSpecs) {
                if (spec.name() == null || spec.name().isBlank()) {
                    throw new IllegalStateException("Multiblock '" + id + "': variant name must not be empty.");
                }
                if (!seenNames.add(spec.name())) {
                    throw new IllegalStateException("Multiblock '" + id + "': duplicate variant name '" + spec.name() + "' - variant names must be unique.");
                }
            }

            VariantSpec firstSpec = variantSpecs.get(0);
            variantName = firstSpec.name();
            effectiveLayers = firstSpec.layers();
            effectiveBlockMap = mergeKeys(blockMap, firstSpec.keys());
            sharedBlockMapForRecord = Map.copyOf(blockMap);
            rawSpecsForRecord = List.copyOf(variantSpecs);
            valid = validateVariantGeometry(id, effectiveLayers, effectiveBlockMap);

            derivedVariants = new ArrayList<>();
            for (int i = 1; i < variantSpecs.size(); i++) {
                VariantSpec spec = variantSpecs.get(i);
                Map<Character, BlockIngredient> mergedKeys = mergeKeys(blockMap, spec.keys());
                valid = validateVariantGeometry(id, spec.layers(), mergedKeys) && valid;
                derivedVariants.add(new MultiblockDefinition(
                        id, mergedKeys, spec.layers(), rotationMode,
                        requireAirInEmptyPositions, activationSymbol, coreSymbol, priority,
                        formationMode, formedCallbacks, brokenCallbacks,
                        tickCallback, ambientCallback, ambientIntervalTicks, validator,
                        patternProvider, boundingBox,
                        optionalSymbols, optionalLayerIndices,
                        effectiveFreeBlocks,
                        shapeless, shapelessMinSize, shapelessMaxSize,
                        shellIngredient, shellFaces, interiorIngredient, shapelessRequirements,
                        wallSharingEnabled, symbolWallSharingOverrides, ghostOverlay, ghostOverlayDurationSeconds,
                        modelId, iconItem, resolvedNameKey, uniqueSymbols, surfaceOnlySymbols, frameOnlySymbols, insideOnlySymbols,
                        keepVisibleSymbols, autoPlace, autoPlaceOverlay, allowedRotations, tierSpecs, formedProperty,
                        spec.name(), List.of(), List.of(), Map.of()
                ));
            }
        }

        MultiblockDefinition definition = new MultiblockDefinition(
                id, effectiveBlockMap, effectiveLayers, rotationMode,
                requireAirInEmptyPositions, activationSymbol, coreSymbol, priority,
                formationMode, formedCallbacks, brokenCallbacks,
                tickCallback, ambientCallback, ambientIntervalTicks, validator,
                patternProvider, boundingBox,
                optionalSymbols, optionalLayerIndices,
                effectiveFreeBlocks,
                shapeless, shapelessMinSize, shapelessMaxSize,
                shellIngredient, shellFaces, interiorIngredient, shapelessRequirements,
                wallSharingEnabled, symbolWallSharingOverrides, ghostOverlay, ghostOverlayDurationSeconds,
                modelId, iconItem, resolvedNameKey, uniqueSymbols, surfaceOnlySymbols, frameOnlySymbols, insideOnlySymbols,
                keepVisibleSymbols, autoPlace, autoPlaceOverlay, allowedRotations, tierSpecs, formedProperty,
                variantName, derivedVariants, rawSpecsForRecord, sharedBlockMapForRecord
        );
        return new BuildResult(valid, definition);
    }

    private static Map<Character, BlockIngredient> mergeKeys(Map<Character, BlockIngredient> shared, Map<Character, BlockIngredient> overrides) {
        Map<Character, BlockIngredient> merged = new LinkedHashMap<>(shared);
        merged.putAll(overrides);
        return merged;
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
     * If no block declares itself core of this multiblock id (via {@code MultiLib.block(...).core(id)}),
     * this is a no-op. Otherwise, resolves/validates the multiblock's core symbol against the block-level
     * declaration: auto-assigns the core symbol when the builder didn't set one, or logs a mismatch error
     * (without throwing) when both sides disagree.
     */
    private boolean resolveAndValidateCore(ResourceLocation id, Map<Character, BlockIngredient> blockMap) {
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

    /**
     * Runs every geometry validation this builder performs, in the same order {@code build()} always
     * has, against an explicit {@code layers}/{@code blockMap} pair instead of always this builder's
     * own fields - so the exact same validation logic can run once for the legacy (no-variant) geometry
     * and once per {@code .variant(...)} declaration (F12 step A), instead of being duplicated. Symbol
     * sets that don't vary per variant (core/activation symbols, unique/surfaceOnly/frameOnly/insideOnly,
     * shapeless) are still read directly off this builder's own fields, since those are shared across
     * every variant by design.
     */
    private boolean validateVariantGeometry(ResourceLocation id, List<List<String>> layers, Map<Character, BlockIngredient> blockMap) {
        return validateLayerDimensions(id, layers)
                && resolveAndValidateCore(id, blockMap)
                && validateGeometryConstraints(id, layers)
                && validateUniqueCore(id, layers)
                && validateCoreActivationInPattern(id, layers);
    }

    /**
     * Every layer in a shaped pattern must have the same number of rows, and every row (across every
     * layer) must have the same length. {@code ShapedMatcher} (and the ghost overlay) computes each
     * layer's own center independently from that layer's own row count/row length - so a shorter layer
     * doesn't get treated as "the middle/edge is missing (implicitly air)", it silently anchors to one
     * side instead, producing an unintended offset/stepped shape rather than the symmetric one a
     * same-sized-but-with-holes layer would give. This is exactly what happened to the bundled KubeJS
     * example's second layer (two rows declared instead of three) - it "worked" with no error, but
     * matched a different shape than the pattern text visually suggests. Pad a shorter layer/row with
     * blank cells (spaces - already the "empty position" convention) instead of omitting them, so every
     * layer shares one common width/height and therefore one common center.
     */
    private boolean validateLayerDimensions(ResourceLocation id, List<List<String>> layers) {
        if (shapeless || layers.isEmpty()) return true;

        int expectedHeight = layers.get(0).size();
        int expectedWidth = expectedHeight == 0 ? 0 : layers.get(0).get(0).length();
        boolean ok = true;

        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            List<String> layer = layers.get(layerIdx);
            if (layer.size() != expectedHeight) {
                MultiLib.LOGGER.error(
                        "[MultiLib] Multiblock '{}': layer {} has {} row(s), but layer 0 has {} - every layer "
                                + "in a shaped pattern must have the same number of rows. Pad the shorter layer "
                                + "with blank rows (a row of spaces) instead of omitting them.",
                        id, layerIdx, layer.size(), expectedHeight);
                ok = false;
            }
            for (int row = 0; row < layer.size(); row++) {
                int rowWidth = layer.get(row).length();
                if (rowWidth != expectedWidth) {
                    MultiLib.LOGGER.error(
                            "[MultiLib] Multiblock '{}': layer {} row {} has length {}, but layer 0 row 0 has "
                                    + "length {} - every row in a shaped pattern must have the same length. Pad "
                                    + "the shorter row with trailing spaces instead of shortening it.",
                            id, layerIdx, row, rowWidth, expectedWidth);
                    ok = false;
                }
            }
        }
        return ok;
    }

    /** Statically validates unique/surfaceOnly/frameOnly/insideOnly against the textual pattern layers. */
    private boolean validateGeometryConstraints(ResourceLocation id, List<List<String>> layers) {
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

    /**
     * The core symbol marks a single controller position - a structure can't have two. Unlike
     * {@code unique()} (an opt-in constraint on any symbol), this is enforced unconditionally whenever
     * {@link #core(char)} was called: a dev who genuinely needs that same block to appear more than
     * once should use {@link #activation(char)} instead for the extra positions (a trigger, not a
     * unique controller).
     */
    private boolean validateUniqueCore(ResourceLocation id, List<List<String>> layers) {
        if (coreSymbol == '\0' || shapeless) return true;

        int count = 0;
        for (List<String> layer : layers) {
            for (String row : layer) {
                for (char c : row.toCharArray()) {
                    if (c == coreSymbol) count++;
                }
            }
        }
        if (count > 1) {
            MultiLib.LOGGER.error(
                    "[MultiLib] Multiblock '{}': core symbol '{}' occurs {} times in the pattern - a core "
                            + "marks a single controller position and must occur exactly once; activation only "
                            + "marks a formation trigger, not a unique controller.",
                    id, coreSymbol, count);
            failValidation(id, "use .activation() instead of .core() for a block used more than once");
            return false;
        }
        return true;
    }

    /**
     * A {@code core}/{@code activation} symbol that never actually occurs in the pattern is always a
     * mistake - usually a typo, or a leftover from an edited pattern - so it's rejected the same way a
     * duplicate core is, rather than silently forming a structure with no real controller/trigger.
     */
    private boolean validateCoreActivationInPattern(ResourceLocation id, List<List<String>> layers) {
        if (shapeless) return true;
        if (coreSymbol != '\0' && !patternContainsSymbol(coreSymbol, layers)) {
            MultiLib.LOGGER.error("[MultiLib] Multiblock '{}': core symbol '{}' does not occur anywhere in the pattern.", id, coreSymbol);
            failValidation(id, "core symbol not found in the pattern");
            return false;
        }
        if (activationSymbol != '\0' && !patternContainsSymbol(activationSymbol, layers)) {
            MultiLib.LOGGER.error("[MultiLib] Multiblock '{}': activation symbol '{}' does not occur anywhere in the pattern.", id, activationSymbol);
            failValidation(id, "activation symbol not found in the pattern");
            return false;
        }
        return true;
    }

    private boolean patternContainsSymbol(char symbol, List<List<String>> layers) {
        for (List<String> layer : layers) {
            for (String row : layer) {
                if (row.indexOf(symbol) >= 0) return true;
            }
        }
        return false;
    }

    /** Records {@code reason} as {@link #getLastValidationError()} and broadcasts it (unless silenced). */
    private void failValidation(ResourceLocation id, String reason) {
        lastValidationError = reason;
        notifyChat("Multiblock '" + id + "': " + reason);
    }

    /**
     * Delivers a chat error (in red, immediately to whoever's online and again to anyone who joins
     * later - see {@link MultiblockLoadErrorNotifier}) unless {@link #silenceDevModeChat()} was called
     * (KubeJS: it has its own console error, chat would just show the player the same thing twice).
     * With {@link CommonConfig#DEV_MODE} on, shows {@code message} itself; off, shows a generic
     * "check the log" notice instead - a player should still know *something* failed to load even
     * without dev mode, just not the technical detail that's really meant for whoever's debugging it.
     */
    private void notifyChat(String message) {
        if (suppressChatNotification) return;
        String chatMessage = CommonConfig.DEV_MODE.get()
                ? message
                : "An error occurred while loading a multiblock - check the server log for details.";
        MultiblockLoadErrorNotifier.notify(chatMessage);
    }
}
