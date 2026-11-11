package com.kasper.mobsenses;

import com.kasper.mobsenses.server.MobSensesCommand;
import com.kasper.mobsenses.server.ServerBridge;
import com.kasper.mobsenses.server.ServerHearing;
import com.kasper.mobsenses.server.Slf4jLog;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Shared Fabric server entrypoint (Minecraft 1.17 - 1.18.2): initializes server hearing,
 * {@code /mobsenses}, and optional {@link ModNetworking} sync.
 */
public final class MobSensesFabric implements net.fabricmc.api.ModInitializer {

    @Override
    public void onInitialize() {
        ModLog.use(new Slf4jLog());
        ModNetworking.registerCommon();
        ModNetworking.registerServer();
        FabricLoader loader = FabricLoader.getInstance();
        String minecraft = loader.getModContainer("minecraft")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerBridge.clear();
            MobSenses.start(loader.getConfigDir(),
                    new MobSenses.Platform("Fabric", minecraft),
                    loader.isModLoaded("voicechat"));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> ServerBridge.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerHearing.tickServer(server);
            MobSenses.serverTick();
            ModNetworking.tickServer(server);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                MobSensesCommand.register(dispatcher));
    }
}
