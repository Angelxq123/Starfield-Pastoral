package com.stardew.craft.client.particle;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
/** DinoMonster's HotPink row44 cloud, holding its final frame and fading from the first update. */
public final class RexDissolveParticle extends TextureSheetParticle {
 private final SpriteSet sprites;
 public RexDissolveParticle(ClientLevel l,double x,double y,double z,SpriteSet s){super(l,x,y,z);sprites=s;lifetime=34;quadSize=.5F;hasPhysics=false;xd=yd=zd=0;setColor(1,105/255F,180/255F);setSprite(s.get(0,9));}
 @Override public void tick(){xo=x;yo=y;zo=z;if(++age>=lifetime)remove();}
 @Override public void render(VertexConsumer b,Camera c,float p){float a=1-(age+p)*.03F;if(a<=0)return;setAlpha(a);setSprite(sprites.get(Math.min(9,(int)((age+p)*50/70)),9));super.render(b,c,p);}
 @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
}
