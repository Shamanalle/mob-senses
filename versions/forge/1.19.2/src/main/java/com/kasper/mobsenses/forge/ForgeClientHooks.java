package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.PoseCanvas;
import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Client-only HUD and {@code /mshud} listener for Forge 1.19 - 1.19.4.
 */
final class ForgeClientHooks {

    private ForgeClientHooks() {
    }

    static void register() {
        MobSenses.initClient(FMLPaths.CONFIGDIR.get());
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) ->
                ClientHudCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) {
                StealthHudOverlay.render(new PoseCanvas(e.getPoseStack(), mc.font));
            }
        });
    }
}
