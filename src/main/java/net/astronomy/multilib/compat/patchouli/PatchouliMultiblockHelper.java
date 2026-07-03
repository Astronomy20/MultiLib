package net.astronomy.multilib.compat.patchouli;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import vazkii.patchouli.api.IMultiblock;
import vazkii.patchouli.api.IStateMatcher;
import vazkii.patchouli.api.PatchouliAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility for converting a {@link MultiblockDefinition} into a Patchouli {@link IMultiblock}
 * and registering it so Patchouli book entries can reference it by its ResourceLocation.
 *
 * <p>Only shaped (non-shapeless, non-PatternProvider) definitions with at least one layer
 * are supported. Call {@link #register(MultiblockDefinition)} during common setup (e.g. from a
 * {@code FMLCommonSetupEvent} listener in a dependent mod).
 */
public final class PatchouliMultiblockHelper {

    private PatchouliMultiblockHelper() {}

    /**
     * Converts a shaped MultiblockDefinition into a Patchouli IMultiblock.
     * Returns {@code null} if the definition is shapeless, functional (PatternProvider-based),
     * or has no layers.
     */
    public static IMultiblock createMultiblock(MultiblockDefinition definition) {
        if (definition.isShapeless()
                || definition.getPatternProvider().isPresent()
                || definition.getLayers().isEmpty()) {
            return null;
        }

        var layers = definition.getLayers();
        var blockMap = definition.getBlockMap();

        // Patchouli expects layers bottom-to-top; MultiLib stores top-to-bottom.
        // MultiLib uses ' ' (space) for empty/air positions in its own pattern strings,
        // never '0' — so '0' never occurs naturally here.
        int layerCount = layers.size();
        char[][][] grid = new char[layerCount][][];
        for (int i = 0; i < layerCount; i++) {
            var layer = layers.get(layerCount - 1 - i); // reverse order
            grid[i] = layer.stream().map(String::toCharArray).toArray(char[][]::new);
        }

        // Patchouli requires exactly one '0' cell in the whole pattern: it marks the
        // structure's anchor/center point (see DenseMultiblock#build, "A structure can't
        // have no center"). '0' is NOT simply an air alias — it defaults to air only
        // because nothing else claims it. We pick the definition's core symbol position
        // (falling back to the first occupied cell) and mark it as the center, while
        // keeping its original block matcher so the book preview still shows the right block.
        int[] centerPos = findCenterPosition(grid, definition);
        if (centerPos == null) {
            MultiLib.LOGGER.warn(
                    "[MultiLib/Patchouli] Could not find a center position for multiblock '{}'; skipping Patchouli registration",
                    definition.getId());
            return null;
        }
        char centerSymbol = grid[centerPos[0]][centerPos[1]][centerPos[2]];
        grid[centerPos[0]][centerPos[1]][centerPos[2]] = '0';

        String[][] patchouliLayers = new String[layerCount][];
        for (int i = 0; i < layerCount; i++) {
            patchouliLayers[i] = new String[grid[i].length];
            for (int y = 0; y < grid[i].length; y++) {
                patchouliLayers[i][y] = new String(grid[i][y]);
            }
        }

        var api = PatchouliAPI.get();
        List<Object> keys = new ArrayList<>();
        keys.add('0');
        keys.add(centerSymbolMatcher(api, blockMap, centerSymbol));

        for (var entry : blockMap.entrySet()) {
            char sym = entry.getKey();
            if (sym == '0') continue; // already handled as the center marker above
            var ingredient = entry.getValue();
            var candidates = ingredient.getCandidateBlocks();
            if (!candidates.isEmpty()) {
                Block block = candidates.iterator().next();
                keys.add(sym);
                keys.add(api.stateMatcher(block.defaultBlockState()));
            }
        }

        return api.makeMultiblock(patchouliLayers, keys.toArray());
    }

    /**
     * Finds the grid cell to use as Patchouli's mandatory center marker: the definition's
     * core symbol if present, otherwise the first non-air cell. Returns {@code null} if the
     * pattern has no occupied cells at all.
     */
    private static int[] findCenterPosition(char[][][] grid, MultiblockDefinition definition) {
        Character preferred = definition.hasCore() ? definition.getCoreSymbol() : null;
        int[] fallback = null;
        for (int layer = 0; layer < grid.length; layer++) {
            for (int row = 0; row < grid[layer].length; row++) {
                for (int col = 0; col < grid[layer][row].length; col++) {
                    char c = grid[layer][row][col];
                    if (c == ' ') continue;
                    if (preferred != null && c == preferred) {
                        return new int[] { layer, row, col };
                    }
                    if (fallback == null) {
                        fallback = new int[] { layer, row, col };
                    }
                }
            }
        }
        return fallback;
    }

    /** Resolves the matcher used to render/validate the cell chosen as the center marker. */
    private static IStateMatcher centerSymbolMatcher(
            PatchouliAPI.IPatchouliAPI api, Map<Character, BlockIngredient> blockMap, char centerSymbol) {
        var ingredient = blockMap.get(centerSymbol);
        if (ingredient != null) {
            var candidates = ingredient.getCandidateBlocks();
            if (!candidates.isEmpty()) {
                return api.stateMatcher(candidates.iterator().next().defaultBlockState());
            }
        }
        return api.stateMatcher(Blocks.AIR.defaultBlockState());
    }

    /**
     * Registers the multiblock with Patchouli under the definition's own ResourceLocation.
     * After this call, Patchouli book JSON entries can reference it with the definition id.
     * Call this during common setup (before world load).
     */
    public static void register(MultiblockDefinition definition) {
        IMultiblock mb = createMultiblock(definition);
        if (mb != null) {
            PatchouliAPI.get().registerMultiblock(definition.getId(), mb);
        }
    }
}
