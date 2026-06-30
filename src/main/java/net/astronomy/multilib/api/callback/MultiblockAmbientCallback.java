package net.astronomy.multilib.api.callback;

@FunctionalInterface
public interface MultiblockAmbientCallback {
    void onAmbient(MultiblockAmbientContext ctx);
}
