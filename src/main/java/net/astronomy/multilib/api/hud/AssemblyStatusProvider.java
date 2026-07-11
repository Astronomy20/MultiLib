package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.api.assembly.AssemblyDefinition;
import net.astronomy.multilib.api.assembly.AssemblyInstance;
import net.astronomy.multilib.core.assembly.AssemblyRegistry;
import net.astronomy.multilib.core.assembly.WorldAssemblyTracker;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Adds an assembly status line to the hover info of any member sub-structure that belongs to an
 * assembly: the assembly's state plus, if it has a master role, the member count of every role.
 * Reuses the existing multiblock HUD channel (Jade/TOP) — no new packets. Registered globally by
 * {@code AssemblyReloadSetup}; appends nothing for members not in an assembly.
 */
public final class AssemblyStatusProvider implements MultiblockHudProvider {

    @Override
    public void appendHudEntries(HudContext ctx, Consumer<HudEntry> out) {
        Optional<AssemblyInstance> assemblyOpt =
                WorldAssemblyTracker.get(ctx.level()).getByMember(ctx.instance().getId());
        if (assemblyOpt.isEmpty()) return;
        AssemblyInstance assembly = assemblyOpt.get();

        AssemblyDefinition def = AssemblyRegistry.get(assembly.getDefinitionId()).orElse(null);
        out.accept(new HudEntry.Text(Component.translatable(
                "hud.multilib.assembly.status", assembly.getState().getId())));

        if (def != null) {
            for (String role : def.getRoles().keySet()) {
                out.accept(new HudEntry.Text(Component.translatable(
                        "hud.multilib.assembly.role", role, assembly.memberCount(role))));
            }
        }
    }
}
