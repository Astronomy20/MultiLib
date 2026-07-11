package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Combines several {@link PatternProvider}s through constructive solid-geometry style boolean
 * operations, so devs can describe shapes like "a cylinder with a spherical hollow carved out" or
 * "the overlap of two boxes" without writing a bespoke lambda.
 *
 * <p>Each child is placed at an integer offset and folded, in declaration order, into the running
 * result:
 * <ul>
 *   <li>{@link Op#UNION} — add the child's cells. Where a cell is already filled the existing
 *       ingredient is kept (earlier operations win on overlap).</li>
 *   <li>{@link Op#SUBTRACT} — clear every cell the child fills.</li>
 *   <li>{@link Op#INTERSECT} — keep only the cells that are filled in <em>both</em> the running
 *       result and the child; everything else is cleared.</li>
 * </ul>
 *
 * <p>Because folding is order-dependent, the first operation should normally be a {@code UNION}
 * that seeds the shape. Offsets may be negative; the whole composite is normalised at construction
 * so its minimum corner sits at {@code (0, 0, 0)}, matching how {@link PatternProvider}s are
 * anchored elsewhere. Each child is only queried inside its own {@link PatternProvider#getSize()}
 * bounds, so providers that return a non-null ingredient for out-of-range coordinates (e.g.
 * {@link HollowCubeProvider}'s interior) do not bleed outside their footprint.
 *
 * <p>Build one with {@link #builder()}:
 * <pre>{@code
 * PatternProvider reactor = CompositeProvider.builder()
 *     .union(new CylinderProvider(4, 8, casing))
 *     .subtract(new SphereProvider(2, casing), 2, 3, 2)
 *     .build();
 * }</pre>
 */
public class CompositeProvider implements PatternProvider {

    public enum Op { UNION, SUBTRACT, INTERSECT }

    /** A single child provider, its boolean operation, and its offset within the composite. */
    public record Operation(Op op, PatternProvider provider, int dx, int dy, int dz) {}

    private final List<Operation> operations;
    private final Vec3i size;

    /**
     * @param rawOperations operations in fold order; offsets are taken as-is and then the whole
     *                      set is translated so the composite's minimum corner is the origin.
     */
    public CompositeProvider(List<Operation> rawOperations) {
        if (rawOperations.isEmpty()) {
            throw new IllegalArgumentException("CompositeProvider requires at least one operation");
        }

        // Bounding box: prefer the additive (UNION) shapes since SUBTRACT/INTERSECT can only ever
        // shrink the result. Fall back to all operations if no UNION was supplied.
        boolean hasUnion = rawOperations.stream().anyMatch(o -> o.op() == Op.UNION);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Operation o : rawOperations) {
            if (hasUnion && o.op() != Op.UNION) continue;
            Vec3i cs = o.provider().getSize();
            minX = Math.min(minX, o.dx());
            minY = Math.min(minY, o.dy());
            minZ = Math.min(minZ, o.dz());
            maxX = Math.max(maxX, o.dx() + cs.getX());
            maxY = Math.max(maxY, o.dy() + cs.getY());
            maxZ = Math.max(maxZ, o.dz() + cs.getZ());
        }

        // Translate every operation so the minimum corner lands on the origin.
        List<Operation> normalized = new ArrayList<>(rawOperations.size());
        for (Operation o : rawOperations) {
            normalized.add(new Operation(o.op(), o.provider(), o.dx() - minX, o.dy() - minY, o.dz() - minZ));
        }
        this.operations = Collections.unmodifiableList(normalized);
        this.size = new Vec3i(maxX - minX, maxY - minY, maxZ - minZ);
    }

    @Override
    public @Nullable BlockIngredient getIngredientAt(int x, int y, int z) {
        BlockIngredient result = null;
        for (Operation o : operations) {
            BlockIngredient child = childAt(o, x, y, z);
            switch (o.op()) {
                case UNION -> {
                    if (result == null && child != null) result = child;
                }
                case SUBTRACT -> {
                    if (child != null) result = null;
                }
                case INTERSECT -> {
                    if (child == null) result = null;
                }
            }
        }
        return result;
    }

    private static @Nullable BlockIngredient childAt(Operation o, int x, int y, int z) {
        int lx = x - o.dx();
        int ly = y - o.dy();
        int lz = z - o.dz();
        Vec3i cs = o.provider().getSize();
        if (lx < 0 || ly < 0 || lz < 0 || lx >= cs.getX() || ly >= cs.getY() || lz >= cs.getZ()) {
            return null;
        }
        return o.provider().getIngredientAt(lx, ly, lz);
    }

    /** The normalised operations (offsets already translated to the composite's own space). */
    public List<Operation> getOperations() {
        return operations;
    }

    @Override
    public Vec3i getSize() {
        return size;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Convenience factory used by the JSON codec. */
    public static CompositeProvider of(List<Operation> operations) {
        return new CompositeProvider(operations);
    }

    /** Fluent builder that appends operations in fold order. */
    public static final class Builder {
        private final List<Operation> ops = new ArrayList<>();

        public Builder add(Op op, PatternProvider provider, int dx, int dy, int dz) {
            ops.add(new Operation(op, provider, dx, dy, dz));
            return this;
        }

        public Builder union(PatternProvider provider) { return add(Op.UNION, provider, 0, 0, 0); }
        public Builder union(PatternProvider provider, int dx, int dy, int dz) { return add(Op.UNION, provider, dx, dy, dz); }
        public Builder subtract(PatternProvider provider) { return add(Op.SUBTRACT, provider, 0, 0, 0); }
        public Builder subtract(PatternProvider provider, int dx, int dy, int dz) { return add(Op.SUBTRACT, provider, dx, dy, dz); }
        public Builder intersect(PatternProvider provider) { return add(Op.INTERSECT, provider, 0, 0, 0); }
        public Builder intersect(PatternProvider provider, int dx, int dy, int dz) { return add(Op.INTERSECT, provider, dx, dy, dz); }

        public CompositeProvider build() {
            return new CompositeProvider(ops);
        }
    }
}
