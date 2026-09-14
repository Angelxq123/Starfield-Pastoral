"""Verify packaged art and execute the real client cache with minimal headless Minecraft stubs."""
from pathlib import Path
from PIL import Image
from tempfile import TemporaryDirectory
import json, subprocess

ROOT=Path(__file__).resolve().parents[1]
A=ROOT/'src/main/resources/assets/stardewcraft'
T=A/'textures/block/terrain/fertilized'
manifest=json.loads((A/'fertilized_farmland_manifest.json').read_text())
names=manifest['names'];assert len(names)==9
for season in manifest['seasons']:
    atlas=Image.open(T/(season+'.png')).convert('RGBA');assert atlas.size==(144,320)
    earth=Image.open(A/'textures/block/terrain'/('' if season=='spring' else season)/'yellow_earth_top.png').convert('RGBA')
    for i,name in enumerate(names):
        for row in range(20):
            tile=atlas.crop((i*16,row*16,i*16+16,row*16+16))
            assert set(tile.getchannel('A').getdata())=={255}
            if row%4==3:assert tile.tobytes()==earth.tobytes()
        for wet in [False,True]:
            state='wet' if wet else 'dry'
            image=Image.open(T/season/(name+'_'+state+'.png')).convert('RGBA')
            base=Image.open(A/'textures/block/terrain/farmland'/season/(state+'.png')).convert('RGBA')
            assert image.size==(16,16)
            changed={(x,y) for y in range(16) for x in range(16) if image.getpixel((x,y))!=base.getpixel((x,y))}
            marks={(x,y) for x,y,c in manifest['marks'][i]}
            assert changed and changed<=marks and all(2<=x<=13 and 2<=y<=13 for x,y in changed)
            row=16 if wet else 0
            assert image.tobytes()==atlas.crop((i*16,row*16,i*16+16,row*16+16)).tobytes()
for i,name in enumerate(names):
    for state in ['dry','wet']:
        vanilla=Image.open(T/'vanilla'/(name+'_'+state+'.png')).convert('RGBA')
        base=Image.open(T/'vanilla_base'/(state+'.png')).convert('RGBA')
        marks={(x,y) for x,y,c in manifest['marks'][i]}
        for y in range(16):
            for x in range(16):
                if (x,y) not in marks:assert vanilla.getpixel((x,y))==base.getpixel((x,y))
        pot=Image.open(T/'pot'/(name+'_'+state+'.png')).convert('RGBA')
        base=Image.open(A/'textures/block/utility'/('garden_pot_watered.png' if state=='wet' else 'garden_pot.png')).convert('RGBA')
        marks={(15-x,15-y) for x,y,c in manifest['marks'][i]}
        assert pot.size==base.size==(64,64)
        for y in range(64):
            for x in range(64):
                if (x,y) not in marks:assert pot.getpixel((x,y))==base.getpixel((x,y))
assert not (A/'blockstates/fertilizer_layer.json').exists()
assert not (ROOT/'src/main/java/com/stardew/craft/client/render/FertilizerOverlayRenderer.java').exists()
print('PASS: all 72 seasonal surfaces, 720 opaque cells, original seams, dirt transition endpoints, vanilla/pot identity and removed overlay resources.')

