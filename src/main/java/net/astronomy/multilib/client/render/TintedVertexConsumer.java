package net.astronomy.multilib.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Wraps a VertexConsumer and multiplies each vertex's RGBA by fixed tint factors.
 *
 * <p>Shared by {@link GhostBlockRenderer} and {@link MultiblockStructurePreviewRenderer}, which both
 * need to apply a fixed color tint on top of a block's normally-rendered vertex colors.
 */
@OnlyIn(Dist.CLIENT)
final class TintedVertexConsumer implements VertexConsumer {

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
