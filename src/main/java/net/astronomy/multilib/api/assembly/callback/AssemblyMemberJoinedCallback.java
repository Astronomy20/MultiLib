package net.astronomy.multilib.api.assembly.callback;

@FunctionalInterface
public interface AssemblyMemberJoinedCallback {
    void onMemberJoined(AssemblyMemberContext ctx);
}
