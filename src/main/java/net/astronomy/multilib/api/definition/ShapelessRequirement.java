package net.astronomy.multilib.api.definition;

import net.astronomy.multilib.api.ingredient.BlockIngredient;

public record ShapelessRequirement(BlockIngredient ingredient, int min, int max) {}
