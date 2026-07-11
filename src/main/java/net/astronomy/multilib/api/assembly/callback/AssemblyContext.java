package net.astronomy.multilib.api.assembly.callback;

import net.astronomy.multilib.api.assembly.AssemblyDefinition;
import net.astronomy.multilib.api.assembly.AssemblyInstance;
import net.minecraft.server.level.ServerLevel;

/**
 * Everything a callback needs to reason about an assembly: the level it lives in, its runtime
 * instance, and its immutable definition.
 */
public record AssemblyContext(
        ServerLevel level,
        AssemblyInstance instance,
        AssemblyDefinition definition
) {}
