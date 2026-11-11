package com.kasper.mobsenses.forge;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.server.MobSensesCommand;
import com.kasper.mobsenses.server.ServerBridge;
import com.kasper.mobsenses.server.ServerHearing;
import com.kasper.mobsenses.server.Slf4jLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Forge entrypoint for Minecraft 1.16.5 (includes pre-GameEvent listeners).
 */
@Mod(MobSenses.MOD_ID)
public final class MobSensesForge {

    private static final Map<UUID, Vec3> LAST_STEP = new HashMap<>();

    public MobSensesForge(FMLJavaModLoadingContext context) {
        ModLog.use(new Slf4jLog());
        ForgeNetworking.register();
        MinecraftForge.EVENT_BUS.addListener((FMLServerStartingEvent e) -> {
            ServerBridge.clear();
            LAST_STEP.clear();
            MobSenses.start(
                    FMLPaths.CONFIGDIR.get(),
                    new MobSenses.Platform("Forge", "1.16.5"),
                    ModList.get().isLoaded("voicechat"));
        });
        MinecraftForge.EVENT_BUS.addListener((FMLServerStoppingEvent e) -> {
            ServerBridge.clear();
            LAST_STEP.clear();
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getPlayer() != null) {
                ServerBridge.onDisconnect(e.getPlayer().getUUID());
                LAST_STEP.remove(e.getPlayer().getUUID());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> MobSensesCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    tickPlayerSteps(server);
                    ServerHearing.tickServer(server);
                    MobSenses.serverTick();
                    ForgeNetworking.tickServer(server);
                }
            }
        });
        registerPreGameEventHooks();
        if (FMLEnvironment.dist.isClient()) {
            ForgeClientHooks.register(context);
        }
    }

    public MobSensesForge() {
        this(FMLJavaModLoadingContext.get());
    }

    private static void registerPreGameEventHooks() {
        MinecraftForge.EVENT_BUS.addListener((ExplosionEvent.Start e) -> {
            if (e.getWorld() instanceof ServerLevel) {
                Vec3 pos = e.getExplosion().getPosition();
                ServerHearing.onGameEvent((ServerLevel) e.getWorld(), "minecraft:explode",
                        pos.x, pos.y, pos.z, e.getExplosion().getSourceMob(), null);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.BreakEvent e) -> {
            if (e.getWorld() instanceof ServerLevel) {
                BlockPos pos = e.getPos();
                ServerHearing.onGameEvent((ServerLevel) e.getWorld(), "minecraft:block_destroy",
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, e.getPlayer(), e.getState());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent e) -> {
            if (e.getWorld() instanceof ServerLevel) {
                BlockPos pos = e.getPos();
                ServerHearing.onGameEvent((ServerLevel) e.getWorld(), "minecraft:block_place",
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, e.getEntity(), e.getPlacedBlock());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (e.getWorld() instanceof ServerLevel && e.getHand() == InteractionHand.MAIN_HAND
                    && e.getPlayer() != null && !e.getPlayer().isSpectator()) {
                BlockPos pos = e.getPos();
                BlockState state = e.getWorld().getBlockState(pos);
                String event = classifyUseBlock(state.getBlock());
                if (event != null) {
                    ServerHearing.onGameEvent((ServerLevel) e.getWorld(), event,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, e.getPlayer(), state);
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((LivingHurtEvent e) -> {
            if (e.getEntityLiving().level instanceof ServerLevel) {
                Entity source = e.getSource().getEntity();
                if (source != null) {
                    Entity target = e.getEntityLiving();
                    ServerHearing.onGameEvent((ServerLevel) target.level, "minecraft:entity_damage",
                            target.getX(), target.getY(), target.getZ(), source, null);
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ProjectileImpactEvent e) -> {
            Entity proj = e.getEntity();
            if (proj != null && proj.level instanceof ServerLevel) {
                Entity owner = proj instanceof Projectile ? ((Projectile) proj).getOwner() : proj;
                ServerHearing.onGameEvent((ServerLevel) proj.level, "minecraft:projectile_land",
                        proj.getX(), proj.getY(), proj.getZ(), owner, null);
            }
        });
    }

    private static String classifyUseBlock(Block block) {
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
            return "minecraft:block_open";
        }
        if (block instanceof BellBlock) {
            return "minecraft:bell_ring";
        }
        if (block instanceof NoteBlock) {
            return "minecraft:note_block_play";
        }
        if (block instanceof AbstractChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock) {
            return "minecraft:container_open";
        }
        if (block instanceof LeverBlock || block instanceof ButtonBlock) {
            return "minecraft:block_activate";
        }
        return null;
    }

    private static void tickPlayerSteps(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()) {
                LAST_STEP.remove(player.getUUID());
                continue;
            }
            Vec3 pos = player.position();
            Vec3 prev = LAST_STEP.get(player.getUUID());
            if (prev == null) {
                LAST_STEP.put(player.getUUID(), pos);
                continue;
            }
            double dx = pos.x - prev.x;
            double dz = pos.z - prev.z;
            double horizSq = dx * dx + dz * dz;
            if (horizSq > 64.0) {
                LAST_STEP.put(player.getUUID(), pos);
                continue;
            }
            boolean swimming = player.isSwimming();
            if ((swimming || (player.isOnGround() && !player.isShiftKeyDown())) && horizSq >= 3.24) {
                LAST_STEP.put(player.getUUID(), pos);
                BlockPos below = new BlockPos(pos.x, pos.y - 0.2, pos.z);
                ServerLevel level = player.getLevel();
                ServerHearing.onGameEvent(level, swimming ? "minecraft:swim" : "minecraft:step",
                        pos.x, pos.y, pos.z, player, level.getBlockState(below));
            }
        }
    }
}
