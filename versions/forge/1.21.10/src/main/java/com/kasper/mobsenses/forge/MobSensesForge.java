package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.server.MobSensesCommand;
import com.kasper.mobsenses.server.ServerHearing;
import com.kasper.mobsenses.server.Slf4jLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * Forge entrypoint (Minecraft 1.21.6 - 26.x, EventBus 7): the server side, also inside the singleplayer client.
 */
@Mod(MobSenses.MOD_ID)
public final class MobSensesForge {

    public MobSensesForge() {
        ModLog.use(new Slf4jLog());
        ServerStartingEvent.BUS.addListener((ServerStartingEvent e) -> MobSenses.start(
                FMLPaths.CONFIGDIR.get(),
                new MobSenses.Platform("Forge", modVersion("minecraft")),
                modVersion("voicechat") != null));
        RegisterCommandsEvent.BUS.addListener((RegisterCommandsEvent e) -> MobSensesCommand.register(e.getDispatcher()));
        TickEvent.ServerTickEvent.Post.BUS.addListener((TickEvent.ServerTickEvent.Post e) -> {
            ServerHearing.tickServer(e.server());
            MobSenses.serverTick();
        });
        VanillaGameEvent.BUS.addListener(MobSensesForge::gameEvent);
        if (FMLEnvironment.dist.isClient()) {
            ForgeClientHooks.register();
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

    private static String modVersion(String modId) {
        try {
            Method byId = ModList.class.getMethod("getModContainerById", String.class);
            Object list = Modifier.isStatic(byId.getModifiers()) ? null : ModList.class.getMethod("get").invoke(null);
            Optional<?> container = (Optional<?>) byId.invoke(list, modId);
            if (!container.isPresent()) {
                return null;
            }
            Object info = ModContainer.class.getMethod("getModInfo").invoke(container.get());
            return String.valueOf(info.getClass().getMethod("getVersion").invoke(info));
        } catch (Throwable t) {
            return "unknown";
        }
    }
}
