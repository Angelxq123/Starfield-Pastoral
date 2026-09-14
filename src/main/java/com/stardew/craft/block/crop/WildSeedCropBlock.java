package com.stardew.craft.block.crop;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.manager.CropGrowthManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.function.Supplier;

/**
 * 野生种子作物方块 — 种下后经历2个生长阶段，成熟后自动变成对应季节的采集物方块(ForageBlock)。
 * SDV: Crops.json phases [3,4], SpriteIndex 23, 成熟后 newDay() 替换为地面采集物。
 * 出苗时固定选择原版六种苗态之一，成熟后替换为采集物。
 */
@SuppressWarnings("null")
public class WildSeedCropBlock extends StardewCropBlock {

    /** SDV wild seed crop: 2 growth phases, 3 days + 4 days = 7 days total */
    private static final int[] PHASE_DAYS = new int[]{3, 4};
    public static final IntegerProperty WILD_VARIANT = IntegerProperty.create("wild_variant", 0, 5);

    private final int season; // 0=spring, 1=summer, 2=fall, 3=winter
    private final Supplier<Item> seedsItem;

    public WildSeedCropBlock(int season, Supplier<Item> seedsItem) {
        super(Properties.of()
                .mapColor(MapColor.PLANT)
                .pushReaction(PushReaction.DESTROY)
                .sound(SoundType.CROP));
        this.season = season;
        this.seedsItem = seedsItem;
        registerDefaultState(defaultBlockState().setValue(WILD_VARIANT, 0));
    }

    @Override
    protected void addExtraProperties(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WILD_VARIANT);
    }

    static BlockState selectFirstSproutVariant(BlockState before, BlockState after, RandomSource random) {
        return before.getValue(AGE) == 0 && after.getValue(AGE) > 0
                ? after.setValue(WILD_VARIANT, random.nextInt(6)) : after;
    }

    @Override
    protected int[] getPhaseDays() {
        return PHASE_DAYS;
    }

    @Override
    protected Supplier<Item> getSeedsItem() {
        return seedsItem;
    }

    @Override
    protected Supplier<Item> getCropItem() {
        return seedsItem;
    }

    @Override
    protected boolean isInSeason(Level level) {
        if (level.isClientSide()) return true;
        return seasonForGrowth() == season;
    }

    @Override
    protected ItemStack getHarvestItem(int quality) {
        return new ItemStack(seedsItem.get());
    }

    @Override
    protected boolean canRegrow() {
        return false;
    }

    @Override
    protected int getRegrowAge() {
        return 0;
    }

    @Override
    public String getCropDisplayNameKey() {
        return switch (season) {
            case 0 -> "item.stardewcraft.spring_seeds";
            case 1 -> "item.stardewcraft.summer_seeds";
            case 2 -> "item.stardewcraft.fall_seeds";
            case 3 -> "item.stardewcraft.winter_seeds";
            default -> "item.stardewcraft.wild_seeds";
        };
    }

    /**
     * Override daily growth: when the crop reaches maturity,
     * transform it into a random seasonal ForageBlock.
     */
    @SuppressWarnings("null")
    @Override
    public void growCropOneDay(ServerLevel level, BlockPos pos, BlockState state,
                               boolean watered, CropGrowthManager.CropGrowthState growthState) {
        super.growCropOneDay(level, pos, state, watered, growthState);

        // Check if we just reached maturity
        BlockState currentState = level.getBlockState(pos);
        if (currentState.getBlock() == this) {
            BlockState selected = selectFirstSproutVariant(state, currentState, level.getRandom());
            if (selected != currentState) {
                level.setBlock(pos, selected, Block.UPDATE_CLIENTS);
                currentState = selected;
            }
        }
        if (currentState.getBlock() == this && currentState.getValue(AGE) == MAX_AGE) {
            transformToForage(level, pos);
        }
    }

    /**
     * Replace this crop block with a random ForageBlock for the corresponding season.
     * ForageBlock.mayPlaceOn now supports FarmBlock, so forage survives on farmland.
     */
    @SuppressWarnings("null")
    private void transformToForage(ServerLevel level, BlockPos pos) {
        Block forageBlock = pickRandomForage(level.getRandom());
        if (forageBlock != null) {
            level.setBlock(pos, forageBlock.defaultBlockState(), 3);
        } else {
            // 兜底：万一没找到合适的 forage 方块，也不要把成熟的种子作物留在原地
            // （否则玩家收获时会掉落种子本身这种诡异结果）。直接清空。
            level.removeBlock(pos, false);
        }
    }

    /**
     * SDV Crop.getRandomWildCropForSeason — picks a random forage block.
     */
    Block pickRandomForage(net.minecraft.util.RandomSource random) {
        return switch (season) {
            case 0 -> switch (random.nextInt(4)) {
                case 0 -> ModBlocks.FORAGE_WILD_HORSERADISH.get();
                case 1 -> ModBlocks.FORAGE_DAFFODIL.get();
                case 2 -> ModBlocks.FORAGE_LEEK.get();
                default -> ModBlocks.FORAGE_DANDELION.get();
            };
            case 1 -> switch (random.nextInt(3)) {
                case 0 -> ModBlocks.FORAGE_SPICE_BERRY.get();
                case 1 -> ModBlocks.FORAGE_SWEET_PEA.get();
                default -> ModBlocks.FORAGE_GRAPE.get();
            };
            case 2 -> switch (random.nextInt(4)) {
                case 0 -> ModBlocks.FORAGE_COMMON_MUSHROOM.get();
                case 1 -> ModBlocks.FORAGE_WILD_PLUM.get();
                case 2 -> ModBlocks.FORAGE_HAZELNUT.get();
                default -> ModBlocks.FORAGE_BLACKBERRY.get();
            };
            case 3 -> switch (random.nextInt(4)) {
                case 0 -> ModBlocks.FORAGE_WINTER_ROOT.get();
                case 1 -> ModBlocks.FORAGE_CRYSTAL_FRUIT.get();
                case 2 -> ModBlocks.FORAGE_SNOW_YAM.get();
                default -> ModBlocks.FORAGE_CROCUS.get();
            };
            default -> null;
        };
    }

}
