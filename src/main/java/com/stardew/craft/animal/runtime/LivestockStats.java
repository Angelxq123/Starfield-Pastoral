package com.stardew.craft.animal.runtime;

import com.stardew.craft.animal.service.AnimalProduceStatService;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import java.util.UUID;

/** Production counters use absolute journal targets, so recovery cannot count a gift twice. */
public final class LivestockStats {
    private LivestockStats() {}
    public static LivestockRecord stage(LivestockRecord animal,ItemStack product){
        var extra=animal.extra();var delta=extra.getCompound("ProduceStatDelta");
        for(var key:AnimalProduceStatService.matchingStats(animal.species().definition(),product))
            delta.putInt(key,Math.addExact(delta.getInt(key),product.getCount()));
        if(!delta.isEmpty())extra.put("ProduceStatDelta",delta);
        return animal.extra(extra);
    }
    public static CompoundTag plan(List<LivestockRecord> animals){
        var targets=new CompoundTag();
        for(int i=0;i<animals.size();i++){
            var animal=animals.get(i);var extra=animal.extra();var delta=extra.getCompound("ProduceStatDelta");
            if(delta.isEmpty())continue;
            String owner=animal.owner().toString();var row=targets.getCompound(owner);var player=PlayerDataManager.getPlayerData(animal.owner());
            for(var key:delta.getAllKeys())row.putInt(key,Math.addExact(row.contains(key)?row.getInt(key):player.getStat(key),delta.getInt(key)));
            targets.put(owner,row);extra.remove("ProduceStatDelta");animals.set(i,animal.extra(extra));
        }
        return targets;
    }
    public static void apply(CompoundTag targets){
        for(var owner:targets.getAllKeys()){
            var player=PlayerDataManager.getPlayerData(UUID.fromString(owner));var row=targets.getCompound(owner);
            for(var key:row.getAllKeys())player.setStat(key,Math.max(player.getStat(key),row.getInt(key)));
        }
    }
}
