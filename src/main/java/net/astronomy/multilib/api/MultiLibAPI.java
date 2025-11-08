package net.astronomy.multilib.api;

import net.astronomy.multilib.pattern.PatternBuilder;
import net.astronomy.multilib.pattern.PatternManager;
import net.astronomy.multilib.pattern.PatternRegistry;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Public entry point for mods wanting to interact with MultiLib's pattern system.
 *
 * Usage:
 *   PatternAPI.createPattern2D()
 *       .key('X', Blocks.XXX)
 *       .layer(" X ", "XXX", " X ")
 *       .action((level, origin) -> { ... })
 *       .build();
 */
public class MultiLibAPI {

    /** Create a new 2D pattern builder */
    public static PatternBuilder pattern() {
        return new PatternBuilder();

    }

    /** Returns all registered patterns */
    public static List<PatternManager> getAllPatterns() {
        return List.copyOf(PatternRegistry.getAllPatterns());
    }

    /** Returns all registered patterns for a specific block*/
    public static List<PatternManager> getPatternsFor(Block block) {
        return PatternRegistry.getPatternsFor(block);
    }
}