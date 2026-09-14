package com.stardew.craft.animal.runtime;

import com.stardew.craft.building.runtime.*;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.util.StardewDeterministicRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.util.UUID;

/** FarmAnimal.behaviors/DigUpProduce: a stored daily opportunity, spent outdoors on bare accessible ground. */
public final class LivestockTruffles {
    private LivestockTruffles() {}
    public static boolean eligible(ServerLevel level, LivestockRecord animal, BuildingRecord home, BlockPos pos) {
        var clock = StardewTimeManager.get(); var farm = LivestockOutdoors.farm(level.getServer(), home);
        return animal.species().hasDefaultBehavior() && animal.species().harvest()==LivestockSpecies.Harvest.DIG && !animal.baby() && !animal.produce().isEmpty()
                && clock.getCurrentTime() < 1140 && clock.getCurrentSeason() != 3 && !LivestockOutdoors.rain(level)
                && !LivestockHomes.bounds(home).contains(pos) && farm != null && empty(level, farm, pos);
    }
    private static boolean empty(ServerLevel level, FarmInstance farm, BlockPos pos) {
        if (!farm.contains(pos) || !level.hasChunkAt(pos) || !level.getBlockState(pos).isAir() || !level.getFluidState(pos).isEmpty()
                || !level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)) return false;
        var claim = BuildingWorldData.get(level.getServer()).occupying(level.dimension().location(),pos);
        if (claim != null) return false;
        return LivestockWorldData.get(level.getServer()).eggs().stream().noneMatch(e -> pos.equals(e.position()));
    }
    public static boolean roll(ServerLevel level, int ticks) {
        return level.random.nextDouble() < 1 - Math.pow(1 - .0002, ticks * 3.0); // SDV 60 Hz -> MC 20 Hz.
    }
    public static boolean dig(ServerLevel level, LivestockRecord animal, BuildingRecord home, BlockPos pos) {
        if (!eligible(level,animal,home,pos)) return false;
        var farm=LivestockOutdoors.farm(level.getServer(),home); BlockPos output=null;
        for (var side:Direction.Plane.HORIZONTAL) if (empty(level,farm,pos.relative(side))) {output=pos.relative(side);break;}
        if (output==null) return false;
        var clock=StardewTimeManager.get(); int sourceTime=clock.getCurrentTime()/60*100+clock.getCurrentTime()%60;
        var random=StardewDeterministicRandom.create(animal.randomId()/2,clock.getAbsoluteDay(),sourceTime);
        boolean replaced=com.stardew.craft.api.v1.agriculture.StardewTruffleFoundHandlers.run(
                new com.stardew.craft.api.v1.agriculture.StardewTruffleFoundContext(level,-animal.randomId(),animal.species().definitionId(),output,LivestockProducts.stack(animal.produce(),1,0)))
                ==com.stardew.craft.api.v1.agriculture.StardewTruffleFoundHandlers.Result.REPLACE_TRUFFLE;
        boolean crab=replaced;
        if (!replaced && random.nextDouble()<.002) {
            var monster = com.stardew.craft.event.MineMonsterSpawnHandler.spawnConfiguredMonster(level,"truffle_crab",net.minecraft.world.phys.Vec3.atBottomCenterOf(output),0,1,m -> m.setPersistenceRequired());
            crab=monster!=null;
        }
        var data=LivestockWorldData.get(level.getServer());
        if (!crab) data.product(new LivestockWorldData.Product(UUID.randomUUID(),animal.id(),home.id(),false,0,animal.produce(),1,output));
        if (random.nextDouble()>=animal.care().friendship()/1500.0) data.put(animal.produce(""));
        return true;
    }
}
