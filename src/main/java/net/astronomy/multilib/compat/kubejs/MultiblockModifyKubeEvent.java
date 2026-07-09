package net.astronomy.multilib.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.script.ConsoleJS;
import net.astronomy.multilib.api.MultiLib;
import net.astronomy.multilib.api.definition.MultiblockBuilder;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Fired once per multiblock JSON datapack (re)load so scripts can patch existing definitions -
 * Java, JSON, or previously KubeJS-defined.
 * <p>
 * Doesn't delegate to {@link MultiLib#redefine} (unlike everything else here, which reuses the
 * shared Java API rather than re-implementing it) specifically because that method's
 * {@code Optional<MultiblockDefinition>} return can't distinguish "no such id" from "validation
 * failed" or carry the failed builder's {@link MultiblockBuilder#getLastValidationError()} back out -
 * both needed here to report an accurate reason to the KubeJS console instead of a generic failure.
 */
public class MultiblockModifyKubeEvent implements KubeEvent {

    public boolean modify(ResourceLocation id, Consumer<MultiblockBuilder> callback) {
        Optional<MultiblockDefinition> existing = MultiLib.getDefinition(id);
        if (existing.isEmpty()) {
            ConsoleJS.SERVER.error("[MultiLib] MultiblockEvents.modify: no multiblock registered with id '" + id + "'");
            return false;
        }

        // silenceDevModeChat(): this reports failures to the KubeJS console below instead - a chat
        // broadcast on top of that would just show the player the same thing twice.
        MultiblockBuilder builder = existing.get().toBuilder().silenceDevModeChat();
        callback.accept(builder);
        MultiblockDefinition rebuilt = builder.buildWithoutRegistering();
        if (rebuilt == null) {
            ConsoleJS.SERVER.error("[MultiLib] MultiblockEvents.modify: '" + id + "' not updated: "
                    + builder.getLastValidationError());
            return false;
        }
        MultiblockRegistry.replace(id, rebuilt);
        return true;
    }
}
