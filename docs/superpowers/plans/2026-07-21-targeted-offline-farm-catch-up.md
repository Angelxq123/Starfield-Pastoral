# Targeted Offline Farm Catch-Up Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace whole-farm synchronous chunk loading during player login with deterministic loading of only the chunks required by registered crops, tree saplings, and sprinklers.

**Architecture:** Build an immutable catch-up plan from the three manager indexes before acquiring chunks. The plan filters entries to the target dimension and inclusive farm X/Z bounds, sorts them deterministically, and derives one required-chunk union using per-system effect radii. A generic identity-keyed lease tracker supplies overlap-safe, rollback-safe temporary chunk ownership; `FarmChunkManager` adapts it to the existing synchronous `setChunkForced` and measured `getChunk` behavior.

**Tech Stack:** Java 21, NeoForge 21.1, Minecraft `GlobalPos`/`ChunkPos`, JUnit 5, Gradle 9.2.

---

### Task 1: Build A Deterministic Offline Catch-Up Plan

**Files:**
- Create: `src/main/java/com/stardew/craft/farm/OfflineFarmCatchUpPlan.java`
- Create: `src/test/java/com/stardew/craft/farm/OfflineFarmCatchUpPlanTest.java`

- [ ] **Step 1: Write failing filtering and chunk-union tests**

Add focused tests for a wished-for package-private factory:

```java
OfflineFarmCatchUpPlan.create(
        dimension, farmMin, farmMax,
        cropPositions, treePositions, sprinklerPositions);
```

Cover these behaviors independently:

- only positions in the same dimension and inclusive farm X/Z bounds are retained;
- Y is ignored when filtering, matching current catch-up semantics;
- crop positions require only their containing chunk;
- tree positions require every chunk intersecting `pos +/- 8` blocks;
- sprinkler positions require every chunk intersecting `pos +/- 2` blocks;
- required chunks are the de-duplicated union across all three systems;
- returned position lists and chunk set are immutable;
- inputs in different iteration orders produce the same sorted output;
- an empty farm produces empty lists and zero required chunks.

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.farm.OfflineFarmCatchUpPlanTest" --no-daemon
```

Expected: compilation fails because `OfflineFarmCatchUpPlan` does not exist.

- [ ] **Step 3: Implement the minimal immutable plan**

Create a package-private record holding:

```java
record OfflineFarmCatchUpPlan(
        List<GlobalPos> crops,
        List<GlobalPos> trees,
        List<GlobalPos> sprinklers,
        Set<ChunkPos> requiredChunks) { ... }
```

Use one shared dimension/bounds filter, normalize reversed X/Z bounds, and sort retained positions by X, then Y, then Z. Build the chunk union with integer block bounds converted through `SectionPos.blockToSectionCoord` (or the repository's equivalent Minecraft API), iterating inclusively. Copy every collection before publication. Tree radius `8` covers all current prefab structures; sprinkler radius `2` covers the iridium 5x5 footprint.

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run the command from Step 2. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the plan core**

```powershell
git add src/main/java/com/stardew/craft/farm/OfflineFarmCatchUpPlan.java src/test/java/com/stardew/craft/farm/OfflineFarmCatchUpPlanTest.java
git commit -m "perf: plan targeted offline farm catch-up"
```

---

### Task 2: Add An Overlap-Safe Temporary Chunk Lease Core

**Files:**
- Create: `src/main/java/com/stardew/craft/farm/TemporaryChunkLeaseTracker.java`
- Create: `src/test/java/com/stardew/craft/farm/TemporaryChunkLeaseTrackerTest.java`
- Modify: `src/main/java/com/stardew/craft/farm/FarmChunkManager.java`
- Modify: `src/test/java/com/stardew/craft/farm/FarmChunkManagerTest.java`

- [ ] **Step 1: Write failing lease lifecycle tests**

Define a package-private generic tracker around an injected backend and test:

- levels are keyed by identity, not `equals`;
- first acquisition calls the backend once per distinct chunk;
- overlapping leases increment per-chunk references and do not reacquire shared chunks;
- closing one lease releases only chunks whose reference count reaches zero;
- closing the same handle twice is harmless;
- duplicate chunks within one request are acquired once;
- an empty request returns a valid no-op handle;
- acquisition failure rolls back all chunks acquired by that request without disturbing pre-existing overlapping references;
- `closeAll(level)` and `closeAll()` release tracked chunks exactly once.

- [ ] **Step 2: Run tracker tests and verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.farm.TemporaryChunkLeaseTrackerTest" --no-daemon
```

Expected: compilation fails because the tracker does not exist.

- [ ] **Step 3: Implement the generic tracker**

Keep the tracker independent of NeoForge so it can be tested with plain objects. Use `IdentityHashMap<L, Map<ChunkPos, Entry>>`, per-chunk reference counts, and an `AutoCloseable` handle that captures its level and distinct chunk set. The backend contract acquires a chunk synchronously and returns whether StardewCraft owns the temporary force ticket; release only when the final reference closes and that ownership flag is true. Roll back in reverse acquisition order after any runtime failure.

- [ ] **Step 4: Run tracker tests and verify GREEN**

Run the command from Step 2. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Adapt FarmChunkManager to targeted handles**

Add a handle-returning API:

```java
AutoCloseable acquireTemporaryChunks(ServerLevel level, Collection<ChunkPos> chunks)
```

The production backend must preserve current behavior:

- call `setChunkForced(..., true)` only when the chunk is not already forced;
- record whether this manager added the force ticket;
- increment `FARM_SYNC_CHUNK_LOADS` and measure `FARM_SYNC_CHUNK_LOAD` around `level.getChunk` for every first tracker acquisition;
- release only tickets owned by this manager;
- clear all leases for the matching level on shutdown.

