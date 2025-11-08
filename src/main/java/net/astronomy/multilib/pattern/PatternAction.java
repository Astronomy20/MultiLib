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

    /**
     * Represents the transformation used to match a pattern
     */
    record TransformData(int rotation, boolean vertical, String axis) {}

    /**
     * Called when a pattern is matched
     * @param level The world level
     * @param origin The origin position of the pattern
     */
    void onMatch(ServerLevel level, BlockPos origin);

    /**
     * Called when a pattern is matched with full transformation data.
     * Default implementation calls the basic onMatch method.
     * Override this if you need access to the transformation data.
     *
     * @param level The world level
     * @param origin The origin position of the pattern
     * @param transform The transformation used to match the pattern
     */
    default void onMatch(ServerLevel level, BlockPos origin, TransformData transform) {
        onMatch(level, origin);
    }

    /**
     * Clears the pattern structure from the world using the exact transformation
     * that was used to match it
     */
    static void clearStructure(ServerLevel level, BlockPos origin, PatternManager pattern, TransformData transform) {
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

                    // Calculate relative position in pattern space
                    int relX = col - centerX;
                    int relY = yOffset - (layersCount - 1);
                    int relZ = row - centerZ;

                    // Apply vertical transformation first (before rotation)
                    if (transform.vertical) {
                        int temp;
                        switch (transform.axis.toUpperCase()) {
                            case "X" -> {
                                // Rotate around X axis: Y becomes -Z, Z becomes Y
                                temp = relY;
                                relY = -relZ;
                                relZ = temp;
                            }
                            case "Z" -> {
                                // Rotate around Z axis: X becomes -Y, Y becomes X
                                temp = relX;
                                relX = -relY;
                                relY = temp;
                            }
                        }
                    }

                    // Apply horizontal rotation
                    int[] transformed = RotationUtils.transform(relX, relY, relZ, transform.rotation, false, transform.axis);
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