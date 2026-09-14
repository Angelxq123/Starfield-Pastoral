package com.stardew.craft.api.v1.npc;

import com.stardew.craft.api.v1.internal.npc.StardewNpcExecutionRegistry;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.npc.runtime.NpcExecutionCoordinator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.UUID;

/** Experimental server-thread hooks for schedule conditions, furniture and temporary actor control. */
public final class StardewNpcExecution {
    private StardewNpcExecution() {}
    public record State(ResourceLocation npcId,Optional<UUID> entityId,String owner,String phase,
                        Optional<Vec3> position,Optional<Vec3> target) {}
    public static State inspect(ServerLevel level,ResourceLocation npcId) {
        var entity=StardewNpcEntities.resolve(level,npcId).orElse(null);
        String id=npcId.getNamespace().equals("stardewcraft")?npcId.getPath():npcId.toString();
        var state=com.stardew.craft.npc.runtime.NpcRuntimeDataManager.get(level).states().get(id);
        var target=state==null?null:com.stardew.craft.npc.runtime.NpcScheduleRuntimeService.resolveWorldTarget(level,state,null);
        String owner=entity instanceof StardewNpcEntity npc?NpcExecutionCoordinator.owner(npc):"";
        var debug=com.stardew.craft.npc.runtime.NpcCentralMovementService.getDebugSnapshot(id);
        String phase=entity==null?"unloaded":entity instanceof StardewNpcEntity npc && npc.isNativeActivityMovementLocked()
                ?"activity":debug==null?"idle":debug.stage;
        var blocked=com.stardew.craft.npc.runtime.NpcTravelStatus.get(id);
        if(entity!=null && blocked!=null) phase="blocked:"+blocked.reason();
        return new State(npcId,Optional.ofNullable(entity==null?null:entity.getUUID()),owner,phase,
                Optional.ofNullable(entity==null?null:entity.position()),Optional.ofNullable(target==null?null:target.position()));
    }
    public record Lease(ResourceLocation npcId,ResourceLocation owner,UUID entityId,long generation) {}
    public record ConditionContext(ServerLevel level,ResourceLocation npcId,UUID playerId,String expression) {}
    public record SupportContext(ServerLevel level,String pointId,BlockPos block,String kind) {}
    public record Support(BlockPos block,Vec3 origin,Vec3 approach,float yaw,float approachYaw) {
        public Support {
            java.util.Objects.requireNonNull(block); java.util.Objects.requireNonNull(origin); java.util.Objects.requireNonNull(approach);
            if (!Double.isFinite(origin.lengthSqr()) || !Double.isFinite(approach.lengthSqr())
                    || !Float.isFinite(yaw) || !Float.isFinite(approachYaw)
                    || origin.distanceToSqr(approach)>16) throw new IllegalArgumentException("Invalid NPC support");
        }
    }
    @FunctionalInterface public interface Condition { Optional<Boolean> evaluate(ConditionContext context); }
    @FunctionalInterface public interface SupportResolver { Optional<Support> resolve(SupportContext context); }
    public static void registerCondition(ResourceLocation id,int priority,Condition condition) {
        StardewNpcExecutionRegistry.registerCondition(id,priority,condition);
    }
    public static void registerSupport(ResourceLocation id,int priority,SupportResolver resolver) {
        StardewNpcExecutionRegistry.registerSupport(id,priority,resolver);
    }
    /** Renew every tick while controlling the actor. Higher priority wins; equal priority never steals. */
    public static Optional<Lease> claim(ServerLevel level,ResourceLocation npcId,ResourceLocation owner,int priority,int ticks) {
        if (priority<1 || priority>90 || ticks<1 || ticks>1200) throw new IllegalArgumentException("NPC lease limits");
        var entity=StardewNpcEntities.resolve(level,npcId).orElse(null);
        if (!(entity instanceof StardewNpcEntity npc)) return Optional.empty();
        long token=NpcExecutionCoordinator.claim(npc,owner.toString(),priority,ticks);
        return token<0 ? Optional.empty() : Optional.of(new Lease(npcId,owner,npc.getUUID(),token));
    }
    public static boolean isCurrent(ServerLevel level,Lease lease) {
        var entity=StardewNpcEntities.resolve(level,lease.npcId()).orElse(null);
        return entity instanceof StardewNpcEntity npc && npc.getUUID().equals(lease.entityId())
                && NpcExecutionCoordinator.owns(npc,lease.owner().toString(),lease.generation());
    }
    public static void release(ServerLevel level,Lease lease) {
        if (isCurrent(level,lease)) StardewNpcEntities.resolve(level,lease.npcId()).ifPresent(entity ->
                NpcExecutionCoordinator.release((StardewNpcEntity)entity,lease.owner().toString()));
    }
}
