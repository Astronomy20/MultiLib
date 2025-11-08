package net.astronomy.multilib.pattern;

import net.astronomy.multilib.utils.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/**
 * PatternMatcher — detects whether a given PatternManager structure exists in the world
 */
public class PatternMatcher {

    /**
     * Result of a pattern match, containing the origin and transformation used
     */
    public record MatchResult(BlockPos origin, PatternAction.TransformData transform) {}

    /**
     * Attempts to find the origin of a pattern around the placed block
     * @param level The world level
     * @param placedPos The placed block position
     * @param pattern The pattern definition
     * @return The match result if found, or null if no match
     */
    public static MatchResult matches(ServerLevel level, BlockPos placedPos, PatternManager pattern) {
        int layersCount = pattern.getLayerCount();
        int topLayer = layersCount - 1;
        var layers = pattern.getLayers();
        var blockMap = pattern.getBlockMap();
        boolean allowVertical = pattern.allowsVerticalRotation();

        for (int layerIndex = 0; layerIndex < layersCount; layerIndex++) {
            var layer = layers.get(layerIndex);
            int centerX = layer.getFirst().length() / 2;
            int centerZ = layer.size() / 2;

            for (int row = 0; row < layer.size(); row++) {
                String line = layer.get(row);
                for (int col = 0; col < line.length(); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ' || !blockMap.containsKey(symbol)) continue;

                    int originX = placedPos.getX() - (col - centerX);
                    int originY = placedPos.getY() - (layerIndex - topLayer);
                    int originZ = placedPos.getZ() - (row - centerZ);
                    BlockPos origin = new BlockPos(originX, originY, originZ);

                    MatchResult result = matchesWithAllTransforms(level, origin, pattern, false);
                    if (result != null) {
                        return result;
                    }

                    if (allowVertical) {
                        origin = new BlockPos(
                                placedPos.getX() - (col - centerX),
                                placedPos.getY() - (row - centerZ),
                                placedPos.getZ() - (layerIndex - topLayer)
                        );
                        result = matchesWithAllTransforms(level, origin, pattern, true);
                        if (result != null) {
                            return result;
                        }

                        origin = new BlockPos(
                                placedPos.getX() - (layerIndex - topLayer),
                                placedPos.getY() - (col - centerX),
                                placedPos.getZ() - (row - centerZ)
                        );
                        result = matchesWithAllTransforms(level, origin, pattern, true);
                        if (result != null) {
                            return result;
                        }
                    }
                }
            }
        }

        return null;
    }

    /**
     * Checks all transformations allowed by the pattern configuration
     */
    private static MatchResult matchesWithAllTransforms(ServerLevel level, BlockPos origin, PatternManager pattern, boolean checkVertical) {
        for (int rotation = 0; rotation < 4; rotation++) {
            if (matchesTransformed(level, origin, pattern, rotation, false, "Y")) {
                return new MatchResult(origin, new PatternAction.TransformData(rotation, false, "Y"));
            }
        }

        if (checkVertical && pattern.allowsVerticalRotation()) {
            for (int rotation = 0; rotation < 4; rotation++) {
                if (matchesTransformed(level, origin, pattern, rotation, true, "X")) {
                    return new MatchResult(origin, new PatternAction.TransformData(rotation, true, "X"));
                }
            }

            for (int rotation = 0; rotation < 4; rotation++) {
                if (matchesTransformed(level, origin, pattern, rotation, true, "Z")) {
                    return new MatchResult(origin, new PatternAction.TransformData(rotation, true, "Z"));
                }
            }
        }

        return null;
    }

    /**
     * Performs a detailed block-by-block comparison using transformation parameters
     */
    public static boolean matchesTransformed(ServerLevel level, BlockPos origin, PatternManager pattern,
                                             int rotation, boolean vertical, String axis) {
        int layersCount = pattern.getLayerCount();
        var layers = pattern.getLayers();
        var blockMap = pattern.getBlockMap();

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

                    Block expected = blockMap.get(symbol);
                    if (expected == null) continue;

                    int relX = col - centerX;
                    int relY = yOffset - (layersCount - 1);
                    int relZ = row - centerZ;

                    if (vertical) {
                        int temp;
                        switch (axis.toUpperCase()) {
                            case "X" -> {
                                temp = relY;
                                relY = -relZ;
                                relZ = temp;
                            }
                            case "Z" -> {
                                temp = relX;
                                relX = -relY;
                                relY = temp;
                            }
                        }
                    }

                    int[] transformed = RotationUtils.transform(relX, relY, relZ, rotation, false, axis);
                    BlockPos checkPos = origin.offset(transformed[0], transformed[1], transformed[2]);
                    var state = level.getBlockState(checkPos);

                    if (!state.is(expected)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}