# Server TPS Phase 1 Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add low-overhead, testable server performance metrics that establish a trustworthy baseline for 30-50 player login and chunk-loading optimization.

**Architecture:** A small `server.performance` package owns rolling timing windows, monotonic counters, event-boundary instrumentation, and report formatting. Existing login content synchronization and the two known synchronous farm chunk-loading paths only record measurements; this phase deliberately does not change gameplay scheduling or packet behavior.

**Tech Stack:** Java 21, NeoForge 21.1.217 events, Brigadier commands, JUnit 5, Gradle ModDev.

---

## Scope Boundary

This plan implements the first independently testable slice of the approved design:

- Server tick, login event, content snapshot build, and synchronous chunk-load timings.
- Content-sync recipient, packet, and estimated registry-byte counters.
- Separate counters for farm-wide and daily-settlement synchronous chunk loads.
- `/stardew perf status` and `/stardew perf reset` operator commands.
- A repeatable Spark and in-mod baseline protocol.

The login synchronization coordinator, content hash negotiation, central chunk lease manager, lazy inactive-farm catch-up, machine scheduler, NPC/animal indexes, and dirty network snapshots each receive a separate implementation plan after this baseline is captured.

## File Map

### New production files

- `src/main/java/com/stardew/craft/server/performance/PerformanceTiming.java`: names timed server operations.
- `src/main/java/com/stardew/craft/server/performance/PerformanceCounter.java`: names monotonic workload counters.
- `src/main/java/com/stardew/craft/server/performance/TimingSummary.java`: immutable timing report in milliseconds.
- `src/main/java/com/stardew/craft/server/performance/RollingTimingWindow.java`: fixed-size rolling sample window and percentile calculation.
- `src/main/java/com/stardew/craft/server/performance/PerformanceSnapshot.java`: immutable snapshot of all timings and counters.
- `src/main/java/com/stardew/craft/server/performance/ServerPerformanceRecorder.java`: static server-thread recording API.
- `src/main/java/com/stardew/craft/server/performance/ServerPerformanceEvents.java`: server tick and login event boundary instrumentation.
- `src/main/java/com/stardew/craft/server/performance/PerformanceReportFormatter.java`: stable human-readable report lines.
- `src/main/java/com/stardew/craft/command/PerformanceCommand.java`: operator command registration.

### Modified production files

- `src/main/java/com/stardew/craft/event/CommandEventHandler.java`: register the performance command.
- `src/main/java/com/stardew/craft/network/DataRegistrySyncPayload.java`: expose a deterministic encoded-byte estimate.
- `src/main/java/com/stardew/craft/network/ClientContentSyncService.java`: time shared snapshot construction and count content-sync work.
- `src/main/java/com/stardew/craft/farm/FarmChunkManager.java`: measure farm-wide synchronous `getChunk` calls.
- `src/main/java/com/stardew/craft/farm/FarmDailyProcessHelper.java`: measure daily-settlement synchronous `getChunk` calls.

### New and modified tests

- `src/test/java/com/stardew/craft/server/performance/RollingTimingWindowTest.java`
- `src/test/java/com/stardew/craft/server/performance/ServerPerformanceRecorderTest.java`
- `src/test/java/com/stardew/craft/server/performance/ServerPerformanceEventsTest.java`
- `src/test/java/com/stardew/craft/server/performance/PerformanceReportFormatterTest.java`
- `src/test/java/com/stardew/craft/api/v1/DataRegistrySyncPayloadTest.java`

### Documentation

- `docs/performance/server-tps-baseline-protocol.md`: exact baseline scenarios and capture commands.

---

### Task 1: Verify The Clean Official Baseline

**Files:**
- Verify only; no file changes.

- [ ] **Step 1: Confirm branch and worktree state**

Run:

```powershell
git status --short --branch
git log -2 --oneline --decorate
```

Expected: branch `perf/server-tps`, no uncommitted source changes, and design commit `699e5f3c` above official `48349a56`.

- [ ] **Step 2: Run the official test suite before instrumentation**

Run:

```powershell
.\gradlew.bat test --no-daemon
```

Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 3: Run the official full build before instrumentation**

