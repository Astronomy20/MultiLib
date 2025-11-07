package net.astronomy.multilib.pattern;

import net.astronomy.multilib.pattern.type.SummonPattern;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;

public class ExamplePattern {
    PatternManager altarPattern = PatternManager.pattern3D()
            .key('O', Blocks.OBSIDIAN)
            .key('D', Blocks.DIAMOND_BLOCK)
            .layer(
                    "OOO",
                    "ODO",
                    "OOO"
            )
            .action(new SummonPattern(EntityType.LIGHTNING_BOLT, true))
            .build();
}