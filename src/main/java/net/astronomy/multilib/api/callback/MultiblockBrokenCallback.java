package net.astronomy.multilib.api.callback;

@FunctionalInterface
public interface MultiblockBrokenCallback {
    void onBroken(MultiblockBrokenContext ctx);
}
