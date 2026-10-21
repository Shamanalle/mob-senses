package com.kasper.mobsenses.gametest;

import net.minecraft.client.Minecraft;

/** What the in-game test does through the client on 26.x. */
final class TestScreens {

    private static volatile java.util.concurrent.CompletableFuture<?> reload;

    private TestScreens() {
    }

    /** Switches the game's language; the resources reload after it. */
    static void language(Minecraft client, String code) {
        client.options.languageCode = code;
        client.getLanguageManager().setSelected(code);
        reload = client.reloadResourcePacks();
    }

    /** A world where the player may use commands (cheats on), so /mobsenses works. */
    static void allowCommands(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState settings) {
        settings.setAllowCommands(true);
    }

    /** Sends a command as if typed in chat, without the slash. */
    static void command(Minecraft client, String command) {
        client.player.connection.sendCommand(command);
    }

    /** Sends the client's options (its language) to the server. */
    static void sendOptions(Minecraft client) {
        client.options.broadcastOptions();
    }

    /**
     * Empties the chat, so each screenshot shows one group of replies. The chat moved out of Gui in 26.x, so
     * it is looked up by its clearMessages(boolean); without it the older lines stay.
     */
    static void clearChat(Minecraft client) {
        for (Object owner : new Object[]{client.gui, client}) {
            for (java.lang.reflect.Method getter : owner.getClass().getMethods()) {
                if (getter.getParameterCount() != 0 || !getter.getReturnType().getSimpleName().contains("Chat")) {
                    continue;
                }
                try {
                    Object chat = getter.invoke(owner);
                    chat.getClass().getMethod("clearMessages", boolean.class).invoke(chat, false);
                    return;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Not the chat; try the next
                }
            }
        }
    }

    /** Whether the game has finished loading resources. */
    static boolean loaded(Minecraft client) {
        return reload == null || reload.isDone();
    }
}
