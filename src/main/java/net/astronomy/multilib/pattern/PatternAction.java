package net.astronomy.multilib.pattern;

import net.astronomy.multilib.utils.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * PatternAction — defines what happens when a pattern is matched
 */
@FunctionalInterface
public interface PatternAction {

    void onMatch(ServerLevel level, BlockPos origin);

    /**
     * Clears the pattern structure from the world,
     * supporting horizontal, mirrored, and vertical rotations
     */
    static void clearStructure(ServerLevel level, BlockPos origin, PatternManager pattern) {
        boolean allowVertical = pattern.allowsVerticalRotation();

        for (int rotation = 0; rotation < 4; rotation++) {
            if (PatternMatcher.matchesTransformed(level, origin, pattern, rotation, false, false, "X")) {
                removeBlocks(level, origin, pattern, rotation, false, "X");
                return;
            }

            if (allowVertical) {
                if (PatternMatcher.matchesTransformed(level, origin, pattern, rotation, false, true, "X")) {
                    removeBlocks(level, origin, pattern, rotation, true, "X");
                    return;
                }

                if (PatternMatcher.matchesTransformed(level, origin, pattern, rotation, false, true, "Z")) {
                    removeBlocks(level, origin, pattern, rotation, true, "Z");
                    return;
                }
            }
        }
    }

    /**
     * Removes blocks according to transformation rules (horizontal, mirrored, vertical)
     */
    private static void removeBlocks(ServerLevel level, BlockPos origin,
                                     PatternManager pattern,
                                     int rotation, boolean vertical, String axis) {

        int layersCount = pattern.getLayerCount();

        for (int yOffset = 0; yOffset < layersCount; yOffset++) {
            var layer = pattern.getLayers().get(yOffset);
            int height = layer.size();
            int width = layer.getFirst().length();
            int centerX = width / 2;
            int centerZ = height / 2;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < width; col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;

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
                    BlockPos target = origin.offset(transformed[0], transformed[1], transformed[2]);
                    level.removeBlock(target, false);
                }
            }
        }
    }

    /** Spawns particles when the pattern activates */
    static void spawnParticles(ServerLevel level, BlockPos origin) {
        level.sendParticles(
                ParticleTypes.END_ROD,
                origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5,
                10, 0.3, 0.3, 0.3, 0
        );
    }

    /** Plays sound when the pattern activates */
    static void playSound(ServerLevel level, BlockPos origin) {
        level.playSound(
                null,
                origin,
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS,
                1.0F, 1.0F
        );
    }
}