package net.astronomy.multilib.pattern;

import net.astronomy.multilib.utils.RotationUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * PatternAction — defines what happens when a pattern is matched.
 * Provides optional utilities for manipulating or clearing patterns.
 */
@FunctionalInterface
public interface PatternAction {

    /**
     * Called when the pattern is successfully matched in the world.
     *
     * @param level  The world (server side)
     * @param origin The origin position of the matched structure
     */
    void onMatch(ServerLevel level, BlockPos origin);

    // ───────────────────────────────
    // UTILITY HELPERS
    // ───────────────────────────────

    /**
     * Clears the structure of a given pattern from the world,
     * only if the structure still matches (via PatternMatcher).
     */
    static void clearStructure(ServerLevel level, BlockPos origin, PatternManager pattern) {
        for (int rotation = 0; rotation < 4; rotation++) {
            if (PatternMatcher.matchesWithRotation(level, origin, rotation, pattern)) {
                removeBlocks(level, origin, rotation, pattern);
                return;
            }
        }
    }

    /**
     * Removes the blocks of a given pattern (no validation).
     * Use {@link #clearStructure} if you want it to check first.
     */
    private static void removeBlocks(ServerLevel level, BlockPos origin, int rotation, PatternManager pattern) {
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
                    int relZ = row - centerZ;
                    int[] rotated = RotationUtils.rotate(relX, relZ, rotation);
                    BlockPos target = origin.offset(rotated[0], yOffset - (layersCount - 1), rotated[1]);
                    level.removeBlock(target, false);
                }
            }
        }
    }

    /** Spawn visual feedback when a pattern activates */
    static void spawnParticles(ServerLevel level, BlockPos origin) {
        level.sendParticles(ParticleTypes.END_ROD, origin.getX(), origin.getY() + 1, origin.getZ(), 20, 0.3, 0.3, 0.3, 0);
    }

    /** Play sound feedback when a pattern activates */
    static void playSound(ServerLevel level, BlockPos origin) {
        level.playSound(null, origin, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}