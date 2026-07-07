package net.astronomy.multilib.compat.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.EnumConfig;
import dev.ftb.mods.ftblibrary.config.NameMap;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.SimpleTextButton;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.AbstractButtonListScreen;
import net.minecraft.util.Mth;

/**
 * {@link EnumConfig} always opens FTB Library's searchable selection popup on click, instead of
 * {@code EnumConfig}'s own default: cycle to the next/previous value on left/right click, only falling
 * back to the popup once an enum has more than 16 entries (or Ctrl is held) - see
 * {@code EnumConfig#onClicked} upstream. MultiLib's multiblock/state pickers want the searchable popup
 * unconditionally, since a modpack may only have a handful of multiblocks registered.
 * <p>
 * {@code EnumConfig}'s own popup ({@code EnumSelectScreen}) is a private inner class, so this reimplements
 * the same behavior as a small screen of our own rather than reusing it.
 */
public class SearchableEnumConfig<E> extends EnumConfig<E> {
    public SearchableEnumConfig(NameMap<E> nameMap) {
        super(nameMap);
    }

    @Override
    public void onClicked(Widget clickedWidget, MouseButton button, ConfigCallback callback) {
        if (value == null || !getCanEdit()) return;
        SelectScreen screen = new SelectScreen(clickedWidget.getParent(), callback);
        screen.setHasSearchBox(true);
        screen.showBottomPanel(false);
        screen.showCloseButton(true);
        screen.openGui();
    }

    private class SelectScreen extends AbstractButtonListScreen {
        private final Panel parent;
        private final ConfigCallback callback;
        private int maxWidth = 176;

        SelectScreen(Panel parent, ConfigCallback callback) {
            this.parent = parent;
            this.callback = callback;
            for (E v : nameMap) {
                maxWidth = Math.max(maxWidth, getTheme().getStringWidth(nameMap.getDisplayName(v)));
            }
        }

        @Override
        public void addButtons(Panel panel) {
            for (E v : nameMap) {
                panel.add(new SimpleTextButton(panel, nameMap.getDisplayName(v), nameMap.getIcon(v)) {
                    @Override
                    public void onClicked(MouseButton button) {
                        playClickSound();
                        setCurrentValue(v);
                        doAccept();
                    }
                });
            }
        }

        @Override
        public boolean onInit() {
            setSize(
                    Mth.clamp(maxWidth + 35, 176, getWindow().getGuiScaledWidth() * 3 / 4),
                    Mth.clamp(nameMap.size() * 20 + 50, 166, getWindow().getGuiScaledHeight() * 4 / 5)
            );
            return super.onInit();
        }

        @Override
        protected void doCancel() {
            parent.run();
            callback.save(false);
        }

        @Override
        protected void doAccept() {
            parent.run();
            callback.save(true);
        }
    }
}
