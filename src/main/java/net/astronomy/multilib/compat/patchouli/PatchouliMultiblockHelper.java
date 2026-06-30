package net.astronomy.multilib.compat.patchouli;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import vazkii.patchouli.api.IMultiblock;
import vazkii.patchouli.api.PatchouliAPI;

import java.util.ArrayList;
import java.util.List;

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

        // Patchouli expects layers bottom-to-top; MultiLib stores top-to-bottom
        int layerCount = layers.size();
        String[][] patchouliLayers = new String[layerCount][];
        for (int i = 0; i < layerCount; i++) {
            var layer = layers.get(layerCount - 1 - i); // reverse order
            patchouliLayers[i] = layer.toArray(new String[0]);
        }

        // Build the alternating char → IStateMatcher key array.
        // '0' is used by Patchouli as the implicit air/space character.
        // TODO: verify API version compatibility — confirm Patchouli treats '0' as air here
        var api = PatchouliAPI.get();
        List<Object> keys = new ArrayList<>();
        keys.add('0');
        keys.add(api.stateMatcher(Blocks.AIR.defaultBlockState()));

        for (var entry : blockMap.entrySet()) {
            char sym = entry.getKey();
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
