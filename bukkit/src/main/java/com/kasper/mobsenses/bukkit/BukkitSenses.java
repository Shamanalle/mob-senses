package com.kasper.mobsenses.bukkit;

import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.ModLog;
import com.kasper.mobsenses.NoiseCoalescer;
import com.kasper.mobsenses.NoiseModel;
import com.kasper.mobsenses.Preset;
import com.kasper.mobsenses.ScentModel;
import com.kasper.mobsenses.SightModel;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
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
import java.util.Set;

/**
 * Full server-side parity for the Bukkit / Spigot / Paper / Purpur / Folia plugin:
 * <ul>
 *   <li>Coalesced hearing and Simple Voice Chat events attenuated through walls via {@link BukkitAcoustics}.</li>
 *   <li>Mob investigation pathing via {@link PaperPathfinder} on Paper/Purpur/Folia, with a pure-Spigot
 *       fallback ({@link #spigotFallbackMove}) when Paper's {@code Pathfinder} API is absent.</li>
 *   <li>Light & posture sight adjustments and wounded blood-scent tracking for zombies and spiders.</li>
 *   <li>Admin debug particles when {@code /mobsenses debug on} is active.</li>
 * </ul>
 */
public final class BukkitSenses {

    private static final boolean HAS_PAPER_PATHFINDER = hasClass("com.destroystokyo.paper.entity.Pathfinder");
    private static final int SENSE_TICK_INTERVAL = 10;

    private static final Set<String> LISTENER_NAMES = new HashSet<>(Arrays.asList(
            "zombie", "zombie_villager", "husk", "drowned", "zombified_piglin", "pig_zombie",
            "skeleton", "stray", "bogged", "wither_skeleton", "creeper", "spider", "cave_spider",
            "pillager", "vindicator", "evoker", "illusioner", "ravager", "witch",
            "piglin", "piglin_brute", "enderman", "blaze", "breeze"
    ));

    private static int tickCounter;
    private static volatile Plugin activePlugin;

