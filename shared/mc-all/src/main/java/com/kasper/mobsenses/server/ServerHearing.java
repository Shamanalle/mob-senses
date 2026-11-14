package com.kasper.mobsenses.server;

import com.kasper.mobsenses.AcousticMaterial;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.NoiseCoalescer;
import com.kasper.mobsenses.NoiseModel;
import com.kasper.mobsenses.Preset;
import com.kasper.mobsenses.ScentModel;
import com.kasper.mobsenses.ServerSettings;
import com.kasper.mobsenses.SightModel;
import com.kasper.mobsenses.StealthHudState;
import com.kasper.mobsenses.TacticalTargets;
import com.kasper.mobsenses.VoxelRay;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges Minecraft server game events and world ticks to {@link MobSenses}'s hearing, voice,
 * sight/light, and blood-scent models, ray-bundle occlusion, and {@link InvestigateGoal}.
 */
public final class ServerHearing {

    private static final int SENSE_TICK_INTERVAL = 10;
    private static final long HORDE_RALLY_COOLDOWN_MS = 4000L;
    private static final double PLAYER_ALERT_NOTIFY_RADIUS = 28.0;
    private static long serverTickCounter;
    private static final int RECENT_BULLET_RING_SIZE = 128;
    private static final int[] RECENT_BULLET_IDS = new int[RECENT_BULLET_RING_SIZE];
    private static int recentBulletCursor;
    private static final Map<UUID, String> LAST_FIREARM_KEY = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_FIREARM_AMMO = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_RALLY_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_DECOY_DROP_MS = new ConcurrentHashMap<>();
    private static final Map<Integer, DecoyTrack> TRACKED_DECOYS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> LAST_HURT_STAMP = new ConcurrentHashMap<>();
    private static final Map<Integer, PursuitStamp> LEGITIMATE_PURSUIT = new ConcurrentHashMap<>();
    private static int cleanupCounter;
    private static final Method ITEM_GET_TAG;
    private static final Method ITEM_GET_COMPONENTS;
    private static final List<Method> SILENCED_FIRE_HELPERS = findSilencedFireHelpers();
    private static final List<Field> ITEM_ENTITY_INT_FIELDS = findItemEntityIntFields();

    private static final class DecoyTrack {
        final UUID ownerId;
        final String worldId;
        final long startedAtMs;

        DecoyTrack(UUID ownerId, String worldId, long startedAtMs) {
            this.ownerId = ownerId;
            this.worldId = worldId;
            this.startedAtMs = startedAtMs;
        }
    }

    private static final class PursuitStamp {
        final UUID playerId;
        final long atMs;

        PursuitStamp(UUID playerId, long atMs) {
            this.playerId = playerId;
            this.atMs = atMs;
        }
    }

