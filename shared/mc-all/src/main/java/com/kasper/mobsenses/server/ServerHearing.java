package com.kasper.mobsenses.server;

import com.kasper.mobsenses.AcousticMaterial;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.NoiseCoalescer;
import com.kasper.mobsenses.NoiseModel;
import com.kasper.mobsenses.Preset;
import com.kasper.mobsenses.ScentModel;
import com.kasper.mobsenses.SightModel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bridges Minecraft server game events and world ticks to {@link MobSenses}'s hearing, voice,
 * sight/light, and blood-scent models, ray-bundle occlusion, and {@link InvestigateGoal}.
 */
public final class ServerHearing {

    private static final int SENSE_TICK_INTERVAL = 10;

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

        BlockPos eventPos = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
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
     * Dispatches all coalesced noises, voice events, sight checks, and scent trails across every
     * loaded dimension.
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
        Preset preset = MobSenses.settings().presetOf(worldId);
        if (preset == Preset.OFF) {
            MobSenses.coalescer().drainWorld(worldId);
            return;
        }
        long now = System.currentTimeMillis();

        // 1. Drain pending Simple Voice Chat frames for players in this dimension
        if (MobSenses.settings().voice()) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator()) {
                    continue;
                }
                double voiceRange = MobSenses.voice().pollPendingRange(player.getUUID(), now);
                if (voiceRange > 0.0) {
                    MobSenses.voiceEvent(worldId, player.getX(), player.getEyeY(), player.getZ(), voiceRange);
                }
            }
        }

        // 2. Dispatch coalesced hearing & voice events through wall occlusion
        List<NoiseCoalescer.Alerted<Mob>> alertedList = MobSenses.coalescer().dispatchWorld(
                worldId,
                (ex, ey, ez, radius) -> findCandidateListeners(level, ex, ey, ez, radius),
                (sx, sy, sz, tx, ty, tz) -> BlockAcoustics.traceBundle(level, sx, sy, sz, tx, ty, tz));

        for (NoiseCoalescer.Alerted<Mob> alerted : alertedList) {
            Mob mob = alerted.mob();
            NoiseCoalescer.CoalescedNoise noise = alerted.noise();
            double speed = noise.kind() == NoiseModel.Kind.EXPLOSION
                    || noise.kind() == NoiseModel.Kind.COMBAT
                    || noise.kind() == NoiseModel.Kind.BELL
                    || noise.kind() == NoiseModel.Kind.VOICE
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

        // 3. Sight (held light / darkness + sneaking) and Scent (wounded blood trail)
        if (level.getGameTime() % SENSE_TICK_INTERVAL == 0) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || (player.isCreative() && !MobSenses.debug())) {
                    continue;
                }
                tickPlayerSightAndScent(level, worldId, player, preset, now);
            }
        }
    }

    /**
     * Evaluates a player's wounded scent trail and light/posture visibility against nearby mobs.
     */
    public static void tickPlayerSightAndScent(
            ServerLevel level, String worldId, Player player, Preset preset, long now) {
        if (level == null || player == null || preset == null || preset == Preset.OFF) {
            return;
        }
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        // Scent: badly wounded players (<= 50% health) leave a trail followed by zombies and spiders
        double scentRange = MobSenses.scent().recordPlayer(
                worldId, player.getUUID(), px, py, pz,
                player.getHealth(), player.getMaxHealth(), preset, now);
        if (scentRange > 0.0) {
            AABB scentBox = new AABB(
                    px - scentRange, py - 6.0, pz - scentRange,
                    px + scentRange, py + 6.0, pz + scentRange);
            List<Mob> trackers = level.getEntitiesOfClass(Mob.class, scentBox,
                    m -> m.isAlive() && !m.isNoAi() && m.getTarget() == null && isScentTrackerMob(m));
            int alertedCount = 0;
            for (Mob mob : trackers) {
                ScentModel.Crumb crumb = MobSenses.scent().findNearestScent(
                        worldId, mob.getX(), mob.getY(), mob.getZ(), now);
                if (crumb != null && InvestigateGoal.alert(mob, crumb.x(), crumb.y(), crumb.z(), 1.10)) {
                    MobSenses.alerted().record(now);
                    if (MobSenses.debug()) {
                        ParticleSender.sendScentTrace(
                                level, crumb.x(), crumb.y(), crumb.z(),
                                mob.getX(), mob.getEyeY(), mob.getZ());
                    }
                    if (++alertedCount >= 6) {
                        break;
                    }
                }
            }
        }

        // Sight & Light: holding a torch/lantern increases detection range; darkness + sneaking hides
        double sightMult = MobSenses.settings().sight();
        if (!(sightMult > 0.0)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        int skyLight = Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
        int heldLight = Math.max(
                SightModel.heldItemLight(itemId(player.getMainHandItem())),
                SightModel.heldItemLight(itemId(player.getOffhandItem())));
        boolean sneaking = player.isShiftKeyDown() || player.isCrouching();
        boolean sprinting = player.isSprinting();

        double baseSight = SightModel.DEFAULT_SIGHT_RANGE * sightMult;
        double effectiveSight = SightModel.effectiveSightRange(
                baseSight, blockLight, skyLight, heldLight, sneaking, sprinting, preset);

        if (effectiveSight > SightModel.DEFAULT_SIGHT_RANGE) {
            AABB sightBox = new AABB(
                    px - effectiveSight, py - 8.0, pz - effectiveSight,
                    px + effectiveSight, py + 8.0, pz + effectiveSight);
            List<Mob> watchers = level.getEntitiesOfClass(Mob.class, sightBox,
                    m -> isListener(m) && m.getTarget() == null);
            int spotted = 0;
            for (Mob mob : watchers) {
                double dx = px - mob.getX();
                double dy = player.getEyeY() - mob.getEyeY();
                double dz = pz - mob.getZ();
                if (dx * dx + dy * dy + dz * dz > effectiveSight * effectiveSight) {
                    continue;
                }
                boolean lineOfSight = BlockAcoustics.traceSingleRay(
                        level,
                        new Vec3(mob.getX(), mob.getEyeY(), mob.getZ()),
                        new Vec3(px, player.getEyeY(), pz)) <= 0.1;
                if (lineOfSight && InvestigateGoal.alert(mob, px, py, pz, 1.10)) {
                    MobSenses.alerted().record(now);
                    if (MobSenses.debug()) {
                        ParticleSender.sendSightTrace(
                                level, px, player.getEyeY(), pz,
                                mob.getX(), mob.getEyeY(), mob.getZ());
                    }
                    if (++spotted >= 6) {
                        break;
                    }
                }
            }
        } else if (effectiveSight < SightModel.DEFAULT_SIGHT_RANGE * 0.75) {
            double checkRadius = SightModel.DEFAULT_SIGHT_RANGE + 6.0;
            AABB darkBox = new AABB(
                    px - checkRadius, py - 8.0, pz - checkRadius,
                    px + checkRadius, py + 8.0, pz + checkRadius);
            double loseDistSq = Math.max(4.0, effectiveSight) * Math.max(4.0, effectiveSight);
            List<Mob> pursuers = level.getEntitiesOfClass(Mob.class, darkBox,
                    m -> m.isAlive() && m.getTarget() == player && m.getLastHurtByMob() != player);
            for (Mob mob : pursuers) {
                if (mob.distanceToSqr(player) > loseDistSq) {
                    mob.setTarget(null);
                }
            }
        }
    }

    public static boolean isScentTrackerMob(Mob mob) {
        if (mob == null) {
            return false;
        }
        String key = Worlds.entityTypeId(mob.getType());
        return ScentModel.isScentTracker(key);
    }

    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return Worlds.itemId(stack);
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
        String key = Worlds.entityTypeId(type);
        if (DEFAULT_LISTENERS.contains(key)) {
            return true;
        }
        return mob instanceof Enemy && !key.contains("warden");
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
