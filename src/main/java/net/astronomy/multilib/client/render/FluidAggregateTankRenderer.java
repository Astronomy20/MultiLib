package net.astronomy.multilib.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.astronomy.multilib.api.aggregate.AggregateGroup;
import net.astronomy.multilib.api.component.FluidTankComponent;
import net.astronomy.multilib.example.FluidAggregateTank;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.TreeMap;

/**
 * Renders the live fill level of a {@link FluidAggregateTank} across its whole current
 * {@link AggregateGroup} - not just this one block's own share. Members are grouped by Y level and
 * filled bottom-up, so a group that's laid out irregularly (offset/stepped, per
 * {@link net.astronomy.multilib.api.aggregate.AggregationShapePolicies#freeform()}) still reads as one
 * continuous rising body of fluid, exactly like a single connected tank would, even though each member
 * only ever stores its own fixed slice in its own block entity (see {@link ExampleAggregateTankRenderers}
 * for registration, and {@code ExampleRedTankBlockEntity}/{@code ExampleGreenTankBlockEntity} for the
 * two structures this renders).
 * <p>
 * Recomputed fresh every frame directly from live member data - nothing here is cached, since the group
 * composition and each member's fill level can both change at any time.
 */
public class FluidAggregateTankRenderer<T extends BlockEntity & FluidAggregateTank> implements BlockEntityRenderer<T> {

    public FluidAggregateTankRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                        int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) return;

        AggregateGroup group = be.getAggregateGroup();
        int capacityPerBlock = be.getTank().getCapacity();
        if (capacityPerBlock <= 0) return;

        FluidStack combined = FluidStack.EMPTY;
        int totalAmount = 0;
        // Y level -> how many group members occupy that level; used to fill bottom-up layer by layer.
        Map<Integer, Integer> blocksPerLayer = new TreeMap<>();
        for (BlockPos pos : group.members()) {
            blocksPerLayer.merge(pos.getY(), 1, Integer::sum);
            if (!(level.getBlockEntity(pos) instanceof FluidAggregateTank member)) continue;
            FluidTankComponent memberTank = member.getTank();
            if (memberTank.isEmpty()) continue;
            totalAmount += memberTank.getFluidAmount();
            if (combined.isEmpty()) combined = memberTank.getFluid().copy();
        }
        if (combined.isEmpty() || totalAmount <= 0) return;

        int remaining = totalAmount;
        double fractionForThisLayer = 0;
        int thisY = be.getBlockPos().getY();
        for (Map.Entry<Integer, Integer> entry : blocksPerLayer.entrySet()) {
            int layerCapacity = entry.getValue() * capacityPerBlock;
            double layerFraction = remaining >= layerCapacity ? 1.0 : Math.max(0.0, (double) remaining / layerCapacity);
            if (entry.getKey() == thisY) {
                fractionForThisLayer = layerFraction;
            }
            remaining -= layerCapacity;
        }
        if (fractionForThisLayer <= 0) return;

        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(combined.getFluid());
        ResourceLocation stillTexture = ext.getStillTexture();
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(stillTexture);
        int tint = ext.getTintColor();
        float a = ((tint >>> 24) & 0xFF) / 255F;
        float r = ((tint >> 16) & 0xFF) / 255F;
        float g = ((tint >> 8) & 0xFF) / 255F;
        float b = (tint & 0xFF) / 255F;
        if (a <= 0F) a = 1F;

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
        float inset = 1F / 16F;
        float height = Math.max(inset + 0.01F, (float) fractionForThisLayer - inset);
        Matrix4f matrix = poseStack.last().pose();
        renderFluidBox(consumer, matrix, sprite, inset, height, r, g, b, a, packedLight);
    }

    private void renderFluidBox(VertexConsumer consumer, Matrix4f matrix, TextureAtlasSprite sprite,
                                 float inset, float height, float r, float g, float b, float a, int light) {
        float min = inset;
        float max = 1F - inset;
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        quad(consumer, matrix,
                min, height, min, u0, v0,
                min, height, max, u0, v1,
                max, height, max, u1, v1,
                max, height, min, u1, v0,
                0, 1, 0, r, g, b, a, light);
        quad(consumer, matrix,
                min, 0, max, u0, v1,
                min, 0, min, u0, v0,
                max, 0, min, u1, v0,
                max, 0, max, u1, v1,
                0, -1, 0, r, g, b, a, light);
        quad(consumer, matrix,
                min, 0, min, u0, v1,
                min, height, min, u0, v0,
                max, height, min, u1, v0,
                max, 0, min, u1, v1,
                0, 0, -1, r, g, b, a, light);
        quad(consumer, matrix,
                max, 0, max, u0, v1,
                max, height, max, u0, v0,
                min, height, max, u1, v0,
                min, 0, max, u1, v1,
                0, 0, 1, r, g, b, a, light);
        quad(consumer, matrix,
                min, 0, max, u0, v1,
                min, height, max, u0, v0,
                min, height, min, u1, v0,
                min, 0, min, u1, v1,
                -1, 0, 0, r, g, b, a, light);
        quad(consumer, matrix,
                max, 0, min, u0, v1,
                max, height, min, u0, v0,
                max, height, max, u1, v0,
                max, 0, max, u1, v1,
                1, 0, 0, r, g, b, a, light);
    }

    private void quad(VertexConsumer consumer, Matrix4f matrix,
                       float x1, float y1, float z1, float u1, float v1,
                       float x2, float y2, float z2, float u2, float v2,
                       float x3, float y3, float z3, float u3, float v3,
                       float x4, float y4, float z4, float u4, float v4,
                       float nx, float ny, float nz,
                       float r, float g, float b, float a, int light) {
        vertex(consumer, matrix, x1, y1, z1, u1, v1, nx, ny, nz, r, g, b, a, light);
        vertex(consumer, matrix, x2, y2, z2, u2, v2, nx, ny, nz, r, g, b, a, light);
        vertex(consumer, matrix, x3, y3, z3, u3, v3, nx, ny, nz, r, g, b, a, light);
        vertex(consumer, matrix, x4, y4, z4, u4, v4, nx, ny, nz, r, g, b, a, light);
    }

    private void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v,
                         float nx, float ny, float nz, float r, float g, float b, float a, int light) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }
}
