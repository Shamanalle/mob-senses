package com.kasper.mobsenses.mixin;

import com.kasper.mobsenses.server.GameEventHook;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks world explosions on Minecraft 1.16.5 (before {@code GameEvent} existed).
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    @Inject(method = "explode", at = @At("HEAD"), require = 0)
    private void mobsenses$explode(
            Entity source,
            DamageSource damageSource,
            ExplosionDamageCalculator damageCalculator,
            double x,
            double y,
            double z,
            float radius,
            boolean fire,
            Explosion.BlockInteraction blockInteraction,
            CallbackInfoReturnable<Explosion> cir) {
        GameEventHook.exploded((ServerLevel) (Object) this, source, x, y, z);
    }
}
