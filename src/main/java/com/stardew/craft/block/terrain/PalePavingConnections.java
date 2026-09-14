package com.stardew.craft.block.terrain;
import com.stardew.craft.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
public final class PalePavingConnections {
 private static final int[][] O={{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
 private PalePavingConnections(){}

 /** A grass edge already covers its corners; keep a diagonal only when both adjacent edges are absent. */
 public static int canonical(int mask) {
  for (int i=0;i<4;i++) {
   int adjacent=(1<<i)|(1<<((i+1)%4));
   if ((mask&adjacent)!=0) mask&=~(1<<(i+4));
  }
  return mask&255;
 }

 public static int mask(BlockGetter l,BlockPos p){int r=0;for(int i=0;i<O.length;i++){var b=l.getBlockState(p.offset(O[i][0],0,O[i][1])).getBlock();if(b==ModBlocks.GRASS_BLOCK.get()||b==ModBlocks.DARK_GRASS_BLOCK.get())r|=1<<i;}return canonical(r);}
}
