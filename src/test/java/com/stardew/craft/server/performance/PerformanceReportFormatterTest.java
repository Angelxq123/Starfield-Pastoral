package com.stardew.craft.server.performance;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.stardew.craft.command.PerformanceCommand;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceReportFormatterTest {

    @Test
    void formatsCompleteReportInEnumOrder() {
        EnumMap<PerformanceTiming, TimingSummary> timings = new EnumMap<>(PerformanceTiming.class);
        timings.put(
            PerformanceTiming.SERVER_TICK,
            new TimingSummary(20L, 12.5D, 40.0D, 30.0D, 40.0D)
        );
        EnumMap<PerformanceCounter, Long> counters = new EnumMap<>(PerformanceCounter.class);
        counters.put(PerformanceCounter.CONTENT_SYNC_PACKETS, 16L);

        List<String> lines = PerformanceReportFormatter.format(new PerformanceSnapshot(timings, counters));

        assertEquals(List.of(
            "Stardew server performance",
            "SERVER_TICK samples=20 avg=12.500ms p95=30.000ms p99=40.000ms max=40.000ms",
            "PLAYER_LOGIN_EVENT samples=0 avg=0.000ms p95=0.000ms p99=0.000ms max=0.000ms",
            "CONTENT_SNAPSHOT_BUILD samples=0 avg=0.000ms p95=0.000ms p99=0.000ms max=0.000ms",
            "JEI_CATALOG_BUILD samples=0 avg=0.000ms p95=0.000ms p99=0.000ms max=0.000ms",
            "FARM_SYNC_CHUNK_LOAD samples=0 avg=0.000ms p95=0.000ms p99=0.000ms max=0.000ms",
            "DAILY_SYNC_CHUNK_LOAD samples=0 avg=0.000ms p95=0.000ms p99=0.000ms max=0.000ms",
            "DAILY_SETTLEMENT_TOTAL samples=0 avg=0.000ms p95=0.000ms p99=0.000ms max=0.000ms",
            "DAILY_SETTLEMENT_TICK samples=0 avg=0.000ms p95=0.000ms p99=0.000ms max=0.000ms",
            "DAILY_SETTLEMENT_ATOMIC_ITEM samples=0 avg=0.000ms p95=0.000ms p99=0.000ms max=0.000ms",
            "DAILY_SETTLEMENT_LOCK_TO_READY samples=0 avg=0.000ms p95=0.000ms p99=0.000ms max=0.000ms",
            "CONTENT_SYNC_RECIPIENTS=0",
            "CONTENT_SYNC_PACKETS=16",
            "CONTENT_REGISTRY_BYTES=0",
            "JEI_CATALOG_ENTRIES=0",
            "FARM_SYNC_CHUNK_LOADS=0",
            "DAILY_SYNC_CHUNK_LOADS=0",
            "DAILY_SETTLEMENT_TICKS=0",
            "DAILY_SETTLEMENT_ITEMS=0",
            "DAILY_SETTLEMENT_CHUNK_LEASES=0",
            "DAILY_SETTLEMENT_OVERSHOOTS=0",
            "DAILY_SETTLEMENT_RETRIES=0",
            "DAILY_SETTLEMENT_PERMANENT_FAILURES=0",
            "DAILY_SETTLEMENT_PLAYER_BATCHES=0",
            "DAILY_SETTLEMENT_READY_PUBLICATIONS=0"
        ), lines);
    }

    @Test
    void sparseSnapshotRendersEveryCategoryOnceWithZeroDefaults() {
        List<String> lines = PerformanceReportFormatter.format(
            new PerformanceSnapshot(Map.of(), Map.of())
        );

        assertEquals(1 + PerformanceTiming.values().length + PerformanceCounter.values().length, lines.size());
        assertEquals("Stardew server performance", lines.get(0));
        for (int index = 0; index < PerformanceTiming.values().length; index++) {
            assertEquals(
                PerformanceTiming.values()[index]
                    + " samples=0 avg=0.000ms p95=0.000ms p99=0.000ms max=0.000ms",
                lines.get(index + 1)
            );
        }
        int counterOffset = 1 + PerformanceTiming.values().length;
        for (int index = 0; index < PerformanceCounter.values().length; index++) {
            assertEquals(
                PerformanceCounter.values()[index] + "=0",
                lines.get(counterOffset + index)
            );
        }
    }

    @Test
    void returnedReportIsImmutable() {
        List<String> lines = PerformanceReportFormatter.format(
            new PerformanceSnapshot(Map.of(), Map.of())
        );

        assertThrows(UnsupportedOperationException.class, () -> lines.add("extra"));
    }

    @Test
    void formattingAlwaysUsesRootLocaleDecimalSeparator() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            PerformanceSnapshot snapshot = new PerformanceSnapshot(
                Map.of(PerformanceTiming.SERVER_TICK, new TimingSummary(1L, 12.5D, 40.0D, 30.0D, 40.0D)),
                Map.of()
            );

            assertEquals(
                "SERVER_TICK samples=1 avg=12.500ms p95=30.000ms p99=40.000ms max=40.000ms",
                PerformanceReportFormatter.format(snapshot).get(1)
            );
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void rejectsNullSnapshot() {
        assertThrows(NullPointerException.class, () -> PerformanceReportFormatter.format(null));
    }

    @Test
    void utilityClassesAreFinalWithPrivateConstructors() {
        assertUtilityClass(PerformanceReportFormatter.class);
        assertUtilityClass(PerformanceCommand.class);
    }

    @Test
    void performanceCommandRegistersExpectedTree() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

        PerformanceCommand.register(dispatcher);

        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("stardew");
        assertNotNull(root);
        CommandNode<CommandSourceStack> perf = root.getChild("perf");
        assertNotNull(perf);
        assertNotNull(perf.getChild("status"));
        assertNotNull(perf.getChild("reset"));
    }

    private static void assertUtilityClass(Class<?> type) {
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
    }
}
