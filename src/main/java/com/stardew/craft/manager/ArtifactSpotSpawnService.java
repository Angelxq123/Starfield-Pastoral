package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.world.*;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.SurfaceArtifactSpotBlock;
import com.stardew.craft.core.FarmAreaResolver;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import java.util.*;

/** Location-level SDV lifecycle. Chunk loading only discovers markers; it never rolls spawns. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ArtifactSpotSpawnService {
    private ArtifactSpotSpawnService() {}
    private static final ResourceLocation OUTDOOR = ResourceLocation.fromNamespaceAndPath("stardewcraft", "outdoor");
    private static final Map<String, String> LOCATION_NAMES = Map.ofEntries(
            Map.entry("town", "Town"), Map.entry("forest", "Forest"), Map.entry("mountain", "Mountain"),
            Map.entry("bus_stop", "BusStop"), Map.entry("backwoods", "Backwoods"),
            Map.entry("railroad", "Railroad"), Map.entry("beach", "Beach"),
            Map.entry("desert", "Desert"), Map.entry("secret_woods", "Woods"));

    public static String locationName(net.minecraft.world.level.Level level, BlockPos ground) {
        if (FarmAreaResolver.isInAnyFarm(level, ground)) return "Farm";
        if (StardewLocations.find(level, ground).map(StardewLocation::indoor).orElse(false)) return "Default";
        return StardewRegions.find(level, ground).or(() -> StardewRegions.find(level, ground.above()))
                .map(r -> LOCATION_NAMES.getOrDefault(r.locationId() == null ? r.id().getPath() : r.locationId().getPath(), "Default"))
                .orElse("Default");
    }

    public static boolean isDiggableSurface(BlockState state) {
        // Deliberate project exception: grass is excluded even in winter.
        return state.is(ModBlocks.DIRT.get()) || state.is(ModBlocks.YELLOW_DIRT.get())
                || state.is(ModBlocks.MINE_EARTH_LOOSE_SOIL.get())
                || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.SAND) || state.is(ModBlocks.SAND.get());
    }

    public static boolean canPlace(ServerLevel level, BlockPos marker) {
        return isDiggableSurface(level.getBlockState(marker.below()))
                && level.getBlockState(marker).isAir() && surfaceMarker(level, marker.getX(), marker.getZ()).equals(marker)
                && !StardewLocations.find(level, marker.below()).map(StardewLocation::indoor).orElse(false);
    }

    /** Invisible map boundary ceilings are not scenery; real roofs, foliage and fluids still block spawning. */
    public static BlockPos surfaceMarker(ServerLevel level, int x, int z) {
        var cursor = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
        while (cursor.getY() >= level.getMinBuildHeight()) {
            var state = level.getBlockState(cursor);
            if (!state.isAir() && !state.is(Blocks.BARRIER)) return cursor.above();
            cursor.move(0, -1, 0);
        }
        return cursor.above();
    }

    public static boolean place(ServerLevel level, BlockPos marker, boolean seed) {
        return canPlace(level, marker) && level.setBlock(marker,
                (seed ? ModBlocks.SEED_SPOT : ModBlocks.ARTIFACT_SPOT).get().defaultBlockState(), Block.UPDATE_ALL);
    }

    public static void track(ServerLevel level, BlockPos pos) {
        if (data(level).spots.add(pos.asLong())) data(level).setDirty();
    }

    public static void untrack(ServerLevel level, BlockPos pos) {
        if (data(level).spots.remove(pos.asLong())) data(level).setDirty();
    }

    public static int totalDays() {
        var time = StardewTimeManager.get();
        return (time.getCurrentYear() - 1) * 112 + time.getCurrentSeason() * 28 + time.getCurrentDay();
    }

    public static int dailyPasses(int day, int totalDays, boolean farm) {
        return 1 + (!farm && day % 7 == 0 ? 2 : 0) + (day == 1 ? 1 : 0) + (totalDays < 4 ? 1 : 0);
    }

    public static boolean maySpawn(int existing, boolean farm, int season) {
        return existing <= (farm ? 0 : 1) || season == 3 && existing <= 4;
    }

    private record Zone(String key, String location, BlockPos min, BlockPos max, StardewRegion region) {
        boolean contains(ServerLevel level, BlockPos ground) {
            return ground.getX() >= min.getX() && ground.getX() <= max.getX()
                    && ground.getY() >= min.getY() - 1 && ground.getY() <= max.getY()
                    && ground.getZ() >= min.getZ() && ground.getZ() <= max.getZ()
                    && (region == null || region.contains(level.dimension().location(), ground)
                        || region.contains(level.dimension().location(), ground.above()))
                    && location.equals(locationName(level, ground));
        }
    }

    private static List<Zone> zones(ServerLevel level) {
        List<Zone> result = new ArrayList<>();
        for (var region : StardewRegions.withTag(OUTDOOR)) {
            if (!region.dimension().equals(level.dimension().location())) continue;
            String location = LOCATION_NAMES.get(region.locationId() == null ? region.id().getPath() : region.locationId().getPath());
            if (location == null) continue;
            BlockPos min = new BlockPos(region.includes().stream().mapToInt(b -> b.min().getX()).min().orElseThrow(),
                    region.includes().stream().mapToInt(b -> b.min().getY()).min().orElseThrow(),
                    region.includes().stream().mapToInt(b -> b.min().getZ()).min().orElseThrow());
            BlockPos max = new BlockPos(region.includes().stream().mapToInt(b -> b.max().getX()).max().orElseThrow(),
                    region.includes().stream().mapToInt(b -> b.max().getY()).max().orElseThrow(),
                    region.includes().stream().mapToInt(b -> b.max().getZ()).max().orElseThrow());
            result.add(new Zone(region.id().toString(), location, min, max, region));
        }
        for (var farm : FarmInstanceRegistry.get().getAllFarms()) {
            var min = farm.getFarmBoundsMin();
            result.add(new Zone("farm:" + min.asLong(), "Farm", min, farm.getFarmBoundsMax(), null));
        }
        return result;
    }

    public static void onNewDay(ServerLevel level, int season) {
        com.stardew.craft.time.settlement.DailySettlementWorkUnits.drain(createDailyWorkUnit(level,
                com.stardew.craft.time.settlement.DailySettlementContextFactory.withSeason(
                    com.stardew.craft.time.settlement.DailySettlementContextFactory.captureCurrentDay(StardewTimeManager.get()), season)));
    }

    public static com.stardew.craft.time.settlement.DailySettlementWorkUnit createDailyWorkUnit(
            ServerLevel level, com.stardew.craft.time.settlement.DailySettlementContext context) {
        var snapshot = level.dimension().equals(ModDimensions.STARDEW_VALLEY) ? zones(level) : List.<Zone>of();
        var pending = INITIAL.remove(level);
        if (pending != null) pending.close();
        return com.stardew.craft.time.settlement.DailySettlementWorkUnits.sequence("artifact_spots",
                snapshot.stream().map(zone -> (com.stardew.craft.time.settlement.DailySettlementWorkUnit)
                    new ZoneWork(level, zone, context.season(), context.day(), context.absoluteDay())).toList(), () -> {});
    }

    private static final Map<ServerLevel, com.stardew.craft.time.settlement.DailySettlementWorkUnit> INITIAL = new WeakHashMap<>();
    public static void ensureInitialSpawn(ServerLevel level, int season) {
        if (!INITIAL.containsKey(level)) {
            var context = com.stardew.craft.time.settlement.DailySettlementContextFactory.withSeason(
                    com.stardew.craft.time.settlement.DailySettlementContextFactory.captureCurrentDay(StardewTimeManager.get()), season);
            INITIAL.put(level, createDailyWorkUnit(level, context));
        }
    }

    @SubscribeEvent public static void tickInitial(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var settlement = com.stardew.craft.time.settlement.DailySettlementServices.find(level.getServer());
        if (settlement != null && settlement.coordinator().isActive()) return;
        var unit = INITIAL.get(level); if (unit == null) return;
        long deadline = System.nanoTime() + 1_000_000L;
        for (int i = 0; i < 16 && !unit.isComplete() && System.nanoTime() < deadline; i++) {
            try { unit.runNext(); } catch (Exception failure) {
                StardewCraft.LOGGER.error("Artifact initialization failed", failure); unit.skipFailedItem();
            }
        }
        if (unit.isComplete()) { unit.close(); INITIAL.remove(level); }
    }
    @SubscribeEvent public static void stopInitial(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        INITIAL.values().forEach(com.stardew.craft.time.settlement.DailySettlementWorkUnit::close); INITIAL.clear();
    }

    /** One marker or terrain candidate per scheduler item; never scan a whole region in a tick. */
    private static final class ZoneWork implements com.stardew.craft.time.settlement.DailySettlementWorkUnit {
        final ServerLevel level; final Zone zone; final int season, day, absoluteDay;
        final RandomSource random, positionRandom;
        List<Long> markers; int markerIndex, count, pass; double chance = 1;
        ArtifactSpotCandidatePool candidates; boolean rolled, complete;
        ZoneWork(ServerLevel level, Zone zone, int season, int day, int absoluteDay) {
            this.level=level; this.zone=zone; this.season=season; this.day=day; this.absoluteDay=absoluteDay;
            random=RandomSource.create(level.getSeed()+absoluteDay*777L+zone.key.hashCode());
            positionRandom=RandomSource.create(level.getSeed()^absoluteDay*777L^zone.key.hashCode()^0x6A09E667F3BCC909L);
            complete=data(level).updated.getOrDefault(zone.key,-1)>=absoluteDay;
        }
        public String name(){return "artifact_spots";}
        public String currentItemIdentity(){return zone.key+":"+pass+":"+markerIndex;}
        public boolean isComplete(){return complete;}
        public void skipFailedItem(){complete=true;}
        public void runNext() {
            if(complete)return;
            if(markers==null){markers=List.copyOf(data(level).spots);return;}
            if(markerIndex<markers.size()) {
                var marker=BlockPos.of(markers.get(markerIndex++));
                if(!zone.contains(level,marker.below()))return;
                try(var lease=com.stardew.craft.farm.FarmDailyProcessHelper.leaseTemporaryBounds(level,marker,marker)) {
                    if(!(level.getBlockState(marker).getBlock() instanceof SurfaceArtifactSpotBlock))untrack(level,marker);
                    else if(random.nextDouble()<.15)level.removeBlock(marker,false);else count++;
                }
                return;
            }
            if(!maySpawn(count,zone.location.equals("Farm"),season)){nextPass();return;}
            if(!rolled){if(random.nextDouble()>=chance){nextPass();return;}rolled=true;}
            if(candidates==null)candidates=new ArtifactSpotCandidatePool(zone.region==null
                    ?List.of(new StardewRegion.Box(zone.min,zone.max)):zone.region.includes(),positionRandom);
            var column=candidates.drawColumn();
            if(column==null){nextPass();return;}
            try(var lease=com.stardew.craft.farm.FarmDailyProcessHelper.leaseTemporaryBounds(level,column,column)) {
                var marker=surfaceMarker(level,column.getX(),column.getZ());
                if(!isDiggableSurface(level.getBlockState(marker.below()))||!zone.contains(level,marker.below())||!canPlace(level,marker))return;
                place(level,marker,random.nextDouble()<.166);
                chance=chance*.75+(season==3?.10000000149011612:0);rolled=false;
            }
        }
        void nextPass(){
            if(++pass>=dailyPasses(day,absoluteDay,zone.location.equals("Farm"))){
                data(level).updated.put(zone.key,absoluteDay);data(level).setDirty();complete=true;
            }else{markers=null;markerIndex=0;count=0;chance=1;candidates=null;rolled=false;}
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)
                || !level.dimension().equals(ModDimensions.STARDEW_VALLEY)) return;
        // The callback must not set blocks or ask for heights while the chunk is being loaded.
        int cx = chunk.getPos().x, cz = chunk.getPos().z;
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 1, () -> {
            var loaded = level.getChunkSource().getChunkNow(cx, cz);
            if (loaded == null) return;
            for (int sectionIndex = 0; sectionIndex < loaded.getSectionsCount(); sectionIndex++) {
                var section = loaded.getSection(sectionIndex);
                if (!section.maybeHas(s -> s.getBlock() instanceof SurfaceArtifactSpotBlock)) continue;
                int bottom = loaded.getSectionYFromSectionIndex(sectionIndex) << 4;
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    var state = section.getBlockState(x, y, z);
                    var pos = new BlockPos((cx << 4) + x, bottom + y, (cz << 4) + z);
                    if (state.getBlock() instanceof SurfaceArtifactSpotBlock) track(level, pos);

                }
            }
        }));
    }

    public static long treasureTotemsUsed(ServerLevel level) { return data(level).totems; }
    public static void recordTreasureTotem(ServerLevel level) { data(level).totems++; data(level).setDirty(); }

    private static SpotData data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(SpotData::new,
                (tag, lookup) -> new SpotData(tag)), "stardewcraft_surface_artifact_spots");
    }

    public static final class SpotData extends SavedData {
        final Set<Long> spots = new LinkedHashSet<>();
        final Map<String, Integer> updated = new HashMap<>();
        long totems;
        public SpotData() {}
        SpotData(CompoundTag tag) {
            for (long pos : tag.getLongArray("Spots")) spots.add(pos);
            var days = tag.getCompound("Days");
            for (String key : days.getAllKeys()) updated.put(key, days.getInt(key));
            totems = tag.getLong("TreasureTotemsUsed");
        }
        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
            tag.putLongArray("Spots", spots.stream().mapToLong(Long::longValue).toArray());
            var days = new CompoundTag(); updated.forEach(days::putInt); tag.put("Days", days);
            tag.putLong("TreasureTotemsUsed", totems); return tag;
        }
    }
}
