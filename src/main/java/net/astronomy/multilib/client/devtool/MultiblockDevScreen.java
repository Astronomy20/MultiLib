package net.astronomy.multilib.client.devtool;

import net.astronomy.multilib.core.devtool.MultiblockDevExportLoader.LoadableMultiblock;
import net.astronomy.multilib.core.devtool.MultiblockDevMenu;
import net.astronomy.multilib.core.devtool.MultiblockScanResult;
import net.astronomy.multilib.network.RequestDevExportPacket;
import net.astronomy.multilib.network.RequestDevLoadListPacket;
import net.astronomy.multilib.network.RequestDevLoadPacket;
import net.astronomy.multilib.network.RequestDevSaveFieldsPacket;
import net.astronomy.multilib.network.RequestDevScanPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GUI for the Multiblock Dev Block: path/display-name fields, offset/size fields, Detect/Render
 * buttons, a read-only scan summary (with a scrollable block-type list), the resolved JSON export path,
 * and the three export buttons. No inventory slots - see {@link MultiblockDevMenu}. Layout follows
 * roadmap Design 5 (not pixel-exact, but the same field/button set and order), reworked for readable
 * labels and to fit its own content (the original size clipped the KubeJS export button and let the
 * vanilla title overlap the path field).
 */
public class MultiblockDevScreen extends AbstractContainerScreen<MultiblockDevMenu> {

    private static final int MARGIN = 10;
    private static final int FIELD_HEIGHT = 16;
    private static final int LABEL_GAP = 10; // vertical space reserved for a label above its field
    private static final int ROW_GAP = 6;
    private static final int WIDE_FIELD_WIDTH = 137;
    private static final int SMALL_FIELD_WIDTH = 86;
    private static final int SMALL_FIELD_GAP = 7;

    /** Viewport height (px) of the scrollable scanned-block-type list - see {@link #renderBlockList}. */
    private static final int LIST_VIEWPORT_HEIGHT = 44;

    /** Viewport height (px) of the Load tab's scrollable list of existing exports - see {@link #renderLoadTab}. */
    private static final int LOAD_LIST_VIEWPORT_HEIGHT = 240;

    private enum Tab { CREATE, LOAD }

    private Tab currentTab = Tab.CREATE;
    private Button createTabButton, loadTabButton;

    /** Lines scrolled down in the Load tab's list - see {@link #renderLoadTab}. */
    private int loadListScroll;

    /** Screen-space hit box of the Load tab's list viewport, for clicking/scrolling; null while the Create tab is shown. */
    private int[] loadListViewport;

    /** Last {@link MultiblockDevMenu#getLoadVersion()} this screen has already applied to its EditBoxes - see {@link #render}. */
    private int lastSeenLoadVersion;

    /** Last {@link MultiblockDevMenu#getExportResultVersion()} this screen has already reacted to - see {@link #render}. */
    private int lastSeenExportVersion;

    /**
     * The export id's namespace half - always {@link net.astronomy.multilib.CommonConfig#DEVTOOL_NAMESPACE},
     * a single fixed value shared by every multiblock (matches standard Minecraft ResourceLocation
     * convention: namespace is the constant, mod-like identifier, path is what varies per object), read
     * once for display next to the path field (see {@link #renderFieldLabels}) so the developer can see
     * the full {@code namespace:path} id and translation key this multiblock will actually resolve to.
     * Never sent back to the server or stored per-block-entity - there used to be a client-side
     * {@code currentPath} field here that tried to track a *derived*, per-multiblock path (slugified from
     * the Display Name) under a "Namespace" label, which the server-side export logic never actually
     * consulted (it always used a config-fixed value instead, just under the wrong name) - that mismatch
     * between what the GUI silently computed and what actually got used, and the swapped labeling, was
     * exactly the namespace/path confusion this field (and the renamed "Path" field below) replaces.
     */
    private final String configuredNamespace = net.astronomy.multilib.CommonConfig.DEVTOOL_NAMESPACE.get();

    /** Y position of the read-only id/translation-key preview line drawn under the path field - see {@link #renderFieldLabels}. */
    private int idPreviewY;

    private EditBox pathBox;
    private EditBox nameBox;
    private EditBox offsetXBox, offsetYBox, offsetZBox;
    private EditBox sizeXBox, sizeYBox, sizeZBox;

    /** Label reflects auto-detect on/off - see {@link #onToggleAutoDetect()}. */
    private Button detectButton;
    /** Label reflects whether this dev-block's HUD list is currently the active one for this player. */
    private Button listButton;
    /** Label reflects whether the area preview ({@link #previewOn}) is currently on. */
    private Button renderButton;

    private Button exportJavaButton, exportJsonButton, exportKubeJsButton;

    /** Every widget only shown/active on the Create tab - hidden (not just disabled) while the Load tab is up, see {@link #updateTabVisibility()}. */
    private final List<net.minecraft.client.gui.components.AbstractWidget> createTabWidgets = new ArrayList<>();

    /** Whether {@link #init()} has already run once - see {@link #captureCurrentValues()}. */
    private boolean everInitialized = false;

    /** Whether the "Render" area preview is currently toggled on - see {@link #onRender()}/{@link #render}. */
    private boolean previewOn;

    /** Lines scrolled down in the scanned-block-type list - see {@link #renderBlockList}. */
    private int blockListScroll;

    /** Screen-space hit box of the block list viewport, for {@link #mouseScrolled}; null if nothing to scroll. */
    private int[] blockListViewport;

    private final List<LabelEntry> labels = new ArrayList<>();
    private int summaryTop;
    private int headerY;

    private record LabelEntry(int x, int y, Component text) {}

