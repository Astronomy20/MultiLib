package net.astronomy.multilib.example.variants;

import net.astronomy.multilib.api.MultiLib;
import net.astronomy.multilib.api.definition.FormationMode;
import net.astronomy.multilib.api.definition.RotationMode;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

public class ExampleVariantsPattern {
    public static void registerAll() {
        // Variants demo (F12): one id, two alternative shapes sharing every behavioral field.
        //
        // Declaration order is load-bearing: the matcher tries variants in declaration order and
        // stops at the first success, so the LARGER shape must be declared first - "tall" is a
        // strict superset of "compact", and with "compact" declared first a fully-built tall
        // structure would still match as compact, making the wrench upgrade below unreachable.
        // (The first variant also becomes the "primary" geometry shown by ghost overlay/auto-place.)
        //
        // In-game test flow:
        //   1. Place iron - lapis - iron in a row (lapis last) -> forms as variant "compact".
        //   2. Add three iron blocks on top of the row.
        //   3. Right-click the structure with the example wrench -> re-matches as "tall" IN PLACE:
        //      same instance UUID, controller state/contents preserved, no onBroken/onFormed
        //      callbacks - the wrench event reports WrenchResult.VariantChanged (KubeJS status
        //      "variant_changed"), and JEI/REI/EMI show one page per variant.
        MultiLib.define(ResourceLocation.fromNamespaceAndPath("multilib", "example_variants"))
                .key('I', BlockIngredient.of(Blocks.IRON_BLOCK)) // shared keys apply to every variant
                .key('L', BlockIngredient.of(Blocks.LAPIS_BLOCK))
                .variant("tall", v -> v
                        .layer("III")
                        .layer("ILI"))
                .variant("compact", v -> v
                        .layer("ILI"))
                .core('L')
                .formationMode(FormationMode.AUTOMATIC_AND_WRENCH)
                .rotations(RotationMode.HORIZONTAL)
                .build();
    }
}
