package net.astronomy.multilib.core.assembly;

import net.astronomy.multilib.api.assembly.ConnectionType;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.api.port.AbstractPortBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * Tests whether two member sub-structures are "connected" under a given {@link ConnectionType}. All
 * checks operate on the members' block positions (and, for {@code PORT_LINK}, their block entities).
 */
public final class ConnectionEvaluator {

    private ConnectionEvaluator() {}

    public static boolean connected(ServerLevel level, MultiblockInstance a, MultiblockInstance b,
                                    ConnectionType type, int radius) {
        return switch (type) {
            case ADJACENCY -> adjacency(a, b);
            case SHARED_BLOCK -> shared(a, b);
            case PROXIMITY -> proximity(a, b, radius);
            case PORT_LINK -> portLink(level, a, b);
        };
    }

    private static boolean adjacency(MultiblockInstance a, MultiblockInstance b) {
        Set<BlockPos> other = b.getPositions();
        for (BlockPos p : a.getPositions()) {
            for (Direction d : Direction.values()) {
                if (other.contains(p.relative(d))) return true;
            }
        }
        return false;
    }

    private static boolean shared(MultiblockInstance a, MultiblockInstance b) {
        Set<BlockPos> other = b.getPositions();
        for (BlockPos p : a.getPositions()) {
            if (other.contains(p)) return true;
        }
        return false;
    }

    /**
     * Bounding-box Chebyshev proximity: true when the axis-aligned bounding boxes of the two members
     * are within {@code radius} blocks on every axis. A cheap, deterministic v1 metric (documented in
     * phase-12) rather than an O(|A|·|B|) exact nearest-pair scan.
     */
    private static boolean proximity(MultiblockInstance a, MultiblockInstance b, int radius) {
        int[] ba = bounds(a);
        int[] bb = bounds(b);
        int dx = axisGap(ba[0], ba[3], bb[0], bb[3]);
        int dy = axisGap(ba[1], ba[4], bb[1], bb[4]);
        int dz = axisGap(ba[2], ba[5], bb[2], bb[5]);
        return Math.max(dx, Math.max(dy, dz)) <= radius;
    }

    /** Gap between two 1-D intervals [minA,maxA] and [minB,maxB]; 0 if they overlap or touch. */
    private static int axisGap(int minA, int maxA, int minB, int maxB) {
        if (maxA < minB) return minB - maxA;
        if (maxB < minA) return minA - maxB;
        return 0;
    }

    private static boolean portLink(ServerLevel level, MultiblockInstance a, MultiblockInstance b) {
        Set<BlockPos> other = b.getPositions();
        for (BlockPos p : a.getPositions()) {
            if (!level.isLoaded(p)) continue;
            if (!(level.getBlockEntity(p) instanceof AbstractPortBlockEntity)) continue;
            for (Direction d : Direction.values()) {
                if (other.contains(p.relative(d))) return true;
            }
        }
        return false;
    }

    /** @return {minX,minY,minZ,maxX,maxY,maxZ} over an instance's positions. */
    private static int[] bounds(MultiblockInstance inst) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : inst.getPositions()) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        return new int[]{minX, minY, minZ, maxX, maxY, maxZ};
    }
}
