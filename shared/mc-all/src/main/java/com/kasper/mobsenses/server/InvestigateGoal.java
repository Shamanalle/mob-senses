package com.kasper.mobsenses.server;

import com.kasper.mobsenses.MobSenses;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Low-priority investigation goal attached lazily to listener mobs.
 *
 * <p>When a mob hears a sound and has no active combat target, it walks toward the noise
 * origin, looks around left and right for a couple of seconds, and returns to its normal
 * routine if nothing is found before the timeout expires.
 */
public final class InvestigateGoal extends Goal {

    public static final int PRIORITY = 6;
    private static final int MAX_INVESTIGATE_TICKS = 160;
    private static final int LOOK_AROUND_TICKS = 50;
    private static final int SUSPICION_PAUSE_TICKS = 36;
    private static final long SUSPICION_WINDOW_MS = 4500L;
    private static final double ARRIVAL_DIST_SQ = 3.25;
    private static final double WAYPOINT_STEP_DIST = 14.0;

    private static final Field GOAL_SELECTOR_FIELD = findGoalSelectorField(0);
    private static final Field TARGET_SELECTOR_FIELD = findGoalSelectorField(1);
    private static final Field AVAILABLE_GOALS_FIELD = findAvailableGoalsField();
    private static final Map<Mob, InvestigateGoal> GOALS = Collections.synchronizedMap(new WeakHashMap<>());

    private final Mob mob;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double speedModifier = 1.05;
    private int ticksRemaining;
    private int lookTicks;
    private int suspicionTicks;
    private int repathCooldown;
    private long lastAlertMs;
    private long suspiciousUntilMs;
    private long priorityUntilMs;

    public InvestigateGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * Directs {@code mob} to investigate {@code (x, y, z)}, installing an {@link InvestigateGoal}
     * in its {@code goalSelector} if not already present.
     */
    public static boolean alert(Mob mob, double x, double y, double z, double speed) {
        return alert(mob, x, y, z, speed, false);
    }

    /**
     * Directs {@code mob} to investigate {@code (x, y, z)}, optionally bypassing the per-mob alert cooldown
     * (used for darkness escape Last Known Position and stealth ambush sweeps).
     */
    public static boolean alert(Mob mob, double x, double y, double z, double speed, boolean bypassCooldown) {
        if (mob == null || !mob.isAlive() || mob.isNoAi() || mob.getTarget() != null) {
            return false;
        }
        InvestigateGoal goal = getOrInstall(mob);
        if (goal == null) {
            // Fallback if goalSelector reflection fails: issue a direct or stepped navigation moveTo
            moveTowards(mob, x, y, z, speed);
            mob.getLookControl().setLookAt(x, y + 0.5, z);
            return true;
        }
        return goal.triggerNow(x, y, z, speed, bypassCooldown);
    }

    /**
     * Triggers Stage-1 suspicion: stops the mob, faces {@code (x, y, z)} for a brief pause, and plays a
     * subtle ambient cue. Returns {@code false} if the mob is unavailable or already primed so the caller
     * escalates to Stage-2 full investigation.
     */
    public static boolean makeSuspicious(Mob mob, double x, double y, double z) {
        if (mob == null || !mob.isAlive() || mob.isNoAi() || mob.getTarget() != null) {
            return false;
        }
        InvestigateGoal goal = getOrInstall(mob);
        if (goal == null) {
            return false;
        }
        return goal.startSuspicion(x, y, z);
    }

    private static void moveTowards(Mob mob, double targetX, double targetY, double targetZ, double speed) {
        double dx = targetX - mob.getX();
        double dy = targetY - mob.getY();
        double dz = targetZ - mob.getZ();
        double horizDist = Math.hypot(dx, dz);
        if (horizDist <= WAYPOINT_STEP_DIST && mob.getNavigation().moveTo(targetX, targetY, targetZ, speed)) {
            return;
        }
        if (horizDist > 1.0e-3) {
            double step = Math.min(WAYPOINT_STEP_DIST, horizDist);
            double wx = mob.getX() + (dx / horizDist) * step;
            double wy = mob.getY() + (dy / Math.max(1.0, horizDist)) * Math.min(4.0, step);
            double wz = mob.getZ() + (dz / horizDist) * step;
            if (!mob.getNavigation().moveTo(wx, wy, wz, speed)) {
                mob.getNavigation().moveTo(targetX, targetY, targetZ, speed);
            }
        } else {
            mob.getNavigation().moveTo(targetX, targetY, targetZ, speed);
        }
    }

