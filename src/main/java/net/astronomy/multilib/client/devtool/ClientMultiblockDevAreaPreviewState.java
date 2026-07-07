package net.astronomy.multilib.client.devtool;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side-only preview of the area a {@code MultiblockDevBlockEntity} is about to scan, driven
 * entirely by the "Render" button in {@code MultiblockDevScreen} - unlike the actual scan (Detect),
 * this never touches the server: offset/size are already known client-side (whatever's currently typed
 * in the GUI, not necessarily saved yet), so the bounding box is computed locally and handed here for
 * {@link MultiblockDevGlowRenderer} to draw every frame.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientMultiblockDevAreaPreviewState {

    private ClientMultiblockDevAreaPreviewState() {}

    /**
     * Inclusive world-space bounding box.
     *
     * @param ownerPos the dev-block that produced this box - lets {@link MultiblockDevGlowRenderer}
     *                 self-heal by checking every frame whether that block still exists, rather than
     *                 relying solely on {@code MultiblockDevBlock#onRemove} firing at the right time to
     *                 call {@link #clear()} (block removal client-side prediction/ordering isn't always
     *                 as immediate as a plain break, e.g. explosions or a piston pulling it away).
     */
    public record Box(BlockPos min, BlockPos max, BlockPos ownerPos) {}

    private static @Nullable Box current;

    public static void set(Box box) {
        current = box;
    }

    /** Turns the preview off if it's already showing exactly {@code box}, otherwise (re)shows it. */
    public static void toggle(Box box) {
        current = box.equals(current) ? null : box;
    }

    public static void clear() {
        current = null;
    }

    public static @Nullable Box get() {
        return current;
    }
}
