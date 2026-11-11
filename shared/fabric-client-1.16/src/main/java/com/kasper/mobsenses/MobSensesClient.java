package com.kasper.mobsenses;

import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.client.SettingsKey;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Shared Fabric client entrypoint for Minecraft 1.16.5 - 1.18.2.
 */
public final class MobSensesClient implements ClientModInitializer {

    private static KeyMapping settingsKey;

    @Override
    public void onInitializeClient() {
        MobSenses.initClient(FabricLoader.getInstance().getConfigDir());
        ClientLink.setSettingsScreenFactory(MobSensesScreen::new);
        ModClientNetworking.register();
        settingsKey = registerSettingsKey();
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientLink.tick(client, settingsKey));
        registerClientCommands();
        HudRenderCallback.EVENT.register((poseStack, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null && !mc.options.hideGui) {
                StealthHudOverlay.render(new PoseCanvas(poseStack, mc.font));
            }
        });
    }

    private static KeyMapping registerSettingsKey() {
        try {
            KeyMapping key = SettingsKey.create();
            return key == null ? null : KeyBindingHelper.registerKeyBinding(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void registerClientCommands() {
        try {
            ClientHudCommand.register(ClientCommandManager.DISPATCHER);
        } catch (Throwable ignored) {
        }
    }
}
