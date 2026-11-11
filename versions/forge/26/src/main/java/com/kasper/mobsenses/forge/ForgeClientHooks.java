package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.ExtractorCanvas;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.MobSensesScreen;
import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.client.SettingsKey;
import com.kasper.mobsenses.client.StealthHudOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Client hooks for Forge 26.x (EventBus 7, unobfuscated).
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
        if (context != null) {
            context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new MobSensesScreen(parent)));
        }
        RegisterKeyMappingsEvent.BUS.addListener(e -> {
            if (settingsKey != null) {
                e.register(settingsKey);
            }
        });
        AddGuiOverlayLayersEvent.BUS.addListener(e ->
                e.getLayeredDraw().add(
                        Identifier.fromNamespaceAndPath(MobSenses.MOD_ID, "stealth_hud"),
                        (graphics, delta) -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc != null && mc.font != null) {
                                StealthHudOverlay.render(new ExtractorCanvas(graphics, mc.font));
                            }
                        }));
        TickEvent.ClientTickEvent.Post.BUS.addListener(e ->
                ClientLink.tick(Minecraft.getInstance(), settingsKey));
        RegisterClientCommandsEvent.BUS.addListener(e ->
                ClientHudCommand.register(e.getDispatcher()));
    }
}
