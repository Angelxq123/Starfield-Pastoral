package com.stardew.craft.mixin;
import com.google.common.collect.ImmutableList;
import com.stardew.craft.client.light.ComplementaryLampBridge;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="net.irisshaders.iris.shaderpack.include.IncludeProcessor",remap=false)
public abstract class IrisMineLampIncludeMixin {
    @Inject(method="getIncludedFile",at=@At("RETURN"),cancellable=true)
    private void stardewcraft$lampColors(@Coerce Object path,CallbackInfoReturnable<ImmutableList<String>> cir) {
        var lines=cir.getReturnValue();if(lines==null)return;
        String source=String.join("\n",lines), patched=ComplementaryLampBridge.patchIncluded(source);
        if(!source.equals(patched))cir.setReturnValue(ImmutableList.copyOf(patched.split("\n",-1)));
    }
}
