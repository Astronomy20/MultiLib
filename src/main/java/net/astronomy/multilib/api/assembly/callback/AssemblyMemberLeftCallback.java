package net.astronomy.multilib.api.assembly.callback;

@FunctionalInterface
public interface AssemblyMemberLeftCallback {
    void onMemberLeft(AssemblyMemberContext ctx);
}
