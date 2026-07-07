package net.astronomy.multilib.api.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * A ready-made {@code IFluidHandler} tank for a multiblock controller. Extends NeoForge's own
 * {@link FluidTank} template rather than reimplementing fill/drain - this only adds the dirty-marking
 * hook and BE-friendly NBT helpers a controller needs on top, plus a constructor-time fluid validator
 * for structures that should only accept specific fluids (e.g. a smeltery core that rejects water).
 * <p>
 * Typical use: keep one as a field on your {@code AbstractMultiblockControllerBE} subclass, wire it
 * up to {@code setChanged()} via the {@code onChanged} callback, persist it from
 * {@code saveController}/{@code loadController}, and expose it to automation with
 * {@link MultiblockComponentHelper#registerFluid}.
 * <pre>{@code
 * private final FluidTankComponent tank = new FluidTankComponent(16_000, fluid -> !fluid.is(Fluids.WATER), this::setChanged);
 *
 * protected void saveController(CompoundTag tag, HolderLookup.Provider registries) {
 *     tank.save(tag, registries);
 * }
 *
 * protected void loadController(CompoundTag tag, HolderLookup.Provider registries) {
 *     tank.load(tag, registries);
 * }
 * }</pre>
 */
public class FluidTankComponent extends FluidTank {

    private @Nullable Runnable onChanged;

    public FluidTankComponent(int capacity) {
        this(capacity, null, null);
    }

    public FluidTankComponent(int capacity, @Nullable Predicate<FluidStack> validator) {
        this(capacity, validator, null);
    }

    /**
     * @param validator accepts every fluid if {@code null}; otherwise gates both {@code fill} and
     *                  {@link #isFluidValid(FluidStack)}.
     * @param onChanged invoked after every fill/drain that actually moved fluid (not on simulated
     *                   calls). Typically {@code this::setChanged} on the owning BE.
     */
    public FluidTankComponent(int capacity, @Nullable Predicate<FluidStack> validator, @Nullable Runnable onChanged) {
        super(validatedCapacity(capacity), validator != null ? validator : stack -> true);
        this.onChanged = onChanged;
    }

    private static int validatedCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("FluidTankComponent capacity must be positive, was " + capacity);
        }
        return capacity;
    }

    /** Replaces the dirty-marking callback, e.g. if the owning BE wasn't available yet at construction time. */
    public void setOnChanged(@Nullable Runnable onChanged) {
        this.onChanged = onChanged;
    }

    // FluidTank itself already guards this to only fire on a real content change (not simulated
    // fill/drain calls), so no extra simulate-checking is needed here.
    @Override
    protected void onContentsChanged() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    /** Writes the held fluid stack into {@code tag} under a self-namespaced key, via {@link #writeToNBT}. */
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        writeToNBT(registries, tag);
    }

    /** Restores the fluid stack previously written by {@link #save(CompoundTag, HolderLookup.Provider)}. */
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        readFromNBT(registries, tag);
    }
}
