package net.astronomy.multilib.api.json;

import com.mojang.serialization.Codec;
import net.astronomy.multilib.api.ingredient.BlockIngredient;

public interface BlockIngredientSerializer {
    String getType();
    Codec<? extends BlockIngredient> codec();
}
