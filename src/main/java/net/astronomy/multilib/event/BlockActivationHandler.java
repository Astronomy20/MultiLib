package net.astronomy.multilib.event;

import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.blockentity.IMultiblockPart;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = MultiLib.MODID)
public class BlockActivationHandler {

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Block placedBlock = event.getPlacedBlock().getBlock();
        ServerPlayer player = event.getEntity() instanceof ServerPlayer sp ? sp : null;

        List<MultiblockDefinition> candidates = MultiblockRegistry.getCandidatesFor(placedBlock);
        if (!candidates.isEmpty() && MultiLib.LOGGER.isDebugEnabled()) {
            MultiLib.LOGGER.debug("[MultiLib] onBlockPlaced: {} at {} has {} candidate definition(s): {}",
                    placedBlock, event.getPos(), candidates.size(), candidates.stream().map(d -> d.getId().toString()).toList());
        }

        for (MultiblockDefinition definition : candidates) {
            if (!definition.getFormationMode().allowsAutomatic()) continue;
            if (!definition.hasActivation()) continue;

            char activationSym = definition.getActivationSymbol();
            BlockIngredient activationIngredient = definition.getBlockMap().get(activationSym);
            if (activationIngredient == null || !activationIngredient.matches(event.getPlacedBlock())) continue;

            MatchResult result = PatternMatcher.matches(level, event.getPos(), definition);
            if (result instanceof MatchResult.Success success) {
                handleFormation(level, definition, success.data(), player);
            } else if (result instanceof MatchResult.Failure failure) {
                MultiLib.LOGGER.debug("[MultiLib] {} did not match at {}: {}",
                        definition.getId(), event.getPos(), failure.report().summary());
            }
        }
    }

    private static void handleFormation(ServerLevel level, MultiblockDefinition definition, MatchData matchData,
                                         @Nullable ServerPlayer player) {
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

        // Formation always drives the controller to IDLE (see AbstractMultiblockControllerBE#onStructureFormed),
        // so recording IDLE here also covers "formed at least once" progression semantics. Anonymous
        // formations (e.g. a dispenser placing the activation block) have no formedBy and are skipped.
        formedBy.ifPresent(playerId ->
                MultiblockProgressionTracker.get(level.getServer().overworld())
                        .recordStateReached(playerId, definition.getId(), StandardMultiblockState.IDLE.getId(),
                                level.getServer().overworld().getGameTime()));

        MultiblockFormedContext formedCtx = new MultiblockFormedContext(ctx);
        for (MultiblockFormedCallback cb : definition.getFormedCallbacks()) {
            cb.onFormed(formedCtx);
        }

        if (definition.hasCore()) {
            instance.getCorePos().ifPresent(corePos -> {
                if (level.getBlockEntity(corePos) instanceof AbstractMultiblockControllerBE controller) {
                    controller.onStructureFormed(formedCtx);
                }
            });
        }

        for (BlockPos pos : instance.getPositions()) {
            if (level.getBlockEntity(pos) instanceof IMultiblockPart part) {
                part.getMultiblockComponent().onJoinedStructure(instance);
            }
        }
    }

    public static void triggerFormationAt(ServerLevel level, BlockPos pos) {
        triggerFormationAt(level, pos, null);
    }

    public static void triggerFormationAt(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        Block block = level.getBlockState(pos).getBlock();
        List<MultiblockDefinition> candidates = MultiblockRegistry.getCandidatesFor(block);
        BlockState state = level.getBlockState(pos);
        for (MultiblockDefinition definition : candidates) {
            // Accept either the activation block or the core block at pos — they're often the same
            // symbol, but a structure can split them (e.g. activation = placed last, core = controller).
            if (!definition.matchesActivationOrCore(state)) continue;

            WorldMultiblockTracker tracker = WorldMultiblockTracker.get(level);
            if (!tracker.getInstancesAt(pos).isEmpty()) continue;

            MatchResult result = PatternMatcher.matches(level, pos, definition);
            if (result instanceof MatchResult.Success success) {
                handleFormation(level, definition, success.data(), player);
                break;
            }
        }
    }
}