STUBS={
'net/minecraft/resources/ResourceKey.java':'''package net.minecraft.resources;public record ResourceKey<T>(String name){}''',
'net/minecraft/core/BlockPos.java':'''package net.minecraft.core;public record BlockPos(int x,int y,int z){public int getX(){return x;}public int getY(){return y;}public int getZ(){return z;}public BlockPos immutable(){return this;}}''',
'net/minecraft/world/level/ChunkPos.java':'''package net.minecraft.world.level;import net.minecraft.core.BlockPos;public class ChunkPos{public final int x,z;public ChunkPos(int x,int z){this.x=x;this.z=z;}public ChunkPos(BlockPos p){this(p.getX()>>4,p.getZ()>>4);}public long toLong(){return asLong(x,z);}public static long asLong(int x,int z){return (x&0xffffffffL)|((long)z<<32);}public static long asLong(BlockPos p){return asLong(p.getX()>>4,p.getZ()>>4);}public static int getX(long n){return (int)n;}public static int getZ(long n){return (int)(n>>32);}}''',
'net/minecraft/world/level/Level.java':'''package net.minecraft.world.level;import net.minecraft.resources.ResourceKey;public class Level{public static final ResourceKey<Level> OVERWORLD=new ResourceKey<>("overworld"),NETHER=new ResourceKey<>("nether");private final ResourceKey<Level> key;public Level(ResourceKey<Level> key){this.key=key;}public ResourceKey<Level> dimension(){return key;}}''',
'net/minecraft/client/Minecraft.java':'''package net.minecraft.client;import net.minecraft.world.level.Level;public class Minecraft{private static final Minecraft INSTANCE=new Minecraft();public Level level=new Level(Level.OVERWORLD);public final Renderer levelRenderer=new Renderer();public static Minecraft getInstance(){return INSTANCE;}public static class Renderer{public int dirty,all;public void setBlocksDirty(int a,int b,int c,int d,int e,int f){dirty++;}public void allChanged(){all++;}}}''',
'net/minecraft/util/StringRepresentable.java':'''package net.minecraft.util;public interface StringRepresentable{String getSerializedName();}''',
'javax/annotation/Nullable.java':'''package javax.annotation;public @interface Nullable{}''',
}
HARNESS='''import com.stardew.craft.client.ClientFertilizerCache;
import com.stardew.craft.block.FertilizerType;import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;import net.minecraft.world.level.*;import java.util.*;
public class VerifyCache{
 static void check(boolean value){if(!value)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  var mc=Minecraft.getInstance();var p=new BlockPos(-1,64,-1);var q=new BlockPos(-2,64,-1);var far=new BlockPos(99,64,99);
  var a=FertilizerType.BASIC_FERTILIZER;var b=FertilizerType.HYPER_SPEED_GRO;
  ClientFertilizerCache.clear();
  ClientFertilizerCache.setFertilizer(Level.OVERWORLD,p,a);check(mc.levelRenderer.dirty==1);
  ClientFertilizerCache.setFertilizer(Level.OVERWORLD,p,a);check(mc.levelRenderer.dirty==1);
  ClientFertilizerCache.setFertilizer(Level.NETHER,p,b);check(mc.levelRenderer.dirty==1);
  var snapshot=ClientFertilizerCache.snapshot(Level.OVERWORLD);
  ClientFertilizerCache.replaceChunk(Level.OVERWORLD,new ChunkPos(p),Map.of(q,b,far,a));
  check(mc.levelRenderer.dirty==3);check(ClientFertilizerCache.getFertilizer(Level.OVERWORLD,p)==null);
  check(ClientFertilizerCache.getFertilizer(Level.OVERWORLD,q)==b);check(ClientFertilizerCache.getFertilizer(Level.OVERWORLD,far)==null);
  check(snapshot.equals(Map.of(p,a)));check(ClientFertilizerCache.getFertilizer(Level.NETHER,p)==b);
  ClientFertilizerCache.replaceChunk(Level.OVERWORLD,new ChunkPos(p),Map.of(q,b));check(mc.levelRenderer.dirty==3);
  ClientFertilizerCache.replaceChunk(Level.OVERWORLD,new ChunkPos(p),Map.of());check(mc.levelRenderer.dirty==4);
  ClientFertilizerCache.removeFertilizer(Level.OVERWORLD,q);check(mc.levelRenderer.dirty==4);
  var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
  Thread worker=new Thread(()->{try{for(int i=0;i<30000;i++){ClientFertilizerCache.getFertilizer(Level.OVERWORLD,p);ClientFertilizerCache.snapshot(Level.OVERWORLD);}}catch(Throwable t){failure.set(t);}});
  worker.start();for(int i=0;i<5000;i++){ClientFertilizerCache.setFertilizer(Level.OVERWORLD,p,a);ClientFertilizerCache.removeFertilizer(Level.OVERWORLD,p);}worker.join();check(failure.get()==null);
  ClientFertilizerCache.setFertilizer(Level.OVERWORLD,p,a);ClientFertilizerCache.clear();check(mc.levelRenderer.all==1);
  System.out.println("PASS: cache change/removal/snapshot invalidation, unchanged Jade updates, dimension isolation, immutable snapshots and concurrent model reads.");
 }
}'''
with TemporaryDirectory(prefix='fertilized-cache-') as folder:
    out=Path(folder);files=[]
    for name,source in STUBS.items():
        path=out/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source);files.append(str(path))
    path=out/'VerifyCache.java';path.write_text(HARNESS);files.append(str(path))
    files += [str(ROOT/'src/main/java/com/stardew/craft'/name) for name in ['client/ClientFertilizerCache.java','block/FertilizerType.java']]
    subprocess.run(['javac','-d',folder]+files,check=True)
    subprocess.run(['java','-cp',folder,'VerifyCache'],check=True)
