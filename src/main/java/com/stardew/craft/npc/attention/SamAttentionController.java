package com.stardew.craft.npc.attention;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;

/** One server-owned reaction; clients receive one timestamped event through entity metadata. */
public final class SamAttentionController {
    private final StardewNpcEntity npc;
    private final NpcStareTracker tracker = new NpcStareTracker();
    private double targetX, targetZ;
    private int settledTicks;
    private static final int IDLE_SETTLE_TICKS = 10;
    private static final int REACTION_COOLDOWN_TICKS = 200;

    public SamAttentionController(StardewNpcEntity npc) { this.npc=npc; }

    public boolean enabled() {
        return npc.usesNativeAttention();
    }

    public void tick() {
        long now=npc.level().getGameTime();
        boolean available = !npc.isFacingOverrideActive()
                && !npc.isNativeActivityMovementLocked()
                && !NpcInteractionService.isDialogueMovementLocked(npc.getNpcId()) && !npc.isWalking()
                && npc.getNavigation().isDone() && npc.onGround() && npc.isAlive()
                && !npc.isInWaterOrBubble() && !npc.isPassenger()
                && npc.getDeltaMovement().horizontalDistanceSqr()<1.0E-5;
        settledTicks=available ? Math.min(IDLE_SETTLE_TICKS,settledTicks+1) : 0;
        if (npc.isAttentionActive()) {
            var event=npc.getAttentionEvent();
            var target=npc.level().getEntity(event.getInt("target"));
            if (!available || !(target instanceof Player player) || !player.isAlive() || player.isSpectator()
                    || player.distanceToSqr(npc)>49 || !player.hasLineOfSight(npc)
                    || Math.hypot(player.getX()-targetX,player.getZ()-targetZ)>.8) cancel();
            // Schedule facing must remain stable while the visual body takes its steps.
            if (available) {
                float yaw=event.getFloat("baseYaw");
                npc.setYRot(yaw); npc.setYBodyRot(yaw); npc.setYHeadRot(yaw);
            }
            tracker.tick(now,java.util.List.of(),false);
            return;
        }
        var candidates=new ArrayList<NpcStareTracker.Candidate>();
        boolean idleReady=available && settledTicks>=IDLE_SETTLE_TICKS;
        if (idleReady) for (Player player:npc.level().players()) {
            double distance=player.distanceToSqr(npc);
            if (!player.isAlive() || player.isSpectator() || distance>36 || distance<.49) continue;
            var eye=player.getEyePosition();
            var end=eye.add(player.getViewVector(1).scale(6));
            if (npc.getBoundingBox().inflate(.10).clip(eye,end).isEmpty() || !player.hasLineOfSight(npc)) continue;
            candidates.add(new NpcStareTracker.Candidate(player.getId(),distance));
        }
        int id=tracker.tick(now,candidates,idleReady);
        if (id<0 || !(npc.level().getEntity(id) instanceof Player player)) return;
        var event=createEvent(player,now);
        float yaw=event.getFloat("yaw");
        npc.setAttentionEvent(event);
        targetX=player.getX(); targetZ=player.getZ();
        tracker.coolDownUntil(now+(long)Math.ceil(NpcAttentionMotion.duration(yaw)*20)+REACTION_COOLDOWN_TICKS);
    }

