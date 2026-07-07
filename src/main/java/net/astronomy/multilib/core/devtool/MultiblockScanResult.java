package net.astronomy.multilib.core.devtool;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Pure, world-independent result of scanning a world area for a multiblock pattern (see
 * {@link MultiblockScanner}). Consumers ({@link MultiblockDevExporter}, the dev-tool GUI/BE) work
 * exclusively off this record - nothing downstream needs to touch a {@code Level} again.
 *
 * <p>{@code layers} follows the same top-to-bottom, row/column convention already used by
 * {@code MultiblockBuilder.layer(...)} and {@link net.astronomy.multilib.core.structure.MultiblockStructureExporter}:
 * {@code layers.get(0)} is the highest layer, the last entry is the lowest. Within a layer, each
 * {@code String} is one row (index = row number), and the character index within that row is the
 * column. Axis convention (matching {@code OverlayRequestHandler.calculateGhostBlocks}): column
 * increases with X (east), row increases with Z (south).
 *
 * @param layers          top-to-bottom list of layers; each layer is a top-to-bottom... actually
 *                        row-ordered list of row strings, one character per column.
 * @param symbolToBlock   symbol -> block id, in first-appearance order (see {@link MultiblockScanner}).
 * @param coreSymbol      symbol tagged as the core block, or {@code null} if nothing was tagged.
 * @param activationSymbol symbol tagged as the activation block, or {@code null} if nothing was
 *                        tagged. Populated as a fallback (instead of {@code coreSymbol}) when the
 *                        tagged block type occurs more than once in the scanned area.
 */
public record MultiblockScanResult(
        List<List<String>> layers,
        LinkedHashMap<Character, ResourceLocation> symbolToBlock,
        Character coreSymbol,
        Character activationSymbol
) {

    /** How many cells across every layer/row hold {@code symbol} - used to show a count next to each block type in the GUI/HUD lists. Not used for the core/activation heuristic - see {@link #countLogicalOccurrences}. */
    public int countOccurrences(char symbol) {
        int count = 0;
        for (List<String> layer : layers) {
            for (String row : layer) {
                for (int i = 0; i < row.length(); i++) {
                    if (row.charAt(i) == symbol) count++;
                }
            }
        }
        return count;
    }

    /**
     * How many *logical* block instances {@code symbol} represents - used to decide core (exactly one)
     * vs. activation (more than one). Differs from {@link #countOccurrences} for a recognized multi-part
     * block (door, tall plant, bed - see {@link MultiblockMultiPartBlocks}): a single door occupies 2
     * cells but is one logical block, so it must count as 1 here, not 2, or it would be permanently
     * mis-tagged as a duplicate/activation instead of the unique core it actually is.
     */
    public int countLogicalOccurrences(char symbol, Block block) {
        List<BlockPos> positions = new ArrayList<>();
        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            List<String> layer = layers.get(layerIdx);
            for (int row = 0; row < layer.size(); row++) {
                String line = layer.get(row);
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == symbol) {
                        // Pseudo-world-space coordinates: only relative adjacency matters for grouping,
                        // not real placement, so (col, layerIdx, row) as (x, y, z) is fine as-is.
                        positions.add(new BlockPos(col, layerIdx, row));
                    }
                }
            }
        }
        return MultiblockMultiPartBlocks.countLogicalInstances(positions, block);
    }
}
