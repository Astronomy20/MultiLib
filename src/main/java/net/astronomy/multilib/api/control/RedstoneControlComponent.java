package net.astronomy.multilib.api.control;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * A small, embeddable helper a dev drops into their own controller block entity to gate "should this
 * multiblock run right now?" on redstone, without MultiLib wiring any events or ticking anything
 * itself. The dev's block calls {@link #onNeighborChanged(boolean)} from its own {@code neighborChanged}
 * (or equivalent), and their tick logic calls {@link #shouldRun(ServerLevel, BlockPos)} to decide
 * whether to actually do work that tick. Nothing here touches the world except reading the redstone
 * signal at the given position - nothing subscribes to any event bus.
 * <p>
 * {@link RedstoneMode#PULSE} needs edge detection (rising edge = "start"), since a plain signal read
 * can't tell a momentary pulse from "still on since I last checked". {@link #onNeighborChanged(boolean)}
 * latches a single pending pulse on a rising edge; {@link #consumePulse()} (or {@link #shouldRun} itself,
 * when in {@code PULSE} mode) clears the latch on read so a pulse only ever satisfies one check.
 */
public final class RedstoneControlComponent {

    /** How a formed multiblock's "should I run" gate reacts to redstone. */
    public enum RedstoneMode {
        /** Redstone is never consulted; {@link #shouldRun} always allows running. */
        IGNORE,
        /** Only allowed to run while the position has an incoming redstone signal. */
        REQUIRE_HIGH,
        /** Only allowed to run while the position has no incoming redstone signal. */
        REQUIRE_LOW,
        /** Allowed to run for exactly one check per rising-edge pulse; see {@link #consumePulse()}. */
        PULSE
    }

    private RedstoneMode mode = RedstoneMode.IGNORE;
    private boolean pulsePending = false;
    // Not persisted: only used to detect the rising edge between two onNeighborChanged calls within a
    // single load of this component. Worst case after a reload is one missed/extra edge detection,
    // which is harmless for a "run on the next pulse" gate and not worth the extra NBT key.
    private boolean lastPowered = false;

    public RedstoneMode getMode() { return mode; }

    public void setMode(RedstoneMode mode) {
        this.mode = mode;
    }

    /**
     * Whether the dev's controller should run right now, given the current mode. For
     * {@link RedstoneMode#PULSE} this consumes the latched pulse (equivalent to calling
     * {@link #consumePulse()}) so a single pulse only ever authorizes one call.
     */
    public boolean shouldRun(ServerLevel level, BlockPos pos) {
        return switch (mode) {
            case IGNORE -> true;
            case REQUIRE_HIGH -> level.hasNeighborSignal(pos);
            case REQUIRE_LOW -> !level.hasNeighborSignal(pos);
            case PULSE -> consumePulse();
        };
    }

    /**
     * Edge detector the dev calls from their block's {@code neighborChanged} (or wherever they observe
     * the incoming redstone state change). Latches {@link #consumePulse() a pending pulse} on a rising
     * edge (was not powered, now is) while in {@link RedstoneMode#PULSE}; in any other mode this only
     * updates the internal last-known state so a later mode switch to {@code PULSE} doesn't
     * spuriously fire on a signal that was already high before the switch.
     */
    public void onNeighborChanged(boolean powered) {
        if (mode == RedstoneMode.PULSE && powered && !lastPowered) {
            pulsePending = true;
        }
        lastPowered = powered;
    }

    /** Peeks whether a pulse is currently latched, without consuming it. */
    public boolean isPulsePending() {
        return pulsePending;
    }

    /** Consumes and returns whether a pulse was latched; clears the latch either way. */
    public boolean consumePulse() {
        boolean was = pulsePending;
        pulsePending = false;
        return was;
    }

    public void save(CompoundTag tag) {
        tag.putString("redstoneMode", mode.name());
        tag.putBoolean("redstonePulsePending", pulsePending);
    }

    public void load(CompoundTag tag) {
        if (tag.contains("redstoneMode")) {
            try {
                this.mode = RedstoneMode.valueOf(tag.getString("redstoneMode"));
            } catch (IllegalArgumentException e) {
                this.mode = RedstoneMode.IGNORE;
            }
        }
        this.pulsePending = tag.getBoolean("redstonePulsePending");
    }
}
