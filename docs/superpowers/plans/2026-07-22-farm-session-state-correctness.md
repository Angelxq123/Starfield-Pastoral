# Farm Session State Correctness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep farm catch-up cursors and occupancy counts correct across creation, logout, visits, repeated entry requests, and farm switching.

**Architecture:** Make farm creation accept an explicit current date at a testable registry boundary and remove logout cursor mutation. Add a pure player-to-slot occupancy tracker, then adapt `FarmChunkManager` and the three entry/exit/logout call sites to use actual recorded occupancy instead of farm membership.

**Tech Stack:** Java 21, NeoForge 21.1, JUnit 5, Gradle 9.2.

---

### Task 1: Correct Farm Cursor Lifecycle

**Files:**
- Modify: `src/main/java/com/stardew/craft/farm/FarmInstanceRegistry.java`
- Modify: `src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java`
- Create: `src/test/java/com/stardew/craft/farm/FarmCursorLifecycleTest.java`

- [ ] **Step 1: Write failing cursor lifecycle tests**

Cover:

- a package-private date-explicit registry creation method initializes `lastOnlineDay` and `lastOnlineSeason` to supplied current values;
- requesting creation for an existing owner returns the existing farm without replacing its cursor;
- the public creation path obtains `StardewTimeManager.getAbsoluteDay()` and `getCurrentSeason()` and delegates to the explicit method;
- the logout handler does not call `setLastOnlineDay`, `setLastOnlineSeason`, or `OfflineFarmCatchUp.computeAbsoluteDay`;
- NBT load and farm transfer cursor preservation remain present.

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.farm.FarmCursorLifecycleTest" --no-daemon
```

Expected: tests fail because creation still defaults to Spring 1 and logout still advances the cursor.

- [ ] **Step 3: Add date-explicit creation and remove logout writes**

Keep the existing public `createFarm` signature. It reads one `StardewTimeManager` instance, then delegates to a package-private creation method with current absolute day and season. The explicit method sets the new instance cursor before publication. Remove only the cursor mutations and associated dirty write from logout; leave occupancy cleanup for Task 2.

- [ ] **Step 4: Run focused tests and production compilation**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.farm.FarmCursorLifecycleTest" --no-daemon
.\gradlew.bat classes --no-daemon
git diff --check
```

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/com/stardew/craft/farm/FarmInstanceRegistry.java src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java src/test/java/com/stardew/craft/farm/FarmCursorLifecycleTest.java
git commit -m "fix: preserve farm catch-up cursors"
```

---

### Task 2: Track Actual Farm Occupancy

**Files:**
- Create: `src/main/java/com/stardew/craft/farm/FarmOccupancyTracker.java`
- Create: `src/test/java/com/stardew/craft/farm/FarmOccupancyTrackerTest.java`
- Modify: `src/main/java/com/stardew/craft/farm/FarmChunkManager.java`
- Modify: `src/test/java/com/stardew/craft/farm/FarmChunkManagerTest.java`
- Modify: `src/main/java/com/stardew/craft/network/payload/FarmEntryRequestPayload.java`
- Modify: `src/main/java/com/stardew/craft/event/InteriorPortalInteractionEvents.java`
- Modify: `src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java`
- Create: `src/test/java/com/stardew/craft/farm/FarmOccupancyIntegrationContractTest.java`

- [ ] **Step 1: Write failing occupancy state-machine tests**

Test the pure tracker for:

- first entry increments the target slot;
- duplicate same-slot entry is idempotent;
- switching slots decrements the old slot and increments the new slot;
- leave returns/removes the actual slot;
- unknown/double leave is a no-op;
- independent players share counts correctly;
- clear removes counts and assignments.

- [ ] **Step 2: Run tracker tests and verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.farm.FarmOccupancyTrackerTest" --no-daemon
```

Expected: compilation fails because the tracker does not exist.

- [ ] **Step 3: Implement the minimal generic tracker**

Use a player-key map and per-slot integer counts. Reject null player keys and negative slot indexes. Return enough transition information for `FarmChunkManager` logging without exposing mutable maps.

- [ ] **Step 4: Integrate FarmChunkManager and call sites**

Replace direct `playerCounts` mutation with the tracker. Entry records the supplied target farm slot. Exit and logout remove the player UUID's recorded slot without consulting `getFarmForPlayer`. Repeated entry and farm switching must remain balanced. Server shutdown clears occupancy in the same guaranteed cleanup block that currently clears counts.

Update:

- `FarmEntryRequestPayload` to keep entering the selected target farm;
- `InteriorPortalInteractionEvents.handleFarmExit` to leave tracked occupancy without resolving the player's owned/member farm;
- `PlayerDataEventHandler` to call the tracked logout cleanup.

Keep compatibility overloads only when an existing caller or public surface requires them.

- [ ] **Step 5: Run focused farm state tests**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.farm.FarmOccupancyTrackerTest" --tests "com.stardew.craft.farm.FarmOccupancyIntegrationContractTest" --tests "com.stardew.craft.farm.FarmChunkManagerTest" --tests "com.stardew.craft.farm.FarmCursorLifecycleTest" --no-daemon
.\gradlew.bat classes --no-daemon
git diff --check
```

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/com/stardew/craft/farm/FarmOccupancyTracker.java src/test/java/com/stardew/craft/farm/FarmOccupancyTrackerTest.java src/main/java/com/stardew/craft/farm/FarmChunkManager.java src/test/java/com/stardew/craft/farm/FarmChunkManagerTest.java src/main/java/com/stardew/craft/network/payload/FarmEntryRequestPayload.java src/main/java/com/stardew/craft/event/InteriorPortalInteractionEvents.java src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java src/test/java/com/stardew/craft/farm/FarmOccupancyIntegrationContractTest.java
git commit -m "fix: track actual farm occupancy"
```

---

### Task 3: Complete Regression Verification

- [ ] Run `\.\gradlew.bat test --no-daemon --rerun-tasks`.
- [ ] Run `\.\gradlew.bat build --no-daemon --rerun-tasks`.
- [ ] Count XML tests and confirm zero failures/errors/skips.
- [ ] Record the JAR SHA-256.
- [ ] Run `git diff --check` and verify a clean synchronized branch after push.

## Deferred Work

- Budgeted daily settlement scheduling.
- Retry-idempotent cross-tick offline catch-up.
- Old-save cursor migration.
- Dedicated NeoForge chunk tickets.
