package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.server.MobSensesCommand;
import com.kasper.mobsenses.server.ServerHearing;
import com.kasper.mobsenses.server.Slf4jLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Forge entrypoint (Minecraft 1.18 - 1.18.2): the server side, also inside the singleplayer client.
 */
@Mod(MobSenses.MOD_ID)
public final class MobSensesForge {

    public MobSensesForge() {
        ModLog.use(new Slf4jLog());
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent e) -> MobSenses.start(FMLPaths.CONFIGDIR.get(),
                new MobSenses.Platform("Forge", FMLLoader.versionInfo().mcVersion()), ModList.get().isLoaded("voicechat")));
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> MobSensesCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    ServerHearing.tickServer(server);
                    MobSenses.serverTick();
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener(MobSensesForge::gameEvent);
        if (FMLEnvironment.dist.isClient()) {
            ForgeClientHooks.register();
        }
    }

    private static void gameEvent(VanillaGameEvent e) {
        try {
            if (!(e.getLevel() instanceof ServerLevel level)) {
                return;
            }
            ResourceLocation id = Registry.GAME_EVENT.getKey(e.getVanillaEvent());
            BlockPos pos = e.getEventPosition();
            ServerHearing.onGameEvent(
                    level,
                    id == null ? "unknown" : id.toString(),
                    pos.getX() + 0.5,
                    pos.getY() + 0.5,
                    pos.getZ() + 0.5,
                    e.getCause(),
                    level.getBlockState(pos));
        } catch (Throwable t) {
            ModLog.failure("Reading a game event", t);
        }
    }
}
