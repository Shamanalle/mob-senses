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
import net.minecraft.client.gui.LayeredDraw;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import java.lang.reflect.Method;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared client hooks for Forge 1.21 - 1.21.5 (EventBus 6).
 *
 * <p>Forge 51 (1.21) lacks both {@code AddGuiOverlayLayersEvent} and
 * {@code FMLJavaModLoadingContext#registerExtensionPoint}, whereas Forge 52+ (1.21.1 - 1.21.5) provides both.
 * Resolving them reflectively keeps a single band compatible with both Forge 51 and Forge 52-55.
 */
final class ForgeClientHooks {

    private static final String LAYERS_EVENT = "net.minecraftforge.client.event.AddGuiOverlayLayersEvent";
    private static final String MOD_LOADING_CONTEXT = "net.minecraftforge.fml.ModLoadingContext";

    private static KeyMapping settingsKey;

    private ForgeClientHooks() {
    }

    static void register(FMLJavaModLoadingContext context) {
        MobSenses.initClient(FMLPaths.CONFIGDIR.get());
        ClientLink.setSettingsScreenFactory(MobSensesScreen::new);
        ClientLink.bindNetworking(ForgeNetworking::sendHelloToServer, ForgeNetworking::sendAdminToServer);
        settingsKey = SettingsKey.create();
        if (context != null) {
            registerConfigScreen(context, () -> new ConfigScreenHandler.ConfigScreenFactory(
                    (mc, parent) -> new MobSensesScreen(parent)));
            IEventBus modBus = context.getModEventBus();
            modBus.addListener((RegisterKeyMappingsEvent e) -> {
                if (settingsKey != null) {
                    e.register(settingsKey);
                }
            });
            registerHudLayer(modBus, (graphics, delta) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.font != null && !mc.options.hideGui) {
                    StealthHudOverlay.render(new GuiCanvas(graphics, mc.font));
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
    }

    private static void registerConfigScreen(
            FMLJavaModLoadingContext context,
            Supplier<ConfigScreenHandler.ConfigScreenFactory> supplier) {
        if (invokeRegisterExtensionPoint(context, supplier)) {
            return;
        }
        try {
            Object legacy = Class.forName(MOD_LOADING_CONTEXT).getMethod("get").invoke(null);
            invokeRegisterExtensionPoint(legacy, supplier);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
    }

    private static boolean invokeRegisterExtensionPoint(
            Object target,
            Supplier<ConfigScreenHandler.ConfigScreenFactory> supplier) {
        try {
            Method m = target.getClass().getMethod("registerExtensionPoint", Class.class, Supplier.class);
            m.invoke(target, ConfigScreenHandler.ConfigScreenFactory.class, supplier);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerHudLayer(IEventBus modBus, LayeredDraw.Layer layer) {
        Class<? extends Event> eventType;
        Method getLayeredDraw;
        try {
            eventType = (Class<? extends Event>) Class.forName(LAYERS_EVENT);
            getLayeredDraw = eventType.getMethod("getLayeredDraw");
        } catch (ReflectiveOperationException | LinkageError e) {
            MinecraftForge.EVENT_BUS.addListener((CustomizeGuiOverlayEvent.Chat ev) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.font != null && !mc.options.hideGui) {
                    StealthHudOverlay.render(new GuiCanvas(ev.getGuiGraphics(), mc.font));
                }
            });
            return;
        }
        Consumer<Event> listener = event -> {
            try {
                ((LayeredDraw) getLayeredDraw.invoke(event)).add(layer);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        };
        modBus.addListener(EventPriority.NORMAL, false, (Class<Event>) eventType, listener);
    }
}
