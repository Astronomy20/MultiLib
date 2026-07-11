package net.astronomy.multilib.api.assembly.callback;

import java.util.UUID;

/** Passed to {@link AssemblyMemberJoinedCallback}/{@link AssemblyMemberLeftCallback}. */
public record AssemblyMemberContext(AssemblyContext context, String role, UUID memberId) {}
