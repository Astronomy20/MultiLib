package net.astronomy.multilib.core.devtool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a raw world area (already read into a plain {@code Map<BlockPos, BlockState>} by the
 * caller) into a {@link MultiblockScanResult} - a pattern of {@code layer(...)} rows plus a
 * symbol-to-block map, with no further access to a {@code Level} required. This keeps the scanner
 * itself testable with a fake map, the same spirit as
 * {@link net.astronomy.multilib.core.structure.MultiblockStructureExporter} operating purely off a
 * {@code MultiblockDefinition} without a {@code Level}.
 *
 * <p>Determinism is the whole point here: scanning the exact same area twice, with no real change
 * to the world, must produce byte-for-byte the same {@link MultiblockScanResult} - otherwise a
 * harmless re-scan would silently reshuffle symbol assignments in an already-exported pattern.
 * Symbols are therefore assigned strictly in first-appearance order during a single deterministic
 * pass: layer 0..N (top to bottom), then row 0..height, then column 0..width - the same order used
 * to build the row strings themselves.
 */
public final class MultiblockScanner {

    /**
     * A-Z then a-z (52 symbols) - comfortably more than any realistic multiblock needs, while
     * deliberately excluding digits: {@code PatchouliMultiblockHelper} treats {@code '0'} as a required
     * center marker in its own (unrelated) pattern format, and mixing that convention with an
     * auto-assigned digit symbol here - even though this class has no direct dependency on Patchouli -
     * isn't worth the ambiguity for a symbol alphabet already large enough without digits.
     */
    private static final char[] SYMBOL_ALPHABET = buildAlphabet();
    private static final int MAX_SYMBOLS = SYMBOL_ALPHABET.length;

    private static char[] buildAlphabet() {
        StringBuilder sb = new StringBuilder();
        for (char c = 'A'; c <= 'Z'; c++) sb.append(c);
        for (char c = 'a'; c <= 'z'; c++) sb.append(c);
        return sb.toString().toCharArray();
    }

    /** How many distinct block types a single scan can tell apart - one symbol per type, see {@link #SYMBOL_ALPHABET}. */
    public static int maxSymbols() {
        return MAX_SYMBOLS;
    }

    private MultiblockScanner() {}

    /** Why a scan didn't produce a result - lets the caller show a specific, correct chat message instead of one generic "scan failed" for every case. */
    public enum FailureReason {
        /** Not a failure - {@link ScanOutcome#result()} is present. */
        NONE,
        /** The area contains no non-air blocks at all. */
        EMPTY_AREA,
        /** More than {@value MultiblockScanner#MAX_SYMBOLS} distinct non-air {@link Block}s were found. */
        TOO_MANY_BLOCK_TYPES,
        /**
         * A recognized multi-part block (door, tall plant, bed - see {@link MultiblockMultiPartBlocks})
         * has only one of its two halves inside the scanned area. Refused rather than silently exported:
         * the export format only ever records a bare block type per symbol, so an incomplete half would
         * either get mis-tagged by the core/activation heuristic or placed back into the world on Load as
         * a broken, unlinked single half (see {@code MultiblockDevBlockEntity#placePatternInWorld}) -
         * resize the area to include the other half instead of silently growing it on the developer's
         * behalf, since that could just as easily pull in unrelated blocks the developer didn't intend.
         */
        INCOMPLETE_MULTIPART_BLOCK
    }

    /** @param result present only when {@code failureReason == NONE}. */
    public record ScanOutcome(Optional<MultiblockScanResult> result, FailureReason failureReason) {
        static ScanOutcome success(MultiblockScanResult r) {
            return new ScanOutcome(Optional.of(r), FailureReason.NONE);
        }

        static ScanOutcome failure(FailureReason reason) {
            return new ScanOutcome(Optional.empty(), reason);
        }
    }

