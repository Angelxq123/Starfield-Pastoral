package com.stardew.craft.api.v1.internal.giant;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.agriculture.*;
import com.stardew.craft.api.v1.agriculture.StardewGiantCrops.*;
import com.stardew.craft.api.v1.internal.crop.StardewCropRuntimeRegistry;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.core.FarmAreaResolver;
import com.stardew.craft.manager.CropGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** One deterministic pass over registered roots, independent of chunk arrival and hash iteration order. */
public final class GiantCropGrowth {
    private GiantCropGrowth() {}
    private record Root(StardewCropState crop, ResourceLocation produce, BlockState block) {}
    public record Statistics(int candidates, int rolls, int footprintChecks, int spaceCells, int grown) {}
    public static final Comparator<BlockPos> ORDER = Comparator.<BlockPos>comparingInt(p -> p.getY())
            .thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX);

    public static ResourceLocation produce(ServerLevel level, BlockPos pos, StardewCropState crop) {
        var data = StardewAgricultureDataApi.crop(level, pos, level.getBlockState(pos));
        if (data == null) {
            var type = StardewCropTypes.definition(crop.typeId());
            if (type != null) data = type.data();
        }
        if (data != null) return data.produce();
        if (level.getBlockState(pos).getBlock() instanceof StardewCropBlock core)
            return BuiltInRegistries.ITEM.getKey(core.getAutomationHarvestPreview(level.getBlockState(pos)).getItem());
        return null;
    }

    public static Statistics process(ServerLevel level, Collection<BlockPos> positions, Set<BlockPos> watered,
                                     int day, boolean offline) {
        return process(level, positions, watered, day, offline, positions);
    }

    public static Statistics process(ServerLevel level, Collection<BlockPos> positions, Set<BlockPos> watered,
                                     int day, boolean offline, Collection<BlockPos> triggers) {
        var roots = new HashMap<BlockPos, Root>();
        for (var pos : positions) {
            if (!level.hasChunkAt(pos)) continue;
            var crop = StardewCropRuntimeRegistry.inspect(level, pos);
            if (crop == null || crop.part() != StardewCropState.Part.ROOT || !crop.root().equals(pos)) continue;
            var produce = produce(level, pos, crop);
            if (produce != null && !GiantCropRegistry.forProduce(produce).isEmpty())
                roots.put(pos.immutable(), new Root(crop, produce, level.getBlockState(pos)));
        }
        var candidates = triggers.stream().distinct().filter(roots::containsKey).sorted(ORDER).toList();
        int considered = 0, rolls = 0, footprints = 0, spaceCells = 0, grown = 0;
        var manager = CropGrowthManager.get(level);
        for (var anchor : candidates) {
            var root = roots.get(anchor);
            if (root == null || !root.crop().mature() || level.getBlockState(anchor) != root.block()) continue;
            var growth = manager.getOrCreateState(level, anchor);
            if (growth.lastGiantDay >= day) continue;
            growth.lastGiantDay = day;
            manager.setDirty();
            considered++;
            for (var definition : GiantCropRegistry.forProduce(root.produce())) {
                var context = new Context(level, anchor, definition, day, watered.contains(anchor), offline);
                if (definition.requiresWater() && !context.watered()) continue;
                rolls++;
                if (!wins(level.getSeed(), level.dimension().location(), anchor, definition.id(), day, definition.chance())) continue;
                var max = anchor.offset(definition.width() - 1, definition.height() - 1, definition.depth() - 1);
                if (anchor.getY() < level.getMinBuildHeight() || max.getY() >= level.getMaxBuildHeight()
                        || !level.getWorldBorder().isWithinBounds(anchor) || !level.getWorldBorder().isWithinBounds(max)
                        || !level.hasChunksAt(anchor, max)) continue;
                try {
                    var farm = FarmAreaResolver.isInAnyFarm(level, anchor) ? FarmAreaResolver.getFarmAt(anchor) : null;
                    if (farm == null ? !definition.handler().allowsOutsideFarm(context)
                            : !farm.getInstanceId().equals(Optional.ofNullable(FarmAreaResolver.getFarmAt(max)).map(f -> f.getInstanceId()).orElse(null))) continue;
                    if (!definition.handler().condition(context)) continue;
                    footprints++;
                    var source = new ArrayList<StardewCropState>();
                    boolean valid = true;
                    for (int z = 0; z < definition.depth() && valid; z++) for (int x = 0; x < definition.width(); x++) {
                        var pos = anchor.offset(x, 0, z);
                        var neighbor = roots.get(pos);
                        var neighborFarm = FarmAreaResolver.isInAnyFarm(level, pos) ? FarmAreaResolver.getFarmAt(pos) : null;
                        if (!Objects.equals(farm == null ? null : farm.getInstanceId(), neighborFarm == null ? null : neighborFarm.getInstanceId())) { valid = false; break; }
                        if (neighbor == null || !neighbor.produce().equals(root.produce()) || level.getBlockState(pos) != neighbor.block()) { valid = false; break; }
                        source.add(neighbor.crop());
                    }
                    if (!valid) continue;
                    var sourceRoots = new HashSet<BlockPos>();
                    source.forEach(c -> sourceRoots.add(c.root()));
                    var previous = new LinkedHashMap<BlockPos, BlockState>();
                    var cropParts = new HashSet<BlockPos>();
                    for (var mutable : BlockPos.betweenClosed(anchor, max)) {
                        var pos = mutable.immutable();
                        var state = level.getBlockState(pos); spaceCells++;
                        // Container inventories and arbitrary terrain must never be erased by growth.
                        if (state.hasBlockEntity()) { valid = false; break; }
                        boolean part = sourceRoots.contains(pos);
                        if (!part && StardewCropRuntimeRegistry.isRegisteredBlock(state)) {
                            var crop = StardewCropRuntimeRegistry.inspect(level, pos);
                            part = crop != null && sourceRoots.contains(crop.root());
                        }
                        if (!part && !state.isAir() && !state.canBeReplaced()) { valid = false; break; }
                        if (part) cropParts.add(pos);
                        previous.put(pos, state);
                    }
                    if (!valid) continue;
                    var plan = definition.handler().plan(context, List.copyOf(source));
                    if (plan == null || plan.isEmpty()) continue;
                    var writes = new LinkedHashMap<BlockPos, BlockState>();
                    for (var cell : plan) {
                        var offset = cell.offset();
                        if (offset.getX() < 0 || offset.getX() >= definition.width() || offset.getY() < 0 || offset.getY() >= definition.height()
                                || offset.getZ() < 0 || offset.getZ() >= definition.depth()
                                || writes.putIfAbsent(anchor.offset(offset), cell.state()) != null) { valid = false; break; }
                    }
                    if (!valid || sourceRoots.stream().anyMatch(p -> !writes.containsKey(p) || writes.get(p).isAir())) continue;
                    for (var pos : cropParts) writes.putIfAbsent(pos, Blocks.AIR.defaultBlockState());
                    if (previous.entrySet().stream().anyMatch(e -> level.getBlockState(e.getKey()) != e.getValue())) continue;
                    // Stage all cells without neighbor shape callbacks, then publish the completed shape.
                    // No drops or per-cell harvesting is involved.
                    var savedGrowth = new HashMap<BlockPos, CropGrowthManager.CropGrowthState>();
                    for (var crop : source) savedGrowth.put(crop.root(), manager.getOrCreateState(level, crop.root()));
                    try {
                        // Native double crops remove their partner in onRemove, even with shape updates
                        // suppressed. Finish that lifecycle before placing any giant cell.
                        for (var pos : cropParts.stream().sorted(ORDER.reversed()).toList()) {
                            if (!level.getBlockState(pos).isAir())
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                            if (!level.getBlockState(pos).isAir())
                                throw new IllegalStateException("Crop removal rejected at " + pos);
                        }
                        for (var entry : writes.entrySet()) {
                            var pos = entry.getKey();
                            if (level.getBlockState(pos) != entry.getValue()
                                    && !level.setBlock(pos, entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE))
                                throw new IllegalStateException("Giant crop placement rejected at " + pos);
                        }
                    } catch (RuntimeException failure) {
                        for (var entry : previous.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                        savedGrowth.forEach((pos, saved) -> manager.restoreGrowthState(level, pos, saved));
                        throw failure;
                    }
                    for (var crop : source) { manager.removeCrop(level, crop.root()); roots.remove(crop.root()); }
                    for (var pos : writes.keySet()) level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
                    grown++;
                    break;
                } catch (RuntimeException exception) {
                    StardewCraft.LOGGER.error("Giant crop {} failed at {}", definition.id(), anchor, exception);
                }
            }
        }
        return new Statistics(considered, rolls, footprints, spaceCells, grown);
    }

    /** Stable across processes; no Class identity, world RNG consumption, or global clock mutation. */
    public static boolean wins(long worldSeed, ResourceLocation dimension, BlockPos anchor, ResourceLocation id, int day, double chance) {
        if (chance <= 0) return false;
        if (chance >= 1) return true;
        long seed = mix(worldSeed ^ anchor.asLong()) ^ mix(day) ^ mix(hash(dimension.toString())) ^ mix(hash(id.toString()));
        return (mix(seed) >>> 11) * 0x1.0p-53 < chance;
    }
    private static long hash(String value) { long hash = 0xcbf29ce484222325L; for (int i = 0; i < value.length(); i++) hash = (hash ^ value.charAt(i)) * 0x100000001b3L; return hash; }
    private static long mix(long value) { value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L; value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL; return value ^ (value >>> 31); }
}
