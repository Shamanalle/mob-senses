package com.kasper.mobsenses.server;

import com.kasper.mobsenses.AcousticMaterial;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.NoiseCoalescer;
import com.kasper.mobsenses.NoiseModel;
import com.kasper.mobsenses.Preset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bridges Minecraft server game events and world ticks to {@link MobSenses}'s noise model,
 * coalescer, ray-bundle occlusion, and {@link InvestigateGoal}.
 */
public final class ServerHearing {

    private static final Set<String> DEFAULT_LISTENERS = new HashSet<>(Arrays.asList(
            "minecraft:zombie",
            "minecraft:zombie_villager",
            "minecraft:husk",
            "minecraft:drowned",
            "minecraft:zombified_piglin",
            "minecraft:skeleton",
            "minecraft:stray",
            "minecraft:bogged",
            "minecraft:wither_skeleton",
            "minecraft:creeper",
            "minecraft:spider",
            "minecraft:cave_spider",
            "minecraft:pillager",
            "minecraft:vindicator",
            "minecraft:evoker",
            "minecraft:illusioner",
            "minecraft:ravager",
            "minecraft:witch",
            "minecraft:piglin",
            "minecraft:piglin_brute",
            "minecraft:enderman",
            "minecraft:blaze",
            "minecraft:breeze"
    ));

    private ServerHearing() {
    }

    /**
     * Records a world game event with full block/posture context if caused by a player or an
     * inherently loud world event (explosions, bells, note blocks, player projectiles).
     */
    public static void onGameEvent(
            ServerLevel level,
            String eventId,
            double x, double y, double z,
            Entity sourceEntity,
            BlockState affectedState) {
        if (level == null || eventId == null) {
            return;
        }
        NoiseModel.Kind kind = NoiseModel.classifyEvent(eventId);
        if (kind == null) {
            return;
        }
        Player player = responsiblePlayer(level, sourceEntity, kind, x, y, z);
        boolean playerInvolved = player != null || !kind.requiresPlayerSource();
        if (!playerInvolved) {
            return;
        }
        String worldId = Worlds.id(level);
        Preset preset = MobSenses.settings().presetOf(worldId);
        if (preset == Preset.OFF) {
            return;
        }

        NoiseModel.Posture posture = NoiseModel.Posture.WALKING;
        if (player != null) {
            if (player.isShiftKeyDown() || player.isCrouching()) {
                posture = NoiseModel.Posture.SNEAKING;
            } else if (player.isSprinting()) {
                posture = NoiseModel.Posture.SPRINTING;
            }
        }

        BlockPos eventPos = BlockPos.containing(x, y, z);
        BlockState surface = affectedState;
        if (surface == null || surface.isAir()) {
            BlockPos surfacePos = BlockAcoustics.blockUnderEvent(level, eventPos);
            surface = level.getBlockState(surfacePos);
        }
        String blockId = BlockAcoustics.blockId(surface);
        AcousticMaterial material = BlockAcoustics.material(surface, blockId);

        MobSenses.gameEvent(eventId, worldId, x, y, z, posture, material, blockId, player != null);
    }

    /**
     * Dispatches all coalesced noises queued during the current server tick across every loaded
     * dimension.
     */
    public static void tickServer(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            tickLevel(level);
        }
    }

    public static void tickLevel(ServerLevel level) {
        if (level == null) {
            return;
        }
        String worldId = Worlds.id(level);
        if (MobSenses.settings().presetOf(worldId) == Preset.OFF) {
            MobSenses.coalescer().drainWorld(worldId);
            return;
        }
        List<NoiseCoalescer.Alerted<Mob>> alertedList = MobSenses.coalescer().dispatchWorld(
                worldId,
                (ex, ey, ez, radius) -> findCandidateListeners(level, ex, ey, ez, radius),
                (sx, sy, sz, tx, ty, tz) -> BlockAcoustics.traceBundle(level, sx, sy, sz, tx, ty, tz));

        long now = System.currentTimeMillis();
        for (NoiseCoalescer.Alerted<Mob> alerted : alertedList) {
            Mob mob = alerted.mob();
            NoiseCoalescer.CoalescedNoise noise = alerted.noise();
            double speed = noise.kind() == NoiseModel.Kind.EXPLOSION
                    || noise.kind() == NoiseModel.Kind.COMBAT
                    || noise.kind() == NoiseModel.Kind.BELL
                    ? 1.15 : 1.0;
            if (InvestigateGoal.alert(mob, noise.x(), noise.y(), noise.z(), speed)) {
                MobSenses.alerted().record(now);
                if (MobSenses.debug()) {
                    ParticleSender.sendHearingTrace(
                            level,
                            noise.x(), noise.y(), noise.z(),
                            mob.getX(), mob.getEyeY(), mob.getZ());
                }
            }
        }
    }

    public static List<NoiseCoalescer.Candidate<Mob>> findCandidateListeners(
            ServerLevel level, double x, double y, double z, double radius) {
        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box,
                m -> isListener(m) && m.getTarget() == null);
        List<NoiseCoalescer.Candidate<Mob>> candidates = new ArrayList<>(mobs.size());
        for (Mob m : mobs) {
            candidates.add(new NoiseCoalescer.Candidate<>(m, m.getX(), m.getY(), m.getZ(), m.getEyeY()));
        }
        return candidates;
    }

    public static boolean isListener(Mob mob) {
        if (mob == null || !mob.isAlive() || mob.isNoAi()) {
            return false;
        }
        EntityType<?> type = mob.getType();
        if (Worlds.isTaggedListener(type)) {
            return true;
        }
        try {
            String key = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(type));
            if (DEFAULT_LISTENERS.contains(key)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return mob instanceof Enemy && !mob.getType().toString().contains("warden");
    }

    private static Player responsiblePlayer(ServerLevel level, Entity source, NoiseModel.Kind kind, double x, double y, double z) {
        if (source instanceof Player p) {
            return p.isSpectator() ? null : p;
        }
        if (source instanceof Projectile proj && proj.getOwner() instanceof Player p) {
            return p.isSpectator() ? null : p;
        }
        if (source == null && (kind == NoiseModel.Kind.DOOR
                || kind == NoiseModel.Kind.CONTAINER
                || kind == NoiseModel.Kind.BLOCK_BREAK
                || kind == NoiseModel.Kind.BLOCK_PLACE
                || kind == NoiseModel.Kind.NOTE_BLOCK
                || kind == NoiseModel.Kind.BELL)) {
            Player nearest = level.getNearestPlayer(x, y, z, 8.0, e -> e instanceof Player p && !p.isSpectator());
            if (nearest != null) {
                return nearest;
            }
        }
        return null;
    }
}
