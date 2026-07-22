# Budgeted Daily Settlement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the monolithic Stardew day-rollover spike with a deterministic, main-thread settlement queue that spends at most 4 ms per tick and unlocks the shared overnight black screen only after all required work commits.

**Architecture:** A pure budget runner drives ordered `DailySettlementWorkUnit` instances through one server-wide `DailySettlementCoordinator`. Existing daily managers expose frozen, cursor-based work units, while a barrier/cache layer keeps participating players on the unchanged black overnight screen until the coordinator publishes their existing settlement result payloads. World mutation remains on the server thread; chunk leases, failures, reconnects, normal shutdown, and telemetry are owned by the coordinator lifecycle.

**Tech Stack:** Java 21, NeoForge 21.1, Minecraft 1.21.1, JUnit 5, Gradle 9.2, JDK compiler AST tests.

**Design:** `docs/superpowers/specs/2026-07-22-budgeted-daily-settlement-design.md`

---

## File Map

New scheduler package `src/main/java/com/stardew/craft/time/settlement/`:

- `DailySettlementPhase.java`: state enum only.
- `DailySettlementContext.java`: immutable target date, sleep minute, season-change flag, and frozen player/farm IDs.
- `DailySettlementContextFactory.java`: capture the current or next date plus frozen server player/farm snapshots without publishing the new date.
- `DailySettlementWorkUnit.java`: one-item-at-a-time main-thread work contract.
- `DailySettlementWorkUnits.java`: cursor and atomic work-unit factories with deterministic failure advancement.
- `BudgetedWorkRunner.java`: clock-driven per-tick time/item budget loop.
- `DailySettlementCoordinator.java`: single active state machine, retries, drain-on-stop, and result retention.
- `DailySettlementServices.java`: production coordinator/barrier ownership keyed by `MinecraftServer`; pure scheduler constructors remain dependency-injected.
- `DailySettlementPlanFactory.java`: exact ordered world/player/commit work-unit construction.
- `DailySettlementBarrier.java`: participating-player lock, ready result cache, reconnect, and acknowledgement state.
- `DailySettlementAccessGuard.java`: one authoritative server-side gate for locked-player movement, teleport, interaction, and gameplay payloads.
- `DailySettlementMetrics.java`: per-settlement and per-subsystem durations, item counts, leases, sync loads, retries, failures, and lock-to-ready summary.
- `DailySettlementEvents.java`: server tick, login, logout, and stopping integration.
- `DailySettlementRandom.java`: stable subsystem/object random seeds.

New network types:

- `network/overnight/OvernightBarrierPayload.java`: S2C `LOCKED` state keyed by absolute day.
- `network/overnight/OvernightReadyAckPayload.java`: C2S acknowledgement when the ready black screen is clicked.

Existing managers keep ownership of their daily behavior. Each heavy manager adds `createDailyWorkUnit(...)`; its old synchronous method drains that same work unit for compatibility, preventing two implementations from diverging.

---

### Task 1: Pure Time-Budgeted Runner

**Files:**
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementWorkUnit.java`
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementWorkUnits.java`
- Create: `src/main/java/com/stardew/craft/time/settlement/BudgetedWorkRunner.java`
- Create: `src/test/java/com/stardew/craft/time/settlement/BudgetedWorkRunnerTest.java`

- [ ] **Step 1: Write failing runner tests**

Cover these exact cases with a fake nanosecond clock and recording units:

```java
@Test
void stopsStartingItemsAfterTimeBudget() {
    FakeClock clock = new FakeClock(0L, 2_000_000L);
    RecordingUnit unit = new RecordingUnit("crops", 10);
    BudgetedWorkRunner runner = new BudgetedWorkRunner(clock::nanoTime);

    BudgetedWorkRunner.TickResult result = runner.run(unit, 4_000_000L, 100);

    assertEquals(2, result.processedItems());
    assertFalse(unit.isComplete());
}

@Test
void alwaysRunsOneItemToPreventStarvation() {
    FakeClock clock = new FakeClock(10_000_000L, 10_000_000L);
    RecordingUnit unit = new RecordingUnit("trees", 2);

    BudgetedWorkRunner.TickResult result =
        new BudgetedWorkRunner(clock::nanoTime).run(unit, 1L, 100);

    assertEquals(1, result.processedItems());
}
```

Also test item-count ceiling, exact cursor resumption, completion, zero/negative argument rejection, and overshoot reporting.

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.BudgetedWorkRunnerTest" --no-daemon
```

Expected: compilation fails because the settlement runner types do not exist.

- [ ] **Step 3: Implement the minimal contracts**

Use these public shapes:

```java
public interface DailySettlementWorkUnit extends AutoCloseable {
    String name();
    String currentItemIdentity();
    boolean isComplete();
    void runNext() throws Exception;
    void skipFailedItem();
    default int maxRetries() { return 0; }
    @Override default void close() {}
}

