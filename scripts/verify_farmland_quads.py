"""Execute production edge/cropping math headlessly against minimal renderer API stubs.
No game client, study directory or external Minecraft installation is required.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import subprocess
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'src/main/java/com/stardew/craft/client/model/terrain'
STUB='''package net.minecraft.client.renderer.block.model;
public class BakedQuad {
 private final int[] vertices; private final int type;
 public BakedQuad(int[] v,int t,Object d,Object s,boolean shade,boolean ao){vertices=v;type=t;}
 public int[] getVertices(){return vertices;} public int getTintIndex(){return type;}
 public Object getDirection(){return null;} public Object getSprite(){return null;}
 public boolean isShade(){return true;} public boolean hasAmbientOcclusion(){return true;}
}
'''
HARNESS='''package com.stardew.craft.client.model.terrain;
import net.minecraft.client.renderer.block.model.BakedQuad;
public class VerifyFarmland {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static float f(int n){return Float.intBitsToFloat(n);}
 public static void main(String[] args){
  int count=0;
  for(boolean wet:new boolean[]{false,true}) {
   BakedQuad[] sources=new BakedQuad[16];
   for(int t=0;t<16;t++) {
    int[] v=new int[32];int[][] corners={{0,1},{1,1},{1,0},{0,0}};
    for(int i=0;i<4;i++) {int x=corners[i][0],z=corners[i][1];v[i*8]=Float.floatToIntBits(x);v[i*8+1]=Float.floatToIntBits(15/16f);v[i*8+2]=Float.floatToIntBits(z);
     v[i*8+4]=Float.floatToIntBits(.75f-.25f*x);v[i*8+5]=Float.floatToIntBits(.125f+.5f*z);}
    sources[t]=new BakedQuad(v,t,null,null,true,true);
   }
   for(float y:new float[]{16.01f/16,16.02f/16}) {
    int[] original=sources[0].getVertices().clone();for(int i=0;i<4;i++)original[i*8+1]=Float.floatToIntBits(y);
    int[] lowered=TerrainFarmlandQuads.lowerOverlay(new BakedQuad(original,0,null,null,true,true)).getVertices();
    for(int i=0;i<original.length;i++) {
     if(i%8==1)check(Math.abs(f(lowered[i])-(y-1/16f))<.000001);
     else check(lowered[i]==original[i]);
    }
    check(f(original[1])==y);
   }
   TerrainFarmlandQuads painter=new TerrainFarmlandQuads(sources,wet);
   for(int soil=0;soil<256;soil++) for(int moisture=0;moisture<256;moisture++) {
    if(GrassConnectionMask.canonical(soil)!=soil || GrassConnectionMask.canonical(moisture)!=moisture)continue;
    int[] covered=new int[256];
    for(BakedQuad q:painter.get(soil,moisture)) {
     int[] v=q.getVertices();int minX=16,minZ=16,maxX=0,maxZ=0;
     for(int i=0;i<4;i++) {float x=f(v[i*8]),z=f(v[i*8+2]);check(f(v[i*8+1])==15/16f);
      check(Math.abs(f(v[i*8+4])-(.75f-.25f*x))<.000001);check(Math.abs(f(v[i*8+5])-(.125f+.5f*z))<.000001);
      int px=Math.round(x*16),pz=Math.round(z*16);minX=Math.min(minX,px);minZ=Math.min(minZ,pz);maxX=Math.max(maxX,px);maxZ=Math.max(maxZ,pz);}
     check(minX<maxX && minZ<maxZ);
     for(int z=minZ;z<maxZ;z++)for(int x=minX;x<maxX;x++) {
      check(q.getTintIndex()==TerrainFarmlandEdges.blendIndex(wet,soil,moisture,x,z));covered[z*16+x]++;
     }
    }
    for(int n:covered)check(n==1);count++;
   }
  }
  check(count==4418);
  for(int mask=0;mask<256;mask++)for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
   int w=TerrainFarmlandEdges.weight(mask,x,z);check(w>=0 && w<=3);
   check(w==TerrainFarmlandEdges.weight(GrassConnectionMask.canonical(mask),x,z));
   if(x>=4 && x<12 && z>=4 && z<12)check(w==0);
  }
  System.out.println("PASS: 4418 dry/wet edge combinations; complete non-overlapping 15/16-height faces, correct blend selection, affine UVs at 1px/unit and canonical masks.");
 }
}
'''
with TemporaryDirectory(prefix='farmland-quads-') as d:
 p=Path(d);(p/'BakedQuad.java').write_text(STUB);(p/'VerifyFarmland.java').write_text(HARNESS)
 subprocess.run(['javac','-d',d,str(p/'BakedQuad.java'),str(p/'VerifyFarmland.java')]+[str(SRC/n) for n in ['GrassConnectionMask.java','TerrainFarmlandEdges.java','TerrainFarmlandQuads.java']],check=True)
 subprocess.run(['java','-Xmx384m','-cp',d,'com.stardew.craft.client.model.terrain.VerifyFarmland'],check=True)
