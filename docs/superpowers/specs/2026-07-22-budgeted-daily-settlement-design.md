# Budgeted Daily Settlement Design

## Goal

Reduce the Stardew day-rollover TPS spike by spreading required daily settlement work across server ticks without changing visible gameplay results, shared time semantics, or the existing overnight result screens.

The target environment is a multiplayer server with approximately 30 concurrent players. The design prioritizes a bounded main-thread cost over minimizing total settlement latency. A typical settlement may keep players on the existing black overnight screen for roughly one to three seconds.

## Current Problem

`StardewTimeManager.advanceDayWithSleepTime` currently performs the entire rollover synchronously in one server-thread call. It advances the date and then serially runs crops, trees, fruit trees, wild tree seeds, sprinklers, pasture grass, animals, fish ponds, forage, artifact spots, quarry content, forest content, farm caves, player settlement, mail, shops, and other daily services.

The method also force-loads a large interior chunk set for the duration of the world pass. At multiplayer scale, the combined scans, synchronous chunk loads, block updates, player synchronization, and packet creation can produce a single-tick stall even when total work is otherwise acceptable.

## Chosen Approach

Use a main-thread, time-budgeted settlement queue protected by a global overnight barrier.

Rejected alternatives:

- Running one whole subsystem per tick is simpler, but a single large crop or animal pass can still exceed the tick budget.
- Running world mutation asynchronously is unsafe because Minecraft levels, chunks, block entities, entities, and most SavedData interactions are main-thread owned.

## Shared-Time Semantics

There is one active settlement for the whole server, not one settlement per player. A `SettlementContext` captures one target date, season, year, sleep minute, weather input, online-player snapshot, and participating-farm snapshot.

All relevant players remain behind the same overnight black-screen barrier until every required task reaches commit. The server sends `READY` to all participating players only after the shared world and player state is complete. No player may enter the new morning while another player is still settling the same date.

Players outside the Stardew gameplay dimensions do not receive a separate time line. They cannot start or observe another Stardew date while the global settlement is active.

## State Machine

The coordinator uses the following states:

```text
IDLE
  -> PREPARE
  -> WORLD_BATCHES
  -> PLAYER_BATCHES
  -> COMMIT
  -> READY
  -> IDLE
```

Only one settlement may be active. Further sleep, pass-out, or vanilla sleep-finished triggers targeting the same rollover are idempotent. A trigger for another rollover is rejected while the coordinator is active.

### PREPARE

- Capture the immutable `SettlementContext`.
- Freeze the participating player and farm lists for this settlement.
- Flush shipping-bin buffers before player ledgers are consumed.
- Establish the daily-process scope and initialize work-unit snapshots.
- Internally lock overnight cancellation once the sleep vote has committed.
- Keep the existing black waiting screen unchanged; do not replace its text with a separate "settling" presentation.

### WORLD_BATCHES

Run the existing world semantics in this order:

1. Festival and season-transition preparation.
2. New-day weather selection and scheduled-NPC reset.
3. Crops.
4. Trees.
5. Fruit trees.
6. Wild tree seeds.
7. Sprinklers.
8. Pasture grass.
9. Animals.
10. Fish ponds.
11. Public forage.
12. Forest-farm forage.
13. Artifact spots.
14. Quarry.
15. Coal forest.
16. Secret Woods entrance maintenance.
17. Farm cave production.

Heavy managers become resumable work units. Each unit snapshots its eligible entries once and advances an internal cursor over those entries. It must not rescan the mutable registry on every tick. Small global services may remain atomic but must be individually timed.

All work remains on the server thread.

### PLAYER_BATCHES

Process the frozen player snapshot in bounded batches. Preserve current behavior for:

- farm eligibility;
- energy, exhaustion, health, and pass-out penalties;
- tool upgrade progression and notifications;
- shipping ledger consumption and history;
- pending skill levels, recipes, professions, and mastery onboarding;
- player-data synchronization;
- day-started quest events;
- pass-out result consumption;
- wake-up event scheduling;
- construction, mail, and other existing player-specific daily hooks.

Disconnected players do not cancel the shared job. Their committed results remain available to the existing persistence and reconnect paths. A reconnecting player receives the current barrier state and either remains on the black screen or receives the completed settlement payload.

### COMMIT

Commit only after all required world and player units have completed or exhausted the defined failure policy.

- Complete special orders, lost-and-found, bookseller, shop-stock, and mail daily hooks.
- Update weather forecast state.
- Advance farm catch-up cursors only after the relevant farm work has completed.
- Mark the time manager and affected SavedData dirty.
- Release every temporary chunk lease and daily-process cache in guaranteed cleanup.
- Build each participating player's existing `OvernightSettlementPayload`.
- Publish the completed shared date and barrier result.

No daily world work continues after `READY`.

### READY

Send the completed settlement payload and readiness state to all participating players. The existing result sequence remains unchanged: pass-out information, level-up and profession screens, and shipping results still appear as they do now.

`READY` only changes the input gate on the existing black overnight screen. The next click skips that black screen and begins the existing result sequence. It does not skip result or profession-choice screens.

## Tick Budget

The default settlement budget is 4 milliseconds per server tick. The coordinator stops starting new items when the deadline is reached and resumes on the next tick.

