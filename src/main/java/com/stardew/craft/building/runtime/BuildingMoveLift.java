package com.stardew.craft.building.runtime;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Durable snapshot held while a building is hidden for movement preview. */
public record BuildingMoveLift(
        UUID owner, UUID document, BuildingTransfer snapshot, CompoundTag preview) {
    public BuildingMoveLift {
        if (!snapshot.before().anchor().equals(snapshot.after().anchor())
                || snapshot.before().facing() != snapshot.after().facing()
                || !snapshot.before().claim().equals(snapshot.after().claim())) {
            throw new IllegalArgumentException("Move lift snapshot changes building placement");
        }
        preview = preview.copy();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Owner", owner);
        tag.putUUID("Document", document);
        tag.put("Snapshot", snapshot.save());
        tag.put("Preview", preview.copy());
        return tag;
    }

    public static BuildingMoveLift load(CompoundTag tag, HolderLookup.Provider registries) {
        return new BuildingMoveLift(tag.getUUID("Owner"), tag.getUUID("Document"),
                BuildingTransfer.load(tag.getCompound("Snapshot"), registries),
                tag.getCompound("Preview"));
    }
}
