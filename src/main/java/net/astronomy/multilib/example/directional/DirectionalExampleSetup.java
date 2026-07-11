package net.astronomy.multilib.example.directional;

import net.astronomy.multilib.MultiLib;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Wiring for the {@code multilib:example_directional} structure — a "rigid" core with its own placed
 * {@code FACING} (see {@link ExampleDirectionalControllerBlock}/{@link ExampleDirectionalPattern}),
 * exercising {@code mainFace()} on the ghost overlay/auto-place preview. Self-contained
 * {@link EventBusSubscriber}, independent of the other example groups.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class DirectionalExampleSetup {

    public static ExampleDirectionalControllerBlock DIRECTIONAL_CONTROLLER_BLOCK;
    public static BlockEntityType<ExampleDirectionalControllerBE> DIRECTIONAL_CONTROLLER_BE_TYPE;
    public static BlockItem DIRECTIONAL_CONTROLLER_ITEM;

    private DirectionalExampleSetup() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            DIRECTIONAL_CONTROLLER_BLOCK = new ExampleDirectionalControllerBlock(BlockBehaviour.Properties.of().strength(3.0F));
            helper.register(id("example_directional_controller"), DIRECTIONAL_CONTROLLER_BLOCK);
        });
        event.register(Registries.ITEM, helper -> {
            DIRECTIONAL_CONTROLLER_ITEM = new BlockItem(DIRECTIONAL_CONTROLLER_BLOCK, new Item.Properties());
            helper.register(id("example_directional_controller"), DIRECTIONAL_CONTROLLER_ITEM);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            DIRECTIONAL_CONTROLLER_BE_TYPE = BlockEntityType.Builder.of(
                    ExampleDirectionalControllerBE::create, DIRECTIONAL_CONTROLLER_BLOCK).build(null);
            helper.register(id("example_directional_controller"), DIRECTIONAL_CONTROLLER_BE_TYPE);
        });
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ExampleDirectionalPattern.registerAll();
            MultiLib.LOGGER.info("[MultiLib] Directional example structure loaded (test build)");
        });
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(DIRECTIONAL_CONTROLLER_ITEM);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, path);
    }
}
