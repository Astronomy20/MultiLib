package net.astronomy.multilib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.callback.MultiblockBrokenContext;
import net.astronomy.multilib.api.definition.MultiblockDefinition;
import net.astronomy.multilib.api.instance.MultiblockInstance;
import net.astronomy.multilib.core.registry.MultiblockAmbiguityResolver;
import net.astronomy.multilib.core.registry.MultiblockRegistry;
import net.astronomy.multilib.core.tracking.WorldMultiblockTracker;
import net.astronomy.multilib.event.BlockActivationHandler;
import net.astronomy.multilib.event.BlockBreakHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Admin/debug commands under {@code /multilib}, gated to permission level 2 (same level vanilla
 * requires for {@code /gamerule} etc.). Registers itself via {@link RegisterCommandsEvent} - no change
 * to {@code MultiLib.java} needed, this class wires up on its own the moment it's loaded.
 * <p>
 * Every subcommand is server-side only (backed by {@link CommandSourceStack#getLevel()}, which is
 * always a {@code ServerLevel}) and null-safe about the sender: {@code list}/{@code info} don't need a
 * position at all, {@code form}/{@code unform} take their position as an explicit argument (so they
 * work from console/command blocks too), and only {@code instances <radius>} - whose radius is
 * relative to the sender - requires the command to be run by a player.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class MultiblockCommands {

    private MultiblockCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("multilib")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .executes(MultiblockCommands::list))
                .then(Commands.literal("info")
                        .then(Commands.argument("definition", ResourceLocationArgument.id())
                                .suggests(MultiblockCommands::suggestDefinitionIds)
                                .executes(MultiblockCommands::info)))
                .then(Commands.literal("instances")
                        .executes(MultiblockCommands::instancesAll)
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0))
                                .executes(MultiblockCommands::instancesRadius)))
                // triggerFormationAt(ServerLevel, BlockPos, ServerPlayer) tries every candidate
                // definition registered for the block currently at `pos` and forms the first one whose
                // pattern matches around that position - it has no way to be pointed at one specific
                // definition id. So `form` only takes a position, not `form <definitionId> <pos>`; see
                // BlockActivationHandler#triggerFormationAt.
                .then(Commands.literal("form")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(MultiblockCommands::form)))
                .then(Commands.literal("unform")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(MultiblockCommands::unform)))
                .then(Commands.literal("prefer")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .then(Commands.argument("definition", ResourceLocationArgument.id())
                                        .suggests(MultiblockCommands::suggestPreferCandidates)
                                        .executes(MultiblockCommands::preferSet)))
                        .then(Commands.literal("clear")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(MultiblockCommands::preferClear))))
        );
    }

    private static CompletableFuture<Suggestions> suggestDefinitionIds(CommandContext<CommandSourceStack> ctx,
                                                                        SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(
                MultiblockRegistry.getAll().stream().map(MultiblockDefinition::getId), builder);
    }

    /**
     * Unlike {@link #suggestDefinitionIds} (every registered id, used by {@code info}/{@code prefer
     * clear}-less contexts), {@code prefer}'s {@code definition} argument only ever makes sense as one of
     * the definitions actually ambiguous at the already-typed {@code pos} - the same "core or activation
     * symbol at this block" candidate set {@link net.astronomy.multilib.api.MultiLibAPI#setPreferredDefinition}
     * itself validates against (see {@link MultiblockAmbiguityResolver#candidatesAt}), so a suggestion
     * offered here always resolves to a binding {@code setPreferredDefinition} will actually accept.
     * {@code pos} may not be a loaded/valid position yet while the player is still mid-typing (or may be
     * relative coordinates Brigadier can't resolve outside a real command execution) - both are treated
     * as "nothing to suggest yet" rather than an error.
     */
    private static CompletableFuture<Suggestions> suggestPreferCandidates(CommandContext<CommandSourceStack> ctx,
                                                                           SuggestionsBuilder builder) {
        try {
            ServerLevel level = ctx.getSource().getLevel();
            BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
            List<MultiblockDefinition> candidates = MultiblockAmbiguityResolver
                    .candidatesAt(level, pos, (def, state) -> def.matchesActivationOrCore(state));
            return SharedSuggestionProvider.suggestResource(candidates.stream().map(MultiblockDefinition::getId), builder);
        } catch (Exception e) {
            return builder.buildFuture();
        }
    }

    // ---- list ----

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Collection<MultiblockDefinition> all = MultiblockRegistry.getAll();
        if (all.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("command.multilib.list.empty"), false);
            return 0;
        }
        for (MultiblockDefinition def : all) {
            ResourceLocation id = def.getId();
            String sourceLabel = MultiblockRegistry.getSource(id).map(Enum::name).orElse("UNKNOWN");
            src.sendSuccess(() -> Component.translatable("command.multilib.list.entry", id.toString(), sourceLabel), false);
            // Own key ("list.entry_variants"), not "info.variants" - list.entry's own leading "- "
            // pushes its text one column further right than info's plain-indented sibling lines, so the
            // two contexts need different indentation to actually line up under their own list.
            sendVariantsLineIfApplicable(src, def, "command.multilib.list.entry_variants");
        }
        int count = all.size();
        src.sendSuccess(() -> Component.translatable("command.multilib.list.count", count), false);
        return count;
    }

    // ---- info ----

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "definition");
        Optional<MultiblockDefinition> defOpt = MultiblockRegistry.get(id);
        if (defOpt.isEmpty()) {
            src.sendFailure(Component.translatable("command.multilib.info.not_found", id.toString()));
            return 0;
        }
        MultiblockDefinition def = defOpt.get();
        String sourceLabel = MultiblockRegistry.getSource(id).map(Enum::name).orElse("UNKNOWN");
        Vec3i dims = def.getBoundingBox();

        src.sendSuccess(() -> Component.translatable("command.multilib.info.header", id.toString(), sourceLabel), false);
        src.sendSuccess(() -> Component.translatable("command.multilib.info.formation_mode", def.getFormationMode().getId()), false);
        src.sendSuccess(() -> Component.translatable("command.multilib.info.rotation_mode", def.getRotationMode().toString()), false);
        src.sendSuccess(() -> Component.translatable("command.multilib.info.dimensions",
                dims.getX(), dims.getY(), dims.getZ(), def.getLayerCount()), false);
        src.sendSuccess(() -> Component.translatable("command.multilib.info.candidates", def.getCandidateBlocks().size()), false);
        src.sendSuccess(() -> Component.translatable("command.multilib.info.priority", def.getPriority()), false);
        sendVariantsLineIfApplicable(src, def, "command.multilib.info.variants");
        return 1;
    }

    /**
     * Shared by {@code list}/{@code info}: only meaningful for definitions built through
     * {@code variant(...)} - the legacy single-shape path reports the implicit "default" name, which
     * isn't worth a line. {@code translationKey} is the caller's own indentation-matched key (see the two
     * call sites' own comments) - both format identically ({@code count, names}), only the leading
     * whitespace differs to line up under that caller's own list style.
     */
    private static void sendVariantsLineIfApplicable(CommandSourceStack src, MultiblockDefinition def, String translationKey) {
        if (def.getAllVariants().size() <= 1) return;
        String variantNames = def.getAllVariants().stream()
                .map(MultiblockDefinition::getVariantName)
                .reduce((a, b) -> a + ", " + b).orElse("");
        int variantCount = def.getAllVariants().size();
        src.sendSuccess(() -> Component.translatable(translationKey, variantCount, variantNames), false);
    }

    // ---- instances ----

    private static int instancesAll(CommandContext<CommandSourceStack> ctx) {
        return listInstances(ctx.getSource(), null, 0);
    }

    private static int instancesRadius(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.translatable("command.multilib.instances.requires_player"));
            return 0;
        }
        int radius = IntegerArgumentType.getInteger(ctx, "radius");
        return listInstances(src, player.blockPosition(), radius);
    }

    private static int listInstances(CommandSourceStack src, @Nullable BlockPos center, int radius) {
        ServerLevel level = src.getLevel();
        Collection<MultiblockInstance> all = WorldMultiblockTracker.get(level).getAllInstances();

        List<MultiblockInstance> filtered = new ArrayList<>();
        for (MultiblockInstance instance : all) {
            if (center != null && distance(instance.getOrigin(), center) > radius) continue;
            filtered.add(instance);
        }

        if (filtered.isEmpty()) {
            src.sendSuccess(() -> center != null
                    ? Component.translatable("command.multilib.instances.empty_radius", radius)
                    : Component.translatable("command.multilib.instances.empty"), false);
            return 0;
        }

        for (MultiblockInstance instance : filtered) {
            BlockPos origin = instance.getOrigin();
            src.sendSuccess(() -> Component.translatable("command.multilib.instances.entry",
                    instance.getId().toString(), instance.getDefinitionId().toString(),
                    origin.getX(), origin.getY(), origin.getZ()), false);
        }
        int count = filtered.size();
        src.sendSuccess(() -> Component.translatable("command.multilib.instances.count", count), false);
        return count;
    }

    private static double distance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---- form ----

    private static int form(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        ServerPlayer player = src.getEntity() instanceof ServerPlayer sp ? sp : null;

        WorldMultiblockTracker tracker = WorldMultiblockTracker.get(level);
        if (!tracker.getInstancesAt(pos).isEmpty()) {
            src.sendFailure(Component.translatable("command.multilib.form.already_formed",
                    pos.getX(), pos.getY(), pos.getZ()));
            return 0;
        }

        // Tries every candidate definition registered for the block at `pos` and forms the first whose
        // pattern matches - see the limitation note on the `form` command node above.
        BlockActivationHandler.triggerFormationAt(level, pos, player);

        if (!tracker.getInstancesAt(pos).isEmpty()) {
            src.sendSuccess(() -> Component.translatable("command.multilib.form.success",
                    pos.getX(), pos.getY(), pos.getZ()), true);
            return 1;
        }
        src.sendFailure(Component.translatable("command.multilib.form.no_match",
                pos.getX(), pos.getY(), pos.getZ()));
        return 0;
    }

    // ---- unform ----

    private static int unform(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");

        WorldMultiblockTracker tracker = WorldMultiblockTracker.get(level);
        Set<MultiblockInstance> instances = tracker.getInstancesAt(pos);
        if (instances.isEmpty()) {
            src.sendFailure(Component.translatable("command.multilib.unform.none",
                    pos.getX(), pos.getY(), pos.getZ()));
            return 0;
        }

        // getInstancesAt already returns a fresh copy, but snapshot explicitly since handleBreak
        // mutates the tracker (unregister) as it goes - iterating a live view here would be unsafe.
        for (MultiblockInstance instance : Set.copyOf(instances)) {
            // Not a real block-break event - the command just tears down the tracked instance (and
            // runs its onBroken callbacks) without necessarily removing any blocks. UNKNOWN is the
            // closest existing BreakReason for an administrative/manual unform rather than an actual
            // PLAYER_BREAK/EXPLOSION/REPLACED.
            BlockBreakHandler.handleBreak(level, tracker, instance, pos, MultiblockBrokenContext.BreakReason.UNKNOWN);
        }

        int count = instances.size();
        src.sendSuccess(() -> Component.translatable("command.multilib.unform.success",
                count, pos.getX(), pos.getY(), pos.getZ()), true);
        return count;
    }

    // ---- prefer ----

    private static int preferSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "definition");

        boolean accepted = net.astronomy.multilib.api.MultiLibAPI.setPreferredDefinition(level, pos, id);
        if (!accepted) {
            src.sendFailure(Component.translatable("command.multilib.prefer.invalid",
                    id.toString(), pos.getX(), pos.getY(), pos.getZ()));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("command.multilib.prefer.set",
                id.toString(), pos.getX(), pos.getY(), pos.getZ()), true);
        return 1;
    }

    private static int preferClear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(ctx, "pos");

        net.astronomy.multilib.api.MultiLibAPI.clearPreferredDefinition(level, pos);
        src.sendSuccess(() -> Component.translatable("command.multilib.prefer.cleared",
                pos.getX(), pos.getY(), pos.getZ()), true);
        return 1;
    }
}
