package com.kasper.mobsenses;

import com.kasper.mobsenses.server.MobSensesCommand;
import com.kasper.mobsenses.server.Slf4jLog;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric entrypoint (Minecraft 1.21 - 1.21.11): the server side, also inside the singleplayer client.
 * Game events reach the mod through {@link com.kasper.mobsenses.mixin.ServerLevelMixin}.
 */
public final class MobSensesFabric implements net.fabricmc.api.ModInitializer {

    @Override
    public void onInitialize() {
        ModLog.use(new Slf4jLog());
        FabricLoader loader = FabricLoader.getInstance();
        String minecraft = loader.getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
        ServerLifecycleEvents.SERVER_STARTING.register(server -> MobSenses.start(loader.getConfigDir(),
                new MobSenses.Platform("Fabric", minecraft), loader.isModLoaded("voicechat")));
        ServerTickEvents.END_SERVER_TICK.register(server -> MobSenses.serverTick());
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> MobSensesCommand.register(dispatcher));
    }
}
