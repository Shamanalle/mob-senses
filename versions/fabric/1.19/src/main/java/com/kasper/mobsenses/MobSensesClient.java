package com.kasper.mobsenses;

import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/**
 * Fabric client entrypoint for Minecraft 1.19 - 1.19.4: initializes client HUD config, registers
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
        HudRenderCallback.EVENT.register((poseStack, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) {
                StealthHudOverlay.render(new PoseCanvas(poseStack, mc.font));
            }
        });
    }
}
