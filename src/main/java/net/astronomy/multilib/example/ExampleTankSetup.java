package net.astronomy.multilib.example;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.component.FluidTankComponent;
import net.astronomy.multilib.api.component.MultiblockComponentHelper;
import net.astronomy.multilib.api.event.MultiblockFormedEvent;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Self-contained registration for {@link ExampleTankBlock}/{@link ExampleTankBlockEntity} - the
 * fluid-capable block used by the KubeJS-defined {@code multilib:expandable_tank} test structure
 * (see {@code expandable_tank.js}). Kept separate from {@link ExampleSetup} (the Java-defined
 * {@code multilib:example} structure's own wiring) since this block belongs to a script-defined
 * structure instead - registering the block here doesn't declare any {@code MultiblockDefinition}
 * itself, that lives entirely in the KubeJS script.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class ExampleTankSetup {

    // Assigned lazily inside onRegister() - see ExampleSetup's own field-declaration comment for why.
    public static ExampleTankBlock TANK_BLOCK;
    public static BlockItem TANK_ITEM;
    public static BlockEntityType<ExampleTankBlockEntity> TANK_BE_TYPE;

    private ExampleTankSetup() {}

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            TANK_BLOCK = new ExampleTankBlock(BlockBehaviour.Properties.of().strength(3.0F));
            helper.register(id("example_tank"), TANK_BLOCK);
        });
        event.register(Registries.ITEM, helper -> {
            TANK_ITEM = new BlockItem(TANK_BLOCK, new Item.Properties());
            helper.register(id("example_tank"), TANK_ITEM);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            TANK_BE_TYPE = BlockEntityType.Builder.of(ExampleTankBlockEntity::create, TANK_BLOCK).build(null);
            helper.register(id("example_tank"), TANK_BE_TYPE);
        });
    }

    // Exposes ExampleTankBlockEntity#tank as the standard NeoForge fluid block capability, so
    // buckets/pipes (and any fluid-aware HUD/tooltip mod) can actually fill/drain it.
    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        MultiblockComponentHelper.registerFluid(event, TANK_BE_TYPE, ExampleTankBlockEntity::resolveActiveHandler);
    }

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(TANK_ITEM);
        }
    }

    /**
     * Without this, breaking a block that happens to hold fluid content loses that content along with
     * the block itself - which is technically "correct" (that's genuinely where the content was stored),
     * but surprising here: {@link ExampleTankAggregateFluidHandler#fill} always tops off the FIRST member
     * tank (by {@code instance.getPositions()}'s own arbitrary iteration order) before moving to the next,
     * completely unrelated to which physical block the player actually clicked with a bucket - so content
     * ends up concentrated in whichever blocks happened to iterate first, invisibly to the player. Breaking
     * an unrelated block elsewhere then loses a chunk of the combined total that has nothing to do with
     * what was broken. Runs at HIGH priority, before {@code BlockBreakHandler}'s own (default-priority)
     * listener unregisters the instance, and before the block is actually removed (same
     * {@code BlockEvent.BreakEvent} timing caveat noted elsewhere in this codebase) - so the breaking
     * block's own tank is still fully readable here. Rescues as much as still fits into the OTHER member
     * tanks (capacity permitting); whatever doesn't fit is genuinely lost with the block, same as it would
     * be for a real single-block tank overflowing.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTankBreakRedistributeFluid(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof ExampleTankBlockEntity be) || be.tank.isEmpty()) return;

        WorldMultiblockTracker tracker = WorldMultiblockTracker.get(level);
        for (MultiblockInstance instance : tracker.getInstancesAt(pos)) {
            List<FluidTankComponent> siblingTanks = new ArrayList<>();
            for (BlockPos p : instance.getPositions()) {
                if (p.equals(pos)) continue;
                if (level.getBlockEntity(p) instanceof ExampleTankBlockEntity sibling) {
                    siblingTanks.add(sibling.tank);
                }
            }
            if (siblingTanks.isEmpty()) continue;

            FluidStack toRescue = be.tank.getFluid();
            int remaining = toRescue.getAmount();
            for (FluidTankComponent sibling : siblingTanks) {
                if (remaining <= 0) break;
                remaining -= sibling.fill(toRescue.copyWithAmount(remaining), IFluidHandler.FluidAction.EXECUTE);
            }
            return;
        }
    }

    /**
     * Breaking one block of an extreme layer doesn't just remove that one block from the instance -
     * {@code ShapelessMatcher}'s solid-fill trim shrinks the WHOLE now-invalid layer away, so the smaller
     * structure that re-forms excludes every position of that layer, not just the broken one. The OTHER
     * (unbroken) blocks of that layer are still physically there and still hold whatever fluid content
     * they had - {@link #onTankBreakRedistributeFluid} only rescues the literally-broken block's own
     * content, so without this, that layer's other 8 blocks would sit there as orphaned "solo" tanks,
     * invisible to {@link ExampleTankBlockEntity#resolveActiveHandler()} (which only sums the CURRENTLY
     * tracked instance's members) until separately broken or re-absorbed by future growth.
     * <p>
     * Runs on every formation of a structure built from this block: scans one block beyond the new
     * instance's own bounding box for {@link ExampleTankBlockEntity} positions that aren't part of ANY
     * currently tracked instance (i.e. exactly the "orphaned by trimming" case, not a block that's part of
     * some other unrelated tracked structure) and sweeps their content into the new instance, draining
     * each swept-from block by exactly the amount that actually fit elsewhere so nothing is duplicated.
     */
    @SubscribeEvent
    public static void onTankFormedSweepOrphans(MultiblockFormedEvent event) {
        MultiblockInstance instance = event.getInstance();
        ServerLevel level = event.getLevel();
        Set<BlockPos> members = instance.getPositions();
        if (members.isEmpty()) return;

        List<FluidTankComponent> targetTanks = new ArrayList<>();
        for (BlockPos p : members) {
            if (level.getBlockEntity(p) instanceof ExampleTankBlockEntity be) targetTanks.add(be.tank);
        }
        if (targetTanks.isEmpty()) return; // not a structure made of this block at all

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : members) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }

        WorldMultiblockTracker tracker = WorldMultiblockTracker.get(level);
        for (BlockPos p : BlockPos.betweenClosed(
                new BlockPos(minX - 1, minY - 1, minZ - 1), new BlockPos(maxX + 1, maxY + 1, maxZ + 1))) {
            if (members.contains(p) || !level.isLoaded(p)) continue;
            if (!(level.getBlockEntity(p) instanceof ExampleTankBlockEntity orphan) || orphan.tank.isEmpty()) continue;
            if (!tracker.getInstancesAt(p).isEmpty()) continue; // part of some other real instance - leave it alone

            FluidStack toRescue = orphan.tank.getFluid();
            int remaining = toRescue.getAmount();
            for (FluidTankComponent target : targetTanks) {
                if (remaining <= 0) break;
                remaining -= target.fill(toRescue.copyWithAmount(remaining), IFluidHandler.FluidAction.EXECUTE);
            }
            int rescued = toRescue.getAmount() - remaining;
            if (rescued > 0) {
                orphan.tank.drain(rescued, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, path);
    }
}
