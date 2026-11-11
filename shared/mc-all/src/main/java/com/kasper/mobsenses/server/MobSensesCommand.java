package com.kasper.mobsenses.server;

import com.kasper.mobsenses.AdminCommands;
import com.kasper.mobsenses.MobSenses;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /mobsenses} on a Fabric, Forge or NeoForge server. The text after the command goes to
 * {@link AdminCommands} as it is, so every version registers the same small tree; the replies come back
 * as coloured chat lines in the sender's language.
 * <p>
 * On integrated singleplayer worlds, the host player may also run {@code /mobsenses hud ...} even when
 * cheats are turned off.
 */
public final class MobSensesCommand {

    private MobSensesCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(AdminCommands.NAME)
                .requires(s -> AdminPermission.isAdmin(s) || isLocalSingleplayerHost(s))
                .executes(ctx -> {
                    if (!AdminPermission.isAdmin(ctx.getSource())) {
                        return run(ctx, "hud");
                    }
                    return run(ctx, "");
                })
                .then(Commands.literal("hud")
                        .executes(ctx -> run(ctx, "hud"))
                        .then(Commands.argument("hudArgs", StringArgumentType.greedyString())
                                .suggests((ctx, b) -> suggestPrefixed(ctx, b, "hud "))
                                .executes(ctx -> run(ctx, "hud " + StringArgumentType.getString(ctx, "hudArgs")))))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .requires(AdminPermission::isAdmin)
                        .suggests((ctx, b) -> suggestPrefixed(ctx, b, ""))
                        .executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "args")))));
    }

    private static boolean isLocalSingleplayerHost(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null || server.isDedicatedServer()) {
            return false;
        }
        if (!(source.getEntity() instanceof ServerPlayer)) {
            return false;
        }
        ServerPlayer player = (ServerPlayer) source.getEntity();
        UUID localId = MobSenses.localPlayerId();
        return localId == null || localId.equals(player.getUUID());
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String args) {
        CommandSourceStack source = ctx.getSource();
        Reply reply = AdminCommands.execute(args, context(source));
        boolean isPlayer = source.getEntity() instanceof ServerPlayer;
        for (Reply.Line line : reply.lines()) {
            Component text = isPlayer
                    ? ReplyComponents.render(line)
                    : Worlds.literal(line.text()).withStyle(color(line.kind()));
            Worlds.sendSuccess(source, text);
        }
        if (!reply.refused()) {
            ServerBridge.markDirty();
        }
        return reply.refused() ? 0 : 1;
    }

    private static CompletableFuture<Suggestions> suggestPrefixed(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder, String prefix) {
        String typed = builder.getRemaining();
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + typed.lastIndexOf(' ') + 1);
        for (String s : AdminCommands.suggestions(prefix + typed, context(ctx.getSource()))) {
            word.suggest(s);
        }
        return word.buildFuture();
    }

    private static AdminCommands.Context context(CommandSourceStack source) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer
                ? (ServerPlayer) source.getEntity()
                : null;
        String language = player == null ? "" : PlayerLanguage.of(player);
        String world = player == null ? null : Worlds.id(source.getLevel());
        MinecraftServer server = source.getServer();
        List<String> worlds = Worlds.ids(server);
        boolean dedicated = server != null && server.isDedicatedServer();
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

            @Override
            public boolean isDedicatedServer() {
                return dedicated;
            }
        };
    }

    public static ChatFormatting color(Reply.Kind kind) {
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
