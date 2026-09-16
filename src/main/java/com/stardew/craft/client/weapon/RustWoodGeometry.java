package com.stardew.craft.client.weapon;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
public final class RustWoodGeometry {
    private RustWoodGeometry(){}
    static void contact(VertexConsumer out,Matrix4f pose,Vec3 c,Vec3 right,Vec3 up,Vec3 normal,boolean wood,float size,float age,float fade,boolean edge,int r,int g,int b){
        if(fade<=0)return;
        if(wood){
            for(int side:new int[]{-1,1}){
                Vec3[] path=new Vec3[11];for(int i=0;i<path.length;i++){double t=i/10.0;path[i]=c.add(right.scale((t-.5)*size)).add(up.scale(side*Math.sin(t*Math.PI)*.15*size+(t-.5)*.34*size));}
                WeaponContactGeometry.ribbon(out,pose,path,normal,.043*size,fade,edge,r,g,b);
            }
        }else{
            WeaponContactGeometry.blade(out,pose,c.add(right.scale(-.46*size)).add(up.scale(.32*size)),c.add(right.scale(.4*size)).add(up.scale(-.33*size)),normal,.064*size,fade,edge,r,g,b);
            for(int i=0;i<3;i++){
                Vec3 p=c.add(right.scale((i-1)*(.16+age*.015)*size)).add(up.scale(-.14-i*.075-age*age*.006));
                WeaponContactGeometry.blade(out,pose,p,p.add(right.scale(.07*size)).add(up.scale(.085*size)),normal,.024*size,fade*.6f,edge,r,g,b);
            }
        }
    }
    public static void shelter(VertexConsumer out,Matrix4f pose,Vec3 base,Vec3 tip,float fade){
        if(fade<=0)return;Vec3 along=tip.subtract(base).normalize(),side=new Vec3(-along.y,along.x,0);
        for(int face:new int[]{-1,1})for(int sign:new int[]{-1,1}){
            Vec3[] path=new Vec3[13];for(int i=0;i<path.length;i++){double t=i/12.0;path[i]=base.lerp(tip,.25+t*.5).add(side.scale(sign*Math.sin(t*Math.PI)*.055)).add(0,0,face*.038);}
            WeaponContactGeometry.ribbon(out,pose,path,new Vec3(0,0,1),.008,fade*.7f,false,170,211,115);
        }
    }
}
