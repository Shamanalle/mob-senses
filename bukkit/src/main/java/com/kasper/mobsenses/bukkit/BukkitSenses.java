package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.AcousticMaterial;
import com.kasper.mobsenses.AdminCommands;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.NoiseCoalescer;
import com.kasper.mobsenses.NoiseModel;
import com.kasper.mobsenses.Preset;
import com.kasper.mobsenses.ScentModel;
import com.kasper.mobsenses.ServerSettings;
import com.kasper.mobsenses.SightModel;
import com.kasper.mobsenses.StealthHudState;
import com.kasper.mobsenses.TacticalTargets;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full server-side parity for the Bukkit / Spigot / Paper / Purpur / Folia plugin:
 * <ul>
 *   <li>Coalesced hearing and Simple Voice Chat events attenuated through walls via {@link BukkitAcoustics}.</li>
 *   <li>Mob investigation pathing via {@link PaperPathfinder} on Paper/Purpur/Folia, with a pure-Spigot
 *       fallback ({@link #spigotFallbackMove}) when Paper's {@code Pathfinder} API is absent.</li>
 *   <li>Light &amp; posture sight adjustments (with solar/weather sky darkening) and wounded blood-scent tracking.</li>
 *   <li>Per-world presets ({@code /mobsenses world}).</li>
 *   <li>{@code mobsenses.bypass} silence permission for vanished moderators and staff.</li>
 *   <li>Folia-safe admin debug particles when {@code /mobsenses debug on} is active.</li>
 * </ul>
 */
public final class BukkitSenses {

    /** Permission node silencing all noise, voice, sight, and blood-scent emission (`default: false` in plugin.yml). */
    public static final String PERM_BYPASS = "mobsenses.bypass";

    private static final boolean HAS_PAPER_PATHFINDER = hasClass("com.destroystokyo.paper.entity.Pathfinder");
    private static final int SENSE_TICK_INTERVAL = 10;
    private static final int MAX_RAY_CHECKS_PER_NOISE = 24;
    private static final long HORDE_RALLY_COOLDOWN_MS = 4000L;
    private static final double PLAYER_ALERT_NOTIFY_RADIUS = 28.0;
    private static final long SUSPICION_WINDOW_MS = 4500L;

    private static final Set<String> LISTENER_NAMES = new HashSet<>(Arrays.asList(
            "zombie", "zombie_villager", "husk", "drowned", "zombified_piglin", "pig_zombie",
            "skeleton", "stray", "bogged", "wither_skeleton", "creeper", "spider", "cave_spider",
            "pillager", "vindicator", "evoker", "illusioner", "ravager", "witch",
            "piglin", "piglin_brute", "hoglin", "zoglin", "enderman", "blaze", "breeze",
            "slime", "magma_cube"
    ));

    private static final Map<UUID, Long> LAST_ALERT_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_RALLY_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_DECOY_DROP_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> SPIGOT_SUSPICIOUS_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, DecoyTrack> TRACKED_DECOYS = new ConcurrentHashMap<>();
    private static final Map<UUID, AmbushTrack> PENDING_AMBUSHES = new ConcurrentHashMap<>();
    private static final Map<UUID, TargetSuppress> UNSEEN_TARGET_SUPPRESS = new ConcurrentHashMap<>();
    private static final Map<UUID, DirectHurt> LAST_DIRECT_HURT_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, DirectHurt> LEGITIMATE_PURSUIT = new ConcurrentHashMap<>();
    private static final Map<UUID, PlayerSightSnapshot> PLAYER_SIGHT_SNAPSHOTS = new ConcurrentHashMap<>();
    private static int tickCounter;
    private static volatile Plugin activePlugin;
    private static volatile Scheduling activeScheduling;

    private static final class DecoyTrack {
        final UUID itemUuid;
        final UUID ownerId;
        final String itemTypeId;
        final long startedAtMs;
        volatile Location lastLoc;
        volatile int ageTicks;
        volatile boolean velocityConfirmed;

        DecoyTrack(UUID itemUuid, UUID ownerId, String itemTypeId, Location lastLoc,
                   long startedAtMs, boolean velocityConfirmed) {
            this.itemUuid = itemUuid;
            this.ownerId = ownerId;
            this.itemTypeId = itemTypeId;
            this.lastLoc = lastLoc;
            this.startedAtMs = startedAtMs;
            this.velocityConfirmed = velocityConfirmed;
        }
    }

    private static final class AmbushTrack {
        final UUID mobUuid;
        final UUID attackerId;
        final String worldName;
        final Location mobLoc;
        final Location attackerLoc;
        final long hurtAtMs;
        final int scheduledTick;

        AmbushTrack(UUID mobUuid, UUID attackerId, String worldName, Location mobLoc,
                    Location attackerLoc, long hurtAtMs, int scheduledTick) {
            this.mobUuid = mobUuid;
            this.attackerId = attackerId;
            this.worldName = worldName;
            this.mobLoc = mobLoc;
            this.attackerLoc = attackerLoc;
            this.hurtAtMs = hurtAtMs;
            this.scheduledTick = scheduledTick;
        }
    }

    private static final class TargetSuppress {
        final UUID attackerId;
        final long expiresAtMs;

        TargetSuppress(UUID attackerId, long expiresAtMs) {
            this.attackerId = attackerId;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final class DirectHurt {
        final UUID attackerId;
        final long atMs;

        DirectHurt(UUID attackerId, long atMs) {
            this.attackerId = attackerId;
            this.atMs = atMs;
        }
    }

    private static final class PlayerSightSnapshot {
        final String worldName;
        final double x;
        final double y;
        final double eyeY;
        final double z;
        final double effectiveSight;
        final long atMs;

        PlayerSightSnapshot(String worldName, double x, double y, double eyeY, double z,
                            double effectiveSight, long atMs) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.eyeY = eyeY;
            this.z = z;
            this.effectiveSight = effectiveSight;
            this.atMs = atMs;
        }
    }

    private BukkitSenses() {
    }

    public static void clear() {
        LAST_ALERT_MS.clear();
        LAST_RALLY_MS.clear();
        LAST_DECOY_DROP_MS.clear();
        SPIGOT_SUSPICIOUS_UNTIL.clear();
        TRACKED_DECOYS.clear();
        PENDING_AMBUSHES.clear();
        UNSEEN_TARGET_SUPPRESS.clear();
        LAST_DIRECT_HURT_MS.clear();
        LEGITIMATE_PURSUIT.clear();
        PLAYER_SIGHT_SNAPSHOTS.clear();
    }

    public static void forgetPlayer(UUID playerId) {
        if (playerId != null) {
            MobSenses.voice().removePlayer(playerId);
            MobSenses.clearPlayerAlert(playerId);
            PLAYER_SIGHT_SNAPSHOTS.remove(playerId);
            LAST_DECOY_DROP_MS.remove(playerId);
        }
    }

    /**
     * Returns {@code true} when {@code player} is in Spectator mode or holds {@code mobsenses.bypass}.
     */
    public static boolean isBypassed(Player player) {
        return player != null && (player.getGameMode() == GameMode.SPECTATOR || player.hasPermission(PERM_BYPASS));
    }

    /**
     * Returns {@code true} when the current thread owns {@code player}'s region on Folia
     * (always {@code true} on CraftBukkit/Spigot/Paper or when {@code player == null}).
     */
    public static boolean ownsPlayer(Player player) {
        if (player == null) {
            return true;
        }
        Scheduling sched = activeScheduling;
        return sched == null || sched.owns(player);
    }

    public static NoiseModel.Posture posture(Player player) {
        if (player == null) {
            return NoiseModel.Posture.WALKING;
        }
        if (player.isSneaking()) {
            return NoiseModel.Posture.SNEAKING;
        }
        if (player.isSprinting()) {
            return NoiseModel.Posture.SPRINTING;
        }
        return NoiseModel.Posture.WALKING;
    }

    public static double envMultiplier(World world, Player player, NoiseModel.Kind kind) {
        double mult = 1.0;
        if (world != null) {
            mult = MobSenses.settings().weatherMultiplier(world.hasStorm(), world.isThundering());
        }
        if (kind == NoiseModel.Kind.STEP && hasStealthBoots(player)) {
            mult *= Math.max(0.0, 1.0 - MobSenses.settings().featherFallingMuffling());
        }
        return mult;
    }

    /**
     * Checks whether {@code player} wears Feather Falling boots or Swift Sneak leggings.
     */
    public static boolean hasStealthBoots(Player player) {
        if (player == null) {
            return false;
        }
        try {
            EntityEquipment eq = player.getEquipment();
            if (eq == null) {
                return false;
            }
            if (hasEnchantMatching(eq.getBoots(), "feather_falling", "protection_fall", "swift_sneak")) {
                return true;
            }
            if (hasEnchantMatching(eq.getLeggings(), "swift_sneak")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean hasEnchantMatching(ItemStack item, String... tokens) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        for (Enchantment ench : item.getEnchantments().keySet()) {
            try {
                String key = ((Keyed) ench).getKey().getKey().toLowerCase(Locale.ROOT);
                for (String token : tokens) {
                    if (key.contains(token)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static void tick(Plugin plugin, Scheduling scheduling) {
        try {
            activePlugin = plugin;
            activeScheduling = scheduling;
            long now = System.currentTimeMillis();
            int currentTick = ++tickCounter;
            boolean senseTick = (currentTick % SENSE_TICK_INTERVAL) == 0;

            if ((currentTick % 400) == 0) {
                long cutoff = now - 60_000L;
                if (!LAST_ALERT_MS.isEmpty()) {
                    LAST_ALERT_MS.entrySet().removeIf(e -> e.getValue() < cutoff);
                }
                if (!LAST_RALLY_MS.isEmpty()) {
                    LAST_RALLY_MS.entrySet().removeIf(e -> e.getValue() < cutoff);
                }
                if (!LAST_DECOY_DROP_MS.isEmpty()) {
                    LAST_DECOY_DROP_MS.entrySet().removeIf(e -> (now - e.getValue()) > 5_000L);
                }
                if (!SPIGOT_SUSPICIOUS_UNTIL.isEmpty()) {
                    SPIGOT_SUSPICIOUS_UNTIL.entrySet().removeIf(e -> e.getValue() < now);
                }
                if (!UNSEEN_TARGET_SUPPRESS.isEmpty()) {
                    UNSEEN_TARGET_SUPPRESS.entrySet().removeIf(e -> e.getValue().expiresAtMs < now);
                }
                if (!LAST_DIRECT_HURT_MS.isEmpty()) {
                    LAST_DIRECT_HURT_MS.entrySet().removeIf(e -> e.getValue().atMs < cutoff);
                }
                if (!LEGITIMATE_PURSUIT.isEmpty()) {
                    LEGITIMATE_PURSUIT.entrySet().removeIf(e -> (now - e.getValue().atMs) > 15_000L);
                }
                if (!PLAYER_SIGHT_SNAPSHOTS.isEmpty()) {
                    PLAYER_SIGHT_SNAPSHOTS.entrySet().removeIf(e -> e.getValue().atMs < cutoff);
                }
            }

            if (!TRACKED_DECOYS.isEmpty()) {
                for (DecoyTrack track : new ArrayList<>(TRACKED_DECOYS.values())) {
                    World w = track.lastLoc != null ? track.lastLoc.getWorld() : null;
                    if (w == null || MobSenses.settings().presetOf(w.getName()) == Preset.OFF
                            || !MobSenses.settings().decoyThrowEnabled()) {
                        TRACKED_DECOYS.remove(track.itemUuid);
                        continue;
                    }
                    scheduling.atLocation(plugin, track.lastLoc, () -> pollDecoy(w, track, now));
                }
            }

            if (!PENDING_AMBUSHES.isEmpty()) {
                for (AmbushTrack track : new ArrayList<>(PENDING_AMBUSHES.values())) {
                    World w = Bukkit.getWorld(track.worldName);
                    if (w == null || MobSenses.settings().presetOf(track.worldName) == Preset.OFF) {
                        PENDING_AMBUSHES.remove(track.mobUuid);
                        continue;
                    }
                    scheduling.atLocation(plugin, track.mobLoc, () -> processAmbush(w, track, now));
                }
            }

            for (World world : Bukkit.getWorlds()) {
                String worldName = world.getName();
                Preset worldPreset = MobSenses.settings().presetOf(worldName);
                if (worldPreset == Preset.OFF) {
                    MobSenses.coalescer().drainWorld(worldName);
                    continue;
                }

                // 1. Drain pending Simple Voice Chat frames for players in this world (Folia-safe)
                if (MobSenses.settings().voice()) {
                    for (Player player : world.getPlayers()) {
                        scheduling.onPlayer(plugin, player, () -> {
                            double voiceRange = MobSenses.voice().pollPendingRange(player.getUniqueId(), now);
                            if (voiceRange > 0.0 && BukkitVoiceHook.shouldEmitVoice(player)) {
                                Location loc = player.getEyeLocation();
                                if (loc.getWorld() != null) {
                                    MobSenses.voiceEvent(loc.getWorld().getName(),
                                            loc.getX(), loc.getY(), loc.getZ(), voiceRange);
                                }
                            }
                        });
                    }
                }

                // 2. Drain coalesced noises for this world and dispatch each on its owning region/main thread
                List<NoiseCoalescer.CoalescedNoise> noises = MobSenses.coalescer().drainWorld(worldName);
                for (NoiseCoalescer.CoalescedNoise noise : noises) {
                    Location loc = new Location(world, noise.x(), noise.y(), noise.z());
                    scheduling.atLocation(plugin, loc, () -> dispatchSingleNoise(world, noise, now));
                }

                // 3. Sight & Scent checks per player on the player's owning region/main thread
                if (senseTick) {
                    for (Player player : world.getPlayers()) {
                        scheduling.onPlayer(plugin, player, () -> {
                            World pw = player.getWorld();
                            if (pw != null) {
                                tickPlayerSightAndScent(pw, pw.getName(), player, now);
                            }
                        });
                    }
                }
            }
        } catch (Throwable t) {
            ModLog.failure("Running Bukkit senses tick", t);
        }
    }

    private static void dispatchSingleNoise(World world, NoiseCoalescer.CoalescedNoise noise, long now) {
        Location center = new Location(world, noise.x(), noise.y(), noise.z());
        double r = noise.range();
        Collection<Entity> nearby = world.getNearbyEntities(center, r, r, r);
        List<Mob> candidates = new ArrayList<>();
        long cooldownMs = MobSenses.settings().alertCooldownMs();
        for (Entity entity : nearby) {
            if (entity instanceof Mob) {
                Mob mob = (Mob) entity;
                if (!isListener(mob) || mob.getTarget() != null) {
                    continue;
                }
                if (cooldownMs > 0L) {
                    Long prev = LAST_ALERT_MS.get(mob.getUniqueId());
                    if (prev != null && (now - prev) < cooldownMs) {
                        continue;
                    }
                }
                candidates.add(mob);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort((a, b) -> Double.compare(
                a.getLocation().distanceSquared(center),
                b.getLocation().distanceSquared(center)));

        int alerted = 0;
        int rayChecks = 0;
        double normalSpeed = MobSenses.settings().investigateSpeed();
        double urgentSpeed = MobSenses.settings().urgentInvestigateSpeed();
        double speed = TacticalTargets.isUrgentKind(noise.kind()) ? urgentSpeed : normalSpeed;

        int maxAlerts = MobSenses.settings().maxAlertedMobs();
        TacticalTargets.HazardWorld hazardWorld = BukkitAcoustics.hazardWorld(world);
        boolean safeFilter = MobSenses.settings().safeGoalFilter();
        boolean suspicionOn = MobSenses.settings().suspicionEnabled();
        for (Mob mob : candidates) {
            if (alerted >= maxAlerts || rayChecks >= MAX_RAY_CHECKS_PER_NOISE) {
                break;
            }
            Location eye = mob.getEyeLocation();
            double dist = eye.distance(center);
            if (dist > r) {
                continue;
            }
            rayChecks++;
            double thickness = BukkitAcoustics.traceBundle(
                    world, noise.x(), noise.y(), noise.z(), eye.getX(), eye.getY(), eye.getZ());
            double audible = NoiseModel.audibleRange(r, thickness);
            if (dist > audible) {
                continue;
            }
            Location mobLoc = mob.getLocation();
            TacticalTargets.Point3 safeGoal = TacticalTargets.resolveSafeGoal(
                    hazardWorld, safeFilter, noise.x(), noise.y(), noise.z(),
                    mobLoc.getX(), mobLoc.getY(), mobLoc.getZ());
            if (safeGoal == null) {
                continue;
            }
            Location goalLoc = new Location(world, safeGoal.x(), safeGoal.y(), safeGoal.z());
            if (TacticalTargets.shouldPauseSuspiciously(
                    noise.kind(), noise.eventId(), dist * dist, audible, suspicionOn)
                    && makeSuspicious(mob, goalLoc)) {
                alerted++;
                LAST_ALERT_MS.put(mob.getUniqueId(), now);
                MobSenses.alerted().record(now);
                notifyPlayersNearAlert(world, mobLoc.getX(), mobLoc.getY(), mobLoc.getZ(),
                        StealthHudState.ALERT_SUSPICIOUS, now);
                if (MobSenses.debug()) {
                    sendDebugTrace(world, center, eye);
                }
                continue;
            }
            boolean lure = TacticalTargets.isIntentionalLure(noise.eventId());
            if (investigate(mob, goalLoc, speed, false, lure)) {
                alerted++;
                LAST_ALERT_MS.put(mob.getUniqueId(), now);
                MobSenses.alerted().record(now);
                notifyPlayersNearAlert(world, mobLoc.getX(), mobLoc.getY(), mobLoc.getZ(),
                        StealthHudState.ALERT_SEARCHING, now);
                if (MobSenses.debug()) {
                    sendDebugTrace(world, center, eye);
                }
                rallyHordeAround(world, mob, safeGoal.x(), safeGoal.y(), safeGoal.z(), speed,
                        false, now, hazardWorld, safeFilter);
            }
        }
    }

    private static void tickPlayerSightAndScent(
            World world, String worldName, Player player, long now) {
        if (!player.isOnline() || !player.isValid()) {
            return;
        }
        if (isBypassed(player) || (player.getGameMode() == GameMode.CREATIVE && !MobSenses.debug())) {
            return;
        }
        Preset preset = MobSenses.settings().presetOf(worldName);
        if (preset == Preset.OFF) {
            return;
        }
        Location ploc = player.getLocation();
        double px = ploc.getX();
        double py = ploc.getY();
        double pz = ploc.getZ();
        double maxHealth = maxHealth(player);
        double investigateSpeed = MobSenses.settings().investigateSpeed();
        TacticalTargets.HazardWorld hazardWorld = BukkitAcoustics.hazardWorld(world);
        boolean safeFilter = MobSenses.settings().safeGoalFilter();

        double sightMult = MobSenses.settings().sight();
        double configuredSightRange = MobSenses.settings().sightRange();
        boolean sightEnabled = sightMult > 0.0 && configuredSightRange > 0.0;
        double baseSight = sightEnabled ? configuredSightRange * sightMult : 0.0;
        double effectiveSight = sightEnabled ? sightRangeFor(world, player, preset, baseSight) : 0.0;
        if (sightEnabled) {
            PLAYER_SIGHT_SNAPSHOTS.put(player.getUniqueId(),
                    new PlayerSightSnapshot(worldName, px, py, player.getEyeLocation().getY(), pz, effectiveSight, now));
        }

        // Darkness stealth & LKP BEFORE ALERT_HUNTED check: clear X-ray locks; search LKP only if legitimately pursued
        if (sightEnabled && MobSenses.settings().darknessStealthEnabled()) {
            boolean hiddenInDark = effectiveSight < baseSight * 0.78;
            double checkRadius = Math.max(40.0, Math.max(configuredSightRange, baseSight) + 12.0);
            double loseDistSq = Math.max(4.0, effectiveSight) * Math.max(4.0, effectiveSight);
            double lkpSpeed = MobSenses.settings().urgentInvestigateSpeed();
            Collection<Entity> nearbyPursuers = world.getNearbyEntities(ploc, checkRadius, 16.0, checkRadius);
            for (Entity entity : nearbyPursuers) {
                if (!(entity instanceof Mob)) {
                    continue;
                }
                Mob mob = (Mob) entity;
                LivingEntity target = mob.getTarget();
                if (!mob.isValid() || target == null
                        || !target.getUniqueId().equals(player.getUniqueId())
                        || !isListener(mob)) {
                    continue;
                }
                if (hiddenInDark
                        && mob.getLocation().distanceSquared(ploc) > loseDistSq
                        && !wasRecentlyHurtDirectly(mob.getUniqueId(), player.getUniqueId(), now)) {
                    UNSEEN_TARGET_SUPPRESS.put(mob.getUniqueId(), new TargetSuppress(player.getUniqueId(), now + 900L));
                    clearMobCombatTarget(mob);
                    DirectHurt prev = LEGITIMATE_PURSUIT.remove(mob.getUniqueId());
                    if (prev != null && prev.attackerId.equals(player.getUniqueId())
                            && (now - prev.atMs) <= 8_000L) {
                        Location mloc = mob.getLocation();
                        TacticalTargets.Point3 lkp = TacticalTargets.resolveSafeGoal(
                                hazardWorld, safeFilter, px, py, pz, mloc.getX(), mloc.getY(), mloc.getZ());
                        if (lkp != null && investigate(
                                mob, new Location(world, lkp.x(), lkp.y(), lkp.z()), lkpSpeed, true)) {
                            LAST_ALERT_MS.put(mob.getUniqueId(), now);
                            MobSenses.recordPlayerAlert(player.getUniqueId(), StealthHudState.ALERT_SEARCHING, now);
                        }
                    }
                } else {
                    LEGITIMATE_PURSUIT.put(mob.getUniqueId(), new DirectHurt(player.getUniqueId(), now));
                }
            }
        }

        // Active aggro HUD indicator: stamp legitimate hunters and mark HUNTED
        Collection<Entity> huntCheck = world.getNearbyEntities(
                ploc, PLAYER_ALERT_NOTIFY_RADIUS, 8.0, PLAYER_ALERT_NOTIFY_RADIUS);
        boolean hunted = false;
        for (Entity entity : huntCheck) {
            if (entity instanceof Mob) {
                Mob mob = (Mob) entity;
                LivingEntity target = mob.getTarget();
                if (mob.isValid() && target != null
                        && target.getUniqueId().equals(player.getUniqueId())
                        && isListener(mob)) {
                    LEGITIMATE_PURSUIT.put(mob.getUniqueId(), new DirectHurt(player.getUniqueId(), now));
                    hunted = true;
                }
            }
        }
        if (hunted) {
            MobSenses.recordPlayerAlert(player.getUniqueId(), StealthHudState.ALERT_HUNTED, now);
        }

        // Scent trail for badly wounded players (washed off by swimming or weakened by rain)
        int bx = ploc.getBlockX();
        int by = ploc.getBlockY();
        int bz = ploc.getBlockZ();
        boolean inWater = BukkitAcoustics.isWaterAt(world, bx, by, bz)
                || BukkitAcoustics.isWaterAt(world, bx, by + 1, bz);
        boolean inRain = !inWater && BukkitAcoustics.isSkyExposedRain(world, bx, by, bz);
        double scentRange = MobSenses.scent().recordPlayer(
                worldName, player.getUniqueId(), px, py, pz,
                player.getHealth(), maxHealth, preset, now, inWater, inRain);
        List<ScentModel.Crumb> playerCrumbs = MobSenses.scent().activePlayerCrumbs(
                worldName, player.getUniqueId(), now);
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
                    sendBloodCrumb(world, c.x(), c.y(), c.z());
                }
            }
            Location boxCenter = new Location(world, (minX + maxX) * 0.5, (minY + maxY) * 0.5, (minZ + maxZ) * 0.5);
            double halfX = (maxX - minX) * 0.5 + boxPad;
            double halfY = (maxY - minY) * 0.5 + 6.0;
            double halfZ = (maxZ - minZ) * 0.5 + boxPad;
            Collection<Entity> nearby = world.getNearbyEntities(boxCenter, halfX, halfY, halfZ);
            int count = 0;
            for (Entity entity : nearby) {
                if (!(entity instanceof Mob)) {
                    continue;
                }
                Mob mob = (Mob) entity;
                if (!mob.isValid() || mob.getTarget() != null || !isScentTracker(mob)) {
                    continue;
                }
                Location mloc = mob.getLocation();
                ScentModel.Crumb crumb = MobSenses.scent().findNearestScent(
                        worldName, mloc.getX(), mloc.getY(), mloc.getZ(), mob.getEyeLocation().getY(), now,
                        (fx, fy, fz, tx, ty, tz) -> BukkitAcoustics.traceBundle(world, fx, fy, fz, tx, ty, tz));
                if (crumb != null) {
                    TacticalTargets.Point3 safeCrumb = TacticalTargets.resolveSafeGoal(
                            hazardWorld, safeFilter, crumb.x(), crumb.y(), crumb.z(),
                            mloc.getX(), mloc.getY(), mloc.getZ());
                    if (safeCrumb != null) {
                        Location target = new Location(world, safeCrumb.x(), safeCrumb.y(), safeCrumb.z());
                        if (investigate(mob, target, investigateSpeed, false)) {
                            LAST_ALERT_MS.put(mob.getUniqueId(), now);
                            MobSenses.alerted().record(now);
                            MobSenses.recordPlayerAlert(player.getUniqueId(), StealthHudState.ALERT_SEARCHING, now);
                            rallyHordeAround(world, mob, safeCrumb.x(), safeCrumb.y(), safeCrumb.z(),
                                    investigateSpeed, true, now, hazardWorld, safeFilter);
                            if (MobSenses.debug()) {
                                sendScentDebugTrace(world, target, mob.getEyeLocation());
                            }
                            if (++count >= 6) {
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Sight & Light (accounting for time-of-day & storm sky darkening on Bukkit)
        if (!sightEnabled) {
            return;
        }
        if (effectiveSight > Math.min(SightModel.DEFAULT_SIGHT_RANGE, configuredSightRange)) {
            Collection<Entity> nearby = world.getNearbyEntities(ploc, effectiveSight, 8.0, effectiveSight);
            int spotted = 0;
            for (Entity entity : nearby) {
                if (!(entity instanceof Mob)) {
                    continue;
                }
                Mob mob = (Mob) entity;
                if (!isListener(mob) || mob.getTarget() != null) {
                    continue;
                }
                if (mob.getEyeLocation().distanceSquared(player.getEyeLocation()) <= effectiveSight * effectiveSight
                        && mob.hasLineOfSight(player)) {
                    Location mloc = mob.getLocation();
                    TacticalTargets.Point3 safeSpot = TacticalTargets.resolveSafeGoal(
                            hazardWorld, safeFilter, px, py, pz, mloc.getX(), mloc.getY(), mloc.getZ());
                    if (safeSpot != null && investigate(
                            mob, new Location(world, safeSpot.x(), safeSpot.y(), safeSpot.z()),
                            investigateSpeed, false)) {
                        LAST_ALERT_MS.put(mob.getUniqueId(), now);
                        MobSenses.alerted().record(now);
                        MobSenses.recordPlayerAlert(player.getUniqueId(), StealthHudState.ALERT_SEARCHING, now);
                        rallyHordeAround(world, mob, safeSpot.x(), safeSpot.y(), safeSpot.z(),
                                investigateSpeed, true, now, hazardWorld, safeFilter);
                        if (MobSenses.debug()) {
                            sendDebugTrace(world, player.getEyeLocation(), mob.getEyeLocation());
                        }
                        if (++spotted >= 6) {
                            break;
                        }
                    }
                }
            }
        }
    }

    private static double sightRangeFor(World world, Player player, Preset preset, double baseSight) {
        Location ploc = player.getLocation();
        Block block = ploc.getBlock();
        int blockLight = block.getLightFromBlocks();
        int skyLight = Math.max(0, block.getLightFromSky() - skyDarkening(world));
        EntityEquipment eq = player.getEquipment();
        int heldLight = 0;
        if (eq != null && MobSenses.settings().heldLightEnabled()) {
            heldLight = Math.max(
                    SightModel.heldItemLight(itemId(eq.getItemInMainHand())),
                    SightModel.heldItemLight(itemId(eq.getItemInOffHand())));
        }
        double effectiveSight = SightModel.effectiveSightRange(
                baseSight, blockLight, skyLight, heldLight,
                player.isSneaking(), player.isSprinting(), preset);
        if (player.isInvisible() && heldLight == 0) {
            effectiveSight *= 0.15;
        }
        return effectiveSight;
    }

    private static boolean wasRecentlyHurtDirectly(UUID mobId, UUID playerId, long now) {
        DirectHurt hurt = LAST_DIRECT_HURT_MS.get(mobId);
        return hurt != null && hurt.attackerId.equals(playerId) && (now - hurt.atMs) < 5000L;
    }

    /**
     * Computes vanilla sky-light darkening ({@code 0..11}) from time of day and weather, because
     * Bukkit's {@link Block#getLightFromSky()} returns baked sky exposure (`15` outdoors at midnight).
     */
    static int skyDarkening(World world) {
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            return 0;
        }
        long t = ((world.getTime() % 24000L) + 24000L) % 24000L;
        int darken;
        if (t >= 13800L && t <= 22200L) {
            darken = 11;
        } else if (t > 12000L && t < 13800L) {
            darken = (int) Math.round(((t - 12000L) / 1800.0) * 11.0);
        } else if (t > 22200L) {
            darken = (int) Math.round(((24000L - t) / 1800.0) * 11.0);
        } else {
            darken = 0;
        }
        if (world.isThundering()) {
            darken = Math.max(darken, 10);
        } else if (world.hasStorm()) {
            darken = Math.min(11, darken + 3);
        }
        return Math.max(0, Math.min(11, darken));
    }

    private static void clearMobCombatTarget(Mob mob) {
        if (mob == null) {
            return;
        }
        if (HAS_PAPER_PATHFINDER) {
            try {
                PaperPathfinder.clearCombatTarget(mob);
                return;
            } catch (Throwable ignored) {
            }
        }
        mob.setTarget(null);
    }

    /**
     * Directs {@code mob} to walk to {@code target}: uses {@link PaperPathfinder} on Paper/Purpur/Folia
     * and falls back to {@link #spigotFallbackMove} on pure Spigot.
     */
    public static boolean investigate(Mob mob, Location target, double speed) {
        return investigate(mob, target, speed, false, false);
    }

    public static boolean investigate(Mob mob, Location target, double speed, boolean bypassCooldown) {
        return investigate(mob, target, speed, bypassCooldown, false);
    }

    public static boolean investigate(Mob mob, Location target, double speed,
                                      boolean bypassCooldown, boolean forceRetarget) {
        if (mob == null || target == null || !mob.isValid() || mob.getTarget() != null) {
            return false;
        }
        if (HAS_PAPER_PATHFINDER) {
            try {
                if (PaperPathfinder.moveTo(activePlugin, mob, target, speed, bypassCooldown, forceRetarget)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        long now = System.currentTimeMillis();
        long cooldown = MobSenses.settings().alertCooldownMs();
        Long prev = LAST_ALERT_MS.get(mob.getUniqueId());
        if (!bypassCooldown && !forceRetarget && prev != null && (now - prev) < cooldown) {
            return false;
        }
        return spigotFallbackMove(mob, target, speed);
    }

    /**
     * Triggers Stage-1 suspicion pause and gaze toward {@code target}. Returns {@code false} if already
     * investigating or primed so the caller escalates to Stage-2 movement.
     */
    public static boolean makeSuspicious(Mob mob, Location target) {
        if (mob == null || target == null || !mob.isValid() || mob.getTarget() != null) {
            return false;
        }
        if (HAS_PAPER_PATHFINDER) {
            try {
                return PaperPathfinder.makeSuspicious(activePlugin, mob, target);
            } catch (Throwable ignored) {
            }
        }
        long now = System.currentTimeMillis();
        Long until = SPIGOT_SUSPICIOUS_UNTIL.get(mob.getUniqueId());
        if (until != null && now < until) {
            SPIGOT_SUSPICIOUS_UNTIL.remove(mob.getUniqueId());
            return false;
        }
        SPIGOT_SUSPICIOUS_UNTIL.put(mob.getUniqueId(), now + SUSPICION_WINDOW_MS);
        Location loc = mob.getLocation();
        double dx = target.getX() - loc.getX();
        double dz = target.getZ() - loc.getZ();
        if (dx * dx + dz * dz > 1e-4) {
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            mob.setRotation(yaw, 0.0f);
        }
        return true;
    }

    /** Records a nearby mob alertness peak for non-bypassed online players within {@code 28m}. */
    private static void notifyPlayersNearAlert(World world, double x, double y, double z,
                                               int alertLevel, long now) {
        if (world == null || alertLevel <= StealthHudState.ALERT_CALM) {
            return;
        }
        double maxDistSq = PLAYER_ALERT_NOTIFY_RADIUS * PLAYER_ALERT_NOTIFY_RADIUS;
        Scheduling sched = activeScheduling;
        Plugin plugin = activePlugin;
        for (Player player : world.getPlayers()) {
            Runnable check = () -> {
                if (!player.isOnline() || !player.isValid() || player.getWorld() != world || isBypassed(player)) {
                    return;
                }
                Location ploc = player.getLocation();
                double dx = ploc.getX() - x;
                double dy = ploc.getY() - y;
                double dz = ploc.getZ() - z;
                if (dx * dx + dy * dy + dz * dz <= maxDistSq) {
                    MobSenses.recordPlayerAlert(player.getUniqueId(), alertLevel, now);
                }
            };
            if (sched != null && plugin != null && !sched.owns(player)) {
                sched.onPlayer(plugin, player, check);
            } else {
                check.run();
            }
        }
    }

    /** Rallies nearby idle listener mobs in a golden-angle ring around the investigation target. */
    private static void rallyHordeAround(
            World world, Mob leader,
            double x, double y, double z, double speed,
            boolean enforceLeaderCooldown, long now,
            TacticalTargets.HazardWorld hazardWorld, boolean safeFilter) {
        double hordeRadius = MobSenses.settings().hordeAlertRange();
        int maxAllies = MobSenses.settings().hordeMaxAllies();
        if (!(hordeRadius > 0.0) || maxAllies <= 0 || leader == null || world == null) {
            return;
        }
        if (enforceLeaderCooldown) {
            Long prev = LAST_RALLY_MS.get(leader.getUniqueId());
            if (prev != null && (now - prev) < HORDE_RALLY_COOLDOWN_MS) {
                return;
            }
            LAST_RALLY_MS.put(leader.getUniqueId(), now);
        }
        Collection<Entity> allies = world.getNearbyEntities(leader.getLocation(), hordeRadius, 5.0, hordeRadius);
        int pulled = 0;
        for (Entity allyEnt : allies) {
            if (!(allyEnt instanceof Mob) || allyEnt == leader) {
                continue;
            }
            Mob ally = (Mob) allyEnt;
            if (!isListener(ally) || ally.getTarget() != null) {
                continue;
            }
            Location allyLoc = ally.getLocation();
            TacticalTargets.Point3 offset = TacticalTargets.fanOutOffset(pulled);
            TacticalTargets.Point3 safe = TacticalTargets.resolveSafeGoal(
                    hazardWorld, safeFilter,
                    x + offset.x(), y + offset.y(), z + offset.z(),
                    allyLoc.getX(), allyLoc.getY(), allyLoc.getZ());
            if (safe == null) {
                continue;
            }
            if (investigate(ally, new Location(world, safe.x(), safe.y(), safe.z()), speed, false)) {
                LAST_ALERT_MS.put(ally.getUniqueId(), now);
                MobSenses.alerted().record(now);
                if (++pulled >= maxAllies) {
                    break;
                }
            }
        }
    }

    /** Begins tracking a Q-dropped item until it lands on ground or water and emits a decoy noise. */
    public static void onPlayerDropDecoy(Player player, Item item) {
        if (!MobSenses.settings().decoyThrowEnabled() || player == null || item == null || player.isDead()) {
            return;
        }
        if (isBypassed(player) || (player.getGameMode() == GameMode.CREATIVE && !MobSenses.debug())) {
            return;
        }
        if (item.getPickupDelay() < 25) {
            return;
        }
        long now = System.currentTimeMillis();
        Long lastDropMs = LAST_DECOY_DROP_MS.get(player.getUniqueId());
        if (lastDropMs != null && (now - lastDropMs) < 200L) {
            return;
        }
        Location loc = item.getLocation();
        if (loc.getWorld() == null || MobSenses.settings().presetOf(loc.getWorld().getName()) == Preset.OFF) {
            return;
        }
        LAST_DECOY_DROP_MS.put(player.getUniqueId(), now);
        boolean fast = item.getVelocity().lengthSquared() >= 0.02;
        String thrownId = itemId(item.getItemStack());
        TRACKED_DECOYS.put(item.getUniqueId(), new DecoyTrack(
                item.getUniqueId(), player.getUniqueId(), thrownId, loc.clone(),
                now, fast));
    }

    @SuppressWarnings("deprecation")
    private static boolean isItemOnGround(Item item) {
        return item.isOnGround();
    }

    private static void pollDecoy(World world, DecoyTrack track, long now) {
        if (!TRACKED_DECOYS.containsKey(track.itemUuid)) {
            return;
        }
        if ((now - track.startedAtMs) > 6000L) {
            TRACKED_DECOYS.remove(track.itemUuid);
            return;
        }
        Item found = null;
        for (Entity entity : world.getNearbyEntities(track.lastLoc, 4.5, 4.5, 4.5)) {
            if (entity instanceof Item && entity.getUniqueId().equals(track.itemUuid)) {
                found = (Item) entity;
                break;
            }
        }
        if (found == null || !found.isValid()) {
            if ((now - track.startedAtMs) > 500L) {
                TRACKED_DECOYS.remove(track.itemUuid);
            }
            return;
        }
        Location loc = found.getLocation();
        track.lastLoc = loc.clone();
        int age = ++track.ageTicks;
        if (!track.velocityConfirmed) {
            if (found.getVelocity().lengthSquared() >= 0.02) {
                track.velocityConfirmed = true;
            } else if (age >= 2) {
                TRACKED_DECOYS.remove(track.itemUuid);
                return;
            }
        }
        int bx = loc.getBlockX();
        int by = loc.getBlockY();
        int bz = loc.getBlockZ();
        if (age >= 2 && (isItemOnGround(found) || BukkitAcoustics.isWaterAt(world, bx, by, bz))) {
            TRACKED_DECOYS.remove(track.itemUuid);
            Block hitBlock = world.getBlockAt(bx, by, bz);
            if (hitBlock.getType().isAir()) {
                Block below = world.getBlockAt(bx, by - 1, bz);
                if (!below.getType().isAir()) {
                    hitBlock = below;
                }
            }
            AcousticMaterial mat = BukkitAcoustics.material(hitBlock);
            double env = envMultiplier(world, null, NoiseModel.Kind.PROJECTILE);
            MobSenses.gameEvent(
                    "mobsenses:decoy_land",
                    world.getName(),
                    loc.getX(), loc.getY(), loc.getZ(),
                    NoiseModel.Posture.WALKING,
                    mat,
                    track.itemTypeId,
                    true,
                    env,
                    track.ownerId);
        }
    }

    /** Evaluates whether damage to {@code mob} came from an unseen sniper and schedules ambush reaction. */
    public static void onMobDamagedByPlayer(Mob mob, Player attacker, Entity damager) {
        if (mob == null || attacker == null || !isListener(mob)) {
            return;
        }
        if (isBypassed(attacker)) {
            return;
        }
        World world = mob.getWorld();
        if (world == null) {
            return;
        }
        String worldName = world.getName();
        Preset preset = MobSenses.settings().presetOf(worldName);
        if (preset == Preset.OFF) {
            return;
        }
        long now = System.currentTimeMillis();
        Scheduling sched = activeScheduling;
        boolean ownsAttacker = sched == null || sched.owns(attacker);
        double sightMult = MobSenses.settings().sight();
        double configuredSight = MobSenses.settings().sightRange();
        double baseSight = Math.max(4.0, configuredSight * sightMult);

        Location attackerEye = null;
        Location attackerFeet = null;
        double effectiveSight = baseSight;
        if (ownsAttacker && attacker.getWorld() == world) {
            if (attacker.getGameMode() == GameMode.SPECTATOR
                    || (attacker.getGameMode() == GameMode.CREATIVE && !MobSenses.debug())) {
                return;
            }
            attackerEye = attacker.getEyeLocation();
            attackerFeet = attacker.getLocation().clone();
            effectiveSight = sightRangeFor(world, attacker, preset, baseSight);
        } else {
            PlayerSightSnapshot snap = PLAYER_SIGHT_SNAPSHOTS.get(attacker.getUniqueId());
            if (snap != null && worldName.equals(snap.worldName) && (now - snap.atMs) < 10_000L) {
                attackerEye = new Location(world, snap.x, snap.eyeY, snap.z);
                attackerFeet = new Location(world, snap.x, snap.y, snap.z);
                effectiveSight = snap.effectiveSight;
            } else if (damager != null && damager.getWorld() == world) {
                attackerEye = damager.getLocation();
                attackerFeet = attackerEye.clone();
            }
        }
        if (attackerEye == null) {
            return;
        }
        if (attackerFeet == null) {
            attackerFeet = attackerEye.clone();
        }

        Location mobEye = mob.getEyeLocation();
        double distSq = mobEye.distanceSquared(attackerEye);
        if (distSq <= 9.0) {
            LAST_DIRECT_HURT_MS.put(mob.getUniqueId(), new DirectHurt(attacker.getUniqueId(), now));
            return;
        }
        double sightLimit = Math.max(3.0, effectiveSight);
        boolean blocked = BukkitAcoustics.traceSingleRay(
                world,
                mobEye.getX(), mobEye.getY(), mobEye.getZ(),
                attackerEye.getX(), attackerEye.getY(), attackerEye.getZ()) > 0.25;
        if (distSq <= sightLimit * sightLimit && !blocked) {
            LAST_DIRECT_HURT_MS.put(mob.getUniqueId(), new DirectHurt(attacker.getUniqueId(), now));
            return;
        }
        if (!MobSenses.settings().ambushReactionEnabled()) {
            return;
        }

        UNSEEN_TARGET_SUPPRESS.put(mob.getUniqueId(), new TargetSuppress(attacker.getUniqueId(), now + 900L));
        PENDING_AMBUSHES.put(mob.getUniqueId(), new AmbushTrack(
                mob.getUniqueId(), attacker.getUniqueId(), worldName,
                mob.getLocation().clone(), attackerFeet.clone(), now, tickCounter));
    }

    /** Suppresses vanilla retaliation against unseen ambushers and darkness X-ray target locks onto hidden players. */
    public static boolean shouldSuppressTarget(Mob mob, LivingEntity target, EntityTargetEvent.TargetReason reason) {
        if (mob == null || target == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (reason == EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY
                || reason == EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY
                || reason == EntityTargetEvent.TargetReason.CLOSEST_PLAYER
                || reason == EntityTargetEvent.TargetReason.RANDOM_TARGET
                || reason == EntityTargetEvent.TargetReason.CUSTOM) {
            TargetSuppress suppress = UNSEEN_TARGET_SUPPRESS.get(mob.getUniqueId());
            if (suppress != null) {
                if (now >= suppress.expiresAtMs) {
                    UNSEEN_TARGET_SUPPRESS.remove(mob.getUniqueId(), suppress);
                } else if (suppress.attackerId.equals(target.getUniqueId())) {
                    return true;
                }
            }
        }
        if ((reason == EntityTargetEvent.TargetReason.CLOSEST_PLAYER
                || reason == EntityTargetEvent.TargetReason.RANDOM_TARGET)
                && target instanceof Player
                && MobSenses.settings().darknessStealthEnabled()
                && isListener(mob)) {
            Player player = (Player) target;
            if (isBypassed(player) || (player.getGameMode() == GameMode.CREATIVE && !MobSenses.debug())) {
                return false;
            }
            World world = mob.getWorld();
            if (world == null) {
                return false;
            }
            String worldName = world.getName();
            Preset preset = MobSenses.settings().presetOf(worldName);
            if (preset == Preset.OFF) {
                return false;
            }
            double sightMult = MobSenses.settings().sight();
            double configuredSight = MobSenses.settings().sightRange();
            if (!(sightMult > 0.0) || !(configuredSight > 0.0)) {
                return false;
            }
            double baseSight = configuredSight * sightMult;
            Scheduling sched = activeScheduling;
            boolean ownsPlayer = sched == null || sched.owns(player);
            double px;
            double py;
            double pz;
            double effectiveSight;
            if (ownsPlayer && player.getWorld() == world) {
                Location eye = player.getEyeLocation();
                px = eye.getX();
                py = eye.getY();
                pz = eye.getZ();
                effectiveSight = sightRangeFor(world, player, preset, baseSight);
            } else {
                PlayerSightSnapshot snap = PLAYER_SIGHT_SNAPSHOTS.get(player.getUniqueId());
                if (snap == null || !worldName.equals(snap.worldName) || (now - snap.atMs) >= 10_000L) {
                    return false;
                }
                px = snap.x;
                py = snap.eyeY;
                pz = snap.z;
                effectiveSight = snap.effectiveSight;
            }
            if (effectiveSight >= baseSight * 0.78) {
                return false;
            }
            Location mobEye = mob.getEyeLocation();
            double dx = mobEye.getX() - px;
            double dy = mobEye.getY() - py;
            double dz = mobEye.getZ() - pz;
            double loseDist = Math.max(4.0, effectiveSight);
            if ((dx * dx + dy * dy + dz * dz) <= loseDist * loseDist) {
                return false;
            }
            if (wasRecentlyHurtDirectly(mob.getUniqueId(), player.getUniqueId(), now)) {
                return false;
            }
            DirectHurt legit = LEGITIMATE_PURSUIT.get(mob.getUniqueId());
            if (legit != null && legit.attackerId.equals(player.getUniqueId()) && (now - legit.atMs) <= 8_000L) {
                return false;
            }
            return true;
        }
        return false;
    }

    private static void processAmbush(World world, AmbushTrack track, long now) {
        if (!PENDING_AMBUSHES.remove(track.mobUuid, track)) {
            return;
        }
        Mob found = null;
        for (Entity entity : world.getNearbyEntities(track.mobLoc, 4.5, 4.5, 4.5)) {
            if (entity instanceof Mob && entity.getUniqueId().equals(track.mobUuid)) {
                found = (Mob) entity;
                break;
            }
        }
        if (found == null || !found.isValid()) {
            return;
        }
        if (!MobSenses.settings().ambushReactionEnabled()) {
            return;
        }
        UNSEEN_TARGET_SUPPRESS.put(found.getUniqueId(), new TargetSuppress(track.attackerId, now + 900L));
        clearMobCombatTarget(found);
        LEGITIMATE_PURSUIT.remove(found.getUniqueId());
        Location mloc = found.getLocation();
        double normalSpeed = MobSenses.settings().investigateSpeed();
        double urgentSpeed = MobSenses.settings().urgentInvestigateSpeed();
        double fleeRatio = MobSenses.settings().ambushFleeHealthThreshold() / 100.0;
        double seedUnit = ((Math.abs(found.getEntityId() * 37) + (int) (track.hurtAtMs & 0xFF)) & 0xFF) / 255.0;
        TacticalTargets.AmbushPlan plan = TacticalTargets.planAmbushReaction(
                mloc.getX(), mloc.getY(), mloc.getZ(),
                track.attackerLoc.getX(), track.attackerLoc.getY(), track.attackerLoc.getZ(),
                found.getHealth(), maxHealth(found),
                normalSpeed, urgentSpeed, seedUnit, fleeRatio);
        TacticalTargets.HazardWorld hazardWorld = BukkitAcoustics.hazardWorld(world);
        boolean safeFilter = MobSenses.settings().safeGoalFilter();
        TacticalTargets.Point3 goal = TacticalTargets.resolveSafeGoal(
                hazardWorld, safeFilter,
                plan.targetX(), plan.targetY(), plan.targetZ(),
                mloc.getX(), mloc.getY(), mloc.getZ());
        if (goal == null) {
            return;
        }
        Location goalLoc = new Location(world, goal.x(), goal.y(), goal.z());
        if (investigate(found, goalLoc, plan.speed(), true)) {
            LAST_ALERT_MS.put(found.getUniqueId(), now);
            MobSenses.alerted().record(now);
            MobSenses.recordPlayerAlert(track.attackerId, StealthHudState.ALERT_SEARCHING, now);
            if (!plan.flee()) {
                rallyHordeAround(world, found, goal.x(), goal.y(), goal.z(), plan.speed(),
                        true, now, hazardWorld, safeFilter);
            }
        }
    }

    /**
     * Pure-Spigot fallback when Paper's {@code Pathfinder} API is unavailable: rotates the mob toward
     * the sound source and nudges its horizontal velocity along the ground.
     */
    public static boolean spigotFallbackMove(Mob mob, Location target, double speed) {
        Location loc = mob.getLocation();
        double dx = target.getX() - loc.getX();
        double dz = target.getZ() - loc.getZ();
        double horiz = Math.hypot(dx, dz);
        if (horiz < 1.0) {
            return false;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        loc.setYaw(yaw);
        mob.setRotation(yaw, loc.getPitch());
        double step = 0.22 * Math.max(0.5, Math.min(1.5, speed));
        Vector vel = mob.getVelocity();
        vel.setX((dx / horiz) * step);
        vel.setZ((dz / horiz) * step);
        mob.setVelocity(vel);
        return true;
    }

    public static boolean isListener(Mob mob) {
        if (mob == null || !mob.isValid()) {
            return false;
        }
        String type = mob.getType().name().toLowerCase(Locale.ROOT);
        if (MobSenses.settings().isIgnoredMob(type)) {
            return false;
        }
        if (MobSenses.settings().isExtraListener(type)) {
            return true;
        }
        if (MobSenses.settings().ignoreBosses() && isBossMob(mob, type)) {
            return false;
        }
        if (type.contains("warden")) {
            return false;
        }
        return LISTENER_NAMES.contains(type) || mob instanceof Monster;
    }

    private static boolean isScentTracker(Mob mob) {
        if (mob == null) {
            return false;
        }
        String type = mob.getType().name().toLowerCase(Locale.ROOT);
        if (MobSenses.settings().isIgnoredMob(type)) {
            return false;
        }
        if (MobSenses.settings().ignoreBosses() && !MobSenses.settings().isExtraListener(type)
                && isBossMob(mob, type)) {
            return false;
        }
        return ScentModel.isScentTracker(type);
    }

    private static boolean isBossMob(Mob mob, String type) {
        return mob instanceof Boss || ServerSettings.isKnownBossId(type);
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        return BukkitAcoustics.materialId(stack.getType());
    }

    @SuppressWarnings("deprecation")
    private static double maxHealth(LivingEntity entity) {
        return entity.getMaxHealth();
    }

    private static Particle findParticle(String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private static void sendBloodCrumb(World world, double x, double y, double z) {
        try {
            Particle dust = findParticle("DUST", "REDSTONE");
            if (dust != null) {
                Particle.DustOptions crimson = new Particle.DustOptions(org.bukkit.Color.fromRGB(176, 8, 8), 0.85f);
                world.spawnParticle(dust, x, y + 0.06, z, 3, 0.14, 0.02, 0.14, 0.0, crimson);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void runForAdminViewers(World world, Location origin, java.util.function.Consumer<Player> action) {
        Plugin plugin = activePlugin;
        Scheduling sched = activeScheduling;
        double ox = origin.getX();
        double oy = origin.getY();
        double oz = origin.getZ();
        for (Player viewer : world.getPlayers()) {
            Runnable task = () -> {
                if (!viewer.isOnline() || !viewer.isValid()) {
                    return;
                }
                if (!viewer.isOp() && !viewer.hasPermission(AdminCommands.PERM_ADMIN)) {
                    return;
                }
                Location vloc = viewer.getLocation();
                if (vloc.getWorld() != world) {
                    return;
                }
                double dx = vloc.getX() - ox;
                double dy = vloc.getY() - oy;
                double dz = vloc.getZ() - oz;
                if (dx * dx + dy * dy + dz * dz > 96.0 * 96.0) {
                    return;
                }
                action.accept(viewer);
            };
            if (plugin != null && sched != null && !sched.owns(viewer)) {
                sched.onPlayer(plugin, viewer, task);
            } else {
                task.run();
            }
        }
    }

    private static void sendScentDebugTrace(World world, Location from, Location to) {
        Particle dust = findParticle("DUST", "REDSTONE");
        Particle angry = findParticle("ANGRY_VILLAGER", "VILLAGER_ANGRY");
        Particle.DustOptions crimson = new Particle.DustOptions(org.bukkit.Color.fromRGB(184, 8, 8), 1.35f);
        double fx = from.getX(), fy = from.getY(), fz = from.getZ();
        double tx = to.getX(), ty = to.getY(), tz = to.getZ();
        runForAdminViewers(world, from, viewer -> {
            try {
                if (angry != null) {
                    viewer.spawnParticle(angry, tx, ty + 0.45, tz, 1, 0.1, 0.1, 0.1, 0.0);
                }
                if (dust != null) {
                    int steps = 7;
                    for (int i = 1; i < steps; i++) {
                        double t = (double) i / steps;
                        double px = fx + (tx - fx) * t;
                        double py = fy + 0.18 + (ty - 0.3 - fy) * t;
                        double pz = fz + (tz - fz) * t;
                        viewer.spawnParticle(dust, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0, crimson);
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static void sendDebugTrace(World world, Location from, Location to) {
        Particle note = findParticle("NOTE");
        Particle angry = findParticle("ANGRY_VILLAGER", "VILLAGER_ANGRY");
        Particle rod = findParticle("END_ROD");
        double fx = from.getX(), fy = from.getY(), fz = from.getZ();
        double tx = to.getX(), ty = to.getY(), tz = to.getZ();
        runForAdminViewers(world, from, viewer -> {
            try {
                if (note != null) {
                    viewer.spawnParticle(note, fx, fy + 0.35, fz, 1, 0.0, 0.0, 0.0, 0.0);
                }
                if (angry != null) {
                    viewer.spawnParticle(angry, tx, ty + 0.45, tz, 1, 0.1, 0.1, 0.1, 0.0);
                }
                if (rod != null) {
                    int steps = 5;
                    for (int i = 1; i < steps; i++) {
                        double t = (double) i / steps;
                        double px = fx + (tx - fx) * t;
                        double py = fy + (ty - fy) * t;
                        double pz = fz + (tz - fz) * t;
                        viewer.spawnParticle(rod, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
