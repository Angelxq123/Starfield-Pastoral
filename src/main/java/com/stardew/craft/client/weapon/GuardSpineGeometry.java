package com.stardew.craft.client.weapon;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
public final class GuardSpineGeometry {
    private GuardSpineGeometry(){}
    static void contact(VertexConsumer out,Matrix4f pose,Vec3 c,Vec3 right,Vec3 up,Vec3 normal,float size,float age,float fade,boolean edge,int r,int g,int b){
        if(fade<=0)return;
        Vec3 top=c.add(right.scale(-.24*size)).add(up.scale(.61*size)),bottom=c.add(right.scale(.28*size)).add(up.scale(-.52*size));
        WeaponContactGeometry.blade(out,pose,top,bottom,normal,.095*size,fade,edge,r,g,b);
        for(int side:new int[]{-1,1}){
            Vec3 p=c.add(right.scale(side*(.2+age*.026)*size)).add(up.scale(-.12*size-age*age*.003));
            WeaponContactGeometry.blade(out,pose,p.add(up.scale(.1*size)),p.add(right.scale(side*.12*size)).add(up.scale(-.06*size)),normal,.025*size,fade*.7f,edge,r,g,b);
        }
    }
    public static void guard(VertexConsumer out,Matrix4f pose,Vec3 base,Vec3 tip,float fade,boolean blocked){
        if(fade<=0)return;
        Vec3 along=tip.subtract(base).normalize(),side=new Vec3(-along.y,along.x,0);
        for(int face:new int[]{-1,1}){
            Vec3 c=base.lerp(tip,.5).add(0,0,face*.04);
            WeaponContactGeometry.blade(out,pose,c.subtract(along.scale(.18)),c.add(along.scale(.18)),new Vec3(0,0,1),.009,fade*.55f,false,192,220,242);
            if(blocked)WeaponContactGeometry.blade(out,pose,c.subtract(side.scale(.11)),c.add(side.scale(.11)),new Vec3(0,0,1),.023,fade,false,233,242,255);
        }
    }
    public static void blade(VertexConsumer out,Matrix4f pose,Vec3 base,Vec3 tip,int phase,float fade){
        if(phase<0||fade<=0)return;Vec3 along=tip.subtract(base).normalize(),side=new Vec3(-along.y,along.x,0);
        for(int face:new int[]{-1,1}){
            Vec3 off=new Vec3(0,0,face*.037);
            int count=phase==1?3:phase==2?1:2;
            for(int i=0;i<count;i++){
                Vec3 c=base.lerp(tip,.34+i*.19).add(off);
                WeaponContactGeometry.blade(out,pose,c.subtract(side.scale(.026)),c.add(side.scale(.026)),new Vec3(0,0,1),.012,fade*(phase==0?.35f:.8f),false,phase==1?239:167,phase==1?203:201,phase==1?143:229);
            }
            if(phase==1)WeaponContactGeometry.blade(out,pose,base.lerp(tip,.27).add(off),base.lerp(tip,.86).add(off),new Vec3(0,0,1),.008,fade*.65f,false,222,233,246);
        }
    }
}
