package com.kasper.mobsenses;

import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Fabric client entrypoint for Minecraft 26.x: initializes client HUD config, registers
 * {@code /mshud}, and renders the Stealth & Noise HUD.
 */
public final class MobSensesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MobSenses.initClient(FabricLoader.getInstance().getConfigDir());
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
}
