package net.astronomy.multilib.example.assembly;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.assembly.AssemblyBreakPolicy;
import net.astronomy.multilib.api.assembly.AssemblyBuilder;
import net.astronomy.multilib.api.assembly.ConnectionType;
import net.astronomy.multilib.api.assembly.StatMerge;
import net.minecraft.resources.ResourceLocation;

/**
 * Minimal demonstration of the Fase 12 assembly system: a "core" ({@code multilib:example}) linked to
 * one-or-more "satellites" ({@code multilib:example_directional}) by proximity. Forms bottom-up once
 * both sub-structures exist near each other; degrades gracefully when a satellite is removed.
 * Registered from {@link AssemblyExampleSetup#onCommonSetup}, after the member definitions themselves.
 */
public final class ExampleAssembly {

    private ExampleAssembly() {}

    public static void register() {
        AssemblyBuilder.create(id("example_assembly"))
                .role("core", id("example"))                       // exactly one core
                .role("satellite", id("example_directional"), 1, 4) // one-to-four satellites
                .proximity("core", "satellite", 6)
                .masterRole("core")
                .breakPolicy(AssemblyBreakPolicy.DEGRADE)
                .aggregateStat("power", StatMerge.SUM)
                .onAssemblyFormed(ctx -> MultiLib.LOGGER.info(
                        "[MultiLib] Example assembly formed with {} satellite(s)",
                        ctx.context().instance().memberCount("satellite")))
                .onMemberJoined(ctx -> MultiLib.LOGGER.info(
                        "[MultiLib] Example assembly gained a '{}' member", ctx.role()))
                .onMemberLeft(ctx -> MultiLib.LOGGER.info(
                        "[MultiLib] Example assembly lost a '{}' member", ctx.role()))
                .onAssemblyBroken(ctx -> MultiLib.LOGGER.info("[MultiLib] Example assembly broken"))
                .build();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, path);
    }
}