Run:

```powershell
.\gradlew.bat build --no-daemon
```

Expected: `BUILD SUCCESSFUL` and a `stardewcraft-0.5.1fix4.jar` artifact under `build/libs`.

---

### Task 2: Implement Rolling Timing Statistics

**Files:**
- Create: `src/main/java/com/stardew/craft/server/performance/PerformanceTiming.java`
- Create: `src/main/java/com/stardew/craft/server/performance/TimingSummary.java`
- Create: `src/main/java/com/stardew/craft/server/performance/RollingTimingWindow.java`
- Test: `src/test/java/com/stardew/craft/server/performance/RollingTimingWindowTest.java`

- [ ] **Step 1: Write the failing rolling-window test**

```java
package com.stardew.craft.server.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RollingTimingWindowTest {
    @Test
    void retainsNewestSamplesAndCalculatesPercentiles() {
        RollingTimingWindow window = new RollingTimingWindow(4);
        window.record(1_000_000L);
        window.record(2_000_000L);
        window.record(3_000_000L);
        window.record(4_000_000L);
        window.record(5_000_000L);

        TimingSummary summary = window.snapshot();

        assertEquals(4L, summary.sampleCount());
        assertEquals(3.5D, summary.averageMillis(), 1.0E-9D);
        assertEquals(5.0D, summary.maxMillis(), 1.0E-9D);
        assertEquals(5.0D, summary.p95Millis(), 1.0E-9D);
        assertEquals(5.0D, summary.p99Millis(), 1.0E-9D);
    }

    @Test
    void emptyWindowProducesZeroSummary() {
        assertEquals(TimingSummary.ZERO, new RollingTimingWindow(8).snapshot());
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "com.stardew.craft.server.performance.RollingTimingWindowTest" --no-daemon
```

Expected: compilation fails because `RollingTimingWindow` and `TimingSummary` do not exist.

- [ ] **Step 3: Add the timing category enum**

```java
package com.stardew.craft.server.performance;

public enum PerformanceTiming {
    SERVER_TICK,
    PLAYER_LOGIN_EVENT,
    CONTENT_SNAPSHOT_BUILD,
    JEI_CATALOG_BUILD,
    FARM_SYNC_CHUNK_LOAD,
    DAILY_SYNC_CHUNK_LOAD
}
```

- [ ] **Step 4: Add the immutable timing summary**

```java
package com.stardew.craft.server.performance;

public record TimingSummary(
        long sampleCount,
        double averageMillis,
        double maxMillis,
        double p95Millis,
        double p99Millis
) {
    public static final TimingSummary ZERO = new TimingSummary(0L, 0.0D, 0.0D, 0.0D, 0.0D);
}
```

- [ ] **Step 5: Implement the fixed-size rolling window**

```java
package com.stardew.craft.server.performance;

import java.util.Arrays;

final class RollingTimingWindow {
    private static final double NANOS_PER_MILLI = 1_000_000.0D;

    private final long[] samples;
    private int nextIndex;
    private int size;

    RollingTimingWindow(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.samples = new long[capacity];
    }

    void record(long nanos) {
        samples[nextIndex] = Math.max(0L, nanos);
        nextIndex = (nextIndex + 1) % samples.length;
        size = Math.min(size + 1, samples.length);
    }

    TimingSummary snapshot() {
        if (size == 0) {
            return TimingSummary.ZERO;
        }
        long[] sorted = Arrays.copyOf(samples, size);
        Arrays.sort(sorted);
        long total = 0L;
        for (long sample : sorted) {
            total += sample;
        }
        return new TimingSummary(
                size,
                total / (double) size / NANOS_PER_MILLI,
                sorted[size - 1] / NANOS_PER_MILLI,
                percentile(sorted, 0.95D) / NANOS_PER_MILLI,
                percentile(sorted, 0.99D) / NANOS_PER_MILLI
        );
    }

    void clear() {
        Arrays.fill(samples, 0L);
        nextIndex = 0;
        size = 0;
    }

    private static long percentile(long[] sorted, double percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.length) - 1);
        return sorted[Math.min(index, sorted.length - 1)];
    }
}
```

