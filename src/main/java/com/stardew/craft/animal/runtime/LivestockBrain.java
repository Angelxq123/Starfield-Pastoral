package com.stardew.craft.animal.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.time.StardewTimeManager;

/** Shared movement/controller for builtin and addon entity projections. */
public final class LivestockBrain {
    private final PathfinderMob entity;
    private net.minecraft.core.BlockPos grassTarget;
    private int eatingUntil,diggingUntil;
    public LivestockBrain(PathfinderMob entity){this.entity=entity;}
    private void eating(boolean value){
        if(entity instanceof LivestockEntity builtin)builtin.setEating(value);
        else if(value&&entity instanceof com.stardew.craft.entity.animal.BaseCoopAnimalEntity addon)addon.triggerForageAnimation();
    }
    public void tick(){
        if (!(entity.level() instanceof ServerLevel level) || entity.tickCount % 20 != 0) return;
        var animal = LivestockWorldData.get(level.getServer()).find(entity.getUUID());
        var buildings = BuildingWorldData.get(level.getServer());
        var home = animal == null ? null : buildings.find(animal.home());
        if (animal != null && !animal.species().known()) {entity.discard();return;}
        if (home == null || !home.farmId().equals(animal.farm())) { entity.discard(); return; }
        LivestockProjection.refresh(entity,animal);
        if(!PrefabDefinitions.available(home) || !animal.species().hasDefaultBehavior() || home.phase() == com.stardew.craft.building.runtime.BuildingRecord.Phase.MISSING) {entity.getNavigation().stop(); return;}
        if (buildings.transfer(home.id()) != null) { entity.getNavigation().stop(); return; }
        var bounds = LivestockHomes.bounds(home); var farm = LivestockOutdoors.farm(level.getServer(), home);
        if (farm == null) { entity.getNavigation().stop(); return; }
        if (!farm.contains(entity.blockPosition())
                || level.getBlockCollisions(entity, entity.getBoundingBox()).iterator().hasNext()) {
            var safe = LivestockHomes.spawn(level, home, animal.species(), animal.baby()); entity.getNavigation().stop();
            if (safe != null) { entity.teleportTo(safe.getX() + .5, safe.getY(), safe.getZ() + .5); grassTarget = null; }
        }
        var data = LivestockWorldData.get(level.getServer());
        var changed = LivestockOutdoors.environment(level, home, animal, entity.blockPosition()); if (!changed.equals(animal)) data.put(changed); animal = changed;
        if (diggingUntil > 0) {
            entity.getNavigation().stop();
            if (entity.tickCount < diggingUntil) { eating(true); return; }
            LivestockTruffles.dig(level, animal, home, entity.blockPosition()); diggingUntil = 0; return;
        }
        if (LivestockTruffles.eligible(level, animal, home, entity.blockPosition()) && LivestockTruffles.roll(level, 20)) {
            diggingUntil = entity.tickCount + 30; entity.getNavigation().stop(); eating(true); return;
        }
        eating(entity.tickCount < eatingUntil);
        if (entity.tickCount < eatingUntil) { entity.getNavigation().stop(); return; }
        boolean outside = !bounds.contains(entity.blockPosition());
        boolean canLeave = LivestockOutdoors.mayLeave(level, home.id());
        if (outside && (StardewTimeManager.get().getCurrentTime() >= 1020 || !data.outdoorsAllowed(home.id()) || LivestockOutdoors.rain(level))) {
            grassTarget = null; var inside = LivestockHomes.spawn(level, home, animal.species(), animal.baby());
            if (inside != null && entity.getNavigation().isDone()) LivestockOutdoors.navigate(entity, farm, home, inside, true);
            return;
        }
        if (StardewTimeManager.get().getCurrentTime() >= 1140) { entity.getNavigation().stop(); return; }
        if (grassTarget != null && grassTarget.distToCenterSqr(entity.position()) < 2.5) {
            var fed = LivestockOutdoors.graze(level, animal, grassTarget);
            if (!fed.equals(animal)) { data.put(fed); eatingUntil = entity.tickCount + 20; eating(true); entity.getNavigation().stop(); }
            grassTarget = null; return;
        }
        if (!entity.getNavigation().isDone() || entity.getRandom().nextInt(3) != 0) return;
        if (canLeave && animal.care().fullness() < 195) {
            grassTarget = LivestockOutdoors.reachableGrass(level, farm, home, entity.blockPosition(), animal.species().dimensions(animal.baby()));
            if (grassTarget != null && LivestockOutdoors.navigate(entity, farm, home, grassTarget, true)) return;
            grassTarget = null;
        }
        var target = entity.blockPosition().offset(entity.getRandom().nextInt(9) - 4, entity.getRandom().nextInt(3) - 1, entity.getRandom().nextInt(9) - 4);
        LivestockOutdoors.navigate(entity, farm, home, target, canLeave || outside);
    }
}
