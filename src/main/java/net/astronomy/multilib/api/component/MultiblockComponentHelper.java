package net.astronomy.multilib.api.component;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.Function;

/**
 * Wires an {@link EnergyBufferComponent}/{@link FluidTankComponent}/{@link ItemBufferComponent} field
 * on a controller BE up to NeoForge's block-entity capability system, so pipes/cables/other mods'
 * automation can see it. MultiLib doesn't do this automatically - it has no way to know which BE
 * types a given mod declares or which fields on them hold buffer components - so the dev calls these
 * from their own {@code RegisterCapabilitiesEvent} listener (fired on the mod bus).
 * <pre>{@code
 * public class MyControllerBE extends AbstractMultiblockControllerBE {
 *     public final EnergyBufferComponent energy = new EnergyBufferComponent(100_000, 1000, 1000, this::setChanged);
 *     public final FluidTankComponent tank = new FluidTankComponent(16_000, null, this::setChanged);
 *     public final ItemBufferComponent items = new ItemBufferComponent(9, null, this::setChanged);
 *     // ...
 * }
 *
 * // In a class subscribed to the mod event bus:
 * @SubscribeEvent
 * public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
 *     MultiblockComponentHelper.registerEnergy(event, MySetup.CONTROLLER_BE_TYPE, be -> be.energy);
 *     MultiblockComponentHelper.registerFluid(event, MySetup.CONTROLLER_BE_TYPE, be -> be.tank);
 *     MultiblockComponentHelper.registerItem(event, MySetup.CONTROLLER_BE_TYPE, be -> be.items);
 * }
 * }</pre>
 * Each {@code getter} is called once per capability lookup and may return {@code null} (e.g. to hide
 * the buffer while unformed) - NeoForge treats a {@code null} capability as "not present".
 */
public final class MultiblockComponentHelper {

    private MultiblockComponentHelper() {}

    /** Exposes {@code getter}'s {@link IEnergyStorage} on every block of {@code type} via {@link Capabilities.EnergyStorage#BLOCK}. */
    public static <BE extends BlockEntity> void registerEnergy(RegisterCapabilitiesEvent event,
            BlockEntityType<BE> type, Function<BE, IEnergyStorage> getter) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> getter.apply(be));
    }

    /** Exposes {@code getter}'s {@link IFluidHandler} on every block of {@code type} via {@link Capabilities.FluidHandler#BLOCK}. */
    public static <BE extends BlockEntity> void registerFluid(RegisterCapabilitiesEvent event,
            BlockEntityType<BE> type, Function<BE, IFluidHandler> getter) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, side) -> getter.apply(be));
    }

    /** Exposes {@code getter}'s {@link IItemHandler} on every block of {@code type} via {@link Capabilities.ItemHandler#BLOCK}. */
    public static <BE extends BlockEntity> void registerItem(RegisterCapabilitiesEvent event,
            BlockEntityType<BE> type, Function<BE, IItemHandler> getter) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> getter.apply(be));
    }
}
