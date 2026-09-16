package com.stardew.craft.templates;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Approved single-material railing, with a shared corner post and closed free ends. */
public final class BalconyRailingProfile {
    private static final List<List<TemplateBox>> PROFILES = List.of(
            List.of(new TemplateBox(0F,0F,0F,4F,16F,4F),new TemplateBox(4F,12F,0F,12F,14F,4F),new TemplateBox(4F,1F,1F,12F,3F,3F),new TemplateBox(12F,0F,0F,16F,16F,4F)),
            List.of(new TemplateBox(0F,0F,0F,4F,16F,4F),new TemplateBox(4F,12F,0F,16F,14F,4F),new TemplateBox(4F,1F,1F,16F,3F,3F),new TemplateBox(11F,3F,1F,13F,12F,3F)),
            List.of(new TemplateBox(0F,12F,0F,16F,14F,4F),new TemplateBox(0F,1F,1F,16F,3F,3F),new TemplateBox(3F,3F,1F,5F,12F,3F),new TemplateBox(11F,3F,1F,13F,12F,3F)),
            List.of(new TemplateBox(12F,0F,0F,16F,16F,4F),new TemplateBox(0F,12F,0F,12F,14F,4F),new TemplateBox(0F,1F,1F,12F,3F,3F),new TemplateBox(3F,3F,1F,5F,12F,3F)),
            List.of(new TemplateBox(12F,0F,0F,16F,16F,4F),new TemplateBox(0F,12F,0F,12F,14F,4F),new TemplateBox(0F,1F,1F,12F,3F,3F),new TemplateBox(3F,3F,1F,5F,12F,3F),new TemplateBox(12F,12F,4F,16F,14F,16F),new TemplateBox(13F,1F,4F,15F,3F,16F),new TemplateBox(13F,3F,11F,15F,12F,13F)),
            List.of(new TemplateBox(0F,0F,0F,4F,16F,4F),new TemplateBox(4F,12F,0F,16F,14F,4F),new TemplateBox(4F,1F,1F,16F,3F,3F),new TemplateBox(11F,3F,1F,13F,12F,3F),new TemplateBox(0F,12F,4F,4F,14F,16F),new TemplateBox(1F,1F,4F,3F,3F,16F),new TemplateBox(1F,3F,11F,3F,12F,13F)),
            List.of(new TemplateBox(12F,0F,0F,16F,16F,4F),new TemplateBox(4F,12F,0F,12F,14F,4F),new TemplateBox(4F,1F,1F,12F,3F,3F),new TemplateBox(4F,3F,1F,5F,12F,3F),new TemplateBox(12F,12F,4F,16F,14F,16F),new TemplateBox(13F,1F,4F,15F,3F,16F),new TemplateBox(13F,3F,11F,15F,12F,13F),new TemplateBox(0F,0F,0F,4F,16F,4F)),
            List.of(new TemplateBox(0F,0F,0F,4F,16F,4F),new TemplateBox(4F,12F,0F,12F,14F,4F),new TemplateBox(4F,1F,1F,12F,3F,3F),new TemplateBox(11F,3F,1F,12F,12F,3F),new TemplateBox(0F,12F,4F,4F,14F,16F),new TemplateBox(1F,1F,4F,3F,3F,16F),new TemplateBox(1F,3F,11F,3F,12F,13F),new TemplateBox(12F,0F,0F,16F,16F,4F))
    );
    public static List<TemplateBox> boxes(BlockState state) { return PROFILES.get(state.getValue(BalconyRailingTemplateBlock.PROFILE)); }
    public static VoxelShape shape(BlockState state) {
        VoxelShape result=Shapes.empty();int turns=TemplateShapeCache.turnsFrom(Direction.NORTH,state.getValue(MaterialTemplateBlock.FACING));
        for(TemplateBox b:boxes(state)){
            double x=b.minX(),z=b.minZ(),X=b.maxX(),Z=b.maxZ();
            for(int i=0;i<turns;i++){double a=x,A=X;x=16-Z;X=16-z;z=a;Z=A;}
            result=Shapes.or(result,Shapes.box(x/16,b.minY()/16,z/16,X/16,b.maxY()/16,Z/16));
        }
        return result.optimize();
    }
    private BalconyRailingProfile() {}
}
