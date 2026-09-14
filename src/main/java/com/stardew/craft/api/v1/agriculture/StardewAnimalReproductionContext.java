package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/** Read-only server context supplied to an addon animal reproduction rule. */
public final class StardewAnimalReproductionContext {
    private com.stardew.craft.animal.runtime.LivestockRecord livestock;
    private com.stardew.craft.building.runtime.BuildingRecord home;
    private final ServerLevel level;
    private final AnimalBuildingRecord building;
    private final FarmAnimalRecord animal;
    private final int absoluteDaysPlayed;

    public StardewAnimalReproductionContext(
            ServerLevel level,
            AnimalBuildingRecord building,
            FarmAnimalRecord animal,
            int absoluteDaysPlayed
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.building = Objects.requireNonNull(building, "building");
        this.animal = Objects.requireNonNull(animal, "animal");
        this.absoluteDaysPlayed = absoluteDaysPlayed;
    }

    StardewAnimalReproductionContext(FarmAnimalRecord animal, int absoluteDaysPlayed) {
        this.level = null;
        this.building = null;
        this.animal = Objects.requireNonNull(animal, "animal");
        this.absoluteDaysPlayed = absoluteDaysPlayed;
    }

    public StardewAnimalReproductionContext(ServerLevel level,com.stardew.craft.building.runtime.BuildingRecord home,com.stardew.craft.animal.runtime.LivestockRecord animal,int day){
        this.level=Objects.requireNonNull(level);this.home=home;livestock=animal;building=null;this.animal=null;absoluteDaysPlayed=day;
    }
    public ServerLevel level() {
        return level;
    }

    public java.util.UUID entityId(){return livestock==null?null:livestock.id();}

    public long animalId() {
        return livestock!=null?-livestock.randomId():animal.animalId();
    }

    public String animalTypeId() {
        return livestock!=null?livestock.species().definitionId():animal.animalTypeId();
    }

    public String buildingId() {
        return livestock!=null?livestock.home().toString():animal.buildingId();
    }

    public String buildingFamily() {
        return home!=null?(home.family().getNamespace().equals("stardewcraft")?home.family().getPath():home.family().toString()):building == null ? "" : building.buildingType().family();
    }

    public int absoluteDaysPlayed() {
        return absoluteDaysPlayed;
    }

    public int friendship() {
        return livestock!=null?livestock.care().friendship():animal.friendship();
    }

    public int ageDays() {
        return livestock!=null?livestock.care().age():animal.ageDays();
    }

    public int daysToMature() {
        return livestock!=null?livestock.species().matureDays():animal.daysToMature();
    }
}
