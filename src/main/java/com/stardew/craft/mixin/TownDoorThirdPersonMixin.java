package com.stardew.craft.mixin;

import com.stardew.craft.client.interior.TownDoorClient;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class TownDoorThirdPersonMixin {
    @Shadow public abstract Vec3 getPosition();
    @Shadow protected abstract void setPosition(Vec3 pos);

    @Redirect(method = "getMaxZoom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private BlockHitResult stardewcraft$clipCameraThroughDoor(BlockGetter level, ClipContext context) {
        return (Object) this == Minecraft.getInstance().gameRenderer.getMainCamera()
                ? TownDoorClient.clipCamera(level, context) : level.clip(context);
    }

    @Inject(method = "setup", at = @At("RETURN"))
    private void stardewcraft$moveCameraThroughDoor(BlockGetter level, Entity entity, boolean detached,
                                                  boolean mirrored, float partial, CallbackInfo ci) {
        if (detached && (Object) this == Minecraft.getInstance().gameRenderer.getMainCamera()) {
            setPosition(TownDoorClient.cameraPosition(getPosition()));
        }
    }
}
