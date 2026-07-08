package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.progress.MultiblockProgressAPI;
import net.astronomy.multilib.api.progress.StructureProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/**
 * Backs {@link MultiblockHudRegistry#gatherUnformedEntries}: for a position holding the declared core
 * block of some registered, layer-based definition (see {@link MultiblockProgressAPI#compute}), but
 * with no formed instance there yet, reports "N of M blocks" as a single {@link HudEntry.Progress}.
 * <p>
 * Not a {@link MultiblockHudProvider} - there is no formed
 * {@link net.astronomy.multilib.api.instance.MultiblockInstance} to build a {@link HudContext} from.
 * This is the mechanism {@link MultiblockHudRegistry#gatherUnformedEntries}
 * calls directly, and only once {@link MultiblockHudRegistry#setUnformedHintsEnabled} has been turned
 * on (it defaults to off, same as every other opt-in mechanism in this library).
 */
public final class MissingBlocksProvider {

    private MissingBlocksProvider() {}

    static List<HudEntry> gather(ServerLevel level, BlockPos pos) {
        Optional<StructureProgress> progress = MultiblockProgressAPI.compute(level, pos);
        if (progress.isEmpty()) return List.of();

        StructureProgress p = progress.get();
        if (p.totalRequired() <= 0) return List.of();

        float fraction = (float) p.placedCount() / p.totalRequired();
        Component label = Component.translatable("multilib.hud.missing_blocks", p.placedCount(), p.totalRequired());
        return List.of(new HudEntry.Progress(fraction, label));
    }
}
