package net.astronomy.multilib.api.hud;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import java.util.function.Consumer;

/**
 * Opt-in provider that queries the instance's core position for NeoForge's standard
 * {@link Capabilities.EnergyStorage#BLOCK} capability (null side) and, if present, shows an
 * "Energy: stored / max FE" line. Works with any cap-exposing controller, including
 * {@code api/component}'s {@code EnergyBufferComponent} once wired up via
 * {@code MultiblockComponentHelper.registerEnergy}. Register per-definition:
 * {@code MultiblockHudRegistry.register(definitionId, new EnergyHudProvider())}.
 */
public final class EnergyHudProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        BlockPos corePos = ctx.instance().getCorePos().orElse(null);
        if (corePos == null) return;

        BlockState state = ctx.level().getBlockState(corePos);
        BlockEntity be = ctx.level().getBlockEntity(corePos);
        IEnergyStorage energy = Capabilities.EnergyStorage.BLOCK.getCapability(ctx.level(), corePos, state, be, null);
        if (energy == null) return;

        Component key = Component.literal("Energy");
        Component value = Component.literal(energy.getEnergyStored() + " / " + energy.getMaxEnergyStored() + " FE");
        out.accept(new HudEntry.KeyValue(key, value));
    }
}
