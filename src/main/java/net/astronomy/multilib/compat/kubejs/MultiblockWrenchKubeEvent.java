package net.astronomy.multilib.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.astronomy.multilib.api.event.WrenchInteractionEvent;
import net.astronomy.multilib.api.tool.WrenchResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Script-facing translation of {@link WrenchInteractionEvent}: the sealed {@link WrenchResult} Java
 * type becomes a plain status string, so scripts never need to deal with it directly - same
 * "translate the Java concept into something plain" approach as {@code MultiblockUtils}.
 */
public class MultiblockWrenchKubeEvent implements KubeEvent {
    private final WrenchInteractionEvent event;

    MultiblockWrenchKubeEvent(WrenchInteractionEvent event) {
        this.event = event;
    }

    @Nullable
    public ServerPlayer getPlayer() {
        return event.getPlayer();
    }

    public BlockPos getPos() {
        return event.getPos();
    }

    /** One of: {@code "not_a_multiblock"}, {@code "already_formed"}, {@code "mode_disallows_wrench"}, {@code "formed"}, {@code "formation_failed"}, {@code "variant_changed"}. */
    public String getStatus() {
        return switch (event.getResult()) {
            case WrenchResult.NotAMultiblock ignored -> "not_a_multiblock";
            case WrenchResult.AlreadyFormed ignored -> "already_formed";
            case WrenchResult.ModeDisallowsWrench ignored -> "mode_disallows_wrench";
            case WrenchResult.Formed ignored -> "formed";
            case WrenchResult.FormationFailed ignored -> "formation_failed";
            case WrenchResult.VariantChanged ignored -> "variant_changed";
        };
    }

    /** Null only when {@link #getStatus()} is {@code "not_a_multiblock"}. */
    @Nullable
    public ResourceLocation getMultiblockId() {
        return switch (event.getResult()) {
            case WrenchResult.AlreadyFormed already -> already.instance().getDefinitionId();
            case WrenchResult.ModeDisallowsWrench mode -> mode.definition().getId();
            case WrenchResult.Formed formed -> formed.definition().getId();
            case WrenchResult.FormationFailed failed -> failed.definition().getId();
            case WrenchResult.VariantChanged changed -> changed.definitionId();
            case WrenchResult.NotAMultiblock ignored -> null;
        };
    }

    /** Only non-null when {@link #getStatus()} is {@code "formation_failed"}. */
    @Nullable
    public String getFailureReason() {
        return event.getResult() instanceof WrenchResult.FormationFailed failed ? failed.reason() : null;
    }

    /** Only non-null when {@link #getStatus()} is {@code "variant_changed"} - the variant the instance was previously matched as. */
    @Nullable
    public String getFromVariant() {
        return event.getResult() instanceof WrenchResult.VariantChanged changed ? changed.fromVariant() : null;
    }

    /** Only non-null when {@link #getStatus()} is {@code "variant_changed"} - the variant it was just upgraded to. */
    @Nullable
    public String getToVariant() {
        return event.getResult() instanceof WrenchResult.VariantChanged changed ? changed.toVariant() : null;
    }
}
