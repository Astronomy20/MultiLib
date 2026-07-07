package net.astronomy.multilib.api.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

/**
 * A ready-made {@code IItemHandler} for a multiblock controller. Extends NeoForge's own
 * {@link ItemStackHandler} rather than reimplementing insert/extract - this only adds the
 * dirty-marking hook and BE-friendly NBT helpers a controller needs on top, plus an optional
 * per-slot validator (e.g. an input slot that only accepts ores, or an output slot that rejects
 * external insertion entirely).
 * <p>
 * Typical use: keep one as a field on your {@code AbstractMultiblockControllerBE} subclass, wire it
 * up to {@code setChanged()} via the {@code onChanged} callback, persist it from
 * {@code saveController}/{@code loadController}, and expose it to automation with
 * {@link MultiblockComponentHelper#registerItem}.
 * <pre>{@code
 * private final ItemBufferComponent items = new ItemBufferComponent(9, (slot, stack) -> slot != 0, this::setChanged);
 *
 * protected void saveController(CompoundTag tag, HolderLookup.Provider registries) {
 *     items.save(tag, registries);
 * }
 *
 * protected void loadController(CompoundTag tag, HolderLookup.Provider registries) {
 *     items.load(tag, registries);
 * }
 * }</pre>
 */
public class ItemBufferComponent extends ItemStackHandler {

    private @Nullable Runnable onChanged;
    private @Nullable BiPredicate<Integer, ItemStack> validator;

    public ItemBufferComponent(int slots) {
        this(slots, null, null);
    }

    public ItemBufferComponent(int slots, @Nullable BiPredicate<Integer, ItemStack> validator) {
        this(slots, validator, null);
    }

    /**
     * @param validator accepts every stack in every slot if {@code null}; otherwise gates
     *                  {@link #isItemValid(int, ItemStack)} (and therefore {@code insertItem}).
     * @param onChanged invoked after every insert/extract/direct-set that actually changed a slot
     *                   (not on simulated calls). Typically {@code this::setChanged} on the owning BE.
     */
    public ItemBufferComponent(int slots, @Nullable BiPredicate<Integer, ItemStack> validator, @Nullable Runnable onChanged) {
        super(validatedSlots(slots));
        this.validator = validator;
        this.onChanged = onChanged;
    }

    private static int validatedSlots(int slots) {
        if (slots <= 0) {
            throw new IllegalArgumentException("ItemBufferComponent slot count must be positive, was " + slots);
        }
        return slots;
    }

    /** Replaces the dirty-marking callback, e.g. if the owning BE wasn't available yet at construction time. */
    public void setOnChanged(@Nullable Runnable onChanged) {
        this.onChanged = onChanged;
    }

    /** Replaces the per-slot validator; pass {@code null} to accept everything again. */
    public void setSlotValidator(@Nullable BiPredicate<Integer, ItemStack> validator) {
        this.validator = validator;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return validator == null || validator.test(slot, stack);
    }

    // ItemStackHandler itself already guards this to only fire on a real slot change (not simulated
    // insert/extract calls), so no extra simulate-checking is needed here.
    @Override
    protected void onContentsChanged(int slot) {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    /**
     * Writes the held stacks into {@code tag} under self-namespaced keys, mirroring
     * {@link #serializeNBT(HolderLookup.Provider)} without wrapping it in an extra nesting level.
     */
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag serialized = serializeNBT(registries);
        tag.put("Items", serialized.get("Items"));
        tag.putInt("Size", serialized.getInt("Size"));
    }

    /** Restores the stacks previously written by {@link #save(CompoundTag, HolderLookup.Provider)}. */
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        deserializeNBT(registries, tag);
    }
}
