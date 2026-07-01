package net.astronomy.multilib.core.matching;

import net.astronomy.multilib.api.definition.AllowedRotation;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.definition.RotationAxis;
import net.astronomy.multilib.api.definition.RotationMode;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves the (axis, rotation) a multiblock structure is being previewed/placed/tracked in. Shared
 * by the ghost overlay, auto-place, and the progress API — extracted into core/matching since it's
 * pattern-matching logic, not event-handling logic, and duplicating it per-caller risks the same
 * silent drift already found once between {@link ShapedMatcher} and {@link FunctionalMatcher}.
 */
public final class StructureOrientation {

    /** (axis, rotation) pair describing a structure's current preview/placement orientation. */
    public record Orientation(String axis, int rotation) {}

    private StructureOrientation() {}

    /**
     * Mirrors how {@link ShapedMatcher} derives a match's origin from a symbol's own cell, instead of
     * the layer's geometric center, so a preview/placement/progress-check lines up with the block
     * that was clicked, wherever in the pattern it sits — for whichever (axis, rotation) is active.
     */
    public static BlockPos findSymbolOrigin(BlockPos clickedPos, List<List<String>> layers, char symbol,
                                             String axis, int rotation) {
        int layersCount = layers.size();
        for (int layerIdx = 0; layerIdx < layersCount; layerIdx++) {
            List<String> layer = layers.get(layerIdx);
            int height = layer.size();
            if (height == 0) continue;
            int width = layer.get(0).length();
            int centerX = width / 2;
            int centerZ = height / 2;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < Math.min(width, line.length()); col++) {
                    if (line.charAt(col) != symbol) continue;
                    int relX = col - centerX;
                    // layers[0] is the topmost declared layer (same convention as MultiblockBuilder.layer()
                    // call order), layers[last] the bottommost.
                    int relY = (layersCount - 1) - layerIdx;
                    int relZ = row - centerZ;
                    int[] t = ShapedMatcher.applyTransform(relX, relY, relZ, axis, rotation);
                    return clickedPos.offset(-t[0], -t[1], -t[2]);
                }
            }
        }
        // Symbol not found in the pattern — fall back to treating clickedPos as the origin.
        return clickedPos;
    }

    /**
     * Picks which (axis, rotation) a preview/placement should use for a click on the given face of
     * the core. The four horizontal faces always cycle the four Y-axis (yaw) rotations — that works
     * for every definition, rotated or not, since axis=Y rotation=0 is always the baseline. UP keeps
     * the default upright placement. DOWN previews the structure flipped onto its head, but only if
     * the definition actually declared an X/Z-axis rotation via {@code .allowRotation(...)} —
     * otherwise DOWN falls back to the same default as UP, since flipping isn't a valid placement.
     */
    public static Orientation orientationForFace(MultiblockDefinition definition, Direction face) {
        return switch (face) {
            case SOUTH -> new Orientation("Y", 0);
            case WEST -> new Orientation("Y", 1);
            case NORTH -> new Orientation("Y", 2);
            case EAST -> new Orientation("Y", 3);
            case DOWN -> {
                if (definition != null) {
                    for (AllowedRotation ar : definition.getAllowedRotations()) {
                        if (ar.axis() == RotationAxis.X) yield new Orientation("X_FLIP", 0);
                        if (ar.axis() == RotationAxis.Z) yield new Orientation("Z_FLIP", 0);
                    }
                }
                yield new Orientation("Y", 0);
            }
            default -> new Orientation("Y", 0); // UP, or anything else: default upright
        };
    }

    /**
     * Scans every orientation the pattern matcher would ever try (mirroring {@link ShapedMatcher}'s
     * own {@code tryAllTransformsForCell}/{@code tryGranularTransformsForCell} enumeration) and picks
     * whichever orientation has the most already-placed, matching non-core pattern blocks around
     * {@code corePos}. This is ground truth from the physical structure, not a guess, so callers
     * should prefer it over a guessed player-facing orientation or a remembered overlay session
     * whenever it's available.
     *
     * @return empty if no orientation has any matching placed blocks beyond the core (i.e. only the
     *         core itself exists so far, so there's nothing to detect from).
     */
    public static Optional<Orientation> detectFromPlacedBlocks(
            ServerLevel level, BlockPos corePos, MultiblockDefinition definition) {
        List<List<String>> layers = definition.getLayers();
        Map<Character, BlockIngredient> blockMap = definition.getBlockMap();
        char coreSymbol = definition.getCoreSymbol();
        Set<Character> freeBlockSymbols = definition.getFreeBlocks().keySet();

        List<String[]> orientationCandidates = new ArrayList<>();
        // Baseline: unrotated plus all four Y-axis (horizontal) rotations are always tried.
        for (int r = 0; r < 4; r++) {
            orientationCandidates.add(new String[]{"Y", String.valueOf(r)});
        }
        if (!definition.getAllowedRotations().isEmpty()) {
            for (AllowedRotation allowed : definition.getAllowedRotations()) {
                int step = allowed.normalizedAngle() / 90;
                switch (allowed.axis()) {
                    case X -> orientationCandidates.add(new String[]{"X", String.valueOf(step)});
                    case Z -> orientationCandidates.add(new String[]{"Z", String.valueOf(step)});
                    case Y -> { /* redundant with the baseline Y sweep above */ }
                }
                switch (allowed.axis()) {
                    case X -> orientationCandidates.add(new String[]{"X_FLIP", String.valueOf(step)});
                    case Z -> orientationCandidates.add(new String[]{"Z_FLIP", String.valueOf(step)});
                    case Y -> { /* no-op */ }
                }
            }
        } else if (definition.getRotationMode() == RotationMode.ALL) {
            for (String axis : new String[]{"X", "Z", "X_FLIP", "Z_FLIP"}) {
                for (int r = 0; r < 4; r++) {
                    orientationCandidates.add(new String[]{axis, String.valueOf(r)});
                }
            }
        }

        String bestAxis = null;
        int bestRotation = 0;
        int bestCount = 0;

        for (String[] candidate : orientationCandidates) {
            String axis = candidate[0];
            int rotation = Integer.parseInt(candidate[1]);

            BlockPos origin = findSymbolOrigin(corePos, layers, coreSymbol, axis, rotation);

            int count = 0;
            for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
                List<String> layer = layers.get(layerIdx);
                int height = layer.size();
                if (height == 0) continue;
                int width = layer.get(0).length();
                int centerX = width / 2;
                int centerZ = height / 2;
                int relY = (layers.size() - 1) - layerIdx;

                for (int row = 0; row < height; row++) {
                    String line = layer.get(row);
                    for (int col = 0; col < Math.min(width, line.length()); col++) {
                        char symbol = line.charAt(col);
                        if (symbol == ' ') continue;
                        if (symbol == coreSymbol) continue;
                        if (freeBlockSymbols.contains(symbol)) continue;
                        BlockIngredient ingredient = blockMap.get(symbol);
                        if (ingredient == null) continue;

                        int relX = col - centerX;
                        int relZ = row - centerZ;
                        int[] t = ShapedMatcher.applyTransform(relX, relY, relZ, axis, rotation);
                        BlockPos worldPos = origin.offset(t[0], t[1], t[2]);

                        if (ingredient.matches(level.getBlockState(worldPos))) {
                            count++;
                        }
                    }
                }
            }

            if (count > bestCount) {
                bestCount = count;
                bestAxis = axis;
                bestRotation = rotation;
            }
        }

        if (bestCount == 0) return Optional.empty();
        return Optional.of(new Orientation(bestAxis, bestRotation));
    }
}
