package com.stardew.craft.npc.runtime;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** Optional named-point binding to a pre-existing piece of animated furniture. Never places blocks. */
public record NpcWorkstationTarget(BlockPos block, String blockId, int facing, String clip) {
    public static boolean required(String pointId) {
        var point = NpcSupportTarget.point(pointId);
        return point != null && point.has("workstation");
    }

    public static NpcWorkstationTarget resolve(ServerLevel level, String pointId) {
        var point = NpcSupportTarget.point(pointId);
        if (point == null || !point.has("workstation")) return null;
        if (!point.get("workstation").isJsonObject()) return null;
        var data = point.getAsJsonObject("workstation");
        for (String key : java.util.List.of("x", "y", "z", "block", "clip"))
            if (!data.has(key) || !data.get(key).isJsonPrimitive()) return null;
        BlockPos pos;
        try {
            pos = new BlockPos(data.get("x").getAsBigDecimal().intValueExact(),
                    data.get("y").getAsBigDecimal().intValueExact(), data.get("z").getAsBigDecimal().intValueExact());
        } catch (NumberFormatException | ArithmeticException error) { return null; }
        if (data.get("clip").getAsString().isBlank()) return null;
        if (!level.hasChunkAt(pos)) return null;
        var state = level.getBlockState(pos);
        String expected = data.get("block").getAsString();
        if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(expected)) return null;
        if (state.getBlock() instanceof MapDecorStaticBlock decor) pos = decor.findMainPos(level, pos, state);
        if (pos == null || !level.hasChunkAt(pos) || level.getBlockEntity(pos) == null) return null;
        state = level.getBlockState(pos);
        var facingProperty = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        if (!state.hasProperty(facingProperty)) return null;
        return new NpcWorkstationTarget(pos, expected, state.getValue(facingProperty).get2DDataValue(),
                data.get("clip").getAsString());
    }

    public boolean free(ServerLevel level) {
        return level.getEntitiesOfClass(com.stardew.craft.entity.npc.StardewNpcEntity.class,
                new net.minecraft.world.phys.AABB(block).inflate(4), npc -> {
                    var event = npc.getScheduleActivityEvent();
                    return event.contains("workstation") && event.getLong("workstation") == block.asLong();
                }).isEmpty();
    }

    public void write(CompoundTag event) {
        event.putLong("workstation", block.asLong());
        event.putString("workstationBlock", blockId);
        event.putInt("workstationFacing", facing);
        event.putString("workstationClip", clip);
    }

    public static boolean unchanged(ServerLevel level, CompoundTag event) {
        if (!event.contains("workstation")) return !required(event.getString("point"));
        var target = resolve(level, event.getString("point"));
        return target != null && target.block.asLong() == event.getLong("workstation")
                && target.blockId.equals(event.getString("workstationBlock"))
                && target.facing == event.getInt("workstationFacing")
                && target.clip.equals(event.getString("workstationClip"));
    }
}
