package com.stardew.craft.interior;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class LoadedColumnScanTest {
    @Test void defersColdColumnsWithoutLosingThemAndNeverExceedsOneCellPerStep() {
        LoadedColumnScan scan = new LoadedColumnScan(0, 1, 0, 1, 0, 1);
        Set<BlockPos> visited = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            int before = visited.size();
            scan.step(pos -> pos.getX() == 0, pos -> assertTrue(visited.add(pos)));
            assertTrue(visited.size() - before <= 1);
        }
        assertEquals(4, visited.size());
        assertFalse(scan.isComplete());
        while (!scan.isComplete()) scan.step(pos -> true, pos -> assertTrue(visited.add(pos)));
        assertEquals(8, visited.size());
    }
}