- [ ] **Step 6: Run the focused test and verify GREEN**

Run:

```powershell
.\gradlew.bat test --tests "com.stardew.craft.server.performance.RollingTimingWindowTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL` and both tests pass.

- [ ] **Step 7: Commit the timing primitive**

```powershell
git add src/main/java/com/stardew/craft/server/performance src/test/java/com/stardew/craft/server/performance/RollingTimingWindowTest.java
git commit -m "perf: add rolling server timing statistics"
```

---

### Task 3: Implement The Central Performance Recorder

**Files:**
- Create: `src/main/java/com/stardew/craft/server/performance/PerformanceCounter.java`
- Create: `src/main/java/com/stardew/craft/server/performance/PerformanceSnapshot.java`
- Create: `src/main/java/com/stardew/craft/server/performance/ServerPerformanceRecorder.java`
- Test: `src/test/java/com/stardew/craft/server/performance/ServerPerformanceRecorderTest.java`

- [ ] **Step 1: Write the failing recorder test**

```java
package com.stardew.craft.server.performance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerPerformanceRecorderTest {
    @AfterEach
    void resetRecorder() {
        ServerPerformanceRecorder.reset();
    }

    @Test
    void recordsTimingsAndMonotonicCounters() {
        ServerPerformanceRecorder.record(PerformanceTiming.SERVER_TICK, 20_000_000L);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 4L);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 2L);

        PerformanceSnapshot snapshot = ServerPerformanceRecorder.snapshot();

        assertEquals(1L, snapshot.timings().get(PerformanceTiming.SERVER_TICK).sampleCount());
        assertEquals(20.0D, snapshot.timings().get(PerformanceTiming.SERVER_TICK).averageMillis(), 1.0E-9D);
        assertEquals(6L, snapshot.counters().get(PerformanceCounter.CONTENT_SYNC_PACKETS));
    }

    @Test
    void measureRecordsDurationAndReturnsValue() {
        String value = ServerPerformanceRecorder.measure(
                PerformanceTiming.CONTENT_SNAPSHOT_BUILD,
                () -> "snapshot"
        );

        assertEquals("snapshot", value);
        assertEquals(
                1L,
                ServerPerformanceRecorder.snapshot().timings()
                        .get(PerformanceTiming.CONTENT_SNAPSHOT_BUILD).sampleCount()
        );
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "com.stardew.craft.server.performance.ServerPerformanceRecorderTest" --no-daemon
```

Expected: compilation fails because the recorder, counters, and snapshot do not exist.

- [ ] **Step 3: Add counter names and immutable snapshot**

```java
package com.stardew.craft.server.performance;

public enum PerformanceCounter {
    CONTENT_SYNC_RECIPIENTS,
    CONTENT_SYNC_PACKETS,
    CONTENT_REGISTRY_BYTES,
    JEI_CATALOG_ENTRIES,
    FARM_SYNC_CHUNK_LOADS,
    DAILY_SYNC_CHUNK_LOADS
}
```

```java
package com.stardew.craft.server.performance;

import java.util.Map;

public record PerformanceSnapshot(
        Map<PerformanceTiming, TimingSummary> timings,
        Map<PerformanceCounter, Long> counters
) {
    public PerformanceSnapshot {
        timings = Map.copyOf(timings);
        counters = Map.copyOf(counters);
    }
}
```

- [ ] **Step 4: Implement the recorder API**

