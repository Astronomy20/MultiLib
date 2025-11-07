package net.astronomy.multilib.pattern;

import net.astronomy.multilib.pattern.type.SummonPattern;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;

public class ExamplePattern {
    public static void registerAll() {
        PatternBuilder builder = PatternManager.pattern2D()
                .key('D', Blocks.DIAMOND_BLOCK)
                .key('O', Blocks.OBSIDIAN)
                .layer(" D ",
                        "DDO",
                        " D ")
                .allowVerticalRotation(true);

        PatternManager pattern = builder.action(new SummonPattern(EntityType.ZOMBIE, true, builder.build())).build();
    }
}