public final class BudgetedWorkRunner {
    @FunctionalInterface public interface NanoClock { long nanoTime(); }
    public record TickResult(int processedItems, long elapsedNanos,
                             long overshootNanos, boolean complete) {}

    public TickResult run(DailySettlementWorkUnit unit,
                          long budgetNanos, int itemLimit) {
        Objects.requireNonNull(unit, "unit");
        if (budgetNanos <= 0L) throw new IllegalArgumentException("budgetNanos must be positive");
        if (itemLimit <= 0) throw new IllegalArgumentException("itemLimit must be positive");

        long started = clock.nanoTime();
        int processed = 0;
        while (!unit.isComplete() && processed < itemLimit) {
            if (processed > 0 && clock.nanoTime() - started >= budgetNanos) break;
            unit.runNext();
            processed++;
        }
        long elapsed = Math.max(0L, clock.nanoTime() - started);
        return new TickResult(processed, elapsed,
            Math.max(0L, elapsed - budgetNanos), unit.isComplete());
    }
}
```

The constructor stores the injected `NanoClock` in `clock`. `DailySettlementWorkUnits.cursor(name, entries, identity, consumer, onClose)` must copy the supplied collection once, retain an index, advance only after a successful consumer call, report the stable identity for the current entry, use `maxRetries() == 0`, and make `skipFailedItem` advance exactly one entry. `atomic(name, action, onClose)` exposes one item, returns `maxRetries() == 2`, and becomes complete after success or skip. Add `drain(unit)` as a try-with-resources loop that runs entries synchronously and applies the same per-entry skip/retry policy as the coordinator.

- [ ] **Step 4: Run tests and verify GREEN**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.BudgetedWorkRunnerTest" --no-daemon
git diff --check
```

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/stardew/craft/time/settlement src/test/java/com/stardew/craft/time/settlement/BudgetedWorkRunnerTest.java
git commit -m "feat: add budgeted settlement runner"
```

---

### Task 2: Coordinator State Machine And Failure Policy

**Files:**
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementPhase.java`
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementContext.java`
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementContextFactory.java`
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementCoordinator.java`
- Create: `src/test/java/com/stardew/craft/time/settlement/DailySettlementCoordinatorTest.java`

- [ ] **Step 1: Write failing coordinator tests**

Build coordinator tests entirely from recording work units. Verify:

- one `SettlementContext` is accepted from `IDLE`;
- duplicate start for the same target day is idempotent;
- a different target day is rejected while active;
- phase order is `PREPARE`, `WORLD_BATCHES`, `PLAYER_BATCHES`, `COMMIT`, `READY`;
- all players share one target day;
- a thrown cursor item is reported and skipped immediately so the remaining objects continue;
- a thrown atomic unit is retried on two later ticks, then reported and skipped;
- every opened work unit closes on success and failure;
- `drain()` ignores the tick budget and reaches `READY`;
- `finishReady()` returns the coordinator to `IDLE` without losing retained player results.

Use an immutable context:

```java
DailySettlementContext context = new DailySettlementContext(
    226, 3, 0, 2, 1560, false,
    List.of(playerA, playerB), Set.of(farmOwner));
```

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.DailySettlementCoordinatorTest" --no-daemon
```

- [ ] **Step 3: Implement the state machine**

Required API:

```java
public enum DailySettlementPhase {
    IDLE, PREPARE, WORLD_BATCHES, PLAYER_BATCHES, COMMIT, READY
}

public record DailySettlementContext(
    int absoluteDay, int year, int season, int day,
    int sleepMinute, boolean seasonChanged,
    List<UUID> playerIds, Set<UUID> farmOwnerIds
) {
    public DailySettlementContext {
        playerIds = List.copyOf(playerIds);
        farmOwnerIds = Set.copyOf(farmOwnerIds);
    }
}
```

`DailySettlementContextFactory` is the only production capture API used by later tasks:

```java
public final class DailySettlementContextFactory {
    public static DailySettlementContext captureNextDay(StardewTimeManager time,
                                                         int sleepMinute,
                                                         Collection<UUID> playerIds,
                                                         Collection<UUID> farmOwnerIds) {
        TargetDate target = TargetDate.after(time.getCurrentYear(),
            time.getCurrentSeason(), time.getCurrentDay());
        return target.toContext(sleepMinute, List.copyOf(playerIds), Set.copyOf(farmOwnerIds));
    }

    public static DailySettlementContext captureCurrentDay(StardewTimeManager time) {
        TargetDate target = TargetDate.of(time.getCurrentYear(),
            time.getCurrentSeason(), time.getCurrentDay(), false);
        return target.toContext(time.getCurrentTime(), List.of(), Set.of());
    }
}
```