```java
package com.stardew.craft.server.performance;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ServerPerformanceRecorder {
    private static final int TIMING_WINDOW_SIZE = 1_200;
    private static final EnumMap<PerformanceTiming, RollingTimingWindow> TIMINGS =
            new EnumMap<>(PerformanceTiming.class);
    private static final EnumMap<PerformanceCounter, Long> COUNTERS =
            new EnumMap<>(PerformanceCounter.class);

    static {
        for (PerformanceTiming timing : PerformanceTiming.values()) {
            TIMINGS.put(timing, new RollingTimingWindow(TIMING_WINDOW_SIZE));
        }
        for (PerformanceCounter counter : PerformanceCounter.values()) {
            COUNTERS.put(counter, 0L);
        }
    }

    private ServerPerformanceRecorder() {
    }

    public static void record(PerformanceTiming timing, long nanos) {
        TIMINGS.get(timing).record(nanos);
    }

    public static void increment(PerformanceCounter counter, long amount) {
        if (amount > 0L) {
            COUNTERS.merge(counter, amount, Long::sum);
        }
    }

    public static <T> T measure(PerformanceTiming timing, Supplier<T> operation) {
        long started = System.nanoTime();
        try {
            return operation.get();
        } finally {
            record(timing, System.nanoTime() - started);
        }
    }

    public static void measure(PerformanceTiming timing, Runnable operation) {
        measure(timing, () -> {
            operation.run();
            return null;
        });
    }

    public static PerformanceSnapshot snapshot() {
        EnumMap<PerformanceTiming, TimingSummary> timingSnapshot =
                new EnumMap<>(PerformanceTiming.class);
        TIMINGS.forEach((timing, window) -> timingSnapshot.put(timing, window.snapshot()));
        return new PerformanceSnapshot(timingSnapshot, new EnumMap<>(COUNTERS));
    }

    public static void reset() {
        TIMINGS.values().forEach(RollingTimingWindow::clear);
        COUNTERS.replaceAll((counter, value) -> 0L);
    }
}
```

- [ ] **Step 5: Run recorder and rolling-window tests**

Run:

```powershell
.\gradlew.bat test --tests "com.stardew.craft.server.performance.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL` and all performance package tests pass.

- [ ] **Step 6: Commit the recorder**

```powershell
git add src/main/java/com/stardew/craft/server/performance src/test/java/com/stardew/craft/server/performance
git commit -m "perf: add central server performance recorder"
```

---

### Task 4: Instrument Server Tick And Login Event Boundaries

**Files:**
- Create: `src/main/java/com/stardew/craft/server/performance/ServerPerformanceEvents.java`
- Test: `src/test/java/com/stardew/craft/server/performance/ServerPerformanceEventsTest.java`

- [ ] **Step 1: Write failing boundary tests**

```java
package com.stardew.craft.server.performance;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerPerformanceEventsTest {
    @AfterEach
    void resetState() {
        ServerPerformanceEvents.clearState();
        ServerPerformanceRecorder.reset();
    }

    @Test
    void serverTickBoundaryRecordsOneDuration() {
        ServerPerformanceEvents.beginServerTick(10L);
        ServerPerformanceEvents.endServerTick(25L);

        assertEquals(
                1L,
                ServerPerformanceRecorder.snapshot().timings()
                        .get(PerformanceTiming.SERVER_TICK).sampleCount()
        );
    }

    @Test
    void loginBoundaryMatchesPlayersIndependently() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        ServerPerformanceEvents.beginLogin(first, 100L);
        ServerPerformanceEvents.beginLogin(second, 120L);
        ServerPerformanceEvents.endLogin(second, 150L);
        ServerPerformanceEvents.endLogin(first, 180L);

        TimingSummary summary = ServerPerformanceRecorder.snapshot().timings()
                .get(PerformanceTiming.PLAYER_LOGIN_EVENT);
        assertEquals(2L, summary.sampleCount());
        assertEquals(55.0E-6D, summary.averageMillis(), 1.0E-12D);
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "com.stardew.craft.server.performance.ServerPerformanceEventsTest" --no-daemon
```

Expected: compilation fails because `ServerPerformanceEvents` does not exist.

- [ ] **Step 3: Implement event-boundary instrumentation**

