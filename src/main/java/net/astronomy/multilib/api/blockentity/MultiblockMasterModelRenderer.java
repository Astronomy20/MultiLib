package net.astronomy.multilib.api.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reusable {@link BlockEntityRenderer} for {@code .model(...)} multiblocks: renders the block
 * registered under the structure's model id in place of the core's own (now-hidden) block model.
 * Register it for your controller's {@code BlockEntityType} the same way as any other BER, e.g.
 * {@code event.registerBlockEntityRenderer(MY_CONTROLLER_BE, MultiblockMasterModelRenderer::new)}.
 */
public class MultiblockMasterModelRenderer<T extends AbstractMultiblockControllerBE> implements BlockEntityRenderer<T> {

    public MultiblockMasterModelRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        ResourceLocation modelId = be.getActiveModelId();
        if (modelId == null) return;

        BuiltInRegistries.BLOCK.getOptional(modelId).ifPresent(block -> {
            BlockState state = block.defaultBlockState();
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.5);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    state, poseStack, bufferSource, packedLight, packedOverlay);
            poseStack.popPose();
        });
    }
}