    public MultiblockDevScreen(MultiblockDevMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = MARGIN * 2 + WIDE_FIELD_WIDTH * 2 + ROW_GAP;
        // Tightened from the previous 380 (a leftover from when the panel had an extra namespace/path
        // row and an unbounded scan summary) to match what's actually laid out below, so the panel no
        // longer leaves a stretch of empty space under the export buttons. +LABEL_GAP for the read-only
        // id/translation-key preview line under the path field (see idPreviewY).
        this.imageHeight = 328 + LABEL_GAP;
        // This menu has no player inventory slots and no vanilla-style title bar at (8, 6) - both
        // default label positions would otherwise land right on top of the path field (that's
        // exactly what happened before this rework: the block's translated name rendered, nearly
        // invisible, underneath the path box). Push both off-screen and draw our own heading
        // in render() instead, positioned with proper spacing above the fields.
        this.inventoryLabelY = this.imageHeight + 4000;
        this.titleLabelY = this.imageHeight + 4000;
        // Reflects whatever the preview was left at, specifically for *this* dev-block - the toggle
        // state itself is persisted server-side (MultiblockDevBlockEntity#isRenderOn) and mirrored into
        // ClientMultiblockDevAreaPreviewState (which is only ever showing one box at a time, tagged with
        // its owner), this just checks whether that's the box currently shown so render() knows whether
        // to keep refreshing it live from the GUI's own fields.
        ClientMultiblockDevAreaPreviewState.Box currentBox = ClientMultiblockDevAreaPreviewState.get();
        this.previewOn = currentBox != null && currentBox.ownerPos().equals(menu.getDevBlockPos());
    }

    @Override
    protected void init() {
        super.init();
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        labels.clear();
        createTabWidgets.clear();

        // Capture whatever's currently typed before the old widgets get thrown away (this method runs
        // again on every window resize) - restored below instead of re-reading the block entity, so
        // in-progress edits the developer hasn't hit Detect on yet survive a resize instead of being
        // silently discarded.
        String[] previous = everInitialized ? captureCurrentValues() : null;

        int y = top + MARGIN;
        headerY = y;
        y += this.font.lineHeight + ROW_GAP + 2;

        // Tab bar, top-right: small enough to sit beside the header title without overlapping it.
        int tabWidth = 46;
        int tabRight = left + this.imageWidth - MARGIN;
        this.createTabButton = this.addRenderableWidget(Button.builder(Component.literal("Create"), b -> switchTab(Tab.CREATE))
                .bounds(tabRight - tabWidth * 2 - 4, top + MARGIN - 2, tabWidth, 14).build());
        this.loadTabButton = this.addRenderableWidget(Button.builder(Component.literal("Load"), b -> switchTab(Tab.LOAD))
                .bounds(tabRight - tabWidth, top + MARGIN - 2, tabWidth, 14).build());

        int fieldX = left + MARGIN;
        addFieldLabel(fieldX, y, "path");
        y += LABEL_GAP;
        this.pathBox = addField(fieldX, y, WIDE_FIELD_WIDTH * 2 + ROW_GAP);
        createTabWidgets.add(pathBox);
        y += FIELD_HEIGHT + ROW_GAP;
        // Read-only, one line: the id/translation key this multiblock actually resolves to
        // (namespace:path / multiblock.namespace.path) - namespace is always configuredNamespace, fixed,
        // never a GUI field of its own (see configuredNamespace's own javadoc).
        this.idPreviewY = y;
        y += LABEL_GAP;

        addFieldLabel(fieldX, y, "displayName");
        y += LABEL_GAP;
        this.nameBox = addField(fieldX, y, WIDE_FIELD_WIDTH * 2 + ROW_GAP);
        createTabWidgets.add(nameBox);
        y += FIELD_HEIGHT + ROW_GAP + 2;

        addFieldLabel(fieldX, y, "offsetX");
        addFieldLabel(fieldX + SMALL_FIELD_WIDTH + SMALL_FIELD_GAP, y, "offsetY");
        addFieldLabel(fieldX + (SMALL_FIELD_WIDTH + SMALL_FIELD_GAP) * 2, y, "offsetZ");
        y += LABEL_GAP;
        this.offsetXBox = addNumberField(fieldX, y, 0);
        this.offsetYBox = addNumberField(fieldX + SMALL_FIELD_WIDTH + SMALL_FIELD_GAP, y, 1);
        this.offsetZBox = addNumberField(fieldX + (SMALL_FIELD_WIDTH + SMALL_FIELD_GAP) * 2, y, 0);
        createTabWidgets.add(offsetXBox);
        createTabWidgets.add(offsetYBox);
        createTabWidgets.add(offsetZBox);
        y += FIELD_HEIGHT + ROW_GAP;

        addFieldLabel(fieldX, y, "sizeX");
        addFieldLabel(fieldX + SMALL_FIELD_WIDTH + SMALL_FIELD_GAP, y, "sizeY");
        addFieldLabel(fieldX + (SMALL_FIELD_WIDTH + SMALL_FIELD_GAP) * 2, y, "sizeZ");
        y += LABEL_GAP;
        this.sizeXBox = addNumberField(fieldX, y, 1);
        this.sizeYBox = addNumberField(fieldX + SMALL_FIELD_WIDTH + SMALL_FIELD_GAP, y, 1);
        this.sizeZBox = addNumberField(fieldX + (SMALL_FIELD_WIDTH + SMALL_FIELD_GAP) * 2, y, 1);
        createTabWidgets.add(sizeXBox);
        createTabWidgets.add(sizeYBox);
        createTabWidgets.add(sizeZBox);
        y += FIELD_HEIGHT + ROW_GAP + 4;

        if (previous != null) {
            restoreValues(previous);
        } else {
            loadFieldsFromMenu();
        }
        everInitialized = true;

        // Three buttons instead of two: Detect (now an on/off auto-detect toggle, not a one-shot action),
        // Show/Hide List (the scoreboard-style HUD, in the middle per how the feature was asked for), and
        // Render. Both Detect and the list button change their own label to reflect current state, so
        // they're kept as fields to update from render() every frame.
        int actionButtonWidth = (WIDE_FIELD_WIDTH * 2 + ROW_GAP - ROW_GAP * 2) / 3;
        this.detectButton = this.addRenderableWidget(Button.builder(Component.literal("Detect"), b -> onToggleAutoDetect())
                .bounds(fieldX, y, actionButtonWidth, 20).build());
        this.listButton = this.addRenderableWidget(Button.builder(Component.literal("Show List"), b -> onToggleList())
                .bounds(fieldX + actionButtonWidth + ROW_GAP, y, actionButtonWidth, 20).build());
        this.renderButton = this.addRenderableWidget(Button.builder(Component.literal("Render"), b -> onRender())
                .bounds(fieldX + (actionButtonWidth + ROW_GAP) * 2, y, actionButtonWidth, 20).build());
        createTabWidgets.add(detectButton);
        createTabWidgets.add(listButton);
        createTabWidgets.add(renderButton);
        y += 20 + ROW_GAP + 4;

        // Scan summary + resolved path render as plain text in render() below this point (no separate
        // widgets needed for read-only text). Fixed-height reservation (pinned tag line + a fixed-height
        // scrollable block-type list + export status) so the panel size never depends on scan content.
        this.summaryTop = y;
        y += 100;

        int exportButtonWidth = (WIDE_FIELD_WIDTH * 2 + ROW_GAP - ROW_GAP * 2) / 3;
        this.exportJavaButton = this.addRenderableWidget(Button.builder(Component.translatable("multilib.devtool.export_java"),
                b -> onExport(RequestDevExportPacket.Format.JAVA)).bounds(fieldX, y, exportButtonWidth, 20).build());
        this.exportJsonButton = this.addRenderableWidget(Button.builder(Component.translatable("multilib.devtool.export_json"),
                b -> onExport(RequestDevExportPacket.Format.JSON))
                .bounds(fieldX + exportButtonWidth + ROW_GAP, y, exportButtonWidth, 20).build());
        this.exportKubeJsButton = this.addRenderableWidget(Button.builder(Component.translatable("multilib.devtool.export_kubejs"),
                b -> onExport(RequestDevExportPacket.Format.KUBEJS))
                .bounds(fieldX + (exportButtonWidth + ROW_GAP) * 2, y, exportButtonWidth, 20).build());
        createTabWidgets.add(exportJavaButton);
        createTabWidgets.add(exportJsonButton);
        createTabWidgets.add(exportKubeJsButton);

        // The server already sends an initial DevScanResultPacket when this menu is opened (see
        // MultiblockDevBlock#openMenu / the menu-open handling), so the last stored scan (if any) shows
        // up without the client needing to ask again here - no extra request needed on init().

        updateTabVisibility();
    }

