package com.stardew.craft.animal.runtime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;

public final class LivestockProjection {
    private LivestockProjection(){}
    public static PathfinderMob create(ServerLevel level,LivestockRecord animal){
        var type=projectionType(animal.species());if(type==null)return null;
        var entity=type.create(level);
        if(entity instanceof LivestockEntity builtin)return animal.species().builtinAsset()?builtin:null;
        if(entity instanceof BaseCoopAnimalEntity addon){addon.bindLivestockProjection();return addon;}
        return null;
    }
    private static net.minecraft.world.entity.EntityType<?> projectionType(LivestockSpecies species){
        var type=species.entityType();if(type==null)return null;
        var key=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return species.builtinAsset()&&key.getNamespace().equals("stardewcraft")&&key.getPath().equals(species.asset().name().toLowerCase(java.util.Locale.ROOT))?com.stardew.craft.entity.ModEntities.LIVESTOCK_ANIMAL.get():type;
    }
    public static boolean matches(PathfinderMob entity,LivestockRecord record){return entity.getType()==projectionType(record.species());}
    public static boolean supported(ServerLevel level,LivestockSpecies species){
        var type=projectionType(species);if(type==null)return false;var candidate=type.create(level);
        return candidate instanceof LivestockEntity&&species.builtinAsset()||candidate instanceof BaseCoopAnimalEntity;
    }
    public static void refresh(PathfinderMob entity,LivestockRecord record){
        if(entity instanceof LivestockEntity builtin)builtin.refresh(record);
        else if(entity instanceof BaseCoopAnimalEntity addon){
            addon.bindLivestockProjection();addon.setManagedAnimalType(record.species().definitionId());
            addon.setAge(record.baby()?-24000:0);addon.setCustomName(net.minecraft.network.chat.Component.literal(record.name()));
        }
    }
}
