package net.astronomy.multilib.api.aggregate;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * A snapshot of one connected, shape-valid cluster of {@link AggregatableBlockEntity} blocks, as found
 * by {@link AggregationEngine}. Never persisted - it's cheap to recompute from live world state any time
 * a member's neighborhood changes, so there's nothing to keep in sync across saves/reloads/other mods
 * editing the world.
 * <p>
 * A block with no valid neighbors of its own is still a group - just a {@link #size()} 1 singleton of
 * itself, always its own {@link #controller()}.
 */
public record AggregateGroup(ResourceLocation groupId, Set<BlockPos> members, BlockPos controller) {

    public int size() {
        return members.size();
    }

    public boolean isController(BlockPos pos) {
        return controller.equals(pos);
    }

    public boolean isSingleton() {
        return members.size() == 1;
    }
}
