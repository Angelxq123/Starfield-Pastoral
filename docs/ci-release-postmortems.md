# Release CI Postmortems

This file is the durable record for release-related CI failures. A release is not considered complete until the exact candidate commit passes the full GitHub workflow from a clean, tracked-only checkout and the pushed GitHub Actions run finishes successfully.

## Recurring structural cause

### 0.6.1 candidate — local clean-checkout gate (2026-09-14)

Before pushing, `build check` rejected the API maturity manifest: its documentation evidence had been generated with local, ignored audit notes present. The clean checkout correctly reported `StardewGiantCrops.doc_ref=no` where the manifest claimed `yes`. Restrict evidence collection to Git-indexed files and regenerate the conservative experimental classification from the published tree. Local modelling/audit directories remain excluded; they are not added to satisfy a gate. This failure occurred locally before GitHub publication.

Several release failures shared the same structural cause: validation was performed in a developer working tree that contained local source assets or locally updated tests, while GitHub Actions built only the committed repository. `./gradlew classes` also hid test and downstream compatibility failures because the workflow actually runs `./gradlew build` and additional verification stages.

The permanent prevention rule is therefore not “fix the next missing file.” It is to validate the exact candidate commit in a clean worktree before pushing it.

## Incident history

| Date (UTC) | Release / commit | Actions run | Verified failure | Classification |
| --- | --- | --- | --- | --- |
| 2026-07-22 | `0.5.2` / `73c171a9` | `29942226058` | Three resource tests read files absent from the GitHub checkout and failed with `NoSuchFileException`. | Local-only dependency |
| 2026-07-30 | `0.5.4` / `4717a0b5` | `30543498768` | Eleven tests depended on repository-external original assets or source files; the clean runner could not find them. | Local-only dependency |
| 2026-08-02 | `0.5.4fix1` / `901e21fd` | `30734828626` | Weapon source-contract tests depended on local Stardew source data absent from GitHub. | Local-only dependency |
| 2026-08-02 | CI follow-up / `7cd290f9` | `30735203382` | The pinned addon canary no longer compiled against the changed public API. | Full workflow not validated before push |
| 2026-08-06 | `0.5.5` / `0f62b6bb` | `31125704695` | GitHub assigned no runner (`runner_id=0`); the check annotation says the hosted job was not acquired after repeated attempts. The run occurred during [GitHub's critical Actions incident](https://stspg.io/rcz3fcm83sff) from 15:22 UTC on August 6 to 02:04 UTC on August 7. | External platform outage |
| 2026-08-07 audit | `0.5.5` / `0f62b6bb` clean checkout | Local reproduction | The exact committed tree fails `compileTestJava` with 12 errors because production construction APIs changed while stale tracked tests remained in the release commit. The original dirty working tree already contained corresponding test edits, but they were deliberately excluded from the release. | Latent tracked-test mismatch |

## 0.5.5 root-cause chain

1. The release was declared ready after `./gradlew classes --offline`, which compiles production code but not test sources and does not execute the rest of the GitHub workflow.
2. Updated local test files were excluded from the release while their older versions remained tracked in GitHub.
3. The push landed during a confirmed GitHub Actions outage. GitHub never started a runner, so this external failure masked the repository's own `compileTestJava` failure.
4. A clean detached checkout of `0f62b6bb` reproduced 12 compiler errors in `AnimalBuildingLifecycleTest`, `AnimalBuildingAutomationCheckpointTest`, and `AnimalBuildingTierDefinitionsTest`.

## Required release procedure

1. Finish and review the intended release diff, including the final policy decision for every tracked test changed by the production code.
2. Create the candidate commit locally, but do not push it yet.
3. Create a clean detached worktree at that commit. Confirm that `git status --short` is empty there.
4. Run the commands in `.github/workflows/build.yml` in order, including Gradle build, compatibility verifiers, GameTest runtime verification, example addon, example data pack, and addon canary.
5. Check that no test, script, or build task requires ignored/untracked paths such as `tmp/`, `源文件/`, or machine-specific absolute paths.
6. Push only after the clean worktree passes. Then monitor the remote Actions run until it reports success.
7. If GitHub itself is degraded, record the check annotation and status incident, wait for recovery, and re-run the same commit. Do not use an empty follow-up commit to disguise an infrastructure retry.

## Test publication policy

The repository must use one consistent state:

- If `src/test` remains tracked, production API changes must include the matching test updates and the clean release commit must compile and pass them.
- If tests are intentionally development-only, remove the whole development-only test set from the Git index and ignore it locally. Do not leave stale tracked copies on GitHub while newer local copies are omitted.

The invalid mixed state was normalized locally on 2026-08-07: all 506
previously tracked files under `src/test` were removed from the Git index,
`/src/test/` was added to `.gitignore`, and the 519 local test files remained
on disk. The API maturity verifier was made independent of local Java tests and
its evidence manifest was regenerated without claiming unpublished test
coverage.

The tracked-only candidate then passed Gradle `build`, 18 compatibility
verifier tests, all 31 required GameTests, runtime shutdown verification, the
example addon build, validation of 66 example data-pack JSON documents, the
pinned addon compilation, and the pinned addon's 45-mixin compatibility audit.

### 0.6.1 candidate — cloth continuity sampling (2026-09-14)

The clean `check` gate rejected Evelyn at the coarse 1/120 versus 1/240 sampling ratio (maximum displacements 0.14621837 / 0.092136994). Denser 1/480 and 1/960 sampling of the unchanged production surface gives 0.04757029 / 0.024577953, with midpoint error falling from 0.019293508 to 0.002320573: the discrepancy is resolved continuous contact acceleration, not a fixed positional jump. Increase continuity sampling density for all garments; retain the existing midpoint, half-step ratio, seam, attachment and penetration limits. No runtime animation or model is changed for this gate correction.

### 0.6.1 candidate — stale GameTest fixtures (2026-09-14)

The full clean-checkout suite reported nine failures against contracts revised during development: three crop checks assumed tall selection in stages whose final models are shorter; template checks assumed one material slot and shift replacement instead of the current removal flow; a farm permission fixture assumed its freed slot was first in the shared FIFO; a pond event fixture used vanilla cod without a configured population request; and the mine reward fixture still expected placeholder swords after slingshots were restored. Update fixtures to the current shipped resources and interaction rules. Crop checks now compare both selection parts to stage geometry, composites exercise both material slots, and farm reuse still verifies the exact recycled slot. Keep inventory, permissions, collision, event-order and lighting assertions. No game rules are changed to satisfy these fixtures.

### 0.6.1 candidate — external SVE is outside the release scope (2026-09-14)

The pinned SVE checkout failed its own macOS dependency verification, then failed compilation against retired wild-tree and dirt-backed artifact-spot internals. The owner explicitly directed this release not to maintain SVE compatibility. Remove the external SVE checkout/build/audit from the mandatory Build workflow and revert the provisional SVE-only production bridges and dependency-metadata supplement. Keep the core build/check, maintained API checks, GameTests, runtime shutdown verification, example addon and data-pack validation. This is a release-scope decision, not evidence that the old SVE version is compatible with 0.6.1. Earlier release procedure entries above describe the historical gate.
