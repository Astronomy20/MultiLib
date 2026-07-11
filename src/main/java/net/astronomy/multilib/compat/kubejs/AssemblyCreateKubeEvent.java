package net.astronomy.multilib.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.astronomy.multilib.api.assembly.AssemblyBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Fired on every reload (server_scripts — see {@code MultiblockKubeEvents}) so scripts can declare
 * assemblies. Mirrors {@link MultiblockCreateKubeEvent}: every {@link AssemblyBuilder} handed out via
 * {@link #assembly(ResourceLocation)} is collected so {@code KubeJSMultiblockSetup} can build and
 * (re-)register them once every listener has configured its builder. From JS:
 * {@code MultiblockEvents.assembly(event => { event.assembly(id).role(...).connection(...) })}.
 */
public class AssemblyCreateKubeEvent implements KubeEvent {
    private final List<AssemblyBuilder> builders = new ArrayList<>();

    public AssemblyBuilder assembly(ResourceLocation id) {
        AssemblyBuilder builder = AssemblyBuilder.create(id);
        builders.add(builder);
        return builder;
    }

    List<AssemblyBuilder> getBuilders() {
        return builders;
    }
}