Implement `TargetDate` as a private record in the factory. `after` performs the existing 28-day season and four-season year rollover without mutating `StardewTimeManager`; `absoluteDay` is `(year - 1) * 112 + season * 28 + day`. The production wiring in Task 8 gathers the server player and farm-owner snapshots before calling this pure factory.

Inject the clock, budget supplier, item-limit supplier, plan factory, and lifecycle listener. Do not access NeoForge statics in the pure coordinator constructor. Keep retry count per active atomic work unit. Cursor items have zero retries and are skipped after their first exception; atomic units retry on two later ticks and are skipped after the third failure. Every report includes `unit.name()`, `unit.currentItemIdentity()`, and `context.absoluteDay()`.

- [ ] **Step 4: Verify GREEN and compatibility compilation**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.DailySettlementCoordinatorTest" --no-daemon
.\gradlew.bat classes --no-daemon
git diff --check
```

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/stardew/craft/time/settlement src/test/java/com/stardew/craft/time/settlement/DailySettlementCoordinatorTest.java
git commit -m "feat: add daily settlement coordinator"
```

---

### Task 3: Overnight Barrier, Ready Cache, And Client Input Gate

**Files:**
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementBarrier.java`
- Create: `src/main/java/com/stardew/craft/network/overnight/OvernightBarrierPayload.java`
- Create: `src/main/java/com/stardew/craft/network/overnight/OvernightReadyAckPayload.java`
- Modify: `src/main/java/com/stardew/craft/network/PacketHandler.java`
- Modify: `src/main/java/com/stardew/craft/network/overnight/OvernightSettlementPayload.java`
- Modify: `src/main/java/com/stardew/craft/network/overnight/ClientOvernightHandler.java`
- Modify: `src/main/java/com/stardew/craft/client/gui/overnight/SleepWaitingOverlayScreen.java`
- Modify: `src/main/java/com/stardew/craft/network/payload/SleepCancelPayload.java`
- Create: `src/test/java/com/stardew/craft/time/settlement/DailySettlementBarrierTest.java`
- Create: `src/test/java/com/stardew/craft/time/settlement/OvernightBarrierIntegrationContractTest.java`

- [ ] **Step 1: Write failing pure barrier tests**

The barrier stores `UUID -> targetDay` locks and `UUID -> DailySettlementBarrier.ReadyResult` payloads. Define the retained value explicitly as a nested type and test lock idempotency, stale-day rejection, ready publication, ACK removal, disconnect retention, and reconnect lookup.

```java
public static record ReadyResult(int absoluteDay, OvernightSettlementPayload payload) {
    public ReadyResult {
        Objects.requireNonNull(payload, "payload");
    }
}
```

```java
barrier.lockAll(226, List.of(alex, sam));
assertTrue(barrier.isLocked(alex));
assertFalse(barrier.canCancelSleep(alex));

DailySettlementBarrier.ReadyResult ready =
    new DailySettlementBarrier.ReadyResult(226, payload);
barrier.publishReady(alex, ready);
assertSame(ready, barrier.readyResult(alex, 226).orElseThrow());
assertFalse(barrier.acknowledge(alex, 225));
assertTrue(barrier.acknowledge(alex, 226));
```

- [ ] **Step 2: Write failing structural client/network tests**

Use JDK compiler AST, not normalized source text. Assert:

- `SleepWaitingOverlayScreen` calls cancel only when unlocked;
- locked/non-ready input is consumed without closing;
- ready input invokes `ClientOvernightHandler.startReadySequence`;
- receiving `OvernightSettlementPayload` stores it instead of immediately replacing the waiting screen;
- `SleepCancelPayload` checks the server barrier before removing a vote;
- both new payloads are registered with the correct direction.

- [ ] **Step 3: Run tests and verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.DailySettlementBarrierTest" --tests "com.stardew.craft.time.settlement.OvernightBarrierIntegrationContractTest" --no-daemon
```

- [ ] **Step 4: Implement the protocol and unchanged visual behavior**

`OvernightBarrierPayload` carries `absoluteDay` and `locked`. It must not carry display text or progress. Add `absoluteDay` to `OvernightSettlementPayload`, retaining convenience constructors for debug/test callers.

`LOCKED` is encoded as `OvernightBarrierPayload(absoluteDay, true)`. Readiness is encoded by the matching `OvernightSettlementPayload`; do not send `locked=false` at `READY`, because the server gameplay lock remains until acknowledgement. A reconnect in ready state receives the lock payload first and then the retained settlement payload. Only a matching ready ACK clears the server lock.

The screen input state is:

```java
if (!locked) {
    cancel();
} else if (!ready) {
    // Consume input and keep the unchanged black waiting screen.
} else {
    ClientOvernightHandler.startReadySequence(absoluteDay);
}
return true;
```

