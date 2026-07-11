package net.astronomy.multilib.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.astronomy.multilib.MultiLib;
import net.astronomy.multilib.api.assembly.AssemblyDefinition;
import net.astronomy.multilib.api.assembly.AssemblyInstance;
import net.astronomy.multilib.core.assembly.AssemblyRegistry;
import net.astronomy.multilib.core.assembly.WorldAssemblyTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;

/**
 * Admin commands under {@code /multilib assembly …}. Registered on its own {@link RegisterCommandsEvent}
 * subscriber; Brigadier merges this {@code multilib} literal with {@link MultiblockCommands}', so no
 * edit to that class is needed. Permission level 2, feedback fully translatable.
 */
@EventBusSubscriber(modid = MultiLib.MODID)
public final class AssemblyCommands {

    private AssemblyCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("multilib")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("assembly")
                        .then(Commands.literal("list").executes(AssemblyCommands::list))
                        .then(Commands.literal("info")
                                .then(Commands.argument("definition", ResourceLocationArgument.id())
                                        .executes(AssemblyCommands::info)))
                        .then(Commands.literal("members")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(AssemblyCommands::members)))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        var all = WorldAssemblyTracker.get(level).getAll();
        if (all.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("command.multilib.assembly.list.empty"), false);
            return 0;
        }
        for (AssemblyInstance a : all) {
            src.sendSuccess(() -> Component.translatable("command.multilib.assembly.list.entry",
                    a.getId().toString(), a.getDefinitionId().toString(),
                    a.getState().getId(), a.allMemberIds().size()), false);
        }
        int count = all.size();
        src.sendSuccess(() -> Component.translatable("command.multilib.assembly.list.count", count), false);
        return count;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "definition");
        AssemblyDefinition def = AssemblyRegistry.get(id).orElse(null);
        if (def == null) {
            src.sendFailure(Component.translatable("command.multilib.assembly.info.unknown", id.toString()));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("command.multilib.assembly.info.header", id.toString()), false);
        def.getRoles().values().forEach(role -> src.sendSuccess(() ->
                Component.translatable("command.multilib.assembly.info.role",
                        role.name(), role.definition().toString(), role.min(), role.max()), false));
        src.sendSuccess(() -> Component.translatable("command.multilib.assembly.info.policy",
                def.getBreakPolicy().name(), def.getFormationPolicy().name()), false);
        return 1;
    }

    private static int members(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos pos = BlockPosArgument.getBlockPos(ctx, "pos");
        Optional<AssemblyInstance> assemblyOpt = WorldAssemblyTracker.get(level).getAssemblyAt(level, pos);
        if (assemblyOpt.isEmpty()) {
            src.sendFailure(Component.translatable("command.multilib.assembly.members.none",
                    pos.getX(), pos.getY(), pos.getZ()));
            return 0;
        }
        AssemblyInstance assembly = assemblyOpt.get();
        src.sendSuccess(() -> Component.translatable("command.multilib.assembly.members.header",
                assembly.getId().toString()), false);
        assembly.getAllMembers().forEach((role, ids) -> src.sendSuccess(() ->
                Component.translatable("command.multilib.assembly.members.role", role, ids.size()), false));
        return 1;
    }
}
