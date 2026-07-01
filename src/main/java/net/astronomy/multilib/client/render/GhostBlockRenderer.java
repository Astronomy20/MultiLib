package net.astronomy.multilib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.client.overlay.AutoPlacePreviewState;
import net.astronomy.multilib.client.overlay.GhostOverlayState;
import net.astronomy.multilib.network.GhostBlockData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MultiLib.MODID, value = Dist.CLIENT)
public class GhostBlockRenderer {

    // Dev debug chat message is rate-limited so it doesn't spam once per frame.
    private static final long DEBUG_MESSAGE_INTERVAL_MS = 1000L;
    private static long lastDebugMessageTime = 0L;

    // RenderType.translucent() bakes its own DepthTestStateShard (LEQUAL) into its CompositeState,
    // which gets re-applied when the buffer is actually drawn at endBatch — silently undoing a plain
    // RenderSystem.disableDepthTest() call around it. A real fix needs the "no depth test" state
    // baked into the RenderType itself, so the WRONG (mismatch) ghosts — the ones that must show
    // through an already-placed wrong block — use this dedicated type instead.
    private static final RenderType GHOST_NO_DEPTH = RenderType.create(
            "multilib_ghost_no_depth",
            net.minecraft.client.renderer.RenderType.translucent().format(),
            VertexFormat.Mode.QUADS,
            1536,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(
                            net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setShaderState(RenderStateShard.RENDERTYPE_TRANSLUCENT_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(true));

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        List<GhostBlockData> overlayBlocks = GhostOverlayState.INSTANCE.isActive()
                ? GhostOverlayState.INSTANCE.getBlocksToRender() : List.of();
        List<GhostBlockData> previewBlocks = AutoPlacePreviewState.INSTANCE.getBlocksToRender();
        if (overlayBlocks.isEmpty() && previewBlocks.isEmpty()) return;

        // The preview is more specific/actionable than a plain "missing" ghost at the same spot, so
        // it takes priority where the two would otherwise overlap.
        List<GhostBlockData> blocks;
        if (previewBlocks.isEmpty()) {
            blocks = overlayBlocks;
        } else if (overlayBlocks.isEmpty()) {
            blocks = previewBlocks;
        } else {
            Set<BlockPos> previewPositions = new HashSet<>();
            for (GhostBlockData g : previewBlocks) previewPositions.add(g.pos());
            blocks = new ArrayList<>(previewBlocks);
            for (GhostBlockData g : overlayBlocks) {
                if (!previewPositions.contains(g.pos())) blocks.add(g);
            }
        }
        if (blocks.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long renderStartNanos = System.nanoTime();

        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        // MISSING/CORE ghosts sit over air or an already-correct block, so normal LEQUAL depth
        // testing against the world is fine (and keeps them properly occluded by anything genuinely
        // in front of them, e.g. the player's own arm or nearer terrain). WRONG ghosts, though, sit
        // on top of a real (incorrect) block that already wrote nearer depth — RenderType.translucent()
        // bakes a LEQUAL depth-test shard into itself that gets re-applied at endBatch regardless of
        // any RenderSystem.disableDepthTest() call wrapped around it, so WRONG ghosts need their own
        // RenderType with depth testing baked off, or they silently lose to the block underneath.
        for (GhostBlockData ghost : blocks) {
            BlockPos pos = ghost.pos();

            poseStack.pushPose();
            poseStack.translate(
                pos.getX() - camPos.x,
                pos.getY() - camPos.y,
                pos.getZ() - camPos.z
            );
            // Render at 80% scale, pivoting around the block's own center (0.5, 0.5, 0.5) so the
            // ghost reads as a "preview" overlay instead of overlapping the real adjacent blocks.
            poseStack.translate(0.5, 0.5, 0.5);
            poseStack.scale(0.8F, 0.8F, 0.8F);
            poseStack.translate(-0.5, -0.5, -0.5);

            RenderType renderType = ghost.status() == GhostBlockData.Status.WRONG
                    ? GHOST_NO_DEPTH
                    : RenderType.translucent();
            VertexConsumer baseConsumer = bufferSource.getBuffer(renderType);

            switch (ghost.status()) {
                case MISSING -> renderGhostBlock(poseStack, baseConsumer, dispatcher, ghost, 1f, 1f, 1f, 0.45f);
                case WRONG -> renderGhostBlock(poseStack, baseConsumer, dispatcher, ghost, 1f, 0.27f, 0.27f, 0.5f);
                // CORE: already-correct core block, highlighted green so it always stands out.
                case CORE -> renderGhostBlock(poseStack, baseConsumer, dispatcher, ghost, 0.3f, 1f, 0.3f, 0.4f);
                // PLACEABLE: the held item can fill this position right now — no color tint, just
                // the plain translucent ghost so the block's real texture reads clearly.
                case PLACEABLE -> renderGhostBlock(poseStack, baseConsumer, dispatcher, ghost, 1f, 1f, 1f, 0.55f);
            }

            poseStack.popPose();
        }

        bufferSource.endBatch(RenderType.translucent());
        bufferSource.endBatch(GHOST_NO_DEPTH);

        if (GhostOverlayState.INSTANCE.isDebugTiming()) {
            long now = System.currentTimeMillis();
            if (now - lastDebugMessageTime >= DEBUG_MESSAGE_INTERVAL_MS) {
                lastDebugMessageTime = now;
                double renderMs = (System.nanoTime() - renderStartNanos) / 1_000_000.0;
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal(String.format(
                            "[MultiLib debug] Ghost overlay render: %.3f ms (%d blocks)",
                            renderMs, blocks.size())), false);
                }
            }
        }
    }

    private static void renderGhostBlock(PoseStack poseStack, VertexConsumer baseConsumer,
                                          BlockRenderDispatcher dispatcher, GhostBlockData ghost,
                                          float tr, float tg, float tb, float alpha) {
        TintedVertexConsumer tinted = new TintedVertexConsumer(baseConsumer, tr, tg, tb, alpha);
        dispatcher.renderBatched(
            ghost.expectedState(),
            ghost.pos(),
            Minecraft.getInstance().level,
            poseStack,
            tinted,
            false,
            Minecraft.getInstance().level.random
        );
    }
}
