package net.astronomy.multilib.core.matching;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.definition.ShapelessRequirement;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Matches a {@code .shapeless()} definition by directly searching for a valid axis-aligned box
 * containing the activation position, instead of flood-filling outward and checking whatever blob it
 * happens to reach. Tried largest-to-smallest (see {@link #matches}) so a bigger valid structure always
 * wins over a smaller one nested inside it, and a stray block of the same material sitting just outside
 * every candidate box's bounds simply never gets looked at - it can't inflate a bounding box or break a
 * "must be fully solid" check the way flood-filling the whole connected blob used to.
 */
public class ShapelessMatcher implements IPatternMatcher {

    @Override
    public MatchResult matches(ServerLevel level, BlockPos activationPos, MultiblockDefinition definition) {
        Vec3i minSz = definition.getShapelessMinSize();
        Vec3i maxSz = definition.getShapelessMaxSize();
        boolean hasShellConfig = definition.getShellIngredient().isPresent() || !definition.getShellFaces().isEmpty();
        boolean hasInteriorConfig = definition.getInteriorIngredient().isPresent();
        Map<Character, BlockIngredient> blockMap = definition.getBlockMap();

        // Solid-fill (no shell/interior declared) has an exact, cheap, always-correct answer: grow the
        // box outward from activationPos one full face-plane at a time for as long as the material
        // continues (see matchSolidFill). That's strictly better than the offset-guessing search below
        // for this case - it finds the TRUE natural extent regardless of which specific block
        // triggered the check (so growing/shrinking the structure can never leave part of it silently
        // excluded), and correctly fails outright if that extent exceeds maxSize on any axis instead of
        // accepting a smaller cropped subset. The offset-search below remains for shell/interior mode,
        // where growth has fundamentally different semantics (which cells count as "shell" changes as
        // the box resizes) and a discrete "try known sizes" search is the more natural fit.
        if (!hasShellConfig && !hasInteriorConfig) {
            return matchSolidFill(level, activationPos, minSz, maxSz, blockMap, definition);
        }

        // Largest size first (descending per axis), so a bigger valid structure is always preferred
        // over a smaller one nested inside it - mirrors PatternMatcher's own "largest variant wins"
        // rule for the exact same reason (declaration/placement order shouldn't determine which size
        // wins; the biggest one actually present should). For each size, only a handful of canonical
        // placements of activationPos within the box are tried (see candidateOffsets) rather than
        // every possible offset - exhaustive placement search is far too expensive to run on every
        // block placement, and real structures are either built symmetrically (activation ends up
        // centered) or by extending in one direction (activation ends up on an edge), both covered.
        for (int sx = maxSz.getX(); sx >= minSz.getX(); sx--) {
            for (int sy = maxSz.getY(); sy >= minSz.getY(); sy--) {
                for (int sz = maxSz.getZ(); sz >= minSz.getZ(); sz--) {
                    Vec3i size = new Vec3i(sx, sy, sz);
                    for (int offX : candidateOffsets(sx)) {
                        for (int offY : candidateOffsets(sy)) {
                            for (int offZ : candidateOffsets(sz)) {
                                BlockPos origin = activationPos.offset(-offX, -offY, -offZ);
                                Optional<MatchData> result = tryBox(level, activationPos, origin, size, maxSz,
                                        definition, blockMap, hasShellConfig, hasInteriorConfig);
                                if (result.isPresent()) {
                                    return new MatchResult.Success(result.get());
                                }
                            }
                        }
                    }
                }
            }
        }

        // Nothing at all worked - re-check the single largest/most "natural" candidate (activation at
        // the box's own origin, maxSize itself) a second time purely to build a helpful diagnostic
        // message, rather than making every one of the (potentially hundreds of) attempts above track
        // a reason for a search that's overwhelmingly going to succeed once a real structure is built.
        String reason = describeFailure(level, activationPos, maxSz, definition, blockMap, hasShellConfig, hasInteriorConfig);
        return failure(reason != null ? reason
                : "No valid structure found containing the activation block, between "
                        + formatSize(minSz) + " and " + formatSize(maxSz));
    }

    /**
     * Grows a box outward from {@code activationPos} one full face-plane at a time, on whichever axis
     * still has room to grow (below {@code maxSz}) and whose next plane is entirely matching material -
     * i.e. the true, natural extent of the solid structure, not a guess. This is provably correct
     * regardless of where {@code activationPos} sits within the final structure: at every step the
     * already-grown box is by construction a subset of the real structure (every cell in it already
     * passed), so repeatedly extending whichever axis currently has a fully-matching next plane
     * converges on the real box's true bounds no matter which order the axes happen to grow in or which
     * block within it triggered the check - unlike the old distance-from-trigger-point flood fill, the
     * base of an already-built structure can never fall outside the window just because a different
     * block (e.g. a newly placed one on top) is what triggered this particular check.
     * <p>
     * Once no axis can grow further, each axis that stopped ONLY because it hit {@code maxSz} (not
     * because the material actually ended) is checked one more time: if there's still more matching
     * material just past that cap, the true structure wants to be bigger than allowed, and the whole
     * match fails outright - see {@link #matches}'s own comment for why this beats silently accepting a
     * maxSz-sized crop of an oversized build.
     */
    private MatchResult matchSolidFill(ServerLevel level, BlockPos activationPos, Vec3i minSz, Vec3i maxSz,
                                        Map<Character, BlockIngredient> blockMap, MultiblockDefinition definition) {
        if (!matchesAt(level, activationPos, blockMap)) {
            return failure("The activation block no longer matches its own declared material");
        }

        int minX = activationPos.getX(), maxX = activationPos.getX();
        int minY = activationPos.getY(), maxY = activationPos.getY();
        int minZ = activationPos.getZ(), maxZ = activationPos.getZ();

        boolean changed = true;
        while (changed) {
            changed = false;
            if (maxX - minX + 1 < maxSz.getX() && planeMatchesYZ(level, minX - 1, minY, maxY, minZ, maxZ, blockMap)) {
                minX--; changed = true;
            }
            if (maxX - minX + 1 < maxSz.getX() && planeMatchesYZ(level, maxX + 1, minY, maxY, minZ, maxZ, blockMap)) {
                maxX++; changed = true;
            }
            if (maxY - minY + 1 < maxSz.getY() && planeMatchesXZ(level, minY - 1, minX, maxX, minZ, maxZ, blockMap)) {
                minY--; changed = true;
            }
            if (maxY - minY + 1 < maxSz.getY() && planeMatchesXZ(level, maxY + 1, minX, maxX, minZ, maxZ, blockMap)) {
                maxY++; changed = true;
            }
            if (maxZ - minZ + 1 < maxSz.getZ() && planeMatchesXY(level, minZ - 1, minX, maxX, minY, maxY, blockMap)) {
                minZ--; changed = true;
            }
            if (maxZ - minZ + 1 < maxSz.getZ() && planeMatchesXY(level, maxZ + 1, minX, maxX, minY, maxY, blockMap)) {
                maxZ++; changed = true;
            }
        }

        if (maxX - minX + 1 >= maxSz.getX()
                && (planeMatchesYZ(level, minX - 1, minY, maxY, minZ, maxZ, blockMap)
                    || planeMatchesYZ(level, maxX + 1, minY, maxY, minZ, maxZ, blockMap))) {
            return failure("Too large on the X axis - maximum is " + formatSize(maxSz));
        }
        if (maxY - minY + 1 >= maxSz.getY()
                && (planeMatchesXZ(level, minY - 1, minX, maxX, minZ, maxZ, blockMap)
                    || planeMatchesXZ(level, maxY + 1, minX, maxX, minZ, maxZ, blockMap))) {
            return failure("Too large on the Y axis - maximum is " + formatSize(maxSz));
        }
        if (maxZ - minZ + 1 >= maxSz.getZ()
                && (planeMatchesXY(level, minZ - 1, minX, maxX, minY, maxY, blockMap)
                    || planeMatchesXY(level, maxZ + 1, minX, maxX, minY, maxY, blockMap))) {
            return failure("Too large on the Z axis - maximum is " + formatSize(maxSz));
        }

        // Self-correction: the growth loop above grows all six directions in parallel, one step at a
        // time, re-checking each plane against whatever the OTHER two axes' extents happen to be AT
        // THAT MOMENT - it never revisits an already-accepted layer once another axis widens further.
        // That means a layer can end up "accepted" based on a narrower X/Z (or X/Y, or Y/Z) snapshot
        // than the box's final extent, without ever being re-checked against the wider range. In
        // practice: breaking one block off the TOP layer of an otherwise-solid box can still let growth
        // commit to that (now-defective) layer early, before the footprint has finished widening -
        // without this pass, the structure would silently keep "including" a layer that isn't actually
        // whole anymore. Trim whichever outer face isn't fully solid, one layer at a time, until every
        // face is - this is what turns "one block broken off an extreme layer" into that WHOLE layer
        // becoming invalid, not a structure that looks fully formed with a hole in it.
        while (true) {
            if (!planeMatchesYZ(level, minX, minY, maxY, minZ, maxZ, blockMap)) {
                minX++;
            } else if (!planeMatchesYZ(level, maxX, minY, maxY, minZ, maxZ, blockMap)) {
                maxX--;
            } else if (!planeMatchesXZ(level, minY, minX, maxX, minZ, maxZ, blockMap)) {
                minY++;
            } else if (!planeMatchesXZ(level, maxY, minX, maxX, minZ, maxZ, blockMap)) {
                maxY--;
            } else if (!planeMatchesXY(level, minZ, minX, maxX, minY, maxY, blockMap)) {
                minZ++;
            } else if (!planeMatchesXY(level, maxZ, minX, maxX, minY, maxY, blockMap)) {
                maxZ--;
            } else {
                break; // every outer face is fully solid - the box is genuinely valid as-is
            }

            // A trim that shrinks past the activation block itself (or collapses the box entirely)
            // means the defect isn't confined to an outer layer - it's interior to whatever's left, and
            // there's no smaller box containing the activation block left to try.
            if (minX > maxX || minY > maxY || minZ > maxZ
                    || activationPos.getX() < minX || activationPos.getX() > maxX
                    || activationPos.getY() < minY || activationPos.getY() > maxY
                    || activationPos.getZ() < minZ || activationPos.getZ() > maxZ) {
                return failure("A block inside the structure is missing or wrong, and there's no valid "
                        + "smaller structure left containing the activation block");
            }
        }

        Vec3i actualSize = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        if (actualSize.getX() < minSz.getX() || actualSize.getY() < minSz.getY() || actualSize.getZ() < minSz.getZ()) {
            return failure("Too small (" + formatSize(actualSize) + ") - needs at least " + formatSize(minSz));
        }

        Set<BlockPos> positions = new HashSet<>(actualSize.getX() * actualSize.getY() * actualSize.getZ());
        Map<Character, Set<BlockPos>> symbolPositions = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    positions.add(p);
                    BlockState state = level.getBlockState(p);
                    for (Map.Entry<Character, BlockIngredient> entry : blockMap.entrySet()) {
                        if (entry.getValue().matches(level, p, state)) {
                            symbolPositions.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add(p);
                            break;
                        }
                    }
                }
            }
        }

        for (ShapelessRequirement req : definition.getShapelessRequirements()) {
            int count = 0;
            for (BlockPos p : positions) {
                if (req.ingredient().matches(level, p, level.getBlockState(p))) count++;
            }
            if (count < req.min() || count > req.max()) {
                return failure("Requirement not met: found " + count
                        + " blocks (need " + req.min() + "-" + req.max() + ")");
            }
        }

        Map<Character, Set<BlockPos>> immutableSymbolPos = symbolPositions.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> Collections.unmodifiableSet(e.getValue())));

        MatchData matchData = new MatchData(
                activationPos,
                new TransformData(0, false, "NONE"),
                Collections.unmodifiableSet(positions),
                immutableSymbolPos,
                actualSize
        );
        return new MatchResult.Success(matchData);
    }

    private static boolean planeMatchesYZ(ServerLevel level, int x, int minY, int maxY, int minZ, int maxZ,
                                           Map<Character, BlockIngredient> blockMap) {
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!matchesAt(level, new BlockPos(x, y, z), blockMap)) return false;
            }
        }
        return true;
    }

    private static boolean planeMatchesXZ(ServerLevel level, int y, int minX, int maxX, int minZ, int maxZ,
                                           Map<Character, BlockIngredient> blockMap) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!matchesAt(level, new BlockPos(x, y, z), blockMap)) return false;
            }
        }
        return true;
    }

    private static boolean planeMatchesXY(ServerLevel level, int z, int minX, int maxX, int minY, int maxY,
                                           Map<Character, BlockIngredient> blockMap) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (!matchesAt(level, new BlockPos(x, y, z), blockMap)) return false;
            }
        }
        return true;
    }

    /** {@code {0, size-1, (size-1)/2}} deduplicated - see {@link #matches}'s own comment on why only these. */
    private static int[] candidateOffsets(int size) {
        Set<Integer> offsets = new LinkedHashSet<>();
        offsets.add(0);
        offsets.add(size - 1);
        offsets.add((size - 1) / 2);
        int[] result = new int[offsets.size()];
        int i = 0;
        for (int o : offsets) result[i++] = o;
        return result;
    }

    /**
     * Validates one candidate box (origin + size) against {@code definition}'s shell/interior/solid-fill
     * rules and {@link ShapelessRequirement}s, returning the resulting {@link MatchData} iff every cell
     * passes. {@code activationPos} must itself fall inside the box - a candidate whose offsets would
     * place it outside is never actually reachable from {@link #matches}' own offset generation, but
     * this is checked anyway since it's the cheapest possible rejection.
     */
    private Optional<MatchData> tryBox(ServerLevel level, BlockPos activationPos, BlockPos origin, Vec3i size,
                                        Vec3i maxSz, MultiblockDefinition definition,
                                        Map<Character, BlockIngredient> blockMap,
                                        boolean hasShellConfig, boolean hasInteriorConfig) {
        int sx = size.getX(), sy = size.getY(), sz = size.getZ();
        int relX = activationPos.getX() - origin.getX();
        int relY = activationPos.getY() - origin.getY();
        int relZ = activationPos.getZ() - origin.getZ();
        if (relX < 0 || relX >= sx || relY < 0 || relY >= sy || relZ < 0 || relZ >= sz) return Optional.empty();

        Set<BlockPos> positions = new HashSet<>(sx * sy * sz);
        Map<Character, Set<BlockPos>> symbolPositions = new HashMap<>();

        for (int x = 0; x < sx; x++) {
            boolean edgeX = x == 0 || x == sx - 1;
            for (int y = 0; y < sy; y++) {
                boolean edgeY = y == 0 || y == sy - 1;
                for (int z = 0; z < sz; z++) {
                    boolean edgeZ = z == 0 || z == sz - 1;
                    boolean isBoundary = edgeX || edgeY || edgeZ;

                    BlockPos p = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(p);

                    if (isBoundary) {
                        if (hasShellConfig) {
                            BlockIngredient required = resolveShellIngredient(definition, p, origin, size);
                            if (required != null && !required.matches(level, p, state)) return Optional.empty();
                        } else if (!hasInteriorConfig && !matchesAny(blockMap, level, p, state)) {
                            return Optional.empty();
                        }
                        // else: interior declared but not shell - boundary intentionally unconstrained.
                    } else {
                        if (hasInteriorConfig) {
                            if (!definition.getInteriorIngredient().get().matches(level, p, state)) return Optional.empty();
                        } else if (!hasShellConfig && !matchesAny(blockMap, level, p, state)) {
                            return Optional.empty();
                        }
                        // else: shell declared but not interior - interior intentionally unconstrained.
                    }

                    positions.add(p);
                    for (Map.Entry<Character, BlockIngredient> entry : blockMap.entrySet()) {
                        if (entry.getValue().matches(level, p, state)) {
                            symbolPositions.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add(p);
                            break;
                        }
                    }
                }
            }
        }

        for (ShapelessRequirement req : definition.getShapelessRequirements()) {
            int count = 0;
            for (BlockPos p : positions) {
                if (req.ingredient().matches(level, p, level.getBlockState(p))) count++;
            }
            if (count < req.min() || count > req.max()) return Optional.empty();
        }

        // Maximality check: a solid box is trivially still "fully valid" as a SUBSET of an even bigger
        // solid structure (every cell inside it still passes), so without this a player who builds
        // past maxSize on some axis would just get silently cropped to the biggest allowed window
        // instead of the whole thing failing - "layer 7 quietly excluded to keep height at 6" rather
        // than a clear "too tall" rejection. Only checked on axes where this candidate is ALREADY at
        // maxSize for that axis (sx/sy/sz == maxSz's corresponding component): if more of the same
        // structural material continues immediately past that face, the true structure wants to be
        // bigger than maxSize allows on that axis, so this candidate can't be the complete structure -
        // reject it and let the search continue to smaller sizes (which will hit the same rejection on
        // every viable window, since the material keeps continuing past all of them, until the whole
        // match legitimately fails). Axes NOT at their cap are left unchecked on purpose: a smaller,
        // deliberately-not-yet-grown structure sitting near unrelated material in some other direction
        // (e.g. built into a stone hillside) must not be penalized for it.
        if (hasExternalContinuation(level, origin, size, maxSz, blockMap)) return Optional.empty();

        Map<Character, Set<BlockPos>> immutableSymbolPos = symbolPositions.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> Collections.unmodifiableSet(e.getValue())));

        MatchData matchData = new MatchData(
                activationPos,
                new TransformData(0, false, "NONE"),
                Collections.unmodifiableSet(positions),
                immutableSymbolPos,
                size
        );
        return Optional.of(matchData);
    }

    private static boolean matchesAny(Map<Character, BlockIngredient> blockMap, ServerLevel level,
                                       BlockPos p, BlockState state) {
        for (BlockIngredient ingredient : blockMap.values()) {
            if (ingredient.matches(level, p, state)) return true;
        }
        return false;
    }

    /** See the maximality-check comment in {@link #tryBox}. Only inspects the pair of faces on an axis whose candidate size equals {@code maxSz}'s own component - a cheap surface-area scan (not a volume one), skipped entirely on axes with room left to grow. */
    private static boolean hasExternalContinuation(ServerLevel level, BlockPos origin, Vec3i size, Vec3i maxSz,
                                                     Map<Character, BlockIngredient> blockMap) {
        int sx = size.getX(), sy = size.getY(), sz = size.getZ();

        if (sx == maxSz.getX()) {
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    if (matchesAt(level, origin.offset(-1, y, z), blockMap)) return true;
                    if (matchesAt(level, origin.offset(sx, y, z), blockMap)) return true;
                }
            }
        }
        if (sy == maxSz.getY()) {
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    if (matchesAt(level, origin.offset(x, -1, z), blockMap)) return true;
                    if (matchesAt(level, origin.offset(x, sy, z), blockMap)) return true;
                }
            }
        }
        if (sz == maxSz.getZ()) {
            for (int x = 0; x < sx; x++) {
                for (int y = 0; y < sy; y++) {
                    if (matchesAt(level, origin.offset(x, y, -1), blockMap)) return true;
                    if (matchesAt(level, origin.offset(x, y, sz), blockMap)) return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesAt(ServerLevel level, BlockPos p, Map<Character, BlockIngredient> blockMap) {
        return matchesAny(blockMap, level, p, level.getBlockState(p));
    }

    /**
     * Re-validates the single largest/most "natural" candidate (activation at the box's own corner,
     * full maxSize) purely to surface a human-readable reason after every candidate in {@link #matches}
     * has already failed - see that method's own comment on why this isn't tracked during the main
     * search itself. Returns {@code null} if even this candidate's activation offset is out of range
     * (falls back to the generic message in {@link #matches}).
     */
    private String describeFailure(ServerLevel level, BlockPos activationPos, Vec3i maxSz,
                                    MultiblockDefinition definition, Map<Character, BlockIngredient> blockMap,
                                    boolean hasShellConfig, boolean hasInteriorConfig) {
        BlockPos origin = activationPos;
        int sx = maxSz.getX(), sy = maxSz.getY(), sz = maxSz.getZ();

        for (int x = 0; x < sx; x++) {
            boolean edgeX = x == 0 || x == sx - 1;
            for (int y = 0; y < sy; y++) {
                boolean edgeY = y == 0 || y == sy - 1;
                for (int z = 0; z < sz; z++) {
                    boolean edgeZ = z == 0 || z == sz - 1;
                    boolean isBoundary = edgeX || edgeY || edgeZ;

                    BlockPos p = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(p);

                    if (isBoundary) {
                        if (hasShellConfig) {
                            BlockIngredient required = resolveShellIngredient(definition, p, origin, maxSz);
                            if (required != null && !required.matches(level, p, state)) {
                                return "Wrong shell block at " + formatPos(p);
                            }
                        } else if (!hasInteriorConfig && !matchesAny(blockMap, level, p, state)) {
                            return "Missing/wrong block at " + formatPos(p)
                                    + " - this structure must be a solid block of its declared materials, with no gaps";
                        }
                    } else {
                        if (hasInteriorConfig) {
                            if (!definition.getInteriorIngredient().get().matches(level, p, state)) {
                                return "Wrong interior block at " + formatPos(p);
                            }
                        } else if (!hasShellConfig && !matchesAny(blockMap, level, p, state)) {
                            return "Missing/wrong block at " + formatPos(p)
                                    + " - this structure must be a solid block of its declared materials, with no gaps";
                        }
                    }
                }
            }
        }
        return null;
    }

    private static String formatSize(Vec3i v) {
        return v.getX() + "x" + v.getY() + "x" + v.getZ();
    }

    private static String formatPos(BlockPos p) {
        return "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
    }

    private BlockIngredient resolveShellIngredient(MultiblockDefinition definition,
                                                   BlockPos pos, BlockPos minCorner, Vec3i size) {
        int minX = minCorner.getX(), minY = minCorner.getY(), minZ = minCorner.getZ();
        int maxX = minX + size.getX() - 1;
        int maxY = minY + size.getY() - 1;
        int maxZ = minZ + size.getZ() - 1;

        Map<Direction, BlockIngredient> faces = definition.getShellFaces();
        if (!faces.isEmpty()) {
            if (pos.getY() == maxY && faces.containsKey(Direction.UP)) return faces.get(Direction.UP);
            if (pos.getY() == minY && faces.containsKey(Direction.DOWN)) return faces.get(Direction.DOWN);
            if (pos.getZ() == minZ && faces.containsKey(Direction.NORTH)) return faces.get(Direction.NORTH);
            if (pos.getZ() == maxZ && faces.containsKey(Direction.SOUTH)) return faces.get(Direction.SOUTH);
            if (pos.getX() == minX && faces.containsKey(Direction.WEST)) return faces.get(Direction.WEST);
            if (pos.getX() == maxX && faces.containsKey(Direction.EAST)) return faces.get(Direction.EAST);
        }
        return definition.getShellIngredient().orElse(null);
    }

    private static MatchResult.Failure failure(String msg) {
        return new MatchResult.Failure(new MatchFailureReport(1, List.of(), msg));
    }
}
