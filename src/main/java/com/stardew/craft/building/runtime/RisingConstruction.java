package com.stardew.craft.building.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Vanilla block-display interpolation provides a real rising fence without moving its logical claim. */
public final class RisingConstruction {
    private static final String MARKER="StardewConstructionRise";
    private RisingConstruction(){}
    public static void place(ServerLevel level,BuildingRecord record,BlockPos pos,BlockState state,boolean animate){
        for(var entity:level.getEntitiesOfClass(Display.BlockDisplay.class,new net.minecraft.world.phys.AABB(pos).inflate(2))) {
            var tag=entity.getPersistentData().getCompound(MARKER);
            if(tag.hasUUID("Building") && tag.getUUID("Building").equals(record.id()) && tag.getLong("Position")==pos.asLong())return;
        }
        if(!animate){level.setBlock(pos,state,Block.UPDATE_ALL);return;}
        var display=EntityType.BLOCK_DISPLAY.create(level);if(display==null)return;
        var saved=new CompoundTag();display.saveWithoutId(saved);saved.put("block_state",NbtUtils.writeBlockState(state));saved.putInt("teleport_duration",20);saved.putBoolean("Invulnerable",true);saved.putFloat("view_range",2f);display.load(saved);
        display.setPos(pos.getX(),pos.getY()-1,pos.getZ());
        var marker=new CompoundTag();marker.putUUID("Building",record.id());marker.putLong("Position",pos.asLong());marker.putLong("Start",level.getGameTime());marker.put("State",NbtUtils.writeBlockState(state));display.getPersistentData().put(MARKER,marker);
        level.addFreshEntity(display);
    }
    private static java.util.List<net.minecraft.world.entity.Entity> snapshot(ServerLevel level){
        var list=new java.util.ArrayList<net.minecraft.world.entity.Entity>();level.getAllEntities().forEach(list::add);return list;
    }
    public static void clear(ServerLevel level,java.util.UUID building){
        for(var entity:snapshot(level))if(entity instanceof Display.BlockDisplay){var tag=entity.getPersistentData().getCompound(MARKER);if(tag.hasUUID("Building") && tag.getUUID("Building").equals(building))entity.discard();}
    }
    public static void tick(ServerLevel level){
        var data=BuildingWorldData.peek(level.getServer());if(data==null)return;
        for(var entity:snapshot(level))if(entity instanceof Display.BlockDisplay display){
            var tag=display.getPersistentData().getCompound(MARKER);if(!tag.hasUUID("Building"))continue;
            var building=data.find(tag.getUUID("Building"));if(building==null || building.phase()!=BuildingRecord.Phase.CONSTRUCTING && building.phase()!=BuildingRecord.Phase.UPGRADING){display.discard();continue;}
            var pos=BlockPos.of(tag.getLong("Position"));long age=level.getGameTime()-tag.getLong("Start");
            if(age>=2 && !tag.getBoolean("Raised")){display.setPos(pos.getX(),pos.getY(),pos.getZ());tag.putBoolean("Raised",true);}
            if(age>=23){var state=NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),tag.getCompound("State"));BuildingProtection.internal(()->level.setBlock(pos,state,Block.UPDATE_ALL));display.discard();}
        }
    }
}
