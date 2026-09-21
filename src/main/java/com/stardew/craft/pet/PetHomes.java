package com.stardew.craft.pet;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.floor.SurfaceFloorData;
import com.stardew.craft.floor.SurfaceFloorType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Verified built-in schematic coordinates; unknown layouts use their own spawn, never the standard layout. */
public final class PetHomes {
    private record Site(BlockPos bowl, BlockPos home, String style, SurfaceFloorType floor) {}
    private PetHomes() {}
    private static Site site(FarmInstance farm) {
        if (farm.getFarmLayoutId().getNamespace().equals("stardewcraft")) {
            Site local = switch (farm.getFarmLayoutId().getPath()) {
                case "standard" -> standardSite(farm);
                case "forest" -> new Site(new BlockPos(178, 25, 70), new BlockPos(199, 25, 93), "hay", SurfaceFloorType.STRAW);
                case "riverland" -> new Site(new BlockPos(182, 25, 83), new BlockPos(199, 25, 93), "stone", SurfaceFloorType.STONE);
                case "hilltop" -> new Site(new BlockPos(178, 25, 78), new BlockPos(199, 25, 93), "stone", SurfaceFloorType.STONE);
                case "wilderness" -> new Site(new BlockPos(177, 25, 70), new BlockPos(199, 25, 93), "wood", SurfaceFloorType.WOOD);
                case "four_corners" -> new Site(new BlockPos(155, 25, 142), new BlockPos(199, 25, 93), "wood", SurfaceFloorType.WOOD);
                case "beach" -> new Site(new BlockPos(178, 25, 102), new BlockPos(154, 25, 99), "wood", SurfaceFloorType.WOOD);
                case "meadowlands" -> new Site(new BlockPos(221, 25, 79), new BlockPos(199, 25, 93), "wood", SurfaceFloorType.WOOD);
                default -> null;
            };
            if (local != null) return new Site(farm.getOrigin().offset(local.bowl()), farm.getOrigin().offset(local.home()), local.style(), local.floor());
        }
        return new Site(farm.getSpawnPoint().offset(3, 0, 3), farm.getSpawnPoint(), "wood", SurfaceFloorType.WOOD);
    }
    private static Site standardSite(FarmInstance farm) {
        // Creation-time layout snapshots keep pre-migration farms on their old
        // authored pet site until that farm is explicitly migrated.
        return farm.getFarmLayout().width() == 288
                ? new Site(new BlockPos(183, 25, 77), new BlockPos(199, 25, 94),
                        "wood", SurfaceFloorType.WOOD)
                : new Site(new BlockPos(227, 5, 248), new BlockPos(243, 6, 260),
                        "wood", SurfaceFloorType.WOOD);
    }
    public static BlockPos authoredBowl(FarmInstance farm) { return site(farm).bowl(); }
    public static BlockPos home(FarmInstance farm) { return site(farm).home(); }

    public static Vec3 rest(ServerLevel level, FarmInstance farm, PetRecord pet, net.minecraft.util.RandomSource random) {
        BlockPos home = home(farm), bed = null;
        for (var player : level.players()) if (farm.isFarmer(player.getUUID())) {
            var saved = com.stardew.craft.player.PlayerStardewDataAPI.getData(player).getLastSleepPoint().orElse(null);
            if (saved != null && farm.contains(saved) && level.hasChunkAt(saved)
                    && level.getBlockState(saved).getBlock() instanceof com.stardew.craft.block.decor.BedDecorBlock) { bed = saved; home = saved; break; }
        }
        // A small search around the authored home also covers its unchanged default bed and rugs.
        if (bed == null) for (var pos : BlockPos.betweenClosed(home.offset(-4, -1, -4), home.offset(4, 1, 4))) {
            if (level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof com.stardew.craft.block.decor.BedDecorBlock) { bed = pos.immutable(); break; }
        }
        if (bed != null && random.nextDouble() < pet.variant.bedChance()) {
            for (var pos : BlockPos.betweenClosed(bed.offset(-1, 0, -1), bed.offset(1, 0, 1))) {
                if (!level.hasChunkAt(pos) || !(level.getBlockState(pos).getBlock() instanceof com.stardew.craft.block.decor.BedDecorBlock)) continue;
                Vec3 top = Vec3.atBottomCenterOf(pos).add(0, .5, 0);
                if (safePosition(level, farm, top, pet.variant)) return top;
            }
        }
        if (bed != null && random.nextDouble() < .3) {
            var near = near(level, farm, bed, pet.variant, 2); if (near != null) return Vec3.atBottomCenterOf(near);
        }
        if (random.nextDouble() < .5) for (var pos : BlockPos.betweenClosed(home.offset(-4, -1, -4), home.offset(4, 1, 4))) {
            if (!level.hasChunkAt(pos)) continue;
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
            if (!path.startsWith("carpet_") && !path.endsWith("_carpet")) continue;
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            double top = shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
            var point = Vec3.atBottomCenterOf(pos).add(0, top, 0);
            if (safePosition(level, farm, point, pet.variant)) return point;
        }
        var near = near(level, farm, home, pet.variant, 4);
        return near == null ? null : Vec3.atBottomCenterOf(near);
    }

