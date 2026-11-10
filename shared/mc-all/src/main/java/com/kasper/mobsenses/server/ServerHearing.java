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
    private static final int RECENT_BULLET_RING_SIZE = 128;
    private static final int[] RECENT_BULLET_IDS = new int[RECENT_BULLET_RING_SIZE];
    private static int recentBulletCursor;
    private static long levelTickCounter;
    private static final java.lang.reflect.Method ITEM_GET_TAG;
    private static final java.lang.reflect.Method ITEM_GET_COMPONENTS;

    static {
        java.lang.reflect.Method getTag = null;
        java.lang.reflect.Method getComponents = null;
        for (java.lang.reflect.Method m : ItemStack.class.getMethods()) {
            if (m.getParameterCount() != 0) {
                continue;
            }
            String name = m.getName();
            if ("getTag".equals(name) || "method_7969".equals(name) || "m_41783_".equals(name)) {
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

        String effectiveEvent = resolveFirearmEvent(eventId, sourceEntity, player);
        MobSenses.gameEvent(effectiveEvent, worldId, x, y, z, posture, material, blockId, player != null);
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

        // 1b. Detect newly spawned gun bullets (e.g. TaCZ EntityKineticBullet, PointBlank, CGM, Scorched Guns)
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || (player.isCreative() && !MobSenses.debug())) {
                continue;
            }
            detectPlayerGunshots(level, worldId, player);
        }

        // 2. Dispatch coalesced hearing & voice events through wall occlusion
        List<NoiseCoalescer.Alerted<Mob>> alertedList = MobSenses.coalescer().dispatchWorld(
                worldId,
                (ex, ey, ez, radius) -> findCandidateListeners(level, ex, ey, ez, radius),
                (sx, sy, sz, tx, ty, tz) -> BlockAcoustics.traceBundle(level, sx, sy, sz, tx, ty, tz));

        double normalSpeed = MobSenses.settings().investigateSpeed();
        double urgentSpeed = MobSenses.settings().urgentInvestigateSpeed();
        for (NoiseCoalescer.Alerted<Mob> alerted : alertedList) {
            Mob mob = alerted.mob();
            NoiseCoalescer.CoalescedNoise noise = alerted.noise();
            double speed = noise.kind() == NoiseModel.Kind.EXPLOSION
                    || noise.kind() == NoiseModel.Kind.GUNSHOT
                    || noise.kind() == NoiseModel.Kind.COMBAT
                    || noise.kind() == NoiseModel.Kind.BELL
                    || noise.kind() == NoiseModel.Kind.VOICE
                    ? urgentSpeed : normalSpeed;
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
        if (++levelTickCounter % SENSE_TICK_INTERVAL == 0) {
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

        // Scent: badly wounded players leave a trail followed by zombies and spiders
        double scentRange = MobSenses.scent().recordPlayer(
                worldId, player.getUUID(), px, py, pz,
                player.getHealth(), player.getMaxHealth(), preset, now);
        if (scentRange > 0.0) {
            if (MobSenses.settings().bloodParticles()) {
                for (ScentModel.Crumb c : MobSenses.scent().activeCrumbs(worldId, now)) {
                    if (player.getUUID().equals(c.playerId())) {
                        ParticleSender.sendBloodCrumb(level, c.x(), c.y(), c.z());
                    }
                }
            }
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
        double configuredSightRange = MobSenses.settings().sightRange();
        if (!(sightMult > 0.0) || !(configuredSightRange > 0.0)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        int skyLight = Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
        int heldLight = MobSenses.settings().heldLightEnabled()
                ? Math.max(
                        SightModel.heldItemLight(itemId(player.getMainHandItem())),
                        SightModel.heldItemLight(itemId(player.getOffhandItem())))
                : 0;
        boolean sneaking = player.isShiftKeyDown() || player.isCrouching();
        boolean sprinting = player.isSprinting();

        double baseSight = configuredSightRange * sightMult;
        double effectiveSight = SightModel.effectiveSightRange(
                baseSight, blockLight, skyLight, heldLight, sneaking, sprinting, preset);

        if (effectiveSight > configuredSightRange) {
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
        } else if (MobSenses.settings().darknessStealthEnabled() && effectiveSight < configuredSightRange * 0.75) {
            double checkRadius = configuredSightRange + 6.0;
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
        if (MobSenses.settings().isIgnoredMob(key)) {
            return false;
        }
        return ScentModel.isScentTracker(key) || MobSenses.settings().isExtraListener(key);
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
     * Upgrades {@code projectile_shoot} / {@code projectile_land} / {@code entity_action} events to
     * {@code mobsenses:gunshot}, {@code mobsenses:gunshot_silenced}, or {@code mobsenses:bullet_impact}
     * when fired from a firearm (TaCZ, PointBlank, CGM, Scorched Guns, etc.) or caused by a bullet entity.
     */
    private static String resolveFirearmEvent(String eventId, Entity sourceEntity, Player player) {
        String norm = NoiseModel.normalizeEvent(eventId);
        boolean isShootLike = "projectile_shoot".equals(norm) || "entity_action".equals(norm);
        boolean isLandLike = "projectile_land".equals(norm);
        if (!isShootLike && !isLandLike) {
            return eventId;
        }
        boolean bulletSource = sourceEntity != null
                && NoiseModel.isBulletEntity(Worlds.entityTypeId(sourceEntity.getType()));
        ItemStack firearmStack = findHeldFirearm(player);
        if (!bulletSource && firearmStack == null) {
            return eventId;
        }
        if (isLandLike) {
            return "mobsenses:bullet_impact";
        }
        if (firearmStack != null) {
            String fId = itemId(firearmStack);
            String tag = itemTagSummary(firearmStack);
            if (NoiseModel.isSilencedFirearm(fId, tag)) {
                return "mobsenses:gunshot_silenced";
            }
        }
        return "mobsenses:gunshot";
    }

    private static ItemStack findHeldFirearm(Player player) {
        if (player == null) {
            return null;
        }
        ItemStack main = player.getMainHandItem();
        if (NoiseModel.isFirearmItem(itemId(main))) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (NoiseModel.isFirearmItem(itemId(off))) {
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
            return;
        }
        double px = player.getX();
        double py = player.getEyeY();
        double pz = player.getZ();
        AABB box = new AABB(px - 5.0, py - 3.5, pz - 5.0, px + 5.0, py + 3.5, pz + 5.0);
        List<Entity> nearby = level.getEntitiesOfClass(Entity.class, box, e -> {
            if (e == null || e == player || e.tickCount > 2) {
                return false;
            }
            if (e instanceof Projectile proj && proj.getOwner() == player) {
                return true;
            }
            return NoiseModel.isBulletEntity(Worlds.entityTypeId(e.getType()));
        });
        if (nearby.isEmpty()) {
            return;
        }
        boolean firedNewBullet = false;
        for (Entity bullet : nearby) {
            if (markBulletSeen(bullet.getId())) {
                firedNewBullet = true;
            }
        }
        if (firedNewBullet) {
            String fId = itemId(firearm);
            String tag = itemTagSummary(firearm);
            String event = NoiseModel.isSilencedFirearm(fId, tag)
                    ? "mobsenses:gunshot_silenced"
                    : "mobsenses:gunshot";
            MobSenses.gameEvent(event, worldId, px, py, pz, NoiseModel.Posture.WALKING, null, null, true);
        }
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
}
