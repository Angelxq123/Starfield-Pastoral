# Farm Session State Correctness Design

## Goal

Prevent farm catch-up cursors and occupancy counts from drifting during farm creation, logout, visiting, repeated entry requests, and farm switching.

## Cursor Semantics

- A newly created farm starts at the current Stardew absolute day and season.
- Successful offline catch-up remains the authority that advances a stale cursor on login.
- Day rollover remains the authority that advances cursors for farms represented by online players.
- Logout does not advance the cursor. This prevents a failed catch-up from being erased by a later logout event.
- Farm transfer and NBT load continue to preserve the stored cursor.

The public `createFarm` path obtains one current `StardewTimeManager` snapshot and delegates to a package-private date-explicit creation method. The explicit method is independently testable without a running server.

## Occupancy Semantics

Farm occupancy represents where a player actually entered, not which farm they own or belong to.

- Entering records `player UUID -> target farm slot`.
- Repeating entry for the same player and slot is idempotent.
- Entering another slot first decrements the previous slot, then increments the new slot.
- Exit and logout remove the recorded slot; they never infer it from farm membership.
- Unknown exits are no-ops and cannot decrement another farm.
- Server shutdown clears both counts and player-to-slot assignments even when chunk-ticket cleanup throws.

A package-private generic `FarmOccupancyTracker<P>` owns this state machine. `FarmChunkManager` adapts `ServerPlayer` UUIDs and keeps the existing count query API.

## Scope

This phase does not change offline catch-up retry idempotency, daily settlement scheduling, save migration, farm permissions, teleport routing, or block/chunk processing. Those remain separate phases.

## Validation

- Unit tests cover explicit farm creation dates and occupancy transitions.
- Contract tests ensure logout no longer writes the cursor and all entry/exit/logout call sites use tracked occupancy.
- The complete unit suite and build run before push.