`DailySettlementBarrier.publishReady(UUID, ReadyResult)` retains the lock, stores readiness for that same absolute day, and rejects stale results. `acknowledge(UUID, absoluteDay)` removes both lock and retained result only when the day matches. This keeps server gameplay frozen after `READY` until the player actually clicks through the black screen. Disconnect changes neither map; login reads the current state and resends `LOCKED` or the retained settlement payload.

On the ready click, send `OvernightReadyAckPayload(absoluteDay)`, close only the waiting overlay through `onDayAdvanced`, then start the existing pass-out/level-up/shipping screen stack.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.DailySettlementBarrierTest" --tests "com.stardew.craft.time.settlement.OvernightBarrierIntegrationContractTest" --no-daemon
.\gradlew.bat classes --no-daemon
git diff --check
```

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/stardew/craft/time/settlement/DailySettlementBarrier.java src/main/java/com/stardew/craft/network/overnight src/main/java/com/stardew/craft/network/PacketHandler.java src/main/java/com/stardew/craft/client/gui/overnight/SleepWaitingOverlayScreen.java src/main/java/com/stardew/craft/network/payload/SleepCancelPayload.java src/test/java/com/stardew/craft/time/settlement
git commit -m "feat: gate overnight screen on settlement readiness"
```

---

### Task 4: Cursorize Crops, Trees, Fruit Trees, And Sprinklers

**Files:**
- Modify: `src/main/java/com/stardew/craft/manager/CropGrowthManager.java`
- Modify: `src/main/java/com/stardew/craft/manager/TreeGrowthManager.java`
- Modify: `src/main/java/com/stardew/craft/manager/FruitTreeGrowthManager.java`
- Modify: `src/main/java/com/stardew/craft/manager/SprinklerManager.java`
- Create: `src/test/java/com/stardew/craft/time/settlement/RegisteredDailyManagerWorkUnitTest.java`

- [ ] **Step 1: Write failing manager work-unit tests**

For each manager verify snapshot isolation, one-entry cursor advancement, mutation-safe pending add/remove application on close, and synchronous compatibility draining.

The production shape for each manager is:

```java
public DailySettlementWorkUnit createDailyWorkUnit(ServerLevel level,
                                                    DailySettlementContext context) {
    List<GlobalPos> snapshot = new ArrayList<>(registeredPositions);
    processing = true;
    return DailySettlementWorkUnits.cursor(
        "crops",
        snapshot,
        globalPos -> globalPos.dimension().location() + ":" + globalPos.pos().toShortString(),
        globalPos -> processDailyEntry(level, context, globalPos),
        () -> {
            processing = false;
            applyPendingChanges();
        });
}

public void growDaily(ServerLevel level) {
    DailySettlementWorkUnits.drain(createDailyWorkUnit(
        level, DailySettlementContextFactory.captureCurrentDay(StardewTimeManager.get())));
}
```

Tests must prove an entry added after work-unit creation is not processed that day.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.RegisteredDailyManagerWorkUnitTest" --no-daemon
```

- [ ] **Step 3: Extract one-entry methods and create units**

Preserve the existing filtering and order inside each extracted method:

- dimension match;
- `FarmDailyProcessHelper.shouldProcessPosition`;
- just-in-time required chunk coverage;
- current block validation;
- existing daily block mutation;
- pending registration changes.

Fruit trees use one stable combined snapshot containing tagged sapling and mature-tree entries so both current loops retain their existing order.

- [ ] **Step 4: Verify focused and existing manager tests**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.RegisteredDailyManagerWorkUnitTest" --tests "com.stardew.craft.manager.*" --no-daemon
.\gradlew.bat classes --no-daemon
git diff --check
```

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/stardew/craft/manager/CropGrowthManager.java src/main/java/com/stardew/craft/manager/TreeGrowthManager.java src/main/java/com/stardew/craft/manager/FruitTreeGrowthManager.java src/main/java/com/stardew/craft/manager/SprinklerManager.java src/test/java/com/stardew/craft/time/settlement/RegisteredDailyManagerWorkUnitTest.java
git commit -m "refactor: cursorize registered daily managers"
```

---

### Task 5: Cursorize Animals, Fish Ponds, Grass, And Wild Seeds

**Files:**
- Modify: `src/main/java/com/stardew/craft/manager/AnimalGrowthManager.java`
- Modify: `src/main/java/com/stardew/craft/fishpond/service/FishPondDailyUpdateService.java`
- Modify: `src/main/java/com/stardew/craft/manager/PastureGrassGrowthManager.java`
- Modify: `src/main/java/com/stardew/craft/manager/WildTreeSeedManager.java`
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementRandom.java`
- Create: `src/test/java/com/stardew/craft/time/settlement/FarmSystemDailyWorkUnitTest.java`
- Create: `src/test/java/com/stardew/craft/time/settlement/DailySettlementRandomTest.java`

