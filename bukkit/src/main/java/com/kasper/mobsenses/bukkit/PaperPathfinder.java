package com.kasper.mobsenses.bukkit;

import com.destroystokyo.paper.entity.Pathfinder;
import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;
import com.kasper.mobsenses.MobSenses;
import com.kasper.mobsenses.TacticalTargets;
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
    private static final int SUSPICION_PAUSE_TICKS = 36;
    private static final long SUSPICION_WINDOW_MS = 4500L;
    private static final double ARRIVAL_DIST_SQ = 3.25;
    private static final double WAYPOINT_STEP_DIST = 14.0;
    private static final java.lang.reflect.Method PLAY_AMBIENT_METHOD = findPlayAmbientMethod();

    private PaperPathfinder() {
    }

    /** Clears the mob's active combat target and stops running Paper {@link GoalType#TARGET} goals. */
    public static void clearCombatTarget(Mob mob) {
        if (mob == null) {
            return;
        }
        mob.setTarget(null);
        try {
            MobGoals goals = Bukkit.getMobGoals();
            if (goals != null) {
                for (Goal<Mob> goal : goals.getRunningGoals(mob, GoalType.TARGET)) {
                    goal.stop();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static java.lang.reflect.Method findPlayAmbientMethod() {
        try {
            return Mob.class.getMethod("playAmbientSound");
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean moveTo(Plugin plugin, Mob mob, Location target, double speed) {
        return moveTo(plugin, mob, target, speed, false, false);
    }

    public static boolean moveTo(Plugin plugin, Mob mob, Location target, double speed, boolean bypassCooldown) {
        return moveTo(plugin, mob, target, speed, bypassCooldown, false);
    }

    public static boolean moveTo(Plugin plugin, Mob mob, Location target, double speed,
                                 boolean bypassCooldown, boolean forceRetarget) {
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
                        return ((InvestigateMobGoal) existing).trigger(target, speed, bypassCooldown, forceRetarget);
                    }
                    if (existing != null) {
                        goals.removeGoal(mob, existing);
                    }
                    InvestigateMobGoal created = new InvestigateMobGoal(
                            key, mob, target, speed, bypassCooldown, forceRetarget);
                    goals.addGoal(mob, PRIORITY, created);
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return stepMoveTo(mob, pathfinder, target, speed);
    }

    /**
     * Triggers Stage-1 suspicion pause + look toward {@code target}. Returns {@code false} if already
     * investigating or primed so the caller escalates to Stage-2 movement.
     */
    public static boolean makeSuspicious(Plugin plugin, Mob mob, Location target) {
        if (plugin == null || mob == null || target == null || !mob.isValid()
                || mob.getTarget() != null || mob.isInsideVehicle()) {
            return false;
        }
        try {
            MobGoals goals = Bukkit.getMobGoals();
            if (goals == null) {
                return false;
            }
            GoalKey<Mob> key = GoalKey.of(Mob.class, new NamespacedKey(plugin, "investigate"));
            Goal<Mob> existing = goals.getGoal(mob, key);
            if (existing instanceof InvestigateMobGoal) {
                return ((InvestigateMobGoal) existing).startSuspicion(target);
            }
            if (existing != null) {
                goals.removeGoal(mob, existing);
            }
            InvestigateMobGoal created = new InvestigateMobGoal(key, mob);
            goals.addGoal(mob, PRIORITY, created);
            return created.startSuspicion(target);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void faceLocation(Mob mob, Location target) {
        Location loc = mob.getLocation();
        if (loc.getWorld() != target.getWorld()) {
            return;
        }
        double dx = target.getX() - loc.getX();
        double dy = (target.getY() + 0.5) - mob.getEyeLocation().getY();
        double dz = target.getZ() - loc.getZ();
        double horiz = Math.hypot(dx, dz);
        if (horiz > 1e-4) {
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
            mob.setRotation(yaw, pitch);
        }
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
        private int suspicionTicks;
        private int repathCooldown;
        private long lastAlertMs;
        private long suspiciousUntilMs;
        private long priorityUntilMs;

        InvestigateMobGoal(GoalKey<Mob> key, Mob mob) {
            this.key = key;
            this.mob = mob;
        }

        InvestigateMobGoal(GoalKey<Mob> key, Mob mob, Location target, double speed,
                           boolean bypassCooldown, boolean forceRetarget) {
            this.key = key;
            this.mob = mob;
            trigger(target, speed, bypassCooldown, forceRetarget);
        }

        boolean trigger(Location nextTarget, double nextSpeed, boolean bypassCooldown) {
            return trigger(nextTarget, nextSpeed, bypassCooldown, false);
        }

        boolean trigger(Location nextTarget, double nextSpeed, boolean bypassCooldown, boolean forceRetarget) {
            long now = System.currentTimeMillis();
            long cooldown = MobSenses.settings().alertCooldownMs();
            Location mobLoc = mob.getLocation();
            boolean upgradingFromSuspicion = this.suspicionTicks > 0 || now < this.suspiciousUntilMs;
            if (!bypassCooldown && ticksRemaining > 0 && now < this.priorityUntilMs) {
                return false;
            }
            if (!bypassCooldown && !upgradingFromSuspicion && ticksRemaining > 0
                    && this.target != null && this.target.getWorld() == nextTarget.getWorld()) {
                double sameSpotSq = this.target.distanceSquared(nextTarget);
                if ((now - lastAlertMs) < cooldown && sameSpotSq < 4.0 && nextSpeed <= this.speed) {
                    return false;
                }
            }
            if (ticksRemaining > 0 && this.target != null && mobLoc.getWorld() == this.target.getWorld()
                    && mobLoc.getWorld() == nextTarget.getWorld()) {
                double curDistSq = mobLoc.distanceSquared(this.target);
                double nextDistSq = mobLoc.distanceSquared(nextTarget);
                if (TacticalTargets.shouldKeepCloserTarget(
                        lookTicks, this.speed, nextSpeed, curDistSq, nextDistSq,
                        upgradingFromSuspicion || bypassCooldown || forceRetarget)) {
                    return false;
                }
            }
            double chosenSpeed = nextSpeed;
            if (!upgradingFromSuspicion && ticksRemaining > 0 && this.lookTicks == 0) {
                chosenSpeed = Math.max(this.speed, nextSpeed);
            }
            if (bypassCooldown) {
                this.priorityUntilMs = now + 2500L;
            }
            this.lastAlertMs = now;
            this.target = nextTarget.clone();
            this.speed = chosenSpeed;
            int configuredTicks = Math.max(20, (int) Math.round(MobSenses.settings().investigationSeconds() * 20.0));
            double dist = mobLoc.getWorld() == nextTarget.getWorld()
                    ? Math.hypot(nextTarget.getX() - mobLoc.getX(), nextTarget.getZ() - mobLoc.getZ())
                    : 0.0;
            this.ticksRemaining = Math.max(configuredTicks, (int) Math.min(1200, dist * 7.0));
            this.lookTicks = 0;
            this.suspicionTicks = 0;
            this.suspiciousUntilMs = 0L;
            this.repathCooldown = 0;
            if (mob.getTarget() == null) {
                Pathfinder pf = mob.getPathfinder();
                if (pf != null) {
                    stepMoveTo(mob, pf, this.target, chosenSpeed);
                }
                faceLocation(mob, this.target);
            }
            return true;
        }

        boolean startSuspicion(Location nextTarget) {
            long now = System.currentTimeMillis();
            if ((ticksRemaining > 0 && this.suspicionTicks == 0) || now < this.suspiciousUntilMs) {
                this.suspicionTicks = 0;
                this.suspiciousUntilMs = 0L;
                return false;
            }
            this.target = nextTarget.clone();
            this.speed = 0.0;
            this.suspicionTicks = SUSPICION_PAUSE_TICKS;
            this.ticksRemaining = SUSPICION_PAUSE_TICKS;
            this.lookTicks = 0;
            this.lastAlertMs = now;
            this.suspiciousUntilMs = now + SUSPICION_WINDOW_MS;
            Pathfinder pf = mob.getPathfinder();
            if (pf != null) {
                pf.stopPathfinding();
            }
            faceLocation(mob, this.target);
            if (PLAY_AMBIENT_METHOD != null) {
                try {
                    PLAY_AMBIENT_METHOD.invoke(mob);
                } catch (Throwable ignored) {
                }
            }
            return true;
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
            if (suspicionTicks > 0) {
                if (pf != null) {
                    pf.stopPathfinding();
                }
                if (target != null) {
                    faceLocation(mob, target);
                }
                return;
            }
            if (pf != null && target != null) {
                stepMoveTo(mob, pf, target, speed);
            }
        }

        @Override
        public void stop() {
            ticksRemaining = 0;
            lookTicks = 0;
            suspicionTicks = 0;
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
            Pathfinder pf = mob.getPathfinder();
            if (suspicionTicks > 0) {
                suspicionTicks--;
                if (pf != null) {
                    pf.stopPathfinding();
                }
                faceLocation(mob, target);
                if (suspicionTicks <= 0) {
                    ticksRemaining = 0;
                }
                return;
            }
            Location loc = mob.getLocation();
            if (loc.getWorld() != target.getWorld()) {
                ticksRemaining = 0;
                return;
            }
            double dx = target.getX() - loc.getX();
            double dy = target.getY() - loc.getY();
            double dz = target.getZ() - loc.getZ();
            double horizSq = dx * dx + dz * dz;
            double absDy = Math.abs(dy);
            boolean arrived = (horizSq <= ARRIVAL_DIST_SQ && absDy <= 2.5)
                    || (horizSq <= 6.25 && absDy <= 3.5 && repathCooldown < 14 && (pf == null || !pf.hasPath()));
            if (arrived || lookTicks > 0) {
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
