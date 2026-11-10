package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.GuiCanvas;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Client-only HUD and {@code /mshud} listener for Forge 1.20.5 - 1.21.5.
 */
final class ForgeClientHooks {

    private ForgeClientHooks() {
    }

    static void register(FMLJavaModLoadingContext context) {
        MobSenses.initClient(FMLPaths.CONFIGDIR.get());
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) ->
                ClientHudCommand.register(e.getDispatcher()));
        if (context != null) {
            context.getModEventBus().addListener((AddGuiOverlayLayersEvent e) ->
                    e.getLayeredDraw().add(
                            ForgeLayeredDraw.PRE_SLEEP_STACK,
                            ResourceLocation.tryBuild(MobSenses.MOD_ID, "stealth_hud"),
                            (graphics, delta) -> {
                                Minecraft mc = Minecraft.getInstance();
                                if (mc != null && mc.font != null) {
                                    StealthHudOverlay.render(new GuiCanvas(graphics, mc.font));
                                }
                            }));
        }
    }
}
