package net.astronomy.multilib.core.devtool;

import net.astronomy.multilib.network.DevExportResultPacket;
import net.astronomy.multilib.network.DevLoadListPacket;
import net.astronomy.multilib.network.DevLoadResultPacket;
import net.astronomy.multilib.network.DevScanResultPacket;
import net.astronomy.multilib.network.RequestDevExportPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Menu for the Multiblock Dev Block GUI. Holds no inventory slots - it's purely a state container for
 * the custom {@code MultiblockDevScreen}: the last scan result and last export outcome received from the
 * server via {@link DevScanResultPacket}/{@link DevExportResultPacket}, updated in place by
 * {@code ClientPacketHandler} as packets arrive.
 */
public class MultiblockDevMenu extends AbstractContainerMenu {

    private final BlockPos devBlockPos;

    private @Nullable MultiblockScanResult lastScan;
    private boolean lastScanSuccess = true;
    private String lastScanMessage = "";

    private @Nullable String lastExportText;
    private String lastExportPath = "";
    private boolean lastExportIsDevSource;
    private boolean lastExportSucceeded;
    private String lastExportError = "";
    private @Nullable RequestDevExportPacket.Format lastExportFormat;
    private boolean lastExportRequiresConfirmation;

    /** Bumped on every {@link #applyExportResult} call (success, failure, or confirmation-needed) - the Screen compares this against its own last-seen value each frame to notice a completed export exactly once, the same pattern {@link #loadVersion} uses for Load. */
    private int exportResultVersion;

    private List<MultiblockDevExportLoader.LoadableMultiblock> loadableEntries = List.of();

    /**
     * Bumped every time a {@link DevLoadResultPacket} with {@code success=true} is applied - the
     * Screen compares this against its own last-seen value each frame (see
     * {@code MultiblockDevScreen#render}) to notice a completed Load and re-populate its EditBoxes,
     * the same way {@link #applyScanResult} feeds the read-only scan summary without needing the
     * Screen to poll a getter every tick for something that changes this rarely.
     */
    private int loadVersion;
    private String loadedPath = "";
    private String loadedDisplayName = "";
    private String loadedVariantName = "";
    private boolean lastLoadSuccess = true;
    private String lastLoadMessage = "";

    public MultiblockDevMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(MultiblockDevRegistry.DEV_MENU_TYPE, containerId);
        this.devBlockPos = pos;
    }

    public BlockPos getDevBlockPos() { return devBlockPos; }

    // ---- Client-side state, updated by ClientPacketHandler as packets arrive ----

    public void applyScanResult(DevScanResultPacket packet) {
        this.lastScanSuccess = packet.success();
        this.lastScanMessage = packet.message();
        this.lastScan = packet.success() ? packet.scan() : null;
    }

    public void applyExportResult(DevExportResultPacket packet) {
        this.lastExportText = packet.generatedText();
        this.lastExportPath = packet.resolvedPath();
        this.lastExportIsDevSource = packet.isDevSource();
        this.lastExportSucceeded = packet.writeSucceeded();
        this.lastExportError = packet.errorMessage();
        this.lastExportFormat = packet.format();
        this.lastExportRequiresConfirmation = packet.requiresConfirmation();
        this.exportResultVersion++;
    }

    public void applyLoadList(DevLoadListPacket packet) {
        this.loadableEntries = packet.entries();
    }

    public void applyLoadResult(DevLoadResultPacket packet) {
        this.lastLoadSuccess = packet.success();
        this.lastLoadMessage = packet.message();
        if (packet.success()) {
            this.loadedPath = packet.path();
            this.loadedDisplayName = packet.displayName();
            this.loadedVariantName = packet.variantName();
            this.lastScanSuccess = true;
            this.lastScanMessage = "";
            this.lastScan = packet.scan();
            this.loadVersion++;
        }
    }

    public List<MultiblockDevExportLoader.LoadableMultiblock> getLoadableEntries() { return loadableEntries; }
    public int getLoadVersion() { return loadVersion; }
    public String getLoadedPath() { return loadedPath; }
    public String getLoadedDisplayName() { return loadedDisplayName; }
    public String getLoadedVariantName() { return loadedVariantName; }
    public boolean isLastLoadSuccess() { return lastLoadSuccess; }
    public String getLastLoadMessage() { return lastLoadMessage; }

    public @Nullable MultiblockScanResult getLastScan() { return lastScan; }
    public boolean isLastScanSuccess() { return lastScanSuccess; }
    public String getLastScanMessage() { return lastScanMessage; }

    public @Nullable String getLastExportText() { return lastExportText; }
    public String getLastExportPath() { return lastExportPath; }
    public boolean isLastExportIsDevSource() { return lastExportIsDevSource; }
    public boolean isLastExportSucceeded() { return lastExportSucceeded; }
    public String getLastExportError() { return lastExportError; }
    public @Nullable RequestDevExportPacket.Format getLastExportFormat() { return lastExportFormat; }
    public boolean isLastExportRequiresConfirmation() { return lastExportRequiresConfirmation; }
    public int getExportResultVersion() { return exportResultVersion; }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(player.level(), devBlockPos), player,
                MultiblockDevRegistry.DEV_BLOCK);
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
