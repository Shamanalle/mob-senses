package com.kasper.mobsenses.mixin;

import com.kasper.mobsenses.server.GameEventHook;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every game event on Minecraft 1.19 - 1.20.4 passes through
 * {@code ServerLevel.gameEvent(GameEvent, Vec3, GameEvent.Context)}.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    @Inject(method = "gameEvent(Lnet/minecraft/world/level/gameevent/GameEvent;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
            at = @At("HEAD"))
    private void mobsenses$gameEvent(GameEvent event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
        GameEventHook.heard((ServerLevel) (Object) this, event, pos, context);
    }
}
