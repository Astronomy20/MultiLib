package net.astronomy.multilib.core.devtool;

import net.astronomy.multilib.network.DevScanResultPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Dev-only, Structure Block-style block: places a {@link MultiblockDevBlockEntity} and opens
 * {@link MultiblockDevMenu} on a plain right-click. No other custom interaction - core/activation tagging
 * (roadmap Design 3) is handled by a separate global listener triggered by right-clicking with the
 * dedicated {@link MultiblockDevWrenchItem}, which cancels the interaction before it ever reaches this
 * block, regardless of what's being clicked.
 * <p>
 * Only {@code useWithoutItem} is overridden, not {@code useItemOn}: {@code Block.useItemOn}'s default
 * implementation already returns {@code ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION}, which
 * falls through to {@code useWithoutItem} regardless of what's in the player's hand - so a single
 * override here already covers both the empty-hand and held-item right-click cases.
 * <p>
 * Only ever registered when {@code CommonConfig.DEV_MODE} is true - see {@link MultiblockDevRegistry}.
 */
public class MultiblockDevBlock extends Block implements EntityBlock {

    public MultiblockDevBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return MultiblockDevBlockEntity.create(pos, state);
    }

    /**
     * Turns off every one of this dev-block's three toggles (Detect, Show/Hide List, Render) the instant
     * it's actually broken, not just re-stated ({@code !state.is(newState.getBlock())} is the standard
     * check for that). Detect and List are server-side state that would otherwise silently linger (a
     * player watching this exact block's HUD list would keep seeing a frozen scan forever). Render isn't
     * cleared from here at all - {@link net.astronomy.multilib.client.devtool.MultiblockDevGlowRenderer}
     * self-heals it every frame instead by checking whether the preview's owning dev-block still exists,
     * since a plain break isn't the only way a block can stop existing (explosions, pistons, chunk
     * reload) and a blunt unconditional clear here would also wipe an unrelated dev-block's own preview.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.isClientSide()) {
                if (level.getBlockEntity(pos) instanceof MultiblockDevBlockEntity be) {
                    be.clearClientRenderState();
                }
            } else {
                // Otherwise a broken dev-block that had auto-detect on would linger in the registry
                // forever, and MultiblockTickHandler would keep trying (harmlessly, but pointlessly) to
                // re-scan a position that no longer holds this block.
                MultiblockDevAutoDetectRegistry.unregister(level.dimension(), pos);

                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    net.minecraft.server.MinecraftServer server = serverLevel.getServer();
                    for (net.minecraft.server.level.ServerPlayer player : serverLevel.players()) {
                        boolean wasActive = MultiblockDevListSessionRegistry.clearIfActive(
                                server, player.getUUID(), serverLevel.dimension(), pos);
                        if (wasActive) {
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                                    new net.astronomy.multilib.network.DevListVisibilityPacket(false, pos));
                        }
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Tagging is now triggered by right-clicking with the dedicated dev wrench item (see
        // MultiblockDevTagHandler), which cancels the interaction before this ever runs - so a plain
        // right-click always opens the GUI here, sneaking or not.
        return openMenu(level, pos, player);
    }

    private InteractionResult openMenu(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof MultiblockDevBlockEntity be)) {
            return InteractionResult.FAIL;
        }

        // Registers the tagging session as soon as the GUI is opened, using whatever offset/size is
        // already persisted on the block entity - not only after a Detect. Without this, tagging
        // (roadmap requisito: "non è necessario fare il detect prima del set del core") would never
        // have a session to check against until Detect ran once, defeating the point.
        BoundingBox box = be.getAbsoluteBoundingBox();
        MultiblockDevTagSessionRegistry.set(serverPlayer.getUUID(), new MultiblockDevTagSessionRegistry.Session(
                pos,
                new BlockPos(box.minX(), box.minY(), box.minZ()),
                new BlockPos(box.maxX(), box.maxY(), box.maxZ())));

        serverPlayer.openMenu(new net.minecraft.world.MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.multilib.dev_block");
            }

            @Override
            public net.minecraft.world.inventory.@Nullable AbstractContainerMenu createMenu(
                    int containerId, net.minecraft.world.entity.player.Inventory inventory, Player p) {
                return new MultiblockDevMenu(containerId, inventory, pos);
            }
        }, pos);

        // The menu itself has no way to pull the BE's last scan on its own (no direct BE reference,
        // per the fixed menu contract) - send it explicitly right after opening so the screen shows
        // the last Detect result without the developer needing to click Detect again. Nothing to send
        // if there's no scan yet: the menu's own defaults (success=true, scan=null) already render as
        // "no scan yet" in the screen, so an empty-failure packet isn't needed here.
        be.getLastScan().ifPresent(scan ->
                PacketDistributor.sendToPlayer(serverPlayer, new DevScanResultPacket(pos, true, "", scan)));
        return InteractionResult.CONSUME;
    }
}
