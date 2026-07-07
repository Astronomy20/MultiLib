package net.astronomy.multilib.client.devtool;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.astronomy.multilib.MultiLib;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.OptionalDouble;

/**
 * Draws two kinds of wireframe outline, both with depth testing disabled so they stay visible through
 * intervening blocks (the "glow" effect from the phase-10 design doc, Design 4 - same technique family
 * as {@code GhostBlockRenderer}'s {@code GHOST_NO_DEPTH} render type for WRONG ghost blocks):
 * <ul>
 *     <li>A unit-cube outline around every position currently tagged core/activation in
 *     {@link ClientMultiblockDevTagState}.</li>
 *     <li>A scaled box outline around the area currently previewed via the Screen's "Render" button,
 *     from {@link ClientMultiblockDevAreaPreviewState} - purely client-side, no server round-trip,
 *     since offset/size are already known from whatever's typed in the GUI.</li>
 * </ul>
 * <p>
 * {@code RenderType.translucent()} (used for quads there) bakes a LEQUAL depth-test shard into its
 * {@code CompositeState} that gets silently re-applied at {@code endBatch}, regardless of any
 * {@code RenderSystem.disableDepthTest()} wrapped around the draw calls - so, exactly like that class,
 * "no depth test" has to be baked into the {@link RenderType} itself. Here the geometry is lines, not
 * quads, so this uses {@link VertexFormat.Mode#LINES} with {@link DefaultVertexFormat#POSITION_COLOR_NORMAL}
 * (the same vertex format vanilla's own {@code RenderType.lines()} uses) and
 * {@link RenderStateShard#RENDERTYPE_LINES_SHADER}, instead of the textured quad shader
 * {@code GhostBlockRenderer} needs for rendering actual block models.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MultiLib.MODID, value = Dist.CLIENT)
public class MultiblockDevGlowRenderer {

    private static final RenderType GLOW_LINES_NO_DEPTH = RenderType.create(
            "multilib_dev_tag_glow_no_depth",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(3.0)))
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    // core -> green; activation (duplicate fallback) -> blue/azure. Full alpha since this is a thin
    // outline, not a filled ghost block - low alpha would make it nearly invisible as a line.
    private static final float CORE_R = 0.15f, CORE_G = 0.95f, CORE_B = 0.15f;
    private static final float ACTIVATION_R = 0.2f, ACTIVATION_G = 0.55f, ACTIVATION_B = 1f;
    // Area preview ("Render" button) -> orange, visually distinct from the green/blue tag outlines.
    private static final float PREVIEW_R = 1f, PREVIEW_G = 0.55f, PREVIEW_B = 0.1f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        ClientMultiblockDevAreaPreviewState.Box previewBox = ClientMultiblockDevAreaPreviewState.get();
        // Self-heals every frame instead of relying solely on MultiblockDevBlock#onRemove firing at the
        // right moment to clear this - that hook covers a plain break, but not every way a block can stop
        // existing (an explosion, a piston pulling it away, chunk unload/reload with stale client state).
        // A frame or two of the preview lingering after the block is actually gone is an acceptable cost
        // for not needing every possible removal path to remember to clear it.
        if (previewBox != null
                && !(mc.level.getBlockEntity(previewBox.ownerPos()) instanceof net.astronomy.multilib.core.devtool.MultiblockDevBlockEntity)) {
            ClientMultiblockDevAreaPreviewState.clear();
            previewBox = null;
        }

        // The core/activation glow is tied to the "Render" toggle rather than always showing whenever a
        // tag exists - turning the area preview off is meant to hide every dev-block render this screen
        // is responsible for, not just the area box itself.
        Collection<ClientMultiblockDevTagState.TagInfo> tags =
                previewBox == null ? java.util.List.of() : ClientMultiblockDevTagState.getAll();
        if (tags.isEmpty() && previewBox == null) return;

        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        for (ClientMultiblockDevTagState.TagInfo tag : tags) {
            float r = tag.isCore() ? CORE_R : ACTIVATION_R;
            float g = tag.isCore() ? CORE_G : ACTIVATION_G;
            float b = tag.isCore() ? CORE_B : ACTIVATION_B;

            // Outline every occurrence of the tagged block type in the area, not just the one clicked -
            // makes it obvious at a glance which/how many positions a duplicated block type covers.
            for (BlockPos pos : tag.positions()) {
                poseStack.pushPose();
                poseStack.translate(
                        pos.getX() - camPos.x,
                        pos.getY() - camPos.y,
                        pos.getZ() - camPos.z
                );
                // Re-fetched right before use rather than cached once at the top of the method: drawLabel
                // below queues glyphs into this same BufferSource via Font.drawInBatch(..., SEE_THROUGH, ...),
                // which ends up flushing/ending the lines builder as a side effect of how it manages draw
                // order for "through walls" text. Reusing a VertexConsumer obtained before that flush threw
                // "IllegalStateException: Not building!" the moment a tag (and therefore its label) existed
                // and any further box outline was drawn afterward - getBuffer() is cheap and always returns
                // a valid consumer for this render type regardless of any prior flush.
                drawBoxOutline(poseStack, bufferSource.getBuffer(GLOW_LINES_NO_DEPTH), 1f, 1f, 1f, r, g, b, 1f);
                poseStack.popPose();
            }

            if (!tag.positions().isEmpty()) {
                drawLabel(poseStack, camera, camPos, bufferSource, tag);
            }
        }

        if (previewBox != null) {
            BlockPos min = previewBox.min();
            BlockPos max = previewBox.max();
            float sizeX = max.getX() - min.getX() + 1;
            float sizeY = max.getY() - min.getY() + 1;
            float sizeZ = max.getZ() - min.getZ() + 1;

            poseStack.pushPose();
            poseStack.translate(
                    min.getX() - camPos.x,
                    min.getY() - camPos.y,
                    min.getZ() - camPos.z
            );
            drawBoxOutline(poseStack, bufferSource.getBuffer(GLOW_LINES_NO_DEPTH), sizeX, sizeY, sizeZ, PREVIEW_R, PREVIEW_G, PREVIEW_B, 1f);
            poseStack.popPose();
        }

        bufferSource.endBatch(GLOW_LINES_NO_DEPTH);
        // Font.drawInBatch queues into whatever RenderType the font's glyph atlas uses internally (not
        // ours to name) - a plain no-arg endBatch() flushes every buffer still pending, this one included.
        bufferSource.endBatch();
    }

    /**
     * Draws a billboarded (camera-facing) text label above the first tagged position, reading e.g.
     * "Iron Block (Core)" / "Iron Block (Activation)" in the same color as that tag's outline - same
     * "SEE_THROUGH" display mode as the outline itself (renders through walls), same standard technique
     * vanilla uses for entity name tags (rotate by the camera's own quaternion, then scale down since
     * text is drawn at a much larger native pixel size than a block).
     */
    private static void drawLabel(PoseStack poseStack, Camera camera, Vec3 camPos,
                                   MultiBufferSource.BufferSource bufferSource, ClientMultiblockDevTagState.TagInfo tag) {
        BlockPos labelPos = tag.positions().get(0);
        String text = tag.blockName() + " (" + (tag.isCore() ? "Core" : "Activation") + ")";
        int color = tag.isCore()
                ? (Math.round(CORE_R * 255f) << 16) | (Math.round(CORE_G * 255f) << 8) | Math.round(CORE_B * 255f)
                : (Math.round(ACTIVATION_R * 255f) << 16) | (Math.round(ACTIVATION_G * 255f) << 8) | Math.round(ACTIVATION_B * 255f);

        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(
                labelPos.getX() + 0.5 - camPos.x,
                labelPos.getY() + 1.4 - camPos.y,
                labelPos.getZ() + 0.5 - camPos.z
        );
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f pose = poseStack.last().pose();
        float width = font.width(text);
        font.drawInBatch(text, -width / 2f, 0f, color | 0xFF000000, false, pose, bufferSource,
                Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        poseStack.popPose();
    }

    /**
     * Draws the 12 edges of the box {@code [0,0,0]-[sx,sy,sz]} in the pose stack's current local space
     * (a unit cube when {@code sx=sy=sz=1}, used for single-block tag outlines; an arbitrary-size box
     * for the area preview).
     */
    private static void drawBoxOutline(PoseStack poseStack, VertexConsumer consumer,
                                        float sx, float sy, float sz,
                                        float r, float g, float b, float alpha) {
        // Bottom face (y = 0)
        line(poseStack, consumer, 0, 0, 0, sx, 0, 0, r, g, b, alpha);
        line(poseStack, consumer, sx, 0, 0, sx, 0, sz, r, g, b, alpha);
        line(poseStack, consumer, sx, 0, sz, 0, 0, sz, r, g, b, alpha);
        line(poseStack, consumer, 0, 0, sz, 0, 0, 0, r, g, b, alpha);
        // Top face (y = sy)
        line(poseStack, consumer, 0, sy, 0, sx, sy, 0, r, g, b, alpha);
        line(poseStack, consumer, sx, sy, 0, sx, sy, sz, r, g, b, alpha);
        line(poseStack, consumer, sx, sy, sz, 0, sy, sz, r, g, b, alpha);
        line(poseStack, consumer, 0, sy, sz, 0, sy, 0, r, g, b, alpha);
        // Vertical edges
        line(poseStack, consumer, 0, 0, 0, 0, sy, 0, r, g, b, alpha);
        line(poseStack, consumer, sx, 0, 0, sx, sy, 0, r, g, b, alpha);
        line(poseStack, consumer, sx, 0, sz, sx, sy, sz, r, g, b, alpha);
        line(poseStack, consumer, 0, 0, sz, 0, sy, sz, r, g, b, alpha);
    }

    // This project's VertexConsumer mapping has no PoseStack.Pose-aware overloads (see
    // TintedVertexConsumer / MultiblockStructurePreviewRenderer, both of which only ever call the
    // plain addVertex(x,y,z)/setColor(int,int,int,int)/setNormal(x,y,z) - any pose transform needs to
    // be applied by hand here, by transforming through the PoseStack's current matrices ourselves
    // instead of relying on the consumer to do it.
    private static void line(PoseStack poseStack, VertexConsumer consumer,
                              float x1, float y1, float z1, float x2, float y2, float z2,
                              float r, float g, float b, float alpha) {
        Matrix4f matrix = poseStack.last().pose();
        float nx = x2 - x1, ny = y2 - y1, nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 1.0e-4f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        Vector3f normal = new Vector3f(nx, ny, nz);
        poseStack.last().normal().transform(normal);

        Vector4f p1 = matrix.transform(new Vector4f(x1, y1, z1, 1f));
        Vector4f p2 = matrix.transform(new Vector4f(x2, y2, z2, 1f));

        int red = Math.round(r * 255f), green = Math.round(g * 255f),
                blue = Math.round(b * 255f), alphaInt = Math.round(alpha * 255f);

        consumer.addVertex(p1.x(), p1.y(), p1.z())
                .setColor(red, green, blue, alphaInt)
                .setNormal(normal.x(), normal.y(), normal.z());
        consumer.addVertex(p2.x(), p2.y(), p2.z())
                .setColor(red, green, blue, alphaInt)
                .setNormal(normal.x(), normal.y(), normal.z());
    }
}
