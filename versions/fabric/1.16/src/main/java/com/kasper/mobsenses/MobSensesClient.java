package com.kasper.mobsenses;

import com.kasper.mobsenses.client.StealthHudOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

/**
 * Fabric client entrypoint for Minecraft 1.16.5: renders the Stealth & Noise HUD.
 */
public final class MobSensesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((poseStack, tickDelta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) {
                StealthHudOverlay.render(new PoseCanvas(poseStack, mc.font));
            }
        });
    }
}
