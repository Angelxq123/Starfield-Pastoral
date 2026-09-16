import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;

/** Decode the processed resources using Minecraft's image decoder, without a game window. */
public final class NativeNpcTextureChecks {
    public static void main(String[] args) throws Exception {
        Path resources=Path.of(args[0]);
        var manifest=JsonParser.parseString(Files.readString(resources.resolve("assets/stardewcraft/npc_native_models.json"))).getAsJsonArray();
        int count=0;
        for(var entry:manifest) {
            String id=entry.getAsString();
            var model=JsonParser.parseString(Files.readString(resources.resolve("assets/stardewcraft/npc_native/"+id+".json"))).getAsJsonObject();
            var texture=model.get("texture").getAsString().split(":",2);
            Path png=resources.resolve("assets/"+texture[0]+"/"+texture[1]);
            try(var input=Files.newInputStream(png);var image=NativeImage.read(input)) {
                if(image.getWidth()!=model.get("textureWidth").getAsInt()
                        || image.getHeight()!=model.get("textureHeight").getAsInt())
                    throw new AssertionError(id+": atlas/model dimensions disagree");
                boolean visible=false;
                for(int y=0;y<image.getHeight() && !visible;y++)for(int x=0;x<image.getWidth();x++)
                    if((image.getPixelRGBA(x,y)>>>24)!=0){visible=true;break;}
                if(!visible)throw new AssertionError(id+": empty texture");
            }
            count++;
        }
        System.out.println("Decoded "+count+" processed native NPC textures; dimensions and visible pixels passed.");
    }
}
