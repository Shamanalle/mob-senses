package com.kasper.mobsenses.mixin;

import com.kasper.mobsenses.server.GameEventHook;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every game event of a world (the ones sculk sensors hear: steps, blocks, doors, fights, explosions)
 * passes through {@code ServerLevel.gameEvent}; Fabric has no event of its own for them.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {

    @Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
            at = @At("HEAD"))
    private void mobsenses$gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context, CallbackInfo ci) {
        GameEventHook.heard((ServerLevel) (Object) this, event, pos);
    }
}
