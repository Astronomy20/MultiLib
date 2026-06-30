package net.astronomy.multilib.compat.rei;

import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * REI display category for MultiLib multiblock structures.
 */
public class MultiblockCategory implements DisplayCategory<MultiblockDisplay> {

    static final CategoryIdentifier<MultiblockDisplay> ID =
            CategoryIdentifier.of("multilib", "multiblock_structure");

    @Override
    public CategoryIdentifier<MultiblockDisplay> getCategoryIdentifier() {
        return ID;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("rei.multilib.category.multiblock_structure");
    }

    @Override
    public Renderer getIcon() {
        String id = net.astronomy.multilib.client.ClientConfig.CATEGORY_ICON.get();
        net.minecraft.resources.ResourceLocation loc = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (loc != null) {
            net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(loc).orElse(null);
            if (item != null) return EntryStacks.of(new ItemStack(item));
        }
        return EntryStacks.of(new ItemStack(Items.STRUCTURE_BLOCK));
    }

    @Override
    public int getDisplayHeight() {
        return 70;
    }

    @Override
    public List<Widget> setupDisplay(MultiblockDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        String defId = display.getData().definition().getId().toString();
        // TODO: verify API version compatibility — Widgets.createLabel signature may differ
        widgets.add(Widgets.createLabel(
                new Point(bounds.x + 5, bounds.y + 5),
                Component.literal(defId)
        ).color(0xFF404040).noShadow());
        return widgets;
    }
}
