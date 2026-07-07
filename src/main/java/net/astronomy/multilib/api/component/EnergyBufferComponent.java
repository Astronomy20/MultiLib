package net.astronomy.multilib.api.component;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.energy.EnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * A ready-made {@code IEnergyStorage} for a multiblock controller, so a modder doesn't have to
 * hand-roll energy buffering (the single biggest boilerplate gap compared to GregTech/Mekanism-style
 * multiblocks). Extends NeoForge's own {@link EnergyStorage} reference implementation rather than
 * reinventing the receive/extract math - this only adds the two things a controller BE actually
 * needs on top: a dirty-marking hook and BE-friendly NBT helpers.
 * <p>
 * Typical use: keep one as a field on your {@code AbstractMultiblockControllerBE} subclass, wire it
 * up to {@code setChanged()} via the {@code onChanged} callback, persist it from
 * {@code saveController}/{@code loadController}, and expose it to automation with
 * {@link MultiblockComponentHelper#registerEnergy}.
 * <pre>{@code
 * private final EnergyBufferComponent energy = new EnergyBufferComponent(100_000, 1000, 1000, this::setChanged);
 *
 * protected void saveController(CompoundTag tag, HolderLookup.Provider registries) {
 *     energy.save(tag);
 * }
 *
 * protected void loadController(CompoundTag tag, HolderLookup.Provider registries) {
 *     energy.load(tag);
 * }
 * }</pre>
 */
public class EnergyBufferComponent extends EnergyStorage {

    private @Nullable Runnable onChanged;

    public EnergyBufferComponent(int capacity, int maxReceive, int maxExtract) {
        this(capacity, maxReceive, maxExtract, null);
    }

    /**
     * @param onChanged invoked after every insertion/extraction that actually moved energy (not on
     *                   simulated calls). Typically {@code this::setChanged} on the owning BE, so the
     *                   chunk gets marked dirty without the BE having to override every accessor itself.
     */
    public EnergyBufferComponent(int capacity, int maxReceive, int maxExtract, @Nullable Runnable onChanged) {
        super(validatedCapacity(capacity), maxReceive, maxExtract, 0);
        this.onChanged = onChanged;
    }

    private static int validatedCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("EnergyBufferComponent capacity must be positive, was " + capacity);
        }
        return capacity;
    }

    /** Replaces the dirty-marking callback, e.g. if the owning BE wasn't available yet at construction time. */
    public void setOnChanged(@Nullable Runnable onChanged) {
        this.onChanged = onChanged;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        int received = super.receiveEnergy(toReceive, simulate);
        if (!simulate && received > 0) {
            fireChanged();
        }
        return received;
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        int extracted = super.extractEnergy(toExtract, simulate);
        if (!simulate && extracted > 0) {
            fireChanged();
        }
        return extracted;
    }

    private void fireChanged() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    /**
     * Writes the current stored amount into {@code tag} under a self-namespaced key. Capacity and
     * transfer limits aren't persisted - those are wiring decided by the dev's code at construction
     * time, not runtime state.
     */
    public void save(CompoundTag tag) {
        tag.putInt("Energy", getEnergyStored());
    }

    /** Restores the stored amount previously written by {@link #save(CompoundTag)}. No-ops on a fresh/empty tag. */
    public void load(CompoundTag tag) {
        this.energy = tag.contains("Energy") ? Math.min(capacity, tag.getInt("Energy")) : 0;
    }
}
