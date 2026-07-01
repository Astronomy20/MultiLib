package net.astronomy.multilib.api.progress;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.core.matching.ShapedMatcher;
import net.astronomy.multilib.core.matching.StructureOrientation;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reports how complete a multiblock structure is, so a consuming mod can show its own progress UI
 * (a progress bar, a "you still need N of block X" shopping list, etc.) without reimplementing
 * pattern matching. Read-only — this never places, breaks, or otherwise changes anything.
 * <p>
 * Only structures declared via {@code .layer(...)} (i.e. backed by a {@code ShapedMatcher}) are
 * supported for now — the same scope {@code AutoPlaceRequestHandler} already covers. Structures
 * declared via a {@link net.astronomy.multilib.api.pattern.PatternProvider} or marked
 * {@code .shapeless()} return {@link Optional#empty()}.
 */
public final class MultiblockProgressAPI {

    private MultiblockProgressAPI() {}

    /**
     * Computes a fresh {@link StructureProgress} for the multiblock whose core sits at {@code corePos}.
     *
     * @return empty if there's no registered multiblock whose core matches the block at
     *         {@code corePos}, or if that definition isn't a supported (layer-based) pattern.
     */
    public static Optional<StructureProgress> compute(ServerLevel level, BlockPos corePos) {
        MultiblockDefinition definition = findDefinitionAt(level, corePos);
        if (definition == null || definition.getLayers().isEmpty()) return Optional.empty();

        // Ground truth from whatever's already placed wins over an arbitrary default — see
        // StructureOrientation.detectFromPlacedBlocks. Only a bare, freshly-placed core (nothing built
        // around it yet) has no ground truth to detect, so the identity orientation is used as a
        // neutral default: with zero blocks placed, every orientation needs exactly the same set of
        // block *types* in the same *quantities*, just at different world positions, so the choice
        // only affects where a caller would highlight/render the missing positions, not the totals.
        StructureOrientation.Orientation orientation = StructureOrientation
                .detectFromPlacedBlocks(level, corePos, definition)
                .orElse(new StructureOrientation.Orientation("Y", 0));

        List<List<String>> layers = definition.getLayers();
        Map<Character, BlockIngredient> blockMap = definition.getBlockMap();
        char coreSymbol = definition.getCoreSymbol();
        Set<Character> freeBlockSymbols = definition.getFreeBlocks().keySet();
        String axis = orientation.axis();
        int rotation = orientation.rotation();

        BlockPos origin = StructureOrientation.findSymbolOrigin(corePos, layers, coreSymbol, axis, rotation);

        int total = 0;
        List<MissingBlock> missing = new ArrayList<>();

        for (int layerIdx = 0; layerIdx < layers.size(); layerIdx++) {
            List<String> layer = layers.get(layerIdx);
            int height = layer.size();
            if (height == 0) continue;
            int width = layer.get(0).length();
            int centerX = width / 2;
            int centerZ = height / 2;
            // layers[0] = topmost declared layer, layers[last] = bottommost (ShapedMatcher's convention).
            int relY = (layers.size() - 1) - layerIdx;

            for (int row = 0; row < height; row++) {
                String line = layer.get(row);
                for (int col = 0; col < Math.min(width, line.length()); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;
                    if (freeBlockSymbols.contains(symbol)) continue;
                    BlockIngredient ingredient = blockMap.get(symbol);
                    if (ingredient == null) continue;

                    total++;

                    int relX = col - centerX;
                    int relZ = row - centerZ;
                    int[] t = ShapedMatcher.applyTransform(relX, relY, relZ, axis, rotation);
                    BlockPos worldPos = origin.offset(t[0], t[1], t[2]);

                    if (!ingredient.matches(level.getBlockState(worldPos))) {
                        BlockState expected = getRepresentativeState(ingredient);
                        if (expected != null) missing.add(new MissingBlock(worldPos, expected));
                    }
                }
            }
        }

        return Optional.of(new StructureProgress(total, missing));
    }

    // Works for any registered definition, not just autoPlace()-enabled ones — this API isn't tied to
    // the auto-place feature, unlike AutoPlaceRequestHandler.findAutoPlaceDefinitionAt.
    private static MultiblockDefinition findDefinitionAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        for (MultiblockDefinition def : MultiblockRegistry.getCandidatesFor(state.getBlock())) {
            if (def.matchesCore(state)) return def;
        }
        return null;
    }

    private static BlockState getRepresentativeState(BlockIngredient ingredient) {
        Set<Block> candidates = ingredient.getCandidateBlocks();
        if (!candidates.isEmpty()) return candidates.iterator().next().defaultBlockState();
        return null;
    }
}
