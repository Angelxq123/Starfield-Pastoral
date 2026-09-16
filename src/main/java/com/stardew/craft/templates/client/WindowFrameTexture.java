package com.stardew.craft.templates.client;

import java.util.ArrayList;
import com.stardew.craft.templates.WindowFrameProfile;
import net.minecraft.core.Direction;

/** Maps the approved native 64px atlas by actual frame part, never by a stretched cube sprite. */
final class WindowFrameTexture {
    static TemplateMesh.MeshQuad apply(TemplateMesh.MeshQuad face, WindowFrameProfile.Part part) {
        var b=part.box();float x=b.maxX()-b.minX(),y=b.maxY()-b.minY(),z=b.maxZ()-b.minZ();
        Direction d=face.direction();
        float width=d.getAxis()==Direction.Axis.X?z:x;
        float height=d.getAxis()==Direction.Axis.Y?z:y;
        float u=32,v=0;boolean rotate=false;
        if(d.getAxis()==Direction.Axis.Y && part.horizontal())v=16;
        if(d==Direction.NORTH || d==Direction.SOUTH) {
            switch(part.stage()) {
                case "front" -> {
                    if(part.horizontal()) {u=8;v=b.minY()==0?2:0;}
                    else {u=b.minX()<8?0:2;v=b.minY();}
                }
                case "bead" -> {
                    u=part.horizontal()?4:b.minX()<8?4:5;v=part.horizontal()?0:b.minY();
                    rotate=part.horizontal();
                }
                case "back" -> {u=48;v=0;rotate=part.horizontal();}
                case "divider" -> {u=part.horizontal()?8:6;v=part.horizontal()?4:b.minY();}
                default -> {}
            }
        }
        var uv=new ArrayList<TemplateMesh.TexturePoint>();
        for(var p:face.vertices()) {
            float px=p.x*16,py=p.y*16,pz=p.z*16;
            float s=switch(d) {
                case NORTH -> (b.maxX()-px)/x;
                case SOUTH,UP,DOWN -> (px-b.minX())/x;
                case WEST -> (pz-b.minZ())/z;
                case EAST -> (b.maxZ()-pz)/z;
            };
            float t=switch(d) {
                case UP -> (pz-b.minZ())/z;
                case DOWN -> (b.maxZ()-pz)/z;
                default -> (b.maxY()-py)/y;
            };
            uv.add(new TemplateMesh.TexturePoint((u+(rotate?t*height:s*width))/64F,
                    (v+(rotate?(1-s)*width:t*height))/64F));
        }
        return new TemplateMesh.MeshQuad(d,d,face.vertices(),uv);
    }
    private WindowFrameTexture() {}
}
