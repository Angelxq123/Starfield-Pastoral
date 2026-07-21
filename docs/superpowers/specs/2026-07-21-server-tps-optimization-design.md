# Starfield Pastoral 0.5 Server TPS Optimization Design

## 1. Context

This performance branch targets the official `v0.5.1fix4` source tree and a
NeoForge/Youer multiplayer server with 30-50 concurrent players. The project is
still in development, so this work does not preserve old save formats or the
custom 0.4.x runtime architecture.

Performance changes are implemented in the mod source rather than in a
separate addon. The public `api.v1` surface is appropriate for content
extensions, but it does not provide replacement boundaries for chunk tickets,
daily settlement, block entity ticking, login synchronization, NPC scheduling,
or server work budgeting. Implementing those changes in an addon would require
fragile Mixins against internal control flow.

## 2. Goals

- Sustain 20 TPS with 30-50 concurrent players under representative Stardew
  gameplay load.
- Keep average MSPT at or below 35 ms, P95 at or below 45 ms, and P99 at or
  below 50 ms.
- Keep non-interactive background latency below 500 ms without delaying combat,
  interaction, fishing, or player movement.
- Prevent inactive farms and interiors from remaining force-loaded.
- Prevent login, teleport, festival transitions, and day rollover from creating
  unbounded main-thread spikes.
- Make each optimization measurable, independently testable, and easy to replay
  on top of later official releases.

## 3. Non-Goals

- Plugin integration or a Bukkit-facing bridge.
- Compatibility with 0.4.x save data, packet formats, or custom runtime hooks.
- Curios integration removed by official 0.5.1fix2.
- Client FPS and visual rendering optimization, except where client work is
  triggered by excessive server synchronization.
- Gameplay or balance changes unrelated to server performance.

## 4. Source And Branch Strategy

- `main` remains an exact mirror of the official upstream branch.
- All performance changes live on `perf/server-tps`.
- Each optimization is a focused commit with a benchmark or regression test.
- Official updates are fetched from `upstream`, fast-forwarded into `main`, and
  then rebased into `perf/server-tps`.
- Content additions continue to use data packs and `api.v1`; core scheduling
  and lifecycle changes remain in the main mod source.

## 5. Observability First

Before behavior changes, add low-overhead counters and timers for:

- Mean, P95, and P99 execution time by server work category.
- Active and queued chunk leases by reason.
- Synchronous chunk load count and duration.
- Active farms, interiors, machines, NPCs, and managed animals.
- Login synchronization packet count, encoded bytes, and serialization time.
- Background queue depth and oldest-task age.
- Day rollover, teleport, login, and festival-transition peak tick time.

Metrics are disabled or sampled at low frequency by default and can be exposed
through a development command. Spark remains the external profiler used to
validate internal measurements.

## 6. Chunk Lifecycle

### 6.1 Loading Policy

- Public areas and occupied farms use normal player view-distance loading.
- Inactive farms and interiors remain unloaded.
- Production machines use absolute completion timestamps and catch up when
  their chunk loads; they never keep a chunk loaded solely to count time.
- Teleports acquire a short-lived lease for a 3x3 destination area. The lease
  expires 5-10 seconds after arrival, after normal view-distance tickets exist.
- Festival map changes and initial farm construction use budgeted temporary
  leases rather than synchronous loading of an entire region.

### 6.2 Active And Inactive Farm Settlement

- Occupied farms settle currently loaded chunks at day rollover.
- Inactive farms record `lastProcessedDay` and perform lazy catch-up on the next
  farm entry or relevant chunk load.
- Catch-up work is divided by chunk and system. It is not executed directly in
  the login event.
- Greenhouses and interiors follow the same occupied-versus-inactive rule; the
  current all-interior daily force-load path is removed.

### 6.3 Central Lease Manager

Introduce `StardewChunkLeaseManager` as the sole owner of StardewCraft-created
temporary tickets. Lease reasons and priorities are:

1. `PLAYER_PRELOAD`
2. `FARM_INIT`
3. `FESTIVAL_PATCH`
4. `DAILY_SETTLEMENT`
5. `MAINTENANCE`

The manager provides reference counting, expiration, per-reason metrics, a
global background lease cap, and cleanup during server shutdown. It releases
only tickets it created and never removes player or third-party tickets.

World access and world mutation remain on the server thread. Asynchronous work
may prepare immutable data, but it may not read or write live levels, chunks,
entities, or block entities.

## 7. Login Synchronization Pipeline

Official 0.5.1fix4 still performs many independent full snapshots during login,
including 12 raw registry documents, mail, festivals, JEI, player data,
cosmetics, community center state, quests, special orders, friendships,
fertilizer, fish ponds, museum state, weather, and other world snapshots.
Offline farm catch-up also runs from the login event.

Introduce `LoginSyncCoordinator` with ordered, budgeted stages:

1. Essential player identity, time, and profile state.
2. Content revision/hash negotiation.
3. Only mismatched shared registry documents.
4. Player-specific progress snapshots.
5. Dimension- and chunk-scoped state after the player begins tracking it.
6. Background farm catch-up after the player is safely connected.

