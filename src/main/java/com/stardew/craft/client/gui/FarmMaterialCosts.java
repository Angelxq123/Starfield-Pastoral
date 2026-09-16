package com.stardew.craft.client.gui;

import com.stardew.craft.shop.CarpenterBlueprint.MaterialEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** One inventory count and aggregated material list for the catalog and purchase pages. */
public final class FarmMaterialCosts {
    private FarmMaterialCosts() {}
    public static List<MaterialEntry> combined(List<MaterialEntry> entries) {
        Map<String,Integer> counts=new LinkedHashMap<>();
        entries.forEach(entry->counts.merge(entry.itemId(),entry.count(),Integer::sum));
        return counts.entrySet().stream().map(e->new MaterialEntry(e.getKey(),e.getValue())).toList();
    }
    public static ItemStack stack(MaterialEntry entry) {
        var id=ResourceLocation.tryParse(entry.itemId());
        return id==null?ItemStack.EMPTY:new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
    public static int owned(MaterialEntry entry) {
        var player=Minecraft.getInstance().player;var stack=stack(entry);
        if(player==null || stack.isEmpty())return 0;
        int count=0;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++) {
            var current=player.getInventory().getItem(slot);
            if(current.is(stack.getItem()))count+=current.getCount();
        }
        return count;
    }
    public static boolean available(List<MaterialEntry> entries) {
        return combined(entries).stream().allMatch(entry->owned(entry)>=entry.count());
    }
}
