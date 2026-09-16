package com.stardew.craft.client.particle;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
/** TemporaryAnimatedSprite row44, holdLastFrame, immediate alphaFade .01 per60Hz update. */
public final class MummyDissolveParticle extends TextureSheetParticle {
 private final SpriteSet sprites;
 public MummyDissolveParticle(ClientLevel level,double x,double y,double z,SpriteSet sprites){super(level,x,y,z);this.sprites=sprites;lifetime=34;quadSize=.5F;hasPhysics=false;xd=yd=zd=0;setColor(138/255F,43/255F,226/255F);setSprite(sprites.get(0,9));}
 @Override public void tick(){xo=x;yo=y;zo=z;if(++age>=lifetime)remove();}
 @Override public void render(VertexConsumer buffer,Camera camera,float p){float alpha=1-(age+p)*.03F;if(alpha<=0)return;setAlpha(alpha);setSprite(sprites.get(Math.min(9,(int)((age+p)*50/70)),9));super.render(buffer,camera,p);}
 @Override public ParticleRenderType getRenderType(){return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;}
 @Override protected int getLightColor(float p){return 15728880;}
}
