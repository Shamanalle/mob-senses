package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.ExtractorCanvas;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Client-only HUD and {@code /mshud} listener for Forge 26.x.
 */
final class ForgeClientHooks {

    private ForgeClientHooks() {
    }

    static void register() {
        MobSenses.initClient(FMLPaths.CONFIGDIR.get());
        RegisterClientCommandsEvent.BUS.addListener(e -> ClientHudCommand.register(e.getDispatcher()));
        AddGuiOverlayLayersEvent.BUS.addListener(e ->
                e.getLayeredDraw().add(
                        ForgeLayeredDraw.PRE_SLEEP_STACK,
                        Identifier.fromNamespaceAndPath(MobSenses.MOD_ID, "stealth_hud"),
                        (graphics, delta) -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc != null && mc.font != null) {
                                StealthHudOverlay.render(new ExtractorCanvas(graphics, mc.font));
                            }
                        }));
    }
}
