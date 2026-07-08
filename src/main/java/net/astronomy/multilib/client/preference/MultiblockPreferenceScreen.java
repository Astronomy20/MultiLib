package net.astronomy.multilib.client.preference;

import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.compat.MultiblockPreviewPanel;
import net.astronomy.multilib.network.RequestSetPreferredDefinitionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Picker opened by {@link MultiblockPreferenceInputHandler} when the preference wrench is used on an
 * ambiguous core/activation block: one button per candidate definition (reusing
 * {@link MultiblockPreviewPanel#multiblockName} for the same display name convention every recipe
 * viewer already uses), plus a Cancel button. Picking one sends
 * {@link RequestSetPreferredDefinitionPacket} and closes immediately - there's no long-running
 * operation to wait on, so the screen doesn't need to stay open for a response; {@link
 * net.astronomy.multilib.client.network.ClientPacketHandler#handlePreferredDefinitionResult} shows the
 * outcome as a standalone chat line instead of updating this (likely already closed) screen.
 */
public class MultiblockPreferenceScreen extends Screen {

    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 4;

    private final BlockPos pos;
    private final List<MultiblockDefinition> candidates;

    public MultiblockPreferenceScreen(BlockPos pos, List<MultiblockDefinition> candidates) {
        super(Component.translatable("multilib.preference.title"));
        this.pos = pos;
        this.candidates = candidates;
    }

    @Override
    protected void init() {
        int totalHeight = candidates.size() * (BUTTON_HEIGHT + BUTTON_SPACING) + BUTTON_HEIGHT;
        int top = (this.height - totalHeight) / 2;
        int left = (this.width - BUTTON_WIDTH) / 2;

        int y = top;
        for (MultiblockDefinition def : candidates) {
            Component label = Component.literal(MultiblockPreviewPanel.multiblockName(def));
            this.addRenderableWidget(Button.builder(label, b -> onPick(def))
                    .bounds(left, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            y += BUTTON_HEIGHT + BUTTON_SPACING;
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(left, y + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void onPick(MultiblockDefinition def) {
        PacketDistributor.sendToServer(new RequestSetPreferredDefinitionPacket(pos, def.getId()));
        onClose();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - (candidates.size() + 1) * (BUTTON_HEIGHT + BUTTON_SPACING) / 2 - 16, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
