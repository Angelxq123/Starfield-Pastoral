package com.stardew.craft.monster;
import com.stardew.craft.mining.OrdinaryMineLayout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
/** Spawn-time placement only; never teleports live flyers through obstacles. */
public final class MonsterFlightPlacement {
    private MonsterFlightPlacement() {}
    public static void ensureSpawnAir(StardewMonsterEntity mob){
  var original=mob.position();var body=mob.getBoundingBox().inflate(.025);
  var context=mob.monsterState().context();
  if(context.generation()!=null){
   var layout=OrdinaryMineLayout.load((ServerLevel)mob.level(),context.floor());
   var origin=layout.origin(context.floor()).offset(layout.tileX,0,layout.tileZ);
   var cell=layout.cell(mob.getBlockX()-origin.getX(),mob.getBlockZ()-origin.getZ());
   if(cell==null||!cell.reachable()||!MonsterSpace.loaded(mob,body)||!mob.level().noCollision(mob,body)){
    Vec3 best=null;double nearest=Double.MAX_VALUE;
    for(var candidate:layout.cells)if(candidate.reachable()&&!layout.blocksSight(candidate.x(),candidate.z())){
     for(int y=0;y<=3;y++){
      Vec3 point=Vec3.atBottomCenterOf(layout.position(context.floor(),candidate.x(),candidate.z())).add(0,.75+y,0);
      double distance=original.distanceToSqr(point);if(distance>=nearest)continue;
      var box=body.move(point.subtract(original));
      if(MonsterSpace.loaded(mob,box)&&mob.level().noCollision(mob,box)&&!mob.level().containsAnyLiquid(box)){best=point;nearest=distance;}
     }
    }
    if(best!=null)mob.setPos(best);else MonsterFactory.cleanup(mob);
    return;
   }
  }
  if(MonsterSpace.loaded(mob,body)&&mob.level().noCollision(mob,body))return;
  // Source off-screen reinforcements can start behind a template wall. Choose nearby
  // loaded air once, at spawn; never enable wall-phasing to escape an invalid point.
  Vec3 best=null;double distance=Double.MAX_VALUE;
  for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=-2;y<=5;y++){
   Vec3 offset=new Vec3(x,y,z);double d=offset.lengthSqr();if(d>=distance)continue;
   var candidate=body.move(offset);
   if(MonsterSpace.loaded(mob,candidate)&&mob.level().noCollision(mob,candidate)&&!mob.level().containsAnyLiquid(candidate)){
    best=original.add(offset);distance=d;
   }
  }
  if(best!=null)mob.setPos(best);else MonsterFactory.cleanup(mob);
 }
}
