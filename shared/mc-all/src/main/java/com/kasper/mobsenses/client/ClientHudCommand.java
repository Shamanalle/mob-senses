package com.kasper.mobsenses.client;

import com.kasper.mobsenses.AdminCommands;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.Reply;
import com.kasper.mobsenses.server.ReplyComponents;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Registers the pure client-side {@code /mshud} ({@code /mobsenseshud}) command on any Brigadier
 * client command dispatcher (Fabric v1/v2, Forge, and NeoForge).
 * <p>
 * Running {@code /mshud} with no arguments (or {@code /mshud gui} / {@code /mshud settings}) opens
 * the Mob Senses settings screen; subcommands adjust HUD settings directly.
 */
public final class ClientHudCommand {

    private static final Method GUI_GET_CHAT = resolveGetChat(Gui.class);
    private static final Method MC_GET_CHAT = resolveGetChat(Minecraft.class);
    private static final Method CHAT_ADD_MESSAGE = resolveChatAddMessage();
    private static final Method PLAYER_SEND_MESSAGE = resolvePlayerSendMessage();

    private ClientHudCommand() {
    }

    private static Method resolveGetChat(Class<?> owner) {
        for (Method m : owner.getMethods()) {
            if (m.getParameterTypes().length == 0 && m.getReturnType() == ChatComponent.class) {
                return m;
            }
        }
        return null;
    }

    private static Method resolveChatAddMessage() {
        Method fallback = null;
        for (Method m : ChatComponent.class.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length != 1 || p[0] != Component.class || m.getReturnType() != void.class) {
                continue;
            }
            String n = m.getName();
            if ("addClientSystemMessage".equals(n)
                    || "addMessage".equals(n)
                    || "method_1812".equals(n)
                    || "m_93785_".equals(n)) {
                return m;
            }
            if (!n.contains("Server")) {
                fallback = m;
            }
        }
        return fallback;
    }

    private static Method resolvePlayerSendMessage() {
        for (Method m : LocalPlayer.class.getMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 1 && p[0] == Component.class && m.getReturnType() == void.class) {
                String n = m.getName();
                if ("sendSystemMessage".equals(n) || "displayClientMessage".equals(n)) {
                    return m;
                }
            }
        }
        return null;
    }

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        if (dispatcher == null) {
            return;
        }
        registerLiteral(dispatcher, AdminCommands.HUD_COMMAND_NAME);
        registerLiteral(dispatcher, AdminCommands.HUD_COMMAND_ALIAS);
    }

    private static <S> void registerLiteral(CommandDispatcher<S> dispatcher, String name) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal(name)
                .executes(ctx -> run(""))
                .then(RequiredArgumentBuilder.<S, String>argument("args", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> suggest(builder))
                        .executes(ctx -> run(StringArgumentType.getString(ctx, "args")))));
    }

    private static int run(String args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && MobSenses.clientSettings().file() == null && mc.gameDirectory != null) {
            MobSenses.initClient(mc.gameDirectory.toPath().resolve("config"));
        }
        String trimmed = args == null ? "" : args.trim();
        if (trimmed.isEmpty()
                || "gui".equalsIgnoreCase(trimmed)
                || "settings".equalsIgnoreCase(trimmed)) {
            ClientLink.requestOpenSettings();
            return 1;
        }
        String lang = (mc != null && mc.options != null) ? mc.options.languageCode : "";
        Reply reply = AdminCommands.executeHud(args, lang);
        if (mc != null) {
            Object chat = null;
            if (CHAT_ADD_MESSAGE != null) {
                try {
                    if (mc.gui != null && GUI_GET_CHAT != null) {
                        chat = GUI_GET_CHAT.invoke(mc.gui);
                    } else if (MC_GET_CHAT != null) {
                        chat = MC_GET_CHAT.invoke(mc);
                    }
                } catch (Throwable ignored) {
                }
            }
            for (Reply.Line line : reply.lines()) {
                Component text = ReplyComponents.render(line);
                try {
                    if (chat != null && CHAT_ADD_MESSAGE != null) {
                        CHAT_ADD_MESSAGE.invoke(chat, text);
                    } else if (mc.player != null && PLAYER_SEND_MESSAGE != null) {
                        PLAYER_SEND_MESSAGE.invoke(mc.player, text);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return reply.refused() ? 0 : 1;
    }

    private static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder) {
        String typed = builder.getRemaining();
        SuggestionsBuilder word = builder.createOffset(builder.getStart() + typed.lastIndexOf(' ') + 1);
        if (!typed.contains(" ")) {
            String low = typed.toLowerCase(Locale.ROOT);
            if ("gui".startsWith(low)) {
                word.suggest("gui");
            }
            if ("settings".startsWith(low)) {
                word.suggest("settings");
            }
        }
        for (String s : AdminCommands.hudSuggestions(typed)) {
            word.suggest(s);
        }
        return word.buildFuture();
    }
}
