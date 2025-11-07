package net.astronomy.multilib.pattern;

import java.util.*;
import net.minecraft.world.level.block.Block;

/**
 * PatternRegistry — manages all pattern registrations and actions
 */
public class PatternRegistry {

    private static final Map<PatternManager, PatternAction> PATTERNS = new HashMap<>();

    /** Register a pattern with its action */
    public static void register(PatternManager pattern, PatternAction action) {
        PATTERNS.put(pattern, action);
    }

    /** Return all registered patterns */
    public static Collection<PatternManager> getAllPatterns() {
        return PATTERNS.keySet();
    }

    /** Get all patterns that contain the placed block */
    public static List<PatternManager> getPatternsFor(Block block) {
        List<PatternManager> result = new ArrayList<>();
        for (PatternManager pattern : PATTERNS.keySet()) {
            if (pattern.isKeyBlock(block)) {
                result.add(pattern);
            }
        }
        return result;
    }
}