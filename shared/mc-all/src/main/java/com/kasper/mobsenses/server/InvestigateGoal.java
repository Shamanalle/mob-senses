package com.kasper.mobsenses.server;

import com.kasper.mobsenses.MobSenses;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
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
    private static final double ARRIVAL_DIST_SQ = 3.25;
    private static final double WAYPOINT_STEP_DIST = 14.0;

    private static final Field GOAL_SELECTOR_FIELD = findGoalSelectorField();
    private static final Map<Mob, InvestigateGoal> GOALS = Collections.synchronizedMap(new WeakHashMap<>());

    private final Mob mob;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double speedModifier = 1.05;
    private int ticksRemaining;
    private int lookTicks;
    private int repathCooldown;
    private long lastAlertMs;

    public InvestigateGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /**
     * Directs {@code mob} to investigate {@code (x, y, z)}, installing an {@link InvestigateGoal}
     * in its {@code goalSelector} if not already present.
     */
    public static boolean alert(Mob mob, double x, double y, double z, double speed) {
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
        goal.trigger(x, y, z, speed);
        return true;
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
        long now = System.currentTimeMillis();
        long cooldown = MobSenses.settings().alertCooldownMs();
        double sameSpotDistSq = (x - this.targetX) * (x - this.targetX) + (z - this.targetZ) * (z - this.targetZ);
        if (isInvestigating() && now - lastAlertMs < cooldown && sameSpotDistSq < 4.0 && speed <= this.speedModifier) {
            return;
        }
        this.lastAlertMs = now;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.speedModifier = speed;
        int configuredTicks = (int) Math.round(MobSenses.settings().investigationSeconds() * 20.0);
        double dist = Math.hypot(x - mob.getX(), z - mob.getZ());
        this.ticksRemaining = Math.max(configuredTicks, (int) Math.min(1200, dist * 7.0));
        this.lookTicks = 0;
        this.repathCooldown = 0;
        if (mob.getTarget() == null) {
            moveTowards(mob, x, y, z, speed);
            mob.getLookControl().setLookAt(x, y + 0.5, z);
        }
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
        moveTowards(mob, targetX, targetY, targetZ, speedModifier);
        mob.getLookControl().setLookAt(targetX, targetY + 0.5, targetZ);
    }

    @Override
    public void stop() {
        ticksRemaining = 0;
        lookTicks = 0;
        if (mob.getTarget() == null) {
            mob.getNavigation().stop();
        }
    }

    @Override
    public void tick() {
        ticksRemaining--;
        double dx = targetX - mob.getX();
        double dy = targetY - mob.getY();
        double dz = targetZ - mob.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq <= ARRIVAL_DIST_SQ || (lookTicks > 0)) {
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

    private static Field findGoalSelectorField() {
        try {
            for (Field f : Mob.class.getDeclaredFields()) {
                if (GoalSelector.class.isAssignableFrom(f.getType())) {
                    if (!java.lang.reflect.Modifier.isPublic(f.getModifiers())) {
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
