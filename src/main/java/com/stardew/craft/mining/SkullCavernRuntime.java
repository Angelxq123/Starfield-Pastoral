package com.stardew.craft.mining;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineChestBlock;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.util.StardewDeterministicRandom;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Source mineLevel 121 is depth 1. The entrance hall must never consume a playable floor. */
public final class SkullCavernRuntime {
    public static final int LOBBY=-1;
    private SkullCavernRuntime() {}
    public static boolean isForcedTreasure(int floor) { return floor==220 || floor==320 || floor==420; }
    public static double treasureChance(double dailyLuck,double luckLevel) { return .01+dailyLuck/10+luckLevel/100; }
    public static String chooseLayout(ServerLevel level,int floor) {
        var manager=MineFloorDataManager.get(level);
        int previous=manager.floorNumbers().stream().filter(f->f>120 && f<floor)
                .filter(f->manager.getFloorData(f).getGenerationVersion()==OrdinaryMineRuntime.VERSION).mapToInt(Integer::intValue).max().orElse(-1);
        int previousMap=-1;
        if(previous>120) {
            var old=OrdinaryMineLayout.load(level,previous);
            if(old.metadata.has("source_layout_number"))previousMap=old.metadata.get("source_layout_number").getAsInt();
        }
        int map;
        do {map=level.random.nextInt(40);} while(map==previousMap);
        while(map%5==0)map=level.random.nextInt(40);
        if(isForcedTreasure(floor))return "desert_reward_"+(floor-120);
        if(floor>=130) {
            var players=level.players().stream().filter(p->OrdinaryMineRuntime.floorAt(p.blockPosition())==floor).toList();
            // The entering player may still be in the previous floor; use the online party then.
            if(players.isEmpty())players=level.getServer().getPlayerList().getPlayers();
            double daily=players.stream().mapToDouble(PlayerStardewDataAPI::getDailyLuck).average().orElse(0);
            double luck=players.stream().mapToInt(PlayerStardewDataAPI::getLuckBuffLevel).average().orElse(0);
            if(level.random.nextDouble()<treasureChance(daily,luck))return "desert_10";
        }
        var alternate=StardewDeterministicRandom.createFromDoubles(StardewTimeManager.get().getAbsoluteDay(),level.getSeed()/2L,1293857+floor*400.0,0,0);
        if(alternate.nextDouble()<.06+Math.min(.06,floor/10000.0)) {
            map=40+alternate.nextInt(21);
            if(Set.of(40,47,50,51).contains(map) && alternate.nextDouble()<.75)map=40+alternate.nextInt(21);
            if(map==40)map=52+alternate.nextInt(9);
            return "extra_desert"+(map==45?"_dark":"")+"_"+map;
        }
        return "desert_"+String.format(Locale.ROOT,"%02d",map);
    }
    public static int shaftLevels(int floor,long seed,int day) {
        var random=StardewDeterministicRandom.createFromDoubles(floor,seed,day,0,0);
        int count=3+random.nextInt(6);
        if(random.nextDouble()<.1)count=count*2-1;
        return floor<220 && floor+count>220 ? 220-floor : count;
    }
    public static void placeTreasureChests(ServerLevel level,int floor,OrdinaryMineLayout layout,MineFloorData data) {
        if(!data.isTreasureRoom())return;
        int[] columns=floor==320?new int[]{10,8}:floor==420?new int[]{9,7,11}:new int[]{9};
        for(int x:columns)level.setBlock(layout.position(floor,x,9),ModBlocks.MINE_CHEST.get().defaultBlockState()
                .setValue(MineChestBlock.FACING,Direction.SOUTH).setValue(MineChestBlock.SPECIAL,isForcedTreasure(floor)),2);
    }
}
