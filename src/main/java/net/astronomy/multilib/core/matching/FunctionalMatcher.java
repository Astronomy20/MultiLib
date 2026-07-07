package net.astronomy.multilib.core.matching;

import net.astronomy.multilib.api.definition.AllowedRotation;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.definition.RotationAxis;
import net.astronomy.multilib.api.definition.RotationMode;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FunctionalMatcher implements IPatternMatcher {

    // Hoisted out of the per-cell transform loops, which run once per pattern cell per match attempt.
    private static final String[] Y_AXIS_ONLY = {"Y"};
    private static final String[] X_AXES = {"X", "X_FLIP"};
    private static final String[] Z_AXES = {"Z", "Z_FLIP"};

    @Override
    public MatchResult matches(ServerLevel level, BlockPos activationPos, MultiblockDefinition definition) {
        PatternProvider provider = definition.getPatternProvider()
                .orElseThrow(() -> new IllegalStateException("FunctionalMatcher requires a PatternProvider"));

        Vec3i bbDef = definition.getBoundingBox();
        Vec3i size = (!bbDef.equals(Vec3i.ZERO)) ? bbDef : provider.getSize();

        int sx = size.getX(), sy = size.getY(), sz = size.getZ();
        int centerX = sx / 2;
        int centerZ = sz / 2;

        Set<AllowedRotation> allowedRotations = definition.getAllowedRotations();
        boolean allowHorizontal = !allowedRotations.isEmpty()
                ? true // the unrotated/Y-axis origin candidate is always needed as the baseline
                : definition.getRotationMode() != RotationMode.NONE;
        boolean allowVertical = !allowedRotations.isEmpty()
                ? allowedRotations.stream().anyMatch(ar -> ar.axis() != RotationAxis.Y)
                : definition.getRotationMode() == RotationMode.ALL;

        int orientationsTried = 0;
        List<String[]> axes = buildAxes(allowHorizontal, allowVertical); // same for every anchor cell

        // Find all (x,y,z) in provider that have the activation ingredient
        char activationSym = definition.getActivationSymbol();
        BlockIngredient activationIngredient = activationSym != '\0'
                ? definition.getBlockMap().get(activationSym)
                : null;

        // Iterate provider positions; for each non-null position, try placing it at activationPos
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    BlockIngredient ing = provider.getIngredientAt(x, y, z);
                    if (ing == null) continue;

                    // Only try positions that match the activation ingredient as anchor,
                    // or try all positions if no activation symbol is defined
                    if (activationIngredient != null && !activationIngredient.equals(ing)) continue;

                    int relX = x - centerX;
                    int relY = y - (sy - 1);
                    int relZ = z - centerZ;

                    if (!allowedRotations.isEmpty()) {
                        MatchData found = tryGranularTransformsForCell(
                                level, activationPos, relX, relY, relZ, definition, provider, size);
                        if (found != null) return new MatchResult.Success(found);
                        orientationsTried += 1 + countGranularTransforms(allowedRotations);
                        continue;
                    }

                    for (String[] axisInfo : axes) {
                        String axis = axisInfo[0];
                        for (int rotation = 0; rotation < 4; rotation++) {
                            int[] anchorTransformed = ShapedMatcher.applyTransform(relX, relY, relZ, axis, rotation);
                            BlockPos origin = activationPos.offset(
                                    -anchorTransformed[0], -anchorTransformed[1], -anchorTransformed[2]);

                            if (matchesProvider(level, origin, provider, definition, size, axis, rotation)) {
                                MatchData data = collectMatchData(level, origin, provider, definition, size, axis, rotation);
                                return new MatchResult.Success(data);
                            }
                            orientationsTried++;
                        }
                    }
                }
            }
        }

        String summary = orientationsTried == 0 ? "No orientations tried"
                : "No matching orientation found after " + orientationsTried + " attempts";
        return new MatchResult.Failure(new MatchFailureReport(orientationsTried, List.of(), summary));
    }

    /**
     * Tries only the unrotated orientation plus the specific axis/angle combinations declared via
     * {@code .allowRotation(...)}, instead of every rotation the coarse {@link RotationMode} allows.
     * Mirrors {@link ShapedMatcher#tryGranularTransformsForCell}.
     */
    private MatchData tryGranularTransformsForCell(ServerLevel level, BlockPos activationPos,
                                                    int relX, int relY, int relZ,
                                                    MultiblockDefinition definition,
                                                    PatternProvider provider, Vec3i size) {
        int[] baseTransformed = ShapedMatcher.applyTransform(relX, relY, relZ, "Y", 0);
        BlockPos baseOrigin = activationPos.offset(-baseTransformed[0], -baseTransformed[1], -baseTransformed[2]);
        if (matchesProvider(level, baseOrigin, provider, definition, size, "Y", 0)) {
            return collectMatchData(level, baseOrigin, provider, definition, size, "Y", 0);
        }
        for (AllowedRotation allowed : definition.getAllowedRotations()) {
            int step = allowed.normalizedAngle() / 90;
            String[] axesToTry = switch (allowed.axis()) {
                case Y -> Y_AXIS_ONLY;
                case X -> X_AXES;
                case Z -> Z_AXES;
            };
            for (String axisStr : axesToTry) {
                int[] t = ShapedMatcher.applyTransform(relX, relY, relZ, axisStr, step);
                BlockPos origin = activationPos.offset(-t[0], -t[1], -t[2]);
                if (matchesProvider(level, origin, provider, definition, size, axisStr, step)) {
                    return collectMatchData(level, origin, provider, definition, size, axisStr, step);
                }
            }
        }
        return null;
    }

    private static int countGranularTransforms(Set<AllowedRotation> allowedRotations) {
        int count = 0;
        for (AllowedRotation allowed : allowedRotations) {
            count += allowed.axis() == RotationAxis.Y ? 1 : 2;
        }
        return count;
    }

    private boolean matchesProvider(ServerLevel level, BlockPos origin, PatternProvider provider,
                                    MultiblockDefinition definition, Vec3i size,
                                    String axis, int rotation) {
        int sx = size.getX(), sy = size.getY(), sz = size.getZ();
        int centerX = sx / 2, centerZ = sz / 2;

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    BlockIngredient ing = provider.getIngredientAt(x, y, z);
                    if (ing == null) {
                        if (definition.isRequireAirInEmptyPositions()) {
                            int relX = x - centerX, relY = y - (sy - 1), relZ = z - centerZ;
                            int[] t = ShapedMatcher.applyTransform(relX, relY, relZ, axis, rotation);
                            BlockPos pos = origin.offset(t[0], t[1], t[2]);
                            if (!level.getBlockState(pos).isAir()) return false;
                        }
                        continue;
                    }

                    int relX = x - centerX;
                    int relY = y - (sy - 1);
                    int relZ = z - centerZ;

                    int[] t = ShapedMatcher.applyTransform(relX, relY, relZ, axis, rotation);
                    BlockPos checkPos = origin.offset(t[0], t[1], t[2]);

                    if (!ing.matches(level, checkPos, level.getBlockState(checkPos))) return false;
                }
            }
        }
        return true;
    }

    private MatchData collectMatchData(ServerLevel level, BlockPos origin, PatternProvider provider,
                                       MultiblockDefinition definition, Vec3i size,
                                       String axis, int rotation) {
        int sx = size.getX(), sy = size.getY(), sz = size.getZ();
        int centerX = sx / 2, centerZ = sz / 2;
        boolean isVertical = !axis.equals("Y");

        Set<BlockPos> allPos = new HashSet<>();
        Map<Character, Set<BlockPos>> symbolPos = new HashMap<>();
        Map<Character, BlockIngredient> blockMap = definition.getBlockMap();

        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    BlockIngredient ing = provider.getIngredientAt(x, y, z);
                    if (ing == null) continue;

                    int relX = x - centerX;
                    int relY = y - (sy - 1);
                    int relZ = z - centerZ;

                    int[] t = ShapedMatcher.applyTransform(relX, relY, relZ, axis, rotation);
                    BlockPos worldPos = origin.offset(t[0], t[1], t[2]);
                    allPos.add(worldPos);

                    // Map to symbol via blockMap
                    for (Map.Entry<Character, BlockIngredient> entry : blockMap.entrySet()) {
                        if (entry.getValue().equals(ing)) {
                            symbolPos.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add(worldPos);
                            break;
                        }
                    }
                }
            }
        }

        Map<Character, Set<BlockPos>> immutable = symbolPos.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Collections.unmodifiableSet(e.getValue())));

        return new MatchData(
                origin,
                new TransformData(rotation, isVertical, axis),
                Collections.unmodifiableSet(allPos),
                immutable,
                size
        );
    }

    private static List<String[]> buildAxes(boolean allowHorizontal, boolean allowVertical) {
        List<String[]> axes = new ArrayList<>();
        if (allowHorizontal) axes.add(new String[]{"Y"});
        if (allowVertical) {
            axes.add(new String[]{"X"});
            axes.add(new String[]{"Z"});
            axes.add(new String[]{"X_FLIP"});
            axes.add(new String[]{"Z_FLIP"});
        }
        return axes;
    }
}
