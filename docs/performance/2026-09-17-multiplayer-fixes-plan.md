# Multiplayer Reliability and Tick Budget Plan

Goal: resolve the ten targeted review findings and make overworld wizard entry nonblocking and cancellable.

Architecture: retain the existing settlement coordinator and durable checkpoints. Use completed-result guards for cleanup, explicit local sleep consent, consistent vote eligibility, transient chunk tickets and bounded server-thread queues. Preserve vanilla chunk tracking rather than modifying its sent-state record. Split seasonal work and publication using existing work-unit/cursor patterns.

Validation: regression tests for offline cleanup/relogin, confirmation broadcasts, changing voters and cancellation, teleport ticket ownership/timeout, and settlement publication budgets. Run focused tests during implementation, then test/build once integrated. No remote push or live-server performance claim.

- [x] Sleep: confirmation isolation; dimension/AFK quorum refresh; consistent voter set; delayed cancellation.
- [x] Settlement: retain incomplete offline records; budget result delivery and READY metrics; handle reconnect and ACK between READY ticks.
- [x] Seasonal work: bounded public restoration and weed scans.
- [x] Teleports: transient leases; bounded wizard entry/return; cancel stale sessions/positions; remove tracking-view mutation; bound tower scans.
- [x] Focused review, regression verification, full test/build and delivery: 73 suites, 623 tests, zero failures/errors/skips; build passed. See `2026-09-17-multiplayer-fix-results.md` for artifact and live-test limits.