    /** Switches the visible tab, requesting a fresh Load list from the server whenever the Load tab is opened. */
    private void switchTab(Tab tab) {
        this.currentTab = tab;
        updateTabVisibility();
        if (tab == Tab.LOAD) {
            PacketDistributor.sendToServer(new RequestDevLoadListPacket(menu.getDevBlockPos()));
        }
    }

    /** Hides every Create-tab widget while the Load tab is shown (and vice versa) - the Load tab's own list is drawn manually, not as widgets. */
    private void updateTabVisibility() {
        boolean createVisible = currentTab == Tab.CREATE;
        for (var widget : createTabWidgets) {
            widget.visible = createVisible;
            widget.active = createVisible;
        }
    }

    private void addFieldLabel(int x, int y, String hint) {
        labels.add(new LabelEntry(x, y, Component.translatable("multilib.devtool." + hint)));
    }

    private EditBox addField(int x, int y, int width) {
        EditBox box = new EditBox(this.font, x, y, width, FIELD_HEIGHT, Component.empty());
        box.setMaxLength(128);
        this.addRenderableWidget(box);
        // Same reasoning as addNumberField's own responder: without this, path/displayName only
        // reached the server on GUI close or the Detect toggle, so typing a path and immediately
        // clicking Export used whatever the server had last seen (often still empty) and failed with
        // "Path is required" until the GUI was closed and reopened.
        box.setResponder(s -> saveFieldsToServer());
        return box;
    }

