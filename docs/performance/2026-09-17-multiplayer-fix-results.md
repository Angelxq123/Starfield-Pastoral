# Multiplayer Settlement and Wizard Travel Fixes

Baseline: `68d6fe311` (upstream 0.6.1fix2 merged). Review scope is the ten findings in `2026-09-17-multiplayer-targeted-review.md` and their direct integration paths.

## Changes

- Preserve incomplete offline settlement checkpoints. Cleanup requires the matching day, completed personal stages and a durable payload; login resumes pending work exactly once.
- Run personal result preparation, persistence, sending and READY delivery under the existing coordinator budget. Include final publication in settlement timing.
- Handle login/ACK between READY ticks without relocking acknowledged players or recreating completed settlements. Restore prepared payloads on reconnect while preserving the world barrier.
- Send sleep progress only to accepted voluntary voters. Recheck eligibility on dimension changes, activity changes and real-tick intervals; valid AFK votes remain in both numerator and denominator.
- Recheck voluntary sleep quorum after collapse delays. Keep forced 2AM advancement separate.
- Replace synchronous forceload requests with reference-counted transient FULL tickets for wizard travel, farm entry and seasonal restoration.
- Queue hot and cold overworld wizard entry/return together: at most eight request checks and a 2 ms budget per tick, with a 200-tick timeout. Validate the exact session, source dimension, distance and gameplay locks; release tickets on completion, invalidation and timeout.
- Keep vanilla chunk tracking authoritative, removing direct sent-view mutations. Clear automatic-routing suppression even when a teleport fails.
- Deduplicate tower scans and inspect loaded columns over successive ticks, with at most 1024 scan steps and 2 ms per tick. Cold columns are deferred and abandoned work expires when players leave.
- Split seasonal public restoration into individual positions and weed refresh into rows of at most 80 block checks.

## Verification

`./gradlew.bat test build --console=plain` passed on 2026-09-17: 73 test suites, 623 tests, zero failures/errors/skips. `git diff --cached --check` passed. Regression coverage includes offline settlement recovery, READY budgets and login/ACK interleavings, sleep consent/eligibility, cold teleport timeout/fairness and deferred tower scans. Targeted failing regressions were observed before the corresponding fixes.

Artifact: `build/libs/stardewcraft-0.6.1fix2.jar` (219131509 bytes). No remote push or deployment was performed.

## Limits

- No live multiplayer server, client visual checks or measured TPS/latency comparison was run.
- Budgets are checked between work items; a single vanilla teleport, packet operation or atomic transaction cannot be preempted.
- Tower discovery state remains in memory. After a server restart, nearby towers are rescanned under the same budget.
- Seasonal restoration records remain durable when a chunk does not become available before its timeout.

Recommended live checks: simultaneous cold/hot overworld tower entry and return; mixed client view distances; movement/logout during queued entry; unconfirmed sleep dialogs with another voter; AFK/dimension changes; cancellation during collapse; logout/login and ACK during both world work and READY delivery; season changes with players spread across farms.
