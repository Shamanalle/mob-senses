package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.client.Canvas;
import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Client-only HUD and {@code /mshud} listener for Forge 1.20 - 1.20.4 (and NeoForge 1.20.1).
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
                GuiGraphics g = e.getGuiGraphics();
                Font font = mc.font;
                StealthHudOverlay.render(new Canvas() {
                    @Override
                    public int width() {
                        return g.guiWidth();
                    }

                    @Override
                    public int height() {
                        return g.guiHeight();
                    }

                    @Override
                    public int textWidth(Component text) {
                        return font.width(text);
                    }

                    @Override
                    public void fill(int x1, int y1, int x2, int y2, int argb) {
                        g.fill(x1, y1, x2, y2, argb);
                    }

                    @Override
                    public void text(Component text, int x, int y, int argb) {
                        int w = font.width(text);
                        g.drawCenteredString(font, text, x + w / 2, y, argb);
                    }
                });
            }
        });
    }
}