```java
package com.stardew.craft.server.performance;

import com.stardew.craft.StardewCraft;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ServerPerformanceEvents {
    private static final Map<UUID, Long> LOGIN_STARTS = new HashMap<>();
    private static long serverTickStart = -1L;

    private ServerPerformanceEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTickPre(ServerTickEvent.Pre event) {
        beginServerTick(System.nanoTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTickPost(ServerTickEvent.Post event) {
        endServerTick(System.nanoTime());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLoginStart(PlayerEvent.PlayerLoggedInEvent event) {
        beginLogin(event.getEntity().getUUID(), System.nanoTime());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLoginEnd(PlayerEvent.PlayerLoggedInEvent event) {
        endLogin(event.getEntity().getUUID(), System.nanoTime());
    }

    static void beginServerTick(long now) {
        serverTickStart = now;
    }

    static void endServerTick(long now) {
        if (serverTickStart >= 0L) {
            ServerPerformanceRecorder.record(
                    PerformanceTiming.SERVER_TICK,
                    Math.max(0L, now - serverTickStart)
            );
            serverTickStart = -1L;
        }
    }

    static void beginLogin(UUID playerId, long now) {
        LOGIN_STARTS.put(playerId, now);
    }

    static void endLogin(UUID playerId, long now) {
        Long started = LOGIN_STARTS.remove(playerId);
        if (started != null) {
            ServerPerformanceRecorder.record(
                    PerformanceTiming.PLAYER_LOGIN_EVENT,
                    Math.max(0L, now - started)
            );
        }
    }

    static void clearState() {
        LOGIN_STARTS.clear();
        serverTickStart = -1L;
    }
}
```

- [ ] **Step 4: Run all performance tests**

Run:

```powershell
.\gradlew.bat test --tests "com.stardew.craft.server.performance.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL` and all tests pass.

- [ ] **Step 5: Commit event instrumentation**

```powershell
git add src/main/java/com/stardew/craft/server/performance/ServerPerformanceEvents.java src/test/java/com/stardew/craft/server/performance/ServerPerformanceEventsTest.java
git commit -m "perf: measure server tick and login event boundaries"
```

---

### Task 5: Add Operator Performance Commands

**Files:**
- Create: `src/main/java/com/stardew/craft/server/performance/PerformanceReportFormatter.java`
- Create: `src/main/java/com/stardew/craft/command/PerformanceCommand.java`
- Modify: `src/main/java/com/stardew/craft/event/CommandEventHandler.java`
- Test: `src/test/java/com/stardew/craft/server/performance/PerformanceReportFormatterTest.java`

- [ ] **Step 1: Write the failing formatter test**

```java
package com.stardew.craft.server.performance;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceReportFormatterTest {
    @Test
    void formatsStableTimingAndCounterLines() {
        EnumMap<PerformanceTiming, TimingSummary> timings = new EnumMap<>(PerformanceTiming.class);
        timings.put(PerformanceTiming.SERVER_TICK, new TimingSummary(20L, 12.5D, 40.0D, 30.0D, 40.0D));
        EnumMap<PerformanceCounter, Long> counters = new EnumMap<>(PerformanceCounter.class);
        counters.put(PerformanceCounter.CONTENT_SYNC_PACKETS, 16L);

        List<String> lines = PerformanceReportFormatter.format(new PerformanceSnapshot(timings, counters));

        assertEquals("Stardew server performance", lines.getFirst());
        assertTrue(lines.contains("SERVER_TICK samples=20 avg=12.500ms p95=30.000ms p99=40.000ms max=40.000ms"));
        assertTrue(lines.contains("CONTENT_SYNC_PACKETS=16"));
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "com.stardew.craft.server.performance.PerformanceReportFormatterTest" --no-daemon
```

Expected: compilation fails because `PerformanceReportFormatter` does not exist.

- [ ] **Step 3: Implement stable report formatting**

```java
package com.stardew.craft.server.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PerformanceReportFormatter {
    private PerformanceReportFormatter() {
    }

    public static List<String> format(PerformanceSnapshot snapshot) {
        List<String> lines = new ArrayList<>();
        lines.add("Stardew server performance");
        for (PerformanceTiming timing : PerformanceTiming.values()) {
            TimingSummary summary = snapshot.timings().getOrDefault(timing, TimingSummary.ZERO);
            lines.add(String.format(
                    Locale.ROOT,
                    "%s samples=%d avg=%.3fms p95=%.3fms p99=%.3fms max=%.3fms",
                    timing.name(),
                    summary.sampleCount(),
                    summary.averageMillis(),
                    summary.p95Millis(),
                    summary.p99Millis(),
                    summary.maxMillis()
            ));
        }
        for (PerformanceCounter counter : PerformanceCounter.values()) {
            lines.add(counter.name() + "=" + snapshot.counters().getOrDefault(counter, 0L));
        }
        return List.copyOf(lines);
    }
}
```