    /**
     * Scans the given area and builds the layer/symbol pattern.
     *
     * @param area       absolute {@code BlockPos -> BlockState}, already clipped by the caller to
     *                   the inclusive {@code [min, max]} bounding box.
     * @param min        inclusive minimum corner of the bounding box (world coordinates).
     * @param max        inclusive maximum corner of the bounding box (world coordinates).
     * @param devBlockPos position of the dev block itself; always treated as air even if present
     *                   in {@code area} (defensive - the dev block should never legitimately fall
     *                   inside its own scanned area, but a misconfigured offset/size could put it
     *                   there).
     */
    public static ScanOutcome scan(Map<BlockPos, BlockState> area, BlockPos min, BlockPos max,
                                    BlockPos devBlockPos) {
        int width = max.getX() - min.getX() + 1;
        int height = max.getZ() - min.getZ() + 1;
        int layerCount = max.getY() - min.getY() + 1;
        if (width <= 0 || height <= 0 || layerCount <= 0) {
            return ScanOutcome.failure(FailureReason.EMPTY_AREA);
        }

        LinkedHashMap<Character, ResourceLocation> symbolToBlock = new LinkedHashMap<>();
        Map<Block, Character> blockToSymbol = new LinkedHashMap<>();
        List<List<String>> layers = new ArrayList<>(layerCount);
        boolean anyNonAir = false;

        // layer 0 = highest Y (top), matching MultiblockBuilder.layer(...)/MultiblockStructureExporter.
        for (int layerIdx = 0; layerIdx < layerCount; layerIdx++) {
            int y = max.getY() - layerIdx;
            List<String> rows = new ArrayList<>(height);
            for (int row = 0; row < height; row++) {
                // row increases with Z (south), same convention as OverlayRequestHandler.calculateGhostBlocks.
                int z = min.getZ() + row;
                StringBuilder line = new StringBuilder(width);
                for (int col = 0; col < width; col++) {
                    // column increases with X (east).
                    int x = min.getX() + col;
                    BlockPos pos = new BlockPos(x, y, z);

                    if (pos.equals(devBlockPos)) {
                        line.append(' ');
                        continue;
                    }

                    BlockState state = area.get(pos);
                    if (state == null || state.isAir()) {
                        line.append(' ');
                        continue;
                    }

                    Block block = state.getBlock();
                    Character symbol = blockToSymbol.get(block);
                    if (symbol == null) {
                        if (blockToSymbol.size() >= MAX_SYMBOLS) {
                            return ScanOutcome.failure(FailureReason.TOO_MANY_BLOCK_TYPES);
                        }
                        symbol = SYMBOL_ALPHABET[blockToSymbol.size()];
                        blockToSymbol.put(block, symbol);
                        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
                        symbolToBlock.put(symbol, blockId);
                    }
                    anyNonAir = true;
                    line.append(symbol);
                }
                rows.add(line.toString());
            }
            layers.add(rows);
        }

        if (!anyNonAir) {
            return ScanOutcome.failure(FailureReason.EMPTY_AREA);
        }

        if (hasIncompleteMultiPartBlock(layers, symbolToBlock)) {
            return ScanOutcome.failure(FailureReason.INCOMPLETE_MULTIPART_BLOCK);
        }

        return ScanOutcome.success(new MultiblockScanResult(trimPadding(layers), symbolToBlock, null, null));
    }

    /**
     * Whether any symbol mapping to a recognized multi-part block (see {@link MultiblockMultiPartBlocks})
     * has an unpaired position in {@code layers} - either because its other half sits just outside the
     * scanned area, or because it was already a broken lone half in the world. Either way it's not safe to
     * export as-is - see {@link FailureReason#INCOMPLETE_MULTIPART_BLOCK}.
     */
    private static boolean hasIncompleteMultiPartBlock(List<List<String>> layers, Map<Character, ResourceLocation> symbolToBlock) {
        Map<Character, List<BlockPos>> positionsBySymbol = new LinkedHashMap<>();
        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            List<String> layer = layers.get(layerIdx);
            for (int row = 0; row < layer.size(); row++) {
                String line = layer.get(row);
                for (int col = 0; col < line.length(); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;
                    // Pseudo-world-space coordinates: only relative adjacency matters for grouping, same
                    // convention as MultiblockScanResult#countLogicalOccurrences.
                    positionsBySymbol.computeIfAbsent(symbol, s -> new ArrayList<>()).add(new BlockPos(col, layerIdx, row));
                }
            }
        }

        for (Map.Entry<Character, List<BlockPos>> entry : positionsBySymbol.entrySet()) {
            ResourceLocation blockId = symbolToBlock.get(entry.getKey());
            if (blockId == null) continue;
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            if (!MultiblockMultiPartBlocks.isMultiPart(block)) continue;
            for (List<BlockPos> group : MultiblockMultiPartBlocks.groupIntoLogicalInstances(entry.getValue(), block)) {
                if (group.size() == 1) return true;
            }
        }
        return false;
    }

    /**
     * Crops the fully-empty border around the occupied cells - the configured dev-block area is
     * almost always larger than the actual multiblock inside it (e.g. a 5x5x5 scan area holding a
     * 2x3x2 structure), and exporting that untrimmed would bake the surrounding padding into every
     * generated pattern/layer string. Only the outer border is cropped; empty cells *between*
     * occupied ones (a genuine gap in the structure's own shape) are left alone.
     */
    private static List<List<String>> trimPadding(List<List<String>> layers) {
        int minLayer = Integer.MAX_VALUE, maxLayer = -1;
        int minRow = Integer.MAX_VALUE, maxRow = -1;
        int minCol = Integer.MAX_VALUE, maxCol = -1;

        for (int l = 0; l < layers.size(); l++) {
            List<String> layer = layers.get(l);
            for (int r = 0; r < layer.size(); r++) {
                String row = layer.get(r);
                for (int c = 0; c < row.length(); c++) {
                    if (row.charAt(c) != ' ') {
                        minLayer = Math.min(minLayer, l);
                        maxLayer = Math.max(maxLayer, l);
                        minRow = Math.min(minRow, r);
                        maxRow = Math.max(maxRow, r);
                        minCol = Math.min(minCol, c);
                        maxCol = Math.max(maxCol, c);
                    }
                }
            }
        }

        if (maxLayer < 0) return layers; // all-blank, shouldn't happen (caller already checked anyNonAir)

        List<List<String>> trimmed = new ArrayList<>(maxLayer - minLayer + 1);
        for (int l = minLayer; l <= maxLayer; l++) {
            List<String> layer = layers.get(l);
            List<String> rows = new ArrayList<>(maxRow - minRow + 1);
            for (int r = minRow; r <= maxRow; r++) {
                rows.add(layer.get(r).substring(minCol, maxCol + 1));
            }
            trimmed.add(rows);
        }
        return trimmed;
    }
}
