package net.astronomy.multilib.compat.jade;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.hud.HudEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

/**
 * Client-side half of the Jade bridge: reads back the {@link HudEntry} list that
 * {@link MultiblockJadeServerProvider} wrote into Jade's server-data tag and renders each entry with
 * Jade's native tooltip elements - {@link HudEntry.Text} as a plain tooltip line,
 * {@link HudEntry.Progress} as Jade's own progress-bar element (via {@link IElementHelper}),
 * {@link HudEntry.KeyValue} as a "key: value" line.
 */
public final class MultiblockJadeComponentProvider implements IBlockComponentProvider {

    public static final MultiblockJadeComponentProvider INSTANCE = new MultiblockJadeComponentProvider();

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, "hud_tooltip");

    private MultiblockJadeComponentProvider() {}

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("entries", Tag.TAG_LIST)) return;

        HolderLookup.Provider registries = accessor.getLevel().registryAccess();
        IElementHelper helper = IElementHelper.get();
        ListTag list = data.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            HudEntry.load(list.getCompound(i), registries).ifPresent(entry -> render(tooltip, helper, entry));
        }
    }

    private void render(ITooltip tooltip, IElementHelper helper, HudEntry entry) {
        switch (entry) {
            case HudEntry.Text text -> tooltip.add(text.text());
            case HudEntry.Progress progress -> {
                IElement element = helper.progress(progress.fraction(), progress.label(),
                        helper.progressStyle(), BoxStyle.getNestedBox(), true);
                tooltip.add(element);
            }
            case HudEntry.KeyValue keyValue -> tooltip.add(Component.empty()
                    .append(keyValue.key())
                    .append(": ")
                    .append(keyValue.value()));
        }
    }
}