- [ ] **Step 4: Implement the operator command**

```java
package com.stardew.craft.command;

import com.mojang.brigadier.CommandDispatcher;
import com.stardew.craft.server.performance.PerformanceReportFormatter;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class PerformanceCommand {
    private PerformanceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stardew")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("perf")
                        .then(Commands.literal("status").executes(context -> show(context.getSource())))
                        .then(Commands.literal("reset").executes(context -> reset(context.getSource())))));
    }

    private static int show(CommandSourceStack source) {
        for (String line : PerformanceReportFormatter.format(ServerPerformanceRecorder.snapshot())) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int reset(CommandSourceStack source) {
        ServerPerformanceRecorder.reset();
        source.sendSuccess(() -> Component.literal("Stardew performance metrics reset"), false);
        return 1;
    }
}
```

- [ ] **Step 5: Register `PerformanceCommand` in the common command handler**

Add the import:

```java
import com.stardew.craft.command.PerformanceCommand;
```

Add this call immediately after `StardewTimeCommand.register(event.getDispatcher());`:

```java
PerformanceCommand.register(event.getDispatcher());
```

- [ ] **Step 6: Run formatter tests and compile production code**

Run:

```powershell
.\gradlew.bat test --tests "com.stardew.craft.server.performance.PerformanceReportFormatterTest" --no-daemon
.\gradlew.bat classes --no-daemon
```

Expected: both commands finish with `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit operator reporting**

```powershell
git add src/main/java/com/stardew/craft/command/PerformanceCommand.java src/main/java/com/stardew/craft/event/CommandEventHandler.java src/main/java/com/stardew/craft/server/performance/PerformanceReportFormatter.java src/test/java/com/stardew/craft/server/performance/PerformanceReportFormatterTest.java
git commit -m "feat: add server performance diagnostics command"
```

---

### Task 6: Measure Login Content Sync And Synchronous Farm Chunk Loads

**Files:**
- Modify: `src/main/java/com/stardew/craft/network/DataRegistrySyncPayload.java`
- Modify: `src/main/java/com/stardew/craft/network/ClientContentSyncService.java`
- Modify: `src/main/java/com/stardew/craft/farm/FarmChunkManager.java`
- Modify: `src/main/java/com/stardew/craft/farm/FarmDailyProcessHelper.java`
- Modify test: `src/test/java/com/stardew/craft/api/v1/DataRegistrySyncPayloadTest.java`

- [ ] **Step 1: Add a failing encoded-size test**

Append this test to `DataRegistrySyncPayloadTest`:

```java
@Test
void estimatesEncodedRegistryDocumentBytes() {
    DataRegistrySyncPayload payload = new DataRegistrySyncPayload(
            "{}", "{}", "{}", "{}", "{}", "{}",
            "{}", "{}", "{}", "{}", "{}", "{}"
    );

    assertEquals(36, payload.estimatedEncodedBytes());
}
```

Add the static import if the test does not already have it:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "com.stardew.craft.api.v1.DataRegistrySyncPayloadTest.estimatesEncodedRegistryDocumentBytes" --no-daemon
```

Expected: compilation fails because `estimatedEncodedBytes()` does not exist.

- [ ] **Step 3: Add deterministic encoded-size estimation**

Add these methods to `DataRegistrySyncPayload` immediately before `type()`:

```java
public int estimatedEncodedBytes() {
    return encodedStringBytes(artisanJson)
            + encodedStringBytes(cookingJson)
            + encodedStringBytes(craftingJson)
            + encodedStringBytes(preservesJson)
            + encodedStringBytes(fishingJson)
            + encodedStringBytes(npcEventsJson)
            + encodedStringBytes(unlockSourcesJson)
            + encodedStringBytes(festivalsJson)
            + encodedStringBytes(masteryRewardsJson)
            + encodedStringBytes(locationsJson)
            + encodedStringBytes(professionsJson)
            + encodedStringBytes(secretNotesJson);
}

private static int encodedStringBytes(String value) {
    int bytes = value.getBytes(StandardCharsets.UTF_8).length;
    return varIntBytes(bytes) + bytes;
}

private static int varIntBytes(int value) {
    int bytes = 1;
    while ((value & -128) != 0) {
        value >>>= 7;
        bytes++;
    }
    return bytes;
}
```

