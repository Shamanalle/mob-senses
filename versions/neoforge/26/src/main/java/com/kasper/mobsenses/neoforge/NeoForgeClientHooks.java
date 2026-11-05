package com.kasper.mobsenses.neoforge;

import com.kasper.mobsenses.client.Canvas;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only HUD listener for NeoForge 26.x.
 */
final class NeoForgeClientHooks {

    private NeoForgeClientHooks() {
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) {
                GuiGraphicsExtractor g = e.getGuiGraphics();
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
                        g.text(font, text, x, y, argb, true);
                    }
                });
            }
        });
    }
}
