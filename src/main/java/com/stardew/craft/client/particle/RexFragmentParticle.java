package com.stardew.craft.client.particle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
/** Separate source bone chunks and single-pixel warm breath fragments. Visual debris only. */
public final class RexFragmentParticle extends TextureSheetParticle {
 public RexFragmentParticle(ClientLevel l,double x,double y,double z,double dx,double dy,double dz,SpriteSet s,boolean bone){super(l,x,y,z);setSprite(s.get(0,0));quadSize=bone?.5F:1F/32;gravity=.6F;friction=.92F;lifetime=bone?35:16;xd=dx;yd=Math.abs(dy)+.08;zd=dz;roll=random.nextFloat()*6.283185F;if(!bone){int[] colors={0xffea47,0xffffae,0xffa600,0xff771e};int c=colors[random.nextInt(colors.length)];setColor((c>>16&255)/255F,(c>>8&255)/255F,(c&255)/255F);}}
 @Override public void tick(){super.tick();oRoll=roll;roll+=.12F;}
 @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_OPAQUE;}
}
