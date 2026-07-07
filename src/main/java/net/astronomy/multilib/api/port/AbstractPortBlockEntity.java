package net.astronomy.multilib.api.port;

import net.astronomy.multilib.api.blockentity.AbstractMultiblockPartBE;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Optional;

/**
 * Base block entity for port/hatch blocks: dedicated structure positions that let the outside world
 * (pipes, cables, other mods' automation) reach a formed multiblock's capabilities, while the actual
 * inventory/tank/energy buffer lives on the controller's own block entity. Modeled after the
 * "hatch"/"port" pattern common to GregTech, Mekanism, Immersive Engineering and Bigger Reactors -
 * the port itself holds no state of its own beyond which controller it currently belongs to.
 * <p>
 * Built on top of {@link AbstractMultiblockPartBE} (and therefore {@link net.astronomy.multilib.api.blockentity.IMultiblockPart}),
 * so joining/leaving a structure is detected exactly the way every other part is. This class adds one
 * thing on top: it remembers the controller's position across {@code onJoinedStructure}/{@code onLeftStructure}
 * and persists it to NBT, so {@link #getController()} keeps working immediately after a chunk reload -
 * before the structure has had a chance to re-validate.
 * <p>
 * Deliberately generic over the controller's actual type: {@link #getController()} returns a plain
 * {@link BlockEntity}, and {@link #getControllerCapability} resolves capabilities via
 * {@link net.minecraft.world.level.Level#getCapability} rather than casting to any specific interface
 * or component class. A port built on this class works with any controller, not just one extending
 * {@link net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE}.
 * <p>
 * No ticking: everything here resolves lazily, only when something actually asks (a capability query,
 * or a direct {@link #getController()} call). The resolved block entity is cached weakly, keyed by the
 * controller position, so repeated queries in the same tick/area don't repeatedly hit the chunk map -
 * but the cache is never itself a strong reference, and is dropped whenever the port leaves its
 * structure or rejoins at a different controller position.
 */
public abstract class AbstractPortBlockEntity extends AbstractMultiblockPartBE {

    private static final String TAG_CONTROLLER_POS = "multilib_port_controllerPos";

    /** Position of this port's controller while part of a formed structure; {@code null} otherwise. */
    private BlockPos controllerPos;

    // Weak, position-keyed cache of the last-resolved controller block entity. Weak so the cache can
    // never itself be the reason a controller's chunk (or the BE object) stays reachable; keyed by
    // position (rather than trusting the referent alone) so a stale entry from a previous structure at
    // the same port is never mistaken for the current one.
    private transient WeakReference<BlockEntity> cachedController;
    private transient BlockPos cachedControllerPos;

    protected AbstractPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState);
    }

    @Override
    public void onJoinedStructure(MultiblockInstance instance) {
        this.controllerPos = instance.getCorePos().orElse(null);
        invalidateCache();
        setChanged();
    }

    @Override
    public void onLeftStructure() {
        this.controllerPos = null;
        invalidateCache();
        setChanged();
    }

    private void invalidateCache() {
        this.cachedController = null;
        this.cachedControllerPos = null;
    }

    /**
     * @return the controller block entity of the structure this port currently belongs to. Empty if this
     *         port isn't part of a formed structure, the controller's chunk isn't currently loaded, or
     *         (defensively) the recorded controller position turns out to be this very port - a port must
     *         never proxy a capability query back to itself.
     */
    public Optional<BlockEntity> getController() {
        if (level == null || controllerPos == null) return Optional.empty();
        if (controllerPos.equals(worldPosition)) return Optional.empty();

        if (controllerPos.equals(cachedControllerPos) && cachedController != null) {
            BlockEntity cached = cachedController.get();
            if (cached != null && !cached.isRemoved()) return Optional.of(cached);
        }

        if (!level.isLoaded(controllerPos)) return Optional.empty();
        BlockEntity be = level.getBlockEntity(controllerPos);
        if (be == null || be == this) return Optional.empty();

        this.cachedControllerPos = controllerPos;
        this.cachedController = new WeakReference<>(be);
        return Optional.of(be);
    }

    /**
     * Convenience overload of {@link #getController()} for a dev who knows (or wants to check) the
     * controller's concrete block entity type - e.g. {@code getController(MyControllerBE.class)}.
     * Still works with any controller type; this isn't limited to
     * {@link net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE}.
     */
    public <C extends BlockEntity> Optional<C> getController(Class<C> type) {
        return getController().filter(type::isInstance).map(type::cast);
    }

    /**
     * Resolves {@code cap} from the controller's block entity rather than this port's own - the generic
     * proxy other parts of the port system (see {@link PortCapabilityHelper}) are built on. Deliberately
     * untyped against any specific capability or controller class: this works for item/fluid/energy
     * handlers alike, and for any controller regardless of its base class.
     *
     * @return empty if this port isn't part of a formed structure, the controller isn't loaded, or the
     *         controller simply doesn't provide {@code cap} on {@code side}.
     */
    public <T> Optional<T> getControllerCapability(BlockCapability<T, Direction> cap, @Nullable Direction side) {
        if (level == null) return Optional.empty();
        return getController().map(controller -> level.getCapability(cap, controller.getBlockPos(), side));
    }

    @Override
    protected void savePart(CompoundTag tag, HolderLookup.Provider registries) {
        if (controllerPos != null) {
            tag.putLong(TAG_CONTROLLER_POS, controllerPos.asLong());
        }
    }

    @Override
    protected void loadPart(CompoundTag tag, HolderLookup.Provider registries) {
        this.controllerPos = tag.contains(TAG_CONTROLLER_POS) ? BlockPos.of(tag.getLong(TAG_CONTROLLER_POS)) : null;
        invalidateCache();
    }
}
