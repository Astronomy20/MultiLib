package net.astronomy.multilib.client;

import net.neoforged.neoforge.common.ModConfigSpec;

// Registered as CLIENT so this persists across game sessions without leaking into server config.
public class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // The in-world ghost overlay never rotates (it's anchored to the structure's actual placement) -
    // this only controls the JEI recipe page's standalone 3D preview model.
    public static final ModConfigSpec.BooleanValue JEI_PREVIEW_AUTO_ROTATE = BUILDER
            .comment("Whether the JEI multiblock recipe page's 3D preview model auto-rotates by default.")
            .define("jeiPreviewAutoRotate", true);

    public static final ModConfigSpec.ConfigValue<String> CATEGORY_ICON = BUILDER
            .comment("Item ID used as the icon for the multiblock category tab in JEI/REI/EMI (e.g. smelting, crafting, brewing).",
                    "Empty by default. When set, this overrides whatever icon a consuming mod registered via",
                    "MultiLibClient.setCategoryIcon(...) in Java, so leave it empty unless a player/dev wants to",
                    "force a specific icon.")
            .define("categoryIcon", "");

    public static final ModConfigSpec SPEC = BUILDER.build();
}
