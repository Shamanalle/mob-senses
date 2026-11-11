package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.AdminCommands;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.NoiseCoalescer;
import com.kasper.mobsenses.NoiseModel;
import com.kasper.mobsenses.Preset;
import com.kasper.mobsenses.ScentModel;
import com.kasper.mobsenses.SightModel;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
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

    private static final Set<String> LISTENER_NAMES = new HashSet<>(Arrays.asList(
            "zombie", "zombie_villager", "husk", "drowned", "zombified_piglin", "pig_zombie",
            "skeleton", "stray", "bogged", "wither_skeleton", "creeper", "spider", "cave_spider",
            "pillager", "vindicator", "evoker", "illusioner", "ravager", "witch",
            "piglin", "piglin_brute", "hoglin", "zoglin", "enderman", "blaze", "breeze",
            "slime", "magma_cube"
    ));

    private static final Map<UUID, Long> LAST_ALERT_MS = new ConcurrentHashMap<>();
    private static int tickCounter;
    private static volatile Plugin activePlugin;
    private static volatile Scheduling activeScheduling;

    private BukkitSenses() {
    }

    public static void clear() {
        LAST_ALERT_MS.clear();
    }

    public static void forgetPlayer(UUID playerId) {
        if (playerId != null) {
            MobSenses.voice().removePlayer(playerId);
        }
    }

    /**
     * Returns {@code true} when {@code player} is in Spectator mode or holds {@code mobsenses.bypass}.
     */
    public static boolean isBypassed(Player player) {
        return player != null && (player.getGameMode() == GameMode.SPECTATOR || player.hasPermission(PERM_BYPASS));
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

            if ((currentTick % 400) == 0 && !LAST_ALERT_MS.isEmpty()) {
                long cutoff = now - 60_000L;
                LAST_ALERT_MS.entrySet().removeIf(e -> e.getValue() < cutoff);
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
        double speed = noise.kind() == NoiseModel.Kind.EXPLOSION
                || noise.kind() == NoiseModel.Kind.GUNSHOT
                || noise.kind() == NoiseModel.Kind.COMBAT
                || noise.kind() == NoiseModel.Kind.BELL
                || noise.kind() == NoiseModel.Kind.VOICE
                ? MobSenses.settings().urgentInvestigateSpeed()
                : MobSenses.settings().investigateSpeed();

        int maxAlerts = MobSenses.settings().maxAlertedMobs();
        double hordeRadius = MobSenses.settings().hordeAlertRange();
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
            if (dist <= audible && investigate(mob, center, speed)) {
                alerted++;
                LAST_ALERT_MS.put(mob.getUniqueId(), now);
                MobSenses.alerted().record(now);
                if (MobSenses.debug()) {
                    sendDebugTrace(world, center, eye);
                }
                int maxAllies = MobSenses.settings().hordeMaxAllies();
                if (hordeRadius > 0.0 && maxAllies > 0) {
                    Collection<Entity> allies = world.getNearbyEntities(mob.getLocation(), hordeRadius, 5.0, hordeRadius);
                    int pulled = 0;
                    for (Entity allyEnt : allies) {
                        if (allyEnt instanceof Mob && allyEnt != mob) {
                            Mob ally = (Mob) allyEnt;
                            if (isListener(ally) && ally.getTarget() == null && investigate(ally, center, speed)) {
                                LAST_ALERT_MS.put(ally.getUniqueId(), now);
                                MobSenses.alerted().record(now);
                                if (++pulled >= maxAllies) {
                                    break;
                                }
                            }
                        }
                    }
                }
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

        // Scent trail for badly wounded players
        double scentRange = MobSenses.scent().recordPlayer(
                worldName, player.getUniqueId(), px, py, pz,
                player.getHealth(), maxHealth, preset, now);
        List<ScentModel.Crumb> playerCrumbs = MobSenses.scent().activePlayerCrumbs(worldName, player.getUniqueId(), now);
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
                        worldName, mloc.getX(), mloc.getY(), mloc.getZ(), now);
                if (crumb != null) {
                    Location target = new Location(world, crumb.x(), crumb.y(), crumb.z());
                    if (investigate(mob, target, investigateSpeed)) {
                        MobSenses.alerted().record(now);
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

        // Sight & Light (accounting for time-of-day & storm sky darkening on Bukkit)
        double sightMult = MobSenses.settings().sight();
        double configuredSightRange = MobSenses.settings().sightRange();
        if (!(sightMult > 0.0) || !(configuredSightRange > 0.0)) {
            return;
        }
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
        double baseSight = configuredSightRange * sightMult;
        double effectiveSight = SightModel.effectiveSightRange(
                baseSight, blockLight, skyLight, heldLight,
                player.isSneaking(), player.isSprinting(), preset);
        if (player.isInvisible() && heldLight == 0) {
            effectiveSight *= 0.15;
        }

        if (effectiveSight > configuredSightRange) {
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
                        && mob.hasLineOfSight(player)
                        && investigate(mob, ploc, investigateSpeed)) {
                    MobSenses.alerted().record(now);
                    if (MobSenses.debug()) {
                        sendDebugTrace(world, player.getEyeLocation(), mob.getEyeLocation());
                    }
                    if (++spotted >= 6) {
                        break;
                    }
                }
            }
        } else if (MobSenses.settings().darknessStealthEnabled() && effectiveSight < baseSight * 0.78) {
            double checkRadius = Math.max(configuredSightRange, baseSight) + 6.0;
            double loseDistSq = Math.max(4.0, effectiveSight) * Math.max(4.0, effectiveSight);
            Collection<Entity> nearby = world.getNearbyEntities(ploc, checkRadius, 8.0, checkRadius);
            for (Entity entity : nearby) {
                if (entity instanceof Mob) {
                    Mob mob = (Mob) entity;
                    LivingEntity target = mob.getTarget();
                    if (target != null && target.getUniqueId().equals(player.getUniqueId())
                            && mob.getLocation().distanceSquared(ploc) > loseDistSq) {
                        mob.setTarget(null);
                    }
                }
            }
        }
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

    /**
     * Directs {@code mob} to walk to {@code target}: uses {@link PaperPathfinder} on Paper/Purpur/Folia
     * and falls back to {@link #spigotFallbackMove} on pure Spigot.
     */
    public static boolean investigate(Mob mob, Location target, double speed) {
        if (mob == null || target == null || !mob.isValid() || mob.getTarget() != null) {
            return false;
        }
        if (HAS_PAPER_PATHFINDER) {
            try {
                if (PaperPathfinder.moveTo(activePlugin, mob, target, speed)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return spigotFallbackMove(mob, target, speed);
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
        if (type.contains("warden")) {
            return false;
        }
        return LISTENER_NAMES.contains(type) || mob instanceof Monster;
    }

    private static boolean isScentTracker(Mob mob) {
        String type = mob.getType().name().toLowerCase(Locale.ROOT);
        if (MobSenses.settings().isIgnoredMob(type)) {
            return false;
        }
        return ScentModel.isScentTracker(type);
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        return BukkitAcoustics.materialId(stack.getType());
    }

    @SuppressWarnings("deprecation")
    private static double maxHealth(Player player) {
        return player.getMaxHealth();
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
        for (Player viewer : world.getPlayers()) {
            if (!viewer.isOp() && !viewer.hasPermission(AdminCommands.PERM_ADMIN)) {
                continue;
            }
            Location vloc = viewer.getLocation();
            if (vloc.getWorld() != world || vloc.distanceSquared(origin) > 96.0 * 96.0) {
                continue;
            }
            if (plugin != null && sched != null) {
                sched.onPlayer(plugin, viewer, () -> action.accept(viewer));
            } else {
                action.accept(viewer);
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
