package net.astronomy.multilib.pattern;

import net.minecraft.world.level.block.Blocks;

public class ExamplePattern {
    public static void registerAll() {
        PatternManager testPattern = PatternManager.pattern2D()
                .key('D', Blocks.DIAMOND_BLOCK)
                .layer(
                        " D ",
                        "DDD",
                        " D "
                )
                .action((level, origin) -> {
                    System.out.println("WORKED!");
                })
                .build();

        PatternRegistry.register(testPattern, testPattern.getAction());
    }
}