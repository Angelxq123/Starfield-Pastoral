package com.stardew.craft.client.light;

import com.mojang.logging.LogUtils;
import com.stardew.craft.block.mine.MineLampBlock;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** A guarded in-memory Complementary ACT adapter. Shader-pack files and user settings are never rewritten. */
public final class ComplementaryLampBridge {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final String VOXEL = "int GetVoxelIDs(int mat) {";
    private static final String COLORS = "vec4 GetSpecialBlocklightColor(int mat) {";
    private static volatile boolean compatible;
    private static volatile boolean nativeProgram;
    private static Method irisInstance, irisEnabled;
    private static boolean apiChecked;
    private ComplementaryLampBridge() {}

    // Iris constructs its base ProgramSet before IdMap. Discover capabilities before either one.
    public static void beginPack(Path directory, String file) {
        if (!file.endsWith("shaders.properties")) return;
        compatible=false; nativeProgram=false;
        try {
            String source=Files.readString(directory.resolve("block.properties"));
            if(!source.contains("block.10560=lantern") || source.contains("stardewcraft:mine_lamp")) return;
            String voxel=Files.readString(directory.resolve("lib/voxelization/lightVoxelization.glsl"));
            String colors=Files.readString(directory.resolve("lib/colors/blocklightColors.glsl"));
            if(!voxel.contains(VOXEL) || !colors.contains(COLORS) || !voxel.contains("COLORED_LIGHTING_INTERNAL")) return;
            for(int i=0;i<10;i++) if(source.matches("(?s).*block\\."+(24000+i*2)+"\\s*=.*")) return;
            if(voxel.matches("(?s).*return\\s+18[0-9]\\s*;.*") || colors.matches("(?s).*mat\\s*==\\s*18[0-9].*")) return;
            compatible=true;
            LOGGER.info("Complementary ACT mine-lamp adapter prepared; waiting for the active shader feature path");
        } catch(IOException ex) {
            LOGGER.debug("Shader pack has no matching Complementary ACT layout; retaining standard light rendering",ex);
        }
    }

    public static String blockProperties(Path directory,String file,String source) {
        if (!compatible || !file.endsWith("block.properties") || source==null) return source;
        StringBuilder out=new StringBuilder(source).append("\n# StardewCraft approved mine lamps: eight independent ACT emitters\n");
        for(var theme:MineLampBlock.Theme.values()) out.append("block.").append(24000+theme.ordinal()*2)
                .append("=stardewcraft:mine_lamp:lit=true:theme=").append(theme.getSerializedName()).append('\n');
        out.append("block.24016=stardewcraft:skull_wall_brazier:lit=true:section=1\n");
        out.append("block.24018=stardewcraft:skull_shrine_wall:lit=true:section=1 stardewcraft:skull_shrine_wall:lit=true:section=4 stardewcraft:skull_shrine_altar:lit=true:section=2 stardewcraft:skull_shrine_altar:lit=true:section=9\n");
        return out.toString();
    }

    public static String patchIncluded(String source) {
        if(!compatible) return source;
        if(source.contains(VOXEL) && !source.contains("SC_MINE_LAMP_VOXELS")) {
            source=source.replace(VOXEL,VOXEL+"\n// SC_MINE_LAMP_VOXELS\nif (mat >= 24000 && mat <= 24018 && mat % 2 == 0) return 180 + (mat - 24000) / 2;\n");
        }
        if(source.contains(COLORS) && !source.contains("SC_MINE_LAMP_COLORS")) {
            StringBuilder injection=new StringBuilder(COLORS).append("\n// SC_MINE_LAMP_COLORS\n");
            for(var theme:MineLampBlock.Theme.values()) {
                int c=theme.lightColor();
                injection.append("if (mat == ").append(180+theme.ordinal()).append(") { vec3 hue = mix(vec3(1.0), vec3(")
                        .append((c>>16&255)/255.0).append(',').append((c>>8&255)/255.0).append(',').append((c&255)/255.0)
                        .append("), 0.22); return vec4(hue * (12.0 / (hue.r + hue.g + hue.b)), 0.0); }\n");
            }
            injection.append("if (mat == 188) { vec3 hue = mix(vec3(1.0), vec3(1.0, 0.894, 0.651), 0.22); return vec4(hue * (12.0 / (hue.r + hue.g + hue.b)), 0.0); }\n");
            injection.append("if (mat == 189) { vec3 hue = mix(vec3(1.0), vec3(0.953, 0.686, 0.847), 0.22); return vec4(hue * (12.0 / (hue.r + hue.g + hue.b)), 0.0); }\n");
            source=source.replace(COLORS,injection);
        }
        return source;
    }

    /** Called after Iris has evaluated settings/platform macros, not just after finding the optional source file. */
    public static void observeCompiledSource(Object path,String source) {
        if(compatible && path.toString().contains("shadow.vsh") && source!=null
                && source.contains("void UpdateVoxelMap") && source.contains("mat >= 24000")) nativeProgram=true;
    }
    public static boolean nativeLightingActive() {
        if(!compatible || !nativeProgram) return false;
        try {
            if(!apiChecked) {
                apiChecked=true;
                Class<?> api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisInstance=api.getMethod("getInstance");irisEnabled=api.getMethod("isShaderPackInUse");
            }
            return irisEnabled!=null && Boolean.TRUE.equals(irisEnabled.invoke(irisInstance.invoke(null)));
        } catch(ReflectiveOperationException ex) { return false; }
    }
}
