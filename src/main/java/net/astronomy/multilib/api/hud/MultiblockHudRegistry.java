package net.astronomy.multilib.api.hud;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The bridge between mods that describe what to show (via {@link MultiblockHudProvider}) and whichever
 * hover-info viewer mod (Jade, The One Probe, ...) the player actually has installed. MultiLib itself
 * never renders anything - {@code compat/jade}/{@code compat/top} call {@link #gatherEntries}/
 * {@link #gatherUnformedEntries} and translate the resulting {@link HudEntry} list into that viewer's
 * own UI primitives.
 *
 * <h2>Registration</h2>
 * {@link #registerGlobal} runs a provider for every formed multiblock, regardless of definition;
 * {@link #register} runs one only for a specific definition id. Both simply append to an
 * insertion-ordered list - {@link #gatherEntries} runs global providers first, then that definition's
 * own, in registration order.
 *
 * <h2>Dev opt-out</h2>
 * {@link #setHudEnabled} is a per-definition killswitch: while disabled, {@link #gatherEntries} returns
 * an empty list for that definition, including {@link FormedStatusProvider}'s default line. Every piece
 * of info exposed through this bridge must be suppressible by the dev - same "mechanism only, UX is the
 * dev's call" policy as the rest of this library.
 *
 * <h2>Thread-safety</h2>
 * All registration ({@link #registerGlobal}, {@link #register}, {@link #setHudEnabled},
 * {@link #setUnformedHintsEnabled}) is expected to happen during mod init - single-threaded, before any
 * world is ticking. {@link #gatherEntries}/{@link #gatherUnformedEntries} are expected to run on the
 * server thread only. Nothing here is synchronized, matching every other MultiLib registry
 * ({@code MultiblockRegistry}, {@code MultiblockStateRegistry}, ...).
 */
public final class MultiblockHudRegistry {

    private static final List<MultiblockHudProvider> GLOBAL_PROVIDERS = new ArrayList<>();
    private static final Map<ResourceLocation, List<MultiblockHudProvider>> PER_DEFINITION_PROVIDERS = new HashMap<>();
    private static final Set<ResourceLocation> DISABLED_DEFINITIONS = new HashSet<>();
    private static boolean unformedHintsEnabled = false;

    static {
        // The only default-on provider - see FormedStatusProvider's own javadoc. Every other built-in
        // provider in this package is opt-in: the dev registers it explicitly for the definitions it
        // applies to. setHudEnabled(id, false) suppresses even this one, per definition.
        registerGlobal(new FormedStatusProvider());
    }

    private MultiblockHudRegistry() {}

    /** Registers {@code provider} to run for every formed multiblock instance, regardless of definition. */
    public static void registerGlobal(MultiblockHudProvider provider) {
        GLOBAL_PROVIDERS.add(provider);
    }

    /** Registers {@code provider} to run only for instances of {@code definitionId}. */
    public static void register(ResourceLocation definitionId, MultiblockHudProvider provider) {
        PER_DEFINITION_PROVIDERS.computeIfAbsent(definitionId, k -> new ArrayList<>()).add(provider);
    }

    /**
     * Dev opt-out killswitch: while {@code enabled} is {@code false} for {@code definitionId},
     * {@link #gatherEntries} returns an empty list for that definition - not even
     * {@link FormedStatusProvider}'s default line is shown. Defaults to enabled for every definition.
     */
    public static void setHudEnabled(ResourceLocation definitionId, boolean enabled) {
        if (enabled) {
            DISABLED_DEFINITIONS.remove(definitionId);
        } else {
            DISABLED_DEFINITIONS.add(definitionId);
        }
    }

    public static boolean isHudEnabled(ResourceLocation definitionId) {
        return !DISABLED_DEFINITIONS.contains(definitionId);
    }

    /**
     * Global mechanism switch for {@link MissingBlocksProvider}'s "why isn't this formed yet" hints on
     * unformed structures (see {@link #gatherUnformedEntries}). Defaults to {@code false} - MultiLib
     * never shows anything about a player's build-in-progress unless the dev opts in, same as every
     * other opt-in feature in this library.
     */
    public static void setUnformedHintsEnabled(boolean enabled) {
        unformedHintsEnabled = enabled;
    }

    public static boolean isUnformedHintsEnabled() {
        return unformedHintsEnabled;
    }

    /**
     * Runs every registered global provider, then every provider registered specifically for
     * {@code ctx.definition()}'s id, in registration order, collecting whatever {@link HudEntry}
     * instances they append. A provider that throws is caught, logged via {@link MultiLib#LOGGER}, and
     * skipped - one misbehaving provider never blanks out every other provider's output.
     */
    public static List<HudEntry> gatherEntries(HudContext ctx) {
        ResourceLocation definitionId = ctx.definition().getId();
        if (!isHudEnabled(definitionId)) return List.of();

        List<HudEntry> out = new ArrayList<>();
        Consumer<HudEntry> collector = out::add;
        runProviders(GLOBAL_PROVIDERS, ctx, collector);
        runProviders(PER_DEFINITION_PROVIDERS.getOrDefault(definitionId, List.of()), ctx, collector);
        return List.copyOf(out);
    }

    private static void runProviders(List<MultiblockHudProvider> providers, HudContext ctx, Consumer<HudEntry> out) {
        for (MultiblockHudProvider provider : providers) {
            try {
                provider.appendHudEntries(ctx, out);
            } catch (Exception e) {
                MultiLib.LOGGER.error("[MultiLib] HUD provider {} threw while gathering entries for {}",
                        provider.getClass().getName(), ctx.definition().getId(), e);
            }
        }
    }

    /**
     * The unformed-structure counterpart to {@link #gatherEntries}: for a position that is NOT
     * currently part of any formed instance, reports why (currently just "N of M blocks placed", via
     * {@code MultiblockProgressAPI}) if {@link #setUnformedHintsEnabled} has been turned on and
     * {@code pos} holds the declared core block of some registered, layer-based definition. Returns an
     * empty list in every other case: hints disabled, an instance IS already formed there, {@code pos}
     * isn't a core block, or the matched definition's pattern isn't progress-trackable (see
     * {@code MultiblockProgressAPI#compute}).
     *
     * @param player unused by the built-in {@link MissingBlocksProvider}, but kept in the signature (as
     *               with {@link HudContext}) so a future unformed-structure provider can be player-aware
     *               without another signature change.
     */
    public static List<HudEntry> gatherUnformedEntries(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!unformedHintsEnabled) return List.of();
        if (!WorldMultiblockTracker.get(level).getInstancesAt(pos).isEmpty()) return List.of();
        return MissingBlocksProvider.gather(level, pos);
    }
}