    private EditBox addNumberField(int x, int y, int defaultValue) {
        EditBox box = new EditBox(this.font, x, y, SMALL_FIELD_WIDTH, FIELD_HEIGHT, Component.empty());
        box.setMaxLength(8);
        box.setFilter(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        box.setValue(String.valueOf(defaultValue));
        this.addRenderableWidget(box);
        // Offset/size are the only fields the actual server-side scan area depends on, and they were
        // previously only ever pushed to the server on GUI close or on the Detect toggle click - so
        // editing them while auto-detect was already on and the GUI stayed open just kept the periodic
        // re-scan running against whatever size/offset the server had last seen, no matter what was
        // currently typed (the "Render" preview looked live because it's computed entirely client-side -
        // the actual scan never was). Pushing on every keystroke instead keeps the server in step with
        // whatever's currently typed, matching what auto-detect already looks like it's doing.
        box.setResponder(s -> saveFieldsToServer());
        return box;
    }

    /** Order matches {@link #restoreValues}. */
    private String[] captureCurrentValues() {
        return new String[] {
                pathBox.getValue(), nameBox.getValue(),
                offsetXBox.getValue(), offsetYBox.getValue(), offsetZBox.getValue(),
                sizeXBox.getValue(), sizeYBox.getValue(), sizeZBox.getValue()
        };
    }

    private void restoreValues(String[] values) {
        pathBox.setValue(values[0]);
        nameBox.setValue(values[1]);
        offsetXBox.setValue(values[2]);
        offsetYBox.setValue(values[3]);
        offsetZBox.setValue(values[4]);
        sizeXBox.setValue(values[5]);
        sizeYBox.setValue(values[6]);
        sizeZBox.setValue(values[7]);
    }

    /**
     * Pre-fills the fields from the client-side {@code MultiblockDevBlockEntity} at this menu's
     * position, if the level already has it loaded and synced (getUpdateTag/handleUpdateTag already
     * ran on chunk load, well before the player interacts to open this menu) - so reopening the GUI
     * resumes exactly where the developer left off, same as vanilla's Structure Block. Only used on the
     * very first {@link #init()} call; a later one (window resize) restores in-progress typing instead
     * (see {@link #captureCurrentValues()}), so it never gets clobbered back to the last-saved state.
     */
    private void loadFieldsFromMenu() {
        if (this.minecraft == null || this.minecraft.level == null) return;
        if (!(this.minecraft.level.getBlockEntity(menu.getDevBlockPos())
                instanceof net.astronomy.multilib.core.devtool.MultiblockDevBlockEntity be)) {
            return;
        }
        this.pathBox.setValue(be.getPath());
        this.nameBox.setValue(be.getDisplayName());
        this.offsetXBox.setValue(String.valueOf(be.getOffset().getX()));
        this.offsetYBox.setValue(String.valueOf(be.getOffset().getY()));
        this.offsetZBox.setValue(String.valueOf(be.getOffset().getZ()));
        this.sizeXBox.setValue(String.valueOf(be.getSize().getX()));
        this.sizeYBox.setValue(String.valueOf(be.getSize().getY()));
        this.sizeZBox.setValue(String.valueOf(be.getSize().getZ()));
    }

    /**
     * "Detect" is now an on/off auto-detect toggle instead of a one-shot scan (see
     * {@code MultiblockTickHandler} for the periodic re-scan this enables) - pushes whatever's currently
     * typed first, same as the old one-shot Detect did, so toggling doesn't scan against stale
     * offset/size/path values the server hasn't seen yet.
     */
    private void onToggleAutoDetect() {
        saveFieldsToServer();
        PacketDistributor.sendToServer(new net.astronomy.multilib.network.RequestDevAutoDetectTogglePacket(menu.getDevBlockPos()));
    }

    /** Shows/hides this dev-block's scoreboard-style HUD list - see {@link MultiblockDevListHudRenderer}. */
    private void onToggleList() {
        PacketDistributor.sendToServer(new net.astronomy.multilib.network.RequestDevListToggleVisibilityPacket(menu.getDevBlockPos()));
    }

    /**
     * Toggles the area preview on/off. While on, {@link #render} recomputes and re-sets the box every
     * frame from whatever's currently in the offset/size fields, so it updates live the instant those
     * change - no need to click Render again after adjusting a value. Also persists the toggle server-side
     * ({@code MultiblockDevBlockEntity#isRenderOn}), so the preview comes back on its own after closing
     * the GUI, relogging, or a world restart - not just for as long as this Screen instance exists.
     */
    private void onRender() {
        previewOn = !previewOn;
        if (previewOn) {
            ClientMultiblockDevAreaPreviewState.set(currentPreviewBox());
        } else {
            ClientMultiblockDevAreaPreviewState.clear();
        }
        PacketDistributor.sendToServer(new net.astronomy.multilib.network.RequestDevRenderTogglePacket(menu.getDevBlockPos()));
    }

    private ClientMultiblockDevAreaPreviewState.Box currentPreviewBox() {
        int offsetX = parseIntOrDefault(offsetXBox.getValue(), 0);
        int offsetY = parseIntOrDefault(offsetYBox.getValue(), 0);
        int offsetZ = parseIntOrDefault(offsetZBox.getValue(), 0);
        int sizeX = Math.max(parseIntOrDefault(sizeXBox.getValue(), 1), 1);
        int sizeY = Math.max(parseIntOrDefault(sizeYBox.getValue(), 1), 1);
        int sizeZ = Math.max(parseIntOrDefault(sizeZBox.getValue(), 1), 1);

        BlockPos min = menu.getDevBlockPos().offset(offsetX, offsetY, offsetZ);
        BlockPos max = min.offset(sizeX - 1, sizeY - 1, sizeZ - 1);
        return new ClientMultiblockDevAreaPreviewState.Box(min, max, menu.getDevBlockPos());
    }

    /**
     * {@code AbstractContainerScreen} closes the screen whenever the "open inventory" key (E by
     * default) is pressed, checked only *after* delegating to the focused widget - but a plain letter
     * key like E never gets consumed by {@code EditBox.keyPressed} (character input goes through the
     * separate {@code charTyped} callback instead), so that fallthrough fired on every "e" typed into
     * any of these fields and closed the GUI mid-edit. While a field has focus, handle Escape ourselves
     * and hand everything else straight to it, never falling through to that check.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Tab must always reach Screen's own focus-cycling logic (changeFocus), even while a field is
        // focused - the blanket "hand everything to the focused field" rule below would otherwise swallow
        // it (EditBox doesn't do anything with Tab itself), silently breaking the vanilla tab-between-
        // widgets behavior every other screen gets for free.
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (isAnyFieldFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.onClose();
                return true;
            }
            return this.getFocused() != null && this.getFocused().keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Right-clicking an offset/size field resets it to the value {@link #addNumberField} declared as
     * that field's default, instead of requiring it to be cleared and retyped by hand.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (currentTab == Tab.LOAD) {
            return handleLoadListClick(mouseX, mouseY) || super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 1) {
            if (resetIfClicked(offsetXBox, 0, mouseX, mouseY)) return true;
            if (resetIfClicked(offsetYBox, 1, mouseX, mouseY)) return true;
            if (resetIfClicked(offsetZBox, 0, mouseX, mouseY)) return true;
            if (resetIfClicked(sizeXBox, 1, mouseX, mouseY)) return true;
            if (resetIfClicked(sizeYBox, 1, mouseX, mouseY)) return true;
            if (resetIfClicked(sizeZBox, 1, mouseX, mouseY)) return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // A left-click that isn't on any of the 8 fields didn't hit anything that would claim focus for
        // itself (a button doesn't stay "focused" the way a text field does) - so whichever field was
        // previously focused otherwise just kept eating keystrokes forever, since nothing in Screen's own
        // click handling clears a widget's own internal focused flag on a miss.
        if (button == 0 && isAnyFieldFocused() && !isMouseOverAnyField(mouseX, mouseY)) {
            clearFieldFocus();
        }
        return handled;
    }

    private boolean isMouseOverAnyField(double mouseX, double mouseY) {
        return pathBox.isMouseOver(mouseX, mouseY) || nameBox.isMouseOver(mouseX, mouseY)
                || offsetXBox.isMouseOver(mouseX, mouseY) || offsetYBox.isMouseOver(mouseX, mouseY)
                || offsetZBox.isMouseOver(mouseX, mouseY)
                || sizeXBox.isMouseOver(mouseX, mouseY) || sizeYBox.isMouseOver(mouseX, mouseY)
                || sizeZBox.isMouseOver(mouseX, mouseY);
    }

    private void clearFieldFocus() {
        pathBox.setFocused(false);
        nameBox.setFocused(false);
        offsetXBox.setFocused(false);
        offsetYBox.setFocused(false);
        offsetZBox.setFocused(false);
        sizeXBox.setFocused(false);
        sizeYBox.setFocused(false);
        sizeZBox.setFocused(false);
        this.setFocused(null);
    }

    private boolean resetIfClicked(EditBox box, int defaultValue, double mouseX, double mouseY) {
        if (!box.isMouseOver(mouseX, mouseY)) return false;
        box.setValue(String.valueOf(defaultValue));
        return true;
    }

    private boolean isAnyFieldFocused() {
        return pathBox.isFocused() || nameBox.isFocused()
                || offsetXBox.isFocused() || offsetYBox.isFocused() || offsetZBox.isFocused()
                || sizeXBox.isFocused() || sizeYBox.isFocused() || sizeZBox.isFocused();
    }

    @Override
    public void onClose() {
        // Deliberately NOT clearing ClientMultiblockDevAreaPreviewState here: the area preview is a
        // toggle ("Render" on/off), and it should stay exactly as the developer left it across closing
        // and reopening the GUI, same as every other field on this screen - not force itself off just
        // because the GUI closed.
        saveFieldsToServer();
        super.onClose();
    }

    /**
     * Persists whatever's currently typed to the block entity even if Detect was never clicked -
     * otherwise closing the GUI mid-edit silently discarded it, since nothing had told the server about
     * those fields yet. Deliberately the lightweight {@link RequestDevSaveFieldsPacket} (setters only),
     * not {@link RequestDevScanPacket} (which would also rescan and clear the current tag on every close).
     */
    private void saveFieldsToServer() {
        BlockPos offset = new BlockPos(
                parseIntOrDefault(offsetXBox.getValue(), 0),
                parseIntOrDefault(offsetYBox.getValue(), 0),
                parseIntOrDefault(offsetZBox.getValue(), 0));
        int sizeX = Math.max(parseIntOrDefault(sizeXBox.getValue(), 1), 1);
        int sizeY = Math.max(parseIntOrDefault(sizeYBox.getValue(), 1), 1);
        int sizeZ = Math.max(parseIntOrDefault(sizeZBox.getValue(), 1), 1);
        PacketDistributor.sendToServer(new RequestDevSaveFieldsPacket(
                menu.getDevBlockPos(), offset, sizeX, sizeY, sizeZ,
                pathBox.getValue(), nameBox.getValue()));
    }

    private static int parseIntOrDefault(String s, int fallback) {
        try {
            return s.isEmpty() || s.equals("-") ? fallback : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void onExport(RequestDevExportPacket.Format format) {
        PacketDistributor.sendToServer(new RequestDevExportPacket(menu.getDevBlockPos(), format, false));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, 0xC0101010);
        guiGraphics.renderOutline(left, top, imageWidth, imageHeight, 0xFF505050);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        applyPendingLoadIfAny();
        applyPendingExportConfirmationIfAny();
        if (previewOn) {
            // Recomputed every frame from the live field values (not just on the Render click) so the
            // in-world box updates immediately as offset/size are edited.
            ClientMultiblockDevAreaPreviewState.set(currentPreviewBox());
        }
        updateToggleButtonLabels();
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderHeader(guiGraphics);
        if (currentTab == Tab.CREATE) {
            renderFieldLabels(guiGraphics);
            renderSummary(guiGraphics, mouseX, mouseY);
        } else {
            renderLoadTab(guiGraphics, mouseX, mouseY);
        }
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /**
     * Notices a Load that just completed (see {@link MultiblockDevMenu#getLoadVersion()}) and copies its
     * path/display-name into the Create tab's EditBoxes, switching back to that tab so the developer
     * immediately sees what was loaded - the scan summary itself already reflects it since
     * {@link MultiblockDevMenu#applyLoadResult} feeds the same {@code lastScan} field the regular Detect
     * flow does.
     */
    private void applyPendingLoadIfAny() {
        int version = menu.getLoadVersion();
        if (version == lastSeenLoadVersion) return;
        lastSeenLoadVersion = version;

        // Responder briefly cleared so this programmatic update doesn't immediately bounce the loaded
        // path/displayName back to the server through saveFieldsToServer() - the server already has
        // them (that's exactly what this Load just set via MultiblockDevBlockEntity#loadExisting).
        pathBox.setResponder(null);
        nameBox.setResponder(null);
        pathBox.setValue(menu.getLoadedPath());
        nameBox.setValue(menu.getLoadedDisplayName());
        pathBox.setResponder(s -> saveFieldsToServer());
        nameBox.setResponder(s -> saveFieldsToServer());

        // Mirrors the same width/layer-count/depth computation MultiblockDevBlockEntity#placePatternInWorld
        // just applied server-side - without this, the sizeX/Y/Z boxes kept showing whatever was typed
        // before the Load, and the "Render" preview box (computed client-side straight from these boxes,
        // see currentPreviewBox()) would keep showing the OLD area instead of matching the loaded pattern,
        // even though the dev-block's own synced size was already correct.
        //
        // Responders cleared here too, for a sharper reason than path/nameBox above: each of the
        // three setValue calls below would otherwise fire saveFieldsToServer() separately, sending the
        // *currently-typed* (not-yet-fully-updated) X/Y/Z combination to the server on every single one -
        // e.g. the new X alongside the still-old Y/Z. If auto-detect happens to be on,
        // MultiblockDevPacketHandler#handleSaveFieldsRequest reacts to that intermediate, inconsistent
        // size by immediately re-Detecting against a bounding box that doesn't actually match where the
        // pattern was just placed - which fails to find the block tagFromScan() just tagged and wipes the
        // tag (see resolveTagAgainstScan). Setting all three silently, then saving once with the fully
        // consistent final size, never exposes that intermediate state to the server at all.
        MultiblockScanResult loadedScan = menu.getLastScan();
        if (loadedScan != null && !loadedScan.layers().isEmpty()) {
            int layerCount = loadedScan.layers().size();
            int depth = 0;
            int width = 0;
            for (List<String> layer : loadedScan.layers()) {
                depth = Math.max(depth, layer.size());
                for (String row : layer) width = Math.max(width, row.length());
            }
            sizeXBox.setResponder(null);
            sizeYBox.setResponder(null);
            sizeZBox.setResponder(null);
            sizeXBox.setValue(String.valueOf(Math.max(width, 1)));
            sizeYBox.setValue(String.valueOf(Math.max(layerCount, 1)));
            sizeZBox.setValue(String.valueOf(Math.max(depth, 1)));
            sizeXBox.setResponder(s -> saveFieldsToServer());
            sizeYBox.setResponder(s -> saveFieldsToServer());
            sizeZBox.setResponder(s -> saveFieldsToServer());
            saveFieldsToServer();
        }

        // The server turns "Render" on as part of every Load (see MultiblockDevPacketHandler#handleLoadRequest),
        // but this Screen's own previewOn only ever changed from clicking the button itself - left false
        // here, the button kept showing "Render: OFF" right after a Load that had actually turned it on
        // server-side, and clicking it to (apparently) turn it on actually toggled it back OFF instead
        // (see onRender()/RequestDevRenderTogglePacket), clearing both the area-preview box and the
        // core/activation glow the Load had just set up.
        previewOn = true;
        ClientMultiblockDevAreaPreviewState.set(currentPreviewBox());

        switchTab(Tab.CREATE);
    }

    /**
     * Notices an export that came back needing confirmation (see
     * {@link net.astronomy.multilib.network.DevExportResultPacket#requiresConfirmation()}) and opens a
     * vanilla {@link ConfirmScreen} asking whether to overwrite the conflicting path anyway. Confirming
     * re-sends {@link RequestDevExportPacket} for the same format with {@code force=true}; declining just
     * returns to this screen with nothing written.
     */
    private void applyPendingExportConfirmationIfAny() {
        int version = menu.getExportResultVersion();
        if (version == lastSeenExportVersion) return;
        lastSeenExportVersion = version;

        if (!menu.isLastExportRequiresConfirmation() || menu.getLastExportFormat() == null) return;
        RequestDevExportPacket.Format format = menu.getLastExportFormat();
        String message = menu.getLastExportError();

        this.minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        PacketDistributor.sendToServer(new RequestDevExportPacket(menu.getDevBlockPos(), format, true));
                    }
                    this.minecraft.setScreen(this);
                },
                Component.literal("Path already in use"),
                Component.literal(message)));
    }

    /**
     * Reflects live state on all three toggle buttons every frame, rather than only at the moment they're
     * clicked - {@code autoDetectOn} (synced from the block entity, since another client/session could
     * have toggled it) and which dev-block currently owns the HUD list (this player's own choice, but the
     * server can also revoke it, e.g. if the block was broken) can both change from outside this button's
     * own click handler; Render's own {@link #previewOn} is purely local state but gets the same
     * "ON"/"OFF" treatment for consistency with the other two.
     */
    private void updateToggleButtonLabels() {
        boolean autoDetectOn = this.minecraft != null && this.minecraft.level != null
                && this.minecraft.level.getBlockEntity(menu.getDevBlockPos()) instanceof net.astronomy.multilib.core.devtool.MultiblockDevBlockEntity be
                && be.isAutoDetectOn();
        detectButton.setMessage(Component.literal(autoDetectOn ? "Detect: ON" : "Detect: OFF"));

        boolean listShown = menu.getDevBlockPos().equals(ClientMultiblockDevListHudState.getActivePos());
        listButton.setMessage(Component.literal(listShown ? "Hide List" : "Show List"));

        renderButton.setMessage(Component.literal(previewOn ? "Render: ON" : "Render: OFF"));
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        int left = (this.width - this.imageWidth) / 2;
        guiGraphics.drawString(this.font, this.title, left + MARGIN, headerY, 0xFFFFFF, false);
    }

    private void renderFieldLabels(GuiGraphics guiGraphics) {
        for (LabelEntry label : labels) {
            guiGraphics.drawString(this.font, label.text(), label.x(), label.y(), 0xAAAAAA, false);
        }
        renderIdPreview(guiGraphics);
    }

    /**
     * "id: <namespace>:<path>  key: multiblock.<namespace>.<path>" - namespace is always
     * {@link #configuredNamespace} (fixed, from config - see its own javadoc), path is whatever's
     * currently typed. Purely informational (never sent anywhere) - lets the developer see the real
     * id/translation key this multiblock will export as, instead of having to infer it.
     */
    private void renderIdPreview(GuiGraphics guiGraphics) {
        int left = (this.width - this.imageWidth) / 2;
        int x = left + MARGIN;
        String path = pathBox.getValue().isBlank() ? "?" : pathBox.getValue();
        String text = "id: " + configuredNamespace + ":" + path + "  key: multiblock." + configuredNamespace + "." + path;
        guiGraphics.drawString(this.font, Component.literal(text), x, idPreviewY, 0x808080, false);
    }

    private void renderSummary(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int x = left + MARGIN;
        int y = summaryTop;
        int lineHeight = this.font.lineHeight + 2;
        int maxWidth = this.imageWidth - MARGIN * 2;
        blockListViewport = null;

        MultiblockScanResult scan = menu.getLastScan();
        // EMPTY_AREA is folded into the same branch as "never scanned yet" rather than treated as a
        // real failure (unlike TOO_MANY_BLOCK_TYPES/INCOMPLETE_MULTIPART_BLOCK, which genuinely need a
        // different world edit): it's just "nothing to show", and re-evaluating autoDetectOn live every
        // frame here (instead of freezing on the one-time packet's static message) is what makes this
        // line track the Detect toggle in real time, not only refresh when a new scan happens to land.
        boolean emptyArea = !menu.isLastScanSuccess()
                && net.astronomy.multilib.core.devtool.MultiblockScanner.EMPTY_AREA_MESSAGE.equals(menu.getLastScanMessage());
        if (!menu.isLastScanSuccess() && !emptyArea) {
            // Word-wrapped instead of a single drawString: the message previously ran straight off the
            // right edge of the panel instead of wrapping, and the underlying "scan failed" messages are
            // now short/specific enough (see MultiblockDevPacketHandler) that two lines is plenty.
            y = drawWrapped(guiGraphics, Component.literal(menu.getLastScanMessage()), x, y, maxWidth, lineHeight, 0xFF5555);
        } else if (scan == null || emptyArea) {
            // Worded for auto-detect (a toggle) rather than "click Detect", which no longer describes
            // what the button does. Re-evaluated live every frame (not just on the packet that produced
            // emptyArea/scan==null) so it tracks the Detect toggle in real time:
            //  - Detect OFF: nothing has run (or ever will, until re-enabled) - a plain gray instruction,
            //    not a warning, since there may or may not even be blocks placed yet.
            //  - Detect ON and still empty: Detect IS running and genuinely found nothing - that's the
            //    one case worth calling out in red, same as a real scan failure below.
            boolean autoDetectOn = this.minecraft != null && this.minecraft.level != null
                    && this.minecraft.level.getBlockEntity(menu.getDevBlockPos())
                            instanceof net.astronomy.multilib.core.devtool.MultiblockDevBlockEntity be
                    && be.isAutoDetectOn();
            if (autoDetectOn) {
                y = drawWrapped(guiGraphics, Component.literal(net.astronomy.multilib.core.devtool.MultiblockScanner.EMPTY_AREA_MESSAGE),
                        x, y, maxWidth, lineHeight, 0xFF5555);
            } else {
                guiGraphics.drawString(this.font, Component.literal("Turn on Detect"), x, y, 0xAAAAAA, false);
                y += lineHeight;
            }
        } else {
            // Pinned first, above the scrollable list below - this is the one piece of scan info that
            // must always stay visible without scrolling, regardless of how many block types the area
            // contains.
            if (scan.coreSymbol() != null) {
                // Component.translatable's varargs only accept Component/Number/Boolean/String - a
                // Character (as returned by coreSymbol()/activationSymbol()) is none of those and
                // throws IllegalArgumentException at render time, crashing the screen the instant a
                // tag exists. String.valueOf(...) converts it first.
                y = drawWrapped(guiGraphics, Component.translatable("multilib.devtool.tag_core", String.valueOf(scan.coreSymbol())), x, y, maxWidth, lineHeight, 0x55FF55);
            } else if (scan.activationSymbol() != null) {
                y = drawWrapped(guiGraphics, Component.translatable("multilib.devtool.tag_activation", String.valueOf(scan.activationSymbol())), x, y, maxWidth, lineHeight, 0x55AAFF);
            } else {
                y = drawWrapped(guiGraphics, Component.translatable("multilib.devtool.no_tag"), x, y, maxWidth, lineHeight, 0xAAAAAA);
            }
            y += 2;
            y = renderBlockList(guiGraphics, scan, x, y, lineHeight, mouseX, mouseY);
        }

        renderExportStatus(guiGraphics, x, y + 4, lineHeight, maxWidth);
    }

    /**
     * Horizontal scroll offset (px) to apply when drawing {@code text}, so a line too wide for
     * {@code maxWidth} scrolls back and forth while the mouse hovers it - like vanilla's own hover-scrolled
     * list entries (e.g. long world/server names) - and simply gets clipped at the edge otherwise. Callers
     * draw at {@code x - offset} inside a scissor region already bounding {@code [x, x + maxWidth]}; the
     * existing clip does the actual cropping; this only computes how far to shift the text left.
     */
    private int scrollOffsetIfHovered(String text, int maxWidth, boolean hovered) {
        if (!hovered) return 0;
        int overflow = this.font.width(text) - maxWidth;
        if (overflow <= 0) return 0;
        long oneWayMs = 1500L + overflow * 6L; // longer lines scroll a bit slower, not just farther
        long t = System.currentTimeMillis() % (oneWayMs * 2);
        float progress = t < oneWayMs ? (float) t / oneWayMs : (float) (2 * oneWayMs - t) / oneWayMs;
        return (int) (progress * overflow);
    }

    /**
     * Renders the scanned symbol -> block-type list in a fixed-height, scrollable viewport (scroll wheel,
     * handled by {@link #mouseScrolled}) instead of truncating with "..." past a hardcoded line count -
     * this keeps the GUI's own size fixed no matter how many distinct block types a scan finds, while
     * still letting every entry actually be reached.
     */
    private int renderBlockList(GuiGraphics guiGraphics, MultiblockScanResult scan, int x, int y, int lineHeight, int mouseX, int mouseY) {
        List<Map.Entry<Character, net.minecraft.resources.ResourceLocation>> entries =
                new ArrayList<>(scan.symbolToBlock().entrySet());
        int left = (this.width - this.imageWidth) / 2;
        int viewportRight = left + this.imageWidth - MARGIN;
        int rowMaxWidth = viewportRight - x;
        int visibleLines = Math.max(1, LIST_VIEWPORT_HEIGHT / lineHeight);
        int maxScroll = Math.max(0, entries.size() - visibleLines);
        blockListScroll = Math.max(0, Math.min(blockListScroll, maxScroll));

        guiGraphics.enableScissor(x, y, viewportRight, y + LIST_VIEWPORT_HEIGHT);
        int lineY = y;
        for (int i = blockListScroll; i < entries.size() && lineY < y + LIST_VIEWPORT_HEIGHT; i++) {
            var entry = entries.get(i);
            int count = scan.countOccurrences(entry.getKey());
            String line = entry.getKey() + " -> " + entry.getValue() + " (" + count + ")";
            boolean hovered = mouseX >= x && mouseX <= viewportRight && mouseY >= lineY && mouseY < lineY + lineHeight;
            int offset = scrollOffsetIfHovered(line, rowMaxWidth, hovered);
            guiGraphics.drawString(this.font, line, x - offset, lineY, 0xCCCCCC, false);
            lineY += lineHeight;
        }
        guiGraphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = viewportRight + 2;
            guiGraphics.fill(trackX, y, trackX + 2, y + LIST_VIEWPORT_HEIGHT, 0xFF303030);
            int thumbHeight = Math.max(6, LIST_VIEWPORT_HEIGHT * visibleLines / entries.size());
            int thumbY = y + (LIST_VIEWPORT_HEIGHT - thumbHeight) * blockListScroll / maxScroll;
            guiGraphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFAAAAAA);
        }

        blockListViewport = new int[] {x, y, viewportRight, y + LIST_VIEWPORT_HEIGHT};
        return y + LIST_VIEWPORT_HEIGHT;
    }

    /**
     * The Load tab's content: a scrollable list of every JSON export found (see
     * {@link net.astronomy.multilib.core.devtool.MultiblockDevExportLoader#list}), one row per
     * {@code namespace:path - displayName}, click to load it into the Create tab - see
     * {@link #handleLoadListClick}. Occupies the same body area the Create tab's fields/summary would.
     */
    private void renderLoadTab(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        int x = left + MARGIN;
        int y = top + MARGIN + this.font.lineHeight + ROW_GAP + 2 + LABEL_GAP;
        int lineHeight = this.font.lineHeight + 2;
        int maxWidth = this.imageWidth - MARGIN * 2;
        int viewportRight = left + this.imageWidth - MARGIN;

        List<LoadableMultiblock> entries = menu.getLoadableEntries();

        if (!menu.isLastLoadSuccess()) {
            y = drawWrapped(guiGraphics, Component.literal(menu.getLastLoadMessage()), x, y, maxWidth, lineHeight, 0xFF5555);
            y += 2;
        }

        if (entries.isEmpty()) {
            guiGraphics.drawString(this.font, Component.literal("No exported multiblocks found."), x, y, 0xAAAAAA, false);
            loadListViewport = null;
            return;
        }

        int visibleLines = Math.max(1, LOAD_LIST_VIEWPORT_HEIGHT / lineHeight);
        int maxScroll = Math.max(0, entries.size() - visibleLines);
        loadListScroll = Math.max(0, Math.min(loadListScroll, maxScroll));

        guiGraphics.enableScissor(x, y, viewportRight, y + LOAD_LIST_VIEWPORT_HEIGHT);
        int lineY = y;
        for (int i = loadListScroll; i < entries.size() && lineY < y + LOAD_LIST_VIEWPORT_HEIGHT; i++) {
            LoadableMultiblock entry = entries.get(i);
            boolean hovered = mouseX >= x && mouseX <= viewportRight && mouseY >= lineY && mouseY < lineY + lineHeight;
            int color = hovered ? 0xFFFFFF : 0xCCCCCC;
            String line = "[" + entry.format() + "] " + entry.namespace() + ":" + entry.path() + " - " + entry.displayName();
            int offset = scrollOffsetIfHovered(line, viewportRight - x, hovered);
            guiGraphics.drawString(this.font, line, x - offset, lineY, color, false);
            lineY += lineHeight;
        }
        guiGraphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = viewportRight + 2;
            guiGraphics.fill(trackX, y, trackX + 2, y + LOAD_LIST_VIEWPORT_HEIGHT, 0xFF303030);
            int thumbHeight = Math.max(6, LOAD_LIST_VIEWPORT_HEIGHT * visibleLines / entries.size());
            int thumbY = y + (LOAD_LIST_VIEWPORT_HEIGHT - thumbHeight) * loadListScroll / maxScroll;
            guiGraphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xFFAAAAAA);
        }

        loadListViewport = new int[] {x, y, viewportRight, y + LOAD_LIST_VIEWPORT_HEIGHT, lineHeight};
    }

    /** Sends a {@link RequestDevLoadPacket} for whichever row (if any) was clicked in the Load tab's list. */
    private boolean handleLoadListClick(double mouseX, double mouseY) {
        if (loadListViewport == null
                || mouseX < loadListViewport[0] || mouseX > loadListViewport[2]
                || mouseY < loadListViewport[1] || mouseY > loadListViewport[3]) {
            return false;
        }
        int lineHeight = loadListViewport[4];
        int index = loadListScroll + (int) ((mouseY - loadListViewport[1]) / lineHeight);
        List<LoadableMultiblock> entries = menu.getLoadableEntries();
        if (index < 0 || index >= entries.size()) return false;

        LoadableMultiblock entry = entries.get(index);
        // Both namespace and path passed now: this list includes every currently *registered* multiblock
        // too (hardcoded Java from other mods, JSON datapacks, KubeJS - not just this dev tool's own
        // exports), so the namespace half can no longer be assumed to always be
        // CommonConfig.DEVTOOL_NAMESPACE - see RequestDevLoadPacket's own javadoc.
        PacketDistributor.sendToServer(new RequestDevLoadPacket(menu.getDevBlockPos(), entry.format(), entry.namespace(), entry.path()));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentTab == Tab.LOAD) {
            if (loadListViewport != null
                    && mouseX >= loadListViewport[0] && mouseX <= loadListViewport[2]
                    && mouseY >= loadListViewport[1] && mouseY <= loadListViewport[3]) {
                loadListScroll -= (int) Math.signum(scrollY);
                return true;
            }
            return false;
        }
        if (blockListViewport != null
                && mouseX >= blockListViewport[0] && mouseX <= blockListViewport[2]
                && mouseY >= blockListViewport[1] && mouseY <= blockListViewport[3]) {
            blockListScroll -= (int) Math.signum(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Shown regardless of whether the export ever got as far as resolving a file path - previously this
     * only rendered when {@code lastExportPath} was non-empty, which silently swallowed every early-return
     * error (no scan yet, missing namespace/path, dev block gone) since those responses carry an empty
     * path. A failed export must always show something, even if it never touched the filesystem.
     */
    private void renderExportStatus(GuiGraphics guiGraphics, int x, int y, int lineHeight, int maxWidth) {
        String path = menu.getLastExportPath();
        String error = menu.getLastExportError();
        boolean hasPath = path != null && !path.isEmpty();
        boolean hasError = error != null && !error.isEmpty();
        if (!hasPath && !hasError) return;

        if (hasPath) {
            guiGraphics.drawString(this.font, Component.translatable("multilib.devtool.resolved_path"), x, y, 0xAAAAAA, false);
            y += lineHeight;
            y = drawWrapped(guiGraphics, Component.literal(path), x, y, maxWidth, lineHeight, menu.isLastExportSucceeded() ? 0x55FF55 : 0xFF5555);
        }
        if (hasError && !menu.isLastExportSucceeded()) {
            drawWrapped(guiGraphics, Component.literal(error), x, y, maxWidth, lineHeight, 0xFF5555);
        }
    }

    /** Draws word-wrapped text and returns the y position just below the last rendered line. */
    private int drawWrapped(GuiGraphics guiGraphics, Component text, int x, int y, int maxWidth, int lineHeight, int color) {
        guiGraphics.drawWordWrap(this.font, text, x, y, maxWidth, color);
        int lines = this.font.split(text, maxWidth).size();
        return y + Math.max(lines, 1) * lineHeight;
    }
}
