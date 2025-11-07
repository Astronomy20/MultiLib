package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.astronomy.multilib.pattern.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;

import java.util.List;

@EventBusSubscriber(modid = MultiLib.MODID)
public class BlockPlaceEvent {

    @SubscribeEvent
    public static void onBlockPlace(EntityPlaceEvent event) {
        var snapshot = event.getBlockSnapshot();
        if (!(snapshot.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = snapshot.getPos();
        Block block = event.getPlacedBlock().getBlock();

        List<PatternManager> patterns = PatternRegistry.getPatternsFor(block);

        for (PatternManager pattern : patterns) {
            BlockPos origin = PatternMatcher.matches(level, pos, pattern);
            if (origin != null) {
                PatternAction action = pattern.getAction();
                if (action != null) {
                    action.onMatch(level, origin);
                }
                break;
            }
        }
    }
}