    private CompoundTag createEvent(Player player, long now) {
        double dx=player.getX()-npc.getX(), dz=player.getZ()-npc.getZ();
        float yaw=-Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx,dz))-npc.yBodyRot);
        double eyeHeight=npc.getEyeHeight();
        float pitch=(float)Math.toDegrees(Math.atan2(player.getEyeY()-(npc.getY()+eyeHeight),Math.hypot(dx,dz)));
        var event=new CompoundTag();
        event.putLong("start",now); event.putInt("target",player.getId());
        if ("george".equals(npc.getNpcId())) event.putBoolean("wheelchair",true);
        event.putFloat("yaw",yaw); event.putFloat("pitch",pitch); event.putFloat("baseYaw",npc.yBodyRot);
        event.putFloat("distance",(float)Math.hypot(dx,dz)*16);
        return event;
    }

    public void beginDialogue(Player player) {
        long now=npc.level().getGameTime();
        var previous=npc.getAttentionEvent();
        // Clicking the player already being watched extends that same turn, without restarting it.
        boolean continueTurn=isActive(previous,now) && previous.getInt("target")==player.getId()
                && !previous.contains("cancel");
        var event=continueTurn ? previous.copy() : createEvent(player,now);
        if (continueTurn && motionTime(previous,now)>=NpcAttentionMotion.holdEnd(previous.getFloat("yaw"))) {
            // Reverse the existing return path from its exact current pose, then hold for the chat.
            event.putDouble("resumeFrom",motionTime(previous,now));
            event.putLong("start",now);
            event.remove("release");
            event.remove("entry");
        }
        event.putBoolean("dialogue",true);
        if (!continueTurn && isActive(previous,now)) {
            var entry=previous.copy();
            entry.remove("entry");
            event.put("entry",entry);
            event.putLong("entryTime",now);
        }
        npc.setAttentionEvent(event);
        settledTicks=0;
    }

    public void releaseDialogue() {
        var event=npc.getAttentionEvent();
        if (!event.getBoolean("dialogue") || event.contains("release")) return;
        long now=npc.level().getGameTime();
        var copy=event.copy(); copy.putLong("release",now);
        npc.setAttentionEvent(copy);
        double remaining=NpcAttentionMotion.duration(event.getFloat("yaw"))-NpcAttentionMotion.holdEnd(event.getFloat("yaw"));
        tracker.coolDownUntil(now+(long)Math.ceil(remaining*20)+REACTION_COOLDOWN_TICKS);
    }

    public static double motionTime(CompoundTag event, double now) {
        double age=(now-event.getLong("start"))/20;
        if (event.getBoolean("dialogue")) {
            double release=event.contains("release") ? (event.getLong("release")-event.getLong("start"))/20.0 : -1;
            return NpcDialogueMotion.motionTime(age,event.getFloat("yaw"),release,
                    event.contains("resumeFrom") ? event.getDouble("resumeFrom") : -1);
        }
        return event.contains("cancel") ? Math.min(age,(event.getLong("cancel")-event.getLong("start"))/20.0) : age;
    }

    public static double dialogueReadyTime(CompoundTag event) {
        return NpcDialogueMotion.readyTime(event.getFloat("yaw"),
                event.contains("resumeFrom") ? event.getDouble("resumeFrom") : -1);
    }

    public static boolean isActive(CompoundTag event, double now) {
        if (event.isEmpty() || now<event.getLong("start")) return false;
        if (event.contains("cancel")) return now-event.getLong("cancel")<5;
        return motionTime(event,now)<NpcAttentionMotion.duration(event.getFloat("yaw"));
    }

    public static NpcAttentionMotion.Sample sample(CompoundTag event, double now) {
        return sample(event,now,NpcAttentionMotion.SAM);
    }

    public static NpcAttentionMotion.Sample sample(CompoundTag event, double now, NpcAttentionMotion.Rig rig) {
        var result=sampleMotion(event,motionTime(event,now),rig);
        double takeover=(now-event.getLong("start"))/7.0;
        if (event.contains("entry") && takeover<1) {
            var entry=event.getCompound("entry");
            double entryTime=event.getLong("entryTime");
            var from=sampleMotion(entry,motionTime(entry,entryTime),rig);
            if (entry.contains("cancel")) from=NpcDialogueMotion.blend(NpcAttentionMotion.sample(0,0,0,64,rig),from,
                    1-NpcAttentionMotion.smooth((entryTime-entry.getLong("cancel"))/5));
            result=NpcDialogueMotion.blend(from,result,NpcAttentionMotion.smooth(takeover));
        }
        return result;
    }

    private static NpcAttentionMotion.Sample sampleMotion(CompoundTag event,double time,NpcAttentionMotion.Rig rig) {
        return event.getBoolean("wheelchair")
                ? NpcWheelchairAttentionMotion.sample(time,event.getFloat("yaw"),event.getFloat("pitch"),event.getFloat("distance"))
                : NpcAttentionMotion.sample(time,event.getFloat("yaw"),event.getFloat("pitch"),event.getFloat("distance"),rig);
    }

    public void cancel() {
        var event=npc.getAttentionEvent();
        if (event.isEmpty() || event.contains("cancel")) return;
        var copy=event.copy(); copy.putLong("cancel",npc.level().getGameTime());
        npc.setAttentionEvent(copy);
    }
}
