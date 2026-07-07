package net.astronomy.multilib.example;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.component.MultiblockComponentHelper;
import net.astronomy.multilib.api.hud.EnergyHudProvider;
import net.astronomy.multilib.api.hud.MultiblockHudRegistry;
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
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Self-contained test/demo wiring for the multilib:example structure. Nothing in MultiLib.java
 * (or any other non-example class) references this package - NeoForge discovers it purely via
 * the {@link EventBusSubscriber} annotation, so excluding net/astronomy/multilib/example/** from
 * compilation (see build.gradle) cleanly removes the whole test multiblock from a real build.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class ExampleSetup {

    // Assigned lazily inside onRegister(), not eagerly as field initializers: constructing a
    // Block/Item/BlockEntityType touches its registry's intrusive holder, which is only legal
    // while that registry is open for modded registration (i.e. during RegisterEvent for it).
    // Building these too early (e.g. at class-load time, before RegisterEvent has unfrozen the
    // registry) throws "Registry is already frozen".
    public static ExamplePartBlock PART_BLOCK;
    public static BlockItem PART_ITEM;
    public static ExampleControllerBlock CONTROLLER_BLOCK;
    public static BlockEntityType<ExampleControllerBE> CONTROLLER_BE_TYPE;
    public static BlockItem CONTROLLER_ITEM;
    // MultiLib itself ships no wrench - this is a reference IMultiblockWrench implementation,
    // registered here purely so the example structure can be tested in-game.
    public static ExampleWrenchItem WRENCH;

    // "Rigid" core demo - same role as CONTROLLER_BLOCK, but with its own placed FACING (see
    // ExampleDirectionalControllerBlock/ExampleDirectionalPattern) to exercise mainFace().
    public static ExampleDirectionalControllerBlock DIRECTIONAL_CONTROLLER_BLOCK;
    public static BlockEntityType<ExampleDirectionalControllerBE> DIRECTIONAL_CONTROLLER_BE_TYPE;
    public static BlockItem DIRECTIONAL_CONTROLLER_ITEM;

    private ExampleSetup() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        // RegisterEvent fires once per registry, in vanilla's dependency order (BLOCK, then ITEM,
        // then BLOCK_ENTITY_TYPE), so CONTROLLER_BLOCK is already set by the time ITEM/BLOCK_ENTITY_TYPE fire.
        event.register(Registries.BLOCK, helper -> {
            PART_BLOCK = new ExamplePartBlock(BlockBehaviour.Properties.of().strength(2.0F));
            helper.register(id("example_part"), PART_BLOCK);

            CONTROLLER_BLOCK = new ExampleControllerBlock(BlockBehaviour.Properties.of().strength(3.0F));
            helper.register(id("example_controller"), CONTROLLER_BLOCK);

            DIRECTIONAL_CONTROLLER_BLOCK = new ExampleDirectionalControllerBlock(BlockBehaviour.Properties.of().strength(3.0F));
            helper.register(id("example_directional_controller"), DIRECTIONAL_CONTROLLER_BLOCK);
        });
        event.register(Registries.ITEM, helper -> {
            PART_ITEM = new BlockItem(PART_BLOCK, new Item.Properties());
            helper.register(id("example_part"), PART_ITEM);

            CONTROLLER_ITEM = new BlockItem(CONTROLLER_BLOCK, new Item.Properties());
            helper.register(id("example_controller"), CONTROLLER_ITEM);

            DIRECTIONAL_CONTROLLER_ITEM = new BlockItem(DIRECTIONAL_CONTROLLER_BLOCK, new Item.Properties());
            helper.register(id("example_directional_controller"), DIRECTIONAL_CONTROLLER_ITEM);

            WRENCH = new ExampleWrenchItem(new Item.Properties().stacksTo(1));
            helper.register(id("example_wrench"), WRENCH);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            CONTROLLER_BE_TYPE = BlockEntityType.Builder.of(ExampleControllerBE::create, CONTROLLER_BLOCK).build(null);
            helper.register(id("example_controller"), CONTROLLER_BE_TYPE);

            DIRECTIONAL_CONTROLLER_BE_TYPE = BlockEntityType.Builder.of(
                    ExampleDirectionalControllerBE::create, DIRECTIONAL_CONTROLLER_BLOCK).build(null);
            helper.register(id("example_directional_controller"), DIRECTIONAL_CONTROLLER_BE_TYPE);
        });
    }

    // api/component demo: exposes ExampleControllerBE's energy buffer as the standard NeoForge
    // energy block capability, so external pipes/cables (and EnergyHudProvider below) can reach it.
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        MultiblockComponentHelper.registerEnergy(event, CONTROLLER_BE_TYPE, be -> be.energy);
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ExamplePattern.registerAll();
            ExampleDirectionalPattern.registerAll();
            // api/hud demo: opt the example structure into an "Energy: X / Y FE" hover line shown
            // by Jade/The One Probe when installed. Providers beyond FormedStatusProvider are
            // always an explicit per-definition opt-in like this.
            MultiblockHudRegistry.register(
                    ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, "example"),
                    new EnergyHudProvider());
            MultiLib.LOGGER.info("[MultiLib] Example multiblock definitions loaded (test build)");
        });
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(PART_ITEM);
            event.accept(CONTROLLER_ITEM);
            event.accept(DIRECTIONAL_CONTROLLER_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WRENCH);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, path);
    }
}
