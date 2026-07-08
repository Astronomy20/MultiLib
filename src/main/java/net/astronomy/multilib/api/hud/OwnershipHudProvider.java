package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.control.OwnershipComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Opt-in provider that shows the owner of a controller, if the block entity at the instance's core
 * implements {@link HudOwnershipSource}. The owner's name is resolved via the server's
 * {@link GameProfileCache}, falling back to the raw UUID string if no cached profile is found (or the
 * cache itself is unavailable). Register per-definition:
 * {@code MultiblockHudRegistry.register(definitionId, new OwnershipHudProvider())}.
 */
public final class OwnershipHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        BlockPos corePos = ctx.instance().getCorePos().orElse(null);
        if (corePos == null) return;

        BlockEntity be = ctx.level().getBlockEntity(corePos);
        if (!(be instanceof HudOwnershipSource source)) return;

        OwnershipComponent ownership = source.getHudOwnership();
        if (ownership == null) return;

        Optional<UUID> owner = ownership.getOwner();
        if (owner.isEmpty()) return;

        String name = resolveName(ctx, owner.get());
        out.accept(new HudEntry.KeyValue(Component.translatable("multilib.hud.owner"), Component.literal(name)));
    }

    private static String resolveName(HudContext ctx, UUID owner) {
        GameProfileCache cache = ctx.level().getServer().getProfileCache();
        if (cache == null) return owner.toString();
        return cache.get(owner).map(profile -> profile.getName()).orElse(owner.toString());
    }
}
