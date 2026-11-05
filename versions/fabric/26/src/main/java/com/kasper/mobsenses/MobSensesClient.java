package com.kasper.mobsenses;

import com.kasper.mobsenses.client.StealthHudOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Fabric client entrypoint for Minecraft 26.x: renders the Stealth & Noise HUD.
 */
public final class MobSensesClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
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
