package com.stardew.craft.mixin;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;
import com.stardew.craft.client.interior.TownDoorShaderPatcher;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/** Shader loading hook adapted from Immersive Portals' MixinProgram (Apache-2.0). */
@Mixin(Program.class)
public abstract class TownDoorProgramMixin {
    private static final ThreadLocal<Program.Type> STARDEWCRAFT_TYPE = new ThreadLocal<>();
    private static final ThreadLocal<String> STARDEWCRAFT_NAME = new ThreadLocal<>();

    @Inject(method = "compileShaderInternal", at = @At("HEAD"))
    private static void stardewcraft$begin(Program.Type type, String name, InputStream data, String source,
                                           GlslPreprocessor preprocessor, CallbackInfoReturnable<Integer> cir) {
        STARDEWCRAFT_TYPE.set(type);
        STARDEWCRAFT_NAME.set(name);
    }

    @Inject(method = "compileShaderInternal", at = @At("RETURN"))
    private static void stardewcraft$end(Program.Type type, String name, InputStream data, String source,
                                         GlslPreprocessor preprocessor, CallbackInfoReturnable<Integer> cir) {
        STARDEWCRAFT_TYPE.remove();
        STARDEWCRAFT_NAME.remove();
    }

    @Redirect(method = "compileShaderInternal", at = @At(value = "INVOKE",
            target = "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/InputStream;Ljava/nio/charset/Charset;)Ljava/lang/String;",
            remap = false))
    private static String stardewcraft$patch(InputStream stream, Charset charset) throws IOException {
        String source = IOUtils.toString(stream, charset);
        Program.Type type = STARDEWCRAFT_TYPE.get();
        String name = STARDEWCRAFT_NAME.get();
        return type == null || name == null ? source : TownDoorShaderPatcher.transformVanilla(type, name, source);
    }
}
