package com.kasper.mobsenses;

import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.client.SettingsKey;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Fabric client entrypoint for Minecraft 26.x.
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
        try {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                    ClientHudCommand.register(dispatcher));
        } catch (Throwable ignored) {
        }
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MobSenses.MOD_ID, "stealth_hud"),
                (graphics, delta) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.font != null) {
                        StealthHudOverlay.render(new ExtractorCanvas(graphics, mc.font));
                    }
                });
    }

    private static KeyMapping registerSettingsKey() {
        try {
            KeyMapping key = SettingsKey.create();
            return key == null ? null : KeyMappingHelper.registerKeyMapping(key);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