    public static InvestigateGoal getOrInstall(Mob mob) {
        InvestigateGoal existing = GOALS.get(mob);
        if (existing != null) {
            return existing;
        }
        GoalSelector selector = goalSelector(mob);
        if (selector == null) {
            return null;
        }
        InvestigateGoal created = new InvestigateGoal(mob);
        selector.addGoal(PRIORITY, created);
        GOALS.put(mob, created);
        return created;
    }

    public void trigger(double x, double y, double z, double speed) {
        triggerNow(x, y, z, speed, false);
    }

    public boolean triggerNow(double x, double y, double z, double speed, boolean bypassCooldown) {
        long now = System.currentTimeMillis();
        long cooldown = MobSenses.settings().alertCooldownMs();
        boolean upgradingFromSuspicion = this.suspicionTicks > 0 || now < this.suspiciousUntilMs;
        if (!bypassCooldown && isInvestigating() && now < this.priorityUntilMs) {
            return false;
        }
        double sameSpotDistSq = (x - this.targetX) * (x - this.targetX) + (z - this.targetZ) * (z - this.targetZ);
        if (!bypassCooldown && !upgradingFromSuspicion && isInvestigating()
                && now - lastAlertMs < cooldown && sameSpotDistSq < 4.0 && speed <= this.speedModifier) {
            return false;
        }
        double nextSpeed = speed;
        if (!upgradingFromSuspicion && isInvestigating() && this.lookTicks == 0) {
            nextSpeed = Math.max(this.speedModifier, speed);
        }
        if (bypassCooldown) {
            this.priorityUntilMs = now + 2500L;
        }
        this.lastAlertMs = now;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.speedModifier = nextSpeed;
        int configuredTicks = (int) Math.round(MobSenses.settings().investigationSeconds() * 20.0);
        double dist = Math.hypot(x - mob.getX(), z - mob.getZ());
        this.ticksRemaining = Math.max(configuredTicks, (int) Math.min(1200, dist * 7.0));
        this.lookTicks = 0;
        this.suspicionTicks = 0;
        this.suspiciousUntilMs = 0L;
        this.repathCooldown = 0;
        if (mob.getTarget() == null) {
            moveTowards(mob, x, y, z, nextSpeed);
            mob.getLookControl().setLookAt(x, y + 0.5, z);
        }
        return true;
    }

    private boolean startSuspicion(double x, double y, double z) {
        long now = System.currentTimeMillis();
        if ((isInvestigating() && this.suspicionTicks == 0) || now < this.suspiciousUntilMs) {
            this.suspicionTicks = 0;
            this.suspiciousUntilMs = 0L;
            return false;
        }
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.speedModifier = 0.0;
        this.suspicionTicks = SUSPICION_PAUSE_TICKS;
        this.ticksRemaining = SUSPICION_PAUSE_TICKS;
        this.lookTicks = 0;
        this.lastAlertMs = now;
        this.suspiciousUntilMs = now + SUSPICION_WINDOW_MS;
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(x, y + 0.5, z);
        try {
            mob.playAmbientSound();
        } catch (Throwable ignored) {
        }
        return true;
    }

    public boolean isSuspicious() {
        return suspicionTicks > 0 && ticksRemaining > 0 && mob.getTarget() == null;
    }

    public boolean isInvestigating() {
        return ticksRemaining > 0 && mob.getTarget() == null;
    }

    public double targetX() {
        return targetX;
    }

    public double targetY() {
        return targetY;
    }

    public double targetZ() {
        return targetZ;
    }

    @Override
    public boolean canUse() {
        return ticksRemaining > 0 && mob.getTarget() == null && !mob.isPassenger();
    }

    @Override
    public boolean canContinueToUse() {
        return ticksRemaining > 0 && mob.getTarget() == null && !mob.isPassenger();
    }

