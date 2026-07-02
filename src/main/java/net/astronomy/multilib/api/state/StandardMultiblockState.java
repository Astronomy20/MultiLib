package net.astronomy.multilib.api.state;

import net.minecraft.resources.ResourceLocation;

public final class StandardMultiblockState {
    public static final MultiblockState UNFORMED = MultiblockStateRegistry.register(
        ResourceLocation.fromNamespaceAndPath("multilib", "unformed"), "multilib.state.unformed");
    public static final MultiblockState IDLE = MultiblockStateRegistry.register(
        ResourceLocation.fromNamespaceAndPath("multilib", "idle"), "multilib.state.idle");
    public static final MultiblockState RUNNING = MultiblockStateRegistry.register(
        ResourceLocation.fromNamespaceAndPath("multilib", "running"), "multilib.state.running");
    public static final MultiblockState ERROR = MultiblockStateRegistry.register(
        ResourceLocation.fromNamespaceAndPath("multilib", "error"), "multilib.state.error");

    private StandardMultiblockState() {}

    /**
     * No-op — calling this forces the class to load (and its fields above to register) without
     * needing an unused-looking bare field reference at the call site. MultiLib calls this before
     * {@link MultiblockStateRegistry#freeze()} for the exact reason documented on
     * {@link MultiblockStateRegistry#register}: registration must happen before freeze, and Java only
     * runs a class's static initializer the first time something touches it.
     */
    public static void touch() {}
}
