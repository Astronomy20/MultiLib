package net.astronomy.multilib.core.matching;

import net.astronomy.multilib.api.definition.AllowedRotation;
import net.astronomy.multilib.api.definition.FreeBlockSpec;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.definition.RotationAxis;
import net.astronomy.multilib.api.definition.RotationMode;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.util.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ShapedMatcher implements IPatternMatcher {

    // Hoisted out of the per-cell transform loops, which run once per pattern cell per match attempt.
    private static final String[] VERTICAL_AXES = {"X", "Z", "X_FLIP", "Z_FLIP"};
    private static final String[] Y_AXIS_ONLY = {"Y"};
    private static final String[] X_AXES = {"X", "X_FLIP"};
    private static final String[] Z_AXES = {"Z", "Z_FLIP"};

    @Override
    public MatchResult matches(ServerLevel level, BlockPos activationPos, MultiblockDefinition definition) {
        Set<AllowedRotation> allowedRotations = definition.getAllowedRotations();
        boolean allowHorizontal = !allowedRotations.isEmpty()
                ? true // the unrotated/Y-axis origin candidate is always needed as the baseline
                : definition.getRotationMode() != RotationMode.NONE;
        boolean allowVertical = !allowedRotations.isEmpty()
                ? allowedRotations.stream().anyMatch(ar -> ar.axis() != RotationAxis.Y)
                : definition.getRotationMode() == RotationMode.ALL;

        List<List<List<String>>> layerCombinations = buildLayerCombinations(definition);
        int orientationsTried = 0;

        for (List<List<String>> filteredLayers : layerCombinations) {
            if (filteredLayers.isEmpty()) continue;
            int layersCount = filteredLayers.size();

            for (int layerIndex = 0; layerIndex < layersCount; layerIndex++) {
                var layer = filteredLayers.get(layerIndex);
                int centerX = layer.isEmpty() ? 0 : layer.get(0).length() / 2;
                int centerZ = layer.size() / 2;

                for (int row = 0; row < layer.size(); row++) {
                    String line = layer.get(row);
                    for (int col = 0; col < line.length(); col++) {
                        char symbol = line.charAt(col);
                        if (symbol == ' ' || !definition.getBlockMap().containsKey(symbol)) continue;

                        // This cell's pattern-local offset (axis=Y, rotation=0 convention - same one
                        // matchesTransformed/applyTransform use). The candidate origin depends on which
                        // (axis, rotation) is being tried, so it's computed per-transform inside
                        // tryAllTransformsForCell rather than fixed once here: a single origin guessed
                        // under the rotation=0 assumption is only correct for rotation=0 itself, so
                        // testing other rotations against it (as the old code did) could never actually
                        // find a structure built in any rotated orientation.
                        int relX = col - centerX;
                        int relY = (layersCount - 1) - layerIndex;
                        int relZ = row - centerZ;

                        MatchData found = tryAllTransformsForCell(
                                level, activationPos, relX, relY, relZ, definition, filteredLayers,
                                allowHorizontal, allowVertical);
                        if (found != null) return new MatchResult.Success(found);
                        orientationsTried += countTransforms(allowHorizontal, allowVertical);
                    }
                }
            }
        }

        List<List<String>> primaryLayers = layerCombinations.isEmpty()
                ? definition.getLayers()
                : layerCombinations.get(0);
        MatchFailureReport report = buildFailureReport(level, activationPos, definition, primaryLayers, orientationsTried);
        return new MatchResult.Failure(report);
    }

    /**
     * Tries every candidate (axis, rotation) transform for one pattern cell, assuming that cell is the
     * block at {@code activationPos}. Each transform gets its own origin - {@code origin = activationPos
     * - applyTransform(relX, relY, relZ, axis, rotation)} - since a structure actually built rotated
     * only lines up against the origin computed for that same rotation, not against the rotation=0
     * origin tested at a different angle.
     */
    private MatchData tryAllTransformsForCell(ServerLevel level, BlockPos activationPos,
                                              int relX, int relY, int relZ,
                                              MultiblockDefinition definition,
                                              List<List<String>> filteredLayers,
                                              boolean allowHorizontal, boolean allowVertical) {
        if (!definition.getAllowedRotations().isEmpty()) {
            return tryGranularTransformsForCell(level, activationPos, relX, relY, relZ, definition, filteredLayers);
        }
        if (allowHorizontal) {
            for (int rotation = 0; rotation < 4; rotation++) {
                BlockPos origin = originForTransform(activationPos, relX, relY, relZ, "Y", rotation);
                if (matchesTransformed(level, origin, definition, filteredLayers, rotation, "Y")) {
                    return collectMatchData(level, origin, definition, filteredLayers, rotation, "Y");
                }
            }
        }
        if (allowVertical) {
            for (String axis : VERTICAL_AXES) {
                for (int rotation = 0; rotation < 4; rotation++) {
                    BlockPos origin = originForTransform(activationPos, relX, relY, relZ, axis, rotation);
                    if (matchesTransformed(level, origin, definition, filteredLayers, rotation, axis)) {
                        return collectMatchData(level, origin, definition, filteredLayers, rotation, axis);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Tries only the unrotated orientation plus the specific axis/angle combinations declared via
     * {@code .allowRotation(...)}, instead of every rotation the coarse {@link RotationMode} allows.
     */
    private MatchData tryGranularTransformsForCell(ServerLevel level, BlockPos activationPos,
                                                    int relX, int relY, int relZ,
                                                    MultiblockDefinition definition,
                                                    List<List<String>> filteredLayers) {
        BlockPos baseOrigin = originForTransform(activationPos, relX, relY, relZ, "Y", 0);
        if (matchesTransformed(level, baseOrigin, definition, filteredLayers, 0, "Y")) {
            return collectMatchData(level, baseOrigin, definition, filteredLayers, 0, "Y");
        }
        for (AllowedRotation allowed : definition.getAllowedRotations()) {
            int step = allowed.normalizedAngle() / 90;
            String[] axesToTry = switch (allowed.axis()) {
                case Y -> Y_AXIS_ONLY;
                case X -> X_AXES;
                case Z -> Z_AXES;
            };
            for (String axisStr : axesToTry) {
                BlockPos origin = originForTransform(activationPos, relX, relY, relZ, axisStr, step);
                if (matchesTransformed(level, origin, definition, filteredLayers, step, axisStr)) {
                    return collectMatchData(level, origin, definition, filteredLayers, step, axisStr);
                }
            }
        }
        return null;
    }

    /** origin such that {@code activationPos == origin.offset(applyTransform(relX, relY, relZ, axis, rotation))}. */
    private static BlockPos originForTransform(BlockPos activationPos, int relX, int relY, int relZ,
                                               String axis, int rotation) {
        int[] t = applyTransform(relX, relY, relZ, axis, rotation);
        return activationPos.offset(-t[0], -t[1], -t[2]);
    }

    private boolean matchesTransformed(ServerLevel level, BlockPos origin, MultiblockDefinition definition,
                                       List<List<String>> filteredLayers, int rotation, String axis) {
        Map<Character, BlockIngredient> blockMap = definition.getBlockMap();
        Set<Character> optionalSymbols = definition.getOptionalSymbols();
        int layersCount = filteredLayers.size();

        for (int yOffset = 0; yOffset < layersCount; yOffset++) {
            var layer = filteredLayers.get(yOffset);
            int height = layer.size();
            int width = height == 0 ? 0 : layer.get(0).length();
            int centerX = width / 2;
            int centerZ = height / 2;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < line.length(); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;
                    BlockIngredient ingredient = blockMap.get(symbol);
                    if (ingredient == null) continue;

                    int relX = col - centerX;
                    int relY = (layersCount - 1) - yOffset; // layers[0] = topmost declared layer, layers[last] = bottommost
                    int relZ = row - centerZ;

                    int[] t = applyTransform(relX, relY, relZ, axis, rotation);
                    BlockPos checkPos = origin.offset(t[0], t[1], t[2]);

                    if (!ingredient.matches(level, checkPos, level.getBlockState(checkPos))) {
                        if (optionalSymbols.contains(symbol)) continue;
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private MatchData collectMatchData(ServerLevel level, BlockPos origin, MultiblockDefinition definition,
                                       List<List<String>> filteredLayers, int rotation, String axis) {
        Map<Character, BlockIngredient> blockMap = definition.getBlockMap();
        boolean isVertical = !axis.equals("Y");
        int layersCount = filteredLayers.size();

        Map<Character, Set<BlockPos>> symbolPos = new HashMap<>();
        Set<BlockPos> allPos = new HashSet<>();
        int maxWidth = 0, maxHeight = 0;

        for (int yOffset = 0; yOffset < layersCount; yOffset++) {
            var layer = filteredLayers.get(yOffset);
            int height = layer.size();
            int width = height == 0 ? 0 : layer.get(0).length();
            maxWidth = Math.max(maxWidth, width);
            maxHeight = Math.max(maxHeight, height);
            int centerX = width / 2;
            int centerZ = height / 2;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < line.length(); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ' || !blockMap.containsKey(symbol)) continue;

                    int relX = col - centerX;
                    int relY = (layersCount - 1) - yOffset; // layers[0] = topmost declared layer, layers[last] = bottommost
                    int relZ = row - centerZ;

                    int[] t = applyTransform(relX, relY, relZ, axis, rotation);
                    BlockPos worldPos = origin.offset(t[0], t[1], t[2]);
                    allPos.add(worldPos);
                    symbolPos.computeIfAbsent(symbol, k -> new HashSet<>()).add(worldPos);
                }
            }
        }

        Map<Character, FreeBlockSpec> freeBlocks = definition.getFreeBlocks();
        if (!freeBlocks.isEmpty()) {
            scanFreeBlocks(level, origin, definition, filteredLayers, rotation, axis,
                    maxWidth, maxHeight, layersCount, allPos, symbolPos, freeBlocks);
        }

        Map<Character, Set<BlockPos>> immutableSymbolPos = symbolPos.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Collections.unmodifiableSet(e.getValue())));

        Vec3i dims = new Vec3i(maxWidth, layersCount, maxHeight);
        return new MatchData(
                origin,
                new TransformData(rotation, isVertical, axis),
                Collections.unmodifiableSet(allPos),
                immutableSymbolPos,
                dims
        );
    }

    private void scanFreeBlocks(ServerLevel level, BlockPos origin, MultiblockDefinition definition,
                                List<List<String>> filteredLayers, int rotation, String axis,
                                int maxWidth, int maxHeight, int layersCount,
                                Set<BlockPos> allPos, Map<Character, Set<BlockPos>> symbolPos,
                                Map<Character, FreeBlockSpec> freeBlocks) {
        int layerCenterX = maxWidth / 2;
        int layerCenterZ = maxHeight / 2;

        for (int yOffset = 0; yOffset < layersCount; yOffset++) {
            for (int row = 0; row < maxHeight; row++) {
                for (int col = 0; col < maxWidth; col++) {
                    int relX = col - layerCenterX;
                    int relY = (layersCount - 1) - yOffset; // layers[0] = topmost declared layer, layers[last] = bottommost
                    int relZ = row - layerCenterZ;

                    int[] t = applyTransform(relX, relY, relZ, axis, rotation);
                    BlockPos pos = origin.offset(t[0], t[1], t[2]);

                    if (allPos.contains(pos)) continue;

                    BlockState state = level.getBlockState(pos);
                    for (Map.Entry<Character, FreeBlockSpec> entry : freeBlocks.entrySet()) {
                        FreeBlockSpec spec = entry.getValue();
                        if (!spec.ingredient().matches(level, pos, state)) continue;
                        if (spec.allowedPositions() != null) {
                            BlockPos relPos = new BlockPos(relX, relY, relZ);
                            if (!spec.allowedPositions().contains(relPos)) continue;
                        }
                        symbolPos.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add(pos);
                        allPos.add(pos);
                        break;
                    }
                }
            }
        }

        // Validate min/max counts for freeBlocks
        for (Map.Entry<Character, FreeBlockSpec> entry : freeBlocks.entrySet()) {
            char key = entry.getKey();
            FreeBlockSpec spec = entry.getValue();
            int count = symbolPos.getOrDefault(key, Set.of()).size();
            if (count < spec.min() || count > spec.max()) {
                // Remove freeBlock positions to signal failure via the returned map. Capture the
                // removed set BEFORE dropping the map entry - the previous order removed the entry
                // first and then removeAll'd against the now-empty lookup, leaving every rejected
                // freeBlock position stranded in allPos.
                Set<BlockPos> removed = symbolPos.remove(key);
                if (removed != null) allPos.removeAll(removed);
            }
        }
    }

    private static List<List<List<String>>> buildLayerCombinations(MultiblockDefinition definition) {
        List<List<String>> allLayers = definition.getLayers();
        List<Integer> optIndices = new ArrayList<>(definition.getOptionalLayerIndices());
        int n = optIndices.size();

        List<List<List<String>>> combinations = new ArrayList<>();
        // mask=0 → no optional layers excluded (full match first)
        for (int mask = 0; mask < (1 << n); mask++) {
            Set<Integer> excluded = new HashSet<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) excluded.add(optIndices.get(i));
            }
            List<List<String>> filtered = new ArrayList<>();
            for (int i = 0; i < allLayers.size(); i++) {
                if (!excluded.contains(i)) filtered.add(allLayers.get(i));
            }
            if (!filtered.isEmpty()) combinations.add(filtered);
        }
        return combinations;
    }

    private static int countTransforms(boolean allowHorizontal, boolean allowVertical) {
        int count = 0;
        if (allowHorizontal) count += 4;
        if (allowVertical) count += 4 + 4 + 4 + 4;
        return count;
    }

    private static MatchFailureReport buildFailureReport(ServerLevel level, BlockPos placedPos,
                                                         MultiblockDefinition definition,
                                                         List<List<String>> layers,
                                                         int orientationsTried) {
        boolean allowHorizontal = definition.getRotationMode() != RotationMode.NONE;
        boolean allowVertical = definition.getRotationMode() == RotationMode.ALL;
        int layersCount = layers.size();
        int bottomLayer = layersCount - 1; // layers[0] = topmost declared layer, layers[bottomLayer] = bottommost
        var blockMap = definition.getBlockMap();
        int totalPositions = countTotalPositions(definition, layers);

        List<MatchFailureReport.FailedPosition> bestMismatches = null;
        int bestCorrect = -1;

        for (int layerIndex = 0; layerIndex < layersCount; layerIndex++) {
            var layer = layers.get(layerIndex);
            int centerX = layer.isEmpty() ? 0 : layer.get(0).length() / 2;
            int centerZ = layer.size() / 2;

            for (int row = 0; row < layer.size(); row++) {
                String line = layer.get(row);
                for (int col = 0; col < line.length(); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ' || !blockMap.containsKey(symbol)) continue;

                    List<BlockPos> origins = new ArrayList<>();
                    if (allowHorizontal) {
                        origins.add(placedPos.offset(
                                -(col - centerX), (layerIndex - bottomLayer), -(row - centerZ)));
                    }
                    if (allowVertical) {
                        origins.add(placedPos.offset(
                                (layerIndex - bottomLayer), (col - centerX), -(row - centerZ)));
                        origins.add(placedPos.offset(
                                -(col - centerX), (row - centerZ), (layerIndex - bottomLayer)));
                    }

                    for (BlockPos origin : origins) {
                        for (String[] axisInfo : getAllowedAxes(allowHorizontal, allowVertical)) {
                            for (int rotation = 0; rotation < 4; rotation++) {
                                List<MatchFailureReport.FailedPosition> mismatches =
                                        getMismatches(level, origin, definition, layers, rotation, axisInfo[0]);
                                int correct = totalPositions - mismatches.size();
                                if (correct > bestCorrect) {
                                    bestCorrect = correct;
                                    bestMismatches = mismatches;
                                }
                            }
                        }
                    }
                }
            }
        }

        List<MatchFailureReport.FailedPosition> finalMismatches =
                bestMismatches != null ? bestMismatches : List.of();
        String summary = orientationsTried == 0
                ? "No orientations tried"
                : finalMismatches.size() + " of " + totalPositions
                + " positions mismatch in best attempt";
        return new MatchFailureReport(orientationsTried, finalMismatches, summary);
    }

    private static String[][] getAllowedAxes(boolean allowHorizontal, boolean allowVertical) {
        List<String[]> axes = new ArrayList<>();
        if (allowHorizontal) axes.add(new String[]{"Y"});
        if (allowVertical) {
            axes.add(new String[]{"X"});
            axes.add(new String[]{"Z"});
            axes.add(new String[]{"X_FLIP"});
            axes.add(new String[]{"Z_FLIP"});
        }
        return axes.toArray(new String[0][]);
    }

    private static List<MatchFailureReport.FailedPosition> getMismatches(ServerLevel level, BlockPos origin,
                                                                          MultiblockDefinition definition,
                                                                          List<List<String>> layers,
                                                                          int rotation, String axis) {
        List<MatchFailureReport.FailedPosition> mismatches = new ArrayList<>();
        var blockMap = definition.getBlockMap();
        int layersCount = layers.size();

        for (int yOffset = 0; yOffset < layersCount; yOffset++) {
            var layer = layers.get(yOffset);
            int height = layer.size();
            int width = height == 0 ? 0 : layer.get(0).length();
            int centerX = width / 2;
            int centerZ = height / 2;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < line.length(); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;
                    BlockIngredient ingredient = blockMap.get(symbol);
                    if (ingredient == null) continue;

                    int relX = col - centerX;
                    int relY = (layersCount - 1) - yOffset; // layers[0] = topmost declared layer, layers[last] = bottommost
                    int relZ = row - centerZ;

                    int[] t = applyTransform(relX, relY, relZ, axis, rotation);
                    BlockPos checkPos = origin.offset(t[0], t[1], t[2]);
                    BlockState state = level.getBlockState(checkPos);

                    if (!ingredient.matches(level, checkPos, state)) {
                        mismatches.add(new MatchFailureReport.FailedPosition(
                                checkPos, state, describeIngredient(ingredient)));
                    }
                }
            }
        }
        return mismatches;
    }

    /**
     * Composes a fixed "tip" (rotating the layer-stacking axis, normally Y, onto the target axis)
     * with a "spin" (the {@code rotation} parameter, around that now-vertical target axis). Both
     * steps are proper rotations (not coordinate swaps/reflections), so this stays chirality-correct.
     * The tip direction for X_FLIP/Z_FLIP is the opposite 90° turn, landing the layer axis on the
     * negative target axis instead of the positive one. axis="Y" needs no tip - it's already vertical.
     */
    public static int[] applyTransform(int relX, int relY, int relZ, String axis, int rotation) {
        return switch (axis) {
            case "X" -> {
                int[] tipped = RotationUtils.rotate(relX, relY, relZ, "Z", 90);
                yield RotationUtils.rotate(tipped[0], tipped[1], tipped[2], "X", rotation * 90);
            }
            case "X_FLIP" -> {
                int[] tipped = RotationUtils.rotate(relX, relY, relZ, "Z", 270);
                yield RotationUtils.rotate(tipped[0], tipped[1], tipped[2], "X", rotation * 90);
            }
            case "Z" -> {
                int[] tipped = RotationUtils.rotate(relX, relY, relZ, "X", 90);
                yield RotationUtils.rotate(tipped[0], tipped[1], tipped[2], "Z", rotation * 90);
            }
            case "Z_FLIP" -> {
                int[] tipped = RotationUtils.rotate(relX, relY, relZ, "X", 270);
                yield RotationUtils.rotate(tipped[0], tipped[1], tipped[2], "Z", rotation * 90);
            }
            default -> RotationUtils.rotate(relX, relY, relZ, axis, rotation * 90);
        };
    }

    private static String describeIngredient(BlockIngredient ingredient) {
        Set<Block> candidates = ingredient.getCandidateBlocks();
        if (!candidates.isEmpty()) {
            return BuiltInRegistries.BLOCK.getKey(candidates.iterator().next()).toString();
        }
        return ingredient.getClass().getSimpleName();
    }

    private static int countTotalPositions(MultiblockDefinition definition, List<List<String>> layers) {
        int count = 0;
        for (List<String> layer : layers) {
            for (String row : layer) {
                for (char c : row.toCharArray()) {
                    if (c != ' ' && definition.getBlockMap().containsKey(c)) count++;
                }
            }
        }
        return count;
    }
}
