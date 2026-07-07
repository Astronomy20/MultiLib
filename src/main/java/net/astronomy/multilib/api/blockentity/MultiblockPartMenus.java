package net.astronomy.multilib.api.blockentity;

import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Lets a controller's own menu (opened via {@link AbstractMultiblockControllerBlock#openMenu}) redirect
 * a player straight to a part block's menu - e.g. a button in the core's GUI for "configure this IO
 * port" instead of making the player walk to it and right-click it themselves. Deliberately just a
 * thin wrapper around vanilla's own {@link ServerPlayer#openMenu(MenuProvider)}: MultiLib doesn't
 * dictate a GUI/menu framework (see {@code AbstractMultiblockControllerBlock#openMenu}, left fully
 * abstract), so this is a mechanism a dev's own screen/packet handler can call, not a UI of its own.
 */
public final class MultiblockPartMenus {

    private MultiblockPartMenus() {}

    /**
     * Opens {@code partPos}'s own menu for {@code player}, if the block entity there provides one.
     *
     * @return {@code false} (no-op) if there's no loaded block entity at {@code partPos}, or it doesn't
     *         implement {@link MenuProvider} - e.g. a plain structural block with no GUI of its own.
     */
    public static boolean openPartMenu(ServerPlayer player, ServerLevel level, BlockPos partPos) {
        BlockEntity be = level.getBlockEntity(partPos);
        if (!(be instanceof MenuProvider menuProvider)) return false;
        player.openMenu(menuProvider);
        return true;
    }

    /**
     * Same as {@link #openPartMenu(ServerPlayer, ServerLevel, BlockPos)}, but first checks that
     * {@code partPos} actually belongs to {@code instance} - a safety net against a caller passing a
     * stale/unrelated position (e.g. from a client-sent packet) instead of one read fresh off the
     * instance itself.
     *
     * @return {@code false} if {@code partPos} isn't part of {@code instance}, in addition to the plain
     *         overload's own reasons for returning {@code false}.
     */
    public static boolean openPartMenu(ServerPlayer player, ServerLevel level, MultiblockInstance instance, BlockPos partPos) {
        if (!instance.contains(partPos)) return false;
        return openPartMenu(player, level, partPos);
    }
}
