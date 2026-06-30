package net.astronomy.multilib.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders a {@link MultiblockDefinition}'s pattern as a small rotating 3D assembly, for use inside
 * a GUI (e.g. a JEI/REI/EMI recipe category). Auto-rotates over time — there is no drag-to-rotate
 * input handling, keeping this self-contained and trivial to call from any draw() method.
 *
 * <p>Uses {@link BlockRenderDispatcher#renderSingleBlock} (the same level-independent path vanilla
 * uses for item icons), so blocks render in isolation without neighbor-aware ambient occlusion or
 * face culling — acceptable for a preview, not meant to look identical to in-world rendering.
 */
@OnlyIn(Dist.CLIENT)
public final class MultiblockStructurePreviewRenderer {

    private MultiblockStructurePreviewRenderer() {}

    /** Identifies one cell of the pattern grid — returned by {@link #pick} and accepted by {@link #render} to highlight it red. */
    public record BlockHit(int layerIndex, int row, int col, char symbol) {}

    /**
     * @param onlyLayer    if non-null, renders only that layer index (0-based); if null, all layers.
     * @param yawDegrees   horizontal turntable rotation.
     * @param pitchDegrees vertical tilt (positive = looking down).
     * @param highlight    if non-null, that cell is rendered with a red "wrong ghost"-style tint.
     */
    public static void render(GuiGraphics graphics, MultiblockDefinition definition,
                               int centerX, int centerY, int viewSize,
                               float yawDegrees, float pitchDegrees, @Nullable Integer onlyLayer,
                               @Nullable BlockHit highlight) {
        List<List<String>> layers = definition.getLayers();
        if (layers.isEmpty()) return;
        Map<Character, BlockIngredient> blockMap = definition.getBlockMap();

        int layersCount = layers.size();
        int maxWidth = 1, maxHeight = 1;
        for (List<String> layer : layers) {
            maxHeight = Math.max(maxHeight, layer.size());
            for (String row : layer) maxWidth = Math.max(maxWidth, row.length());
        }
        // Fit the largest dimension (including layer count, since it's also rendered as depth) into the view.
        float largestDimension = Math.max(maxWidth, Math.max(maxHeight, layersCount));
        float scale = (viewSize / largestDimension) / 1.4f;

        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, 200.0);
        poseStack.scale(scale, -scale, scale); // screen Y grows downward; flip so models render upright
        poseStack.mulPose(Axis.XP.rotationDegrees(pitchDegrees));
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDegrees));

        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        MultiBufferSource highlightSource = renderType ->
                new TintedVertexConsumer(bufferSource.getBuffer(renderType), 1f, 0.27f, 0.27f, 1f);

        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();
        for (int layerIdx = 0; layerIdx < layersCount; layerIdx++) {
            if (onlyLayer != null && layerIdx != onlyLayer) continue;
            List<String> rows = layers.get(layerIdx);
            int height = rows.size();
            if (height == 0) continue;
            int width = rows.get(0).length();
            // layers[0] is the topmost declared layer (same convention as MultiblockBuilder.layers()
            // call order / OverlayRequestHandler's ghost preview), layers[last] the bottommost.
            float relY = (layersCount - 1) / 2.0F - layerIdx;

            for (int row = 0; row < height; row++) {
                String line = rows.get(row);
                for (int col = 0; col < line.length(); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;
                    BlockIngredient ingredient = blockMap.get(symbol);
                    if (ingredient == null) continue;
                    BlockState state = representativeState(ingredient);
                    if (state == null || state.isAir()) continue;

                    float relX = col - (width - 1) / 2.0F;
                    float relZ = row - (height - 1) / 2.0F;

                    boolean isHighlighted = highlight != null
                            && highlight.layerIndex() == layerIdx && highlight.row() == row && highlight.col() == col;

                    poseStack.pushPose();
                    poseStack.translate(relX - 0.5, relY - 0.5, relZ - 0.5);
                    dispatcher.renderSingleBlock(state, poseStack, isHighlighted ? highlightSource : bufferSource,
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    poseStack.popPose();
                }
            }
        }
        bufferSource.endBatch();
        RenderSystem.disableDepthTest();
        Lighting.setupForFlatItems();
        poseStack.popPose();
    }

    /**
     * Finds which pattern cell, if any, projects under (mouseX, mouseY) given the same camera
     * parameters {@link #render} would use. Picks the cell whose screen-projected center is closest
     * to the mouse, within a hit radius proportional to the current block size on screen. Independent
     * of any live {@link GuiGraphics}/pose stack — builds its own matrix replicating render()'s chain.
     */
    @Nullable
    public static BlockHit pick(MultiblockDefinition definition, int centerX, int centerY, int viewSize,
                                 float yawDegrees, float pitchDegrees, @Nullable Integer onlyLayer,
                                 double mouseX, double mouseY) {
        List<List<String>> layers = definition.getLayers();
        if (layers.isEmpty()) return null;
        Map<Character, BlockIngredient> blockMap = definition.getBlockMap();

        int layersCount = layers.size();
        int maxWidth = 1, maxHeight = 1;
        for (List<String> layer : layers) {
            maxHeight = Math.max(maxHeight, layer.size());
            for (String row : layer) maxWidth = Math.max(maxWidth, row.length());
        }
        float largestDimension = Math.max(maxWidth, Math.max(maxHeight, layersCount));
        float scale = (viewSize / largestDimension) / 1.4f;

        PoseStack poseStack = new PoseStack();
        poseStack.translate(centerX, centerY, 200.0);
        poseStack.scale(scale, -scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(pitchDegrees));
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDegrees));
        Matrix4f matrix = new Matrix4f(poseStack.last().pose());

        float hitRadius = Math.max(4f, Math.abs(scale) * 0.6f);
        BlockHit best = null;
        float bestDist2 = hitRadius * hitRadius;

        for (int layerIdx = 0; layerIdx < layersCount; layerIdx++) {
            if (onlyLayer != null && layerIdx != onlyLayer) continue;
            List<String> rows = layers.get(layerIdx);
            int height = rows.size();
            if (height == 0) continue;
            int width = rows.get(0).length();
            float relY = (layersCount - 1) / 2.0F - layerIdx;

            for (int row = 0; row < height; row++) {
                String line = rows.get(row);
                for (int col = 0; col < line.length(); col++) {
                    char symbol = line.charAt(col);
                    if (symbol == ' ') continue;
                    BlockIngredient ingredient = blockMap.get(symbol);
                    if (ingredient == null) continue;
                    BlockState state = representativeState(ingredient);
                    if (state == null || state.isAir()) continue;

                    float relX = col - (width - 1) / 2.0F;
                    float relZ = row - (height - 1) / 2.0F;

                    Vector4f center = new Vector4f(relX, relY, relZ, 1f);
                    matrix.transform(center);

                    float dx = (float) mouseX - center.x();
                    float dy = (float) mouseY - center.y();
                    float dist2 = dx * dx + dy * dy;
                    if (dist2 < bestDist2) {
                        bestDist2 = dist2;
                        best = new BlockHit(layerIdx, row, col, symbol);
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    private static BlockState representativeState(BlockIngredient ingredient) {
        Set<Block> candidates = ingredient.getCandidateBlocks();
        if (candidates.isEmpty()) return null;
        return candidates.iterator().next().defaultBlockState();
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
                    Math.min(255, (int) (red * tr)),
                    Math.min(255, (int) (green * tg)),
                    Math.min(255, (int) (blue * tb)),
                    Math.min(255, (int) (alpha * ta))
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
