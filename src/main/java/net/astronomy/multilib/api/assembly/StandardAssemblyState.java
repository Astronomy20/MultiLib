package net.astronomy.multilib.api.assembly;

/**
 * Built-in {@link AssemblyState} values.
 */
public enum StandardAssemblyState implements AssemblyState {
    /** No assembly present. */
    UNFORMED("unformed"),
    /** Formed once, but a member is currently missing (e.g. unloaded chunk) — waiting to recompose. */
    PARTIAL("partial"),
    /** Fully formed and valid. */
    FORMED("formed"),
    /** Formed and actively doing work (dev-driven). */
    RUNNING("running");

    private final String id;

    StandardAssemblyState(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return "multilib:" + id;
    }

    /** Resolves a persisted id back to a standard state, defaulting to {@link #UNFORMED}. */
    public static AssemblyState byId(String id) {
        for (StandardAssemblyState s : values()) {
            if (s.getId().equals(id)) return s;
        }
        return UNFORMED;
    }
}
