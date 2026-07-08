package net.astronomy.multilib.api.component;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ContentCache}'s keyed snapshot/restore contract: values roundtrip per key, and restore
 * skips keys missing from the snapshot instead of force-resetting - the property that makes adding
 * a new slot in a mod update safe against old snapshots.
 */
class ContentCacheTest {

    /** Minimal stand-in for a buffer component: one int persisted under "v". */
    private static final class FakeComponent {
        int value;

        FakeComponent(int value) { this.value = value; }

        void save(CompoundTag tag) { tag.putInt("v", value); }
        void load(CompoundTag tag) { value = tag.getInt("v"); }

        ContentCache.Slot slot(String key) {
            return ContentCache.slot(key, this::save, this::load);
        }
    }

    @Test
    void roundtripRestoresEachKeyedSlot() {
        FakeComponent energy = new FakeComponent(1234);
        FakeComponent items = new FakeComponent(7);

        CompoundTag snapshot = ContentCache.snapshot(energy.slot("energy"), items.slot("items"));

        energy.value = 0;
        items.value = 0;
        ContentCache.restore(snapshot, energy.slot("energy"), items.slot("items"));

        assertEquals(1234, energy.value);
        assertEquals(7, items.value);
    }

    @Test
    void restoreIsKeyedNotPositional() {
        FakeComponent a = new FakeComponent(1);
        FakeComponent b = new FakeComponent(2);
        CompoundTag snapshot = ContentCache.snapshot(a.slot("a"), b.slot("b"));

        a.value = 0;
        b.value = 0;
        // Reversed argument order - keys must still land on the right component.
        ContentCache.restore(snapshot, b.slot("b"), a.slot("a"));

        assertEquals(1, a.value);
        assertEquals(2, b.value);
    }

    @Test
    void missingKeyIsSkippedNotReset() {
        FakeComponent existing = new FakeComponent(42);
        CompoundTag snapshot = ContentCache.snapshot(existing.slot("existing"));

        // A slot added after the snapshot was taken: restore must leave it untouched.
        FakeComponent addedLater = new FakeComponent(99);
        existing.value = 0;
        ContentCache.restore(snapshot, existing.slot("existing"), addedLater.slot("added_later"));

        assertEquals(42, existing.value);
        assertEquals(99, addedLater.value, "keys absent from the snapshot must be skipped");
    }
}
