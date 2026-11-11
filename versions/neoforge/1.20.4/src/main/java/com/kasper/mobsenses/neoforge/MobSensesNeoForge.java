package com.kasper.mobsenses.neoforge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.server.MobSensesCommand;
import com.kasper.mobsenses.server.ServerBridge;
import com.kasper.mobsenses.server.ServerHearing;
import com.kasper.mobsenses.server.Slf4jLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * NeoForge entrypoint for Minecraft 1.20.4.
 */
@Mod(MobSenses.MOD_ID)
public final class MobSensesNeoForge {

    public MobSensesNeoForge(IEventBus modBus, ModContainer container, Dist dist) {
        init(modBus, dist == Dist.CLIENT);
    }

    public MobSensesNeoForge(IEventBus modBus) {
        init(modBus, FMLEnvironment.dist == Dist.CLIENT);
    }

    private static void init(IEventBus modBus, boolean client) {
        ModLog.use(new Slf4jLog());
        modBus.addListener(NeoNetworking::register);
        String mcVersion = ModList.get().getModContainerById("minecraft")
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("1.20.4");
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> {
            ServerBridge.clear();
            MobSenses.start(
                    FMLPaths.CONFIGDIR.get(),
                    new MobSenses.Platform("NeoForge", mcVersion),
                    ModList.get().isLoaded("voicechat"));
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> ServerBridge.clear());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() != null) {
                ServerBridge.onDisconnect(e.getEntity().getUUID());
            }
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> MobSensesCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    ServerHearing.tickServer(server);
                    MobSenses.serverTick();
                    NeoNetworking.tickServer(server);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(MobSensesNeoForge::gameEvent);
        if (client) {
            NeoForgeClientHooks.register(modBus);
        }
    }

    private static void gameEvent(VanillaGameEvent e) {
        try {
            if (!(e.getLevel() instanceof ServerLevel level)) {
                return;
            }
            String id = String.valueOf(BuiltInRegistries.GAME_EVENT.getKey(e.getVanillaEvent()));
            Vec3 pos = e.getEventPosition();
            GameEvent.Context ctx = e.getContext();
            ServerHearing.onGameEvent(
                    level,
                    id,
                    pos.x, pos.y, pos.z,
                    ctx == null ? e.getCause() : ctx.sourceEntity(),
                    ctx == null ? null : ctx.affectedState());
        } catch (Throwable t) {
            ModLog.failure("Reading a game event", t);
        }
    }
}
