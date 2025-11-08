package net.astronomy.multilib.pattern.type;

import net.astronomy.multilib.pattern.PatternAction;
import net.astronomy.multilib.pattern.PatternManager;
import net.minecraft.world.level.block.Blocks;

public class ExamplePattern {
    public static void registerAll() {
        PatternManager pattern = PatternManager.pattern()
                .key('D', Blocks.DIAMOND_BLOCK)
                .key('O', Blocks.OBSIDIAN)
                .key('E', Blocks.EMERALD_BLOCK)
                .layer(" D ",
                        "EDO",
                        " D ")
                .allowVerticalRotation(true)
                .allowSideRotation(false)
                .allowUpsideDown(false)
                .action((level, origin) -> {
                    PatternAction.playSound(level, origin);
                    PatternAction.spawnParticles(level, origin);
                })
                .build();
    }
}