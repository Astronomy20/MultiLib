package net.astronomy.multilib.api.aggregate;

import net.minecraft.core.BlockPos;

import java.util.Set;

/**
 * Ready-made {@link AggregationShapePolicy} implementations. Every one of them is validated the same
 * way: infer the shape's parameters (center, radius, base size, ...) from the connected set's own
 * bounding box, then check that the set exactly matches the ideal discretized shape for those inferred
 * parameters - no extra cells, no missing ones. None of them know or care what content the group holds.
 */
public final class AggregationShapePolicies {

    private AggregationShapePolicies() {}

    /**
     * Create mod-style: the connected set must exactly fill a solid rectangular prism - no notches, no
     * L-shapes, no holes - and stay within {@code maxX}/{@code maxY}/{@code maxZ} blocks per axis.
     * Validated cheaply: the bounding box's volume must equal the member count, which is only possible
     * if every cell inside that box is actually present (a connected set can't have "extra" cells
     * outside its own bounding box, and a smaller connected set inside a larger box could never equal
     * the box's full volume).
     */
    public static AggregationShapePolicy cuboid(int maxX, int maxY, int maxZ) {
        return members -> {
            if (members.isEmpty()) return false;
            Bounds b = Bounds.of(members);
            if (b.dx() > maxX || b.dy() > maxY || b.dz() > maxZ) return false;
            return b.dx() * b.dy() * b.dz() == members.size();
        };
    }

    /**
     * Alias for {@link #cuboid(int, int, int)} with names some devs will reach for instead - "cuboid"
     * and "parallelepiped" describe the exact same solid rectangular-prism shape, independently sized
     * per axis. No behavior difference at all.
     */
    public static AggregationShapePolicy parallelepiped(int width, int height, int depth) {
        return cuboid(width, height, depth);
    }

    /**
     * A solid sphere up to {@code maxRadius} blocks. The bounding box must be a cube (a sphere's bounding
     * box always is); its side length is the sphere's diameter, and its center is the inferred center.
     * A block is expected to be present iff its center point falls within {@code radius} of the sphere's
     * center - the same rule used to voxelize a sphere anywhere else - checked for every cell in the
     * bounding box, not just the members, so a hollowed-out or lumpy blob that merely fits inside a
     * sphere-sized box is correctly rejected.
     */
    public static AggregationShapePolicy sphere(int maxRadius) {
        return members -> {
            if (members.isEmpty()) return false;
            Bounds b = Bounds.of(members);
            if (b.dx() != b.dy() || b.dy() != b.dz()) return false;
            if (b.dx() > 2L * maxRadius) return false;

            double radius = b.dx() / 2.0;
            double cx = b.minX() + radius;
            double cy = b.minY() + radius;
            double cz = b.minZ() + radius;
            double radiusSq = radius * radius;

            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        boolean inside = distSq(x + 0.5 - cx, y + 0.5 - cy, z + 0.5 - cz) <= radiusSq;
                        if (inside != members.contains(new BlockPos(x, y, z))) return false;
                    }
                }
            }
            return true;
        };
    }

    /**
     * A solid upright cylinder (circular footprint, extruded along the Y axis) up to {@code maxRadius}
     * blocks wide and {@code maxHeight} blocks tall. The footprint's bounding square infers the circle's
     * diameter/center exactly like {@link #sphere(int)} does; every Y layer must match that same circle.
     */
    public static AggregationShapePolicy cylinder(int maxRadius, int maxHeight) {
        return members -> {
            if (members.isEmpty()) return false;
            Bounds b = Bounds.of(members);
            if (b.dx() != b.dz()) return false;
            if (b.dx() > 2L * maxRadius) return false;
            if (b.dy() > maxHeight) return false;

            double radius = b.dx() / 2.0;
            double cx = b.minX() + radius;
            double cz = b.minZ() + radius;
            double radiusSq = radius * radius;

            for (int x = b.minX(); x <= b.maxX(); x++) {
                for (int z = b.minZ(); z <= b.maxZ(); z++) {
                    boolean insideCircle = distSq(x + 0.5 - cx, z + 0.5 - cz) <= radiusSq;
                    for (int y = b.minY(); y <= b.maxY(); y++) {
                        if (insideCircle != members.contains(new BlockPos(x, y, z))) return false;
                    }
                }
            }
            return true;
        };
    }

    /**
     * A solid stepped (ziggurat-style) square pyramid up to {@code maxBaseSize} blocks wide at the base
     * and {@code maxHeight} layers tall: the bottom layer is a centered {@code baseSize x baseSize}
     * square, and each layer up insets by exactly one block per side (so it shrinks by two per layer)
     * until it either runs out of height or would shrink to nothing.
     */
    public static AggregationShapePolicy pyramid(int maxBaseSize, int maxHeight) {
        return members -> {
            if (members.isEmpty()) return false;
            Bounds b = Bounds.of(members);
            if (b.dx() != b.dz()) return false;
            int baseSize = (int) b.dx();
            if (baseSize > maxBaseSize) return false;
            if (b.dy() > maxHeight) return false;

            for (int layer = 0; layer < b.dy(); layer++) {
                int size = baseSize - 2 * layer;
                if (size <= 0) return false;
                int y = b.minY() + layer;
                int loX = b.minX() + layer, hiX = b.maxX() - layer;
                int loZ = b.minZ() + layer, hiZ = b.maxZ() - layer;
                for (int x = b.minX(); x <= b.maxX(); x++) {
                    for (int z = b.minZ(); z <= b.maxZ(); z++) {
                        boolean insideLayer = x >= loX && x <= hiX && z >= loZ && z <= hiZ;
                        if (insideLayer != members.contains(new BlockPos(x, y, z))) return false;
                    }
                }
            }
            return true;
        };
    }

    /**
     * Video/organic style: any connected shape at all is valid, no matter how irregular (steps, offsets,
     * branches) - the only thing that ever mattered to reach this point was neighbor adjacency, and
     * {@link AggregationEngine} already guarantees that. This policy exists purely to make "no shape
     * constraint" an explicit, readable choice instead of a magic {@code null}. Pair with
     * {@link AggregatableBlockEntity#getMaxAggregateSize()} if some cap is still wanted - freeform has no
     * shape of its own to bound growth otherwise.
     */
    public static AggregationShapePolicy freeform() {
        return members -> !members.isEmpty();
    }

    private static double distSq(double a, double b) {
        return a * a + b * b;
    }

    private static double distSq(double a, double b, double c) {
        return a * a + b * b + c * c;
    }

    /** Inclusive axis-aligned bounding box of a non-empty member set, plus its per-axis side lengths. */
    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long dx() { return maxX - minX + 1L; }
        long dy() { return maxY - minY + 1L; }
        long dz() { return maxZ - minZ + 1L; }

        static Bounds of(Set<BlockPos> members) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : members) {
                minX = Math.min(minX, pos.getX());
                maxX = Math.max(maxX, pos.getX());
                minY = Math.min(minY, pos.getY());
                maxY = Math.max(maxY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
