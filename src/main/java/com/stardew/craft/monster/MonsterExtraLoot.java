package com.stardew.craft.monster;

import com.stardew.craft.entity.monster.GreenSlimeEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.mining.OrdinaryMineLayout;
import com.stardew.craft.player.*;
import com.stardew.craft.specialorder.SpecialOrderManager;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.util.StardewDeterministicRandom;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.manager.ArtifactDropService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Death-time methods stay separate from the spawn table, so Burglar independently calls them twice. */
public final class MonsterExtraLoot {
    private MonsterExtraLoot() {}
    public static List<ItemStack> roll(LivingEntity monster, ServerPlayer player, RandomSource random) {
        var result = new ArrayList<ItemStack>();
        var tags = monster.getTags();
        if (tags.contains("sd_mob_prismatic_slime") && player != null
                && SpecialOrderManager.hasActiveIncompleteOrder(player.serverLevel(), "Wizard2")) {
            result.add(new ItemStack(ModItems.PRISMATIC_JELLY.get()));
            return result;
        }
        if (monster instanceof GreenSlimeEntity slime) {
            double x = slime.getX(), z = slime.getZ();
            if (MonsterFactory.ownedFloor(slime) != null) {
                var layout = OrdinaryMineLayout.load((net.minecraft.server.level.ServerLevel) slime.level(), slime.sourceFloor());
                var origin = layout.origin(slime.sourceFloor()); x -= origin.getX() + layout.tileX + .5; z -= origin.getZ() + layout.tileZ + .5;
            }
            var positional = StardewDeterministicRandom.createFromDoubles(x * 64 * 777, z * 64 * 77,
                    StardewTimeManager.get().getAbsoluteDay(), 0, 0).asMinecraftSource();
            for (var drop : SlimeLootRules.colorDrops(slime.color(), slime.specialNumber(), slime.firstGeneration(), random, positional))
                add(result, drop.id(), drop.count());
        }
        if (tags.contains("sd_mob_metal_head") && player != null
                && MetalHeadLoot.hasHelmet(PlayerDataManager.getPlayerData(player).getMonsterKills("source:Metal Head"),
                    player.serverLevel().getServer().overworld().getSeed())) result.add(new ItemStack(ModItems.SQUIRES_HELMET.get()));
        if (tags.contains("sd_mob_dino")) {
            if (random.nextDouble() < 0.10000000149011612) add(result, "107", 1);
            else add(result, new String[]{"580", "583", "584"}[random.nextInt(3)], 1);
        }
        if ((tags.contains("sd_mob_serpent") || tags.contains("sd_mob_royal_serpent") || tags.contains("sd_mob_mummy"))
                && random.nextDouble() < .002) add(result, "485", 1);
        if (tags.contains("sd_mob_skeleton") && random.nextDouble() < .04) result.add(new ItemStack(ModItems.BONE_SWORD.get()));
        if (tags.contains("sd_mob_armored_bug") && random.nextDouble() <= .1) add(result, "874", 1);
        if (tags.contains("sd_mob_ghost") && random.nextDouble() < .095 && player != null
                && SpecialOrderManager.hasActiveIncompleteOrder(player.serverLevel(), "Wizard")
                && !SpecialOrderManager.hasSpecialDropFlag(player, "ectoplasmDrop")) result.add(new ItemStack(ModItems.ECTOPLASM.get()));
        if (monster instanceof com.stardew.craft.entity.monster.MineRockGolemEntity golem && golem.isFarmGolem()) {
            var time = StardewTimeManager.get();
            double luck = player == null ? 0 : ArtifactDropService.averageDailyLuck(player);
            for (var drop : FarmGolemRules.extraDrops(golem.isIridium(), time.getCurrentSeason(), time.getCurrentDay(), luck, random))
                add(result, drop.id(), drop.count());
        }
        String held = monster.getPersistentData().getString("StardewMonsterHeldLoot");
        if (!held.isBlank()) add(result, held, 1);
        return result;
    }
    private static void add(List<ItemStack> out, String id, int count) {
        var stack = MonsterSourceLoot.item(id, count); if (!stack.isEmpty()) out.add(stack);
    }
    /** GameLocation common rewards occur after Book_Void duplication, not inside it. */
    public static List<ItemStack> common(ServerPlayer player, LivingEntity monster, RandomSource random) {
        var out = new ArrayList<ItemStack>(); var data = PlayerDataManager.getPlayerData(player);
        double luck = ArtifactDropService.averageDailyLuck(player);
        int day = StardewTimeManager.get().getAbsoluteDay();
        if (data.hasMastery(SkillType.FARMING) && random.nextDouble() < .0015 * (1 + luck)) out.add(new ItemStack(ModItems.GOLDEN_ANIMAL_CRACKER.get()));
        if (day > 2 && random.nextDouble() < .003) { var item = ArtifactDropService.rollCosmetic(random); if (!item.isEmpty()) out.add(item); }
        if (day > 2 && random.nextDouble() < .0009) out.add(new ItemStack(ModItems.BOOKS.get("skill_book_" + random.nextInt(5)).get()));
        if (data.hasMailFlag("sawQiPlane") && random.nextDouble() < BookPowerEffects.applyMysteryBoxChance(data,
                .01 + luck / 10 + PlayerStardewDataAPI.getLuckBuffLevel(player) * .008))
            out.add(new ItemStack(data.hasMastery(SkillType.FORAGING) ? ModItems.GOLDEN_MYSTERY_BOX.get() : ModItems.MYSTERY_BOX.get()));
        return out;
    }
}
