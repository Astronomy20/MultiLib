package net.astronomy.multilib.core.matching;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.definition.RotationMode;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.utils.RotationUtils;
import net.minecraft.core.BlockPos;
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

public class PatternMatcher {

    private static final ShapedMatcher SHAPED = new ShapedMatcher();
    private static final ShapelessMatcher SHAPELESS = new ShapelessMatcher();
    private static final FunctionalMatcher FUNCTIONAL = new FunctionalMatcher();

    public static MatchResult matches(ServerLevel level, BlockPos placedPos, MultiblockDefinition definition) {
        if (definition.isShapeless()) {
            return SHAPELESS.matches(level, placedPos, definition);
        }
        if (definition.getPatternProvider().isPresent()) {
            return FUNCTIONAL.matches(level, placedPos, definition);
        }
        return SHAPED.matches(level, placedPos, definition);
    }

    /** @deprecated Internal legacy path kept for compatibility — use {@link #matches} */
    @Deprecated
    static MatchResult matchesLegacy(ServerLevel level, BlockPos placedPos, MultiblockDefinition definition) {
        int layersCount = definition.getLayerCount();
        int topLayer = layersCount - 1;
        var layers = definition.getLayers();
        var blockMap = definition.getBlockMap();

        boolean allowHorizontal = definition.getRotationMode() != RotationMode.NONE;
        boolean allowVertical = definition.getRotationMode() == RotationMode.ALL;

        int orientationsTried = 0;

        for (int layerIndex = 0; layerIndex < layersCount; layerIndex++) {
            var layer = layers.get(layerIndex);
            int centerX = layer.getFirst().length() / 2;
            int centerZ = layer.size() / 2;

            for (int row = 0; row < layer.size(); row++) {
                String line = layer.get(row);
                for (int col = 0; col < line.length(); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ' || !blockMap.containsKey(symbol)) continue;

                    if (allowHorizontal) {
                        BlockPos origin = placedPos.offset(
                                -(col - centerX), -(layerIndex - topLayer), -(row - centerZ));
                        MatchData found = tryAllTransformsForOrigin(
                                level, origin, definition, allowHorizontal, allowVertical);
                        if (found != null) return new MatchResult.Success(found);
                        orientationsTried += countTransforms(allowHorizontal, allowVertical);
                    }

                    if (allowVertical) {
                        BlockPos originX = placedPos.offset(
                                -(layerIndex - topLayer), -(col - centerX), -(row - centerZ));
                        MatchData found = tryAllTransformsForOrigin(
                                level, originX, definition, allowHorizontal, allowVertical);
                        if (found != null) return new MatchResult.Success(found);
                        orientationsTried += countTransforms(allowHorizontal, allowVertical);

                        BlockPos originZ = placedPos.offset(
                                -(col - centerX), -(row - centerZ), -(layerIndex - topLayer));
                        found = tryAllTransformsForOrigin(
                                level, originZ, definition, allowHorizontal, allowVertical);
                        if (found != null) return new MatchResult.Success(found);
                        orientationsTried += countTransforms(allowHorizontal, allowVertical);
                    }
                }
            }
        }

        // Lazy failure report — only generated when no match is found
        MatchFailureReport report = buildFailureReport(level, placedPos, definition, orientationsTried);
        return new MatchResult.Failure(report);
    }

    private static MatchData tryAllTransformsForOrigin(ServerLevel level, BlockPos origin,
                                                        MultiblockDefinition definition,
                                                        boolean allowHorizontal, boolean allowVertical) {
        if (allowHorizontal) {
            for (int rotation = 0; rotation < 4; rotation++) {
                if (matchesTransformed(level, origin, definition, rotation, "Y")) {
                    return collectMatchData(level, origin, definition, rotation, "Y");
                }
            }
        }

        if (allowVertical) {
            for (int rotation = 0; rotation < 4; rotation++) {
                if (matchesTransformed(level, origin, definition, rotation, "X")) {
                    return collectMatchData(level, origin, definition, rotation, "X");
                }
            }
            for (int rotation = 0; rotation < 4; rotation++) {
                if (matchesTransformed(level, origin, definition, rotation, "Z")) {
                    return collectMatchData(level, origin, definition, rotation, "Z");
                }
            }
            for (int rotation = 0; rotation < 4; rotation++) {
                if (matchesTransformed(level, origin, definition, rotation, "X_FLIP")) {
                    return collectMatchData(level, origin, definition, rotation, "X_FLIP");
                }
                if (matchesTransformed(level, origin, definition, rotation, "Z_FLIP")) {
                    return collectMatchData(level, origin, definition, rotation, "Z_FLIP");
                }
            }
        }

        return null;
    }

    private static int countTransforms(boolean allowHorizontal, boolean allowVertical) {
        int count = 0;
        if (allowHorizontal) count += 4;
        if (allowVertical) count += 4 + 4 + 4 + 4; // X, Z, X_FLIP, Z_FLIP each with 4 rotations
        return count;
    }

