package net.astronomy.multilib.example;

import net.astronomy.multilib.api.tool.IMultiblockWrench;
import net.minecraft.world.item.Item;

/**
 * Reference {@link IMultiblockWrench} implementation, kept here as a usage example for third-party
 * devs. Implementing the interface is all it takes - {@code WrenchInteractionHandler} triggers a
 * formation attempt on right-click for any item that implements it or is registered via
 * {@code MultiLibAPI.registerWrenchItem} (for data-driven/scripted items that can't implement it).
 * That's the entire mechanism: no chat feedback is sent by the library itself - a mod wanting to tell
 * the player what happened does so via {@code .onFormed(...)}/{@code .onBroken(...)} or its own item.
 */
public class ExampleWrenchItem extends Item implements IMultiblockWrench {

    public ExampleWrenchItem(Properties properties) {
        super(properties);
    }
}