- [ ] **Step 1: Write deterministic random tests**

Required API:

```java
RandomSource forPosition(long worldSeed, int absoluteDay,
                         String subsystem, BlockPos pos);
RandomSource forId(long worldSeed, int absoluteDay,
                   String subsystem, long stableId);
```

Verify identical values for identical inputs, changed streams for different days/subsystems/positions, and independence from processing order.

- [ ] **Step 2: Write failing work-unit tests**

Verify:

- animal records retain `lastProcessedAbsDay` behavior and do not double-process;
- fish ponds process a frozen pond-ID snapshot;
- pasture grass collects its eligible positions once and uses position-derived randomness;
- wild tree seed entries use a frozen map-entry snapshot and target-day randomness;
- draining with 1-item and 100-item batches produces identical final records and blocks.

- [ ] **Step 3: Verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.FarmSystemDailyWorkUnitTest" --tests "com.stardew.craft.time.settlement.DailySettlementRandomTest" --no-daemon
```

- [ ] **Step 4: Implement resumable units**

Pass `context.absoluteDay()` and deterministic random sources explicitly into extracted one-record methods. Do not call `level.getRandom()` from the new cursor body. Preserve existing probabilities and state transitions.

Keep old `growDaily`/`onNewDay` methods as compatibility drains of the new units.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.FarmSystemDailyWorkUnitTest" --tests "com.stardew.craft.time.settlement.DailySettlementRandomTest" --no-daemon
.\gradlew.bat classes --no-daemon
git diff --check
```

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/stardew/craft/manager/AnimalGrowthManager.java src/main/java/com/stardew/craft/fishpond/service/FishPondDailyUpdateService.java src/main/java/com/stardew/craft/manager/PastureGrassGrowthManager.java src/main/java/com/stardew/craft/manager/WildTreeSeedManager.java src/main/java/com/stardew/craft/time/settlement/DailySettlementRandom.java src/test/java/com/stardew/craft/time/settlement
git commit -m "refactor: cursorize farm system settlement"
```

---

### Task 6: Cursorize Public-Area Scans And Spawn Services

**Files:**
- Modify: `src/main/java/com/stardew/craft/manager/ForageSpawnService.java`
- Modify: `src/main/java/com/stardew/craft/manager/ArtifactSpotSpawnService.java`
- Modify: `src/main/java/com/stardew/craft/manager/QuarrySpawnService.java`
- Modify: `src/main/java/com/stardew/craft/manager/CoalForestClumpSpawnService.java`
- Modify: `src/main/java/com/stardew/craft/manager/FarmCaveDailyService.java`
- Create: `src/test/java/com/stardew/craft/time/settlement/PublicAreaDailyWorkUnitTest.java`

- [ ] **Step 1: Write failing scan cursor tests**

Represent large rectangular scans as stable integer cursors rather than materializing every `BlockPos`:

```java
int width = maxX - minX + 1;
int x = minX + cursor % width;
int z = minZ + cursor / width;
```

Test exact first/last positions, resume without duplicates, daily caps across ticks, deterministic output across item budgets, and closure after early cap completion.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.PublicAreaDailyWorkUnitTest" --no-daemon
```

- [ ] **Step 3: Implement cursor units**

Forage and artifact services expose ordered units for their current zones. Forest-farm forage snapshots eligible forest farms once. Quarry and coal forest retain current attempt caps but run one attempt per item. Farm caves snapshot eligible farms and process one farm per item.

`SecretWoodsAccessManager.ensureEntranceReady` remains an atomic timed work unit because it does not contain an unbounded registry or area loop.

- [ ] **Step 4: Verify GREEN and service regressions**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.PublicAreaDailyWorkUnitTest" --tests "com.stardew.craft.manager.*" --no-daemon
.\gradlew.bat classes --no-daemon
git diff --check
```

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/stardew/craft/manager/ForageSpawnService.java src/main/java/com/stardew/craft/manager/ArtifactSpotSpawnService.java src/main/java/com/stardew/craft/manager/QuarrySpawnService.java src/main/java/com/stardew/craft/manager/CoalForestClumpSpawnService.java src/main/java/com/stardew/craft/manager/FarmCaveDailyService.java src/test/java/com/stardew/craft/time/settlement/PublicAreaDailyWorkUnitTest.java
git commit -m "refactor: budget public daily scans"
```

---

### Task 7: Just-In-Time Settlement Chunk Leases

