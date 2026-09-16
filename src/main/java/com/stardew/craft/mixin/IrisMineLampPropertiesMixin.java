package com.stardew.craft.mixin;
import com.stardew.craft.client.light.ComplementaryLampBridge;
import java.nio.file.Path;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="net.irisshaders.iris.shaderpack.IdMap",remap=false)
public abstract class IrisMineLampPropertiesMixin {
    @Inject(method="readProperties",at=@At("RETURN"),cancellable=true)
    private static void stardewcraft$lampIds(Path directory,String file,CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(ComplementaryLampBridge.blockProperties(directory,file,cir.getReturnValue()));
    }
}
