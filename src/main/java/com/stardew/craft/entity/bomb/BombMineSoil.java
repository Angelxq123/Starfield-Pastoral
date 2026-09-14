package com.stardew.craft.entity.bomb;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.mining.OrdinaryMineRuntime;
import com.stardew.craft.museum.LostBookService;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;

/** MineShaft.checkForBuriedItem: mine soil keeps its palette and a solid floor. */
final class BombMineSoil {
    @Nullable
    static BlockState tilled(BlockState state) {
        Block soil = state.getBlock();
        Block loose = soil == ModBlocks.MINE_EARTH_SOIL.get() ? ModBlocks.MINE_EARTH_LOOSE_SOIL.get()
                : soil == ModBlocks.MINE_FROST_SOIL.get() ? ModBlocks.MINE_FROST_LOOSE_SOIL.get()
                : soil == ModBlocks.MINE_LAVA_SOIL.get() ? ModBlocks.MINE_LAVA_LOOSE_SOIL.get()
                : soil == ModBlocks.MINE_DESERT_SOIL.get() ? ModBlocks.MINE_DESERT_LOOSE_SOIL.get()
                : soil == ModBlocks.MINE_EARTH_DARK_SOIL.get() ? ModBlocks.MINE_EARTH_DARK_LOOSE_SOIL.get()
                : soil == ModBlocks.MINE_FROST_DARK_SOIL.get() ? ModBlocks.MINE_FROST_DARK_LOOSE_SOIL.get()
                : soil == ModBlocks.MINE_LAVA_DARK_SOIL.get() ? ModBlocks.MINE_LAVA_DARK_LOOSE_SOIL.get()
                : soil == ModBlocks.MINE_DESERT_DARK_SOIL.get() ? ModBlocks.MINE_DESERT_DARK_LOOSE_SOIL.get() : null;
        return loose == null ? null : loose.defaultBlockState();
    }

    static void drop(ServerLevel level, BlockPos pos, @Nullable ServerPlayer player) {
        if (level.dimension() != ModMiningDimensions.STARDEW_MINING) return;
        int floor = OrdinaryMineRuntime.floorAt(pos);
        if (floor <= 0 || floor == 77377) return;
        boolean festival = com.stardew.craft.festival.desert.DesertFestivalMineService.isActive();
        Item item = roll(level.random, floor, player != null
                && PlayerDataManager.getPlayerData(player).hasMailFlag(LostBookService.FIRST_BOOK_FLAG)
                && LostBookService.canFindAnother(player),
                festival, festival ? com.stardew.craft.festival.desert.DesertFestivalMineService.currentRating(level) : 0);
        if (item == null) return;
        Block.popResource(level, pos.above(), new ItemStack(item));
        if (player != null) {
            var tool = player.getMainHandItem();
            if ((tool.getItem() instanceof com.stardew.craft.item.tool.HoeItem
                    || tool.getItem() instanceof net.minecraft.world.item.HoeItem)
                    && com.stardew.craft.enchantment.StardewEnchantments.has(tool,
                            com.stardew.craft.enchantment.StardewEnchantments.GENEROUS)
                    && level.random.nextDouble() < 0.25D) {
                Block.popResource(level, pos.above(), new ItemStack(item));
            }
        }
    }

    @Nullable
    static Item roll(RandomSource random, int floor, boolean lostBookAvailable, boolean festival, int festivalRating) {
        if (random.nextDouble() >= 0.15D) return null;
        Item item = ModItems.CLAY.get();
        if (random.nextDouble() < 0.07D) {
            if (random.nextDouble() < 0.75D) {
                item = switch (random.nextInt(5)) {
                    case 0 -> ModItems.DWARF_SCROLL_I.get();
                    case 1 -> lostBookAvailable ? ModItems.LOST_BOOK.get() : ModItems.MIXED_SEEDS.get();
                    case 2 -> ModItems.RUSTY_SPOON.get();
                    case 3 -> ModItems.RUSTY_COG.get();
                    default -> ModItems.SKELETAL_TAIL.get();
                };
            } else if (random.nextDouble() < 0.75D) {
                if (floor < 40) item = random.nextBoolean() ? ModItems.DWARVISH_HELM.get() : ModItems.DWARF_SCROLL_II.get();
                else if (floor < 80) item = random.nextBoolean() ? ModItems.DWARF_GADGET.get() : ModItems.GOLD_BAR.get();
                else if (floor < 121) item = ModItems.DWARF_SCROLL_IV.get();
            } else {
                item = random.nextBoolean() ? ModItems.STRANGE_DOLL_GREEN.get() : ModItems.STRANGE_DOLL_YELLOW.get();
            }
        } else if (random.nextDouble() < 0.19D) {
            item = random.nextBoolean() ? ModItems.STONE.get() : ore(random, floor, festival, festivalRating);
        } else if (random.nextDouble() < 0.45D) {
            item = ModItems.CLAY.get();
        } else if (random.nextDouble() < 0.12D) {
            if (random.nextDouble() < 0.25D) item = ModItems.OMNI_GEODE.get();
            else if (floor < 40) item = ModItems.GEODE.get();
            else if (floor < 80) item = ModItems.FROZEN_GEODE.get();
            else if (floor < 121) item = ModItems.MAGMA_GEODE.get();
        } else {
            item = ModItems.VANILLA_CATEGORY_ITEMS.get("cave_carrot").get();
        }
        return item;
    }

    private static Item ore(RandomSource random, int floor, boolean festival, int festivalRating) {
        if (floor < 40) return floor >= 20 && random.nextDouble() < 0.1D
                ? ModItems.IRON_ORE.get() : ModItems.COPPER_ORE.get();
        if (floor < 80) {
            if (floor >= 60 && random.nextDouble() < 0.1D) return ModItems.GOLD_ORE.get();
            return random.nextDouble() < 0.75D ? ModItems.IRON_ORE.get() : ModItems.COPPER_ORE.get();
        }
        if (floor >= 120) {
            if (festival && random.nextDouble() < 0.13D + festivalRating * 0.005D) return ModItems.CALICO_EGG.get();
            if (random.nextDouble() < 0.01D + (floor - 120) / 2000D) return ModItems.IRIDIUM_ORE.get();
        }
        if (random.nextDouble() < 0.75D) return ModItems.GOLD_ORE.get();
        return random.nextDouble() < 0.75D ? ModItems.IRON_ORE.get() : ModItems.COPPER_ORE.get();
    }

    private BombMineSoil() {}
}
