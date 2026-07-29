package net.astronomy.multilib.example.donut;

import net.astronomy.multilib.api.MultiLib;
import net.astronomy.multilib.api.definition.FormationMode;
import net.astronomy.multilib.api.definition.RotationMode;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.pattern.PatternProvider;
import net.astronomy.multilib.api.pattern.providers.CompositeProvider;
import net.astronomy.multilib.api.pattern.providers.RevolutionProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

/**
 * Test/demo structure showing off {@link RevolutionProvider}: a donut lying flat in the XZ plane,
 * built by revolving a 3x3 square tube cross section between {@code MIN_RADIUS} and
 * {@code MAX_RADIUS}:
 * <pre>
 * D D D
 * D F D
 * D D D
 * </pre>
 * {@code D} (crust) is the outer ring of the cross section, {@code F} (filling, pink terracotta) is
 * its single centre cell. {@code MIN_RADIUS} is measured to the tube's <em>inner</em> face - i.e. the
 * donut's hole radius - since that's the anchor {@link RevolutionProvider} already uses internally to
 * index into the cross section ({@code localRadius = distance-from-axis - MIN_RADIUS}); {@code
 * MAX_RADIUS} is derived from it plus the section's own width.
 *
 * <p>The crust ingredient repeats at every angle around the ring - a rotationally symmetric shape has
 * no single "front", so using it as {@code .core('O')} would give the matcher one activation candidate
 * per angle, per allowed rotation (see wiki/Rotation-And-Matching.md). Instead the core
 * ({@code multilib:donut_controller}) is unioned in <em>first</em>, at one exact voxel on the outer
 * edge of the tube - {@link CompositeProvider}'s union keeps the earliest writer on overlap, and the
 * landmark's lambda has the default {@code getSize() == (1,1,1)}, so it only ever answers at that
 * single absolute cell. That gives {@code FunctionalMatcher} exactly one activation/core hypothesis
 * instead of one per ring cell.
 */
public class DonutPattern {

    private static final int SECTION_SIZE = 3; // 3x3 cross section
    private static final int HOLE_RADIUS = 3;  // "raggio": distance from the axis to the tube's inner face
    private static final int MIN_RADIUS = HOLE_RADIUS;
    private static final int MAX_RADIUS = MIN_RADIUS + (SECTION_SIZE - 1);
    private static final int HEIGHT = SECTION_SIZE;

    public static void registerAll() {
        BlockIngredient crust = BlockIngredient.of(Blocks.SMOOTH_SANDSTONE);
        BlockIngredient filling = BlockIngredient.of(Blocks.PINK_TERRACOTTA);
        BlockIngredient core = BlockIngredient.of(DonutExampleSetup.CONTROLLER_BLOCK);

        // 3x3 cross section: border cells (touching any edge of the SECTION_SIZE square) are crust,
        // the single centre cell is the filling. RevolutionProvider queries this at
        // (localRadius, y, 0) for every cell within [MIN_RADIUS, MAX_RADIUS] of the ring's centre.
        PatternProvider crossSection = (x, y, z) -> {
            if (x < 0 || x >= SECTION_SIZE || y < 0 || y >= SECTION_SIZE) return null;
            boolean border = x == 0 || x == SECTION_SIZE - 1 || y == 0 || y == SECTION_SIZE - 1;
            return border ? crust : filling;
        };
        PatternProvider ring = new RevolutionProvider(MIN_RADIUS, MAX_RADIUS, HEIGHT, crossSection);

        // The single landmark voxel: outer edge of the tube (outermost crust column) at angle 0
        // (+X from the ring's centre), tube's vertical middle. RevolutionProvider centres the ring on
        // (MAX_RADIUS, MAX_RADIUS) in its own local space, so the outermost point along +X sits at
        // x = MAX_RADIUS + MAX_RADIUS.
        int landmarkX = MAX_RADIUS + MAX_RADIUS;
        int landmarkY = SECTION_SIZE / 2;
        int landmarkZ = MAX_RADIUS;
        PatternProvider landmark = (x, y, z) -> core;

        PatternProvider donut = CompositeProvider.builder()
                .union(landmark, landmarkX, landmarkY, landmarkZ)
                .union(ring)
                .build();

        MultiLib.define(ResourceLocation.fromNamespaceAndPath("multilib", "example_donut"))
                .pattern(donut)
                .key('O', core)
                .core('O')
                .formationMode(FormationMode.AUTOMATIC_AND_WRENCH)
                .rotations(RotationMode.HORIZONTAL)
                .autoPlace().autoPlaceOverlay()
                .build();
    }
}