    @Override
    public void start() {
        repathCooldown = 0;
        if (suspicionTicks > 0) {
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(targetX, targetY + 0.5, targetZ);
            return;
        }
        moveTowards(mob, targetX, targetY, targetZ, speedModifier);
        mob.getLookControl().setLookAt(targetX, targetY + 0.5, targetZ);
    }

    @Override
    public void stop() {
        ticksRemaining = 0;
        lookTicks = 0;
        suspicionTicks = 0;
        priorityUntilMs = 0L;
        if (mob.getTarget() == null) {
            mob.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        ticksRemaining--;
        if (suspicionTicks > 0) {
            suspicionTicks--;
            mob.getNavigation().stop();
            mob.getLookControl().setLookAt(targetX, targetY + 0.5, targetZ);
            if (suspicionTicks <= 0) {
                ticksRemaining = 0;
            }
            return;
        }
        double dx = targetX - mob.getX();
        double dy = targetY - mob.getY();
        double dz = targetZ - mob.getZ();
        double horizSq = dx * dx + dz * dz;
        double absDy = Math.abs(dy);
        boolean arrived = (horizSq <= ARRIVAL_DIST_SQ && absDy <= 2.5)
                || (horizSq <= 6.25 && absDy <= 3.5 && repathCooldown < 14 && mob.getNavigation().isDone());

        if (arrived || (lookTicks > 0)) {
            mob.getNavigation().stop();
            lookTicks++;
            double angle = (lookTicks * 0.18) + (mob.getId() * 0.7);
            double lx = mob.getX() + Math.cos(angle) * 3.0;
            double lz = mob.getZ() + Math.sin(angle) * 3.0;
            mob.getLookControl().setLookAt(lx, mob.getEyeY(), lz);
            if (lookTicks >= LOOK_AROUND_TICKS) {
                ticksRemaining = 0;
            }
            return;
        }

        mob.getLookControl().setLookAt(targetX, targetY + 0.5, targetZ);
        if (--repathCooldown <= 0 || mob.getNavigation().isDone()) {
            repathCooldown = 15;
            moveTowards(mob, targetX, targetY, targetZ, speedModifier);
        }
    }

    /**
     * Clears {@code mob}'s active combat target and stops any running target-selector goals (so vanilla
     * {@code TargetGoal#targetMob} does not re-lock onto the player on the very next tick).
     */
    public static void clearCombatTarget(Mob mob, boolean clearLastHurt) {
        if (mob == null) {
            return;
        }
        mob.setTarget(null);
        if (clearLastHurt) {
            mob.setLastHurtByMob(null);
        }
        GoalSelector targetSel = targetSelector(mob);
        if (targetSel == null || AVAILABLE_GOALS_FIELD == null) {
            return;
        }
        try {
            Object raw = AVAILABLE_GOALS_FIELD.get(targetSel);
            if (raw instanceof Set<?>) {
                for (Object entry : (Set<?>) raw) {
                    if (entry instanceof WrappedGoal) {
                        WrappedGoal wg = (WrappedGoal) entry;
                        if (wg.isRunning()) {
                            wg.stop();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static GoalSelector goalSelector(Mob mob) {
        if (GOAL_SELECTOR_FIELD == null) {
            return null;
        }
        try {
            return (GoalSelector) GOAL_SELECTOR_FIELD.get(mob);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static GoalSelector targetSelector(Mob mob) {
        if (TARGET_SELECTOR_FIELD == null) {
            return null;
        }
        try {
            return (GoalSelector) TARGET_SELECTOR_FIELD.get(mob);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findGoalSelectorField(int ordinal) {
        try {
            int seen = 0;
            for (Field f : Mob.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && GoalSelector.class.isAssignableFrom(f.getType())) {
                    if (!Modifier.isPublic(f.getModifiers())) {
                        f.setAccessible(true);
                    }
                    if (seen == ordinal) {
                        return f;
                    }
                    seen++;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Field findAvailableGoalsField() {
        try {
            for (Field f : GoalSelector.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && f.getType() == Set.class) {
                    if (!Modifier.isPublic(f.getModifiers())) {
                        f.setAccessible(true);
                    }
                    return f;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
