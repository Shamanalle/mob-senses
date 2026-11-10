package com.kasper.mobsenses;

import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Fabric client entrypoint for Minecraft 1.16.5 - 1.18.2: initializes client HUD config, registers
 * {@code /mshud}, and renders the Stealth & Noise HUD.
 */
public final class MobSensesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MobSenses.initClient(FabricLoader.getInstance().getConfigDir());
        registerClientCommands();
        HudRenderCallback.EVENT.register((poseStack, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) {
                StealthHudOverlay.render(new PoseCanvas(poseStack, mc.font));
            }
        });
    }

    private static void registerClientCommands() {
        try {
            ClientHudCommand.register(ClientCommandManager.DISPATCHER);
        } catch (Throwable ignored) {
        }
    }
}
