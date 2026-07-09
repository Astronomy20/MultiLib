package net.astronomy.multilib.core.registry;

import net.astronomy.multilib.api.tool.IMultiblockWrench;
import net.minecraft.world.item.Item;

import java.util.HashSet;
import java.util.Set;

/**
 * Items registered here are treated as wrenches by {@code WrenchInteractionHandler} exactly like an
 * {@link IMultiblockWrench}-implementing Item, without needing an actual Java subclass - the entry
 * point for this is {@code MultiLib.registerWrenchItem}, meant for data-driven/scripted items
 * (e.g. KubeJS) that can't implement a custom interface.
 */
public final class WrenchItemRegistry {
    private static final Set<Item> ITEMS = new HashSet<>();

    private WrenchItemRegistry() {}

    public static void register(Item item) {
        ITEMS.add(item);
    }

    public static boolean isWrench(Item item) {
        return item instanceof IMultiblockWrench || ITEMS.contains(item);
    }
}