Shared content is serialized and hashed once after datapack reload, then reused
for all players. Large snapshots are split across ticks with a target budget of
256-512 KiB per player per tick. Client readiness gates only the Stardew UI that
depends on missing content; it does not block normal server entry.

Fertilizer, fish pond, ore-pan, and similar positional data move from global
login snapshots to chunk-watch synchronization. Cosmetic state is sent as one
batch to the joining player, while the new player's state is broadcast once to
relevant recipients.

## 8. Server Work Scheduler

Introduce `StardewServerWorkScheduler` with four priorities:

- `IMMEDIATE`: combat, interaction, fishing, and movement-sensitive work.
- `HIGH`: teleport preload, login stages, and festival transitions.
- `NORMAL`: NPC schedules, animal state, and coalesced network updates.
- `BACKGROUND`: machine completion, catch-up, maintenance, and cleanup.

Immediate work remains synchronous. Budgeted work uses the remaining server
tick allowance, with an initial combined background budget of 3-5 ms. Once the
budget is exhausted, remaining work continues on the next tick.

Exceptions fail one task, not the scheduler. Repeated failures are rate-limited
and surfaced through diagnostics. Tasks that remain queued beyond their service
target are reported with category and age.

## 9. Machines And Per-Player Work

- Remove server tickers from machines whose only job is comparing an absolute
  completion timestamp.
- Group loaded machines into staggered time buckets and check each group at a
  reduced cadence.
- Preserve low-frequency ticking only for automation or state that truly needs
  active processing.
- Stagger background player checks by UUID so all 50 players are not evaluated
  on the same tick.
- Cache farm, interior, festival, and mine-location state and recompute it only
  when a player crosses a relevant boundary.
- Keep combat and movement-sensitive state at immediate cadence.

## 10. NPC And Animal Runtime

- Maintain managed-entity indexes by active region and stable managed ID.
- Avoid repeated broad entity scans for lookup, duplicate detection, or routine
  schedule work.
- Represent NPCs in unloaded or unobserved regions as virtual schedule state;
  materialize them when a player activates the region.
- Separate low-frequency schedule calculation from nearby entity movement.
- Process animal feeding, mood, produce, and daily state in staggered batches.
- Preserve normal entity ticking for animals near players where animation and
  interaction are visible.

## 11. Incremental Network Updates

- Mark player and world snapshots dirty and send only changed revisions.
- Coalesce multiple changes during one tick into one end-of-tick update.
- Scope broadcasts by dimension, farm, festival session, or entity tracking.
- Include revisions so clients can reject stale packets.
- Record packet count and encoded bytes by payload type to find regressions.

## 12. Failure Handling

- Temporary leases have hard expiry and owner/reason diagnostics.
- Server shutdown releases all leases still owned by StardewCraft.
- Lazy catch-up records completion per chunk/system so interrupted work can be
  resumed without duplicate processing.
- A failing background task is isolated and retried only when the task type has
  an explicit idempotent retry policy.
- Login stages are individually acknowledged or revisioned; failure in optional
  world-state synchronization does not disconnect the player.
- Queue and lease limits reject or defer low-priority work before they affect
  immediate gameplay.

## 13. Validation

Representative scenarios include:

- 50 players distributed across public areas, mines, interiors, and farms.
- 30 farms containing crops, trees, animals, and dense production machines.
- Concurrent login, teleport, and dimension transitions.
- Day rollover with a mix of occupied and inactive farms.
- Festival start/end with concentrated player movement.
- Concurrent mining, combat, fishing, and farm automation.
- Multi-hour soak testing for lease, queue, cache, and entity-index leaks.

Acceptance criteria are:

- 20 TPS sustained under the representative load.
- Average MSPT <= 35 ms, P95 <= 45 ms, and P99 <= 50 ms.
- Non-interactive background latency <= 500 ms during normal load.
- No added latency for interaction, combat, fishing, or movement.
- No force-loaded chunks belonging only to inactive farms.
- No StardewCraft lease leaks after task completion or server shutdown.
- Day rollover produces no main-thread pause longer than 100 ms.

Unit tests cover lease reference counting, expiration, priorities, catch-up
idempotency, login stage ordering, content hash negotiation, dirty snapshot
coalescing, and queue budgeting. Integration tests cover server startup, player
login, chunk tracking, farm entry, day rollover, and clean shutdown. Every phase
also runs the official test suite and full build.

## 14. Delivery Phases

1. Establish the clean 0.5.1fix4 baseline and add observability.
2. Implement the login synchronization coordinator and shared snapshot cache.
3. Implement the central chunk lease manager and lazy inactive-farm catch-up.
4. Add the server work scheduler and migrate pure-timing machines.
5. Stagger player work and add NPC/animal active-region indexes.
6. Add incremental network state and remove remaining redundant full snapshots.
7. Run staged 30-50 player load tests, retain only measured improvements, and
   publish a verified server build.
