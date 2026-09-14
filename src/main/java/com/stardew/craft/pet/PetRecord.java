package com.stardew.craft.pet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

/** Server-thread state. The visible entity is a disposable projection of this identity. */
public final class PetRecord {
    public final UUID id, farm;
    public final PetVariant variant;
    public String name;
    public int friendship, settledDay, careDay = -1, timesPet;
    public final Map<UUID, Integer> petted = new HashMap<>();
    public BlockPos bowl;
    public Vec3 position;
    public float yaw;
    public boolean indoors;
    public int restDay = -1, bowlSadDay = -1;
    public Vec3 restPosition;
    public CompoundTag hat = new CompoundTag();

    public PetRecord(UUID id, UUID farm, PetVariant variant, String name, int day) {
        this.id = id; this.farm = farm; this.variant = variant; this.name = name; settledDay = day;
    }

    public static String cleanName(String value) {
        String name = value == null ? "" : value.replaceAll("[\\p{Cntrl}§]", "").strip();
        return name.codePoints().limit(24).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    public void friendship(int delta) { friendship = Math.max(0, Math.min(1000, friendship + delta)); }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", id); tag.putUUID("Farm", farm); tag.putString("Variant", variant.id()); tag.putString("Name", name);
        tag.putInt("Friendship", friendship); tag.putInt("SettledDay", settledDay); tag.putInt("CareDay", careDay); tag.putInt("TimesPet", timesPet);
        var care = new ListTag();
        petted.forEach((player, day) -> { var row = new CompoundTag(); row.putUUID("Player", player); row.putInt("Day", day); care.add(row); });
        tag.put("Petted", care); tag.put("Hat", hat.copy()); tag.putBoolean("Indoors", indoors); tag.putFloat("Yaw", yaw);
        tag.putInt("RestDay", restDay); tag.putInt("BowlSadDay", bowlSadDay);
        if (restPosition != null) { var rest = new CompoundTag(); rest.putDouble("X", restPosition.x); rest.putDouble("Y", restPosition.y); rest.putDouble("Z", restPosition.z); tag.put("Rest", rest); }
        if (bowl != null) tag.putLong("Bowl", bowl.asLong());
        if (position != null) { var pos = new CompoundTag(); pos.putDouble("X", position.x); pos.putDouble("Y", position.y); pos.putDouble("Z", position.z); tag.put("Position", pos); }
        return tag;
    }

    public static PetRecord load(CompoundTag tag) {
        var record = new PetRecord(tag.getUUID("Id"), tag.getUUID("Farm"), PetVariant.fromSaved(tag.getString("Variant")), tag.getString("Name"), tag.getInt("SettledDay"));
        record.friendship = Math.max(0, Math.min(1000, tag.getInt("Friendship"))); record.careDay = tag.getInt("CareDay"); record.timesPet = tag.getInt("TimesPet");
        for (var entry : tag.getList("Petted", Tag.TAG_COMPOUND)) { var row = (CompoundTag) entry; record.petted.put(row.getUUID("Player"), row.getInt("Day")); }
        record.hat = tag.getCompound("Hat").copy(); record.indoors = tag.getBoolean("Indoors"); record.yaw = tag.getFloat("Yaw");
        record.bowlSadDay = tag.contains("BowlSadDay") ? tag.getInt("BowlSadDay") : -1;
        record.restDay = tag.contains("RestDay") ? tag.getInt("RestDay") : -1;
        if (tag.contains("Rest")) { var rest = tag.getCompound("Rest"); record.restPosition = new Vec3(rest.getDouble("X"), rest.getDouble("Y"), rest.getDouble("Z")); }
        if (tag.contains("Bowl")) record.bowl = BlockPos.of(tag.getLong("Bowl"));
        if (tag.contains("Position")) { var pos = tag.getCompound("Position"); record.position = new Vec3(pos.getDouble("X"), pos.getDouble("Y"), pos.getDouble("Z")); }
        return record;
    }
}
