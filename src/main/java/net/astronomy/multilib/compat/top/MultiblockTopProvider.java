package net.astronomy.multilib.compat.top;

import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.IProgressStyle;
import mcjty.theoneprobe.api.ProbeMode;
import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.hud.HudContext;
import net.astronomy.multilib.api.hud.HudEntry;
import net.astronomy.multilib.api.hud.MultiblockHudRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * TOP counterpart of {@code compat/jade}'s bridge. Unlike Jade, {@link IProbeInfoProvider#addProbeInfo}
 * already runs entirely server-side, so no NBT round-trip is needed here - {@link HudEntry} instances
 * are translated straight into {@link IProbeInfo} calls in the same method:
 * {@link HudEntry.Text}&nbsp;→&nbsp;{@code mcText}, {@link HudEntry.Progress}&nbsp;→&nbsp;{@code progress},
 * {@link HudEntry.KeyValue}&nbsp;→&nbsp;a plain text line.
 */
public final class MultiblockTopProvider implements IProbeInfoProvider {

    public static final MultiblockTopProvider INSTANCE = new MultiblockTopProvider();

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MultiLib.MODID, "hud");

    private MultiblockTopProvider() {}

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, Player player, Level level,
                              BlockState blockState, IProbeHitData data) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        List<HudEntry> entries = HudContext.at(serverLevel, data.getPos(), serverPlayer)
                .map(MultiblockHudRegistry::gatherEntries)
                .orElseGet(() -> MultiblockHudRegistry.gatherUnformedEntries(serverLevel, data.getPos(), serverPlayer));
        if (entries.isEmpty()) return;

        for (HudEntry entry : entries) {
            switch (entry) {
                case HudEntry.Text text -> probeInfo.mcText(text.text());
                case HudEntry.Progress progress -> {
                    IProgressStyle style = probeInfo.defaultProgressStyle()
                            .prefix(progress.label())
                            .suffix(Component.empty());
                    probeInfo.progress(Math.round(progress.fraction() * 1000f), 1000, style);
                }
                case HudEntry.KeyValue keyValue -> probeInfo.mcText(Component.empty()
                        .append(keyValue.key())
                        .append(": ")
                        .append(keyValue.value()));
            }
        }
    }
}
