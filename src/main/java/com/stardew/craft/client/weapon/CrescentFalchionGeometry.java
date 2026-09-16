package com.stardew.craft.client.weapon;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
public final class CrescentFalchionGeometry {
    private CrescentFalchionGeometry(){}
    static void crescent(VertexConsumer out,Matrix4f pose,Vec3 c,Vec3 right,Vec3 up,Vec3 normal,float size,float fade,boolean edge,int r,int g,int b){
        if(fade<=0)return;Vec3[] arc=new Vec3[19];
        for(int i=0;i<arc.length;i++){double a=-1.18+i/18.0*2.36;arc[i]=c.add(right.scale((Math.cos(a)-.66)*size*.8)).add(up.scale(Math.sin(a)*size*.66));}
        WeaponContactGeometry.ribbon(out,pose,arc,normal,.09*size,fade,edge,r,g,b);
    }
    static void etchHit(VertexConsumer out,Matrix4f pose,Vec3 c,Vec3 right,Vec3 up,Vec3 normal,float size,float fade,boolean edge,int r,int g,int b){
        if(fade<=0)return;
        Vec3 axis=right.add(up.scale(.22)).normalize().scale(size*.58);
        WeaponContactGeometry.blade(out,pose,c.subtract(axis),c.add(axis),normal,.045*size,fade,edge,r,g,b);
        WeaponContactGeometry.blade(out,pose,c.add(up.scale(-size*.13)),c.add(up.scale(size*.13)),normal,.018*size,fade*.55f,edge,r,g,b);
    }
    static void etch(VertexConsumer out,Matrix4f pose,Vec3[] path,float fade,float burst){
        if(fade<=0||path.length<2)return;
        WeaponContactGeometry.ribbon(out,pose,path,new Vec3(0,1,0),.028+burst*.018,fade,false,168,212,231);
        if(burst>0){
            Vec3[] raised=new Vec3[path.length];for(int i=0;i<path.length;i++)raised[i]=path[i].add(0,.09*burst,0);
            WeaponContactGeometry.ribbon(out,pose,raised,new Vec3(0,1,0),.012,fade*burst,false,236,224,163);
        }
    }
}
