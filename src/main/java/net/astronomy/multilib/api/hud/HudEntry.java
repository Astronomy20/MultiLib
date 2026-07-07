package net.astronomy.multilib.api.hud;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

import java.util.Optional;

/**
 * A single, viewer-agnostic piece of information a {@link MultiblockHudProvider} wants to show for a
 * formed multiblock. Kept as a small closed set of shapes - rather than a raw string or a pre-rendered
 * widget - so each viewer adapter ({@code compat/jade}, {@code compat/top}, ...) can render every entry
 * with its own native UI element instead of MultiLib picking one look that might clash with the
 * viewer's own theme.
 * <p>
 * Instances round-trip through NBT (see {@link #save}/{@link #load}) so a server-side gather (see
 * {@link MultiblockHudRegistry#gatherEntries}) can be shipped to the client inside a viewer's own
 * server-data payload (e.g. Jade's {@code IServerDataProvider}) and rendered there. {@link Component}s
 * are (de)serialized with {@link ComponentSerialization#CODEC} over {@link NbtOps}, wrapped in the
 * {@code RegistryOps} built from the caller's {@link HolderLookup.Provider} - the same registry-aware
 * machinery Minecraft itself uses to (de)serialize chat components, needed because a {@link Component}
 * can reference registry-backed data that only resolves with the current registries in hand.
 */
public sealed interface HudEntry {

    byte TYPE_TEXT = 0;
    byte TYPE_PROGRESS = 1;
    byte TYPE_KEY_VALUE = 2;

    /** Writes this entry into {@code tag}, tagging it with a type discriminator so {@link #load} can dispatch. */
    void save(CompoundTag tag, HolderLookup.Provider registries);

    /** A single line of plain text - e.g. a definition's display name, or a static status line. */
    record Text(Component text) implements HudEntry {
        @Override
        public void save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putByte("type", TYPE_TEXT);
            tag.put("text", encode(text, registries));
        }
    }

    /**
     * A progress bar: {@code fraction} (expected in {@code [0, 1]}, clamped display-side by whichever
     * viewer renders it) plus a label such as {@code "Smelting"} or {@code "42 of 60 blocks"}.
     */
    record Progress(float fraction, Component label) implements HudEntry {
        @Override
        public void save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putByte("type", TYPE_PROGRESS);
            tag.putFloat("fraction", fraction);
            tag.put("label", encode(label, registries));
        }
    }

    /** A "key: value" line - e.g. a resolved tier name, energy stored, or an owner's name. */
    record KeyValue(Component key, Component value) implements HudEntry {
        @Override
        public void save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putByte("type", TYPE_KEY_VALUE);
            tag.put("key", encode(key, registries));
            tag.put("value", encode(value, registries));
        }
    }

    /**
     * Restores whatever {@link #save} wrote, or empty if {@code tag} isn't a recognized entry (e.g.
     * written by a future MultiLib version this client doesn't know about yet) or fails to decode.
     */
    static Optional<HudEntry> load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("type")) return Optional.empty();
        byte type = tag.getByte("type");
        try {
            return switch (type) {
                case TYPE_TEXT -> Optional.of(new Text(decode(tag.get("text"), registries)));
                case TYPE_PROGRESS -> Optional.of(new Progress(
                        tag.getFloat("fraction"), decode(tag.get("label"), registries)));
                case TYPE_KEY_VALUE -> Optional.of(new KeyValue(
                        decode(tag.get("key"), registries), decode(tag.get("value"), registries)));
                default -> Optional.empty();
            };
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Tag encode(Component component, HolderLookup.Provider registries) {
        return ComponentSerialization.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), component)
                .getOrThrow();
    }

    private static Component decode(Tag tag, HolderLookup.Provider registries) {
        return ComponentSerialization.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .getOrThrow();
    }
}
