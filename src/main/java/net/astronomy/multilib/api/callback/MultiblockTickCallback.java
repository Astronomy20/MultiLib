package net.astronomy.multilib.api.callback;

@FunctionalInterface
public interface MultiblockTickCallback {
    void onTick(MultiblockTickContext ctx);
}
