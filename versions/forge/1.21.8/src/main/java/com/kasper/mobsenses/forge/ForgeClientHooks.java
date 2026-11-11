package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.MobSensesScreen;
import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.client.SettingsKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Client hooks for Forge 1.21.6 - 1.21.8 (EventBus 7).
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
            BusGroup modBus = context.getModBusGroup();
            context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new MobSensesScreen(parent)));
            RegisterKeyMappingsEvent.getBus(modBus).addListener(e -> {
                if (settingsKey != null) {
                    e.register(settingsKey);
                }
            });
            try {
                ForgeHudLayer.register(modBus);
            } catch (LinkageError ignored) {
                // Forge 1.21.6 - 1.21.7 had no AddGuiOverlayLayersEvent yet; everything else still loads cleanly.
            }
        }
        TickEvent.ClientTickEvent.Post.BUS.addListener(e ->
                ClientLink.tick(Minecraft.getInstance(), settingsKey));
        RegisterClientCommandsEvent.BUS.addListener(e ->
                ClientHudCommand.register(e.getDispatcher()));
    }
}
