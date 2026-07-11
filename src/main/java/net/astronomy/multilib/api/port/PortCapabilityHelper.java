package net.astronomy.multilib.api.port;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Wires a port block entity type's declared capabilities up to
 * {@link AbstractPortBlockEntity#getControllerCapability}, so a dev never has to hand-write the
 * {@code RegisterCapabilitiesEvent} lambda themselves. One call per (capability, port BE type) pair.
 * <p>
 * Example dev-side wiring, mirroring how {@code BasicExampleSetup} registers its own blocks/block entities
 * (an {@code @EventBusSubscriber}-annotated class listening for {@code RegisterCapabilitiesEvent}):
 * <pre>{@code
 * @SubscribeEvent
 * public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
 *     PortCapabilityHelper.registerProxy(event, Capabilities.ItemHandler.BLOCK, MyPorts.ITEM_PORT_BE_TYPE);
 *     PortCapabilityHelper.registerProxy(event, Capabilities.FluidHandler.BLOCK, MyPorts.FLUID_PORT_BE_TYPE);
 *     PortCapabilityHelper.registerProxy(event, Capabilities.EnergyStorage.BLOCK, MyPorts.ENERGY_PORT_BE_TYPE);
 * }
 * }</pre>
 * From then on, any pipe/cable/other mod querying {@code cap} against a block of that port type is
 * transparently redirected to whatever the port's structure's controller currently exposes - or gets
 * nothing back if the port isn't part of a formed structure.
 */
public final class PortCapabilityHelper {

    private PortCapabilityHelper() {}

    /**
     * Registers a proxy for {@code capability} on every block entity of {@code portType}: queries are
     * forwarded to {@link AbstractPortBlockEntity#getControllerCapability}, i.e. resolved from the
     * port's current controller rather than the port block entity itself.
     */
    public static <T, BE extends AbstractPortBlockEntity> void registerProxy(
            RegisterCapabilitiesEvent event,
            BlockCapability<T, Direction> capability,
            BlockEntityType<BE> portType) {
        event.registerBlockEntity(capability, portType,
                (port, side) -> port.getControllerCapability(capability, side).orElse(null));
    }
}
