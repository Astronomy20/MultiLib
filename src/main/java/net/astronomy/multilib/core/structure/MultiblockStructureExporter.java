package net.astronomy.multilib.core.structure;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Converts a shaped {@link MultiblockDefinition} into a vanilla structure NBT (the same format
 * produced by a Structure Block), without needing a real {@code Level} to read block states from.
 * Consumers embed the resulting file wherever a structure-file-driven preview is wanted — e.g. a
 * GuideME {@code <GameScene><ImportStructure src="..."/></GameScene>} tag — without having to
 * hand-build the structure in a test world first.
 *
 * <p>Only shaped (non-shapeless, non-PatternProvider) definitions with at least one occupied
 * cell are supported, matching the other pattern-consuming compat helpers.
 */
public final class MultiblockStructureExporter {

    private MultiblockStructureExporter() {}

    /**
     * Builds the structure NBT compound for the given definition, or {@link Optional#empty()} if
     * the definition is shapeless, functional (PatternProvider-based), or has no occupied cells.
     */
    public static Optional<CompoundTag> export(MultiblockDefinition definition) {
        if (definition.isShapeless()
                || definition.getPatternProvider().isPresent()
                || definition.getLayers().isEmpty()) {
            return Optional.empty();
        }

        var layers = definition.getLayers(); // top-to-bottom
        Map<Character, BlockState> resolved = resolveSymbols(definition);

        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> paletteIndex = new LinkedHashMap<>();
        ListTag blocksTag = new ListTag();

        int layerCount = layers.size();
        int width = 0;
        int depth = layers.get(0).size();
        for (int y = 0; y < layerCount; y++) {
            var layer = layers.get(layerCount - 1 - y); // bottom-to-top for the structure's Y axis
            for (int row = 0; row < layer.size(); row++) {
                String rowStr = layer.get(row);
                width = Math.max(width, rowStr.length());
                for (int col = 0; col < rowStr.length(); col++) {
                    char symbol = rowStr.charAt(col);
                    if (symbol == ' ') continue;
                    BlockState state = resolved.get(symbol);
                    if (state == null) continue;

                    int idx = paletteIndex.computeIfAbsent(state, s -> {
                        palette.add(s);
                        return palette.size() - 1;
                    });

                    CompoundTag block = new CompoundTag();
                    block.put("pos", intList(col, y, row));
                    block.putInt("state", idx);
                    blocksTag.add(block);
                }
            }
        }

        if (blocksTag.isEmpty()) {
            return Optional.empty();
        }

        ListTag paletteTag = new ListTag();
        for (BlockState state : palette) {
            paletteTag.add(NbtUtils.writeBlockState(state));
        }

        CompoundTag root = new CompoundTag();
        root.put("size", intList(width, layerCount, depth));
        root.put("entities", new ListTag());
        root.put("blocks", blocksTag);
        root.put("palette", paletteTag);
        return Optional.of(NbtUtils.addCurrentDataVersion(root));
    }

    /** Same as {@link #export(MultiblockDefinition)}, serialized to the human-readable SNBT text format. */
    public static Optional<String> exportToSnbt(MultiblockDefinition definition) {
        return export(definition).map(NbtUtils::structureToSnbt);
    }

    /**
     * Finds the exported structure-local (x, y, z) position of the definition's core symbol, for
     * callers that want to annotate/highlight it (e.g. a GameScene {@code BlockAnnotation}).
     * Falls back to the first occupied cell if the definition has no core symbol.
     */
    public static Optional<int[]> findCorePosition(MultiblockDefinition definition) {
        if (definition.isShapeless()
                || definition.getPatternProvider().isPresent()
                || definition.getLayers().isEmpty()) {
            return Optional.empty();
        }

        var layers = definition.getLayers();
        int layerCount = layers.size();
        Character core = definition.hasCore() ? definition.getCoreSymbol() : null;
        int[] fallback = null;

        for (int y = 0; y < layerCount; y++) {
            var layer = layers.get(layerCount - 1 - y);
            for (int row = 0; row < layer.size(); row++) {
                String rowStr = layer.get(row);
                for (int col = 0; col < rowStr.length(); col++) {
                    char symbol = rowStr.charAt(col);
                    if (symbol == ' ') continue;
                    if (core != null && symbol == core) {
                        return Optional.of(new int[] { col, y, row });
                    }
                    if (fallback == null) {
                        fallback = new int[] { col, y, row };
                    }
                }
            }
        }
        return Optional.ofNullable(fallback);
    }

    private static Map<Character, BlockState> resolveSymbols(MultiblockDefinition definition) {
        Map<Character, BlockState> resolved = new LinkedHashMap<>();
        for (var entry : definition.getBlockMap().entrySet()) {
            var candidates = entry.getValue().getCandidateBlocks();
            if (!candidates.isEmpty()) {
                resolved.put(entry.getKey(), candidates.iterator().next().defaultBlockState());
            }
        }
        return resolved;
    }

    private static ListTag intList(int x, int y, int z) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(x));
        list.add(IntTag.valueOf(y));
        list.add(IntTag.valueOf(z));
        return list;
    }
}
