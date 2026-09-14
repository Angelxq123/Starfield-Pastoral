package com.stardew.craft.mining;

import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.manager.ArtifactDropService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.util.StardewDeterministicRandom;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Ordinary BreakableContainer rewards preceding the theme table (SDV 1.6). */
public final class MineContainerRewards {
    private MineContainerRewards() {}

    public record Roll(boolean continueToContents, List<ItemStack> items) {}

    public static RandomSource random(ServerLevel level, BlockPos pos, int floor) {
        int x = pos.getX(), z = pos.getZ();
        boolean mine = level.dimension() == ModMiningDimensions.STARDEW_MINING;
        if (mine && floor > 0) {
            var layout = OrdinaryMineLayout.load(level, floor);
            var origin = layout.origin(floor);
            x -= origin.getX() + layout.tileX;
            z -= origin.getZ() + layout.tileZ;
        }
        // Source CreateRandom deliberately does not include the save/world seed.
        return StardewDeterministicRandom.createFromDoubles(x, z * 10000.0,
                StardewTimeManager.get().getAbsoluteDay(), mine ? floor : 0, 0).asMinecraftSource();
    }

    public static Roll roll(ServerPlayer player, RandomSource random) {
        return roll(player, random, () -> {});
    }

    public static Roll roll(ServerPlayer player, RandomSource random, Runnable onNonempty) {
        var time = StardewTimeManager.get();
        var data = player == null ? null : PlayerDataManager.getPlayerData(player);
        double luck = ArtifactDropService.averageDailyLuck(player);
        var drops = new ArrayList<ItemStack>();
        if (random.nextDouble() < .2) {
            if (random.nextDouble() < .1) drops.add(ArtifactDropService.rollSeedDrop(
                    time.getCurrentSeason(), time.getCurrentDay(), luck, random));
            return new Roll(false, List.copyOf(drops));
        }
        // Festival eggs and trinkets precede the deterministic auxiliary rolls in the source.
        onNonempty.run();
        // The absent Qi bean quest is inactive. Its leading probability draw still occurs
        // in the source even when the quest is inactive; preserve subsequent table rolls.
        random.nextDouble();
        if (data != null && data.hasMailFlag("sawQiPlane") && random.nextDouble()
                < BookPowerEffects.applyMysteryBoxChance(data, .0081 + luck / 15.0)) {
            drops.add(new ItemStack(data.hasMastery(SkillType.FORAGING)
                    ? ModItems.GOLDEN_MYSTERY_BOX.get() : ModItems.MYSTERY_BOX.get()));
        }
        // Utility.trySpawnRareObject(who, ..., chanceModifier: 1.5, dailyLuckWeight: 1).
        if (data != null && data.hasMastery(SkillType.FARMING)
                && random.nextDouble() < .0015 * (1 + luck))
            drops.add(new ItemStack(ModItems.GOLDEN_ANIMAL_CRACKER.get()));
        if (time.getAbsoluteDay() > 2 && random.nextDouble() < .003) {
            ItemStack cosmetic = ArtifactDropService.rollCosmetic(random);
            if (!cosmetic.isEmpty()) drops.add(cosmetic);
        }
        if (time.getAbsoluteDay() > 2 && random.nextDouble() < .0009)
            drops.add(new ItemStack(ModItems.BOOKS.get("skill_book_" + random.nextInt(5)).get()));
        return new Roll(true, List.copyOf(drops));
    }
}
