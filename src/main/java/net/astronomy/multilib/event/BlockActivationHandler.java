package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.blockentity.IMultiblockPart;
import net.astronomy.multilib.api.callback.MultiblockBrokenContext;
import net.astronomy.multilib.api.callback.MultiblockFormedCallback;
import net.astronomy.multilib.api.callback.MultiblockFormedContext;
import net.astronomy.multilib.api.definition.FormationMode;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.event.MultiblockFormedEvent;
import net.astronomy.multilib.api.instance.MultiblockContext;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.api.ingredient.BlockIngredient;
import net.astronomy.multilib.api.validation.ValidationResult;
import net.astronomy.multilib.core.matching.MatchData;
import net.astronomy.multilib.core.matching.MatchResult;
import net.astronomy.multilib.core.matching.PatternMatcher;
import net.astronomy.multilib.api.state.StandardMultiblockState;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.core.tracking.MultiblockProgressionTracker;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = MultiLib.MODID)
public class BlockActivationHandler {

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Block placedBlock = event.getPlacedBlock().getBlock();
        ServerPlayer player = event.getEntity() instanceof ServerPlayer sp ? sp : null;

        List<MultiblockDefinition> candidates = MultiblockRegistry.getCandidatesFor(placedBlock);
        // TEMP DEBUG - remove once the "block vanishes right after placing" bug is confirmed fixed.
        MultiLib.LOGGER.info("[MultiLib DEBUG] onBlockPlaced: {} at {}, {} candidate definition(s)",
                placedBlock, event.getPos(), candidates.size());

