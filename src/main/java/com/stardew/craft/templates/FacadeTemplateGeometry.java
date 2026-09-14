package com.stardew.craft.templates;

import java.util.ArrayList;
import java.util.List;

/** Local facade coordinates: front is north, all dimensions are model units. */
public final class FacadeTemplateGeometry {
    public static List<TemplateBox> frame(TemplateShape shape,int joins) {
        if(shape==TemplateShape.WALL_JUNCTION) {
            int mask=joins==0?15:joins;
            var boxes=new ArrayList<TemplateBox>();boxes.add(box(6,6,0,10,10,3));
            if((mask&1)!=0)boxes.add(box(6,10,0,10,16,3));
            if((mask&2)!=0)boxes.add(box(10,6,0,16,10,3));
            if((mask&4)!=0)boxes.add(box(6,0,0,10,6,3));
            if((mask&8)!=0)boxes.add(box(0,6,0,6,10,3));
            return boxes;
        }
        if(!shape.isWindow())return shape.collisionBoxes();
        var boxes=new ArrayList<TemplateBox>();
        WindowFrameProfile.parts(joins).forEach(part -> boxes.add(part.box()));
        if(shape==TemplateShape.WINDOW_TRANSOM) {
            float x0=(joins&8)==0?2:1,x1=(joins&2)==0?14:15;
            boxes.add(box(x0,7,3,x1,9,6));
        }
        return boxes;
    }
    public static List<TemplateBox> fill(TemplateShape shape,int joins) {
        if(!shape.isWindow())return List.of(box(0,0,3,16,16,16));
        float x0=(joins&8)==0?2:1,x1=(joins&2)==0?14:15;
        float y0=(joins&4)==0?2:1,y1=(joins&1)==0?14:15;
        return shape==TemplateShape.WINDOW_TRANSOM?List.of(box(x0,y0,5,x1,7,6),box(x0,9,5,x1,y1,6))
                :List.of(box(x0,y0,5,x1,y1,6));
    }
    private static TemplateBox box(float x,float y,float z,float xx,float yy,float zz) {return new TemplateBox(x,y,z,xx,yy,zz);}
    private FacadeTemplateGeometry() {}
}
