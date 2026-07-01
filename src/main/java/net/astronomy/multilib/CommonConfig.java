package net.astronomy.multilib;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Config values accessible from both client and server (registered as {@link net.neoforged.fml.config.ModConfig.Type#COMMON}).
 */
public class CommonConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue GHOST_OVERLAY_DURATION_SECONDS = BUILDER
            .comment("How long (in seconds) the ghost overlay session stays active. The server tracks "
                    + "the timer and sends a disable packet to the client when it expires.")
            .defineInRange("ghostOverlayDurationSeconds", 10, 1, 3600);

    public static final ModConfigSpec.DoubleValue AUTO_PLACE_SPEED_HELD_ITEM = BUILDER
            .comment("Auto-place repeat speed while holding Ctrl+Right-click with an item in hand, as a "
                    + "multiplier of vanilla's own right-click repeat rate (1.0 = vanilla speed, "
                    + "2.0 = twice as fast, 0.5 = half as fast).")
            .defineInRange("autoPlaceSpeedHeldItem", 1.25, 0.1, 10.0);

    public static final ModConfigSpec.DoubleValue AUTO_PLACE_SPEED_EMPTY_HAND = BUILDER
            .comment("Auto-place repeat speed while holding Ctrl+Right-click with an empty hand, as a "
                    + "multiplier of vanilla's own right-click repeat rate (1.0 = vanilla speed, "
                    + "2.0 = twice as fast, 0.5 = half as fast).")
            .defineInRange("autoPlaceSpeedEmptyHand", 1.25, 0.1, 10.0);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
