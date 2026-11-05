package com.kasper.mobsenses;

import com.kasper.mobsenses.client.StealthHudOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;

/**
 * Fabric client entrypoint for Minecraft 1.21 - 1.21.11: renders the Stealth & Noise HUD.
 */
public final class MobSensesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) {
                StealthHudOverlay.render(new GuiCanvas(graphics, mc.font));
            }
        });
    }
}
