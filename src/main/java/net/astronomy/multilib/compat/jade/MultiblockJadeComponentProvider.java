package net.astronomy.multilib.compat.jade;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.hud.HudEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.addon.core.ObjectNameProvider;
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

        // Formed multiblock: replace Jade's default object-name line (normally the looked-at block's
        // own name, e.g. "Gold Block") with the structure's own display name, so the tooltip reads
        // "Example Multiblock" rather than naming whichever block happens to be standing at this
        // position. ObjectNameProvider.getBlock() runs at a much lower priority than this provider
        // (-10100 vs. the default 0), so its line has already been added by the time this runs, and
        // Jade's replace(uid, Component) overwrites that specific line rather than appending a new one.
        String overrideNameText = null;
        if (data.contains("definitionId", Tag.TAG_STRING)) {
            ResourceLocation definitionId = ResourceLocation.tryParse(data.getString("definitionId"));
            if (definitionId != null) {
                Component name = Component.translatable(
                        "multiblock." + definitionId.getNamespace() + "." + definitionId.getPath());
                tooltip.replace(ObjectNameProvider.getBlock().getUid(), name);
                overrideNameText = name.getString();
            }
        }

        if (!data.contains("entries", Tag.TAG_LIST)) return;

        HolderLookup.Provider registries = accessor.getLevel().registryAccess();
        IElementHelper helper = IElementHelper.get();
        ListTag list = data.getList("entries", Tag.TAG_COMPOUND);
        String skipText = overrideNameText;
        for (int i = 0; i < list.size(); i++) {
            HudEntry.load(list.getCompound(i), registries).ifPresent(entry -> render(tooltip, helper, entry, skipText));
        }
    }

    /**
     * @param skipIfText when non-null, a plain-text {@link HudEntry.Text} whose resolved string equals
     *                    this is dropped instead of rendered - dedupes a body line (typically
     *                    {@code FormedStatusProvider}'s own definition-name line, viewer-agnostic and
     *                    still needed as-is for probes without a title-override hook, e.g. TOP) against
     *                    the name already placed in Jade's title line above. Any provider's line can
     *                    trigger this, not just the built-in one - the check is by resolved content, not
     *                    by which provider produced it.
     */
    private void render(ITooltip tooltip, IElementHelper helper, HudEntry entry, String skipIfText) {
        switch (entry) {
            case HudEntry.Text text -> {
                if (skipIfText != null && skipIfText.equals(text.text().getString())) return;
                tooltip.add(text.text());
            }
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
