package com.kasper.mobsenses.neoforge;

import com.kasper.mobsenses.GuiCanvas;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.MobSensesScreen;
import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.client.SettingsKey;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Shared client hooks for NeoForge 1.20.5 - 1.21.11.
 */
final class NeoForgeClientHooks {

    private static KeyMapping settingsKey;

    private NeoForgeClientHooks() {
    }

    static void register(IEventBus modBus, ModContainer container) {
        MobSenses.initClient(FMLPaths.CONFIGDIR.get());
        ClientLink.setSettingsScreenFactory(MobSensesScreen::new);
        ClientLink.bindNetworking(NeoNetworking::sendHelloToServer, NeoNetworking::sendAdminToServer);
        settingsKey = SettingsKey.create();
        if (container != null) {
            container.registerExtensionPoint(IConfigScreenFactory.class,
                    (IConfigScreenFactory) (mod, parent) -> new MobSensesScreen(parent));
        }
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            if (settingsKey != null) {
                e.register(settingsKey);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) ->
                ClientLink.tick(Minecraft.getInstance(), settingsKey));
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) ->
                ClientHudCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null && !mc.options.hideGui) {
                StealthHudOverlay.render(new GuiCanvas(e.getGuiGraphics(), mc.font));
            }
        });
    }
}
