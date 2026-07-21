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
