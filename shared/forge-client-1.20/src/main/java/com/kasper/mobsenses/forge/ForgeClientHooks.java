package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.GuiCanvas;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.MobSensesScreen;
import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.client.SettingsKey;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Shared client hooks for Forge 1.20 - 1.20.4.
 */
final class ForgeClientHooks {

    private static KeyMapping settingsKey;

    private ForgeClientHooks() {
    }

    @SuppressWarnings("removal")
    static void register(FMLJavaModLoadingContext context) {
        MobSenses.initClient(FMLPaths.CONFIGDIR.get());
        ClientLink.setSettingsScreenFactory(MobSensesScreen::new);
        ClientLink.bindNetworking(ForgeNetworking::sendHelloToServer, ForgeNetworking::sendAdminToServer);
        settingsKey = SettingsKey.create();
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new MobSensesScreen(parent)));
        FMLJavaModLoadingContext ctx = context != null ? context : FMLJavaModLoadingContext.get();
        if (ctx != null) {
            ctx.getModEventBus().addListener((RegisterKeyMappingsEvent e) -> {
                if (settingsKey != null) {
                    e.register(settingsKey);
                }
            });
        }
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                ClientLink.tick(Minecraft.getInstance(), settingsKey);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) ->
                ClientHudCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((RenderGuiEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null && !mc.options.hideGui) {
                StealthHudOverlay.render(new GuiCanvas(e.getGuiGraphics(), mc.font));
            }
        });
    }
}
