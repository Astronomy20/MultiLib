package net.astronomy.multilib.example;

import net.astronomy.multilib.api.blockentity.AbstractMultiblockControllerBE;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.api.tool.IMultiblockWrench;
import net.astronomy.multilib.core.matching.MatchResult;
import net.astronomy.multilib.core.matching.PatternMatcher;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.astronomy.multilib.event.BlockActivationHandler;
import net.astronomy.multilib.MultiLib;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reference {@link IMultiblockWrench} implementation, kept here as a usage example for third-party
 * devs. MultiLib itself does not ship a wrench — implement IMultiblockWrench on your own item, or
 * copy this one, if you want manual formation/inspection in your mod.
 */
public class ExampleWrenchItem extends Item implements IMultiblockWrench {

    public ExampleWrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) return InteractionResult.PASS;

        ServerLevel serverLevel = (ServerLevel) level;
        BlockPos pos = context.getClickedPos();
        Block block = serverLevel.getBlockState(pos).getBlock();
        Player player = context.getPlayer();

        List<MultiblockDefinition> candidates = MultiblockRegistry.getCandidatesFor(block);
        MultiLib.LOGGER.debug("[MultiLib] Wrench used on {} at {}: {} candidate definition(s)",
                block, pos, candidates.size());
        if (candidates.isEmpty() && player != null) {
            player.sendSystemMessage(Component.literal(
                    "[MultiLib] This block (" + block + ") isn't part of any registered multiblock."));
        }

        for (MultiblockDefinition definition : candidates) {
            // Accept either the activation block or the core block — they're often the same symbol,
            // but a structure can split them (e.g. activation = placed last, core = the controller).
            if (!definition.matchesActivationOrCore(serverLevel.getBlockState(pos))) continue;

            WorldMultiblockTracker tracker = WorldMultiblockTracker.get(serverLevel);
            Set<MultiblockInstance> instances = tracker.getInstancesAt(pos);

            if (!instances.isEmpty()) {
                MultiblockInstance instance = instances.iterator().next();
                String stateId = "unknown";
                if (definition.hasCore()) {
                    Optional<BlockPos> corePosOpt = instance.getCorePos();
                    if (corePosOpt.isPresent()
                            && serverLevel.getBlockEntity(corePosOpt.get()) instanceof AbstractMultiblockControllerBE ctrl) {
                        stateId = ctrl.getState().getId();
                    }
                }
                if (player != null) {
                    player.sendSystemMessage(Component.translatable(
                        "multilib.wrench.formed",
                        instance.getDefinitionId().toString(),
                        stateId
                    ));
                }
                return InteractionResult.SUCCESS;
            }

            if (!definition.getFormationMode().allowsWrench()) {
                if (player != null) {
                    player.sendSystemMessage(Component.translatable("multilib.wrench.mode_disallows_wrench"));
                }
                return InteractionResult.SUCCESS;
            }

            int prevCount = tracker.getAllInstances().size();
            BlockActivationHandler.triggerFormationAt(serverLevel, pos);
            int newCount = tracker.getAllInstances().size();

            if (player != null) {
                if (newCount > prevCount) {
                    player.sendSystemMessage(Component.translatable("multilib.wrench.formed_success"));
                } else {
                    MatchResult result = PatternMatcher.matches(serverLevel, pos, definition);
                    if (result instanceof MatchResult.Failure failure) {
                        player.sendSystemMessage(Component.translatable(
                            "multilib.wrench.failed", failure.report().summary()));
                    }
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
