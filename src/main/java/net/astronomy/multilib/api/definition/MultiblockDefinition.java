package net.astronomy.multilib.api.definition;

import net.astronomy.multilib.api.MultiLibAPI;
import net.astronomy.multilib.api.block.BlockDefinition;
import net.astronomy.multilib.api.callback.MultiblockAmbientCallback;
import net.astronomy.multilib.api.callback.MultiblockBrokenCallback;
import net.astronomy.multilib.api.callback.MultiblockFormedCallback;
import net.astronomy.multilib.api.callback.MultiblockTickCallback;
import net.astronomy.multilib.api.callback.MultiblockValidator;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.ingredient.IWallSharable;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.astronomy.multilib.core.registry.BlockDefinitionRegistry;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class MultiblockDefinition {
    private final ResourceLocation id;
    private final Map<Character, BlockIngredient> blockMap;
    private final List<List<String>> layers;
    private final RotationMode rotationMode;
    private final boolean requireAirInEmptyPositions;
    private final char activationSymbol;
    private final char coreSymbol;
    private final int priority;

    private final FormationMode formationMode;
    private final List<MultiblockFormedCallback> formedCallbacks;
    private final List<MultiblockBrokenCallback> brokenCallbacks;
    private final MultiblockTickCallback tickCallback;
    private final MultiblockAmbientCallback ambientCallback;
    private final int ambientIntervalTicks;
    private final MultiblockValidator validator;

    private final PatternProvider patternProvider;
    private final Vec3i boundingBox;
    private final Set<Character> optionalSymbols;
    private final Set<Integer> optionalLayerIndices;
    private final Map<Character, FreeBlockSpec> freeBlocks;
    private final boolean shapeless;
    private final Vec3i shapelessMinSize;
    private final Vec3i shapelessMaxSize;
    private final BlockIngredient shellIngredient;
    private final Map<Direction, BlockIngredient> shellFaces;
    private final BlockIngredient interiorIngredient;
    private final List<ShapelessRequirement> shapelessRequirements;
    private final boolean wallSharingEnabled;
    private final Map<Character, WallSharingMode> symbolWallSharingOverrides;
    private final boolean ghostOverlayDebug;
    private final ResourceLocation modelId;
    private final ResourceLocation iconItem;
    private final String nameTranslationKey;
    private final Set<Character> uniqueSymbols;
    private final Set<Character> surfaceOnlySymbols;
    private final Set<Character> frameOnlySymbols;
    private final Set<Character> insideOnlySymbols;
    /** Symbols whose block positions are NOT auto-hidden when {@link #hasModel()} is true. */
    private final Set<Character> keepVisibleSymbols;
    private final boolean autoPlace;
    private final boolean autoPlaceOverlay;
    private final Set<AllowedRotation> allowedRotations;
    private final Map<Character, List<TierSpec>> tierSpecs;
    private final String formedProperty;

    MultiblockDefinition(ResourceLocation id, Map<Character, BlockIngredient> blockMap,
                         List<List<String>> layers, RotationMode rotationMode,
                         boolean requireAirInEmptyPositions, char activationSymbol,
                         char coreSymbol, int priority,
                         FormationMode formationMode,
                         List<MultiblockFormedCallback> formedCallbacks,
                         List<MultiblockBrokenCallback> brokenCallbacks,
                         MultiblockTickCallback tickCallback,
                         MultiblockAmbientCallback ambientCallback,
                         int ambientIntervalTicks,
                         MultiblockValidator validator,
                         PatternProvider patternProvider,
                         Vec3i boundingBox,
                         Set<Character> optionalSymbols,
                         Set<Integer> optionalLayerIndices,
                         Map<Character, FreeBlockSpec> freeBlocks,
                         boolean shapeless,
                         Vec3i shapelessMinSize,
                         Vec3i shapelessMaxSize,
                         BlockIngredient shellIngredient,
                         Map<Direction, BlockIngredient> shellFaces,
                         BlockIngredient interiorIngredient,
                         List<ShapelessRequirement> shapelessRequirements,
                         boolean wallSharingEnabled,
                         Map<Character, WallSharingMode> symbolWallSharingOverrides,
                         boolean ghostOverlayDebug,
                         ResourceLocation modelId,
                         ResourceLocation iconItem,
                         String nameTranslationKey,
                         Set<Character> uniqueSymbols,
                         Set<Character> surfaceOnlySymbols,
                         Set<Character> frameOnlySymbols,
                         Set<Character> insideOnlySymbols,
                         Set<Character> keepVisibleSymbols,
                         boolean autoPlace,
                         boolean autoPlaceOverlay,
                         Set<AllowedRotation> allowedRotations,
                         Map<Character, List<TierSpec>> tierSpecs,
                         String formedProperty) {
        this.id = id;
        this.blockMap = Map.copyOf(blockMap);
        this.layers = layers.stream().map(List::copyOf).collect(Collectors.toUnmodifiableList());
        this.rotationMode = rotationMode;
        this.requireAirInEmptyPositions = requireAirInEmptyPositions;
        this.activationSymbol = activationSymbol;
        this.coreSymbol = coreSymbol;
        this.priority = priority;
        this.formationMode = formationMode;
        this.formedCallbacks = List.copyOf(formedCallbacks);
        this.brokenCallbacks = List.copyOf(brokenCallbacks);
        this.tickCallback = tickCallback;
        this.ambientCallback = ambientCallback;
        this.ambientIntervalTicks = ambientIntervalTicks;
        this.validator = validator;
        this.patternProvider = patternProvider;
        this.boundingBox = boundingBox;
        this.optionalSymbols = Set.copyOf(optionalSymbols);
        this.optionalLayerIndices = Set.copyOf(optionalLayerIndices);
        this.freeBlocks = Map.copyOf(freeBlocks);
        this.shapeless = shapeless;
        this.shapelessMinSize = shapelessMinSize;
        this.shapelessMaxSize = shapelessMaxSize;
        this.shellIngredient = shellIngredient;
        this.shellFaces = Map.copyOf(shellFaces);
        this.interiorIngredient = interiorIngredient;
        this.shapelessRequirements = List.copyOf(shapelessRequirements);
        this.wallSharingEnabled = wallSharingEnabled;
        this.symbolWallSharingOverrides = Map.copyOf(symbolWallSharingOverrides);
        this.ghostOverlayDebug = ghostOverlayDebug;
        this.modelId = modelId;
        this.iconItem = iconItem;
        this.nameTranslationKey = nameTranslationKey;
        this.uniqueSymbols = Set.copyOf(uniqueSymbols);
        this.surfaceOnlySymbols = Set.copyOf(surfaceOnlySymbols);
        this.frameOnlySymbols = Set.copyOf(frameOnlySymbols);
        this.insideOnlySymbols = Set.copyOf(insideOnlySymbols);
        this.keepVisibleSymbols = Set.copyOf(keepVisibleSymbols);
        this.autoPlace = autoPlace;
        this.autoPlaceOverlay = autoPlaceOverlay;
        this.allowedRotations = Set.copyOf(allowedRotations);
        this.tierSpecs = tierSpecs.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        this.formedProperty = formedProperty;
    }

    /**
     * Snapshots this definition into a fresh, mutable {@link MultiblockBuilder} pre-populated with
     * every field, for patching an already-registered definition (Java or JSON-sourced) instead of
     * declaring one from scratch. See {@link MultiblockBuilder#fromDefinition} - kept next to this
     * class's constructor deliberately, both need updating together when a field is added.
     */
    public MultiblockBuilder toBuilder() {
        return MultiblockBuilder.fromDefinition(this);
    }

    public ResourceLocation getId() { return id; }
    public Map<Character, BlockIngredient> getBlockMap() { return blockMap; }
    public List<List<String>> getLayers() { return layers; }
    public RotationMode getRotationMode() { return rotationMode; }
    public boolean isRequireAirInEmptyPositions() { return requireAirInEmptyPositions; }
    public char getActivationSymbol() { return activationSymbol; }
    public boolean hasActivation() { return activationSymbol != '\0'; }
    public char getCoreSymbol() { return coreSymbol; }
    public boolean hasCore() { return coreSymbol != '\0'; }
    public int getPriority() { return priority; }
    public int getLayerCount() { return layers.size(); }

    public FormationMode getFormationMode() { return formationMode; }
    public List<MultiblockFormedCallback> getFormedCallbacks() { return formedCallbacks; }
    public List<MultiblockBrokenCallback> getBrokenCallbacks() { return brokenCallbacks; }
    public Optional<MultiblockTickCallback> getTickCallback() { return Optional.ofNullable(tickCallback); }
    public Optional<MultiblockAmbientCallback> getAmbientCallback() { return Optional.ofNullable(ambientCallback); }
    public boolean hasTickCallback() { return tickCallback != null; }
    public boolean hasAmbientCallback() { return ambientCallback != null; }
    public int getAmbientIntervalTicks() { return ambientIntervalTicks; }
    public Optional<MultiblockValidator> getValidator() { return Optional.ofNullable(validator); }

    public Optional<PatternProvider> getPatternProvider() { return Optional.ofNullable(patternProvider); }
    public Vec3i getBoundingBox() { return boundingBox; }
    public Set<Character> getOptionalSymbols() { return optionalSymbols; }
    public boolean isOptional(char symbol) { return optionalSymbols.contains(symbol); }
    public Set<Integer> getOptionalLayerIndices() { return optionalLayerIndices; }
    public Map<Character, FreeBlockSpec> getFreeBlocks() { return freeBlocks; }
    public boolean isFreeBlock(char symbol) { return freeBlocks.containsKey(symbol); }
    public boolean isShapeless() { return shapeless; }
    public Vec3i getShapelessMinSize() { return shapelessMinSize; }
    public Vec3i getShapelessMaxSize() { return shapelessMaxSize; }
    public Optional<BlockIngredient> getShellIngredient() { return Optional.ofNullable(shellIngredient); }
    public Map<Direction, BlockIngredient> getShellFaces() { return shellFaces; }
    public Optional<BlockIngredient> getInteriorIngredient() { return Optional.ofNullable(interiorIngredient); }
    public List<ShapelessRequirement> getShapelessRequirements() { return shapelessRequirements; }
    public boolean isWallSharingEnabled() { return wallSharingEnabled; }
    public Map<Character, WallSharingMode> getSymbolWallSharingOverrides() { return symbolWallSharingOverrides; }
    public boolean isGhostOverlayDebug() { return ghostOverlayDebug; }
    public Optional<ResourceLocation> getModelId() { return Optional.ofNullable(modelId); }
    public boolean hasModel() { return modelId != null; }
    public Optional<ResourceLocation> getIconItem() { return Optional.ofNullable(iconItem); }
    public Optional<String> getNameTranslationKey() { return Optional.ofNullable(nameTranslationKey); }
    public Set<Character> getUniqueSymbols() { return uniqueSymbols; }
    public Set<Character> getSurfaceOnlySymbols() { return surfaceOnlySymbols; }
    public Set<Character> getFrameOnlySymbols() { return frameOnlySymbols; }
    public Set<Character> getInsideOnlySymbols() { return insideOnlySymbols; }
    public Set<Character> getKeepVisibleSymbols() { return keepVisibleSymbols; }
    public boolean isAutoPlace() { return autoPlace; }
    public boolean isAutoPlaceOverlay() { return autoPlaceOverlay; }
    public Set<AllowedRotation> getAllowedRotations() { return allowedRotations; }
    /** Tier levels declared via {@code .tier(...)} for each symbol, in ascending order (index 0 = lowest). */
    public Map<Character, List<TierSpec>> getTierSpecs() { return tierSpecs; }
    /**
     * Name of the {@code BooleanProperty} (e.g. {@code "lit"}) that every member block's state gets
     * flipped true/false on as the structure forms/breaks, if set via
     * {@code MultiblockBuilder#formedProperty(String)}. Empty = feature disabled (no state is touched).
     */
    public Optional<String> getFormedProperty() { return Optional.ofNullable(formedProperty); }
    public boolean hasFormedProperty() { return formedProperty != null; }

    public WallSharingMode getWallSharingMode(char symbol) {
        if (symbol == coreSymbol || symbol == activationSymbol) {
            if (symbolWallSharingOverrides.containsKey(symbol)) {
                return symbolWallSharingOverrides.get(symbol);
            }
            Optional<Boolean> blockOverride = getBlockLevelWallSharingOverride(symbol);
            if (blockOverride.isPresent()) {
                return blockOverride.get() ? WallSharingMode.ENABLED : WallSharingMode.DISABLED;
            }
            return WallSharingMode.DISABLED;
        }

        if (symbolWallSharingOverrides.containsKey(symbol)) {
            return symbolWallSharingOverrides.get(symbol);
        }

        BlockIngredient ingredient = blockMap.get(symbol);
        if (ingredient != null) {
            Set<Block> candidates = ingredient.getCandidateBlocks();
            if (candidates.size() == 1) {
                Block block = candidates.iterator().next();
                if (block instanceof IWallSharable ws) {
                    return ws.getDefaultWallSharingMode();
                }
                Optional<WallSharingMode> registered = MultiLibAPI.getRegisteredWallSharingMode(block);
                if (registered.isPresent()) {
                    return registered.get();
                }
            }
        }

        return wallSharingEnabled ? WallSharingMode.ENABLED : WallSharingMode.DISABLED;
    }

    private Optional<Boolean> getBlockLevelWallSharingOverride(char symbol) {
        BlockIngredient ingredient = blockMap.get(symbol);
        if (ingredient == null) return Optional.empty();
        for (Block block : ingredient.getCandidateBlocks()) {
            Optional<BlockDefinition> bd = BlockDefinitionRegistry.get(block);
            if (bd.isPresent() && bd.get().getWallSharingOverride().isPresent()) {
                return bd.get().getWallSharingOverride();
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the given block state matches this structure's activation symbol, core symbol, or
     * both - they're often the same symbol, but a structure can split them (e.g. activation = the
     * block placed last, core = the controller). Used to decide whether to react to a block at all.
     */
    /** Whether the given block state matches this structure's core symbol specifically. */
    public boolean matchesCore(BlockState state) {
        if (!hasCore()) return false;
        BlockIngredient ing = blockMap.get(coreSymbol);
        return ing != null && ing.matches(state);
    }

    public boolean matchesActivationOrCore(BlockState state) {
        if (hasActivation()) {
            BlockIngredient ing = blockMap.get(activationSymbol);
            if (ing != null && ing.matches(state)) return true;
        }
        if (hasCore()) {
            BlockIngredient ing = blockMap.get(coreSymbol);
            if (ing != null && ing.matches(state)) return true;
        }
        return false;
    }

    public Set<Block> getCandidateBlocks() {
        Set<Block> result = new HashSet<>();
        for (BlockIngredient ingredient : blockMap.values()) {
            result.addAll(ingredient.getCandidateBlocks());
        }
        if (patternProvider != null) {
            // PatternProvider-based definitions register no candidate blocks; always-checked path.
        }
        return result;
    }
}