    private BukkitSenses() {
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

    public static void tick(Plugin plugin, Scheduling scheduling) {
        try {
            activePlugin = plugin;
            long now = System.currentTimeMillis();
            boolean senseTick = (++tickCounter % SENSE_TICK_INTERVAL) == 0;

            for (World world : Bukkit.getWorlds()) {
                String worldName = world.getName();
                Preset preset = MobSenses.settings().presetOf(worldName);
                if (preset == Preset.OFF) {
                    MobSenses.coalescer().drainWorld(worldName);
                    continue;
                }

                // 1. Drain pending Simple Voice Chat frames for players in this world
                if (MobSenses.settings().voice()) {
                    for (Player player : world.getPlayers()) {
                        if (player.getGameMode() == GameMode.SPECTATOR) {
                            continue;
                        }
                        double voiceRange = MobSenses.voice().pollPendingRange(player.getUniqueId(), now);
                        if (voiceRange > 0.0) {
                            Location loc = player.getEyeLocation();
                            MobSenses.voiceEvent(worldName, loc.getX(), loc.getY(), loc.getZ(), voiceRange);
                        }
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
                        if (player.getGameMode() == GameMode.SPECTATOR
                                || (player.getGameMode() == GameMode.CREATIVE && !MobSenses.debug())) {
                            continue;
                        }
                        Location loc = player.getLocation();
                        scheduling.atLocation(plugin, loc, () -> tickPlayerSightAndScent(world, worldName, player, preset, now));
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
        for (Entity entity : nearby) {
            if (entity instanceof Mob) {
                Mob mob = (Mob) entity;
                if (isListener(mob) && mob.getTarget() == null) {
                    candidates.add(mob);
                }
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort((a, b) -> Double.compare(
                a.getLocation().distanceSquared(center),
                b.getLocation().distanceSquared(center)));

        int alerted = 0;
        double speed = noise.kind() == NoiseModel.Kind.EXPLOSION
                || noise.kind() == NoiseModel.Kind.COMBAT
                || noise.kind() == NoiseModel.Kind.BELL
                || noise.kind() == NoiseModel.Kind.VOICE
                ? 1.15 : 1.0;

        for (Mob mob : candidates) {
            if (alerted >= NoiseCoalescer.DEFAULT_MAX_TRACES_PER_EVENT) {
                break;
            }
            Location eye = mob.getEyeLocation();
            double dist = eye.distance(center);
            if (dist > r) {
                continue;
            }
            double thickness = BukkitAcoustics.traceBundle(
                    world, noise.x(), noise.y(), noise.z(), eye.getX(), eye.getY(), eye.getZ());
            double audible = NoiseModel.audibleRange(r, thickness);
            if (dist <= audible && investigate(mob, center, speed)) {
                alerted++;
                MobSenses.alerted().record(now);
                if (MobSenses.debug()) {
                    sendDebugTrace(world, center, eye);
                }
            }
        }
    }

    private static void tickPlayerSightAndScent(
            World world, String worldName, Player player, Preset preset, long now) {
        if (!player.isOnline() || !player.isValid()) {
            return;
        }
        Location ploc = player.getLocation();
        double px = ploc.getX();
        double py = ploc.getY();
        double pz = ploc.getZ();
        double maxHealth = maxHealth(player);

        // Scent trail for badly wounded players (<= 50% health)
        double scentRange = MobSenses.scent().recordPlayer(
                worldName, player.getUniqueId(), px, py, pz,
                player.getHealth(), maxHealth, preset, now);
        if (scentRange > 0.0) {
            Collection<Entity> nearby = world.getNearbyEntities(ploc, scentRange, 6.0, scentRange);
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
                    if (investigate(mob, target, 1.10)) {
                        MobSenses.alerted().record(now);
                        if (MobSenses.debug()) {
                            sendDebugTrace(world, target, mob.getEyeLocation());
                        }
                        if (++count >= 6) {
                            break;
                        }
                    }
                }
            }
        }

        // Sight & Light
        double sightMult = MobSenses.settings().sight();
        if (!(sightMult > 0.0)) {
            return;
        }
        Block block = ploc.getBlock();
        int blockLight = block.getLightFromBlocks();
        int skyLight = block.getLightFromSky();
        EntityEquipment eq = player.getEquipment();
        int heldLight = 0;
        if (eq != null) {
            heldLight = Math.max(
                    SightModel.heldItemLight(itemId(eq.getItemInMainHand())),
                    SightModel.heldItemLight(itemId(eq.getItemInOffHand())));
        }
        double baseSight = SightModel.DEFAULT_SIGHT_RANGE * sightMult;
        double effectiveSight = SightModel.effectiveSightRange(
                baseSight, blockLight, skyLight, heldLight,
                player.isSneaking(), player.isSprinting(), preset);

        if (effectiveSight > SightModel.DEFAULT_SIGHT_RANGE) {
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
                        && investigate(mob, ploc, 1.10)) {
                    MobSenses.alerted().record(now);
                    if (MobSenses.debug()) {
                        sendDebugTrace(world, player.getEyeLocation(), mob.getEyeLocation());
                    }
                    if (++spotted >= 6) {
                        break;
                    }
                }
            }
        } else if (effectiveSight < SightModel.DEFAULT_SIGHT_RANGE * 0.75) {
            double checkRadius = SightModel.DEFAULT_SIGHT_RANGE + 6.0;
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
        if (type.contains("warden")) {
            return false;
        }
        return LISTENER_NAMES.contains(type) || mob instanceof Monster;
    }

    private static boolean isScentTracker(Mob mob) {
        return ScentModel.isScentTracker(mob.getType().name().toLowerCase(Locale.ROOT));
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "";
        }
        return BukkitAcoustics.materialId(stack.getType());
    }

    @SuppressWarnings("deprecation")
    private static double maxHealth(Player player) {
        try {
            AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attr != null) {
                return attr.getValue();
            }
        } catch (Throwable ignored) {
        }
        return player.getMaxHealth();
    }

    private static void sendDebugTrace(World world, Location from, Location to) {
        for (Player viewer : world.getPlayers()) {
            if (!viewer.isOp() && !viewer.hasPermission("mobsenses.admin")) {
                continue;
            }
            viewer.spawnParticle(Particle.NOTE, from.getX(), from.getY() + 0.35, from.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            viewer.spawnParticle(Particle.VILLAGER_ANGRY, to.getX(), to.getY() + 0.45, to.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
            int steps = 5;
            for (int i = 1; i < steps; i++) {
                double t = (double) i / steps;
                double px = from.getX() + (to.getX() - from.getX()) * t;
                double py = from.getY() + (to.getY() - from.getY()) * t;
                double pz = from.getZ() + (to.getZ() - from.getZ()) * t;
                viewer.spawnParticle(Particle.END_ROD, px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
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
