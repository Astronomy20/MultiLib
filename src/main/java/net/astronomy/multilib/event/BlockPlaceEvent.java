package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.astronomy.multilib.pattern.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.List;

@EventBusSubscriber(modid = MultiLib.MODID)
public class BlockPlaceEvent {

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        Block block = event.getPlacedBlock().getBlock();

        List<PatternManager> patterns = PatternRegistry.getPatternsFor(block);

        for (PatternManager pattern : patterns) {
            PatternMatcher.MatchResult result = PatternMatcher.matches(level, pos, pattern);
            if (result != null) {
                PatternAction action = pattern.getAction();
                if (action != null) {
                    action.onMatch(level, result.origin(), result.transform());
                }
                break;
            }
        }
    }
}