package net.astronomy.multilib.core.devtool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recognizes vanilla multi-part blocks - doors and tall plants (both use
 * {@link BlockStateProperties#DOUBLE_BLOCK_HALF}) and beds (use {@link BlockStateProperties#BED_PART})
 * - so the dev tool can treat the two world positions one of these occupies as a single logical block
 * instance, instead of two independent ones. Detection is property-based rather than an
 * {@code instanceof} list, so it also covers any modded block reusing the same vanilla properties.
 * <p>
 * Two places in {@code MultiblockDevBlockEntity}/{@code MultiblockScanResult} needed this: the
 * core-vs-activation heuristic ("is this block type unique in the area?"), which previously counted a
 * single door/bed as 2 occurrences and mis-tagged it as a duplicate/activation instead of the unique
 * core it actually is; and {@code placePatternInWorld} (the Load tab's in-world placement), which
 * previously stamped {@code Block.defaultBlockState()} independently onto both of a door/bed's cells -
 * producing two broken lower-halves instead of one working door.
 * <p>
 * The dev tool's export format only ever records a bare {@code Block} type per symbol (no per-position
 * BlockState/NBT - see {@code MultiblockDevExporter}'s own scope note), so there's no original
 * facing/hinge to restore; {@link #relinkPlaced} always derives a fresh, functionally-correct pairing
 * instead (arbitrary but consistent hinge, a facing computed from the two positions' own layout for
 * beds).
 */
public final class MultiblockMultiPartBlocks {

    private MultiblockMultiPartBlocks() {}

    private static boolean isVerticalHalf(Block block) {
        return block.defaultBlockState().hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
    }

    private static boolean isBedPart(Block block) {
        return block.defaultBlockState().hasProperty(BlockStateProperties.BED_PART);
    }

    /** Whether one logical instance of {@code block} spans more than one world position (door, tall plant, bed, ...). */
    public static boolean isMultiPart(Block block) {
        return isVerticalHalf(block) || isBedPart(block);
    }

    /**
     * Groups {@code positions} (all already known to hold {@code block}) into logical instances -
     * adjacent pairs for a recognized multi-part block (vertically for doors/tall plants, horizontally
     * for beds), one instance per position otherwise. A half found without its other half (e.g. a
     * pattern trimmed awkwardly) still counts as its own instance rather than being dropped.
     */
    public static List<List<BlockPos>> groupIntoLogicalInstances(List<BlockPos> positions, Block block) {
        List<List<BlockPos>> groups = new ArrayList<>();
        if (positions.isEmpty()) return groups;

        boolean vertical = isVerticalHalf(block);
        boolean bed = isBedPart(block);
        if (!vertical && !bed) {
            for (BlockPos pos : positions) groups.add(List.of(pos));
            return groups;
        }

        Set<BlockPos> remaining = new HashSet<>(positions);
        for (BlockPos pos : positions) {
            if (!remaining.remove(pos)) continue;
            BlockPos partner = vertical ? findVerticalPartner(remaining, pos) : findHorizontalPartner(remaining, pos);
            if (partner != null) {
                remaining.remove(partner);
                groups.add(List.of(pos, partner));
            } else {
                groups.add(List.of(pos));
            }
        }
        return groups;
    }

    private static BlockPos findVerticalPartner(Set<BlockPos> remaining, BlockPos pos) {
        BlockPos above = pos.above();
        if (remaining.contains(above)) return above;
        BlockPos below = pos.below();
        return remaining.contains(below) ? below : null;
    }

    private static BlockPos findHorizontalPartner(Set<BlockPos> remaining, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(dir);
            if (remaining.contains(neighbor)) return neighbor;
        }
        return null;
    }

    /** How many logical instances {@code positions} (all holding {@code block}) collapse into - see {@link #groupIntoLogicalInstances}. */
    public static int countLogicalInstances(List<BlockPos> positions, Block block) {
        return groupIntoLogicalInstances(positions, block).size();
    }

    /**
     * Overwrites {@code positions} - already placed with {@code block.defaultBlockState()} by the caller
     * - with properly linked multi-part states where recognized: a door/tall-plant pair gets
     * {@code HALF=LOWER}/{@code HALF=UPPER} (whichever position is actually lower/higher in the world),
     * a bed pair gets {@code PART=FOOT}/{@code PART=HEAD} plus a {@code FACING} computed from the foot's
     * direction toward the head. A no-op for anything {@link #isMultiPart} says isn't multi-part, or for
     * a position that couldn't be paired with another (left as whatever default state the caller already
     * placed).
     */
    public static void relinkPlaced(Level level, List<BlockPos> positions, Block block) {
        if (!isMultiPart(block)) return;
        boolean vertical = isVerticalHalf(block);
        for (List<BlockPos> group : groupIntoLogicalInstances(positions, block)) {
            if (group.size() != 2) continue;
            BlockPos a = group.get(0);
            BlockPos b = group.get(1);
            if (vertical) {
                BlockPos lower = a.getY() <= b.getY() ? a : b;
                BlockPos upper = lower == a ? b : a;
                BlockState base = block.defaultBlockState();
                level.setBlock(lower, base.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER), 3);
                level.setBlock(upper, base.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), 3);
            } else {
                Direction facing = Direction.fromDelta(b.getX() - a.getX(), 0, b.getZ() - a.getZ());
                if (facing == null || !facing.getAxis().isHorizontal()) continue;
                BlockState footState = block.defaultBlockState()
                        .setValue(BlockStateProperties.BED_PART, BedPart.FOOT)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
                BlockState headState = footState.setValue(BlockStateProperties.BED_PART, BedPart.HEAD);
                level.setBlock(a, footState, 3);
                level.setBlock(b, headState, 3);
            }
        }
    }
}
