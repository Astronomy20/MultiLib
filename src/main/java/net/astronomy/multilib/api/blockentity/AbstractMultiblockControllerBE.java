package net.astronomy.multilib.api.blockentity;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.callback.MultiblockBrokenContext;
import net.astronomy.multilib.api.callback.MultiblockFormedContext;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.event.MultiblockStateChangedEvent;
import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.api.state.MultiblockState;
import net.astronomy.multilib.api.state.MultiblockStateRegistry;
import net.astronomy.multilib.api.state.StandardMultiblockState;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.core.tracking.MultiblockProgressionTracker;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.astronomy.multilib.event.BlockActivationHandler;
import net.astronomy.multilib.event.BlockBreakHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public abstract class AbstractMultiblockControllerBE extends BlockEntity {

    private MultiblockState state = StandardMultiblockState.UNFORMED;
    private UUID instanceId = null;
    private int validationInterval = 0;
    private int validationTicker = 0;
    // Set by invalidateStructure() to force the very next tick to (re)validate/try formation
    // immediately, instead of waiting out the rest of validationInterval - the interval remains a
    // periodic fallback (in case some change bypasses whatever calls invalidateStructure()), but a dev
    // who already knows something relevant changed shouldn't have to wait for it.
    private boolean structureDirty = false;
    /** Set while formed iff the structure's definition has {@code .model(...)}; read by {@link MultiblockMasterModelRenderer}. */
    private ResourceLocation activeModelId = null;

    protected AbstractMultiblockControllerBE(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    // ---- Public API ----

    public MultiblockState getState() { return state; }

    public void setState(MultiblockState newState) {
        if (this.state.equals(newState)) return;
        MultiblockState prev = this.state;
        this.state = newState;
        onStateChanged(prev, newState);
        recordProgressionOnStateChange(prev, newState);
        markDirtyAndSync();
    }

    /**
     * Records progression (for FTB Quests etc.) and posts {@link MultiblockStateChangedEvent} whenever
     * this controller's state changes, provided we can resolve a formed instance with a known former.
     * Best-effort: silently no-ops if not server-side, not tracked yet, or formed anonymously (e.g. by
     * a dispenser) since there's no player to attribute progression to.
     */
    private void recordProgressionOnStateChange(MultiblockState prev, MultiblockState next) {
        if (instanceId == null) return;
        getServerLevel().ifPresent(serverLevel -> {
            WorldMultiblockTracker tracker = WorldMultiblockTracker.get(serverLevel);
            tracker.getById(instanceId).ifPresent(instance -> {
                instance.getFormedBy().ifPresent(playerId ->
                        MultiblockProgressionTracker.get(serverLevel.getServer().overworld())
                                .recordStateReached(playerId, instance.getDefinitionId(), next.getId(),
                                        serverLevel.getServer().overworld().getGameTime()));

                MultiblockRegistry.get(instance.getDefinitionId()).ifPresent(definition -> {
                    MultiblockContext ctx = new MultiblockContext(serverLevel, instance, definition);
                    NeoForge.EVENT_BUS.post(new MultiblockStateChangedEvent(ctx, prev, next));
                });
            });
        });
    }

    public boolean isFormed() {
        return state != StandardMultiblockState.UNFORMED;
    }

    public UUID getInstanceId() { return instanceId; }

    public void setValidationInterval(int ticks) {
        this.validationInterval = ticks;
    }

    /**
     * Marks the structure for (re)validation/formation on the very next server tick, instead of waiting
     * out the rest of {@link #setValidationInterval(int)}'s countdown. For a dev whose own code knows a
     * relevant block changed (e.g. reacting to a neighbor update) and wants the controller to notice
     * immediately rather than up to {@code validationInterval} ticks later. Safe to call from anywhere,
     * any number of times - it only ever brings the next check closer, never delays it.
     */
    public void invalidateStructure() {
        this.structureDirty = true;
    }

    public @Nullable ResourceLocation getActiveModelId() { return activeModelId; }

    public Optional<ServerLevel> getServerLevel() {
        if (level instanceof ServerLevel sl) return Optional.of(sl);
        return Optional.empty();
    }

    public void markDirtyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ---- Internal hooks called by the system ----

    public final void onStructureFormed(MultiblockFormedContext ctx) {
        this.instanceId = ctx.instance().getId();
        setState(StandardMultiblockState.IDLE);
        if (ctx.definition().hasModel() && level != null) {
            this.activeModelId = ctx.definition().getModelId().orElse(null);
            // Compute positions to keep visible: core + any keepVisible symbols
            Set<BlockPos> keepVisible = buildKeepVisibleSet(ctx);
            for (BlockPos pos : ctx.instance().getPositions()) {
                if (!keepVisible.contains(pos)) {
                    AbstractMultiblockPartBlock.setModelHidden(level, pos, true);
                }
            }
        }
        onFormed(ctx);
        markDirtyAndSync();
    }

    public final void onStructureBroken(MultiblockBrokenContext ctx) {
        this.instanceId = null;
        setState(StandardMultiblockState.UNFORMED);
        if (this.activeModelId != null && level != null) {
            BlockPos removed = ctx.removedPos();
            for (BlockPos pos : ctx.instance().getPositions()) {
                if (!pos.equals(removed)) {
                    AbstractMultiblockPartBlock.setModelHidden(level, pos, false);
                }
            }
        }
        this.activeModelId = null;
        onBroken(ctx);
        markDirtyAndSync();
    }

    private Set<BlockPos> buildKeepVisibleSet(MultiblockFormedContext ctx) {
        Set<BlockPos> keep = new HashSet<>();
        keep.add(worldPosition);  // core is always kept visible
        for (char sym : ctx.definition().getKeepVisibleSymbols()) {
            keep.addAll(ctx.instance().getPositionsFor(sym));
        }
        return keep;
    }

    // ---- Developer-overridable hooks ----

    protected void onFormed(MultiblockFormedContext ctx) {}
    protected void onBroken(MultiblockBrokenContext ctx) {}
    protected void onStateChanged(MultiblockState prev, MultiblockState next) {}
    protected void serverTick() {}

    // ---- Server ticker ----

    public static <T extends AbstractMultiblockControllerBE> BlockEntityTicker<T> createServerTicker() {
        return (level, pos, state, be) -> {
            if (level.isClientSide()) return;
            be.tickInternal();
        };
    }

    void tickInternal() {
        boolean dueToDirty = structureDirty;
        structureDirty = false;

        if (!isFormed()) {
            if (dueToDirty) {
                tryPeriodicFormation();
            } else if (validationInterval > 0) {
                validationTicker++;
                if (validationTicker >= validationInterval) {
                    validationTicker = 0;
                    tryPeriodicFormation();
                }
            }
            return;
        }
        if (dueToDirty) {
            tryPeriodicValidation();
        } else if (validationInterval > 0) {
            validationTicker++;
            if (validationTicker >= validationInterval) {
                validationTicker = 0;
                tryPeriodicValidation();
            }
        }
        serverTick();
    }

    private void tryPeriodicFormation() {
        getServerLevel().ifPresent(sl ->
                BlockActivationHandler.triggerFormationAt(sl, worldPosition));
    }

    private void tryPeriodicValidation() {
        getServerLevel().ifPresent(sl -> {
            WorldMultiblockTracker tracker = WorldMultiblockTracker.get(sl);
            if (instanceId == null) return;
            Optional<MultiblockInstance> opt = tracker.getById(instanceId);
            if (opt.isEmpty()) {
                setState(StandardMultiblockState.UNFORMED);
                instanceId = null;
                return;
            }
            MultiblockInstance instance = opt.get();
            for (BlockPos pos : instance.getPositions()) {
                if (!sl.isLoaded(pos)) continue;
                if (sl.isEmptyBlock(pos)) {
                    BlockBreakHandler.handleBreak(sl, tracker, instance, pos,
                            MultiblockBrokenContext.BreakReason.UNKNOWN);
                    return;
                }
            }
        });
    }

    // ---- NBT ----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("state", state.getId().toString());
        if (instanceId != null) {
            tag.put("instanceId", NbtUtils.createUUID(instanceId));
        }
        if (activeModelId != null) {
            tag.putString("activeModelId", activeModelId.toString());
        }
        saveController(tag, registries);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.state = resolveState(tag.getString("state"));
        if (tag.contains("instanceId")) {
            this.instanceId = NbtUtils.loadUUID(tag.get("instanceId"));
        }
        if (tag.contains("activeModelId")) {
            this.activeModelId = ResourceLocation.tryParse(tag.getString("activeModelId"));
        }
        loadController(tag, registries);
    }

    protected MultiblockState resolveState(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            MultiLib.LOGGER.warn("[MultiLib] Invalid MultiblockState id '{}', falling back to UNFORMED", id);
            return StandardMultiblockState.UNFORMED;
        }
        return MultiblockStateRegistry.get(rl).orElseGet(() -> {
            MultiLib.LOGGER.warn("[MultiLib] Unknown MultiblockState '{}', falling back to UNFORMED", id);
            return StandardMultiblockState.UNFORMED;
        });
    }

    protected void saveController(CompoundTag tag, HolderLookup.Provider registries) {}
    protected void loadController(CompoundTag tag, HolderLookup.Provider registries) {}

    // ---- Client sync ----

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Mirrors saveAdditional() exactly (instanceId + saveController content included) - the default
        // BlockEntity#handleUpdateTag() feeds whatever this returns straight into loadAdditional() on the
        // client, so leaving any of these out means the client silently keeps stale/default values for
        // them forever (e.g. a controller's own fluid tank never reflecting real contents client-side,
        // which broke fill/drain "full" detection - see ExampleTankBlock#useItemOn).
        CompoundTag tag = new CompoundTag();
        tag.putString("state", state.getId().toString());
        if (instanceId != null) {
            tag.put("instanceId", NbtUtils.createUUID(instanceId));
        }
        if (activeModelId != null) {
            tag.putString("activeModelId", activeModelId.toString());
        }
        saveController(tag, registries);
        return tag;
    }

    @Override
    public @Nullable ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * {@code setChanged()} alone (the default {@code onChanged} callback wired into every controller's
     * component fields, e.g. {@link net.astronomy.multilib.api.component.FluidTankComponent}) only marks
     * this block entity's chunk dirty for saving - it does NOT push an update packet to nearby clients.
     * Without this override, content changes (fluid fill/drain, energy/item component changes, etc.) were
     * only ever visible to the client that triggered them via other means (open GUI, chat message) and
     * never actually synced, so every OTHER client-side read of this data (e.g. deciding whether a bucket
     * interaction should fall through to placing a world fluid block) worked off permanently stale data.
     */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }
}
