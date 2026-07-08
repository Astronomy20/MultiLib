package net.astronomy.multilib.example;

import net.astronomy.multilib.api.MultiLibAPI;
import net.astronomy.multilib.api.definition.FormationMode;
import net.astronomy.multilib.api.definition.RotationMode;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/**
 * Test/demo structure for the "rigid" {@code mainFace()} ghost-overlay behavior: the core
 * ({@code multilib:example_directional_controller}) has its own placed facing (like a furnace), and
 * the pattern is deliberately asymmetric on all 4 horizontal sides - gold north, diamond east,
 * emerald south, iron west of the core - so the preview's orientation is visually obvious. With
 * {@code .mainFace()} declared on the core's BlockDefinition (see {@link ExampleSetup}), the ghost
 * overlay must always show this cross in the orientation the core is actually facing, never rotating
 * to match the player's look direction the way {@link ExamplePattern}'s non-directional core does.
 * {@code RotationMode.HORIZONTAL} is still allowed here on purpose, to demonstrate that "rigid"
 * preview orientation and "the built structure may be rotated" are independent: the matcher accepts
 * the structure built facing any of the 4 directions, while the *preview* always reflects the core's
 * own facing rather than free-cycling like the rotation-only example does.
 */
public class ExampleDirectionalPattern {
    public static void registerAll() {
        MultiLibAPI.define(ResourceLocation.fromNamespaceAndPath("multilib", "example_directional"))
                .layer(
                        " G ",
                        "IOD",
                        " E "
                )
                .key('G', BlockIngredient.of(Blocks.GOLD_BLOCK))
                .key('D', BlockIngredient.of(Blocks.DIAMOND_BLOCK))
                .key('E', BlockIngredient.of(Blocks.EMERALD_BLOCK))
                .key('I', BlockIngredient.of(Blocks.IRON_BLOCK))
                .key('O', BlockIngredient.of(ExampleSetup.DIRECTIONAL_CONTROLLER_BLOCK))
                .core('O')
                .formationMode(FormationMode.AUTOMATIC_AND_WRENCH)
                .rotations(RotationMode.HORIZONTAL)
                .autoPlace().autoPlaceOverlay()
                // Ghost overlay is on by default (see .ghostOverlay's javadoc) - no call needed here.
                .build();

        // The core's own placed facing (read from its HORIZONTAL_FACING blockstate property) pins the
        // ghost overlay/auto-place preview orientation - see OverlayRequestHandler.extractMainFace.
        MultiLibAPI.block(ExampleSetup.DIRECTIONAL_CONTROLLER_BLOCK).mainFace().build();
    }
}
