package net.astronomy.multilib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.astronomy.multilib.MultiLib;
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

import java.util.List;

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
        if (!GhostOverlayState.INSTANCE.isActive()) return;

        List<GhostBlockData> blocks = GhostOverlayState.INSTANCE.getBlocksToRender();
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

    /** Wraps a VertexConsumer and multiplies each vertex's RGBA by fixed tint factors. */
    private static final class TintedVertexConsumer implements VertexConsumer {

        private final VertexConsumer delegate;
        private final float tr, tg, tb, ta;

        TintedVertexConsumer(VertexConsumer delegate, float tr, float tg, float tb, float ta) {
            this.delegate = delegate;
            this.tr = tr;
            this.tg = tg;
            this.tb = tb;
            this.ta = ta;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(
                Math.min(255, (int)(red * tr)),
                Math.min(255, (int)(green * tg)),
                Math.min(255, (int)(blue * tb)),
                Math.min(255, (int)(alpha * ta))
            );
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            delegate.setNormal(nx, ny, nz);
            return this;
        }
    }
}
