package com.stardew.craft.templates;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StairsShape;

/** Exterior trim follows actual touching profiles, including the next height step. */
public final class RoofTemplateEdges {
    public static int exposed(BlockGetter level, BlockPos pos, BlockState state) {
        return 15 ^ connected(level,pos,state,false);
    }

    /** A glass neighbor must not remove the opaque roof section seen through it. */
    public static int hiddenSections(BlockGetter level, BlockPos pos, BlockState state) {
        return connected(level,pos,state,true);
    }

    private static int connected(BlockGetter level,BlockPos pos,BlockState state,boolean requireOcclusion) {
        int mask=0;
        var own=TemplateMaterials.effectiveMaterial(level,pos);
        Direction[] sides={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
        for(int side=0;side<4;side++) {
            Direction direction=sides[side];
            for(int dy=-1;dy<=1;dy++) {
                var at=pos.relative(direction).offset(0,dy,0);
                BlockState neighbor=level.getBlockState(at);
                if(!(neighbor.getBlock() instanceof RoofTemplateBlock)
                        || neighbor.getValue(MaterialTemplateBlock.FLIPPED)!=state.getValue(MaterialTemplateBlock.FLIPPED)) continue;
                if(requireOcclusion) {
                    var other=TemplateMaterials.effectiveMaterial(level,at);
                    if(!other.canOcclude() && !(own==other && own.skipRendering(other,direction))) continue;
                }
                boolean joins=true;
                for(int i=0;i<=16;i++) {
                    float t=i/16F;
                    float x=direction==Direction.WEST?0:direction==Direction.EAST?1:t;
                    float z=direction==Direction.NORTH?0:direction==Direction.SOUTH?1:t;
                    if(Math.abs(height(state,x,z)-dy-height(neighbor,x-direction.getStepX(),z-direction.getStepZ()))>1E-5) { joins=false;break; }
                    if(i<16) {
                        float mid=(i+0.5F)/16F;
                        float sx=direction.getAxis()==Direction.Axis.X?x:mid;
                        float sz=direction.getAxis()==Direction.Axis.Z?z:mid;
                        boolean a=hasSurface(state,sx-direction.getStepX()/1024F,sz-direction.getStepZ()/1024F);
                        boolean b=hasSurface(neighbor,sx-direction.getStepX()+direction.getStepX()/1024F,
                                sz-direction.getStepZ()+direction.getStepZ()/1024F);
                        if(!a || !b) {joins=false;break;}
                    }
                }
                if(joins) {mask|=1<<side;break;}
            }
        }
        return mask;
    }

    private static boolean hasSurface(BlockState state,float x,float z) {
        float y=height(state,x,z);
        return (state.getValue(MaterialTemplateBlock.FLIPPED)?1-y:y)>1E-6F;
    }

    private static float height(BlockState state,float x,float z) {
        var block=(RoofTemplateBlock)state.getBlock();
        var shape=block.templateShape();
        int turns=shape.roofForm().usesFacing()?TemplateShapeCache.turnsFrom(shape.baseFacing(),state.getValue(MaterialTemplateBlock.FACING)):0;
        for(int i=0;i<turns;i++){float old=x;x=z;z=1-old;}
        var corner=state.hasProperty(SmartRoofTemplateBlock.ROOF_SHAPE)?state.getValue(SmartRoofTemplateBlock.ROOF_SHAPE):StairsShape.STRAIGHT;
        float h=shape.roofForm().collisionHeight(x,z,corner,SmartRidgeTemplateBlock.connectionMask(state));
        return state.getValue(MaterialTemplateBlock.FLIPPED)?1-h:h;
    }
    private RoofTemplateEdges() {}
}
