package net.astronomy.multilib.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.astronomy.multilib.api.MultiLib;
import net.astronomy.multilib.api.definition.MultiblockBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Fired on every reload (server_scripts - see {@code MultiblockKubeEvents}) so scripts can declare
 * multiblocks. Every builder handed out via {@link #multiblock(ResourceLocation)} is collected here
 * so {@code KubeJSMultiblockSetup} can build (and re-declare, since this fires again on every
 * {@code /reload}) them all once every listener has had a chance to configure its builder.
 */
public class MultiblockCreateKubeEvent implements KubeEvent {
    private final List<MultiblockBuilder> builders = new ArrayList<>();

    /**
     * Named {@code multiblock(...)} rather than {@code create(...)} on purpose: the same script file
     * typically also has a {@code StartupEvents.registry('item'/'block', event => event.create(id))}
     * block, where {@code create} means something completely different - a shared name across two
     * event objects with unrelated semantics is exactly the kind of thing that trips up whoever reads
     * (or copy-pastes) the script later.
     */
    public MultiblockBuilder multiblock(ResourceLocation id) {
        // silenceDevModeChat(): KubeJSMultiblockSetup already reports a validation failure to the
        // KubeJS console/script error overlay - broadcasting the same failure to chat too would just
        // show the player the same thing twice.
        MultiblockBuilder builder = MultiLib.define(id).silenceDevModeChat();
        builders.add(builder);
        return builder;
    }

    /**
     * Registers {@code item} as a wrench (see {@link MultiLib#registerWrenchItem}) - the
     * script-side equivalent of implementing {@code IMultiblockWrench} on a hand-written Item
     * subclass, for items created in KubeJS itself which can't implement a custom Java interface.
     */
    public void wrench(Item item) {
        MultiLib.registerWrenchItem(item);
    }

    List<MultiblockBuilder> getBuilders() {
        return builders;
    }
}