        for (MultiblockDefinition definition : candidates) {
            if (!definition.getFormationMode().allowsAutomatic()) continue;
            if (!definition.hasActivation()) continue;

            char activationSym = definition.getActivationSymbol();
            BlockIngredient activationIngredient = definition.getBlockMap().get(activationSym);
            if (activationIngredient == null || !activationIngredient.matches(event.getPlacedBlock())) continue;

            MatchResult result = PatternMatcher.matches(level, event.getPos(), definition);
            if (result instanceof MatchResult.Success success) {
                MultiLib.LOGGER.info("[MultiLib DEBUG] onBlockPlaced: {} MATCHED at {} with {} positions - "
                                + "calling handleFormation", definition.getId(), event.getPos(),
                        success.data().positions().size());
                handleFormation(level, definition, success.data(), player);
            } else if (result instanceof MatchResult.Failure failure) {
                MultiLib.LOGGER.info("[MultiLib DEBUG] {} did not match at {}: {}",
                        definition.getId(), event.getPos(), failure.report().summary());
            }
        }
    }

    private static void handleFormation(ServerLevel level, MultiblockDefinition definition, MatchData matchData,
                                         @Nullable ServerPlayer player) {
        // A newly placed block that matches this definition's activation symbol re-triggers matching
        // from ITS position (see onBlockPlaced) - for a structure built out of a common material
        // (typical for a shapeless solid-fill definition), a later block placed nearby but outside the
        // already-formed instance still floods straight into that instance's own positions (they're
        // still the same connected material), producing another MatchResult.Success that overlaps the
        // first.
        Set<MultiblockInstance> overlapping = existingInstancesOfSameDefinition(level, definition, matchData);
        if (!overlapping.isEmpty()) {
            boolean identical = overlapping.size() == 1
                    && overlapping.iterator().next().getPositions().equals(matchData.positions());
            if (identical) {
                // Exact same structure, unchanged - an unrelated placement elsewhere just re-discovered
                // it via flood fill. Not a new or different structure; nothing to do. Without this,
                // every such placement re-ran validator/formed callbacks and registered a duplicate
                // instance on top of the one already formed - "re-validated on every block placed".
                return;
            }
            // The new match differs from what's currently registered - e.g. the player extended a
            // smaller already-formed structure into a bigger one (stone is the activation block, they
            // built a taller box around the existing one). That's a real change: the stale instance(s)
            // need to be broken properly (broken callbacks + formed-property reset, same as any other
            // break - see BlockBreakHandler) before the new, current match is formed below. Skipping
            // unconditionally here (the old behavior) would silently ignore legitimate growth instead
            // of forming the now-larger structure and retiring the smaller one.
            WorldMultiblockTracker replacedTracker = WorldMultiblockTracker.get(level);
            for (MultiblockInstance old : overlapping) {
                BlockBreakHandler.handleBreak(level, replacedTracker, old, matchData.origin(),
                        MultiblockBrokenContext.BreakReason.REPLACED);
            }
        }

        Optional<UUID> formedBy = player != null ? Optional.of(player.getUUID()) : Optional.empty();

        if (definition.getValidator().isPresent()) {
            UUID tempId = UUID.randomUUID();
            MultiblockInstance tempInstance = new MultiblockInstance(
                    tempId, definition.getId(), matchData.origin(), matchData.transform(), matchData, formedBy);
            MultiblockContext ctx = MultiblockContext.of(level, tempInstance);
            ValidationResult vResult = definition.getValidator().get().validate(ctx);
            if (vResult instanceof ValidationResult.Invalid invalid) {
                MultiLib.LOGGER.debug("[MultiLib] Formation blocked by validator: {}", invalid.message());
                return;
            }
        }

        MultiblockInstance instance = new MultiblockInstance(
                UUID.randomUUID(), definition.getId(),
                matchData.origin(), matchData.transform(), matchData, formedBy);
        MultiblockContext ctx = MultiblockContext.of(level, instance);

        MultiblockFormedEvent formedEvent = new MultiblockFormedEvent(ctx);
        NeoForge.EVENT_BUS.post(formedEvent);
        if (formedEvent.isCanceled()) return;

        WorldMultiblockTracker tracker = WorldMultiblockTracker.get(level);
        tracker.register(instance, definition);

        // Applied before the formed callbacks below fire, so that any callback (Java/JSON/KubeJS) that
        // reads world state observes the structure already in its final "formed" blockstate rather than
        // catching it mid-flip. See MultiblockBuilder#formedProperty for the footgun this can create if
        // a pattern ingredient matches on the same property.
        definition.getFormedProperty().ifPresent(propertyName -> {
            for (BlockPos pos : instance.getPositions()) {
                setFormedPropertyIfPresent(level, pos, propertyName, true);
            }
        });

        // Formation always drives the controller to IDLE (see AbstractMultiblockControllerBE#onStructureFormed),
        // so recording IDLE here also covers "formed at least once" progression semantics. Anonymous
        // formations (e.g. a dispenser placing the activation block) have no formedBy and are skipped.
        formedBy.ifPresent(playerId ->
                MultiblockProgressionTracker.get(level.getServer().overworld())
                        .recordStateReached(playerId, definition.getId(), StandardMultiblockState.IDLE.getId(),
                                level.getServer().overworld().getGameTime()));

        MultiblockFormedContext formedCtx = new MultiblockFormedContext(ctx);
        for (MultiblockFormedCallback cb : definition.getFormedCallbacks()) {
            try {
                cb.onFormed(formedCtx);
            } catch (Exception e) {
                // A callback throwing (Java, JSON validator, or a KubeJS .onFormed(...) script bug)
                // must not stop the structure from finishing formation, and must not stay silent -
                // without this, a script exception here could otherwise surface as "nothing happened"
                // with no indication why.
                MultiLib.LOGGER.error("[MultiLib] onFormed callback for '{}' threw", definition.getId(), e);
            }
        }

        resolveControllerPos(definition, instance).ifPresent(controllerPos -> {
            if (level.getBlockEntity(controllerPos) instanceof AbstractMultiblockControllerBE controller) {
                controller.onStructureFormed(formedCtx);
            }
        });

        for (BlockPos pos : instance.getPositions()) {
            if (level.getBlockEntity(pos) instanceof IMultiblockPart part) {
                part.getMultiblockComponent().onJoinedStructure(instance);
            }
        }
    }

    /** Every distinct tracked instance of {@code definition} itself that shares at least one position with {@code matchData} - see {@link #handleFormation}'s guard comment. */
    private static Set<MultiblockInstance> existingInstancesOfSameDefinition(ServerLevel level, MultiblockDefinition definition, MatchData matchData) {
        WorldMultiblockTracker tracker = WorldMultiblockTracker.get(level);
        Set<MultiblockInstance> result = new HashSet<>();
        for (BlockPos pos : matchData.positions()) {
            for (MultiblockInstance existing : tracker.getInstancesAt(pos)) {
                if (existing.getDefinitionId().equals(definition.getId())) result.add(existing);
            }
        }
        return result;
    }

    /**
     * Which position's block entity should receive controller callbacks ({@code onStructureFormed}/
     * {@code onStructureBroken}) for {@code instance} - the declared core if there is one, otherwise
     * falls back to (any one of) the activation symbol's own positions. Without this fallback, a
     * definition that never bothers declaring a separate {@code .core(...)} - the common case for a
     * uniform-material shapeless structure, where the activation symbol already is the only material -
     * would never fire controller callbacks at all (per {@code MultiblockDefinition#hasCore}), silently
     * disabling any state/component (e.g. a fluid tank) a controller block entity is meant to expose,
     * even though the block placed is perfectly capable of hosting one.
     */
    public static Optional<BlockPos> resolveControllerPos(MultiblockDefinition definition, MultiblockInstance instance) {
        if (definition.hasCore()) {
            Optional<BlockPos> corePos = instance.getCorePos();
            if (corePos.isPresent()) return corePos;
        }
        if (definition.hasActivation()) {
            Set<BlockPos> activationPositions = instance.getPositionsFor(definition.getActivationSymbol());
            if (!activationPositions.isEmpty()) return Optional.of(activationPositions.iterator().next());
        }
        return Optional.empty();
    }

    public static void triggerFormationAt(ServerLevel level, BlockPos pos) {
        triggerFormationAt(level, pos, null);
    }

    /** @return true if a definition actually matched and was formed from {@code pos}. */
    public static boolean triggerFormationAt(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        Block block = level.getBlockState(pos).getBlock();
        List<MultiblockDefinition> candidates = MultiblockRegistry.getCandidatesFor(block);
        BlockState state = level.getBlockState(pos);
        for (MultiblockDefinition definition : candidates) {
            // Accept either the activation block or the core block at pos - they're often the same
            // symbol, but a structure can split them (e.g. activation = placed last, core = controller).
            if (!definition.matchesActivationOrCore(state)) continue;

            WorldMultiblockTracker tracker = WorldMultiblockTracker.get(level);
            if (!tracker.getInstancesAt(pos).isEmpty()) continue;

            MatchResult result = PatternMatcher.matches(level, pos, definition);
            if (result instanceof MatchResult.Success success) {
                handleFormation(level, definition, success.data(), player);
                return true;
            }
        }
        return false;
    }

    /**
     * Flips {@code propertyName} to {@code value} on the block currently at {@code pos}, if (and only
     * if) that block's state definition actually declares a {@link BooleanProperty} of that name.
     * Silently does nothing for an unloaded position or a block with no such property - see
     * {@link net.astronomy.multilib.api.definition.MultiblockBuilder#formedProperty} for why this must
     * never crash on an arbitrary member block that doesn't opt in.
     */
    static void setFormedPropertyIfPresent(ServerLevel level, BlockPos pos, String propertyName, boolean value) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
        if (!(property instanceof BooleanProperty boolProperty)) return;
        if (state.getValue(boolProperty) == value) return;
        level.setBlock(pos, state.setValue(boolProperty, value), 3);
    }
}
