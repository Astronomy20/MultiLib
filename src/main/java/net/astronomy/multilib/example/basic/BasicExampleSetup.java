package net.astronomy.multilib.example.basic;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.component.MultiblockComponentHelper;
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
 * Wiring for the basic {@code multilib:example} structure: a controller block entity (with an energy
 * buffer exposed as the NeoForge energy capability), a plain part block, and a reference wrench item.
 * Self-contained {@link EventBusSubscriber} — NeoForge discovers it via the annotation, so excluding
 * {@code net/astronomy/multilib/example/**} from a real build cleanly removes the whole demo.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class BasicExampleSetup {

    // Assigned lazily inside onRegister(), not as field initializers: constructing a
    // Block/Item/BlockEntityType touches its registry's intrusive holder, only legal while that
    // registry is open for modded registration (i.e. during RegisterEvent for it).
    public static ExamplePartBlock PART_BLOCK;
    public static BlockItem PART_ITEM;
    public static ExampleControllerBlock CONTROLLER_BLOCK;
    public static BlockEntityType<ExampleControllerBE> CONTROLLER_BE_TYPE;
    public static BlockItem CONTROLLER_ITEM;
    // MultiLib itself ships no wrench - this is a reference IMultiblockWrench implementation.
    public static ExampleWrenchItem WRENCH;

    private BasicExampleSetup() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            PART_BLOCK = new ExamplePartBlock(BlockBehaviour.Properties.of().strength(2.0F));
            helper.register(id("example_part"), PART_BLOCK);

            CONTROLLER_BLOCK = new ExampleControllerBlock(BlockBehaviour.Properties.of().strength(3.0F));
            helper.register(id("example_controller"), CONTROLLER_BLOCK);
        });
        event.register(Registries.ITEM, helper -> {
            PART_ITEM = new BlockItem(PART_BLOCK, new Item.Properties());
            helper.register(id("example_part"), PART_ITEM);

            CONTROLLER_ITEM = new BlockItem(CONTROLLER_BLOCK, new Item.Properties());
            helper.register(id("example_controller"), CONTROLLER_ITEM);

            WRENCH = new ExampleWrenchItem(new Item.Properties().stacksTo(1));
            helper.register(id("example_wrench"), WRENCH);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            CONTROLLER_BE_TYPE = BlockEntityType.Builder.of(ExampleControllerBE::create, CONTROLLER_BLOCK).build(null);
            helper.register(id("example_controller"), CONTROLLER_BE_TYPE);
        });
    }

    // api/component demo: exposes ExampleControllerBE's energy buffer as the standard NeoForge energy
    // block capability, so external pipes/cables (and example/hud/ExampleHudSetup's EnergyHudProvider
    // demo) can reach it.
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        MultiblockComponentHelper.registerEnergy(event, CONTROLLER_BE_TYPE, be -> be.energy);
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ExamplePattern.registerAll();
            // HUD demos live in example/hud/ExampleHudSetup, not here - the HUD bridge is a
            // cross-cutting concern, not tied to any one example structure's own setup.
            MultiLib.LOGGER.info("[MultiLib] Basic example structure loaded (test build)");
        });
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(PART_ITEM);
            event.accept(CONTROLLER_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WRENCH);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, path);
    }
}
