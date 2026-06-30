package net.astronomy.multilib.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.astronomy.multilib.compat.MultiblockRecipeDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.stream.Collectors;

/**
 * EMI recipe entry for a MultiLib multiblock structure.
 * {@link EmiRecipe} is an interface in EMI 1.1+; all methods are implemented here.
 */
public class MultiblockEmiRecipe implements EmiRecipe {

    static EmiRecipeCategory CATEGORY = null;

    static EmiRecipeCategory getOrCreateCategory() {
        if (CATEGORY == null) {
            String id = net.astronomy.multilib.client.ClientConfig.CATEGORY_ICON.get();
            net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(id);
            net.minecraft.world.item.Item item = loc != null
                    ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(loc).orElse(null)
                    : null;
            ItemStack iconStack = item != null ? new ItemStack(item) : new ItemStack(Items.STRUCTURE_BLOCK);
            CATEGORY = new EmiRecipeCategory(
                    ResourceLocation.fromNamespaceAndPath("multilib", "multiblock_structure"),
                    EmiStack.of(iconStack)
            );
        }
        return CATEGORY;
    }

    private final MultiblockRecipeDisplay data;

    public MultiblockEmiRecipe(MultiblockRecipeDisplay data) {
        this.data = data;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return getOrCreateCategory();
    }

    @Override
    public ResourceLocation getId() {
        return ResourceLocation.fromNamespaceAndPath("multilib", data.definition().getId().getPath());
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return data.inputs().stream()
                .map(EmiStack::of)
                .collect(Collectors.toList());
    }

    @Override
    public List<EmiStack> getOutputs() {
        return data.outputs().stream()
                .map(EmiStack::of)
                .collect(Collectors.toList());
    }

    @Override
    public int getDisplayWidth() {
        return 176;
    }

    @Override
    public int getDisplayHeight() {
        return 70;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        // TODO: verify API version compatibility
        widgets.addText(Component.literal(data.definition().getId().toString()), 5, 5, 0x404040, false);
    }
}
