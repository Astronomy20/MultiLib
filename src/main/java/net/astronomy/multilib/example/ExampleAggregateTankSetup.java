package net.astronomy.multilib.example;

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
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Self-contained registration for {@link ExampleRedTankBlock}/{@link ExampleRedTankBlockEntity} and
 * {@link ExampleGreenTankBlock}/{@link ExampleGreenTankBlockEntity} - the two neighbor-aggregating tank
 * demos (see their javadocs). Kept separate from {@link ExampleTankSetup} (the pattern-matched
 * {@code expandable_tank} demo) since these two don't declare or depend on any
 * {@code MultiblockDefinition} at all - they're built entirely on
 * {@link net.astronomy.multilib.api.aggregate.AbstractAggregatingBlock}.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class ExampleAggregateTankSetup {

    public static ExampleRedTankBlock RED_TANK_BLOCK;
    public static BlockItem RED_TANK_ITEM;
    public static BlockEntityType<ExampleRedTankBlockEntity> RED_TANK_BE_TYPE;

    public static ExampleGreenTankBlock GREEN_TANK_BLOCK;
    public static BlockItem GREEN_TANK_ITEM;
    public static BlockEntityType<ExampleGreenTankBlockEntity> GREEN_TANK_BE_TYPE;

    private ExampleAggregateTankSetup() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            RED_TANK_BLOCK = new ExampleRedTankBlock(BlockBehaviour.Properties.of().strength(3.0F));
            helper.register(id("example_red_tank"), RED_TANK_BLOCK);
            GREEN_TANK_BLOCK = new ExampleGreenTankBlock(BlockBehaviour.Properties.of().strength(3.0F));
            helper.register(id("example_green_tank"), GREEN_TANK_BLOCK);
        });
        event.register(Registries.ITEM, helper -> {
            RED_TANK_ITEM = new BlockItem(RED_TANK_BLOCK, new Item.Properties());
            helper.register(id("example_red_tank"), RED_TANK_ITEM);
            GREEN_TANK_ITEM = new BlockItem(GREEN_TANK_BLOCK, new Item.Properties());
            helper.register(id("example_green_tank"), GREEN_TANK_ITEM);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            RED_TANK_BE_TYPE = BlockEntityType.Builder.of(ExampleRedTankBlockEntity::create, RED_TANK_BLOCK).build(null);
            helper.register(id("example_red_tank"), RED_TANK_BE_TYPE);
            GREEN_TANK_BE_TYPE = BlockEntityType.Builder.of(ExampleGreenTankBlockEntity::create, GREEN_TANK_BLOCK).build(null);
            helper.register(id("example_green_tank"), GREEN_TANK_BE_TYPE);
        });
    }

    // Exposes each tank's resolveActiveHandler() as the standard NeoForge fluid block capability, so
    // buckets/pipes (and any fluid-aware HUD/tooltip mod) can actually fill/drain it.
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        MultiblockComponentHelper.registerFluid(event, RED_TANK_BE_TYPE, ExampleRedTankBlockEntity::resolveActiveHandler);
        MultiblockComponentHelper.registerFluid(event, GREEN_TANK_BE_TYPE, ExampleGreenTankBlockEntity::resolveActiveHandler);
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(RED_TANK_ITEM);
            event.accept(GREEN_TANK_ITEM);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, path);
    }
}
