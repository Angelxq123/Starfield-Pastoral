# Server TPS Baseline Protocol

This document defines the baseline protocol. It does not claim that a live
baseline has been captured because 30-50 test clients are not available here.

## Target

- 30-50 concurrent players
- 20 TPS
- Average MSPT <= 35 ms
- P95 MSPT <= 45 ms
- P99 MSPT <= 50 ms

## Required Tools

- Spark profiler installed on the test server
- Operator access to `/stardew perf`
- Each tested StardewCraft commit recorded with `git rev-parse HEAD`

## Run Manifest

Choose one exact target player count from 30 through 50. Use that count for
both the baseline and optimized runs. Record these build-under-test identities
separately for the baseline and optimized runs:

- StardewCraft commit
- Tested StardewCraft mod JAR SHA-256

Retain these common values for every run:

- Minecraft, Java, and Spark versions
- Server loader and distribution with versions, including NeoForge or Youer as
  applicable
- Dependency or modpack identity, version, and hash computed without the
  StardewCraft JAR; alternatively, a complete mod manifest in which only the
  StardewCraft entry may differ
- Hash of all relevant server and mod configuration
- Complete JVM arguments, server hardware, view distance, and simulation
  distance
- Named world snapshot identity and hash
- Exact scenario script, version, and hash

The retained scenario script must define player roles, activity allocation,
cadence, destination order, repetitions, duration, and objective endpoint.

## Preparation And Measurement

Use the same named world snapshot and this sequence before every baseline and
optimized run:

1. Stop the server and restore the clean named world snapshot.
2. Start the server and connect the background clients required by the
   scenario.
3. Perform no scenario workload during preparation. After all required
   background clients are ready, wait a fixed 30-second warm-up.
4. Run `/stardew perf reset`.
5. Immediately before starting the scripted workload, run
   `/spark profiler start` without a fixed timeout.
6. Record the start timestamp and begin the scenario script.
7. At the objective endpoint, end scripted activity and immediately run
   `/spark profiler stop`. Record the end timestamp and Spark report URL, then
   record the complete `/stardew perf status` output.

A comparison is valid only when all environmental, runtime, dependency,
configuration, world, and scenario values are identical, including the
warm-up and target count. The build-under-test commit, its JAR SHA-256, and any
aggregate hash that includes that JAR are excluded from this rule. Rerun any
trial that departs from the shared controls or player-count requirements.

## Scenario A: Steady Multiplayer

Maintain the exact configured target player count for exactly 300 seconds.
Allocate players across the public map, farms, mines, interiors, fishing, and
combat exactly as defined by the retained script. The endpoint is 300 seconds
after scripted activity begins.

## Scenario B: Concurrent Login

Record the exact background count and total target count; the total must equal
the background count plus ten reconnect clients. During preparation, connect
the ten designated reconnect clients once, disconnect them, then complete the
common 30-second warm-up with only the background clients connected. After
profiling starts, all ten reconnect clients must begin their connection
attempts within one ten-second window. The endpoint is when all ten are present
and a fixed 60-second stabilization interval has completed.

## Scenario C: Day Rollover

Record exact occupied and inactive farm counts, with at least ten occupied and
at least twenty inactive farms in the restored named snapshot. Follow the
retained sleep and settlement-screen script. The endpoint is 60 seconds after
the authoritative server day value increments; clients may be required to
close settlement screens, but screen timing does not determine the endpoint.

## Scenario D: Teleport And Interior Churn

Assign exactly twenty churn clients to repeat the fixed farm -> public map ->
mine -> interior sequence at the script's fixed cadence and repetition count
for exactly 300 seconds. Keep all other players in their documented steady
roles. The endpoint is 300 seconds after the churn sequence begins.

## Capture

For every scenario retain:

- Start and end timestamps and actual measured duration
- Configured target count and minimum and maximum online player counts
- Spark report URL
- Complete `/stardew perf status` output
- Sample counts for every reported timing and values for every reported counter
- Relevant server log warnings or errors

## Interpretation

Spark is the authoritative full-scenario source for the average, P95, and P99
MSPT targets. `ServerPerformanceRecorder` retains `SERVER_TICK` as a rolling
1,200-sample window, approximately 60 seconds at 20 TPS. Therefore,
`/stardew perf status` is endpoint and subsystem diagnostic evidence, not a
five-minute aggregate. Retain and interpret every other timing sample count and
counter value in the context of its scenario.
