package com.stardew.craft.manager;

import com.stardew.craft.block.utility.SprinklerBlock;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.time.settlement.DailySettlementContext;
import com.stardew.craft.time.settlement.DailySettlementContextFactory;
import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class SprinklerManager extends SavedData {
    private static final String DATA_NAME = "stardew_sprinkler_manager";

    private final Set<GlobalPos> sprinklerPositions = new HashSet<>();

    /** 返回所有已注册洒水器位置的不可变快照。 */
    public java.util.List<GlobalPos> getAllSprinklerPositions() {
        return new java.util.ArrayList<>(sprinklerPositions);
    }

    private boolean isProcessing = false;
    private final Set<GlobalPos> pendingAdds = new HashSet<>();
    private final Set<GlobalPos> pendingRemoves = new HashSet<>();

    @SuppressWarnings("null")
    public void addSprinkler(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        if (isProcessing) {
            pendingAdds.add(gp);
            pendingRemoves.remove(gp);
            setDirty();
            return;
        }
        if (sprinklerPositions.add(gp)) {
            setDirty();
        }
    }

    @SuppressWarnings("null")
    public void removeSprinkler(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
        if (isProcessing) {
            pendingRemoves.add(gp);
            pendingAdds.remove(gp);
            setDirty();            return;
        }
        if (sprinklerPositions.remove(gp)) {
            setDirty();
        }
    }

    private void applyPendingChanges() {
        boolean changed = false;
        if (!pendingRemoves.isEmpty()) {
            changed |= sprinklerPositions.removeAll(pendingRemoves);
            pendingRemoves.clear();
        }
        if (!pendingAdds.isEmpty()) {
            changed |= sprinklerPositions.addAll(pendingAdds);
            pendingAdds.clear();
        }
        if (changed) {
            setDirty();
        }
    }

    @SuppressWarnings("null")
    public void waterDaily(ServerLevel level) {
        DailySettlementWorkUnits.drain(createDailyWorkUnit(
                level,
                DailySettlementContextFactory.captureCurrentDay(StardewTimeManager.get())));
    }

    public DailySettlementWorkUnit createDailyWorkUnit(
            ServerLevel level,
            DailySettlementContext context) {
        if (isProcessing) {
            throw new IllegalStateException("Sprinkler daily work is already active");
        }
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(context, "context");
        isProcessing = true;
        try {
            List<GlobalPos> snapshot = new ArrayList<>(sprinklerPositions);
            return DailySettlementWorkUnits.cursor(
                    "sprinkler_watering",
                    snapshot,
                    SprinklerManager::dailyItemIdentity,
                    globalPos -> processSprinklerDay(level, globalPos),
                    this::finishDailyProcessing);
        } catch (RuntimeException | Error exception) {
            finishDailyProcessing();
            throw exception;
        }
    }

    private void processSprinklerDay(ServerLevel level, GlobalPos globalPos) {
        if (globalPos.dimension() != level.dimension()) {
            return;
        }
        BlockPos pos = globalPos.pos();
        if (!com.stardew.craft.farm.FarmDailyProcessHelper.shouldProcessPosition(level, pos)) {
            return;
        }
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SprinklerBlock sprinkler)) {
            removeSprinkler(level, pos);
            return;
        }
        SprinklerBlock.waterNow(level, pos, sprinkler.getTier(), false);
    }

    private void finishDailyProcessing() {
        isProcessing = false;
        applyPendingChanges();
    }

    private static String dailyItemIdentity(GlobalPos globalPos) {
        Objects.requireNonNull(globalPos, "globalPos");
        return globalPos.dimension().location() + ":" + globalPos.pos().toShortString();
    }

    @SuppressWarnings("null")
    @Override
    public CompoundTag save(@SuppressWarnings("null") CompoundTag tag,
                            @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (GlobalPos pos : sprinklerPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putString("Dimension", pos.dimension().location().toString());
            posTag.put("Pos", NbtUtils.writeBlockPos(pos.pos()));
            list.add(posTag);
        }
        tag.put("Sprinklers", list);
        return tag;
    }

    @SuppressWarnings("null")
    public static SprinklerManager load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
        SprinklerManager manager = new SprinklerManager();
        if (tag.contains("Sprinklers", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Sprinklers", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag posTag = list.getCompound(i);
                ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(posTag.getString("Dimension")));
                BlockPos pos = NbtUtils.readBlockPos(posTag, "Pos").orElse(BlockPos.ZERO);
                manager.sprinklerPositions.add(GlobalPos.of(dim, pos));
            }
        }
        return manager;
    }

    @SuppressWarnings("null")
    public static SprinklerManager get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        SprinklerManager::new,
                        SprinklerManager::load,
                        null
                ),
                DATA_NAME
        );
    }
}
