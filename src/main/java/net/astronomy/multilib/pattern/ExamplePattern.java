package net.astronomy.multilib.pattern;

import net.astronomy.multilib.pattern.type.SummonPattern;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;

public class ExamplePattern {
    public static void registerAll() {
        PatternBuilder builder = PatternManager.pattern()
                .key('D', Blocks.DIAMOND_BLOCK)
                .key('O', Blocks.OBSIDIAN)
                .key('E', Blocks.EMERALD_BLOCK)
                .layer(" D ",
                        "EDO",
                        " D ")
                .allowVerticalRotation(true)
                .allowSideRotation(false)
                .allowUpsideDown(false);

        PatternManager pattern = builder.action(new SummonPattern(EntityType.ZOMBIE, true, builder.build())).build();
    }
}