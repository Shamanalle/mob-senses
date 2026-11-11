package com.kasper.mobsenses.forge;

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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Shared Forge server entrypoint for Minecraft 1.19.4 - 1.20.4.
 */
@Mod(MobSenses.MOD_ID)
public final class MobSensesForge {

    public MobSensesForge(FMLJavaModLoadingContext context) {
        ModLog.use(new Slf4jLog());
        ForgeNetworking.register();
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent e) -> {
            ServerBridge.clear();
            MobSenses.start(FMLPaths.CONFIGDIR.get(),
                    new MobSenses.Platform("Forge", FMLLoader.versionInfo().mcVersion()),
                    ModList.get().isLoaded("voicechat"));
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> ServerBridge.clear());
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() != null) {
                ServerBridge.onDisconnect(e.getEntity().getUUID());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> MobSensesCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    ServerHearing.tickServer(server);
                    MobSenses.serverTick();
                    ForgeNetworking.tickServer(server);
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener(MobSensesForge::gameEvent);
        if (FMLEnvironment.dist.isClient()) {
            ForgeClientHooks.register(context);
        }
    }

    @SuppressWarnings("removal")
    public MobSensesForge() {
        this(FMLJavaModLoadingContext.get());
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
