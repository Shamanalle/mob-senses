package com.kasper.mobsenses.server;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.lang.reflect.Field;
import java.util.EnumSet;

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

    private static final Field GOAL_SELECTOR_FIELD = findGoalSelectorField();

    private final Mob mob;
    private double targetX;
    private double targetY;
    private double targetZ;
    private double speedModifier = 1.05;
    private int ticksRemaining;
    private int lookTicks;
    private int repathCooldown;

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
            // Fallback if goalSelector reflection fails: issue a direct navigation moveTo
            mob.getNavigation().moveTo(x, y, z, speed);
            mob.getLookControl().setLookAt(x, y + 0.5, z);
            return true;
        }
        goal.trigger(x, y, z, speed);
        return true;
    }

    public static InvestigateGoal getOrInstall(Mob mob) {
        GoalSelector selector = goalSelector(mob);
        if (selector == null) {
            return null;
        }
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof InvestigateGoal existing) {
                return existing;
            }
        }
        InvestigateGoal created = new InvestigateGoal(mob);
        selector.addGoal(PRIORITY, created);
        return created;
    }

    public void trigger(double x, double y, double z, double speed) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.speedModifier = speed;
        this.ticksRemaining = MAX_INVESTIGATE_TICKS;
        this.lookTicks = 0;
        this.repathCooldown = 0;
        if (mob.getTarget() == null) {
            mob.getNavigation().moveTo(x, y, z, speed);
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
        mob.getNavigation().moveTo(targetX, targetY, targetZ, speedModifier);
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
            mob.getNavigation().moveTo(targetX, targetY, targetZ, speedModifier);
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
        for (Field f : Mob.class.getDeclaredFields()) {
            if (GoalSelector.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }
}
