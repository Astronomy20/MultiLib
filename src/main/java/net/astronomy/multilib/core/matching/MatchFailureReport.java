package net.astronomy.multilib.core.matching;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public record MatchFailureReport(
        int orientationsTried,
        List<FailedPosition> closestMismatch,
        String summary
) {
    public record FailedPosition(BlockPos pos, BlockState found, String ingredientDesc) {}
}