    private static List<Method> findSilencedFireHelpers() {
        List<Method> helpers = new ArrayList<>();
        String[] helperClasses = {
                "top.ribs.scguns.util.GunModifierHelper",
                "com.mrcrayfish.guns.util.GunModifierHelper",
                "ttv.migami.jeg.util.GunModifierHelper"
        };
        for (String clsName : helperClasses) {
            try {
                Class<?> helper = Class.forName(clsName);
                for (Method m : helper.getMethods()) {
                    if ("isSilencedFire".equals(m.getName()) && m.getParameterCount() == 1
                            && ItemStack.class.isAssignableFrom(m.getParameterTypes()[0])) {
                        helpers.add(m);
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return helpers;
    }

    private static List<Field> findItemEntityIntFields() {
        List<Field> fields = new ArrayList<>();
        try {
            for (Field f : ItemEntity.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == int.class) {
                    if (!Modifier.isPublic(f.getModifiers())) {
                        f.setAccessible(true);
                    }
                    fields.add(f);
                }
            }
        } catch (Throwable ignored) {
        }
        return fields;
    }

    static {
        Arrays.fill(RECENT_BULLET_IDS, -1);
        java.lang.reflect.Method getTag = null;
        java.lang.reflect.Method getComponents = null;
        for (java.lang.reflect.Method m : ItemStack.class.getMethods()) {
            if (m.getParameterCount() != 0) {
                continue;
            }
            String name = m.getName();
            if ("getTag".equals(name) || "method_7969".equals(name) || "m_41783_".equals(name)
                    || "func_77978_p".equals(name)) {
                getTag = m;
            } else if ("getComponents".equals(name) || "method_57353".equals(name)) {
                getComponents = m;
            }
        }
        ITEM_GET_TAG = getTag;
        ITEM_GET_COMPONENTS = getComponents;
    }

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

    /** Clears all entity-ID and player tracking caches when a server starts or stops. */
    public static synchronized void clear() {
        Arrays.fill(RECENT_BULLET_IDS, -1);
        recentBulletCursor = 0;
        serverTickCounter = 0L;
        cleanupCounter = 0;
        LAST_FIREARM_KEY.clear();
        LAST_FIREARM_AMMO.clear();
        LAST_RALLY_MS.clear();
        LAST_DECOY_DROP_MS.clear();
        TRACKED_DECOYS.clear();
        LAST_HURT_STAMP.clear();
        LEGITIMATE_PURSUIT.clear();
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
        if (player != null && BypassPermission.hasBypass(player)) {
            if (kind.requiresPlayerSource()
                    || player == sourceEntity
                    || (sourceEntity instanceof Projectile proj && proj.getOwner() == player)) {
                return;
            }
            player = null;
        }
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

        String effectiveEvent = resolveFirearmEvent(eventId, sourceEntity, player);
        String contextId = blockId;
        if (effectiveEvent.startsWith("mobsenses:gunshot")) {
            ItemStack firearm = findActiveFirearmForShoot(player);
            if (firearm == null) {
                firearm = findHeldFirearm(player);
            }
            if (firearm != null) {
                contextId = itemId(firearm) + " " + itemTagSummary(firearm);
            }
        }
        double envMult = MobSenses.settings().weatherMultiplier(level.isRaining(), level.isThundering());
        if (kind == NoiseModel.Kind.STEP && hasStealthEnchant(player)) {
            envMult *= Math.max(0.0, 1.0 - MobSenses.settings().featherFallingMuffling());
        }
        MobSenses.gameEvent(
                effectiveEvent, worldId, x, y, z,
                posture, material, contextId,
                player != null, envMult,
                player != null ? player.getUUID() : null);
    }

    /**
     * Dispatches all coalesced noises, voice events, sight checks, and scent trails across every
     * loaded dimension.
     */
    public static void tickServer(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (++cleanupCounter % 200 == 0) {
            long nowCleanup = System.currentTimeMillis();
            if (!LAST_FIREARM_KEY.isEmpty()) {
                for (java.util.UUID id : new ArrayList<>(LAST_FIREARM_KEY.keySet())) {
                    if (server.getPlayerList().getPlayer(id) == null) {
                        LAST_FIREARM_KEY.remove(id);
                        LAST_FIREARM_AMMO.remove(id);
                    }
                }
            }
            if (!LAST_RALLY_MS.isEmpty()) {
                LAST_RALLY_MS.entrySet().removeIf(e -> (nowCleanup - e.getValue()) > 30_000L);
            }
            if (!LAST_DECOY_DROP_MS.isEmpty()) {
                LAST_DECOY_DROP_MS.entrySet().removeIf(e -> (nowCleanup - e.getValue()) > 5_000L);
            }
            if (!TRACKED_DECOYS.isEmpty()) {
                TRACKED_DECOYS.entrySet().removeIf(e -> (nowCleanup - e.getValue().startedAtMs) > 6_000L);
            }
            if (!LEGITIMATE_PURSUIT.isEmpty()) {
                LEGITIMATE_PURSUIT.entrySet().removeIf(e -> (nowCleanup - e.getValue().atMs) > 15_000L);
            }
            if (LAST_HURT_STAMP.size() > 512) {
                LAST_HURT_STAMP.clear();
            }
        }
        boolean runSenses = (++serverTickCounter % SENSE_TICK_INTERVAL) == 0;
        for (ServerLevel level : server.getAllLevels()) {
            tickLevel(level, runSenses);
        }
    }

    public static void tickLevel(ServerLevel level) {
        tickLevel(level, true);
    }

    private static void tickLevel(ServerLevel level, boolean runSenses) {
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
                if (player.isSpectator() || BypassPermission.hasBypass(player)) {
                    MobSenses.voice().pollPendingRange(player.getUUID(), now);
                    continue;
                }
                double voiceRange = MobSenses.voice().pollPendingRange(player.getUUID(), now);
                if (voiceRange > 0.0) {
                    MobSenses.voiceEvent(worldId, player.getX(), player.getEyeY(), player.getZ(), voiceRange);
                }
            }
        }

        // 1b. Detect newly spawned gun bullets (e.g. TaCZ EntityKineticBullet, PointBlank, CGM, Scorched Guns)
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || (player.isCreative() && !MobSenses.debug()) || BypassPermission.hasBypass(player)) {
                continue;
            }
            detectPlayerGunshots(level, worldId, player);
        }

        // 1c. Track Q-dropped decoy items until they hit ground or water
        tickDroppedItemDecoys(level, worldId, now);

        // 2. Dispatch coalesced hearing & voice events through wall occlusion
        List<NoiseCoalescer.Alerted<Mob>> alertedList = MobSenses.coalescer().dispatchWorld(
                worldId,
                (ex, ey, ez, radius) -> findCandidateListeners(level, ex, ey, ez, radius),
                (sx, sy, sz, tx, ty, tz) -> BlockAcoustics.traceBundle(level, sx, sy, sz, tx, ty, tz));

        double normalSpeed = MobSenses.settings().investigateSpeed();
        double urgentSpeed = MobSenses.settings().urgentInvestigateSpeed();
        TacticalTargets.HazardWorld hazardWorld = BlockAcoustics.hazardWorld(level);
        boolean safeFilter = MobSenses.settings().safeGoalFilter();
        boolean suspicionOn = MobSenses.settings().suspicionEnabled();
        for (NoiseCoalescer.Alerted<Mob> alerted : alertedList) {
            Mob mob = alerted.mob();
            NoiseCoalescer.CoalescedNoise noise = alerted.noise();
            double speed = TacticalTargets.isUrgentKind(noise.kind()) ? urgentSpeed : normalSpeed;
            TacticalTargets.Point3 safeGoal = TacticalTargets.resolveSafeGoal(
                    hazardWorld, safeFilter, noise.x(), noise.y(), noise.z(),
                    mob.getX(), mob.getY(), mob.getZ());
            if (safeGoal == null) {
                continue;
            }
            if (TacticalTargets.shouldPauseSuspiciously(
                    noise.kind(), noise.eventId(), alerted.distance() * alerted.distance(),
                    alerted.audibleRange(), suspicionOn)
                    && InvestigateGoal.makeSuspicious(mob, safeGoal.x(), safeGoal.y(), safeGoal.z())) {
                MobSenses.alerted().record(now);
                notifyPlayersNearAlert(level, mob.getX(), mob.getY(), mob.getZ(),
                        StealthHudState.ALERT_SUSPICIOUS, now);
                if (MobSenses.debug()) {
                    ParticleSender.sendHearingTrace(
                            level, noise.x(), noise.y(), noise.z(),
                            mob.getX(), mob.getEyeY(), mob.getZ());
                }
                continue;
            }
            boolean lure = TacticalTargets.isIntentionalLure(noise.eventId());
            if (InvestigateGoal.alert(mob, safeGoal.x(), safeGoal.y(), safeGoal.z(), speed, false, lure)) {
                MobSenses.alerted().record(now);
                notifyPlayersNearAlert(level, mob.getX(), mob.getY(), mob.getZ(),
                        StealthHudState.ALERT_SEARCHING, now);
                if (MobSenses.debug()) {
                    ParticleSender.sendHearingTrace(
                            level,
                            noise.x(), noise.y(), noise.z(),
                            mob.getX(), mob.getEyeY(), mob.getZ());
                }
                rallyHordeAround(level, mob, safeGoal.x(), safeGoal.y(), safeGoal.z(), speed,
                        false, now, hazardWorld, safeFilter);
            }
        }

