package com.kasper.mobsenses.neoforge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.server.MobSensesCommand;
import com.kasper.mobsenses.server.ServerBridge;
import com.kasper.mobsenses.server.ServerHearing;
import com.kasper.mobsenses.server.Slf4jLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Shared NeoForge entrypoint for Minecraft 1.20.5 - 26.x.
 */
@Mod(MobSenses.MOD_ID)
public final class MobSensesNeoForge {

    public MobSensesNeoForge(IEventBus modBus, ModContainer container, Dist dist) {
        init(modBus, container, dist == Dist.CLIENT);
    }

    private static void init(IEventBus modBus, ModContainer container, boolean client) {
        ModLog.use(new Slf4jLog());
        modBus.addListener(NeoNetworking::register);
        String mcVersion = ModList.get().getModContainerById("minecraft")
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
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
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            ServerHearing.tickServer(e.getServer());
            MobSenses.serverTick();
            NeoNetworking.tickServer(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener(MobSensesNeoForge::gameEvent);
        if (client) {
            NeoForgeClientHooks.register(modBus, container);
        }
    }

    private static void gameEvent(VanillaGameEvent e) {
        try {
            if (!(e.getLevel() instanceof ServerLevel level)) {
                return;
            }
            String id = String.valueOf(BuiltInRegistries.GAME_EVENT.getKey(e.getVanillaEvent().value()));
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
