package com.kasper.mobsenses.neoforge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.server.MobSensesCommand;
import com.kasper.mobsenses.server.Slf4jLog;
import com.kasper.mobsenses.server.Worlds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge entrypoint (Minecraft 1.21 - 1.21.1): the server side, also inside the singleplayer client.
 * NeoForge hands every game event of a world to {@link VanillaGameEvent}.
 */
@Mod(MobSenses.MOD_ID)
public final class MobSensesNeoForge {

    public MobSensesNeoForge(IEventBus modBus) {
        ModLog.use(new Slf4jLog());
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> MobSenses.start(FMLPaths.CONFIGDIR.get(),
                new MobSenses.Platform("NeoForge", FMLLoader.versionInfo().mcVersion()), ModList.get().isLoaded("voicechat")));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> MobSensesCommand.register(e.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> MobSenses.serverTick());
        NeoForge.EVENT_BUS.addListener(MobSensesNeoForge::gameEvent);
    }

    private static void gameEvent(VanillaGameEvent e) {
        try {
            if (!(e.getLevel() instanceof ServerLevel level)) {
                return;
            }
            String id = e.getVanillaEvent().unwrapKey().map(k -> k.location().toString()).orElse("unknown");
            Vec3 pos = e.getEventPosition();
            MobSenses.gameEvent(id, Worlds.id(level), pos.x, pos.y, pos.z);
        } catch (Throwable t) {
            ModLog.failure("Reading a game event", t);
        }
    }
}
