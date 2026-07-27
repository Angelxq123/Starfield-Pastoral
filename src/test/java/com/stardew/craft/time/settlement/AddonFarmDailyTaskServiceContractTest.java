package com.stardew.craft.time.settlement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonFarmDailyTaskServiceContractTest {
    private static final Path PROJECT =
            Path.of(System.getProperty("stardewcraft.projectDir", "."));

    @Test
    void dispatchUsesFrozenFarmsOneAtATimeWithDeterministicRandom() throws IOException {
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/stardew/craft/farm/AddonFarmDailyTaskService.java"));

        assertTrue(source.contains("frozenFarms.values().stream()"));
        assertTrue(source.contains("DailySettlementWorkUnits.cursor("));
        assertTrue(source.contains("StardewFarmDailyTaskRegistry.run("));
        assertTrue(source.contains("StardewFarmSnapshots.from(farm)"));
        assertTrue(source.contains("DailySettlementRandom.forId("));
        assertTrue(source.contains("\"addon_farm_tasks\""));
        assertFalse(source.contains("runActiveFarms("));
        assertFalse(source.contains("getPlayerList()"));
        assertFalse(source.contains("level.getRandom()"));
    }
}
