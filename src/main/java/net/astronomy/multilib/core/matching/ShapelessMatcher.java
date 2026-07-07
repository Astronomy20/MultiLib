package net.astronomy.multilib.core.matching;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.definition.ShapelessRequirement;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

public class ShapelessMatcher implements IPatternMatcher {

    // Direction.values() clones the array on every call; the flood fill queries it once per visited block.
    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    public MatchResult matches(ServerLevel level, BlockPos activationPos, MultiblockDefinition definition) {
        Vec3i maxSize = definition.getShapelessMaxSize();

        Set<BlockPos> found = floodFill(level, activationPos, maxSize);
        if (found.isEmpty()) {
            return failure("No blocks found in flood fill");
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : found) {
            if (p.getX() < minX) minX = p.getX();
            if (p.getY() < minY) minY = p.getY();
            if (p.getZ() < minZ) minZ = p.getZ();
            if (p.getX() > maxX) maxX = p.getX();
            if (p.getY() > maxY) maxY = p.getY();
            if (p.getZ() > maxZ) maxZ = p.getZ();
        }

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        Vec3i actualSize = new Vec3i(sizeX, sizeY, sizeZ);

        Vec3i minSz = definition.getShapelessMinSize();
        if (sizeX < minSz.getX() || sizeY < minSz.getY() || sizeZ < minSz.getZ()) {
            return failure("Structure smaller than minSize: " + actualSize);
        }
        if (sizeX > maxSize.getX() || sizeY > maxSize.getY() || sizeZ > maxSize.getZ()) {
            return failure("Structure larger than maxSize: " + actualSize);
        }

        BlockPos minCorner = new BlockPos(minX, minY, minZ);

        // Shell validation
        for (BlockPos p : found) {
            boolean isShell = p.getX() == minX || p.getX() == maxX
                    || p.getY() == minY || p.getY() == maxY
                    || p.getZ() == minZ || p.getZ() == maxZ;
            if (!isShell) continue;

            BlockIngredient required = resolveShellIngredient(definition, p, minCorner, actualSize);
            if (required != null && !required.matches(level, p, level.getBlockState(p))) {
                return failure("Shell block mismatch at " + p);
            }
        }

        // Interior validation
        if (definition.getInteriorIngredient().isPresent()) {
            BlockIngredient interior = definition.getInteriorIngredient().get();
            for (int x = minX + 1; x < maxX; x++) {
                for (int y = minY + 1; y < maxY; y++) {
                    for (int z = minZ + 1; z < maxZ; z++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (!interior.matches(level, p, level.getBlockState(p))) {
                            return failure("Interior block mismatch at " + p);
                        }
                    }
                }
            }
        }

        // Shapeless requirements
        for (ShapelessRequirement req : definition.getShapelessRequirements()) {
            int count = 0;
            for (BlockPos p : found) {
                if (req.ingredient().matches(level, p, level.getBlockState(p))) count++;
            }
            if (count < req.min() || count > req.max()) {
                return failure("Requirement not met: found " + count
                        + " blocks (need " + req.min() + "-" + req.max() + ")");
            }
        }

        // Build symbol position map
        Map<Character, BlockIngredient> blockMap = definition.getBlockMap();
        Map<Character, Set<BlockPos>> symbolPositions = new HashMap<>();
        for (BlockPos p : found) {
            BlockState state = level.getBlockState(p);
            for (Map.Entry<Character, BlockIngredient> entry : blockMap.entrySet()) {
                if (entry.getValue().matches(level, p, state)) {
                    symbolPositions.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add(p);
                    break;
                }
            }
        }

        Map<Character, Set<BlockPos>> immutableSymbolPos = symbolPositions.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey, e -> Collections.unmodifiableSet(e.getValue())));

        MatchData matchData = new MatchData(
                activationPos,
                new TransformData(0, false, "NONE"),
                Collections.unmodifiableSet(found), // floodFill's set is local and never touched again - no defensive copy needed
                immutableSymbolPos,
                actualSize
        );
        return new MatchResult.Success(matchData);
    }

    private Set<BlockPos> floodFill(ServerLevel level, BlockPos start, Vec3i maxSize) {
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> found = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);
            if (state.isAir()) continue;

            found.add(current);

            for (Direction dir : DIRECTIONS) {
                BlockPos next = current.relative(dir);
                if (visited.contains(next)) continue;

                int dx = Math.abs(next.getX() - start.getX());
                int dy = Math.abs(next.getY() - start.getY());
                int dz = Math.abs(next.getZ() - start.getZ());
                if (dx >= maxSize.getX() || dy >= maxSize.getY() || dz >= maxSize.getZ()) continue;

                visited.add(next);
                queue.add(next);
            }
        }
        return found;
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
