package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.MobSensesScreen;
import com.kasper.mobsenses.PoseCanvas;
import com.kasper.mobsenses.client.ClientHudCommand;
import com.kasper.mobsenses.client.ClientLink;
import com.kasper.mobsenses.client.SettingsKey;
import com.kasper.mobsenses.client.StealthHudOverlay;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Client hooks for Forge 1.16.5.
 */
final class ForgeClientHooks {

    private static final CommandDispatcher<Object> DISPATCHER = new CommandDispatcher<>();
    private static KeyMapping settingsKey;

    static {
        ClientHudCommand.register(DISPATCHER);
    }

    private ForgeClientHooks() {
    }

    static void register(FMLJavaModLoadingContext context) {
        MobSenses.initClient(FMLPaths.CONFIGDIR.get());
        ClientLink.setSettingsScreenFactory(MobSensesScreen::new);
        ClientLink.bindNetworking(ForgeNetworking::sendHelloToServer, ForgeNetworking::sendAdminToServer);
        settingsKey = SettingsKey.create();
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.CONFIGGUIFACTORY,
                () -> (mc, parent) -> new MobSensesScreen(parent));
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
        MinecraftForge.EVENT_BUS.addListener((ClientChatEvent e) -> {
            String msg = e.getMessage();
            if (msg == null) {
                return;
            }
            if (matchesCommand(msg, "/mshud") || matchesCommand(msg, "/mobsenseshud")) {
                e.setCanceled(true);
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.gui != null) {
                    mc.gui.getChat().addRecentChat(msg);
                }
                try {
                    DISPATCHER.execute(msg.substring(1).trim(), new Object());
                } catch (CommandSyntaxException ex) {
                    ClientHudCommand.register(new CommandDispatcher<>());
                }
            }
        });
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

    private static boolean matchesCommand(String msg, String prefix) {
        return msg.equalsIgnoreCase(prefix)
                || (msg.length() > prefix.length()
                && msg.regionMatches(true, 0, prefix, 0, prefix.length())
                && msg.charAt(prefix.length()) == ' ');
    }
}
