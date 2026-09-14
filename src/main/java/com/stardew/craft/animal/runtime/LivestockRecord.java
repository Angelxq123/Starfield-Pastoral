package com.stardew.craft.animal.runtime;

import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

/** Stable home identity survives building moves. The entity is only a projection. */
public record LivestockRecord(UUID id, UUID owner, UUID farm, UUID home, String name, long randomId,
                              int settledDay, LivestockCare care, LivestockLocation location, LivestockSpecies species, String produce, boolean cracker, boolean reproduction, CompoundTag extra) {
    public LivestockRecord { extra = extra.copy(); }
    @Override public CompoundTag extra() { return extra.copy(); }
    public LivestockRecord(UUID id, UUID owner, UUID farm, UUID home, String name, long randomId, int day,
            LivestockCare care, LivestockLocation location, LivestockSpecies species, String produce, boolean cracker, boolean reproduction) {
        this(id, owner, farm, home, name, randomId, day, care, location, species, produce, cracker, reproduction, new CompoundTag());
    }
    public LivestockRecord extra(CompoundTag value) { return new LivestockRecord(id, owner, farm, home, name, randomId, settledDay, care, location, species, produce, cracker, reproduction, value); }
    public CompoundTag persistentData() { return extra.getCompound("AddonData").copy(); }
    public LivestockRecord persistentData(CompoundTag value) { var tag=extra();tag.put("AddonData",value.copy());return extra(tag); }
    public LivestockRecord(UUID id, UUID owner, UUID farm, UUID home, String name, long randomId, int day, LivestockCare care) { this(id, owner, farm, home, name, randomId, day, care, null, LivestockSpecies.WHITE_CHICKEN, "", false, true); }
    public LivestockRecord withCare(int day, LivestockCare next) { return new LivestockRecord(id, owner, farm, home, name, randomId, day, next, location, species, produce, cracker, reproduction, extra); }
    public LivestockRecord at(LivestockLocation next) { return new LivestockRecord(id, owner, farm, home, name, randomId, settledDay, care, next, species, produce, cracker, reproduction, extra); }
    public boolean baby() { return care.age() < species.matureDays(); }
    public LivestockRecord species(LivestockSpecies value) { return new LivestockRecord(id, owner, farm, home, name, randomId, settledDay, care, location, value, produce, cracker, reproduction, extra); }
    public LivestockRecord produce(String value) {var tag=extra();if(value.isEmpty()||!value.equals(produce))tag.remove("HeldProduce");return new LivestockRecord(id,owner,farm,home,name,randomId,settledDay,care,location,species,value,cracker,reproduction,tag);}
    public LivestockRecord cracker(boolean value) { return new LivestockRecord(id, owner, farm, home, name, randomId, settledDay, care, location, species, produce, value, reproduction, extra); }
    public LivestockRecord reproduction(boolean value) { return new LivestockRecord(id, owner, farm, home, name, randomId, settledDay, care, location, species, produce, cracker, value, extra); }
    public LivestockRecord rename(String value) { return new LivestockRecord(id, owner, farm, home, value, randomId, settledDay, care, location, species, produce, cracker, reproduction, extra); }
    public LivestockRecord rehome(UUID value) { return new LivestockRecord(id, owner, farm, value, name, randomId, settledDay, care, null, species, produce, cracker, reproduction, extra); }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", id); tag.putUUID("Owner", owner); tag.putUUID("Farm", farm); tag.putUUID("Home", home);
        tag.putString("Name", name); tag.putLong("RandomId", randomId); tag.putInt("Day", settledDay); tag.put("Care", care.save());
        tag.putString("Species", species.id()); tag.putString("Produce", produce); tag.putBoolean("Cracker", cracker); tag.putBoolean("Reproduction", reproduction);
        if (location != null) tag.put("Location", location.save());
        tag.put("Extra", extra.copy());
        return tag;
    }
    public static LivestockRecord load(CompoundTag tag) {
        return new LivestockRecord(tag.getUUID("Id"), tag.getUUID("Owner"), tag.getUUID("Farm"), tag.getUUID("Home"),
                tag.getString("Name"), tag.getLong("RandomId"), tag.getInt("Day"), LivestockCare.load(tag.getCompound("Care")), tag.contains("Location") ? LivestockLocation.load(tag.getCompound("Location")) : null, tag.contains("Species") ? LivestockSpecies.parse(tag.getString("Species")) : LivestockSpecies.WHITE_CHICKEN, tag.getString("Produce"), tag.getBoolean("Cracker"), !tag.contains("Reproduction") || tag.getBoolean("Reproduction"), tag.getCompound("Extra"));
    }
}
