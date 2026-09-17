package com.stardew.craft.time.settlement;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeasonalWorkContractTest {
    @Test void seasonalStageMustComposeCursorsInsteadOfDrainingWholeWorldScans() throws Exception {
        String factory = read("time/settlement/DailySettlementPlanFactory.java");
        assertFalse(factory.contains(".restoreAll(level)"));
        assertFalse(factory.contains(".refreshLoadedWeedsForSeason("));
        assertTrue(factory.contains("createRestorationWorkUnit"));
        assertTrue(factory.contains("createSeasonRefreshWorkUnit"));
    }
    private String read(String path) throws Exception {
        return Files.readString(Path.of(System.getProperty("stardewcraft.projectDir", "."))
                .resolve("src/main/java/com/stardew/craft/" + path));
    }
}