- [ ] **Step 4: Instrument shared content snapshot construction**

Add imports to `ClientContentSyncService`:

```java
import com.stardew.craft.server.performance.PerformanceCounter;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
```

Replace direct registry snapshot construction with:

```java
DataRegistrySyncPayload registrySnapshot = ServerPerformanceRecorder.measure(
        PerformanceTiming.CONTENT_SNAPSHOT_BUILD,
        DataRegistrySyncPayload::current
);
```

After collecting `recipients`, add:

```java
ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_RECIPIENTS, recipients.size());
ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, recipients.size() * 4L);
ServerPerformanceRecorder.increment(
        PerformanceCounter.CONTENT_REGISTRY_BYTES,
        recipients.size() * (long) registrySnapshot.estimatedEncodedBytes()
);
```

Replace the existing recipient loop with a measured per-player JEI catalog build:

```java
for (ServerPlayer player : recipients) {
    JeiCatalogSyncPayload jeiSnapshot = ServerPerformanceRecorder.measure(
            PerformanceTiming.JEI_CATALOG_BUILD,
            () -> JeiCatalogSyncPayload.current(player)
    );
    long jeiEntries = (long) jeiSnapshot.shops().size()
            + jeiSnapshot.geodes().size()
            + jeiSnapshot.fishPonds().size();
    ServerPerformanceRecorder.increment(PerformanceCounter.JEI_CATALOG_ENTRIES, jeiEntries);

    PacketDistributor.sendToPlayer(player, registrySnapshot);
    PacketDistributor.sendToPlayer(player, mailSnapshot);
    PacketDistributor.sendToPlayer(player, festivalSnapshot);
    PacketDistributor.sendToPlayer(player, jeiSnapshot);
}
```

This counts the four packets sent by this service, records the player-dependent
JEI rebuild cost and entry count, and does not change packet order or contents.

- [ ] **Step 5: Instrument farm-wide synchronous chunk loads**

Add imports to `FarmChunkManager`:

```java
import com.stardew.craft.server.performance.PerformanceCounter;
import com.stardew.craft.server.performance.PerformanceTiming;
import com.stardew.craft.server.performance.ServerPerformanceRecorder;
```

Replace:

```java
level.getChunk(chunk.x, chunk.z);
```

with:

```java
ServerPerformanceRecorder.increment(PerformanceCounter.FARM_SYNC_CHUNK_LOADS, 1L);
ServerPerformanceRecorder.measure(
        PerformanceTiming.FARM_SYNC_CHUNK_LOAD,
        () -> level.getChunk(chunk.x, chunk.z)
);
```

- [ ] **Step 6: Instrument daily-settlement synchronous chunk loads**

Add the same three performance imports to `FarmDailyProcessHelper`.

Replace:

```java
level.getChunk(chunkX, chunkZ);
```

with:

```java
ServerPerformanceRecorder.increment(PerformanceCounter.DAILY_SYNC_CHUNK_LOADS, 1L);
ServerPerformanceRecorder.measure(
        PerformanceTiming.DAILY_SYNC_CHUNK_LOAD,
        () -> level.getChunk(chunkX, chunkZ)
);
```

- [ ] **Step 7: Run focused and related regression tests**

Run:

```powershell
.\gradlew.bat test --tests "com.stardew.craft.api.v1.DataRegistrySyncPayloadTest" --tests "com.stardew.craft.farm.FarmChunkManagerTest" --tests "com.stardew.craft.server.performance.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL` and all selected tests pass.

- [ ] **Step 8: Compile all production classes**

Run:

```powershell
.\gradlew.bat classes --no-daemon
```

