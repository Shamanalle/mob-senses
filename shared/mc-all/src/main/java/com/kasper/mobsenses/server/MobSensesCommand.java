package com.kasper.mobsenses.server;

import com.kasper.mobsenses.AdminCommands;
import com.kasper.mobsenses.Reply;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /mobsenses} on a Fabric, Forge or NeoForge server. The text after the command goes to
 * {@link AdminCommands} as it is, so every version registers the same small tree; the replies come back
 * as coloured chat lines in the sender's language.
 */
public final class MobSensesCommand {

    private MobSensesCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(AdminCommands.NAME)
                .requires(AdminPermission::isAdmin)
                .executes(ctx -> run(ctx, ""))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .suggests(MobSensesCommand::suggest)
                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "args")))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String args) {
        CommandSourceStack source = ctx.getSource();
        Reply reply = AdminCommands.execute(args, context(source));
        for (Reply.Line line : reply.lines()) {
            Component text = Component.literal(line.text()).withStyle(color(line.kind()));
            source.sendSuccess(() -> text, false);
        }
        return reply.refused() ? 0 : 1;
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String typed = builder.getRemaining();
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + typed.lastIndexOf(' ') + 1);
        for (String s : AdminCommands.suggestions(typed, context(ctx.getSource()))) {
            word.suggest(s);
        }
        return word.buildFuture();
    }

    private static AdminCommands.Context context(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        String language = player == null ? "" : PlayerLanguage.of(player);
        String world = player == null ? null : Worlds.id(source.getLevel());
        List<String> worlds = Worlds.ids(source.getServer());
        return new AdminCommands.Context() {
            @Override
            public String language() {
                return language;
            }

            @Override
            public String world() {
                return world;
            }

            @Override
            public List<String> worlds() {
                return worlds;
            }
        };
    }

    private static ChatFormatting color(Reply.Kind kind) {
        switch (kind) {
            case TITLE:
                return ChatFormatting.GOLD;
            case DONE:
                return ChatFormatting.GREEN;
            case ERROR:
                return ChatFormatting.RED;
            case HINT:
                return ChatFormatting.GRAY;
            default:
                return ChatFormatting.WHITE;
        }
    }
}
