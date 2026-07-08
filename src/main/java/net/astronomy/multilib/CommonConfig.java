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

    public static final ModConfigSpec.BooleanValue DEV_MODE = BUILDER
            .comment("Enables debugging-facing feedback: the ghost overlay countdown chat message "
                    + "(shown for every ghost-overlay-enabled structure) and the wrench state "
                    + "feedback messages (formed/already-formed/failed/etc.). Off by default since "
                    + "this is developer/debugging output, not meant for regular players.")
            .define("devMode", false);

    public static final ModConfigSpec.ConfigValue<String> DEVTOOL_NAMESPACE = BUILDER
            .comment("The single fixed identifier shared by every Multiblock Dev Block export - matches "
                    + "standard Minecraft ResourceLocation convention (namespace:path, e.g. \"minecraft:"
                    + "diamond_sword\"): namespace is the constant, mod-like identifier, path is what varies "
                    + "per object. What's typed into the GUI's own \"Path\" field is the path half, and is "
                    + "what tells two different multiblocks' output files apart. Used for: (1) the "
                    + "ResourceLocation namespace half of every export's id, (2) the translation key's "
                    + "middle segment (multiblock.<namespace>.<path> - the GUI's Display Name field never "
                    + "enters the key, only the key's *value*), (3) the JSON export's generated datapack "
                    + "folder name (world datapacks/<namespace>, or data/<namespace> under an explicit "
                    + "devtoolJsonOutputDir), and (4) the KubeJS export's subfolder name under "
                    + "kubejs/server_scripts/<namespace>.")
            .define("devtoolNamespace", "multilib");

    public static final ModConfigSpec.ConfigValue<String> DEVTOOL_JAVA_OUTPUT_DIR = BUILDER
            .comment("Output directory for the Multiblock Dev Block's \"Export: Java\" button, relative to "
                    + "the game directory (or an absolute path).")
            .define("devtoolJavaOutputDir", "config/multilib/output");

    public static final ModConfigSpec.ConfigValue<String> DEVTOOL_KUBEJS_OUTPUT_DIR = BUILDER
            .comment("Output directory for the Multiblock Dev Block's \"Export: KubeJS\" button, relative to "
                    + "the game directory (or an absolute path). Left empty (the default), "
                    + "kubejs/server_scripts/<devtoolNamespace> is used instead.")
            .define("devtoolKubeJsOutputDir", "");

    public static final ModConfigSpec.ConfigValue<String> DEVTOOL_JSON_OUTPUT_DIR = BUILDER
            .comment("Base directory for the Multiblock Dev Block's \"Export: JSON\" button - the "
                    + "data/<namespace>/multiblocks/<path>.json tree is created under this. Relative to "
                    + "the game directory (or an absolute path). Left empty (the default), the current "
                    + "world's own datapacks/<devtoolNamespace> folder is used instead (the scaffold is "
                    + "created automatically if missing) - set this only to force a different target, e.g. "
                    + "your own mod's src/main/resources.")
            .define("devtoolJsonOutputDir", "");

    public static final ModConfigSpec.IntValue DEVTOOL_AUTO_DETECT_INTERVAL_TICKS = BUILDER
            .comment("How often (in ticks) a Multiblock Dev Block with auto-detect switched on gets "
                    + "re-scanned. 20 ticks = 1 real-world second.")
            .defineInRange("devtoolAutoDetectIntervalTicks", 10, 1, 1200);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
