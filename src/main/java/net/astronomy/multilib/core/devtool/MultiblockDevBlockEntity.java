package net.astronomy.multilib.core.devtool;

import net.astronomy.multilib.MultiLib;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Block entity backing the dev-only Multiblock Dev Block (see {@code roadmap/phase-10-dev-authoring-tool.md}).
 * Persists the area configuration (offset/size), the multiblock identity fields (path/display
 * name - no {@code namespace}: the export id's namespace half is always {@link net.astronomy.multilib.CommonConfig#DEVTOOL_NAMESPACE},
 * a single fixed value with no per-block-entity copy of its own), the last {@link MultiblockScanResult},
 * and an optional core/activation tag - mirroring vanilla's Structure Block, reopening the GUI resumes
 * exactly where the developer left off.
 * <p>
 * The tag ({@link #tagBlockId}/{@link #tagIsCore}) is deliberately independent of {@link #lastScan}: it
 * identifies a tagged <em>block type</em> within the current area, not a cell in a specific scan grid -
 * so a developer can right-click with the dev wrench to set a core/activation tag before ever running
 * Detect. Whenever a scan
 * does happen, {@link #detectAndStore()} resolves the tag against the fresh grid to fill in
 * {@code coreSymbol}/{@code activationSymbol} for export; the tag itself survives untouched as long as
 * that block type is still present in the newly scanned area.
 */
public class MultiblockDevBlockEntity extends BlockEntity {

    /** Outcome reported back to the tagging listener for chat feedback. */
    public enum TagOutcome { TAGGED_CORE, TAGGED_ACTIVATION_DUPLICATE, UNTAGGED, POSITION_NOT_IN_AREA, NOT_PART_OF_SCAN }

    // Y=1 by default: the dev block itself sits at the area's own position on Detect/Render otherwise,
    // which almost always means the block directly below the intended scan area's floor - starting one
    // block up matches how the tool is actually used in practice.
    private BlockPos offset = new BlockPos(0, 1, 0);
    private Vec3i size = new Vec3i(0, 0, 0);
    private String path = "";
    private String displayName = "";

    private @Nullable MultiblockScanResult lastScan;

    /** Block type currently tagged core/activation, or {@code null} if nothing is tagged. */
    private @Nullable ResourceLocation tagBlockId;
    private boolean tagIsCore;

    /**
     * Exact world positions the tagged block type was placed at, known precisely because
     * {@link #tagFromScan} derives them from the very scan it's tagging from (the same layer-grid math
     * {@link #placePatternInWorld} used to place them) - set only by {@code tagFromScan}, {@code null}
     * for every other tagging path (wrench-tagging, re-Detect), which fall back to
     * {@link #findMatchingPositionsLive} instead. Bypasses that live client-side world query entirely for
     * the Load flow: relying on it there meant the glow depended on the client's own copy of the world
     * already reflecting the blocks {@link #placePatternInWorld} had just placed, which is not guaranteed
     * to be true yet at the exact moment the tag-sync packet is processed - "no core/activation shown
     * right after Load" even though the tag itself was set correctly. Known positions sent directly over
     * the wire have no such dependency.
     */
    private @Nullable List<BlockPos> knownTagPositions;

    /**
     * Whether the "Detect" button is in its on/off toggle state - when true, {@code MultiblockTickHandler}
     * periodically re-scans this dev-block on behalf of whichever player (if any) currently has its HUD
     * list shown (see {@link MultiblockDevListSessionRegistry}), instead of requiring a manual re-click
     * every time something in the area changes.
     */
    private boolean autoDetectOn = false;

    /**
     * Whether the "Render" area-preview toggle is on - persisted (unlike the GUI Screen's own local
     * {@code previewOn}, which only tracks the currently-open screen instance) so the preview box comes
     * back on its own after closing the GUI, relogging, or restarting the world, instead of only lasting
     * for as long as the client happens to keep {@code ClientMultiblockDevAreaPreviewState} around.
     */
    private boolean renderOn = false;

    public MultiblockDevBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static MultiblockDevBlockEntity create(BlockPos pos, BlockState state) {
        return new MultiblockDevBlockEntity(MultiblockDevRegistry.DEV_BLOCK_ENTITY_TYPE, pos, state);
    }

    // ---- Getters/setters used by the menu/screen/packet handler ----

    public BlockPos getOffset() { return offset; }

    public void setOffset(BlockPos offset) {
        this.offset = offset;
        setChanged();
        syncToClients();
    }

    public Vec3i getSize() { return size; }

    public void setSize(Vec3i size) {
        this.size = size;
        setChanged();
        syncToClients();
    }

    public String getPath() { return path; }

    public void setPath(String path) {
        this.path = path == null ? "" : path;
        setChanged();
        syncToClients();
    }

    public boolean isAutoDetectOn() { return autoDetectOn; }

    public void setAutoDetectOn(boolean autoDetectOn) {
        this.autoDetectOn = autoDetectOn;
        updateAutoDetectRegistration();
        setChanged();
        syncToClients();
    }

    /** Keeps {@link MultiblockDevAutoDetectRegistry} in sync with {@link #autoDetectOn} - a no-op on the client, where that registry isn't consulted. */
    private void updateAutoDetectRegistration() {
        if (level == null || level.isClientSide()) return;
        if (autoDetectOn) {
            MultiblockDevAutoDetectRegistry.register(level.dimension(), worldPosition);
        } else {
            MultiblockDevAutoDetectRegistry.unregister(level.dimension(), worldPosition);
        }
    }

    /** Re-registers with {@link MultiblockDevAutoDetectRegistry} once the level is actually available - a world reload/server restart re-adds a block that was left with auto-detect on. */
    @Override
    public void onLoad() {
        super.onLoad();
        if (autoDetectOn) {
            updateAutoDetectRegistration();
        }
        propagateRenderPreviewIfApplicable();
    }

    public boolean isRenderOn() { return renderOn; }

    public void setRenderOn(boolean renderOn) {
        this.renderOn = renderOn;
        setChanged();
        syncToClients();
    }

    /**
     * Mirrors {@link #renderOn} into {@code ClientMultiblockDevAreaPreviewState} - the client-side-only
     * area preview box - using this block entity's own persisted offset/size, so the preview comes back
     * automatically on chunk load/rejoin without needing the GUI open or the "Render" button clicked
     * again. Only meaningful client-side.
     */
    private void propagateRenderPreviewIfApplicable() {
        if (level == null || !level.isClientSide()) return;
        if (!renderOn) {
            // Only clear if this block is the one that actually owns the currently-shown preview -
            // don't stomp a different dev-block's own preview just because this one loaded/synced.
            net.astronomy.multilib.client.devtool.ClientMultiblockDevAreaPreviewState.Box current =
                    net.astronomy.multilib.client.devtool.ClientMultiblockDevAreaPreviewState.get();
            if (current != null && current.ownerPos().equals(worldPosition)) {
                net.astronomy.multilib.client.devtool.ClientMultiblockDevAreaPreviewState.clear();
            }
            return;
        }
        BlockPos min = worldPosition.offset(offset);
        BlockPos max = min.offset(Math.max(size.getX(), 1) - 1, Math.max(size.getY(), 1) - 1, Math.max(size.getZ(), 1) - 1);
        net.astronomy.multilib.client.devtool.ClientMultiblockDevAreaPreviewState.set(
                new net.astronomy.multilib.client.devtool.ClientMultiblockDevAreaPreviewState.Box(min, max, worldPosition));
    }

    public String getDisplayName() { return displayName; }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? "" : displayName;
        setChanged();
        syncToClients();
    }

    public Optional<MultiblockScanResult> getLastScan() {
        return Optional.ofNullable(lastScan);
    }

    /**
     * Populates this dev-block's identity fields and scan data from an existing export loaded via
     * {@link MultiblockDevExportLoader} (the GUI's Load tab), as if the developer had typed the same
     * path/display-name and then scanned exactly that structure. No {@code namespace} parameter - the
     * export id's namespace half is always {@link net.astronomy.multilib.CommonConfig#DEVTOOL_NAMESPACE},
     * a single fixed value this block entity never stores its own copy of. Any current core/activation
     * tag is dropped - the loaded scan's own core/activation symbol (already baked into {@code scan}) is
     * authoritative from this point, and a stale tag from whatever was previously in this dev-block's
     * area would otherwise silently override it.
     */
    public void loadExisting(String path, String displayName, MultiblockScanResult scan) {
        this.path = path;
        this.displayName = displayName;
        this.lastScan = scan;
        this.tagBlockId = null;
        this.tagIsCore = false;
        this.knownTagPositions = null;
        setChanged();
        syncToClients();
    }

    /**
     * Grows {@link #size} to {@code scan}'s own width/layer-count/depth, clears that entire area to air,
     * then places every non-space cell into the world at this dev-block's configured {@link #offset} -
     * called right after {@link #loadExisting} so the developer sees the actual loaded structure appear
     * in the world (ready to tweak by hand and re-Detect) instead of only reading its layers in the
     * GUI's text summary. The area is cleared first (not just stamped over whatever was already there)
     * so the render preview box and the placed structure always agree exactly with the loaded pattern's
     * own shape - a stray leftover block from whatever used to occupy the area would otherwise look like
     * part of the loaded multiblock. A no-op on the client, and if the scan has no layers (nothing to
     * place).
     */
    public void placePatternInWorld(MultiblockScanResult scan) {
        if (level == null || level.isClientSide() || scan.layers().isEmpty()) return;

        List<List<String>> layers = scan.layers();
        int layerCount = layers.size();
        int depth = 0;
        int width = 0;
        for (List<String> layer : layers) {
            depth = Math.max(depth, layer.size());
            for (String row : layer) width = Math.max(width, row.length());
        }
        setSize(new Vec3i(Math.max(width, 1), Math.max(layerCount, 1), Math.max(depth, 1)));

        BlockPos origin = worldPosition.offset(offset);
        BoundingBox box = getAbsoluteBoundingBox();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.equals(worldPosition)) continue; // never overwrite the dev block itself
                    level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        Map<Character, List<BlockPos>> placedPositionsBySymbol = new LinkedHashMap<>();
        for (int layerIdx = 0; layerIdx < layerCount; layerIdx++) {
            // layer 0 = highest Y (top), matching MultiblockScanner/MultiblockStructureExporter's own convention.
            int y = layerCount - 1 - layerIdx;
            List<String> layer = layers.get(layerIdx);
            for (int row = 0; row < layer.size(); row++) {
                String line = layer.get(row);
                for (int col = 0; col < line.length(); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;
                    ResourceLocation blockId = scan.symbolToBlock().get(symbol);
                    if (blockId == null) continue;
                    BlockPos pos = origin.offset(col, y, row);
                    if (pos.equals(worldPosition)) continue; // never overwrite the dev block itself
                    level.setBlock(pos, BuiltInRegistries.BLOCK.get(blockId).defaultBlockState(), 3);
                    placedPositionsBySymbol.computeIfAbsent(symbol, s -> new ArrayList<>()).add(pos);
                }
            }
        }

        // A door/bed/tall-plant placed cell-by-cell above ends up as independent default states (e.g.
        // two unlinked lower door halves instead of one working door) - see MultiblockMultiPartBlocks's
        // own javadoc for why. Re-links each recognized multi-part symbol's positions afterward.
        for (Map.Entry<Character, List<BlockPos>> entry : placedPositionsBySymbol.entrySet()) {
            ResourceLocation blockId = scan.symbolToBlock().get(entry.getKey());
            if (blockId == null) continue;
            Block block = BuiltInRegistries.BLOCK.get(blockId);
            if (MultiblockMultiPartBlocks.isMultiPart(block)) {
                MultiblockMultiPartBlocks.relinkPlaced(level, entry.getValue(), block);
            }
        }
        setChanged();
    }

    /**
     * Tags whichever block type {@code scan}'s core (or, lacking one, activation) symbol maps to, the
     * same {@link #tagBlockId}/{@link #tagIsCore} fields {@link #tagPosition} maintains - so the
     * existing glow renderer ({@link net.astronomy.multilib.client.devtool.ClientMultiblockDevTagState},
     * fed via {@link #propagateTagToClientIfApplicable}) immediately outlines the loaded structure's
     * core/activation block(s) in the world, exactly as if the developer had just wrench-tagged it by
     * hand. A no-op if the scan has neither symbol tagged.
     */
    public void tagFromScan(MultiblockScanResult scan) {
        // A no-op whenever the loaded export was itself created without ever wrench-tagging a core/
        // activation block first (Detect + Export, skipping the tag step) - there's nothing recorded to
        // restore in that case, not a bug: re-tag the source multiblock and re-export it to fix that at
        // the source, rather than expecting Load to invent a tag that was never there to begin with.
        Character symbol = scan.coreSymbol() != null ? scan.coreSymbol() : scan.activationSymbol();
        if (symbol == null) return;
        ResourceLocation blockId = scan.symbolToBlock().get(symbol);
        if (blockId == null) return;

        this.tagBlockId = blockId;
        this.tagIsCore = scan.coreSymbol() != null;
        this.knownTagPositions = findSymbolPositions(scan, symbol);
        setChanged();
        syncToClients();
    }

    /**
     * Every world position {@code symbol} occupies in {@code scan}, using the exact same layer-grid ->
     * world-position math {@link #placePatternInWorld} uses to actually place those blocks - see
     * {@link #knownTagPositions}.
     */
    private List<BlockPos> findSymbolPositions(MultiblockScanResult scan, char symbol) {
        List<BlockPos> positions = new ArrayList<>();
        List<List<String>> layers = scan.layers();
        int layerCount = layers.size();
        BlockPos origin = worldPosition.offset(offset);
        for (int layerIdx = 0; layerIdx < layerCount; layerIdx++) {
            int y = layerCount - 1 - layerIdx;
            List<String> layer = layers.get(layerIdx);
            for (int row = 0; row < layer.size(); row++) {
                String line = layer.get(row);
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) != symbol) continue;
                    BlockPos pos = origin.offset(col, y, row);
                    if (pos.equals(worldPosition)) continue;
                    positions.add(pos);
                }
            }
        }
        return positions;
    }

    /** @return the absolute (world-space) bounding box this dev-block currently scans, derived from offset+size. */
    public BoundingBox getAbsoluteBoundingBox() {
        BlockPos origin = worldPosition.offset(offset);
        int x2 = origin.getX() + Math.max(size.getX(), 1) - 1;
        int y2 = origin.getY() + Math.max(size.getY(), 1) - 1;
        int z2 = origin.getZ() + Math.max(size.getZ(), 1) - 1;
        return new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), x2, y2, z2);
    }

    /**
     * Reads the real world within {@link #getAbsoluteBoundingBox()}, excluding this dev-block's own
     * position, and hands the resulting {@code Map<BlockPos, BlockState>} to {@link MultiblockScanner#scan}.
     * The current tag (if any) is preserved and resolved against the new grid - see
     * {@link #resolveTagAgainstScan(MultiblockScanResult)} - rather than being wiped on every Detect, so
     * tagging before ever running Detect (or re-Detecting after tagging) both work.
     */
    public MultiblockScanner.ScanOutcome detectAndStore() {
        if (level == null) return new MultiblockScanner.ScanOutcome(Optional.empty(), MultiblockScanner.FailureReason.EMPTY_AREA);

        BoundingBox box = getAbsoluteBoundingBox();
        Map<BlockPos, BlockState> area = new LinkedHashMap<>();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    area.put(pos, level.getBlockState(pos));
                }
            }
        }

        MultiblockScanner.ScanOutcome outcome = MultiblockScanner.scan(
                area,
                new BlockPos(box.minX(), box.minY(), box.minZ()),
                new BlockPos(box.maxX(), box.maxY(), box.maxZ()),
                worldPosition
        );

        this.lastScan = outcome.result().map(this::resolveTagAgainstScan).orElse(null);
        setChanged();
        syncToClients();
        return outcome;
    }

    /**
     * Fills {@code coreSymbol}/{@code activationSymbol} on a freshly scanned result from the current tag,
     * if the tagged block type is still present in the new grid. Exported results always come straight
     * from this method's return value, never from {@link #tagBlockId} directly, so leaving
     * {@code coreSymbol}/{@code activationSymbol} unset here is already enough to keep a stale tag from
     * ever reaching an export - {@code tagBlockId} itself is deliberately *not* cleared when the block
     * type isn't found, only the displayed symbol is: a scan can transiently miss a block that's
     * genuinely still there (e.g. a re-Detect fired against a briefly inconsistent offset/size mid-edit,
     * or one queued a tick before a `Load`-placed structure's blocks actually landed), and clearing
     * {@code tagBlockId} outright turned every one of those transient misses into a *permanent* loss of
     * the tag - the next correct scan had nothing left to re-find. Keeping it dormant instead means the
     * very next scan that actually contains the block silently restores the correct display on its own.
     * <p>
     * Also re-derives core-vs-activation from this fresh scan's occurrence count, not from whatever
     * {@link #tagIsCore} was left at by the original tag - re-running Detect is exactly the moment a
     * previously-unique block can turn out to no longer be unique (or vice versa), and that should flip
     * the tag automatically instead of only ever getting fixed by manually untagging and re-tagging.
     */
    private MultiblockScanResult resolveTagAgainstScan(MultiblockScanResult scan) {
        if (tagBlockId == null) return scan;

        Character symbol = findSymbolForBlock(scan, tagBlockId);
        if (symbol == null) {
            return scan;
        }
        this.tagIsCore = scan.countLogicalOccurrences(symbol, BuiltInRegistries.BLOCK.get(tagBlockId)) == 1;
        // A real Detect (manual or auto) is exactly the moment to re-verify the tag against the live
        // world instead of trusting a remembered position list from however it was last set (e.g. a
        // Load, possibly of a different structure since) - falls back to findMatchingPositionsLive.
        this.knownTagPositions = null;
        return new MultiblockScanResult(scan.layers(), scan.symbolToBlock(),
                tagIsCore ? symbol : null, tagIsCore ? null : symbol);
    }

    private static @Nullable Character findSymbolForBlock(MultiblockScanResult scan, ResourceLocation blockId) {
        for (Map.Entry<Character, ResourceLocation> entry : scan.symbolToBlock().entrySet()) {
            if (entry.getValue().equals(blockId)) return entry.getKey();
        }
        return null;
    }

    /**
     * Tags {@code worldPos} (already known to hold {@code stateAtPos}) as core or activation, per the
     * roadmap (Design 3 / requisito 4): unique block type in the current area -> core, otherwise ->
     * activation only. Works without ever having run Detect - occurrences are counted directly against
     * the live world within {@link #getAbsoluteBoundingBox()}, not against {@link #lastScan}. Right
     * -clicking a block of the currently-tagged type again with the dev wrench removes the tag
     * ({@link TagOutcome#UNTAGGED}).
     */
    public TagOutcome tagPosition(BlockPos worldPos, BlockState stateAtPos) {
        if (level == null) return TagOutcome.POSITION_NOT_IN_AREA;

        BoundingBox box = getAbsoluteBoundingBox();
        if (worldPos.getX() < box.minX() || worldPos.getX() > box.maxX()
                || worldPos.getY() < box.minY() || worldPos.getY() > box.maxY()
                || worldPos.getZ() < box.minZ() || worldPos.getZ() > box.maxZ()) {
            return TagOutcome.POSITION_NOT_IN_AREA;
        }

        ResourceLocation clickedBlockId = BuiltInRegistries.BLOCK.getKey(stateAtPos.getBlock());

        if (clickedBlockId.equals(tagBlockId)) {
            clearTag();
            return TagOutcome.UNTAGGED;
        }

        // Rejected outright (no field mutated) rather than tagging it "invisibly": if there's already a
        // scan and this block isn't part of it, updating tagBlockId here without also being able to
        // refresh lastScan's displayed core/activation symbol (the block below this comment used to skip
        // that update silently when the symbol lookup failed) left tagBlockId pointing at a block the GUI
        // never showed as tagged - so the *next* click on that same block matched tagBlockId and untagged
        // it, looking like "select it -> nothing happens -> select it again -> deselects". Requiring the
        // block to already be part of the last scan keeps tagBlockId and the displayed tag atomically in
        // sync; re-Detect first to pick up a block that isn't part of the scan yet.
        if (lastScan != null && findSymbolForBlock(lastScan, clickedBlockId) == null) {
            return TagOutcome.NOT_PART_OF_SCAN;
        }

        List<BlockPos> livePositions = findMatchingPositionsLive(stateAtPos.getBlock());
        int occurrences = MultiblockMultiPartBlocks.countLogicalInstances(livePositions, stateAtPos.getBlock());
        if (occurrences == 0) {
            // Shouldn't happen (worldPos is inside the box and holds this exact block), but defensive.
            return TagOutcome.POSITION_NOT_IN_AREA;
        }

        this.tagBlockId = clickedBlockId;
        this.tagIsCore = occurrences == 1;
        // A live wrench-tag always re-derives its glow positions from the world (findMatchingPositionsLive) -
        // only tagFromScan (Load) knows its positions ahead of time.
        this.knownTagPositions = null;
        TagOutcome outcome = tagIsCore ? TagOutcome.TAGGED_CORE : TagOutcome.TAGGED_ACTIVATION_DUPLICATE;

        if (lastScan != null) {
            // Guaranteed non-null now - already checked above before tagBlockId/tagIsCore were mutated.
            Character symbol = findSymbolForBlock(lastScan, clickedBlockId);
            this.lastScan = new MultiblockScanResult(lastScan.layers(), lastScan.symbolToBlock(),
                    tagIsCore ? symbol : lastScan.coreSymbol(), symbol);
        }
        setChanged();
        syncToClients();
        return outcome;
    }

    public void clearTag() {
        this.tagBlockId = null;
        this.tagIsCore = false;
        this.knownTagPositions = null;
        if (lastScan != null) {
            this.lastScan = new MultiblockScanResult(lastScan.layers(), lastScan.symbolToBlock(), null, null);
        }
        setChanged();
        syncToClients();
    }

    /** Every position within {@link #getAbsoluteBoundingBox()} currently holding {@code block}. */
    private List<BlockPos> findMatchingPositionsLive(Block block) {
        List<BlockPos> matches = new ArrayList<>();
        if (level == null) return matches;
        BoundingBox box = getAbsoluteBoundingBox();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (pos.equals(worldPosition)) continue;
                    if (level.getBlockState(pos).is(block)) matches.add(pos.immutable());
                }
            }
        }
        return matches;
    }

    /**
     * Pushes a fresh {@link ClientboundBlockEntityDataPacket} to every client tracking this block so
     * their cached {@code MultiblockDevBlockEntity} copy reflects whatever just changed server-side. A
     * plain {@link #setChanged()} only marks the chunk dirty for saving to disk - it does not, by
     * itself, notify any client, which previously meant reopening the GUI in the same session showed
     * stale field values (path/offset/size) until the next chunk reload/relog naturally
     * triggered a resync via {@link #getUpdatePacket()}. No-op on the client-side copy of this BE.
     */
    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Mirrors the current tag (if any) into {@code ClientMultiblockDevTagState}, the client-side glow
     * renderer's data source. Uses {@link #knownTagPositions} directly when set (synced straight from
     * the server, see its own javadoc for why) - otherwise falls back to recomputing every matching
     * position (not just the one originally clicked) from the client's own view of the world, so the
     * renderer can outline every occurrence of the tagged block type. Only meaningful client-side, after
     * a sync packet/update-tag has repopulated this BE's fields on the client.
     */
    private void propagateTagToClientIfApplicable() {
        if (level == null || !level.isClientSide()) return;
        if (tagBlockId == null) {
            net.astronomy.multilib.client.devtool.ClientMultiblockDevTagState.update(this.getBlockPos(), null);
            return;
        }
        Block block = BuiltInRegistries.BLOCK.get(tagBlockId);
        List<BlockPos> positions = knownTagPositions != null ? knownTagPositions : findMatchingPositionsLive(block);
        String blockName = block.getName().getString();
        net.astronomy.multilib.client.devtool.ClientMultiblockDevTagState.update(
                this.getBlockPos(),
                new net.astronomy.multilib.client.devtool.ClientMultiblockDevTagState.TagInfo(positions, tagIsCore, blockName));
    }

    // ---- NBT ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("offset", net.minecraft.nbt.NbtUtils.writeBlockPos(offset));
        tag.putInt("sizeX", size.getX());
        tag.putInt("sizeY", size.getY());
        tag.putInt("sizeZ", size.getZ());
        tag.putString("path", path);
        tag.putString("displayName", displayName);
        tag.putBoolean("autoDetectOn", autoDetectOn);
        tag.putBoolean("renderOn", renderOn);

        if (lastScan != null) {
            tag.put("scan", writeScan(lastScan));
        }
        if (tagBlockId != null) {
            tag.putString("tagBlockId", tagBlockId.toString());
            tag.putBoolean("tagIsCore", tagIsCore);
        }
        if (knownTagPositions != null) {
            ListTag positionsTag = new ListTag();
            for (BlockPos pos : knownTagPositions) {
                CompoundTag posTag = new CompoundTag();
                posTag.put("p", net.minecraft.nbt.NbtUtils.writeBlockPos(pos));
                positionsTag.add(posTag);
            }
            tag.put("knownTagPositions", positionsTag);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.offset = tag.contains("offset")
                ? net.minecraft.nbt.NbtUtils.readBlockPos(tag, "offset").orElse(new BlockPos(0, 1, 0))
                : new BlockPos(0, 1, 0);
        int sx = Math.max(tag.getInt("sizeX"), 1);
        int sy = Math.max(tag.getInt("sizeY"), 1);
        int sz = Math.max(tag.getInt("sizeZ"), 1);
        this.size = tag.contains("sizeX") ? new Vec3i(sx, sy, sz) : new Vec3i(1, 1, 1);
        this.path = tag.getString("path");
        this.displayName = tag.getString("displayName");
        this.autoDetectOn = tag.getBoolean("autoDetectOn");
        this.renderOn = tag.getBoolean("renderOn");

        this.lastScan = tag.contains("scan") ? readScan(tag.getCompound("scan")) : null;

        if (tag.contains("tagBlockId")) {
            this.tagBlockId = ResourceLocation.tryParse(tag.getString("tagBlockId"));
            this.tagIsCore = tag.getBoolean("tagIsCore");
        } else {
            this.tagBlockId = null;
            this.tagIsCore = false;
        }

        if (tag.contains("knownTagPositions")) {
            List<BlockPos> positions = new ArrayList<>();
            ListTag positionsTag = tag.getList("knownTagPositions", Tag.TAG_COMPOUND);
            for (int i = 0; i < positionsTag.size(); i++) {
                CompoundTag posTag = (CompoundTag) positionsTag.get(i);
                net.minecraft.nbt.NbtUtils.readBlockPos(posTag, "p").ifPresent(positions::add);
            }
            this.knownTagPositions = positions;
        } else {
            this.knownTagPositions = null;
        }
    }

    private static CompoundTag writeScan(MultiblockScanResult scan) {
        CompoundTag scanTag = new CompoundTag();

        ListTag layersTag = new ListTag();
        for (java.util.List<String> layer : scan.layers()) {
            ListTag layerTag = new ListTag();
            for (String row : layer) {
                layerTag.add(StringTag.valueOf(row));
            }
            layersTag.add(layerTag);
        }
        scanTag.put("layers", layersTag);

        ListTag symbolsTag = new ListTag();
        ListTag blocksTag = new ListTag();
        for (Map.Entry<Character, ResourceLocation> entry : scan.symbolToBlock().entrySet()) {
            symbolsTag.add(StringTag.valueOf(String.valueOf(entry.getKey())));
            blocksTag.add(StringTag.valueOf(entry.getValue().toString()));
        }
        scanTag.put("symbols", symbolsTag);
        scanTag.put("blocks", blocksTag);

        if (scan.coreSymbol() != null) {
            scanTag.putString("core", String.valueOf((char) scan.coreSymbol()));
        }
        if (scan.activationSymbol() != null) {
            scanTag.putString("activation", String.valueOf((char) scan.activationSymbol()));
        }
        return scanTag;
    }

    private static @Nullable MultiblockScanResult readScan(CompoundTag scanTag) {
        java.util.List<java.util.List<String>> layers = new java.util.ArrayList<>();
        ListTag layersTag = scanTag.getList("layers", Tag.TAG_LIST);
        for (int i = 0; i < layersTag.size(); i++) {
            ListTag layerTag = (ListTag) layersTag.get(i);
            java.util.List<String> rows = new java.util.ArrayList<>();
            for (int j = 0; j < layerTag.size(); j++) {
                rows.add(layerTag.getString(j));
            }
            layers.add(rows);
        }

        LinkedHashMap<Character, ResourceLocation> symbolToBlock = new LinkedHashMap<>();
        ListTag symbolsTag = scanTag.getList("symbols", Tag.TAG_STRING);
        ListTag blocksTag = scanTag.getList("blocks", Tag.TAG_STRING);
        for (int i = 0; i < symbolsTag.size() && i < blocksTag.size(); i++) {
            String symbolStr = symbolsTag.getString(i);
            if (symbolStr.isEmpty()) continue;
            ResourceLocation blockId = ResourceLocation.tryParse(blocksTag.getString(i));
            if (blockId == null) {
                MultiLib.LOGGER.warn("[MultiLib] Dev block: could not parse stored block id '{}' for symbol '{}' while loading scan NBT", blocksTag.getString(i), symbolStr);
                continue;
            }
            symbolToBlock.put(symbolStr.charAt(0), blockId);
        }

        Character core = scanTag.contains("core") ? scanTag.getString("core").charAt(0) : null;
        Character activation = scanTag.contains("activation") ? scanTag.getString("activation").charAt(0) : null;
        return new MultiblockScanResult(layers, symbolToBlock, core, activation);
    }

    // ---- Client sync ----
    //
    // Design choice: rather than a slimmer partial-update payload, getUpdateTag/handleUpdateTag simply
    // reuse the full saveAdditional/loadAdditional NBT. This BE's state (a handful of strings/ints plus
    // one modest scan) is small and only needs to sync on block-update/chunk-load, not every tick, so
    // the simplicity of one NBT format for both persistence and sync outweighs the bandwidth cost of a
    // dedicated slim payload. See AbstractMultiblockControllerBE for the project's other precedent of
    // this pattern (a smaller custom getUpdateTag) - this BE instead goes for the "just reuse save/load"
    // variant, which is equally standard for NeoForge 1.21 block entities with small persisted state.

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
        propagateTagToClientIfApplicable();
        propagateRenderPreviewIfApplicable();
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Overridden because NeoForge's own default (from {@code IBlockEntityExtension}) calls
     * {@code loadWithComponents(...)} directly for a live {@link ClientboundBlockEntityDataPacket} -
     * the packet {@link #syncToClients()} sends after every tag/field change - and never routes through
     * {@link #handleUpdateTag}. {@code handleUpdateTag} is only ever invoked for the chunk's *initial*
     * block entity data at chunk load, not for a live resync. That meant every field (offset, size,
     * path, the tag itself) updated correctly client-side after a tag/untag - which is why reopening
     * the GUI or checking the scan summary looked fine - but {@link #propagateTagToClientIfApplicable()}
     * (the one call that actually feeds {@link net.astronomy.multilib.client.devtool.ClientMultiblockDevTagState},
     * the glow renderer's data source) never ran on a live retag, only once at the chunk's initial load.
     * Delegating explicitly to {@link #handleUpdateTag} here (after replicating the default's own
     * {@code loadWithComponents} call, so component data still loads exactly as before) makes both paths
     * behave identically.
     */
    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ClientboundBlockEntityDataPacket packet,
                              HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null && !tag.isEmpty()) {
            loadWithComponents(tag, registries);
        }
        propagateTagToClientIfApplicable();
        propagateRenderPreviewIfApplicable();
    }

    /** Called by {@code MultiblockDevBlock#onRemove} when this dev-block is actually broken (not just a state change) - clears any client-side glow this instance was responsible for. */
    public void clearClientRenderState() {
        if (level != null && level.isClientSide()) {
            net.astronomy.multilib.client.devtool.ClientMultiblockDevTagState.update(this.getBlockPos(), null);
        }
    }
}
