package com.stardew.craft.api.v1.building;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.item.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import java.util.*;
import java.util.function.Supplier;

/** Runtime bindings complement data/<namespace>/farm_building_prefabs/<family>.json. */
public final class StardewBuildingFamilies {
    private static final Map<ResourceLocation,Binding> REGISTERED=new java.util.concurrent.ConcurrentHashMap<>();
    private StardewBuildingFamilies(){}
    public record Display(String nameKey,ResourceLocation texture,int width,int height){
        public Display {Objects.requireNonNull(nameKey);Objects.requireNonNull(texture);if(width<1||height<1)throw new IllegalArgumentException("Invalid building artwork dimensions");}
    }
    public record Binding(Supplier<? extends Block> manager,Supplier<? extends Item> blueprint,
                          Map<Integer,Supplier<? extends Item>> upgrades,Map<Integer,Display> displays){
        public Binding{Objects.requireNonNull(manager);Objects.requireNonNull(blueprint);upgrades=Map.copyOf(upgrades);displays=Map.copyOf(displays);}
    }
    public static void register(ResourceLocation family,Binding binding){
        Objects.requireNonNull(family);Objects.requireNonNull(binding);
        if(builtin(family)!=null||REGISTERED.putIfAbsent(family,binding)!=null)throw new IllegalStateException("Duplicate building family "+family);
    }
    public static Optional<Binding> find(ResourceLocation family){
        Binding binding=REGISTERED.get(family);return Optional.ofNullable(binding==null?builtin(family):binding);
    }
    public static net.minecraft.network.chat.MutableComponent title(ResourceLocation family,int tier){
        var display=find(family).map(b->b.displays().get(tier)).orElse(null);
        return display!=null?net.minecraft.network.chat.Component.translatable(display.nameKey()):family.getNamespace().equals("stardewcraft")?net.minecraft.network.chat.Component.translatable("stardewcraft.manager.building."+family.getPath()):net.minecraft.network.chat.Component.literal(family.toString());
    }
    public static Item managerItem(ResourceLocation family,Item.Properties properties){
        return new com.stardew.craft.building.runtime.BuildingManagerItem(family,properties);
    }
    public static Item blueprintItem(ResourceLocation family,Item.Properties properties){
        return new com.stardew.craft.building.runtime.BuildingBlueprintItem(family,properties);
    }
    public static Item upgradeItem(ResourceLocation family,int tier,Item.Properties properties){
        return new com.stardew.craft.building.runtime.BuildingUpgradePermitItem(family,tier,properties);
    }
    public static boolean openManager(net.minecraft.server.level.ServerPlayer player,net.minecraft.core.BlockPos pos){
        return com.stardew.craft.building.runtime.BuildingManagerInteraction.open(player,pos);
    }
    private static Binding builtin(ResourceLocation id){
        if(!id.getNamespace().equals("stardewcraft"))return null;
        return switch(id.getPath()){
            case "coop" -> new Binding(ModBlocks.COOP_MANAGER,ModItems.COOP_BLUEPRINT,Map.of(2,ModItems.COOP_UPGRADE_2_PERMIT,3,ModItems.COOP_UPGRADE_3_PERMIT),Map.of());
            case "barn" -> new Binding(ModBlocks.BARN_MANAGER,ModItems.BARN_BLUEPRINT,Map.of(2,ModItems.BARN_UPGRADE_2_PERMIT,3,ModItems.BARN_UPGRADE_3_PERMIT),Map.of());
            case "fish_pond" -> new Binding(ModBlocks.FISH_POND_MANAGER,ModItems.FISH_POND_BLUEPRINT,Map.of(),Map.of());
            case "greenhouse" -> new Binding(ModBlocks.GREENHOUSE_MANAGER,ModItems.GREENHOUSE_BLUEPRINT,Map.of(),Map.of());
            case "silo" -> new Binding(ModBlocks.SILO_MANAGER,ModItems.SILO_BLUEPRINT,Map.of(),Map.of());
            case "pet_bowl_wood" -> new Binding(ModBlocks.PET_BOWL_WOOD,ModItems.PET_BOWL_WOOD_MOVE,Map.of(),Map.of());
            case "pet_bowl_stone" -> new Binding(ModBlocks.PET_BOWL_STONE,ModItems.PET_BOWL_STONE_MOVE,Map.of(),Map.of());
            case "pet_bowl_hay" -> new Binding(ModBlocks.PET_BOWL_HAY,ModItems.PET_BOWL_HAY_MOVE,Map.of(),Map.of());
            default -> null;
        };
    }
}