**Files:**
- Modify: `src/main/java/com/stardew/craft/farm/FarmDailyProcessHelper.java`
- Modify: `src/main/java/com/stardew/craft/farm/FarmChunkManager.java`
- Modify: `src/main/java/com/stardew/craft/time/StardewTimeManager.java`
- Create: `src/test/java/com/stardew/craft/time/settlement/DailySettlementChunkLeaseTest.java`
- Modify: `src/test/java/com/stardew/craft/farm/FarmChunkManagerTest.java`

- [ ] **Step 1: Write failing lease-scope tests**

Verify overlapping entry leases reference-count correctly, only newly acquired chunks are released, crop/sprinkler/tree radius policies remain tight, exception cleanup closes every lease, and the time manager no longer calls `setInteriorChunksForced(..., true, "daily_settlement")`.

- [ ] **Step 2: Verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.DailySettlementChunkLeaseTest" --tests "com.stardew.craft.farm.FarmChunkManagerTest" --no-daemon
```

- [ ] **Step 3: Replace global daily force-loading**

Add a settlement lease scope backed by the existing `TemporaryChunkLeaseTracker`. `FarmDailyProcessHelper.leasePosition` and `leaseBounds` return `AutoCloseable` entry leases instead of directly calling `setChunkForced`; every cursor item wraps its block/entity mutation in try-with-resources and releases the lease before the next item. The root scope is opened in prepare and closed in coordinator cleanup as a leak-safe fallback. Tests must prove a completed item does not leave its chunks forced until the end of the whole settlement.

Remove both the global interior force-on and force-off calls from `StardewTimeManager`. Indoor daily entries must request their actual registered chunk through the same scope.

- [ ] **Step 4: Verify GREEN**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.DailySettlementChunkLeaseTest" --tests "com.stardew.craft.farm.FarmChunkManagerTest" --no-daemon
.\gradlew.bat classes --no-daemon
git diff --check
```

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/stardew/craft/farm/FarmDailyProcessHelper.java src/main/java/com/stardew/craft/farm/FarmChunkManager.java src/main/java/com/stardew/craft/time/StardewTimeManager.java src/test/java/com/stardew/craft/time/settlement/DailySettlementChunkLeaseTest.java src/test/java/com/stardew/craft/farm/FarmChunkManagerTest.java
git commit -m "perf: lease daily settlement chunks on demand"
```

---

### Task 8: Extract Player Batches, Commit Steps, And Wire Rollover Triggers

**Files:**
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java`
- Create: `src/main/java/com/stardew/craft/time/settlement/PlayerDailySettlementService.java`
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementServices.java`
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementDateView.java`
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementEvents.java`
- Modify: `src/main/java/com/stardew/craft/time/StardewTimeManager.java`
- Modify: `src/main/java/com/stardew/craft/event/DimensionEventHandler.java`
- Modify: `src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java`
- Create: `src/test/java/com/stardew/craft/time/settlement/DailySettlementPlanOrderTest.java`
- Create: `src/test/java/com/stardew/craft/time/settlement/DailySettlementLifecycleContractTest.java`

- [ ] **Step 1: Write failing plan-order tests**

Assert the exact work-unit name order defined by the design. Explicitly verify:

- weather preparation precedes crops;
- crops precede sprinklers;
- world work precedes players;
- shipping flush precedes ledger consumption;
- player level-up application precedes payload construction;
- farm cursors advance only in commit after world completion;
- shop/mail/special-order hooks occur before ready publication.
- the backing `StardewTimeManager` date remains the old night throughout prepare/world/player work and is published as the final commit item;
- settlement work sees the frozen target date through `DailySettlementDateView`, whose thread-local scope is cleared in `finally` after every item.

- [ ] **Step 2: Write failing lifecycle AST contracts**

Use JDK AST to require:

- `advanceDayWithSleepTime` delegates to the coordinator instead of directly invoking heavy managers;
- sleep confirm, pass-out, and vanilla sleep completion share that entry point;
- server tick advances the coordinator once;
- login resends lock or retained ready data;
- normal server stop calls `drain` before final settlement cleanup;
- logout does not cancel the shared coordinator.

- [ ] **Step 3: Verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.DailySettlementPlanOrderTest" --tests "com.stardew.craft.time.settlement.DailySettlementLifecycleContractTest" --no-daemon
```

- [ ] **Step 4: Extract the monolithic method**

`StardewTimeManager.advanceDayWithSleepTime` must not mutate its backing date fields. It freezes participating players and farm owners once, computes the target date once, and calls:

```java
List<UUID> playerIds = server.getPlayerList().getPlayers().stream()
    .filter(PlayerDailySettlementService::participates)
    .map(ServerPlayer::getUUID)
    .toList();
FarmInstanceRegistry farms = FarmInstanceRegistry.get();
Set<UUID> ownerIds = playerIds.stream()
    .map(farms::getOwnerForPlayer)
    .filter(Objects::nonNull)
    .collect(Collectors.toUnmodifiableSet());
DailySettlementContext context = DailySettlementContextFactory.captureNextDay(
    StardewTimeManager.get(), sleepMinute, playerIds, ownerIds);
DailySettlementServices.get(server).coordinator().start(context);
```

