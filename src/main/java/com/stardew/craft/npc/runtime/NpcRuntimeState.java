package com.stardew.craft.npc.runtime;

import net.minecraft.nbt.CompoundTag;

/**
 * Persistent runtime state for one NPC.
 */
@SuppressWarnings("null")
public final class NpcRuntimeState {
    private final String npcId;
    private String locationName;
    private String activeScheduleKey;
    private int scheduleCheckpoint;
    private int tileX;
    private int tileY;
    private int facing;
    private int scheduleNodeIndex;
    private String routeBehaviorToken;
    /**
     * Named route-point ID resolved from the active schedule node (\"@point_id\" format).
     * Empty string when the node uses legacy tile-offset or anchor logic.
     */
    private String namedPointId;
    private boolean pathingSuppressed;
    private ActualPosition actualPosition;
    public record ActualPosition(String dimension,double x,double y,double z,float yaw,int day) {
        public ActualPosition {
            if (dimension == null || dimension.isBlank() || !Double.isFinite(x) || !Double.isFinite(y)
                    || !Double.isFinite(z) || !Float.isFinite(yaw)) throw new IllegalArgumentException("Invalid NPC position");
        }
    }
    public ActualPosition actualPosition() { return actualPosition; }
    public boolean rememberPosition(ActualPosition position) {
        if (java.util.Objects.equals(actualPosition,position)) return false;
        actualPosition = position;
        return true;
    }


    public NpcRuntimeState(String npcId) {
        this.npcId = npcId;
        this.locationName = "Town";
        this.activeScheduleKey = "default";
        this.scheduleCheckpoint = 600;
        this.tileX = 0;
        this.tileY = 0;
        this.facing = 2;
        this.scheduleNodeIndex = 0;
        this.routeBehaviorToken = "";
        this.namedPointId = "";
        this.pathingSuppressed = false;
    }

    /** Clear desired work without discarding physical recovery data or caller-owned suppression. */
    public boolean clearSchedule() {
        boolean changed = !activeScheduleKey.isEmpty() || !locationName.isEmpty()
                || !namedPointId.isEmpty() || !routeBehaviorToken.isEmpty();
        activeScheduleKey = ""; locationName = ""; namedPointId = ""; routeBehaviorToken = "";
        scheduleCheckpoint = 0; scheduleNodeIndex = 0; tileX = 0; tileY = 0;
        return changed;
    }

    public String npcId() {
        return npcId;
    }

    public String locationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        if (locationName != null && !locationName.isBlank()) {
            this.locationName = locationName;
        }
    }

    public String activeScheduleKey() {
        return activeScheduleKey;
    }

    public void setActiveScheduleKey(String activeScheduleKey) {
        if (activeScheduleKey != null && !activeScheduleKey.isBlank()) {
            this.activeScheduleKey = activeScheduleKey;
        }
    }

    public int scheduleCheckpoint() {
        return scheduleCheckpoint;
    }

    public void setScheduleCheckpoint(int scheduleCheckpoint) {
        this.scheduleCheckpoint = Math.max(0, scheduleCheckpoint);
    }

    public int tileX() {
        return tileX;
    }

    public void setTileX(int tileX) {
        this.tileX = tileX;
    }

    public int tileY() {
        return tileY;
    }

    public void setTileY(int tileY) {
        this.tileY = tileY;
    }

    public int facing() {
        return facing;
    }

    public void setFacing(int facing) {
        this.facing = facing;
    }

    public int scheduleNodeIndex() {
        return scheduleNodeIndex;
    }

    public void setScheduleNodeIndex(int scheduleNodeIndex) {
        this.scheduleNodeIndex = Math.max(0, scheduleNodeIndex);
    }

    public String routeBehaviorToken() {
        return routeBehaviorToken;
    }

    public void setRouteBehaviorToken(String routeBehaviorToken) {
        this.routeBehaviorToken = routeBehaviorToken == null ? "" : routeBehaviorToken;
    }

    public String namedPointId() {
        return namedPointId;
    }

    public void setNamedPointId(String namedPointId) {
        this.namedPointId = namedPointId == null ? "" : namedPointId;
    }

    public boolean pathingSuppressed() {
        return pathingSuppressed;
    }

    public void setPathingSuppressed(boolean pathingSuppressed) {
        this.pathingSuppressed = pathingSuppressed;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        if (actualPosition != null) {
            var p = new CompoundTag();
            p.putString("Dimension",actualPosition.dimension());
            p.putDouble("X",actualPosition.x()); p.putDouble("Y",actualPosition.y()); p.putDouble("Z",actualPosition.z());
            p.putFloat("Yaw",actualPosition.yaw()); p.putInt("Day",actualPosition.day());
            tag.put("ActualPosition",p);
        }
        tag.putString("NpcId", npcId);
        tag.putString("LocationName", locationName);
        tag.putString("ActiveScheduleKey", activeScheduleKey);
        tag.putInt("ScheduleCheckpoint", scheduleCheckpoint);
        tag.putInt("TileX", tileX);
        tag.putInt("TileY", tileY);
        tag.putInt("Facing", facing);
        tag.putInt("ScheduleNodeIndex", scheduleNodeIndex);
        tag.putString("RouteBehaviorToken", routeBehaviorToken);
        tag.putString("NamedPointId", namedPointId);
        tag.putBoolean("PathingSuppressed", pathingSuppressed);
        return tag;
    }

    public static NpcRuntimeState fromNbt(CompoundTag tag) {
        String npcId = tag.getString("NpcId");
        NpcRuntimeState state = new NpcRuntimeState(npcId);
        state.locationName = tag.contains("LocationName") ? tag.getString("LocationName") : "Town";
        state.activeScheduleKey = tag.contains("ActiveScheduleKey") ? tag.getString("ActiveScheduleKey") : "default";
        state.scheduleCheckpoint = tag.contains("ScheduleCheckpoint") ? Math.max(0, tag.getInt("ScheduleCheckpoint")) : 600;
        state.tileX = tag.getInt("TileX");
        state.tileY = tag.getInt("TileY");
        state.facing = tag.contains("Facing") ? tag.getInt("Facing") : 2;
        state.scheduleNodeIndex = Math.max(0, tag.getInt("ScheduleNodeIndex"));
        state.routeBehaviorToken = tag.contains("RouteBehaviorToken") ? tag.getString("RouteBehaviorToken") : "";
        state.namedPointId = tag.contains("NamedPointId") ? tag.getString("NamedPointId") : "";
        state.pathingSuppressed = tag.getBoolean("PathingSuppressed");
        if (tag.contains("ActualPosition")) {
            var p=tag.getCompound("ActualPosition");
            try { state.actualPosition=new ActualPosition(p.getString("Dimension"),p.getDouble("X"),p.getDouble("Y"),p.getDouble("Z"),p.getFloat("Yaw"),p.getInt("Day")); }
            catch (IllegalArgumentException invalid) { /* Old or damaged records recover from their schedule. */ }
        }
        return state;
    }
}
