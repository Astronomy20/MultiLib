package net.astronomy.multilib.api.definition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Governs how a structure is allowed to form. Not a plain enum so third-party mods can register
 * their own modes (e.g. a redstone-pulse trigger) via {@link #register} — code must go through
 * {@link #allowsAutomatic()} / {@link #allowsWrench()} rather than switching on a fixed set.
 */
public final class FormationMode {
    private static final Map<String, FormationMode> REGISTRY = new LinkedHashMap<>();

    /** Forms automatically when the activation symbol is placed; cannot be force-triggered by a wrench. */
    public static final FormationMode AUTOMATIC = register("automatic", true, false);
    /** Never forms automatically; only a wrench (or other manual trigger) can form it. */
    public static final FormationMode WRENCH = register("wrench", false, true);
    /** Forms automatically on placement, and can also be force-triggered manually. */
    public static final FormationMode AUTOMATIC_AND_WRENCH = register("automatic_and_wrench", true, true);

    private final String id;
    private final boolean allowsAutomatic;
    private final boolean allowsWrench;

    private FormationMode(String id, boolean allowsAutomatic, boolean allowsWrench) {
        this.id = id;
        this.allowsAutomatic = allowsAutomatic;
        this.allowsWrench = allowsWrench;
    }

    /** Registers a new formation mode under a unique id. Throws if the id is already taken. */
    public static FormationMode register(String id, boolean allowsAutomatic, boolean allowsWrench) {
        if (REGISTRY.containsKey(id)) {
            throw new IllegalStateException("FormationMode '" + id + "' is already registered");
        }
        FormationMode mode = new FormationMode(id, allowsAutomatic, allowsWrench);
        REGISTRY.put(id, mode);
        return mode;
    }

    public static FormationMode byId(String id) {
        return REGISTRY.get(id);
    }

    public String getId() { return id; }
    public boolean allowsAutomatic() { return allowsAutomatic; }
    public boolean allowsWrench() { return allowsWrench; }

    @Override public String toString() { return id; }
}
