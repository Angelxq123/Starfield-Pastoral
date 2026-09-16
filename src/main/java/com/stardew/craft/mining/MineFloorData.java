package com.stardew.craft.mining;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * 矿井楼层数据
 * 
 * 管理每层的状态：
 * - stonesLeft: 剩余的"可计数石头"数量（用于梯子概率计算）
 * - ladderFound: 本层是否已生成梯子
 * - enemyCount: 本层敌人数量（用于梯子加成判断）
 * - isMonsterArea: 是否为清怪层
 */
public class MineFloorData {
    private int stonesLeft;
    private boolean ladderFound;
    private boolean stoneLadderSpawned;
    private final java.util.Set<java.util.UUID> generatedMonsters = new java.util.HashSet<>();
    public boolean hasStoneLadderSpawned() { return stoneLadderSpawned; }
    public void setStoneLadderSpawned(boolean value) { stoneLadderSpawned=value; }
    public void addGeneratedMonster(java.util.UUID id) { if(generatedMonsters.add(id)) enemyCount++; }
    public boolean removeGeneratedMonster(java.util.UUID id) {
        if(!generatedMonsters.remove(id)) return false;
        enemyCount = Math.max(0, enemyCount - 1);return true;
    }
    private BlockPos ladderPos;
    private int enemyCount;
    private boolean isMonsterArea;
    private int generationVersion;
    private java.util.UUID generationId = java.util.UUID.randomUUID();
    public java.util.UUID generationId() { return generationId; }
    private boolean treasureRoom;
    private final java.util.Set<Integer> treasureKeys=new java.util.HashSet<>();
    public java.util.Set<Integer> treasureKeys() {return java.util.Set.copyOf(treasureKeys);}
    public void addTreasureKey(int key) {treasureKeys.add(key); }
    public boolean isTreasureRoom() { return treasureRoom; }
    public void setTreasureRoom(boolean value) { treasureRoom=value; }
    private String layoutName="";
    private java.util.BitSet architecture = new java.util.BitSet();
    public void markArchitecture(int index) { architecture.set(index); }
    public boolean isArchitecture(int index) { return index>=0 && architecture.get(index); }
    public String getLayoutName() { return layoutName; }
    public void setLayoutName(String name) { layoutName=name; }
    private final java.util.Set<Long> generatedStones = new java.util.HashSet<>();

    public void addGeneratedStone(BlockPos pos) { addGeneratedStone(pos, true); }
    public void addGeneratedStone(BlockPos pos, boolean initialPopulation) {
        if (generatedStones.add(pos.asLong()) && initialPopulation) stonesLeft++;
    }
    public int generatedStoneCount() { return generatedStones.size(); }
    public boolean removeGeneratedStone(BlockPos pos) {
        boolean removed = generatedStones.remove(pos.asLong());
        if (removed) stonesLeft = Math.max(0, stonesLeft - 1);
        return removed;
    }
    /** Source removeObjectsAndSpawned bypasses the mining counter and ladder roll. */
    public void forgetGeneratedStone(BlockPos pos) { generatedStones.remove(pos.asLong()); }
    public boolean isGeneratedStone(BlockPos pos) { return generatedStones.contains(pos.asLong()); }
    
    public MineFloorData() {
        this.stonesLeft = 0;
        this.ladderFound = false;
    this.ladderPos = null;
        this.enemyCount = 0;
        this.isMonsterArea = false;
        this.generationVersion = 0;
    }
    
    
    // Getters
    public int getStonesLeft() {
        return stonesLeft;
    }
    
    public boolean hasLadderFound() {
        return ladderFound;
    }

    public BlockPos getLadderPos() {
        return ladderPos;
    }

    
    public int getEnemyCount() {
        return enemyCount;
    }
    
    public boolean isMonsterArea() {
        return isMonsterArea;
    }

    public int getGenerationVersion() {
        return generationVersion;
    }
    
    // Setters
    public void setStonesLeft(int stonesLeft) {
        this.stonesLeft = Math.max(0, stonesLeft);
    }
    
    public void setLadderFound(boolean ladderFound) {
        this.ladderFound = ladderFound;
    }

    public void setLadderPos(BlockPos ladderPos) {
        this.ladderPos = ladderPos;
    }

    
    public void setEnemyCount(int enemyCount) {
        this.enemyCount = Math.max(0, enemyCount);
    }
    
    public void setMonsterArea(boolean isMonsterArea) {
        this.isMonsterArea = isMonsterArea;
    }

    public void setGenerationVersion(int generationVersion) {
        this.generationVersion = generationVersion;
    }
    
    
    
    /**
     * 序列化到NBT
     */
    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stonesLeft", stonesLeft);
        tag.putBoolean("ladderFound", ladderFound);
        tag.putBoolean("stoneLadderSpawned",stoneLadderSpawned);
        var monsters=new CompoundTag();generatedMonsters.forEach(id->monsters.putBoolean(id.toString(),true));tag.put("generatedMonsters",monsters);
        if (ladderPos != null) {
            tag.putLong("ladderPos", ladderPos.asLong());
        }
        tag.putInt("enemyCount", enemyCount);
        tag.putBoolean("isMonsterArea", isMonsterArea);
        tag.putInt("generationVersion", generationVersion);
        tag.putUUID("generationId", generationId);
        tag.putBoolean("treasureRoom",treasureRoom);
        tag.putIntArray("treasureKeys",treasureKeys.stream().mapToInt(Integer::intValue).toArray());
        tag.putString("layoutName",layoutName);
        tag.putLongArray("architecture",architecture.toLongArray());
        tag.putLongArray("generatedStones", generatedStones.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
    
    /**
     * 从NBT反序列化
     */
    public static MineFloorData fromNBT(CompoundTag tag) {
        MineFloorData data = new MineFloorData();
        data.stonesLeft = tag.getInt("stonesLeft");
        data.ladderFound = tag.getBoolean("ladderFound");
        data.stoneLadderSpawned=tag.getBoolean("stoneLadderSpawned");
        for(String id:tag.getCompound("generatedMonsters").getAllKeys()) data.generatedMonsters.add(java.util.UUID.fromString(id));
        if (tag.contains("ladderPos")) {
            data.ladderPos = BlockPos.of(tag.getLong("ladderPos"));
        }
        data.enemyCount = tag.getInt("enemyCount");
        data.isMonsterArea = tag.getBoolean("isMonsterArea");
        data.generationVersion = tag.getInt("generationVersion");
        if (tag.hasUUID("generationId")) data.generationId = tag.getUUID("generationId");
        data.treasureRoom=tag.getBoolean("treasureRoom");
        for(int key:tag.getIntArray("treasureKeys"))data.treasureKeys.add(key);
        data.layoutName=tag.getString("layoutName");
        data.architecture=java.util.BitSet.valueOf(tag.getLongArray("architecture"));
        for (long pos : tag.getLongArray("generatedStones")) data.generatedStones.add(pos);
        return data;
    }
}
