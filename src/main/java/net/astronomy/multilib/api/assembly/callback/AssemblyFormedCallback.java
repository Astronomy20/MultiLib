package net.astronomy.multilib.api.assembly.callback;

@FunctionalInterface
public interface AssemblyFormedCallback {
    void onFormed(AssemblyFormedContext ctx);
}
