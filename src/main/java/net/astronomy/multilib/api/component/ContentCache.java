package net.astronomy.multilib.api.component;

import net.minecraft.nbt.CompoundTag;

import java.util.function.Consumer;

/**
 * Lets a formed structure's buffered contents (energy, fluid, items - or any other NBT-serializable
 * component) survive an unform/reform cycle, Mekanism-style, instead of the buffers resetting to
 * empty every time the structure is (re)built. Deliberately keyed rather than positional: components
 * are named ({@link #slot}), so a dev adding/removing/reordering fields on their controller BE between
 * versions doesn't silently shuffle a saved snapshot onto the wrong component - an unmatched key is
 * just skipped on restore.
 * <p>
 * This has no opinion on where the snapshot tag lives between {@code onBroken} and the next
 * {@code onFormed} - stash it on an instance field of the BE (survives as long as the BE itself does,
 * e.g. a quick rebuild), write it into the BE's own NBT so it survives a save/reload too, or hand it
 * off elsewhere entirely. That choice belongs to the consuming mod, not the library.
 * <pre>{@code
 * public class MyControllerBE extends AbstractMultiblockControllerBE {
 *     private final EnergyBufferComponent energy = new EnergyBufferComponent(100_000, 1000, 1000, this::setChanged);
 *     private final FluidTankComponent tank = new FluidTankComponent(16_000, null, this::setChanged);
 *     private CompoundTag cachedContents;
 *
 *     protected void onBroken(MultiblockBrokenContext ctx) {
 *         cachedContents = ContentCache.snapshot(
 *                 ContentCache.slot("energy", energy::save, energy::load),
 *                 ContentCache.slot("tank", t -> tank.save(t, ctx.level().registryAccess()),
 *                                           t -> tank.load(t, ctx.level().registryAccess())));
 *     }
 *
 *     protected void onFormed(MultiblockFormedContext ctx) {
 *         if (cachedContents != null) {
 *             ContentCache.restore(cachedContents,
 *                     ContentCache.slot("energy", energy::save, energy::load),
 *                     ContentCache.slot("tank", t -> tank.save(t, ctx.level().registryAccess()),
 *                                               t -> tank.load(t, ctx.level().registryAccess())));
 *             cachedContents = null;
 *         }
 *     }
 * }
 * }</pre>
 */
public final class ContentCache {

    private ContentCache() {}

    /**
     * One named component to snapshot/restore. {@code save}/{@code load} are almost always method
     * references onto a buffer component - {@code energy::save}, or a lambda closing over a
     * {@code HolderLookup.Provider} for components (fluid/item) whose NBT round-trip needs one, e.g.
     * {@code t -> tank.save(t, registries)}.
     */
    public record Slot(String key, Consumer<CompoundTag> save, Consumer<CompoundTag> load) {}

    /** Convenience factory so call sites read {@code ContentCache.slot(...)} instead of {@code new ContentCache.Slot(...)}. */
    public static Slot slot(String key, Consumer<CompoundTag> save, Consumer<CompoundTag> load) {
        return new Slot(key, save, load);
    }

    /**
     * Serializes every given {@code slot} into its own nested tag, keyed by {@link Slot#key()}, so
     * unrelated slots can never collide or overwrite each other's data.
     */
    public static CompoundTag snapshot(Slot... slots) {
        CompoundTag tag = new CompoundTag();
        for (Slot slot : slots) {
            CompoundTag nested = new CompoundTag();
            slot.save().accept(nested);
            tag.put(slot.key(), nested);
        }
        return tag;
    }

    /**
     * Restores every given {@code slot} whose key is present in {@code tag}. A slot with no matching
     * key is left untouched (its component keeps whatever state it already had) rather than being
     * force-reset - handles both "this component didn't exist when the snapshot was taken" and
     * "nothing was ever saved" identically.
     */
    public static void restore(CompoundTag tag, Slot... slots) {
        for (Slot slot : slots) {
            if (tag.contains(slot.key())) {
                slot.load().accept(tag.getCompound(slot.key()));
            }
        }
    }
}
