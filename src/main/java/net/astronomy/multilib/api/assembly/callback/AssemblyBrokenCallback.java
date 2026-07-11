package net.astronomy.multilib.api.assembly.callback;

@FunctionalInterface
public interface AssemblyBrokenCallback {
    void onBroken(AssemblyBrokenContext ctx);
}
