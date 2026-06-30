package net.astronomy.multilib.api.pattern.providers;

import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class LayeredPatternProvider implements PatternProvider {
    private final List<List<String>> layers;
    private final Map<Character, BlockIngredient> blockMap;
    private final Vec3i size;

    public LayeredPatternProvider(List<List<String>> layers, Map<Character, BlockIngredient> blockMap) {
        this.layers = layers;
        this.blockMap = blockMap;
        int layerCount = layers.size();
        int depth = (layerCount > 0) ? layers.get(0).size() : 0;
        int width = (layerCount > 0 && !layers.get(0).isEmpty()) ? layers.get(0).get(0).length() : 0;
        this.size = new Vec3i(width, layerCount, depth);
    }

    @Override
    public @Nullable BlockIngredient getIngredientAt(int x, int y, int z) {
        if (y < 0 || y >= layers.size()) return null;
        List<String> layer = layers.get(y);
        if (z < 0 || z >= layer.size()) return null;
        String row = layer.get(z);
        if (x < 0 || x >= row.length()) return null;
        char symbol = row.charAt(x);
        if (symbol == ' ') return null;
        return blockMap.get(symbol);
    }

    @Override
    public Vec3i getSize() {
        return size;
    }
}
