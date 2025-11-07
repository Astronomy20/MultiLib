package net.astronomy.multilib.pattern.type;

import net.astronomy.multilib.pattern.PatternAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * SummonEntityAction — spawns an entity at the pattern origin, then clears the structure.
 */
public class SummonPattern implements PatternAction {

    private final EntityType<?> entityType;
    private final boolean clearAfterSummon;

    public SummonPattern(EntityType<?> entityType, boolean clearAfterSummon) {
        this.entityType = entityType;
        this.clearAfterSummon = clearAfterSummon;
    }

    @Override
    public void onMatch(ServerLevel level, BlockPos origin) {
        Entity entity = entityType.create(level);
        if (entity != null) {
            entity.moveTo(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0, 0);
            level.addFreshEntity(entity);
        }

        PatternAction.spawnParticles(level, origin);
        PatternAction.playSound(level, origin);
    }
}