`DailySettlementServices` owns a weak, synchronized `MinecraftServer -> Services` map. `Services` is a record containing one coordinator and one barrier; `get(server)` creates production dependencies exactly once, `find(server)` does not create during event guards, and `remove(server)` runs only after stop-drain and cleanup. All later code uses this service instead of `DailySettlementCoordinator.get(...)`.

`DailySettlementDateView.run(context, action)` sets a server-thread `ThreadLocal<DailySettlementContext>`, rejects nesting with a different target day, and clears it in `finally`. The coordinator executes each work-unit item through this scope. `StardewTimeManager` date/time getters return the scoped target year/season/day and `MORNING_START` only while that item is executing; ordinary block-entity ticks continue to see the old night. The final commit unit copies the target date into the backing fields, resets time/event flags, and marks the time data dirty. This unit is last, immediately followed in the same coordinator tick by ready publication, so `CaskBlockEntity`, `CoffeeMakerBlockEntity`, `SolarPanelBlockEntity`, `CrabPotBlockEntity`, and absolute-minute `TimedProductionBlockEntity` implementations cannot observe a partially settled new date.

`DailySettlementPlanFactory` builds separate ordered lists for prepare, world, player, and commit. Shipping-bin flush is the first prepare unit. Player work snapshots UUIDs and resolves the current `ServerPlayer` only when running the item; a disconnected UUID retains its result/cache state without aborting the queue.

The commit list runs forecast, farm cursor, special-order, lost-and-found, bookseller, shop-stock, mail, dirty-mark, and guaranteed lease/cache cleanup before the final date-publication unit. No commit unit may follow date publication. Publish every `ReadyResult` immediately after that final unit, transition `READY -> IDLE`, and retain unacknowledged per-player results in `DailySettlementBarrier`.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.*" --tests "com.stardew.craft.farm.FarmCursorLifecycleTest" --no-daemon
.\gradlew.bat classes --no-daemon
git diff --check
```

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/stardew/craft/time src/main/java/com/stardew/craft/event/DimensionEventHandler.java src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java src/test/java/com/stardew/craft/time/settlement
git commit -m "feat: schedule daily settlement across ticks"
```

---

### Task 9: Gameplay Isolation, Configuration, And Telemetry

**Files:**
- Modify: `src/main/java/com/stardew/craft/Config.java`
- Modify: `src/main/java/com/stardew/craft/time/StardewTimeManager.java`
- Modify: `src/main/java/com/stardew/craft/time/settlement/DailySettlementEvents.java`
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementAccessGuard.java`
- Modify: `src/main/java/com/stardew/craft/network/PacketHandler.java`
- Modify: `src/main/java/com/stardew/craft/server/performance/PerformanceTiming.java`
- Modify: `src/main/java/com/stardew/craft/server/performance/PerformanceCounter.java`
- Modify: `src/main/java/com/stardew/craft/server/performance/PerformanceReportFormatter.java`
- Create: `src/main/java/com/stardew/craft/time/settlement/DailySettlementMetrics.java`
- Create: `src/test/java/com/stardew/craft/time/settlement/DailySettlementIsolationContractTest.java`
- Create: `src/test/java/com/stardew/craft/server/performance/DailySettlementPerformanceTelemetryTest.java`

- [ ] **Step 1: Write failing configuration and telemetry tests**

Require common-config values:

```java
DAILY_SETTLEMENT_BUDGET_MILLIS = defineInRange("dailySettlementBudgetMillis", 4, 1, 10);
DAILY_SETTLEMENT_ITEM_LIMIT = defineInRange("dailySettlementItemLimit", 256, 1, 4096);
```

Add `DAILY_SETTLEMENT_TOTAL`, `DAILY_SETTLEMENT_TICK`, `DAILY_SETTLEMENT_ATOMIC_ITEM`, and `DAILY_SETTLEMENT_LOCK_TO_READY` timings. Add `DAILY_SETTLEMENT_TICKS`, `DAILY_SETTLEMENT_ITEMS`, `DAILY_SETTLEMENT_CHUNK_LEASES`, `DAILY_SETTLEMENT_OVERSHOOTS`, `DAILY_SETTLEMENT_RETRIES`, `DAILY_SETTLEMENT_PERMANENT_FAILURES`, `DAILY_SETTLEMENT_PLAYER_BATCHES`, and `DAILY_SETTLEMENT_READY_PUBLICATIONS` counters; retain the existing `DAILY_SYNC_CHUNK_LOADS` counter in the summary.

`DailySettlementMetrics` keeps `Map<String, SubsystemMetrics>` for each work-unit name, where `SubsystemMetrics` records cumulative nanoseconds, processed items, retries, and permanent failures. Its immutable ready summary also records total wall time, tick count, maximum per-tick work time, lease count, synchronous chunk-load delta, overshoot count/worst overshoot, player batch count, and lock-to-ready time. Verify two subsystem names remain separate and that the formatter prints all fields without per-object log lines.

- [ ] **Step 2: Write failing gameplay isolation contracts**

Assert:

- virtual Stardew time does not advance again while settlement is active;
- date-driven and absolute-minute production block entities continue seeing the old frozen night until final commit;
- locked players are held at a captured position, have movement velocity cleared, and have teleport, block/entity/item interaction canceled by server events;
- `SleepCancelPayload` remains the authoritative custom-payload lock check;
- every gameplay `playToServer` registration is wrapped by the shared access guard, except `SleepCancelPayload` and `OvernightReadyAckPayload`, which perform their own day-aware barrier checks;
- other dimensions are not globally paused;
- coordinator work remains on the server tick thread.

- [ ] **Step 3: Verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.DailySettlementIsolationContractTest" --tests "com.stardew.craft.server.performance.DailySettlementPerformanceTelemetryTest" --no-daemon
```

