package net.astronomy.multilib.client.render;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.example.ExampleAggregateTankSetup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Registers {@link FluidAggregateTankRenderer} for both neighbor-aggregating example tanks. */
@EventBusSubscriber(modid = MultiLib.MODID, value = Dist.CLIENT)
public final class ExampleAggregateTankRenderers {

    private ExampleAggregateTankRenderers() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ExampleAggregateTankSetup.RED_TANK_BE_TYPE, FluidAggregateTankRenderer::new);
        event.registerBlockEntityRenderer(ExampleAggregateTankSetup.GREEN_TANK_BE_TYPE, FluidAggregateTankRenderer::new);
    }
}