    private static MatchFailureReport buildFailureReport(ServerLevel level, BlockPos placedPos,
                                                         MultiblockDefinition definition,
                                                         int orientationsTried) {
        boolean allowHorizontal = definition.getRotationMode() != RotationMode.NONE;
        boolean allowVertical = definition.getRotationMode() == RotationMode.ALL;
        int layersCount = definition.getLayerCount();
        int topLayer = layersCount - 1;
        var layers = definition.getLayers();
        var blockMap = definition.getBlockMap();
        int totalPositions = countTotalPositions(definition);

        List<MatchFailureReport.FailedPosition> bestMismatches = null;
        int bestCorrect = -1;

        for (int layerIndex = 0; layerIndex < layersCount; layerIndex++) {
            var layer = layers.get(layerIndex);
            int centerX = layer.getFirst().length() / 2;
            int centerZ = layer.size() / 2;

            for (int row = 0; row < layer.size(); row++) {
                String line = layer.get(row);
                for (int col = 0; col < line.length(); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ' || !blockMap.containsKey(symbol)) continue;

                    List<BlockPos> origins = new ArrayList<>();
                    if (allowHorizontal) {
                        origins.add(placedPos.offset(
                                -(col - centerX), -(layerIndex - topLayer), -(row - centerZ)));
                    }
                    if (allowVertical) {
                        origins.add(placedPos.offset(
                                -(layerIndex - topLayer), -(col - centerX), -(row - centerZ)));
                        origins.add(placedPos.offset(
                                -(col - centerX), -(row - centerZ), -(layerIndex - topLayer)));
                    }

                    for (BlockPos origin : origins) {
                        for (String[] axisInfo : getAllowedAxes(allowHorizontal, allowVertical)) {
                            for (int rotation = 0; rotation < 4; rotation++) {
                                List<MatchFailureReport.FailedPosition> mismatches =
                                        getMismatches(level, origin, definition, rotation, axisInfo[0]);
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

    private static MatchData collectMatchData(ServerLevel level, BlockPos origin,
                                              MultiblockDefinition definition,
                                              int rotation, String axis) {
        int layersCount = definition.getLayerCount();
        var layers = definition.getLayers();
        var blockMap = definition.getBlockMap();
        boolean isVertical = !axis.equals("Y");

        Map<Character, Set<BlockPos>> symbolPos = new HashMap<>();
        Set<BlockPos> allPos = new HashSet<>();

        for (int yOffset = 0; yOffset < layersCount; yOffset++) {
            var layer = layers.get(yOffset);
            int height = layer.size();
            int width = layer.getFirst().length();
            int centerX = width / 2;
            int centerZ = height / 2;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < width; col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ' || !blockMap.containsKey(symbol)) continue;

                    int relX = col - centerX;
                    int relY = yOffset - (layersCount - 1);
                    int relZ = row - centerZ;

                    int[] transformed = applyTransform(relX, relY, relZ, axis, rotation);
                    BlockPos worldPos = origin.offset(transformed[0], transformed[1], transformed[2]);
                    allPos.add(worldPos);
                    symbolPos.computeIfAbsent(symbol, k -> new HashSet<>()).add(worldPos);
                }
            }
        }

        Map<Character, Set<BlockPos>> immutableSymbolPos = symbolPos.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> Collections.unmodifiableSet(e.getValue())
                ));

        return new MatchData(
                origin,
                new TransformData(rotation, isVertical, axis),
                Collections.unmodifiableSet(allPos),
                immutableSymbolPos
        );
    }

    private static List<MatchFailureReport.FailedPosition> getMismatches(ServerLevel level, BlockPos origin,
                                                                          MultiblockDefinition definition,
                                                                          int rotation, String axis) {
        List<MatchFailureReport.FailedPosition> mismatches = new ArrayList<>();
        int layersCount = definition.getLayerCount();
        var layers = definition.getLayers();
        var blockMap = definition.getBlockMap();

        for (int yOffset = 0; yOffset < layersCount; yOffset++) {
            var layer = layers.get(yOffset);
            int height = layer.size();
            int width = layer.getFirst().length();
            int centerX = width / 2;
            int centerZ = height / 2;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < width; col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;

                    BlockIngredient ingredient = blockMap.get(symbol);
                    if (ingredient == null) continue;

                    int relX = col - centerX;
                    int relY = yOffset - (layersCount - 1);
                    int relZ = row - centerZ;

                    int[] transformed = applyTransform(relX, relY, relZ, axis, rotation);
                    BlockPos checkPos = origin.offset(transformed[0], transformed[1], transformed[2]);
                    BlockState state = level.getBlockState(checkPos);

                    if (!ingredient.matches(state)) {
                        mismatches.add(new MatchFailureReport.FailedPosition(
                                checkPos, state, describeIngredient(ingredient)));
                    }
                }
            }
        }

        return mismatches;
    }

    public static boolean matchesTransformed(ServerLevel level, BlockPos origin, MultiblockDefinition definition,
                                             int rotation, String axis) {
        int layersCount = definition.getLayerCount();
        var layers = definition.getLayers();
        var blockMap = definition.getBlockMap();

        for (int yOffset = 0; yOffset < layersCount; yOffset++) {
            var layer = layers.get(yOffset);
            int height = layer.size();
            int width = layer.getFirst().length();
            int centerX = width / 2;
            int centerZ = height / 2;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < width; col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;

                    BlockIngredient ingredient = blockMap.get(symbol);
                    if (ingredient == null) continue;

                    int relX = col - centerX;
                    int relY = yOffset - (layersCount - 1);
                    int relZ = row - centerZ;

                    int[] transformed = applyTransform(relX, relY, relZ, axis, rotation);
                    BlockPos checkPos = origin.offset(transformed[0], transformed[1], transformed[2]);

                    if (!ingredient.matches(level.getBlockState(checkPos))) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private static int[] applyTransform(int relX, int relY, int relZ, String axis, int rotation) {
        return switch (axis) {
            case "X_FLIP" -> RotationUtils.rotate(relX, -relY, relZ, "X", rotation * 90);
            case "Z_FLIP" -> RotationUtils.rotate(relX, -relY, relZ, "Z", rotation * 90);
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

    private static int countTotalPositions(MultiblockDefinition definition) {
        int count = 0;
        for (List<String> layer : definition.getLayers()) {
            for (String row : layer) {
                for (char c : row.toCharArray()) {
                    if (c != ' ' && definition.getBlockMap().containsKey(c)) count++;
                }
            }
        }
        return count;
    }
}