        // 2b. Unseen stealth ambush reactions (clear X-ray aggro, sweep incoming sector or flee when low HP)
        tickAmbushReactions(level, preset, now);

        // 2c. Per-tick darkness stealth & LKP: suppress X-ray targets against hidden players before aggro HUD checks
        tickDarknessStealth(level, preset, now);

        // 3. Sight (held light / darkness + sneaking) and Scent (wounded blood trail)
        if (runSenses) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || (player.isCreative() && !MobSenses.debug())) {
                    continue;
                }
                tickPlayerSightAndScent(level, worldId, player, preset, now);
            }
        }
    }

    private static double effectiveSightRangeFor(ServerLevel level, Player player, Preset preset, double baseSight) {
        BlockPos feetPos = player.blockPosition();
        int blockLight = level.getBrightness(LightLayer.BLOCK, feetPos);
        int skyLight = Math.max(0, level.getBrightness(LightLayer.SKY, feetPos) - level.getSkyDarken());
        int heldLight = MobSenses.settings().heldLightEnabled()
                ? Math.max(
                        SightModel.heldItemLight(itemId(player.getMainHandItem())),
                        SightModel.heldItemLight(itemId(player.getOffhandItem())))
                : 0;
        boolean sneaking = player.isShiftKeyDown() || player.isCrouching();
        boolean sprinting = player.isSprinting();
        double effectiveSight = SightModel.effectiveSightRange(
                baseSight, blockLight, skyLight, heldLight, sneaking, sprinting, preset);
        if (player.isInvisible() && heldLight == 0) {
            effectiveSight *= 0.15;
        }
        return effectiveSight;
    }

    /**
     * Suppresses vanilla X-ray target locks onto sneaking/dark-hidden players every tick. Only mobs that
     * had recently pursued the player while visible transition into an urgent Last Known Position search;
     * unaware mobs never leak the player's location or flash {@code ALERT_HUNTED}.
     */
    private static void tickDarknessStealth(ServerLevel level, Preset preset, long now) {
        if (!MobSenses.settings().darknessStealthEnabled() || level.players().isEmpty()) {
            return;
        }
        double sightMult = MobSenses.settings().sight();
        double configuredSightRange = MobSenses.settings().sightRange();
        if (!(sightMult > 0.0) || !(configuredSightRange > 0.0)) {
            return;
        }
        double baseSight = configuredSightRange * sightMult;
        double checkRadius = Math.max(40.0, Math.max(configuredSightRange, baseSight) + 12.0);
        double lkpSpeed = MobSenses.settings().urgentInvestigateSpeed();
        TacticalTargets.HazardWorld hazardWorld = BlockAcoustics.hazardWorld(level);
        boolean safeFilter = MobSenses.settings().safeGoalFilter();

        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()
                    || (player.isCreative() && !MobSenses.debug())
                    || BypassPermission.hasBypass(player)) {
                continue;
            }
            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();
            AABB box = new AABB(
                    px - checkRadius, py - 16.0, pz - checkRadius,
                    px + checkRadius, py + 16.0, pz + checkRadius);
            List<Mob> pursuers = level.getEntitiesOfClass(Mob.class, box,
                    m -> m.getTarget() == player && isListener(m));
            if (pursuers.isEmpty()) {
                continue;
            }
            double effectiveSight = effectiveSightRangeFor(level, player, preset, baseSight);
            boolean hiddenInDark = effectiveSight < baseSight * 0.78;
            double loseDistSq = Math.max(4.0, effectiveSight) * Math.max(4.0, effectiveSight);
            for (Mob mob : pursuers) {
                int hurtStamp = mob.getLastHurtByMobTimestamp();
                boolean directlyHurt = mob.getLastHurtByMob() == player
                        && hurtStamp > 0
                        && mob.tickCount >= hurtStamp
                        && (mob.tickCount - hurtStamp) <= 100;
                if (hiddenInDark && !directlyHurt && mob.distanceToSqr(player) > loseDistSq) {
                    InvestigateGoal.clearCombatTarget(mob, false);
                    PursuitStamp stamp = LEGITIMATE_PURSUIT.remove(mob.getId());
                    if (stamp != null && stamp.playerId.equals(player.getUUID())
                            && (now - stamp.atMs) <= 8_000L) {
                        TacticalTargets.Point3 lkp = TacticalTargets.resolveSafeGoal(
                                hazardWorld, safeFilter, px, py, pz, mob.getX(), mob.getY(), mob.getZ());
                        if (lkp != null && InvestigateGoal.alert(mob, lkp.x(), lkp.y(), lkp.z(), lkpSpeed, true)) {
                            MobSenses.recordPlayerAlert(player.getUUID(), StealthHudState.ALERT_SEARCHING, now);
                        }
                    }
                } else {
                    LEGITIMATE_PURSUIT.put(mob.getId(), new PursuitStamp(player.getUUID(), now));
                }
            }
        }
    }

    /**
     * Evaluates a player's wounded scent trail and light/posture visibility against nearby mobs.
     */
    public static void tickPlayerSightAndScent(
            ServerLevel level, String worldId, Player player, Preset preset, long now) {
        if (level == null || player == null || preset == null || preset == Preset.OFF
                || BypassPermission.hasBypass(player)) {
            return;
        }
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        double investigateSpeed = MobSenses.settings().investigateSpeed();
        TacticalTargets.HazardWorld hazardWorld = BlockAcoustics.hazardWorld(level);
        boolean safeFilter = MobSenses.settings().safeGoalFilter();

        // Active aggro HUD indicator: if any nearby listener currently targets this player, mark HUNTED
        AABB huntedBox = new AABB(
                px - PLAYER_ALERT_NOTIFY_RADIUS, py - 8.0, pz - PLAYER_ALERT_NOTIFY_RADIUS,
                px + PLAYER_ALERT_NOTIFY_RADIUS, py + 8.0, pz + PLAYER_ALERT_NOTIFY_RADIUS);
        List<Mob> activeHunters = level.getEntitiesOfClass(Mob.class, huntedBox,
                m -> m.isAlive() && m.getTarget() == player && isListener(m));
        if (!activeHunters.isEmpty()) {
            MobSenses.recordPlayerAlert(player.getUUID(), StealthHudState.ALERT_HUNTED, now);
        }

        // Scent: badly wounded players leave a trail (washed off by swimming or weakened by rain)
        BlockPos feetPos = player.blockPosition();
        boolean inWater = player.isInWater() || BlockAcoustics.isWaterAt(level, feetPos);
        boolean inRain = !inWater && level.isRainingAt(feetPos.above());
        double scentRange = MobSenses.scent().recordPlayer(
                worldId, player.getUUID(), px, py, pz,
                player.getHealth(), player.getMaxHealth(), preset, now, inWater, inRain);
        List<ScentModel.Crumb> playerCrumbs = MobSenses.scent().activePlayerCrumbs(worldId, player.getUUID(), now);
        if (scentRange > 0.0 || !playerCrumbs.isEmpty()) {
            double boxPad = scentRange > 0.0 ? scentRange : MobSenses.settings().scentMaxRange();
            double minX = px;
            double maxX = px;
            double minY = py;
            double maxY = py;
            double minZ = pz;
            double maxZ = pz;
            for (ScentModel.Crumb c : playerCrumbs) {
                minX = Math.min(minX, c.x());
                maxX = Math.max(maxX, c.x());
                minY = Math.min(minY, c.y());
                maxY = Math.max(maxY, c.y());
                minZ = Math.min(minZ, c.z());
                maxZ = Math.max(maxZ, c.z());
                boxPad = Math.max(boxPad, c.range());
            }
            if (scentRange > 0.0 && MobSenses.settings().bloodParticles()) {
                int start = Math.max(0, playerCrumbs.size() - 3);
                for (int i = start; i < playerCrumbs.size(); i++) {
                    ScentModel.Crumb c = playerCrumbs.get(i);
                    ParticleSender.sendBloodCrumb(level, c.x(), c.y(), c.z());
                }
            }
            AABB scentBox = new AABB(
                    minX - boxPad, minY - 6.0, minZ - boxPad,
                    maxX + boxPad, maxY + 6.0, maxZ + boxPad);
            List<Mob> trackers = level.getEntitiesOfClass(Mob.class, scentBox,
                    m -> m.isAlive() && !m.isNoAi() && m.getTarget() == null && isScentTrackerMob(m));
            int alertedCount = 0;
            for (Mob mob : trackers) {
                ScentModel.Crumb crumb = MobSenses.scent().findNearestScent(
                        worldId, mob.getX(), mob.getY(), mob.getZ(), mob.getEyeY(), now,
                        (fx, fy, fz, tx, ty, tz) -> BlockAcoustics.traceBundle(level, fx, fy, fz, tx, ty, tz));
                if (crumb == null) {
                    continue;
                }
                TacticalTargets.Point3 safeCrumb = TacticalTargets.resolveSafeGoal(
                        hazardWorld, safeFilter, crumb.x(), crumb.y(), crumb.z(),
                        mob.getX(), mob.getY(), mob.getZ());
                if (safeCrumb != null && InvestigateGoal.alert(mob, safeCrumb.x(), safeCrumb.y(), safeCrumb.z(), investigateSpeed)) {
                    MobSenses.alerted().record(now);
                    MobSenses.recordPlayerAlert(player.getUUID(), StealthHudState.ALERT_SEARCHING, now);
                    rallyHordeAround(level, mob, safeCrumb.x(), safeCrumb.y(), safeCrumb.z(), investigateSpeed,
                            true, now, hazardWorld, safeFilter);
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
        double configuredSightRange = MobSenses.settings().sightRange();
        if (!(sightMult > 0.0) || !(configuredSightRange > 0.0)) {
            return;
        }
        double baseSight = configuredSightRange * sightMult;
        double effectiveSight = effectiveSightRangeFor(level, player, preset, baseSight);

        if (effectiveSight > Math.min(SightModel.DEFAULT_SIGHT_RANGE, configuredSightRange)) {
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
                if (!lineOfSight) {
                    continue;
                }
                TacticalTargets.Point3 safeSpot = TacticalTargets.resolveSafeGoal(
                        hazardWorld, safeFilter, px, py, pz, mob.getX(), mob.getY(), mob.getZ());
                if (safeSpot != null && InvestigateGoal.alert(mob, safeSpot.x(), safeSpot.y(), safeSpot.z(), investigateSpeed)) {
                    MobSenses.alerted().record(now);
                    MobSenses.recordPlayerAlert(player.getUUID(), StealthHudState.ALERT_SEARCHING, now);
                    rallyHordeAround(level, mob, safeSpot.x(), safeSpot.y(), safeSpot.z(), investigateSpeed,
                            true, now, hazardWorld, safeFilter);
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
        }
    }

    public static boolean isScentTrackerMob(Mob mob) {
        if (mob == null) {
            return false;
        }
        EntityType<?> type = mob.getType();
        String key = Worlds.entityTypeId(type);
        if (MobSenses.settings().isIgnoredMob(key)) {
            return false;
        }
        if (MobSenses.settings().ignoreBosses() && !MobSenses.settings().isExtraListener(key)
                && isBossMob(mob, type, key)) {
            return false;
        }
        return ScentModel.isScentTracker(key);
    }

    private static boolean isBossMob(Mob mob, EntityType<?> type, String key) {
        return mob instanceof WitherBoss
                || mob instanceof EnderDragon
                || Worlds.isTaggedBoss(type)
                || ServerSettings.isKnownBossId(key);
    }

    public static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return Worlds.itemId(stack);
    }

    public static String itemTagSummary(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            if (ITEM_GET_COMPONENTS != null) {
                Object comp = ITEM_GET_COMPONENTS.invoke(stack);
                if (comp != null) {
                    return comp.toString();
                }
            }
            if (ITEM_GET_TAG != null) {
                Object tag = ITEM_GET_TAG.invoke(stack);
                if (tag != null) {
                    return tag.toString();
                }
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    /**
     * Checks runtime item type plus {@link NoiseModel#isFirearmItem(String)} so block items cannot masquerade
     * as firearms.
     */
    public static boolean isFirearmStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Object item = stack.getItem();
        if (item instanceof BlockItem) {
            String cls = item.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (!cls.contains("gun")) {
                return false;
            }
        }
        return NoiseModel.isFirearmItem(itemId(stack));
    }

    /**
     * Checks both {@link NoiseModel#isSilencedFirearm(String, String)} and runtime gun-mod APIs
     * (such as Scorched Guns 2 {@code IAirGun}/{@code AnimatedAirGunItem} and
     * {@code GunModifierHelper#isSilencedFire(ItemStack)} across SCGuns/CGM/JEG) so air guns and
     * integrally silenced guns from datapack overrides are recognized accurately.
     */
    public static boolean isSilencedFirearmStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String fId = itemId(stack);
        String tag = itemTagSummary(stack);
        if (NoiseModel.isSilencedFirearm(fId, tag)) {
            return true;
        }
        try {
            Object item = stack.getItem();
            for (Class<?> c = item.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                String clsName = c.getSimpleName().toLowerCase(Locale.ROOT);
                if (clsName.contains("airgun") || clsName.contains("air_gun")) {
                    return true;
                }
                for (Class<?> iface : c.getInterfaces()) {
                    String iName = iface.getSimpleName().toLowerCase(Locale.ROOT);
                    if (iName.contains("airgun") || iName.contains("air_gun")) {
                        return true;
                    }
                }
            }
            for (Method helperMethod : SILENCED_FIRE_HELPERS) {
                Object res = helperMethod.invoke(null, stack);
                if (Boolean.TRUE.equals(res)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Upgrades {@code projectile_shoot} / {@code projectile_land} events to
     * {@code mobsenses:gunshot}, {@code mobsenses:gunshot_silenced}, or {@code mobsenses:bullet_impact}
     * when fired from a firearm (TaCZ, PointBlank, CGM, Scorched Guns, etc.) or caused by a bullet entity.
     */
    private static String resolveFirearmEvent(String eventId, Entity sourceEntity, Player player) {
        String norm = NoiseModel.normalizeEvent(eventId);
        boolean isShootLike = "projectile_shoot".equals(norm);
        boolean isLandLike = "projectile_land".equals(norm);
        if (!isShootLike && !isLandLike) {
            return eventId;
        }
        String sourceTypeId = sourceEntity != null ? Worlds.entityTypeId(sourceEntity.getType()) : "";
        boolean bulletSource = !sourceTypeId.isEmpty() && NoiseModel.isBulletEntity(sourceTypeId);
        if (isLandLike) {
            return bulletSource ? "mobsenses:bullet_impact" : eventId;
        }
        if (!sourceTypeId.isEmpty() && NoiseModel.isNonFirearmProjectile(sourceTypeId)) {
            return eventId;
        }
        ItemStack firearmStack = findActiveFirearmForShoot(player);
        if (!bulletSource && firearmStack == null) {
            return eventId;
        }
        if (firearmStack != null && isSilencedFirearmStack(firearmStack)) {
            return "mobsenses:gunshot_silenced";
        }
        return "mobsenses:gunshot";
    }

    private static ItemStack findActiveFirearmForShoot(Player player) {
        if (player == null) {
            return null;
        }
        if (player.isUsingItem() && !isFirearmStack(player.getUseItem())) {
            return null;
        }
        ItemStack main = player.getMainHandItem();
        if (isFirearmStack(main)) {
            return main;
        }
        if (isNonFirearmProjectileItem(itemId(main))) {
            return null;
        }
        ItemStack off = player.getOffhandItem();
        return isFirearmStack(off) ? off : null;
    }

    private static boolean isNonFirearmProjectileItem(String fullId) {
        if (fullId == null || fullId.isEmpty()) {
            return false;
        }
        if (NoiseModel.isNonFirearmProjectile(fullId)) {
            return true;
        }
        String norm = NoiseModel.normalizeEvent(fullId);
        return "bow".equals(norm) || norm.endsWith("_bow")
                || "crossbow".equals(norm) || norm.endsWith("_crossbow")
                || "fishing_rod".equals(norm) || norm.endsWith("_fishing_rod");
    }

    private static ItemStack findHeldFirearm(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack main = player.getMainHandItem();
        if (isFirearmStack(main)) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (isFirearmStack(off)) {
            return off;
        }
        return null;
    }

    /**
     * Detects newly spawned firearm bullets (such as TaCZ's {@code tacz:bullet} / {@code EntityKineticBullet})
     * around a player holding a gun, even when the gun mod spawns the bullet directly via
     * {@code addFreshEntity} without emitting a vanilla {@code GameEvent.PROJECTILE_SHOOT}.
     */
    private static void detectPlayerGunshots(ServerLevel level, String worldId, Player player) {
        ItemStack firearm = findHeldFirearm(player);
        if (firearm == null) {
            LAST_FIREARM_KEY.remove(player.getUUID());
            LAST_FIREARM_AMMO.remove(player.getUUID());
            return;
        }
        String fId = itemId(firearm);
        String tag = itemTagSummary(firearm);
        String gunKey = fId + "|" + extractGunId(tag);
        int currentAmmo = extractFirearmAmmo(tag);
        String prevGunKey = LAST_FIREARM_KEY.put(player.getUUID(), gunKey);
        Integer prevAmmo = LAST_FIREARM_AMMO.put(player.getUUID(), currentAmmo);
        boolean ammoDecremented = gunKey.equals(prevGunKey)
                && prevAmmo != null
                && prevAmmo >= 0
                && currentAmmo >= 0
                && currentAmmo < prevAmmo;

        double px = player.getX();
        double py = player.getEyeY();
        double pz = player.getZ();
        AABB box = new AABB(px - 5.0, py - 3.5, pz - 5.0, px + 5.0, py + 3.5, pz + 5.0);
        List<Entity> nearby = level.getEntitiesOfClass(Entity.class, box, e -> {
            if (e == null || e == player || e.tickCount > 2) {
                return false;
            }
            if (!NoiseModel.isBulletEntity(Worlds.entityTypeId(e.getType()))) {
                return false;
            }
            if (e instanceof Projectile proj) {
                Entity owner = proj.getOwner();
                return owner == null || owner == player;
            }
            return true;
        });
        boolean firedNewBullet = false;
        for (Entity bullet : nearby) {
            if (markBulletSeen(bullet.getId())) {
                firedNewBullet = true;
            }
        }
        if (firedNewBullet || ammoDecremented) {
            String event = isSilencedFirearmStack(firearm)
                    ? "mobsenses:gunshot_silenced"
                    : "mobsenses:gunshot";
            double envMult = MobSenses.settings().weatherMultiplier(level.isRaining(), level.isThundering());
            MobSenses.gameEvent(event, worldId, px, py, pz,
                    NoiseModel.Posture.WALKING, null, fId + " " + tag, true, envMult, player.getUUID());

            if (MobSenses.settings().bulletImpactRange() > 0.0) {
                Vec3 look = player.getLookAngle();
                double endX = px + look.x * 64.0;
                double endY = py + look.y * 64.0;
                double endZ = pz + look.z * 64.0;
                int[] hitBlock = new int[3];
                boolean[] foundHit = {false};
                VoxelRay.walk(px, py, pz, endX, endY, endZ, 68, (bx, by, bz) -> {
                    double dx = (bx + 0.5) - px;
                    double dy = (by + 0.5) - py;
                    double dz = (bz + 0.5) - pz;
                    if (dx * dx + dy * dy + dz * dz < 4.0) {
                        return false;
                    }
                    BlockPos bPos = new BlockPos(bx, by, bz);
                    BlockState st = level.getBlockState(bPos);
                    if (!st.isAir()
                            && st.getFluidState().isEmpty()
                            && !BlockAcoustics.isOpenableOpen(st)
                            && !st.getCollisionShape(level, bPos).isEmpty()) {
                        hitBlock[0] = bx;
                        hitBlock[1] = by;
                        hitBlock[2] = bz;
                        foundHit[0] = true;
                        return true;
                    }
                    return false;
                });
                if (foundHit[0]) {
                    double hx = hitBlock[0] + 0.5;
                    double hy = hitBlock[1] + 0.5;
                    double hz = hitBlock[2] + 0.5;
                    double distSq = (hx - px) * (hx - px) + (hy - py) * (hy - py) + (hz - pz) * (hz - pz);
                    if (distSq >= 144.0) {
                        MobSenses.gameEvent("mobsenses:bullet_impact", worldId, hx, hy, hz,
                                NoiseModel.Posture.WALKING, null, null, true, envMult, player.getUUID());
                    }
                }
            }
        }
    }

    public static String extractGunId(String tag) {
        if (tag == null || tag.isEmpty()) {
            return "";
        }
        int idx = tag.indexOf("GunId");
        if (idx < 0) {
            return "";
        }
        int end = Math.min(tag.length(), idx + 48);
        return tag.substring(idx, end);
    }

    public static int extractFirearmAmmo(String tag) {
        if (tag == null || tag.isEmpty()) {
            return -1;
        }
        int val = parseTagInt(tag, "GunCurrentAmmoCount");
        if (val >= 0) {
            return val;
        }
        val = parseTagInt(tag, "AmmoCount");
        if (val >= 0) {
            return val;
        }
        val = parseTagInt(tag, "Ammo");
        if (val >= 0) {
            return val;
        }
        return parseTagInt(tag, "ammo");
    }

    private static int parseTagInt(String tag, String key) {
        int from = 0;
        while (true) {
            int idx = tag.indexOf(key, from);
            if (idx < 0) {
                return -1;
            }
            int boundaryIdx = (idx > 0 && tag.charAt(idx - 1) == '"') ? idx - 1 : idx;
            boolean leftBoundary = boundaryIdx == 0 || !Character.isLetterOrDigit(tag.charAt(boundaryIdx - 1));
            int after = idx + key.length();
            boolean rightBoundary = after < tag.length() && (tag.charAt(after) == '"' || tag.charAt(after) == ':'
                    || tag.charAt(after) == '=' || tag.charAt(after) == ' ');
            if (leftBoundary && rightBoundary) {
                int i = after;
                while (i < tag.length()) {
                    char c = tag.charAt(i);
                    if (c == ':' || c == '=' || c == ' ' || c == '"') {
                        i++;
                    } else {
                        break;
                    }
                }
                int start = i;
                while (i < tag.length() && Character.isDigit(tag.charAt(i))) {
                    i++;
                }
                if (i > start) {
                    try {
                        return Integer.parseInt(tag.substring(start, i));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            from = idx + 1;
        }
    }

    public static boolean hasStealthEnchant(Player player) {
        if (player == null) {
            return false;
        }
        String feet = itemTagSummary(player.getItemBySlot(EquipmentSlot.FEET)).toLowerCase(java.util.Locale.ROOT);
        if (feet.contains("feather_falling") || feet.contains("swift_sneak")) {
            return true;
        }
        String legs = itemTagSummary(player.getItemBySlot(EquipmentSlot.LEGS)).toLowerCase(java.util.Locale.ROOT);
        return legs.contains("swift_sneak");
    }

    private static synchronized boolean markBulletSeen(int entityId) {
        for (int existing : RECENT_BULLET_IDS) {
            if (existing == entityId) {
                return false;
            }
        }
        RECENT_BULLET_IDS[recentBulletCursor] = entityId;
        recentBulletCursor = (recentBulletCursor + 1) % RECENT_BULLET_RING_SIZE;
        return true;
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
        String key = Worlds.entityTypeId(type);
        if (MobSenses.settings().isIgnoredMob(key)) {
            return false;
        }
        if (MobSenses.settings().isExtraListener(key)) {
            return true;
        }
        if (MobSenses.settings().ignoreBosses() && isBossMob(mob, type, key)) {
            return false;
        }
        if (Worlds.isTaggedListener(type)) {
            return true;
        }
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

    private static void notifyPlayersNearAlert(ServerLevel level, double x, double y, double z,
                                               int alertLevel, long now) {
        if (level == null || alertLevel <= StealthHudState.ALERT_CALM) {
            return;
        }
        double maxDistSq = PLAYER_ALERT_NOTIFY_RADIUS * PLAYER_ALERT_NOTIFY_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || BypassPermission.hasBypass(player)) {
                continue;
            }
            double dx = player.getX() - x;
            double dy = player.getY() - y;
            double dz = player.getZ() - z;
            if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                MobSenses.recordPlayerAlert(player.getUUID(), alertLevel, now);
            }
        }
    }

    private static void rallyHordeAround(
            ServerLevel level, Mob leader,
            double x, double y, double z, double speed,
            boolean enforceLeaderCooldown, long now,
            TacticalTargets.HazardWorld hazardWorld, boolean safeFilter) {
        double hordeRadius = MobSenses.settings().hordeAlertRange();
        int maxAllies = MobSenses.settings().hordeMaxAllies();
        if (!(hordeRadius > 0.0) || maxAllies <= 0 || leader == null) {
            return;
        }
        if (enforceLeaderCooldown) {
            Long prev = LAST_RALLY_MS.get(leader.getUUID());
            if (prev != null && (now - prev) < HORDE_RALLY_COOLDOWN_MS) {
                return;
            }
            LAST_RALLY_MS.put(leader.getUUID(), now);
        }
        AABB hordeBox = new AABB(
                leader.getX() - hordeRadius, leader.getY() - 5.0, leader.getZ() - hordeRadius,
                leader.getX() + hordeRadius, leader.getY() + 5.0, leader.getZ() + hordeRadius);
        List<Mob> allies = level.getEntitiesOfClass(Mob.class, hordeBox,
                ally -> ally != leader && isListener(ally) && ally.getTarget() == null);
        int pulled = 0;
        for (Mob ally : allies) {
            TacticalTargets.Point3 offset = TacticalTargets.fanOutOffset(pulled);
            TacticalTargets.Point3 safe = TacticalTargets.resolveSafeGoal(
                    hazardWorld, safeFilter,
                    x + offset.x(), y + offset.y(), z + offset.z(),
                    ally.getX(), ally.getY(), ally.getZ());
            if (safe == null) {
                continue;
            }
            if (InvestigateGoal.alert(ally, safe.x(), safe.y(), safe.z(), speed)) {
                MobSenses.alerted().record(now);
                if (++pulled >= maxAllies) {
                    break;
                }
            }
        }
    }

    private static void tickDroppedItemDecoys(ServerLevel level, String worldId, long now) {
        if (!MobSenses.settings().decoyThrowEnabled()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()
                    || (player.isCreative() && !MobSenses.debug())
                    || BypassPermission.hasBypass(player)) {
                continue;
            }
            Long lastDropMs = LAST_DECOY_DROP_MS.get(player.getUUID());
            if (lastDropMs != null && (now - lastDropMs) < 200L) {
                continue;
            }
            double px = player.getX();
            double py = player.getEyeY();
            double pz = player.getZ();
            AABB box = new AABB(px - 2.6, py - 2.6, pz - 2.6, px + 2.6, py + 2.6, pz + 2.6);
            List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, box,
                    it -> it != null && it.isAlive() && it.tickCount <= 3 && !TRACKED_DECOYS.containsKey(it.getId()));
            for (ItemEntity item : dropped) {
                if (isPlayerThrownItemEntity(player, item)) {
                    TRACKED_DECOYS.put(item.getId(), new DecoyTrack(player.getUUID(), worldId, now));
                    LAST_DECOY_DROP_MS.put(player.getUUID(), now);
                    break;
                }
            }
        }
        if (TRACKED_DECOYS.isEmpty()) {
            return;
        }
        double envMult = MobSenses.settings().weatherMultiplier(level.isRaining(), level.isThundering());
        for (java.util.Map.Entry<Integer, DecoyTrack> entry : new ArrayList<>(TRACKED_DECOYS.entrySet())) {
            DecoyTrack track = entry.getValue();
            if (!worldId.equals(track.worldId)) {
                continue;
            }
            Entity ent = level.getEntity(entry.getKey());
            if (!(ent instanceof ItemEntity item) || !item.isAlive() || (now - track.startedAtMs) > 6000L) {
                TRACKED_DECOYS.remove(entry.getKey());
                continue;
            }
            BlockPos pos = item.blockPosition();
            if (item.tickCount >= 2 && (Worlds.isOnGround(item) || BlockAcoustics.isWaterAt(level, pos))) {
                TRACKED_DECOYS.remove(entry.getKey());
                BlockPos hitPos = BlockAcoustics.blockUnderEvent(level, pos);
                AcousticMaterial material = BlockAcoustics.material(level.getBlockState(hitPos));
                String thrownItemId = itemId(item.getItem());
                MobSenses.gameEvent(
                        "mobsenses:decoy_land",
                        worldId,
                        item.getX(), item.getY(), item.getZ(),
                        NoiseModel.Posture.WALKING,
                        material,
                        thrownItemId,
                        true,
                        envMult,
                        track.ownerId);
            }
        }
    }

    private static boolean isPlayerThrownItemEntity(ServerPlayer player, ItemEntity item) {
        if (!item.hasPickUpDelay()) {
            return false;
        }
        if (!ITEM_ENTITY_INT_FIELDS.isEmpty()) {
            boolean foundThrowDelay = false;
            for (Field f : ITEM_ENTITY_INT_FIELDS) {
                try {
                    int val = f.getInt(item);
                    if (val >= 30 && val <= 45) {
                        foundThrowDelay = true;
                        break;
                    }
                } catch (Throwable ignored) {
                }
            }
            if (!foundThrowDelay) {
                return false;
            }
        }
        double dropY = player.getEyeY() - 0.3;
        double dx = item.getX() - player.getX();
        double dy = item.getY() - dropY;
        double dz = item.getZ() - player.getZ();
        if (dx * dx + dy * dy + dz * dz > 2.25) {
            return false;
        }
        Vec3 vel = item.getDeltaMovement();
        if ((vel.x * vel.x + vel.y * vel.y + vel.z * vel.z) < 0.025) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        return (vel.x * look.x + vel.y * look.y + vel.z * look.z) > 0.04;
    }

    private static void tickAmbushReactions(ServerLevel level, Preset preset, long now) {
        if (level.players().isEmpty() || !MobSenses.settings().ambushReactionEnabled()) {
            return;
        }
        double sightMult = MobSenses.settings().sight();
        double configuredSight = MobSenses.settings().sightRange();
        double normalSpeed = MobSenses.settings().investigateSpeed();
        double urgentSpeed = MobSenses.settings().urgentInvestigateSpeed();
        double fleeRatio = MobSenses.settings().ambushFleeHealthThreshold() / 100.0;
        TacticalTargets.HazardWorld hazardWorld = BlockAcoustics.hazardWorld(level);
        boolean safeFilter = MobSenses.settings().safeGoalFilter();
        Set<Integer> checked = new HashSet<>();

        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || (player.isCreative() && !MobSenses.debug()) || BypassPermission.hasBypass(player)) {
                continue;
            }
            AABB box = new AABB(
                    player.getX() - 96.0, player.getY() - 48.0, player.getZ() - 96.0,
                    player.getX() + 96.0, player.getY() + 48.0, player.getZ() + 96.0);
            List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box,
                    m -> isListener(m) && m.getLastHurtByMob() instanceof Player);
            for (Mob mob : mobs) {
                if (!checked.add(mob.getId())) {
                    continue;
                }
                if (!(mob.getLastHurtByMob() instanceof Player attacker) || attacker.isSpectator()
                        || (attacker.isCreative() && !MobSenses.debug())
                        || BypassPermission.hasBypass(attacker)) {
                    continue;
                }
                int hurtStamp = mob.getLastHurtByMobTimestamp();
                if (hurtStamp <= 0 || Math.abs(mob.tickCount - hurtStamp) > 5) {
                    continue;
                }
                Integer prevStamp = LAST_HURT_STAMP.get(mob.getId());
                if (prevStamp != null && prevStamp == hurtStamp) {
                    continue;
                }
                LAST_HURT_STAMP.put(mob.getId(), hurtStamp);

                double distSq = mob.distanceToSqr(attacker);
                if (distSq <= 9.0) {
                    continue;
                }
                double baseSight = Math.max(4.0, configuredSight * sightMult);
                double effectiveSight = effectiveSightRangeFor(level, attacker, preset, baseSight);
                double sightLimit = Math.max(3.0, effectiveSight);
                boolean blocked = BlockAcoustics.traceSingleRay(
                        level,
                        new Vec3(mob.getX(), mob.getEyeY(), mob.getZ()),
                        new Vec3(attacker.getX(), attacker.getEyeY(), attacker.getZ())) > 0.25;
                if (distSq <= sightLimit * sightLimit && !blocked) {
                    continue;
                }

                InvestigateGoal.clearCombatTarget(mob, true);
                LEGITIMATE_PURSUIT.remove(mob.getId());
                double seedUnit = ((mob.getId() * 37 + hurtStamp * 13) & 0xFF) / 255.0;
                TacticalTargets.AmbushPlan plan = TacticalTargets.planAmbushReaction(
                        mob.getX(), mob.getY(), mob.getZ(),
                        attacker.getX(), attacker.getY(), attacker.getZ(),
                        mob.getHealth(), mob.getMaxHealth(),
                        normalSpeed, urgentSpeed, seedUnit, fleeRatio);
                TacticalTargets.Point3 goal = TacticalTargets.resolveSafeGoal(
                        hazardWorld, safeFilter, plan.targetX(), plan.targetY(), plan.targetZ(),
                        mob.getX(), mob.getY(), mob.getZ());
                if (goal == null) {
                    continue;
                }
                if (InvestigateGoal.alert(mob, goal.x(), goal.y(), goal.z(), plan.speed(), true)) {
                    MobSenses.alerted().record(now);
                    MobSenses.recordPlayerAlert(attacker.getUUID(), StealthHudState.ALERT_SEARCHING, now);
                    if (!plan.flee()) {
                        rallyHordeAround(level, mob, goal.x(), goal.y(), goal.z(), plan.speed(),
                                true, now, hazardWorld, safeFilter);
                    }
                }
            }
        }
    }
}
