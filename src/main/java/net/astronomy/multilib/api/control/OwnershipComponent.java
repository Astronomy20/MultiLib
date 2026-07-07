package net.astronomy.multilib.api.control;

import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * A small, embeddable helper a dev drops into their own controller block entity to track "who formed
 * this multiblock" and, optionally, gate access to it. MultiLib never blocks anyone by default - the
 * {@link #setAccessPolicy(BiPredicate) access policy} starts out as "everyone allowed", exactly like
 * every other opt-in mechanism in this library; a dev who wants owner-only access has to explicitly
 * install a stricter predicate themselves.
 * <p>
 * Nothing here subscribes to any event or reads world state - it's just a UUID and a predicate the dev
 * consults from their own code (e.g. a menu's {@code stillValid}, or before running an ability).
 */
public final class OwnershipComponent {

    private Optional<UUID> owner = Optional.empty();
    private BiPredicate<OwnershipComponent, Player> accessPolicy = (component, player) -> true;

    public Optional<UUID> getOwner() { return owner; }

    public void setOwner(UUID owner) {
        this.owner = Optional.ofNullable(owner);
    }

    public void setOwner(Optional<UUID> owner) {
        this.owner = owner == null ? Optional.empty() : owner;
    }

    public void clearOwner() {
        this.owner = Optional.empty();
    }

    /**
     * Convenience for the common case of adopting whoever formed the structure (typically called from
     * {@code onStructureFormed}/{@code onFormed}) as the owner. No-ops (leaves the current owner as-is)
     * if the instance was formed anonymously (e.g. by a dispenser), since {@link MultiblockInstance#getFormedBy()}
     * is then empty.
     */
    public void setOwnerFromFormedBy(MultiblockInstance instance) {
        instance.getFormedBy().ifPresent(this::setOwner);
    }

    public boolean isOwner(Player player) {
        return owner.isPresent() && owner.get().equals(player.getUUID());
    }

    /**
     * Installs the predicate consulted by {@link #canAccess(Player)}. Defaults to "everyone allowed" -
     * MultiLib never blocks anyone by default, this is purely an opt-in hook for a dev who wants e.g.
     * owner-only or team/permission-based access.
     */
    public void setAccessPolicy(BiPredicate<OwnershipComponent, Player> accessPolicy) {
        this.accessPolicy = accessPolicy != null ? accessPolicy : (component, player) -> true;
    }

    public boolean canAccess(Player player) {
        return accessPolicy.test(this, player);
    }

    public void save(CompoundTag tag) {
        owner.ifPresent(uuid -> tag.put("owner", NbtUtils.createUUID(uuid)));
    }

    public void load(CompoundTag tag) {
        if (tag.contains("owner", Tag.TAG_INT_ARRAY)) {
            this.owner = Optional.of(NbtUtils.loadUUID(tag.get("owner")));
        } else {
            this.owner = Optional.empty();
        }
    }
}
