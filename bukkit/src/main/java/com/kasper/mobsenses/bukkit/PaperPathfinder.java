package com.kasper.mobsenses.bukkit;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.kasper.mobsenses.MobSenses;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.Plugin;

import java.util.EnumSet;

/**
 * Paper-specific mob investigation goal and pathfinder using {@link MobGoals} and {@link Pathfinder}.
 * <p>
 * Loaded only when {@code com.destroystokyo.paper.entity.Pathfinder} is present (Paper, Purpur, Folia);
 * pure Spigot servers fall back to {@link BukkitSenses#spigotFallbackMove}.
 */
public final class PaperPathfinder {

    public static final int PRIORITY = 6;
    private static final int LOOK_AROUND_TICKS = 50;
    private static final double ARRIVAL_DIST_SQ = 3.25;
    private static final double WAYPOINT_STEP_DIST = 14.0;

    private PaperPathfinder() {
    }

    public static boolean moveTo(Plugin plugin, Mob mob, Location target, double speed) {
        if (mob == null || target == null || !mob.isValid() || mob.getTarget() != null) {
            return false;
        }
        Pathfinder pathfinder = mob.getPathfinder();
        if (pathfinder == null) {
            return false;
        }
        if (plugin != null) {
            try {
                MobGoals goals = Bukkit.getMobGoals();
                if (goals != null) {
                    GoalKey<Mob> key = GoalKey.of(Mob.class, new NamespacedKey(plugin, "investigate"));
                    Goal<Mob> existing = goals.getGoal(mob, key);
                    if (existing instanceof InvestigateMobGoal) {
                        ((InvestigateMobGoal) existing).trigger(target, speed);
                        return true;
                    }
                    if (existing != null) {
                        goals.removeGoal(mob, existing);
                    }
                    InvestigateMobGoal created = new InvestigateMobGoal(key, mob, target, speed);
                    goals.addGoal(mob, PRIORITY, created);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return stepMoveTo(mob, pathfinder, target, speed);
    }

    private static boolean stepMoveTo(Mob mob, Pathfinder pf, Location target, double speed) {
        Location loc = mob.getLocation();
        if (loc.getWorld() != target.getWorld()) {
            return false;
        }
        double dx = target.getX() - loc.getX();
        double dy = target.getY() - loc.getY();
        double dz = target.getZ() - loc.getZ();
        double horiz = Math.hypot(dx, dz);
        if (horiz <= WAYPOINT_STEP_DIST && pf.moveTo(target, speed)) {
            return true;
        }
        if (horiz > 1.0e-3) {
            double step = Math.min(WAYPOINT_STEP_DIST, horiz);
            Location wp = new Location(
                    loc.getWorld(),
                    loc.getX() + (dx / horiz) * step,
                    loc.getY() + (dy / Math.max(1.0, horiz)) * Math.min(4.0, step),
                    loc.getZ() + (dz / horiz) * step);
            if (pf.moveTo(wp, speed)) {
                return true;
            }
        }
        return pf.moveTo(target, speed);
    }

    private static final class InvestigateMobGoal implements Goal<Mob> {

        private final GoalKey<Mob> key;
        private final Mob mob;
        private Location target;
        private double speed;
        private int ticksRemaining;
        private int lookTicks;
        private int repathCooldown;
        private long lastAlertMs;

        InvestigateMobGoal(GoalKey<Mob> key, Mob mob, Location target, double speed) {
            this.key = key;
            this.mob = mob;
            trigger(target, speed);
        }

        void trigger(Location nextTarget, double nextSpeed) {
            long now = System.currentTimeMillis();
            long cooldown = MobSenses.settings().alertCooldownMs();
            Location mobLoc = mob.getLocation();
            if (ticksRemaining > 0 && this.target != null && this.target.getWorld() == nextTarget.getWorld()) {
                double sameSpotSq = this.target.distanceSquared(nextTarget);
                if ((now - lastAlertMs) < cooldown && sameSpotSq < 4.0 && nextSpeed <= this.speed) {
                    return;
                }
                if (lookTicks == 0 && mobLoc.getWorld() == nextTarget.getWorld() && nextSpeed <= this.speed) {
                    double curDistSq = mobLoc.distanceSquared(this.target);
                    double nextDistSq = mobLoc.distanceSquared(nextTarget);
                    if (curDistSq < nextDistSq * 0.72) {
                        return;
                    }
                }
            }
            this.lastAlertMs = now;
            this.target = nextTarget.clone();
            this.speed = nextSpeed;
            int configuredTicks = Math.max(20, (int) Math.round(MobSenses.settings().investigationSeconds() * 20.0));
            double dist = mobLoc.getWorld() == nextTarget.getWorld()
                    ? Math.hypot(nextTarget.getX() - mobLoc.getX(), nextTarget.getZ() - mobLoc.getZ())
                    : 0.0;
            this.ticksRemaining = Math.max(configuredTicks, (int) Math.min(1200, dist * 7.0));
            this.lookTicks = 0;
            this.repathCooldown = 0;
            if (mob.getTarget() == null) {
                Pathfinder pf = mob.getPathfinder();
                if (pf != null) {
                    stepMoveTo(mob, pf, this.target, nextSpeed);
                }
            }
        }

        @Override
        public boolean shouldActivate() {
            return ticksRemaining > 0 && target != null && mob.isValid() && mob.getTarget() == null && !mob.isInsideVehicle();
        }

        @Override
        public boolean shouldStayActive() {
            return shouldActivate();
        }

        @Override
        public void start() {
            repathCooldown = 0;
            Pathfinder pf = mob.getPathfinder();
            if (pf != null && target != null) {
                stepMoveTo(mob, pf, target, speed);
            }
        }

        @Override
        public void stop() {
            ticksRemaining = 0;
            lookTicks = 0;
            if (mob.getTarget() == null) {
                Pathfinder pf = mob.getPathfinder();
                if (pf != null) {
                    pf.stopPathfinding();
                }
            }
        }

        @Override
        public void tick() {
            ticksRemaining--;
            if (target == null || !mob.isValid()) {
                ticksRemaining = 0;
                return;
            }
            Location loc = mob.getLocation();
            if (loc.getWorld() != target.getWorld()) {
                ticksRemaining = 0;
                return;
            }
            double distSq = loc.distanceSquared(target);
            Pathfinder pf = mob.getPathfinder();
            if (distSq <= ARRIVAL_DIST_SQ || lookTicks > 0) {
                if (pf != null) {
                    pf.stopPathfinding();
                }
                lookTicks++;
                float yaw = (float) ((lookTicks * 10.0 + mob.getEntityId() * 37.0) % 360.0);
                mob.setRotation(yaw, 0.0f);
                if (lookTicks >= LOOK_AROUND_TICKS) {
                    ticksRemaining = 0;
                }
                return;
            }
            if (--repathCooldown <= 0 || (pf != null && !pf.hasPath())) {
                repathCooldown = 15;
                if (pf != null) {
                    stepMoveTo(mob, pf, target, speed);
                }
            }
        }

        @Override
        public GoalKey<Mob> getKey() {
            return key;
        }

        @Override
        public EnumSet<GoalType> getTypes() {
            return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
        }
    }
}
