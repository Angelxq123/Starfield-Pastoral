package com.stardew.craft.monster;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import java.util.*;

/** Shared Monster.MovePosition cardinal chase, corner scoots, trajectory and source tile path. */
public final class SourceGroundMovement {
    private final StardewMonsterEntity mob;private final double sourceSpeed;private double slipperiness;
    private List<SourceTilePath.Tile> path=List.of();private int pathIndex,direction=-1,facing=2,skip;
    private double slideX,slideZ,lastX,lastZ,pathPaused,pathX,pathZ;
    public SourceGroundMovement(StardewMonsterEntity mob,double speed,double slipperiness){this.mob=mob;sourceSpeed=speed;this.slipperiness=slipperiness;}
    public void slipperiness(double value){slipperiness=value;}
    public boolean hasPath(){return !path.isEmpty();}
    public void clearPath(){path=List.of();}
    public void halt(){direction=-1;}
    public int facing(){return facing;}
    public void direction(int d){direction=d;}
    public void randomize(double jitter){if(mob.getRandom().nextDouble()<jitter*1.8&&skip<=0){int d=mob.getRandom().nextInt(6);direction=d<4?d:-1;}}
    public boolean near(Player p,double range){return mob.getBoundingBox().getCenter().distanceToSqr(p.getBoundingBox().getCenter())<=range*range;}
    public void face(int d){facing=d;mob.setYRot(d*90-180);mob.yBodyRot=mob.getYRot();mob.setYHeadRot(mob.getYRot());}
    public void faceTarget(Player p){double dx=p.getX()-mob.getX(),dz=p.getZ()-mob.getZ();face(Math.abs(dx)>Math.abs(dz)?(dx>0?1:3):(dz>0?2:0));}
    public void knockback(double x,double z){if(Math.abs(x)>Math.abs(slideX))slideX=x;if(Math.abs(z)>Math.abs(slideZ))slideZ=z;}
    public void tick(Player p,boolean walking,int range,boolean focused,double jitter,Runnable animate){
        if(hasPath()){applySlide();followPath(animate);return;}
        var random=mob.getRandom();
        if(p!=null&&walking&&(focused||near(p,range))){
            if(skip<=0){if(mob.getX()==lastX&&mob.getZ()==lastZ&&random.nextDouble()<.001){direction=facing%2==1?(random.nextBoolean()?0:2):(random.nextBoolean()?1:3);skip=700;}else choose(p,mob.getX()==lastX,animate);}else skip-=16;
        }else if(!walking&&random.nextDouble()<jitter*1.8&&skip<=0){int d=random.nextInt(6);direction=d<4?d:-1;}
        lastX=mob.getX();lastZ=mob.getZ();
        applySlide();
        if(direction>=0)moveCardinal(direction,animate);
        if(walking&&p!=null&&near(p,range)&&mob.getX()==lastX&&mob.getZ()==lastZ){halt();faceTarget(p);}
    }
    private void applySlide(){if(slideX!=0||slideZ!=0){var v=new Vec3(slideX/64,0,slideZ/64);if(clearBox(mob.getBoundingBox().move(v)))mob.move(MoverType.SELF,v);slideX-=slideX/slipperiness;slideZ-=slideZ/slipperiness;if(Math.abs(slideX)<=.05)slideX=0;if(Math.abs(slideZ)<=.05)slideZ=0;}}
    private void choose(Player p,boolean horizontal,Runnable animate){
        double dx=p.getX()-mob.getX(),dz=p.getZ()-mob.getZ();int h=Math.abs(dx)>.25?(dx>0?1:3):-1,v=Math.abs(dz)>.25?(dz>0?2:0):-1;boolean chosen=false;
        for(int d:new int[]{horizontal?h:v,horizontal?v:h})if(d>=0){chosen=true;direction=d;if(clearBox(mob.getBoundingBox().move(step(d)))){skip=500;return;}moveCardinal(d,animate);}
        if(!chosen){halt();faceTarget(p);}
    }
    private Vec3 step(int d){double s=sourceSpeed*mob.getAttributeValue(Attributes.MOVEMENT_SPEED)/.25/64;return new Vec3(d==1?s:d==3?-s:0,0,d==2?s:d==0?-s:0);}
    private boolean clearBox(AABB box){var p=BlockPos.containing(box.getCenter().x,mob.getY()-.05,box.getCenter().z);var level=mob.level();return level.noCollision(mob,box)&&level.getFluidState(p).isEmpty()&&level.getFluidState(p.above()).isEmpty()&&!level.getBlockState(p).getCollisionShape(level,p).isEmpty();}
    private void moveCardinal(int d,Runnable animate){
        var delta=step(d);var box=mob.getBoundingBox().move(delta);
        if(clearBox(box)){mob.move(MoverType.SELF,delta);face(d);animate.run();return;}
        boolean vertical=d%2==0;double a=vertical?box.getXsize()/4:box.getZsize()/4;
        var first=vertical?new AABB(box.minX,box.minY,box.minZ,box.minX+a,box.maxY,box.maxZ):new AABB(box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.minZ+a);var last=vertical?first.move(a*3,0,0):first.move(0,0,a*3);
        boolean left=!clearBox(first),right=!clearBox(last);int side=left&&!right?(vertical?1:2):right&&!left?(vertical?3:0):-1;
        if(side>=0&&clearBox(mob.getBoundingBox().move(step(side))))mob.move(MoverType.SELF,step(side).scale(.25));halt();
    }
    /** Character.tryToMoveInDirection: one direct step, without the base corner-scoot helper. */
    public void tryDirect(int d){var v=step(d);if(clearBox(mob.getBoundingBox().move(v)))mob.move(MoverType.SELF,v);}
    /** DinoMonster's one-pixel recoil replaces only the firing axis. */
    public void breathRecoil(int facing){switch(facing){case 0->slideZ=1;case 1->slideX=-1;case 2->slideZ=-1;case 3->slideX=1;}}
    public void pathTo(Player p,int limit){var end=new SourceTilePath.Tile(p.getBlockX(),p.getBlockZ());path=SourceTilePath.find(new SourceTilePath.Tile(mob.getBlockX(),mob.getBlockZ()),end,limit,end::equals,t->clearBox(mob.getBoundingBox().move(t.x()+.5-mob.getX(),0,t.z()+.5-mob.getZ())));pathIndex=0;pathPaused=0;pathX=mob.getX();pathZ=mob.getZ();}
    private void followPath(Runnable animate){
        if(pathX==mob.getX()&&pathZ==mob.getZ())pathPaused+=1000./60;else pathPaused=0;pathX=mob.getX();pathZ=mob.getZ();if(pathPaused>5000){clearPath();halt();return;}
        var tile=path.get(pathIndex);double dx=tile.x()+.5-mob.getX(),dz=tile.z()+.5-mob.getZ();if(Math.abs(dx)<.1&&Math.abs(dz)<.1){if(++pathIndex==path.size()){clearPath();halt();}return;}direction=Math.abs(dx)>.1?(dx>0?1:3):(dz>0?2:0);moveCardinal(direction,animate);
    }
    public CompoundTag save(){var t=new CompoundTag();t.putInt("Direction",direction);t.putInt("Facing",facing);t.putInt("Skip",skip);t.putDouble("SlideX",slideX);t.putDouble("SlideZ",slideZ);t.putInt("PathIndex",pathIndex);t.putDouble("PathPaused",pathPaused);var a=new ListTag();for(var p:path){var n=new CompoundTag();n.putInt("X",p.x());n.putInt("Z",p.z());a.add(n);}t.put("Path",a);return t;}
    public void load(CompoundTag t){direction=t.contains("Direction")?t.getInt("Direction"):-1;face(t.getInt("Facing"));skip=t.getInt("Skip");slideX=t.getDouble("SlideX");slideZ=t.getDouble("SlideZ");pathIndex=t.getInt("PathIndex");pathPaused=t.getDouble("PathPaused");var a=new ArrayList<SourceTilePath.Tile>();for(var e:t.getList("Path",Tag.TAG_COMPOUND)){var p=(CompoundTag)e;a.add(new SourceTilePath.Tile(p.getInt("X"),p.getInt("Z")));}path=pathIndex<a.size()?List.copyOf(a):List.of();lastX=pathX=mob.getX();lastZ=pathZ=mob.getZ();}
}
