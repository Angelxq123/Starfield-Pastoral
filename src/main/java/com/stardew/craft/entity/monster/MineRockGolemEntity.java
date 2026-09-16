package com.stardew.craft.entity.monster;
import com.stardew.craft.monster.*;
import com.stardew.craft.mining.OrdinaryMineRuntime;
import com.stardew.craft.combat.MonsterStats;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
/** Ordinary RockGolem only. Wilderness/Iridium farm constructors and dangerous mode stay separate. */
@SuppressWarnings("null")
public final class MineRockGolemEntity extends StardewMonsterEntity {
    public static final float WIDTH=.9F,HEIGHT=1.48F;
    private static final EntityDataAccessor<Boolean> MOVING=SynchedEntityData.defineId(MineRockGolemEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> RISE=SynchedEntityData.defineId(MineRockGolemEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> HIT=SynchedEntityData.defineId(MineRockGolemEntity.class,EntityDataSerializers.LONG);
    private final SourceGroundMovement movement=new SourceGroundMovement(this,2,2);
    private final RockGolemAwakening waking=new RockGolemAwakening();private double fallSpeed;private int stunMilliseconds;
    public MineRockGolemEntity(EntityType<? extends MineRockGolemEntity> type,Level level){super(type,level);addTag("sd_mob_rock_golem");refreshDimensions();}
    public static AttributeSupplier.Builder createAttributes(){return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,45).add(Attributes.ATTACK_DAMAGE,5).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,64).add(Attributes.STEP_HEIGHT,0);}
    @Override protected void registerGoals(){}
    @Override protected ResourceLocation definitionId(){return ResourceLocation.parse("stardewcraft:rock_golem");}
    @Override protected void configureSpawn(MonsterDefinition d,MonsterSpawnContext c){
        var r=MonsterStatResolver.base(d,c,random);var base=r.combat();double health=c.floor()>80?2.5:c.floor()>40?1.75:1,damage=c.floor()>80?2:c.floor()>40?1.5:1;
        setInitialHealth((int)(r.initialHealth()*health));replaceCombatStats(MonsterStats.builder().damage((int)(base.getDamage()*damage)).resilience(base.getResilience()).missChance(base.getMissChance()).experience(base.getExperience()).build());movement.face(2);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(MOVING,false);b.define(RISE,0F);b.define(HIT,-100L);}
    @Override public EntityDimensions getDefaultDimensions(Pose pose){return EntityDimensions.scalable(WIDTH,1.15F+(HEIGHT-1.15F)*entityData.get(RISE));}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(RISE.equals(key))refreshDimensions();}
    public boolean moving(){return entityData.get(MOVING);}
    public double riseProgress(float p){return Math.min(1,entityData.get(RISE)+(phase()==1?p/12.:0));}
    public double hitTime(float p){return (level().getGameTime()-entityData.get(HIT)+p)/20.;}
    public RockGolemAwakening awakening(){return waking;}
    public void stunFor(int milliseconds){stunMilliseconds=Math.max(stunMilliseconds,milliseconds);}
    private boolean valid(Player p){return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!p.hasEffect(ModMobEffects.AVOID_MONSTERS)&&(monsterState().context().generation()==null||OrdinaryMineRuntime.floorAt(p.blockPosition())==monsterState().context().floor());}
    @Override protected void customServerAiStep(){
        if(!initialized())initialize(MonsterSpawnContext.capture((ServerLevel)level(),MonsterSpawnContext.Source.WORLD,1));var target=level().getNearestPlayer(getX(),getY(),getZ(),64,e->e instanceof Player p&&valid(p));setTarget(target);double x=getX(),z=getZ();
        for(int i=0;i<3;i++){
            if(stunMilliseconds<=0){
                movement.tick(target,waking.walking(),waking.awake()?16:3,waking.focused(),waking.awake()?.01:0,waking::walkFrame);
                if(target!=null){boolean seen=waking.seen(),unfolding=seen&&!waking.awake();if(waking.step(waking.focused()||movement.near(target,3)))playSound(ModSounds.ROCK_GOLEM_SPAWN.get(),1,sourcePitch());
                    if(seen&&!unfolding&&waking.walking()&&random.nextDouble()<.001&&distanceToSqr(target)<400)movement.pathTo(target,200);
                }
            }else stunMilliseconds=Math.max(0,stunMilliseconds-16);
            if(target!=null&&movement.near(target,3))movement.clearPath();startAction(waking.awake()?2:waking.seen()?1:0,false);entityData.set(RISE,(float)waking.progress());
            for(var p:((ServerLevel)level()).players())if(valid(p)&&getBoundingBox().intersects(p.getBoundingBox())){var attack=MonsterDamageSource.contact(this);p.hurt(attack,attack.baseDamage());}
        }
        entityData.set(MOVING,x!=getX()||z!=getZ());
    }
    @Override public boolean hurt(DamageSource s,float amount){if(!level().isClientSide)waking.struck();float hp=getHealth();boolean hit=super.hurt(s,amount);if(!level().isClientSide&&getHealth()<hp)entityData.set(HIT,level().getGameTime());return hit;}
    @Override public void knockback(double strength,double x,double z){super.knockback(strength,x,z);var v=getDeltaMovement();movement.knockback(v.x*64,v.z*64);setDeltaMovement(Vec3.ZERO);}
    private float sourcePitch(){return (float)Math.pow(2,-.1+random.nextDouble()*.467);}
    @Override public float getVoicePitch(){return sourcePitch();}
    @Override protected SoundEvent getHurtSound(DamageSource s){return ModSounds.ROCK_GOLEM_HIT.get();}
    @Override protected SoundEvent getDeathSound(){return ModSounds.ROCK_GOLEM_DIE.get();}
    @Override protected void onFinalDeath(DamageSource s){((ServerLevel)level()).sendParticles(new DustParticleOptions(new Vector3f(.23F,.27F,.26F),.7F),getX(),getY()+.4,getZ(),4+random.nextInt(5),.35,.4,.35,.09);}
    @Override public void travel(Vec3 v){fallSpeed=isNoGravity()?0:onGround()?-.08:Math.max(-3.9,(fallSpeed-.08)*.98);move(MoverType.SELF,new Vec3(0,fallSpeed,0));setDeltaMovement(Vec3.ZERO);}
    @Override public boolean isPushable(){return false;}
    @Override public void push(Entity e){}
    @Override public boolean causeFallDamage(float d,float m,DamageSource s){return false;}
    @Override public void addAdditionalSaveData(CompoundTag t){super.addAdditionalSaveData(t);t.put("GolemAwakening",waking.save());t.put("GroundMovement",movement.save());t.putInt("GolemStun",stunMilliseconds);}
    @Override public void readAdditionalSaveData(CompoundTag t){super.readAdditionalSaveData(t);waking.load(t.getCompound("GolemAwakening"));movement.load(t.getCompound("GroundMovement"));stunMilliseconds=t.getInt("GolemStun");entityData.set(RISE,(float)waking.progress());}
}