Each tick also has an item-count ceiling so timer granularity or unusually cheap entries cannot create an unbounded pass. At least one item may run when work remains, preventing starvation. An individual item is atomic and may overshoot the deadline; such overshoots are measured so the item can be split in a later optimization.

The budget should be configurable within a conservative range, with 4 milliseconds as the server default. Configuration changes affect future ticks without changing the active settlement's date or snapshots.

## Chunk Loading

Do not force-load the entire interior allocation for the full settlement.

Work units acquire only the chunks required by their current entry or small entry batch. Crop and sprinkler entries use tight position coverage; trees use their required neighborhood; buildings and animal areas use explicit bounds. Leases are overlap-safe and released when the batch or work unit no longer needs them.

The coordinator owns the settlement lease scope and closes it in `COMMIT` or failure cleanup. Existing chunk tickets that predate settlement are never released by the coordinator.

## Gameplay Isolation

Splitting work across ticks must not create observable partial-day gameplay.

- Participating players remain behind the server-side overnight barrier.
- Voting may be canceled before the rollover commits. Once internally locked, clicks no longer withdraw the vote or close the black screen.
- Stardew movement, interaction, teleport, and gameplay payloads from locked players are rejected or deferred until `READY`.
- Daily-dependent ticking that can conflict with settlement is held at the rollover boundary; unrelated dimensions and normal server ticking continue.
- Each work unit uses its frozen entry snapshot.
- Daily random decisions use a deterministic source derived from world seed, target date, subsystem identity, and stable object identity such as position or record ID. Results cannot depend on how many ticks the queue takes.

The acceptance rule is: except for a longer black-screen wait, the player must not observe a gameplay difference caused by batching.

## Client And Network Behavior

The client retains one visual overnight waiting screen.

- Before the sleep vote commits, the current cancel behavior remains available.
- When the server starts settlement, it sends an internal locked state. The screen does not change its waiting text or visual treatment.
- While locked and not ready, clicks and keys do not cancel the vote or close the screen.
- When the server sends `READY`, the next click closes only the black waiting screen and starts the existing settlement result sequence.
- A duplicate locked or ready packet is idempotent.
- A reconnect receives the current coordinator state and the player's completed payload when available.

The network state must identify the target absolute day so stale packets from a previous rollover cannot unlock the current screen.

## Failure Handling

- An individual object failure records subsystem, target day, and stable object identity, then continues with the next object.
- A failed atomic subsystem is retried on a later tick up to two times.
- After the retry limit, the subsystem is marked failed and the coordinator follows the mod's existing fail-open daily semantics rather than trapping the server in an infinite black screen.
- Commit cleanup always runs, including daily-process cache cleanup, player barrier release, and temporary chunk lease release.
- A normal server stop drains the remaining queue without the gameplay tick budget before the final save.
- Player logout never cancels the shared settlement.

Hard process termination during the short active settlement is outside this phase's atomicity guarantee. Fully atomic crash recovery would require persistent, idempotent processed-day markers across every affected world object and is deferred as a separate reliability project. No old-save migration is required for this phase.

## Performance Telemetry

Extend the existing server performance recorder with:

- total settlement duration;
- number of ticks used;
- maximum settlement work time in one tick;
- per-subsystem duration;
- objects processed per subsystem;
- synchronous chunk loads and lease counts;
- budget overshoot count and worst overshoot;
- retry and permanent-failure counts;
- player batch count;
- time from internal lock to `READY`.

Logs should summarize one settlement and report exceptional overshoots or failures without logging every successfully processed object.

## Testing

Use strict TDD with pure scheduler tests before integration changes.

Required automated coverage:

- a fake-clock scheduler stops at its time and item budgets and resumes from the exact cursor;
- at least one item runs when work remains, preventing starvation;
- duplicate rollover triggers do not create duplicate coordinators;
- work-unit ordering preserves the current world and player dependencies;
- different budgets and batch sizes produce identical deterministic results;
- entry snapshots do not absorb objects added during settlement;
- cancellation works before lock and is rejected after lock;
- input cannot close the black screen before `READY`;
- after `READY`, input skips only the black screen and retains all result screens;
- all participating players share one target date and readiness transition;
- disconnect and reconnect restore the correct locked or ready state;
- object failures, subsystem retries, and retry exhaustion advance according to policy;
- temporary chunk leases and daily caches close on success, exception, and server stop;
- farm catch-up cursors advance only after required farm work completes;
- the monolithic time-manager method no longer directly runs all heavy managers;
- XML test results remain free of failures, errors, and skips;
- the full Gradle test and build tasks succeed.

Live multiplayer load testing is not required before implementation completion. The telemetry remains available for later real-server tuning.

## Scope

Included:

- coordinator and work-unit abstractions;
- conversion of heavy daily managers to resumable cursors;
- just-in-time settlement chunk leases;
- overnight input lock and ready gate;
- reconnect and normal-shutdown handling;
- automated tests and performance telemetry.

Deferred:

- background-thread world mutation;
- per-object crash-atomic hard-termination recovery;
- old-save migration;
- redesign of existing overnight result screens;
- unrelated daily gameplay changes.
