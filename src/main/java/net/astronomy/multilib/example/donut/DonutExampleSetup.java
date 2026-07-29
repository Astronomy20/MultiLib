package net.astronomy.multilib.example.donut;

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
 * Wiring for the {@code multilib:example_donut} structure: a controller block/entity and the
 * {@link DonutPattern} registration, demonstrating {@link net.astronomy.multilib.api.pattern.providers.RevolutionProvider}.
 * The ring itself is plain vanilla pink terracotta - only the controller (the pattern's single
 * landmark cell) needs a dedicated block. Self-contained {@link EventBusSubscriber}, like the other
 * {@code example/**} demos - excluding this package from a real build removes it cleanly.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class DonutExampleSetup {

    public static DonutControllerBlock CONTROLLER_BLOCK;
    public static BlockEntityType<DonutControllerBE> CONTROLLER_BE_TYPE;
    public static BlockItem CONTROLLER_ITEM;

    private DonutExampleSetup() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            CONTROLLER_BLOCK = new DonutControllerBlock(BlockBehaviour.Properties.of().strength(3.0F));
            helper.register(id("donut_controller"), CONTROLLER_BLOCK);
        });
        event.register(Registries.ITEM, helper -> {
            CONTROLLER_ITEM = new BlockItem(CONTROLLER_BLOCK, new Item.Properties());
            helper.register(id("donut_controller"), CONTROLLER_ITEM);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            CONTROLLER_BE_TYPE = BlockEntityType.Builder.of(DonutControllerBE::create, CONTROLLER_BLOCK).build(null);
            helper.register(id("donut_controller"), CONTROLLER_BE_TYPE);
        });
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DonutPattern.registerAll();
            MultiLib.LOGGER.info("[MultiLib] Donut example structure loaded (test build)");
        });
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(CONTROLLER_ITEM);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, path);
    }
}
