package net.astronomy.multilib.core.assembly;

import net.astronomy.multilib.api.assembly.AssemblyInstance;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Gathers a NeoForge {@link BlockCapability} from every member of an assembly, read from each
 * member's core position. The basis for read-only aggregation views (energy/fluid/item): the
 * assembly does not own a buffer of its own, it composes its members' capabilities.
 */
public final class AssemblyCapabilities {

    private AssemblyCapabilities() {}

    /** Collects {@code cap} across every member's core position. */
    public static <T> List<T> collect(ServerLevel level, AssemblyInstance assembly,
                                      BlockCapability<T, Direction> cap, @Nullable Direction side) {
        return collect(level, assembly, assembly.allMemberIds(), cap, side);
    }

    /** Collects {@code cap} across members of a single role. */
    public static <T> List<T> collectForRole(ServerLevel level, AssemblyInstance assembly, String role,
                                             BlockCapability<T, Direction> cap, @Nullable Direction side) {
        return collect(level, assembly, assembly.getMembers(role), cap, side);
    }

    private static <T> List<T> collect(ServerLevel level, AssemblyInstance assembly, Set<UUID> memberIds,
                                       BlockCapability<T, Direction> cap, @Nullable Direction side) {
        WorldMultiblockTracker mTracker = WorldMultiblockTracker.get(level);
        List<T> out = new ArrayList<>();
        for (UUID memberId : memberIds) {
            MultiblockInstance mi = mTracker.getById(memberId).orElse(null);
            if (mi == null) continue;
            BlockPos core = mi.getCorePos().orElse(null);
            if (core == null || !level.isLoaded(core)) continue;
            T c = level.getCapability(cap, core, side);
            if (c != null) out.add(c);
        }
        return out;
    }
}
