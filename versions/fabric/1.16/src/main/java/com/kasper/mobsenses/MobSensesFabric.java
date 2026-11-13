package com.kasper.mobsenses;

import com.kasper.mobsenses.server.MobSensesCommand;
import com.kasper.mobsenses.server.ServerBridge;
import com.kasper.mobsenses.server.ServerHearing;
import com.kasper.mobsenses.server.Slf4jLog;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fabric server entrypoint for Minecraft 1.16.5 (includes pre-GameEvent player listeners).
 */
public final class MobSensesFabric implements net.fabricmc.api.ModInitializer {

    private static final Map<UUID, Vec3> LAST_STEP = new HashMap<>();
    private static final Map<UUID, Float> FALL_DIST = new HashMap<>();

    @Override
    public void onInitialize() {
        ModLog.use(new Slf4jLog());
        ModNetworking.registerCommon();
        ModNetworking.registerServer();
        FabricLoader loader = FabricLoader.getInstance();
        String minecraft = loader.getModContainer("minecraft")
                .map(m -> m.getMetadata().getVersion().getFriendlyString())
                .orElse("1.16.5");
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ServerBridge.clear();
            LAST_STEP.clear();
            FALL_DIST.clear();
            MobSenses.start(loader.getConfigDir(),
                    new MobSenses.Platform("Fabric", minecraft),
                    loader.isModLoaded("voicechat"));
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerBridge.clear();
            LAST_STEP.clear();
            FALL_DIST.clear();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler != null && handler.player != null) {
                UUID uid = handler.player.getUUID();
                LAST_STEP.remove(uid);
                FALL_DIST.remove(uid);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickPlayerSteps(server);
            ServerHearing.tickServer(server);
            MobSenses.serverTick();
            ModNetworking.tickServer(server);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                MobSensesCommand.register(dispatcher));
        registerPreGameEventHooks();
    }

    private static void registerPreGameEventHooks() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world instanceof ServerLevel) {
                ServerHearing.onGameEvent((ServerLevel) world, "minecraft:block_destroy",
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, player, state);
            }
        });
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world instanceof ServerLevel && !player.isSpectator()) {
                BlockPos pos = hitResult.getBlockPos();
                BlockState state = world.getBlockState(pos);
                String event = (hand == InteractionHand.MAIN_HAND
                        && (!player.isShiftKeyDown() || player.getMainHandItem().isEmpty()))
                        ? classifyUseBlock(state.getBlock()) : null;
                if (event != null) {
                    ServerHearing.onGameEvent((ServerLevel) world, event,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, player, state);
                } else {
                    ItemStack held = player.getItemInHand(hand);
                    if (held.getItem() instanceof BlockItem) {
                        Block placed = ((BlockItem) held.getItem()).getBlock();
                        BlockPos placePos = pos.relative(hitResult.getDirection());
                        ServerHearing.onGameEvent((ServerLevel) world, "minecraft:block_place",
                                placePos.getX() + 0.5, placePos.getY() + 0.5, placePos.getZ() + 0.5,
                                player, placed.defaultBlockState());
                    }
                }
            }
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world instanceof ServerLevel && !player.isSpectator()) {
                ServerHearing.onGameEvent((ServerLevel) world, "minecraft:entity_damage",
                        entity.getX(), entity.getY(), entity.getZ(), player, null);
            }
            return InteractionResult.PASS;
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
            UUID uid = player.getUUID();
            if (player.isSpectator() || !player.isAlive()) {
                LAST_STEP.remove(uid);
                FALL_DIST.remove(uid);
                continue;
            }
            Vec3 pos = player.position();
            boolean onGround = player.isOnGround();
            float prevFall = FALL_DIST.containsKey(uid) ? FALL_DIST.get(uid) : 0.0f;
            if (onGround) {
                if (prevFall > 1.25f && !player.isInWater()) {
                    BlockPos below = new BlockPos(pos.x, pos.y - 0.2, pos.z);
                    ServerLevel level = player.getLevel();
                    ServerHearing.onGameEvent(level, "minecraft:hit_ground",
                            pos.x, pos.y, pos.z, player, level.getBlockState(below));
                }
                FALL_DIST.remove(uid);
            } else {
                FALL_DIST.put(uid, Math.max(prevFall, player.fallDistance));
            }
            Vec3 prev = LAST_STEP.get(uid);
            if (prev == null) {
                LAST_STEP.put(uid, pos);
                continue;
            }
            double dx = pos.x - prev.x;
            double dz = pos.z - prev.z;
            double horizSq = dx * dx + dz * dz;
            if (horizSq > 64.0) {
                LAST_STEP.put(uid, pos);
                continue;
            }
            boolean swimming = player.isSwimming();
            if ((swimming || (onGround && !player.isShiftKeyDown())) && horizSq >= 3.24) {
                LAST_STEP.put(uid, pos);
                BlockPos below = new BlockPos(pos.x, pos.y - 0.2, pos.z);
                ServerLevel level = player.getLevel();
                ServerHearing.onGameEvent(level, swimming ? "minecraft:swim" : "minecraft:step",
                        pos.x, pos.y, pos.z, player, level.getBlockState(below));
            }
        }
    }
}
