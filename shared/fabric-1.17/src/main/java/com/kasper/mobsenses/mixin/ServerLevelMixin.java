package com.kasper.mobsenses.mixin;

import com.kasper.mobsenses.server.GameEventHook;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every game event on Minecraft 1.17 - 1.18.2 passes through {@code ServerLevel.gameEvent(Entity, GameEvent, BlockPos)};
 * explosions also pass through {@code explode}.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    @Inject(method = "gameEvent(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD"), require = 0)
    private void mobsenses$gameEvent(Entity entity, GameEvent event, BlockPos pos, CallbackInfo ci) {
        GameEventHook.heard((ServerLevel) (Object) this, entity, event, pos);
    }

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
