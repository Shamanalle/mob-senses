package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.PoseCanvas;
import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Client-only HUD and {@code /mshud} listener for Forge 1.18 - 1.18.2.
 */
final class ForgeClientHooks {

    private ForgeClientHooks() {
    }

    static void register() {
        MobSenses.initClient(FMLPaths.CONFIGDIR.get());
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) ->
                ClientHudCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((RenderGameOverlayEvent.Post e) -> {
            if (e.getType() != RenderGameOverlayEvent.ElementType.ALL) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) {
                StealthHudOverlay.render(new PoseCanvas(e.getMatrixStack(), mc.font));
            }
        });
    }
}