Keep `acquireTemporaryFarmChunks`, `releaseTemporaryFarmChunks`, `forceLoadFarmChunksForCatchUp`, and `releaseTempChunks` as compatibility wrappers for unmodified callers. Key wrapper handles by level identity plus slot so two server levels cannot share a lease accidentally.

- [ ] **Step 6: Run farm lease and compatibility tests**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.farm.TemporaryChunkLeaseTrackerTest" --tests "com.stardew.craft.farm.FarmChunkManagerTest" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit the lease manager**

```powershell
git add src/main/java/com/stardew/craft/farm/TemporaryChunkLeaseTracker.java src/test/java/com/stardew/craft/farm/TemporaryChunkLeaseTrackerTest.java src/main/java/com/stardew/craft/farm/FarmChunkManager.java src/test/java/com/stardew/craft/farm/FarmChunkManagerTest.java
git commit -m "perf: add targeted temporary chunk leases"
```

---

### Task 3: Integrate Targeted Loading Into Offline Catch-Up

**Files:**
- Modify: `src/main/java/com/stardew/craft/farm/OfflineFarmCatchUp.java`
- Create: `src/test/java/com/stardew/craft/farm/OfflineFarmCatchUpContractTest.java`

- [ ] **Step 1: Write failing integration contract tests**

Add source-level or extracted-helper contract tests requiring `OfflineFarmCatchUp.catchUp` to:

- collect crop, tree, and sprinkler manager positions once;
- build `OfflineFarmCatchUpPlan` before any chunk acquisition;
- call `acquireTemporaryChunks(level, plan.requiredChunks())`;
- use try-with-resources so release happens on success and exceptions;
- process `plan.crops()`, then `plan.trees()`, then `plan.sprinklers()` without rescanning manager indexes;
- update `lastOnlineDay`, `lastOnlineSeason`, and dirty state only after all processing succeeds;
- still advance the cursor for an empty plan while loading zero chunks.

- [ ] **Step 2: Run the focused contract and plan tests and verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.farm.OfflineFarmCatchUpContractTest" --tests "com.stardew.craft.farm.OfflineFarmCatchUpPlanTest" --no-daemon
```

Expected: the contract test fails because catch-up still loads whole farm bounds and each processor rescans its manager.

- [ ] **Step 3: Integrate the immutable plan**

At the start of actual catch-up, obtain the three manager instances and create one `OfflineFarmCatchUpPlan` from their current registered positions. Acquire only `plan.requiredChunks()` in try-with-resources. Refactor the three private processors to accept their already-filtered lists while preserving the existing crop -> tree -> sprinkler order, block-type checks, `level.isLoaded` guards, grace-period behavior, logging, and successful cursor update semantics.

Do not add deferred execution, retry markers, logout cursor changes, or visitor occupancy fixes in this task.

- [ ] **Step 4: Run focused farm tests and production compilation**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.farm.*" --no-daemon
.\gradlew.bat classes --no-daemon
git diff --check
```

Expected: all focused tests pass, production code compiles, and no whitespace errors are reported.

- [ ] **Step 5: Commit catch-up integration**

```powershell
git add src/main/java/com/stardew/craft/farm/OfflineFarmCatchUp.java src/test/java/com/stardew/craft/farm/OfflineFarmCatchUpContractTest.java
git commit -m "perf: load only active farm chunks for catch-up"
```

---

### Task 4: Complete Regression Verification

**Files:**
- No production changes expected.

- [ ] **Step 1: Run the complete test suite**

```powershell
.\gradlew.bat test --no-daemon
```

Expected: `BUILD SUCCESSFUL` with zero failures.

- [ ] **Step 2: Run the complete build**

```powershell
.\gradlew.bat build --no-daemon
```

Expected: `BUILD SUCCESSFUL` and `build/libs/stardewcraft-0.5.1fix4.jar` exists.

- [ ] **Step 3: Verify result and artifact identity**

```powershell
$reports = Get-ChildItem build/test-results/test -Filter 'TEST-*.xml'
$tests = ($reports | Select-String -Pattern '<testsuite ' | ForEach-Object { if ($_.Line -match 'tests="(\d+)"') { [int]$matches[1] } } | Measure-Object -Sum).Sum
$failures = ($reports | Select-String -Pattern '<testsuite ' | ForEach-Object { if ($_.Line -match 'failures="(\d+)"') { [int]$matches[1] } } | Measure-Object -Sum).Sum
$errors = ($reports | Select-String -Pattern '<testsuite ' | ForEach-Object { if ($_.Line -match 'errors="(\d+)"') { [int]$matches[1] } } | Measure-Object -Sum).Sum
"tests=$tests failures=$failures errors=$errors"
Get-FileHash build/libs/stardewcraft-0.5.1fix4.jar -Algorithm SHA256
git diff --check
git status --short --branch
```

Expected: zero failures/errors, a clean worktree, and a recorded SHA-256 for the new server JAR.

---

## Deferred Work

- Retry-idempotent chunk-by-chunk or cross-tick catch-up.
- Correcting logout cursor overwrite after failed catch-up.
- Initializing new farm cursors from the current Stardew date.
- Correct visitor-aware farm occupancy tracking.
- Migrating from `setChunkForced` to a dedicated NeoForge `TicketController`.
- Detecting an external force ticket added after StardewCraft acquired a lease.

These remain separate phases because they alter persistence, retry, ownership, or player-presence semantics rather than the number of chunks needed by the existing synchronous catch-up.
