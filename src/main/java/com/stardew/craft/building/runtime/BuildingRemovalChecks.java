package com.stardew.craft.building.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import java.util.Set;

public final class BuildingRemovalChecks {
    private BuildingRemovalChecks(){}
    public static boolean containsItems(Tag tag){
        if(tag instanceof CompoundTag compound){
            if(compound.contains("id",8) && (compound.getInt("count")>0 || compound.getInt("Count")>0))return true;
            for(String key:compound.getAllKeys())if(containsItems(compound.get(key)))return true;
        }else if(tag instanceof ListTag list){for(var entry:list)if(containsItems(entry))return true;}
        return false;
    }
    /** Ask each neighboring block's own survival rule against a read-only view of the removal. */
    public static BlockPos unsupportedAddition(ServerLevel level,Set<BlockPos> removed){
        LevelReader view=(LevelReader)java.lang.reflect.Proxy.newProxyInstance(LevelReader.class.getClassLoader(),new Class[]{LevelReader.class},(proxy,method,args)->{
            if(args!=null && args.length==1 && args[0] instanceof BlockPos pos && removed.contains(pos)){
                if(method.getName().equals("getBlockState"))return Blocks.AIR.defaultBlockState();
                if(method.getName().equals("getFluidState"))return net.minecraft.world.level.material.Fluids.EMPTY.defaultFluidState();
                if(method.getName().equals("getBlockEntity"))return null;
            }
            try{return method.invoke(level,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
        });
        for(var pos:removed)for(var direction:net.minecraft.core.Direction.values()){
            var neighbor=pos.relative(direction);if(removed.contains(neighbor))continue;var state=level.getBlockState(neighbor);
            if(!state.isAir() && (!state.canSurvive(view,neighbor) || direction==net.minecraft.core.Direction.UP && state.getBlock() instanceof net.minecraft.world.level.block.FallingBlock))return neighbor;
        }
        return null;
    }
}
