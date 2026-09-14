package com.stardew.craft.combat.skill.handler;

import com.stardew.craft.combat.ResolvedWeaponHit;
import com.stardew.craft.combat.HeavyHammerCombatEvents;
import com.stardew.craft.combat.network.IronClubFxPayload;
import com.stardew.craft.combat.skill.*;
import com.stardew.craft.combat.skill.runtime.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import static com.stardew.craft.combat.skill.handler.IronClubRules.*;

public final class IronClubSkillHandler implements PostServerRuntimeWeaponSkillHandler {
    private final String skill;
    public IronClubSkillHandler(String skill){this.skill=skill;}
    @Override public SkillValidation validate(SkillExecutionContext c) {
        if(c.hand()!=InteractionHand.MAIN_HAND||!supports(c.weaponId().getPath(),skill)||!c.player().isAlive()
                ||c.player().isSpectator()||c.player().isPassenger()||WeaponSkillAnimationLock.isLocked(c.player(),c.nowTick())
                ||YetiFreezeTracker.isMovementLocked(c.player(),c.nowTick())||WeaponSkillRuntime.hasActive(c.player().getUUID(),c.skillId()))
            return SkillValidation.reject(SkillValidation.RejectionReason.INVALID_STATE);
        return WeaponSkillCooldowns.isOnCooldown(c.player(),c.weaponId().getPath(),skill,c.nowTick())
                ?SkillValidation.reject(SkillValidation.RejectionReason.COOLDOWN):SkillValidation.accept();
    }
    @Override public void begin(SkillExecutionContext c,SkillInstance i) {
        WeaponSkillRuntime.commitCooldown(c,i,cooldown(skill)*20);i.initializeExecutionState(new State(c));
        i.registerCommittedEffect(()->{
            WeaponSkillAnimationLock.setLock(c.player(),c.nowTick(),duration(skill));
            WeaponSkillAnimationDispatcher.sendSkillAnim(c.player(),c.weaponId().getPath(),skill,duration(skill));
        });
    }
    @Override public boolean completesImmediately(){return false;}
    @Override public SkillTickResult tick(SkillExecutionContext c,SkillInstance i){return i.requireExecutionState(State.class).valid(c)?SkillTickResult.CONTINUE:SkillTickResult.CANCEL;}
    @Override public SkillTickResult postServerTick(SkillExecutionContext c,SkillInstance i) {
        State s=i.requireExecutionState(State.class);if(!s.valid(c))return SkillTickResult.CANCEL;
        long age=c.nowTick()-s.started;
        if(!s.hit&&age>=hitTick(skill)) {
            s.hit=true; // One authored strike, including under re-entrant damage callbacks.
            Vec3 center=c.player().position();
            double reach=radius(skill);
            com.stardew.craft.event.MineBarrelBreakHandler.breakInVolume(c.player(),
                    new net.minecraft.world.phys.AABB(center.add(-reach,-1.5,-reach),center.add(reach,2.5,reach)),
                    center.add(0,.65,0), point -> {
                        Vec3 offset=point.subtract(center);
                        return offset.x*offset.x+offset.z*offset.z<=reach*reach
                                && inArc(skill,offset.dot(s.forward),offset.dot(s.side));
                    });
            for(var target:HeavyHammerExecutionState.targets(c,center,radius(skill))) {
                Vec3 offset=target.position().subtract(center);
                if(inArc(skill,offset.dot(s.forward),offset.dot(s.side)))
                    WeaponSkillDamage.apply(c.player(),target,damageContext(skill),c.weaponSnapshot(),c.nowTick()+2,
                            WeaponSkillDamage.AttackGatePolicy.RESPECT_AT_IMPACT,WeaponSkillDamage.HitCooldownPolicy.BYPASS_FOR_AUTHORED_SEQUENCE);
            }
            IronClubFxPayload.send(c.player(),skill,center,PRESS.equals(skill)?0:1,-1,s.yaw);
        }
        return age>=duration(skill)?SkillTickResult.COMPLETE:SkillTickResult.CONTINUE;
    }
    @Override public void finish(SkillExecutionContext c,SkillInstance i,SkillInstance.EndReason reason) {
        i.executionState(State.class).ifPresent(s->{s.canceled=true;if(c.nowTick()<s.started+duration(skill))WeaponSkillAnimationLock.clear(c.player());});
    }
    private static final class State implements SkillInstance.ExecutionState {
        final long started;final ItemStack held;final int slot;final Vec3 forward,side;final float yaw;
        boolean hit,canceled;
        State(SkillExecutionContext c){started=c.nowTick();held=c.player().getMainHandItem();slot=c.player().getInventory().selected;
            forward=HeavyHammerExecutionState.forward(c.player());side=new Vec3(-forward.z,0,forward.x);
            yaw=(float)Math.toDegrees(Math.atan2(-forward.x,forward.z));}
        boolean valid(SkillExecutionContext c){var p=c.player();return !canceled&&p.isAlive()&&!p.isSpectator()&&!p.isPassenger()
                &&p.getMainHandItem()==held&&p.getInventory().selected==slot&&!YetiFreezeTracker.isMovementLocked(p,c.nowTick());}
    }
    public static void appliedHit(ResolvedWeaponHit hit) {
        if(!hit.dealtPositiveDamage()||!(hit.attacker() instanceof ServerPlayer p))return;
        String id=hit.authoredSkillContext().getSkillId();if(!supports(hit.weaponIdentity().logicId(),id))return;
        var target=hit.target();boolean press=PRESS.equals(id);long now=hit.gameTick();
        if(target.isAlive()&&target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)<.8
                &&!YetiFreezeTracker.isMovementLocked(target,now)&&!WeaponSkillMovementControl.isLocked(target,now)) {
            int ticks=YetiFreezeTracker.applyWithEquipmentProtection(target,now,press?9:3,YetiFreezeTracker.PresentationPolicy.SERVER_ONLY_STAGGER);
            if(ticks>0&&!press)HeavyHammerCombatEvents.queuePush(target,p.position(),.35f,now+ticks);
        }
        IronClubFxPayload.send(p,id,target.getBoundingBox().getCenter(),press?2:3,target.getId(),p.getYRot());
    }
}
