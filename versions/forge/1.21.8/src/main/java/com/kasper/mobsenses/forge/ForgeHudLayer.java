package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.GuiCanvas;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;

/**
 * Isolated HUD overlay layer registration for Forge 1.21.8 so 1.21.6 - 1.21.7 do not fail classloading.
 */
final class ForgeHudLayer {

    private ForgeHudLayer() {
    }

    static void register(BusGroup modBus) {
        AddGuiOverlayLayersEvent.getBus(modBus).addListener(e ->
                e.getLayeredDraw().add(
                        ResourceLocation.fromNamespaceAndPath(MobSenses.MOD_ID, "stealth_hud"),
                        (graphics, delta) -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc != null && mc.font != null && !mc.options.hideGui) {
                                StealthHudOverlay.render(new GuiCanvas(graphics, mc.font));
                            }
                        }));
    }
}
