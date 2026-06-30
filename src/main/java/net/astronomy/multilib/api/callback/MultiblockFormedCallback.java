package net.astronomy.multilib.api.callback;

@FunctionalInterface
public interface MultiblockFormedCallback {
    void onFormed(MultiblockFormedContext ctx);
}
