package com.stardew.craft.building.runtime;

import com.stardew.craft.animal.runtime.*;
import com.stardew.craft.floor.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.phys.Vec3;

/** Additional content is journaled with blocks, so replay never regenerates inventory or animal identity. */
public final class BuildingTransferExtras {
    private BuildingTransferExtras() {}
    public static void requireUpgradeClear(ServerLevel level, BuildingRecord record) {
        var bounds = BuildingTransfer.contentBounds(record);
        var next = PrefabDefinitions.transform(PrefabDefinitions.get(record.family()).tier(record.tier()+1).bounds(),record.anchor(),PrefabDefinitions.rotation(record.facing()));
        var floors = SurfaceFloorData.get(level);
        for (var pos : BlockPos.betweenClosed(record.claim().min(),record.claim().maxInclusive()))
            if ((bounds.contains(pos) || next.contains(pos) || next.contains(pos.above())) && floors.at(pos) != null) throw new BuildingTransfer.Collision(pos.immutable());
        for (var entity : level.getEntitiesOfClass(HangingEntity.class, BuildingPlacementService.aabb(bounds).minmax(BuildingPlacementService.aabb(next))))
            throw new BuildingTransfer.Collision(entity.blockPosition());
    }
    public static CompoundTag capture(ServerLevel level, BuildingRecord before, BuildingRecord after) {
        var tag = new CompoundTag(); var bounds = BuildingTransfer.contentBounds(before);
        boolean moving = before.tier() == after.tier();
        var floors = new ListTag(); var decorations = new ListTag(); var animals = new ListTag(); var products = new ListTag();
        var rotation = PrefabDefinitions.rotation(after.facing()).getRotated(PrefabDefinitions.inverse(PrefabDefinitions.rotation(before.facing())));
        if (moving) {
            var data = SurfaceFloorData.get(level);
            for (var pos : BlockPos.betweenClosed(bounds.min().below(before.mode() == BuildingRecord.Mode.SELF_BUILT ? 1 : 0), bounds.maxInclusive())) {
                var cover = data.at(pos); if (cover == null) continue;
                var row = new CompoundTag(); row.putLong("From", pos.asLong()); row.putLong("To", BuildingTransfer.destination(before, after, pos).asLong());
                row.putString("Type", cover.type().id); row.putInt("Variant", cover.variant()); floors.add(row);
            }
            for (var entity : level.getEntitiesOfClass(HangingEntity.class, BuildingPlacementService.aabb(bounds))) {
                var box = entity.getBoundingBox();
                if (!bounds.contains(BlockPos.containing(box.minX, box.minY, box.minZ)) || !bounds.contains(BlockPos.containing(box.maxX - .001, box.maxY - .001, box.maxZ - .001)))
                    throw new BuildingTransfer.Collision(entity.blockPosition());
                var saved = new CompoundTag(); entity.save(saved);
                var anchor = new BlockPos(saved.getInt("TileX"), saved.getInt("TileY"), saved.getInt("TileZ"));
                var destination = BuildingTransfer.destination(before, after, anchor);
                saved.putInt("TileX", destination.getX()); saved.putInt("TileY", destination.getY()); saved.putInt("TileZ", destination.getZ());
                saved.putByte("Facing", (byte) (entity instanceof net.minecraft.world.entity.decoration.ItemFrame
                        ? rotation.rotate(entity.getDirection()).get3DDataValue() : rotation.rotate(entity.getDirection()).get2DDataValue()));
                var point = point(before, after, entity.position());
                var coords = new ListTag(); coords.add(net.minecraft.nbt.DoubleTag.valueOf(point.x)); coords.add(net.minecraft.nbt.DoubleTag.valueOf(point.y)); coords.add(net.minecraft.nbt.DoubleTag.valueOf(point.z)); saved.put("Pos", coords);
                decorations.add(saved);
            }
        }
        var livestock = LivestockWorldData.get(level.getServer());
        for (var animal : livestock.all()) if (animal.home().equals(before.id())) {
            var entity = level.getEntity(animal.id()); var location = animal.location();
            var original = entity != null ? entity.position() : Vec3.atBottomCenterOf(location == null ? LivestockHomes.preferred(before) : location.position());
            boolean follows = bounds.contains(BlockPos.containing(original));
            var target = moving && follows ? point(before, after, original) : original;
            var row = new CompoundTag(); row.putUUID("Id", animal.id()); row.putBoolean("Follow", follows);
            row.putDouble("X", target.x); row.putDouble("Y", target.y); row.putDouble("Z", target.z);
            row.putFloat("Yaw", entity == null ? 0 : entity.getYRot() + (moving && follows ? after.facing().toYRot() - before.facing().toYRot() : 0));
            row.putFloat("Pitch", entity == null ? 0 : entity.getXRot());
            animals.add(row);
        }
        for (var product : livestock.eggs()) if (product.home().equals(before.id())) {
            var row = new CompoundTag(); row.putUUID("Id", product.id());
            if (product.position() != null) row.putLong("Position", (moving ? BuildingTransfer.destination(before, after, product.position()) : product.position()).asLong());
            products.add(row);
        }
        tag.put("PetBowls", com.stardew.craft.pet.PetBowlBuildings.capture(level, before, after));
        tag.put("Floors", floors); tag.put("Decorations", decorations); tag.put("Animals", animals); tag.put("Products", products); return tag;
    }
    private static Vec3 point(BuildingRecord before, BuildingRecord after, Vec3 source) {
        var from = BlockPos.containing(source); var to = BuildingTransfer.destination(before, after, from);
        var offset = source.subtract(Vec3.atCenterOf(from));
        int turns = Math.floorMod(after.facing().get2DDataValue() - before.facing().get2DDataValue(), 4);
        for (int i = 0; i < turns; i++) offset = new Vec3(-offset.z, offset.y, offset.x);
        return Vec3.atCenterOf(to).add(offset);
    }
    public static void clear(ServerLevel level, CompoundTag tag) {
        var floors = SurfaceFloorData.get(level);
        for (var raw : tag.getList("Floors", 10)) { var row = (CompoundTag) raw; floors.remove(level, BlockPos.of(row.getLong("From")), false); floors.remove(level, BlockPos.of(row.getLong("To")), false); }
        for (var raw : tag.getList("Decorations", 10)) { var row = (CompoundTag) raw; var entity = level.getEntity(row.getUUID("UUID")); if (entity != null) entity.discard(); }
    }
    public static void project(ServerLevel level, BuildingRecord before, BuildingRecord after, CompoundTag tag) {
        com.stardew.craft.pet.PetBowlBuildings.project(level, tag.getList("PetBowls", 10));
        var floors = SurfaceFloorData.get(level);
        for (var raw : tag.getList("Floors", 10)) {
            var row = (CompoundTag) raw; floors.restore(level, BlockPos.of(row.getLong("To")), new SurfaceFloorData.Cover(SurfaceFloorType.byId(row.getString("Type")), row.getInt("Variant")));
        }
        for (var raw : tag.getList("Decorations", 10)) {
            var row = (CompoundTag) raw;
            if (level.getEntity(row.getUUID("UUID")) == null) {
                var entity = EntityType.loadEntityRecursive(row, level, value -> value);
                if (entity != null) level.addFreshEntity(entity);
            }
        }
        var data = LivestockWorldData.get(level.getServer());
        for (var raw : tag.getList("Animals", 10)) {
            var row = (CompoundTag) raw; var animal = data.find(row.getUUID("Id")); if (animal == null || !animal.home().equals(before.id())) continue;
            var position = new Vec3(row.getDouble("X"), row.getDouble("Y"), row.getDouble("Z"));
            if (row.getBoolean("Follow") && !level.noCollision(animal.species().dimensions(animal.baby()).makeBoundingBox(position))) {
                var safe = LivestockHomes.spawn(level, after, animal.species(), animal.baby());
                if (safe == null) throw new BuildingTransfer.Collision(BlockPos.containing(position));
                position = Vec3.atBottomCenterOf(safe);
            }
            var old = animal.location();
            data.put(animal.at(new LivestockLocation(BlockPos.containing(position), after.anchor(), !row.getBoolean("Follow"), old == null ? 0 : old.environmentStamp(), old != null && old.leftOut())));
            var entity = level.getEntity(animal.id());
            if (row.getBoolean("Follow") && entity != null) entity.moveTo(position.x, position.y, position.z, row.getFloat("Yaw"), row.getFloat("Pitch"));
        }
        for (var raw : tag.getList("Products", 10)) {
            var row = (CompoundTag) raw;
            for (var egg : data.eggs()) if (egg.id().equals(row.getUUID("Id"))) {
                var pos = row.contains("Position") ? BlockPos.of(row.getLong("Position")) : LivestockHomes.spawn(level, after);
                data.product(new LivestockWorldData.Product(egg.id(), egg.animal(), egg.home(), egg.large(), egg.quality(), egg.item(), egg.count(), pos, egg.stackData()));
                var entity = level.getEntity(egg.id()); if (entity != null && pos != null) entity.setPos(Vec3.atBottomCenterOf(pos));
                break;
            }
        }
    }
}
