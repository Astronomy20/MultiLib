package net.astronomy.multilib.core.assembly;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.assembly.AssemblyBreakPolicy;
import net.astronomy.multilib.api.assembly.AssemblyDefinition;
import net.astronomy.multilib.api.assembly.AssemblyInstance;
import net.astronomy.multilib.api.assembly.StandardAssemblyState;
import net.astronomy.multilib.api.assembly.callback.AssemblyBrokenContext;
import net.astronomy.multilib.api.assembly.callback.AssemblyContext;
import net.astronomy.multilib.api.assembly.callback.AssemblyMemberContext;
import net.astronomy.multilib.api.event.MultiblockBrokenEvent;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Optional;
import java.util.UUID;

/**
 * Reacts to a member sub-structure breaking. Subscribes to {@link MultiblockBrokenEvent} on the game
 * bus (the existing single-structure break path already fired it), then applies the assembly's
 * {@link AssemblyBreakPolicy}. Breaking the assembly never destroys the surviving members — it only
 * dissolves the logical link. Auto-registered via {@link EventBusSubscriber}.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class AssemblyBreakHandler {

    private AssemblyBreakHandler() {}

    @SubscribeEvent
    public static void onMultiblockBroken(MultiblockBrokenEvent event) {
        ServerLevel level = event.getLevel();
        UUID brokenMemberId = event.getInstance().getId();

        WorldAssemblyTracker tracker = WorldAssemblyTracker.get(level);
        AssemblyInstance assembly = tracker.getByMember(brokenMemberId).orElse(null);
        if (assembly == null) return;

        AssemblyDefinition def = AssemblyRegistry.get(assembly.getDefinitionId()).orElse(null);
        Optional<String> role = assembly.removeMember(brokenMemberId);
        tracker.unindexMember(brokenMemberId);
        tracker.setDirty();

        if (def == null) {
            // Definition no longer registered: can't apply policy or fire callbacks. Drop the now-orphan
            // assembly if it has no members left.
            if (assembly.allMemberIds().isEmpty()) tracker.unregister(assembly.getId());
            return;
        }

        switch (def.getBreakPolicy()) {
            case BREAK_ALL -> AssemblyManager.dissolve(level, def, assembly, AssemblyBrokenContext.Reason.MEMBER_LOST);
            case DEGRADE -> {
                fireLeft(level, def, assembly, role, brokenMemberId);
                if (!AssemblyValidity.isValid(def, assembly)) {
                    AssemblyManager.dissolve(level, def, assembly, AssemblyBrokenContext.Reason.MEMBER_LOST);
                }
            }
            case PARTIAL_HOLD -> {
                fireLeft(level, def, assembly, role, brokenMemberId);
                assembly.setState(AssemblyValidity.isValid(def, assembly)
                        ? StandardAssemblyState.FORMED : StandardAssemblyState.PARTIAL);
                if (assembly.allMemberIds().isEmpty()) {
                    // Nothing left to hold onto — dissolve quietly (broken already fired per member).
                    AssemblyManager.dissolve(level, def, assembly, AssemblyBrokenContext.Reason.MEMBER_LOST);
                }
            }
        }
    }

    private static void fireLeft(ServerLevel level, AssemblyDefinition def, AssemblyInstance assembly,
                                 Optional<String> role, UUID memberId) {
        if (role.isEmpty()) return;
        AssemblyContext ctx = new AssemblyContext(level, assembly, def);
        AssemblyManager.fireMemberLeft(def, new AssemblyMemberContext(ctx, role.get(), memberId));
    }
}
