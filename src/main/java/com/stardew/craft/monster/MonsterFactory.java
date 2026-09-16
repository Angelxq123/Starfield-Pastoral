package com.stardew.craft.monster;

import com.stardew.craft.mining.MineFloorData;
import com.stardew.craft.mining.MineFloorDataManager;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.OrdinaryMineRuntime;
import net.minecraft.server.level.ServerLevel;

/** Common insertion/ownership rules. Species resolve their own ordered constructor rules. */
public final class MonsterFactory {
    private MonsterFactory() {}
    public static boolean add(ServerLevel level, StardewMonsterEntity monster) {
        if (!monster.initialized()) throw new IllegalStateException("Initialize monster before insertion");
        var owner = monster.monsterState().context();
        if (owner.generation() != null) {
            monster.setPersistenceRequired(); monster.addTag(OrdinaryMineRuntime.MOB_TAG);
        }
        return com.stardew.craft.event.MineMonsterSpawnHandler.addWithSpawnReason(level, monster);
    }
    public static MineFloorData ownedFloor(StardewMonsterEntity monster) {
        if (!monster.initialized() || !(monster.level() instanceof ServerLevel level)
                || !level.dimension().equals(ModMiningDimensions.STARDEW_MINING)) return null;
        var context = monster.monsterState().context();
        if (context.generation() == null) return null;
        var data = MineFloorDataManager.get(level).getFloorData(context.floor());
        return data != null && context.generation().equals(data.generationId()) ? data : null;
    }
    /** Initial population is held by the caller until the floor is published. Children use the published owner. */
    public static boolean addOffspring(ServerLevel level, StardewMonsterEntity child) {
        var data = ownedFloor(child);
        if (child.monsterState().context().generation() != null && data == null) return false;
        if (!add(level, child)) return false;
        if (data != null) {
            data.addGeneratedMonster(child.getUUID());
            MineFloorDataManager.get(level).setFloorData(child.monsterState().context().floor(), data);
        }
        return true;
    }
    /** Metamorphosis is insertion followed by retirement, never a death or a second kill roll. */
    public static boolean replace(StardewMonsterEntity parent, StardewMonsterEntity child) {
        if (!parent.isAlive() || parent.monsterState().life() != MonsterState.Life.ALIVE
                || !(parent.level() instanceof ServerLevel level) || child.level() != level
                || !child.monsterState().context().equals(parent.monsterState().context().offspring())) return false;
        if (!addOffspring(level, child)) return false;
        parent.monsterState().life(MonsterState.Life.TRANSFORMING);
        var data = ownedFloor(parent);
        if (data != null && data.removeGeneratedMonster(parent.getUUID()))
            MineFloorDataManager.get(level).setFloorData(parent.monsterState().context().floor(), data);
        parent.discard();
        return true;
    }
    public static void cleanup(StardewMonsterEntity monster) {
        if(monster.monsterState().life()!=MonsterState.Life.ALIVE&&monster.monsterState().life()!=MonsterState.Life.DOWNED)return;
        monster.monsterState().life(MonsterState.Life.CLEANUP);
        var data=ownedFloor(monster);
        if(data!=null&&data.removeGeneratedMonster(monster.getUUID()))
            MineFloorDataManager.get((ServerLevel)monster.level()).setFloorData(monster.monsterState().context().floor(),data);
        monster.discard();
    }
    static void removePopulation(StardewMonsterEntity monster) {
        var data = ownedFloor(monster);
        if (data != null && monster.claimSettlement(MonsterState.Settlement.POPULATION)
                && data.removeGeneratedMonster(monster.getUUID())) {
            MineFloorDataManager.get((ServerLevel) monster.level()).setFloorData(monster.monsterState().context().floor(), data);
        }
    }
}
