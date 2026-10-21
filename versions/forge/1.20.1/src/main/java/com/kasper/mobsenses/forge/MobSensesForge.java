package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.server.MobSensesCommand;
import com.kasper.mobsenses.server.Slf4jLog;
import com.kasper.mobsenses.server.Worlds;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.VanillaGameEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge entrypoint (Minecraft 1.20 - 1.20.1): the server side, also inside the singleplayer client. Forge
 * hands every game event of a world to {@link VanillaGameEvent}.
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
                MobSenses.serverTick();
            }
        });
        MinecraftForge.EVENT_BUS.addListener(MobSensesForge::gameEvent);
    }

    private static void gameEvent(VanillaGameEvent e) {
        try {
            if (!(e.getLevel() instanceof ServerLevel level)) {
                return;
            }
            ResourceLocation id = BuiltInRegistries.GAME_EVENT.getKey(e.getVanillaEvent());
            Vec3 pos = e.getEventPosition();
            MobSenses.gameEvent(id == null ? "unknown" : id.toString(), Worlds.id(level), pos.x, pos.y, pos.z);
        } catch (Throwable t) {
            ModLog.failure("Reading a game event", t);
        }
    }
}