    public static void prepare(ServerLevel level, FarmInstance farm) {
        var data = PetWorldData.get(level.getServer());
        if (!farm.isInitialized()) return;
        // A saved bowl is already the farm's provisioned bowl, even when older data lacks the
        // preparation receipt.  Regenerating the authored default here would resurrect the old
        // bowl after a move (and would also duplicate any legacy or player-placed bowl).
        if (!data.prepared(farm.getInstanceId()) && data.bowls().stream().anyMatch(bowl -> bowl.farm().equals(farm.getInstanceId())))
            data.markPrepared(farm.getInstanceId());
        if (data.prepared(farm.getInstanceId())) { migrateFloor(level, farm); return; }
        var site = site(farm);
        if (!level.hasChunksAt(site.bowl().offset(-4, -3, -4), site.bowl().offset(4, 3, 4))) return;
        BlockPos position = near(level, farm, site.bowl(), PetVariant.CAT0, 4);
        if (position != null) {
            var floor = SurfaceFloorData.get(level);
            // Cover existing terrain without replacing its identity, planting space or another floor design.
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
                var support = position.offset(x, -1, z);
                if (farm.contains(support) && floor.at(support) == null && level.isEmptyBlock(support.above())
                        && com.stardew.craft.floor.SurfaceFloorItem.supports(level, support, level.getBlockState(support)))
                    floor.restore(level, support, new SurfaceFloorData.Cover(site.floor(), 0));
            }
            var block = switch (site.style()) { case "stone" -> ModBlocks.PET_BOWL_STONE.get(); case "hay" -> ModBlocks.PET_BOWL_HAY.get(); default -> ModBlocks.PET_BOWL_WOOD.get(); };
            level.setBlock(position, block.defaultBlockState().setValue(PetBowlBlock.SEASON, com.stardew.craft.time.StardewTimeManager.get().getCurrentSeason()), 3);
        }
        if (position != null) { data.markPrepared(farm.getInstanceId()); data.markSquareBowlFloor(farm.getInstanceId()); }
    }

    /** Only trim an intact, isolated original 3x3 pad near its authored site. */
    private static void migrateFloor(ServerLevel level, FarmInstance farm) {
        var data = PetWorldData.get(level.getServer());
        if (data.squareBowlFloor(farm.getInstanceId())) return;
        var site = site(farm);
        if (!level.hasChunksAt(site.bowl().offset(-6, -4, -6), site.bowl().offset(6, 4, 6))) return;
        var floors = SurfaceFloorData.get(level);
        for (var bowl : data.bowls()) {
            var pos = bowl.position();
            if (!bowl.farm().equals(farm.getInstanceId()) || !bowl.style().equals(site.style())
                    || Math.abs(pos.getX() - site.bowl().getX()) > 4 || Math.abs(pos.getZ() - site.bowl().getZ()) > 4
                    || Math.abs(pos.getY() - site.bowl().getY()) > 3 || !(level.getBlockState(pos).getBlock() instanceof PetBowlBlock)) continue;
            boolean original = true;
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                var support = pos.offset(x, -1, z); var cover = floors.at(support);
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    if (cover == null || cover.type() != site.floor() || cover.variant() != 0
                            || (x != 0 || z != 0) && !level.isEmptyBlock(support.above())) original = false;
                } else if (cover != null) original = false;
            }
            if (original) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                if (x < 0 || z < 0) floors.remove(level, pos.offset(x, -1, z), false);
        }
        data.markSquareBowlFloor(farm.getInstanceId());
    }

    public static BlockPos near(ServerLevel level, FarmInstance farm, BlockPos center, PetVariant variant, int radius) {
        for (int r = 0; r <= radius; r++) for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            if (Math.max(Math.abs(x), Math.abs(z)) != r) continue;
            for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                var pos = center.offset(x, dy, z);
                if (safe(level, farm, pos, variant)) return pos;
            }
        }
        return null;
    }
    public static boolean safe(ServerLevel level, FarmInstance farm, BlockPos pos, PetVariant variant) {
        if (!farm.contains(pos) || !level.hasChunkAt(pos) || !level.getFluidState(pos).isEmpty()
                || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return false;
        double width = variant.species().width();
        double height = variant.species().height();
        var box = new AABB(pos.getX() + .5 - width / 2, pos.getY(), pos.getZ() + .5 - width / 2,
                pos.getX() + .5 + width / 2, pos.getY() + height, pos.getZ() + .5 + width / 2);
        return level.noCollision(box);
    }

    public static boolean safePosition(ServerLevel level, FarmInstance farm, Vec3 point, PetVariant variant) {
        var pos = BlockPos.containing(point);
        if (!farm.contains(pos) || !level.hasChunkAt(pos) || !level.getFluidState(pos).isEmpty()) return false;
        double width = variant.species().width();
        double height = variant.species().height();
        var box = new AABB(point.x - width / 2, point.y, point.z - width / 2, point.x + width / 2, point.y + height, point.z + width / 2);
        return level.noCollision(box) && level.getBlockCollisions(null, box.move(0, -.02, 0)).iterator().hasNext();
    }
}