- [ ] **Step 4: Implement isolation and recording**

`DailySettlementAccessGuard` captures a locked player's dimension, position, yaw, and pitch when the barrier lock is sent. Its player-tick hook clears velocity and restores that anchor if movement or teleport changed it. Use cancellable NeoForge teleport and player/block/entity/item interaction events as the early rejection path. Release the anchor only after a matching ready ACK removes the barrier lock; retain it across logout and rebuild it from the player's login position when a reconnect is still locked.

Add a generic `DailySettlementAccessGuard.gated(handler)` wrapper for `PacketHandler` C2S registrations. It schedules on the payload context executor as before, resolves the sending `ServerPlayer`, and invokes the original handler only when the player is not locked. Register `SleepCancelPayload` and `OvernightReadyAckPayload` without the wrapper so they can validate the matching absolute day themselves. Do not pause the Minecraft server or unrelated dimensions.

Do not add individual pause flags to production machines. `TimedProductionBlockEntity` readiness is derived from Stardew absolute minutes, while casks, coffee makers, solar panels, crab pots, mastery statues, heaters, and other date/season readers use `StardewTimeManager` getters. The frozen backing date plus the item-scoped `DailySettlementDateView` therefore prevents extra progress during the 1-3 second settlement window. Add AST contracts covering the base absolute-minute calculation and the representative date-driven tickers named above so a later refactor cannot expose the pending date early.

Record one summary log at ready publication and warning logs only for budget overshoots, retries, or permanent failures. Do not log each successful object.

- [ ] **Step 5: Verify GREEN**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.DailySettlementIsolationContractTest" --tests "com.stardew.craft.server.performance.DailySettlementPerformanceTelemetryTest" --no-daemon
.\gradlew.bat classes --no-daemon
git diff --check
```

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/stardew/craft/Config.java src/main/java/com/stardew/craft/time src/main/java/com/stardew/craft/network/PacketHandler.java src/main/java/com/stardew/craft/server/performance src/test/java/com/stardew/craft/time/settlement src/test/java/com/stardew/craft/server/performance
git commit -m "perf: add settlement budgets and telemetry"
```

---

### Task 10: Full Regression Verification

**Files:**
- Modify only if verification exposes a directly related defect.

- [ ] **Step 1: Run all settlement-focused tests with forced execution**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.time.settlement.*" --no-daemon --rerun-tasks
```

Expected: all settlement scheduler, manager, barrier, lifecycle, isolation, chunk, deterministic-random, and telemetry tests pass with zero skips.

- [ ] **Step 2: Run the full test suite**

```powershell
.\gradlew.bat test --no-daemon --rerun-tasks
```

- [ ] **Step 3: Run the complete build**

```powershell
.\gradlew.bat build --no-daemon --rerun-tasks
```

- [ ] **Step 4: Verify artifacts and repository state**

```powershell
git diff --check
git status --short
Get-ChildItem build/test-results/test/TEST-*.xml
Get-FileHash -Algorithm SHA256 build/libs/stardewcraft-*.jar
```

Parse all JUnit XML suites and confirm total failures, errors, and skipped tests are zero. Record the final test count and JAR SHA-256.

- [ ] **Step 5: Final whole-range review**

Request an independent reviewer for the complete implementation range. Fix and re-review every Critical or Important finding before push.

- [ ] **Step 6: Push**

```powershell
git push origin perf/server-tps
```

Confirm `perf/server-tps` and `origin/perf/server-tps` point to the same commit and the working tree is clean.
