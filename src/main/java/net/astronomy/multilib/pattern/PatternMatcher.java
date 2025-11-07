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
     * Attempts to find the origin of a pattern around the placed block
     * @param level The world level
     * @param placedPos The placed block position
     * @param pattern The pattern definition
     * @return The origin position if matched, or null if no match
     */
    public static BlockPos matches(ServerLevel level, BlockPos placedPos, PatternManager pattern) {
        int layersCount = pattern.getLayerCount();
        int topLayer = layersCount - 1;
        var layers = pattern.getLayers();
        var blockMap = pattern.getBlockMap();

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

                    if (matchesWithAllTransforms(level, origin, pattern)) {
                        return origin;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Checks all transformations allowed by the pattern configuration
     */
    private static boolean matchesWithAllTransforms(ServerLevel level, BlockPos origin, PatternManager pattern) {
        boolean allowVertical = pattern.allowsVerticalRotation();

        for (int rotation = 0; rotation < 4; rotation++) {
            if (matchesTransformed(level, origin, pattern, rotation, false, false, "X"))
                return true;

            if (allowVertical) {
                if (matchesTransformed(level, origin, pattern, rotation, false, true, "X"))
                    return true;
                if (matchesTransformed(level, origin, pattern, rotation, false, true, "Z"))
                    return true;
            }
        }

        return false;
    }

    /**
     * Performs a detailed block-by-block comparison using transformation parameters
     */
    public static boolean matchesTransformed(ServerLevel level, BlockPos origin, PatternManager pattern,
                                             int rotation, boolean mirror, boolean vertical, String axis) {
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
                        switch (axis.toUpperCase()) {
                            case "X" -> {
                                int temp = relY;
                                relY = relZ;
                                relZ = temp;
                            }
                            case "Z" -> {
                                int temp = relY;
                                relY = relX;
                                relX = temp;
                            }
                        }
                    }

                    int[] transformed = RotationUtils.transform(relX, relY, relZ, rotation, vertical, axis);
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