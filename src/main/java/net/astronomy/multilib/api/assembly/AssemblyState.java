package net.astronomy.multilib.api.assembly;

/**
 * Runtime state of an {@link AssemblyInstance}. An interface (not an enum) so devs can add custom
 * states, mirroring {@link net.astronomy.multilib.api.state.MultiblockState}. Standard values live
 * in {@link StandardAssemblyState}. Persisted as its string id, with unknown ids falling back to
 * {@link StandardAssemblyState#UNFORMED}.
 */
public interface AssemblyState {
    String getId();
}
