package net.astronomy.multilib.core.matching;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

import java.util.Map;
import java.util.Set;

public record MatchData(
        BlockPos origin,
        TransformData transform,
        Set<BlockPos> positions,
        Map<Character, Set<BlockPos>> symbolPositions,
        Vec3i actualDimensions
) {
    public MatchData(BlockPos origin, TransformData transform,
                     Set<BlockPos> positions, Map<Character, Set<BlockPos>> symbolPositions) {
        this(origin, transform, positions, symbolPositions, Vec3i.ZERO);
    }
}
