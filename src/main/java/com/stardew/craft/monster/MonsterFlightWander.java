package com.stardew.craft.monster;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** MC-only idle intent. Destinations stay stable, with bounded travel and deliberate pauses. */
public final class MonsterFlightWander {
    private Vec3 anchor, destination;
    private int remaining, pause=20;
    public void reset(){anchor=destination=null;pause=20;}
    public Vec3 next(StardewMonsterEntity mob,double radius,double verticalRange){
        if(anchor==null||anchor.distanceToSqr(mob.position())>144)anchor=mob.position();
        if(pause>0){pause--;return null;}
        if(destination!=null){
            if(--remaining>0&&mob.position().distanceToSqr(destination)>.36)return destination;
            destination=null;pause=15+mob.getRandom().nextInt(26);return null;
        }
        for(int attempt=0;attempt<12;attempt++){
            var random=mob.getRandom();
            Vec3 candidate=anchor.add((random.nextDouble()*2-1)*radius,(random.nextDouble()*2-1)*verticalRange,(random.nextDouble()*2-1)*radius);
            var box=mob.getBoundingBox().move(candidate.subtract(mob.position())).inflate(.025);
            if(MonsterSpace.loaded(mob,box)&&mob.level().noCollision(mob,box)&&!mob.level().containsAnyLiquid(box)){
                destination=candidate;remaining=160;return candidate;
            }
        }
        pause=30;return null;
    }
    public CompoundTag save(){var tag=new CompoundTag();if(anchor!=null){tag.putDouble("X",anchor.x);tag.putDouble("Y",anchor.y);tag.putDouble("Z",anchor.z);}return tag;}
    public void load(CompoundTag tag){anchor=tag.contains("X")?new Vec3(tag.getDouble("X"),tag.getDouble("Y"),tag.getDouble("Z")):null;destination=null;pause=20;}
}