Expected: `BUILD SUCCESSFUL` with no new compile errors.

- [ ] **Step 9: Commit workload instrumentation**

```powershell
git add src/main/java/com/stardew/craft/network/DataRegistrySyncPayload.java src/main/java/com/stardew/craft/network/ClientContentSyncService.java src/main/java/com/stardew/craft/farm/FarmChunkManager.java src/main/java/com/stardew/craft/farm/FarmDailyProcessHelper.java src/test/java/com/stardew/craft/api/v1/DataRegistrySyncPayloadTest.java
git commit -m "perf: measure login content and synchronous chunk load cost"
```

---

### Task 7: Add The Baseline Protocol And Complete Verification

**Files:**
- Create: `docs/performance/server-tps-baseline-protocol.md`

- [ ] **Step 1: Add the fixed baseline protocol**

```markdown
# Server TPS Baseline Protocol

## Target

- 30-50 concurrent players
- 20 TPS
- Average MSPT <= 35 ms
- P95 MSPT <= 45 ms
- P99 MSPT <= 50 ms

## Required Tools

- Spark profiler installed on the test server
- Operator access to `/stardew perf`
- The exact tested StardewCraft commit recorded with `git rev-parse HEAD`

## Reset

1. Run `/stardew perf reset`.
2. Run `/spark profiler start --timeout 300`.
3. Wait 30 seconds before starting the scenario.

## Scenario A: Steady Multiplayer

Maintain 30-50 players for five minutes across the public map, farms, mines,
interiors, fishing, and combat. Record `/stardew perf status` at the end.

## Scenario B: Concurrent Login

Disconnect ten test clients, reset metrics, then reconnect all ten during a
ten-second window. Record `/stardew perf status` after all clients finish
loading.

## Scenario C: Day Rollover

Keep at least ten farms occupied and at least twenty farms inactive. Reset
metrics immediately before sleep, advance the day, and record metrics after all
settlement screens close.

## Scenario D: Teleport And Interior Churn

Have twenty players repeatedly alternate between farm, public map, mine, and
interior destinations for five minutes. Record metrics when the run ends.

## Capture

For every scenario retain:

- Git commit ID and mod JAR SHA-256
- Server hardware and JVM arguments
- Online player count
- Spark report URL
- Complete `/stardew perf status` output
- Relevant server log warnings or errors

Compare optimized runs only against a baseline captured with the same world,
hardware, JVM flags, view distance, simulation distance, and player script.
```

- [ ] **Step 2: Run the complete test suite**

Run:

```powershell
.\gradlew.bat test --no-daemon
```

Expected: `BUILD SUCCESSFUL` with zero failed tests.

- [ ] **Step 3: Run the complete build**

Run:

```powershell
.\gradlew.bat build --no-daemon
```

Expected: `BUILD SUCCESSFUL` and `build/libs/stardewcraft-0.5.1fix4.jar` exists.

- [ ] **Step 4: Check source and documentation diffs**

Run:

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; only the protocol document is uncommitted.

- [ ] **Step 5: Commit the baseline protocol**

```powershell
git add -f docs/performance/server-tps-baseline-protocol.md
git commit -m "docs: add multiplayer TPS baseline protocol"
```

- [ ] **Step 6: Verify the final branch and push**

Run:

```powershell
git status --short --branch
git log --oneline --decorate -8
git push origin perf/server-tps
```

Expected: clean worktree, the observability commits above `699e5f3c`, and a successful push to `origin/perf/server-tps`.

---

## Phase Completion Gate

Phase 1 is complete only when:

- All new metric classes have deterministic JUnit coverage.
- The official test suite and full build pass.
- `/stardew perf status` reports rolling tick and login timings.
- Login content-sync packet/byte counters increase during a real login.
- Per-player JEI catalog timing and entry counters increase during content sync.
- Farm and daily synchronous chunk-load counters increase only when those paths run.
- A Spark baseline and matching in-mod report have been captured using the protocol.
- No scheduling, packet ordering, chunk ownership, or gameplay behavior changed in this phase.

The captured results determine the numerical budgets in the login synchronization and chunk lease implementation plans.
