package net.astronomy.multilib.api.ability;

import net.astronomy.multilib.api.blockentity.IMultiblockPart;
import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves all parts of a formed multiblock instance that declare a given {@link MultiblockAbility},
 * so controller logic can ask "give me every energy port" instead of hardcoding a single symbol's
 * position. Read-only - this never places, breaks, or otherwise changes anything.
 */
public final class MultiblockAbilities {

    private MultiblockAbilities() {}

    /**
     * @return every loaded part of {@code instance} that declares {@code ability}, cast to its type.
     *         Unloaded positions are silently skipped rather than forcing a chunk load.
     */
    public static <T> List<T> get(ServerLevel level, MultiblockInstance instance, MultiblockAbility<T> ability) {
        List<T> result = new ArrayList<>();
        for (BlockPos pos : instance.getPositions()) {
            if (!level.isLoaded(pos)) continue;
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof IMultiblockPart part && part.getAbilities().contains(ability) && ability.type().isInstance(be)) {
                result.add(ability.type().cast(be));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Convenience overload that pulls the level and instance out of a {@link MultiblockContext}.
     */
    public static <T> List<T> get(MultiblockContext ctx, MultiblockAbility<T> ability) {
        return get(ctx.level(), ctx.instance(), ability);
    }
}
