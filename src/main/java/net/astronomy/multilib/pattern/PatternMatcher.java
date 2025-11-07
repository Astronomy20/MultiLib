package net.astronomy.multilib.pattern;

import net.astronomy.multilib.utils.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/**
 * PatternMatch — detects whether a given PatternManager structure exists in the world.
 */
public class PatternMatcher {

    /** Try to find a matching structure around the placed block. */
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

                    // Candidate origin
                    int originX = placedPos.getX() - (col - centerX);
                    int originY = placedPos.getY() - (layerIndex - topLayer);
                    int originZ = placedPos.getZ() - (row - centerZ);
                    BlockPos origin = new BlockPos(originX, originY, originZ);

                    // Check all 4 rotations
                    for (int rotation = 0; rotation < 4; rotation++) {
                        if (matchesWithRotation(level, origin, rotation, pattern)) {
                            return origin;
                        }
                    }
                }
            }
        }

        return null; // no match
    }

    private static boolean matchesAt(ServerLevel level, BlockPos origin, PatternManager pattern) {
        for (int rotation = 0; rotation < 4; rotation++) {
            if (matchesWithRotation(level, origin, rotation, pattern)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesWithRotation(ServerLevel level, BlockPos origin, int rotation, PatternManager pattern) {
        int layersCount = pattern.getLayerCount();
        List<List<String>> layers = pattern.getLayers();
        Map<Character, Block> blockMap = pattern.getBlockMap();

        for (int yOffset = 0; yOffset < layersCount; yOffset++) {
            List<String> layer = layers.get(yOffset);
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
                    int relZ = row - centerZ;
                    int[] rotated = RotationUtils.rotate(relX, relZ, rotation);
                    BlockPos checkPos = origin.offset(rotated[0], yOffset - (layersCount - 1), rotated[1]);
                    BlockState state = level.getBlockState(checkPos);

                    if (!state.is(expected)) return false;
                }
            }
        }

        return true;
    }
}