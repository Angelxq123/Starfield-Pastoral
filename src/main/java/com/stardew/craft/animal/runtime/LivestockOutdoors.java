package com.stardew.craft.animal.runtime;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.PastureGrassBlock;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.farm.*;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Shared movement constraints and unloaded-farm simulation. No old animal AI/data calls. */
public final class LivestockOutdoors {
    private LivestockOutdoors() {}
    public static FarmInstance farm(MinecraftServer server, BuildingRecord home) {
        var farms = FarmInstanceRegistry.get(server); var owner = farms.getOwnerBySlot(home.farmSlot());
        var farm = owner == null ? null : farms.getFarm(owner);
        return farm != null && farm.getInstanceId().equals(home.farmId()) ? farm : null;
    }
    public static boolean rain(ServerLevel level) {
        String weather = WeatherManager.getCurrentWeather(level);
        return weather.equals("Rain") || weather.equals("Storm") || weather.equals("GreenRain");
    }
    public static boolean mayLeave(ServerLevel level, UUID home) {
        var clock = StardewTimeManager.get();
        return clock.getCurrentTime() < 990 && clock.getCurrentSeason() != 3 && !rain(level)
                && LivestockWorldData.get(level.getServer()).outdoorsAllowed(home);
    }
    public static boolean standable(ServerLevel level, FarmInstance farm, BlockPos pos) {
        return standable(level, farm, pos, LivestockSpecies.WHITE_CHICKEN.dimensions(false));
    }
    private static boolean standable(ServerLevel level, FarmInstance farm, BlockPos pos, net.minecraft.world.entity.EntityDimensions body) {
        return farm.contains(pos) && level.hasChunkAt(pos) && level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
                && !level.getBlockCollisions(null, body.makeBoundingBox(pos.getX() + .5, pos.getY() + .01, pos.getZ() + .5)).iterator().hasNext();
    }
    /** Bounded 3D walk search: grass behind a solid wall is not available to an offscreen animal. */
    public static BlockPos reachableGrass(ServerLevel level, FarmInstance farm, BuildingRecord home, BlockPos start) {
        return reachableGrass(level, farm, home, start, LivestockSpecies.WHITE_CHICKEN.dimensions(false));
    }
    public static BlockPos reachableGrass(ServerLevel level, FarmInstance farm, BuildingRecord home, BlockPos start, net.minecraft.world.entity.EntityDimensions body) {
        return reachableOutside(level, farm, home, start, body, true);
    }
    public static BlockPos reachableOutside(ServerLevel level, FarmInstance farm, BuildingRecord home, BlockPos start, net.minecraft.world.entity.EntityDimensions body, boolean grassOnly) {
        var queue = new ArrayDeque<BlockPos>(); var visited = new HashSet<BlockPos>();
        queue.add(start); visited.add(start); var bounds = LivestockHomes.bounds(home);
        var buildings = BuildingWorldData.get(level.getServer());
        while (!queue.isEmpty() && visited.size() <= 2048) {
            var pos = queue.removeFirst();
            if (!bounds.contains(pos) && (grassOnly ? level.getBlockState(pos).getBlock() instanceof PastureGrassBlock : level.getBlockState(pos).isAir())) return pos;
            for (var direction : Direction.Plane.HORIZONTAL) for (int dy : new int[]{0, 1, -1}) {
                var next = pos.relative(direction).offset(0, dy, 0);
                if (Math.abs(next.getX() - start.getX()) > 24 || Math.abs(next.getZ() - start.getZ()) > 24 || Math.abs(next.getY() - start.getY()) > 8 || visited.contains(next)) continue;
                var claim = buildings.occupying(level.dimension().location(), next);
                if (claim != null && !claim.equals(home.id()) || !standable(level, farm, next, body)) continue;
                // Rising first needs headroom above the current cell; falling first needs room above the landing.
                var clearance = dy > 0 ? pos.above() : dy < 0 ? next.above() : null;
                if (clearance != null && level.getBlockCollisions(null, body.makeBoundingBox(clearance.getX() + .5, clearance.getY() + .01, clearance.getZ() + .5)).iterator().hasNext()) continue;
                visited.add(next); queue.addLast(next); break;
            }
        }
        return null;
    }
    public static LivestockRecord graze(ServerLevel level, LivestockRecord animal, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof PastureGrassBlock) || animal.care().fullness() >= 255) return animal;
        boolean blue = state.is(ModBlocks.BLUE_PASTURE_GRASS.get());
        int amount = blue ? animal.species().grassAmount() / 2 : animal.species().grassAmount(); int remaining = state.getValue(PastureGrassBlock.CLUMPS) - amount;
        if (remaining <= 0) level.removeBlock(pos, false);
        else level.setBlock(pos, state.setValue(PastureGrassBlock.CLUMPS, remaining), 3);
        return animal.withCare(animal.settledDay(), animal.care().graze(blue, !rain(level) && (animal.location() == null || !animal.location().leftOut())));
    }
    public static LivestockRecord environment(ServerLevel level, BuildingRecord home, LivestockRecord animal, BlockPos pos) {
        var clock = StardewTimeManager.get(); int time = clock.getCurrentTime();
        int stamp = clock.getAbsoluteDay() * 144 + time / 10;
        var previous = animal.location();
        boolean outside = !LivestockHomes.bounds(home).contains(pos);
        int first = Math.max(previous == null ? stamp : previous.environmentStamp() + 1, clock.getAbsoluteDay() * 144 + 108);
        var care = animal.care(); boolean winter = clock.getCurrentSeason() == 3;
        boolean heater = false;
        if (winter && !outside && first <= stamp) for (var cell : BlockPos.betweenClosed(LivestockHomes.bounds(home).min(), LivestockHomes.bounds(home).maxInclusive())) {
            var state = level.getBlockState(cell);
            if (com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.count(level,cell,com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.Role.HEATER)>0) { heater = true; break; }
        }
        for (int slot = first; slot <= stamp; slot++) {
            int drain = animal.species().drain();
            int change = outside ? (slot - clock.getAbsoluteDay() * 144 > 114 || rain(level) || winter ? -drain : drain) : winter && care.happiness() > 150 ? (heater ? drain : -drain) : 0;
            care = care.mood(care.happiness() + change);
        }
        return animal.withCare(animal.settledDay(), care).at(new LivestockLocation(pos.immutable(), home.anchor(), outside, stamp, previous != null && previous.leftOut()));
    }
    public static void simulateUnloaded(MinecraftServer server) {
        var data = LivestockWorldData.get(server); var buildings = BuildingWorldData.get(server);
        for (var animal : data.all()) {
            if (data.newborn(animal.id()) || !animal.species().hasDefaultBehavior()) continue;
            var home = buildings.find(animal.home()); if (home == null || !PrefabDefinitions.available(home) || home.phase() == BuildingRecord.Phase.MISSING || buildings.transfer(home.id()) != null) continue;
            var farm = farm(server, home); var level = LivestockService.level(server, home); if (farm == null || level == null) continue;
            var entity = level.getEntity(animal.id());
            if (entity != null && level.isPositionEntityTicking(entity.blockPosition()) || level.players().stream().anyMatch(player -> farm.contains(player.blockPosition()))) continue;
            var bounds = LivestockHomes.bounds(home); LivestockHomes.load(level, bounds);
            BlockPos pos = animal.location() != null && animal.location().homeAnchor().equals(home.anchor()) ? animal.location().position() : LivestockHomes.spawn(level, home, animal.species(), animal.baby());
            if (pos == null) continue;
            var next = animal;
            int stamp = StardewTimeManager.get().getAbsoluteDay() * 144 + StardewTimeManager.get().getCurrentTime() / 10;
            if (LivestockTruffles.eligible(level, animal, home, pos) && LivestockTruffles.roll(level, 100)) {
                LivestockTruffles.dig(level, animal, home, pos); next = data.find(animal.id());
            }
            if (animal.location() != null && animal.location().environmentStamp() == stamp) continue;
            if (mayLeave(level, home.id()) && animal.care().fullness() < 195) {
                // Load a bounded block neighborhood, not an entire farm or new ticking tickets.
                LivestockHomes.load(level, new BuildingBounds(pos.offset(-24, 0, -24), pos.offset(25, 1, 25)));
                var grass = reachableGrass(level, farm, home, pos, animal.species().dimensions(animal.baby()));
                if (grass != null) { next = graze(level, next, grass); pos = grass; }
            }
            if (mayLeave(level, home.id()) && bounds.contains(pos)) {
                var outside = reachableOutside(level, farm, home, pos, animal.species().dimensions(animal.baby()), false);
                if (outside != null) pos = outside;
            }
            if (StardewTimeManager.get().getCurrentTime() >= 1020 && data.outdoorsAllowed(home.id())) {
                var inside = LivestockHomes.spawn(level, home, animal.species(), animal.baby()); if (inside != null) pos = inside;
            }
            next = environment(level, home, next, pos);
            if (!next.equals(animal)) data.put(next);
            if (entity != null && !entity.blockPosition().equals(pos)) entity.discard();
        }
    }
    public static boolean navigate(net.minecraft.world.entity.PathfinderMob entity, FarmInstance farm, BuildingRecord home, BlockPos target, boolean mayExit) {
        var level = (ServerLevel) entity.level();
        if (!standable(level, farm, target, entity.getDimensions(entity.getPose()))) return false;
        var path = entity.getNavigation().createPath(target, 0); if (path == null || !path.canReach()) return false;
        var bounds = LivestockHomes.bounds(home); var buildings = BuildingWorldData.get(level.getServer());
        for (int i = 0; i < path.getNodeCount(); i++) {
            var pos = path.getNode(i).asBlockPos(); var claim = buildings.occupying(level.dimension().location(), pos);
            if (!farm.contains(pos) || !mayExit && !bounds.contains(pos) || claim != null && !claim.equals(home.id())) return false;
        }
        return entity.getNavigation().moveTo(path, 1);
    }
}
