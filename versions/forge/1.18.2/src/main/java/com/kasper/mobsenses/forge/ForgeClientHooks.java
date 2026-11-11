package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.MobSensesScreen;
import com.kasper.mobsenses.PoseCanvas;
import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.client.SettingsKey;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.client.ConfigGuiHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Client hooks for Forge 1.18 - 1.18.2.
 */
final class ForgeClientHooks {

    private static KeyMapping settingsKey;

    private ForgeClientHooks() {
    }

    static void register(FMLJavaModLoadingContext context) {
        MobSenses.initClient(FMLPaths.CONFIGDIR.get());
        ClientLink.setSettingsScreenFactory(MobSensesScreen::new);
        ClientLink.bindNetworking(ForgeNetworking::sendHelloToServer, ForgeNetworking::sendAdminToServer);
        settingsKey = SettingsKey.create();
        ModLoadingContext.get().registerExtensionPoint(ConfigGuiHandler.ConfigGuiFactory.class,
                () -> new ConfigGuiHandler.ConfigGuiFactory((mc, parent) -> new MobSensesScreen(parent)));
        FMLJavaModLoadingContext ctx = context != null ? context : FMLJavaModLoadingContext.get();
        if (ctx != null) {
            ctx.getModEventBus().addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
                if (settingsKey != null) {
                    ClientRegistry.registerKeyBinding(settingsKey);
                }
            }));
        }
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                ClientLink.tick(Minecraft.getInstance(), settingsKey);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) ->
                ClientHudCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((RenderGameOverlayEvent.Post e) -> {
            if (e.getType() != RenderGameOverlayEvent.ElementType.ALL) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.font != null && !mc.options.hideGui) {
                StealthHudOverlay.render(new PoseCanvas(e.getMatrixStack(), mc.font));
            }
        });
    }
}
