# Login Shared Content Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove repeated shared-content and global JEI catalog construction from every player login while preserving the existing four-packet protocol, packet order, dynamic festival evaluation, and player-specific geode filtering.

**Architecture:** Add one server-identity-scoped cache whose immutable snapshot contains the registry payload, its encoded size, the mail index, and the globally shared JEI shops/fish-pond catalog. The cache rebuilds after `/reload`, initializes lazily on the first login after startup, clears on server stop, and never publishes a partially built or stale snapshot. Festival availability remains live per sync event, while JEI geodes remain player-specific.

**Tech Stack:** Java 21, NeoForge 21.1, immutable custom payload records, JUnit 5, Gradle 9.2.

---

### Task 1: Add The Server-Scoped Snapshot Cache

**Files:**
- Create: `src/main/java/com/stardew/craft/network/ClientContentSnapshotCache.java`
- Create: `src/test/java/com/stardew/craft/network/ClientContentSnapshotCacheTest.java`

- [ ] **Step 1: Write failing cache lifecycle tests**

Create tests covering:

```java
@Test
void reusesSnapshotForTheSameServerIdentity() {
    Object server = new Object();
    AtomicInteger builds = new AtomicInteger();
    ClientContentSnapshotCache<Object, Integer> cache = new ClientContentSnapshotCache<>();

    var first = cache.getOrBuild(server, generation -> builds.incrementAndGet());
    var second = cache.getOrBuild(server, generation -> builds.incrementAndGet());

    assertSame(first, second);
    assertEquals(1, builds.get());
    assertEquals(1L, first.generation());
    assertEquals(1, first.value());
}

@Test
void forcedRebuildReplacesThePublishedSnapshot() {
    Object server = new Object();
    ClientContentSnapshotCache<Object, Integer> cache = new ClientContentSnapshotCache<>();
    var first = cache.getOrBuild(server, generation -> 1);
    var second = cache.rebuild(server, generation -> 2);

    assertNotSame(first, second);
    assertEquals(2L, second.generation());
    assertEquals(2, second.value());
}

@Test
void failedRebuildDoesNotExposeTheOldSnapshot() {
    Object server = new Object();
    ClientContentSnapshotCache<Object, Integer> cache = new ClientContentSnapshotCache<>();
    cache.getOrBuild(server, generation -> 1);

    assertThrows(IllegalStateException.class,
            () -> cache.rebuild(server, generation -> { throw new IllegalStateException("boom"); }));
    var recovered = cache.getOrBuild(server, generation -> 2);

    assertEquals(2, recovered.value());
}
```

Also cover owner identity changes, matching/non-matching `clear`, null owner rejection, null builder result rejection, and monotonically increasing successful generations.

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat test --tests "com.stardew.craft.network.ClientContentSnapshotCacheTest" --no-daemon
```

Expected: compilation fails because `ClientContentSnapshotCache` does not exist.

- [ ] **Step 3: Implement the minimal identity-scoped cache**

Create a package-private final generic cache:

```java
final class ClientContentSnapshotCache<K, V> {
    private K owner;
    private Entry<V> snapshot;
    private long generation;

    Entry<V> getOrBuild(K owner, LongFunction<V> builder) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(builder, "builder");
        if (this.owner == owner && snapshot != null) return snapshot;
        return build(owner, builder);
    }

    Entry<V> rebuild(K owner, LongFunction<V> builder) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(builder, "builder");
        invalidate();
        return build(owner, builder);
    }

    void clear(K owner) {
        if (this.owner == owner) invalidate();
    }

    private Entry<V> build(K owner, LongFunction<V> builder) {
        invalidate();
        long nextGeneration = Math.addExact(generation, 1L);
        V value = Objects.requireNonNull(builder.apply(nextGeneration), "snapshot");
        Entry<V> built = new Entry<>(nextGeneration, value);
        this.owner = owner;
        this.snapshot = built;
        this.generation = nextGeneration;
        return built;
    }

    private void invalidate() {
        owner = null;
        snapshot = null;
    }

    record Entry<V>(long generation, V value) {
        Entry {
            if (generation <= 0L) throw new IllegalArgumentException("generation must be positive");
            Objects.requireNonNull(value, "value");
        }
    }
}
```

The cache is server-thread-only. It deliberately uses identity (`==`) rather than `equals` for the owner key. A failed build leaves no stale snapshot published and a later login retries.

- [ ] **Step 4: Run cache tests and verify GREEN**

Run the focused command from Step 2. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the cache core**

```powershell
git add src/main/java/com/stardew/craft/network/ClientContentSnapshotCache.java src/test/java/com/stardew/craft/network/ClientContentSnapshotCacheTest.java
git commit -m "perf: add server-scoped content snapshot cache"
```

---

### Task 2: Split Shared And Player-Specific JEI Catalog Work

**Files:**
- Modify: `src/main/java/com/stardew/craft/network/JeiCatalogSyncPayload.java`
- Modify: `src/test/java/com/stardew/craft/network/JeiCatalogSyncPayloadTest.java`

- [ ] **Step 1: Write failing shared-catalog composition tests**

Add tests proving that `SharedCatalog` defensively retains shops and fish ponds, and that composing player geodes produces a full payload without mutating or copying from another player's geodes:

```java
@Test
void composesPlayerGeodesWithReusableSharedCatalog() {
    var shop = testShop();
    var pond = new JeiCatalogSyncPayload.FishPondEntry(
            new ItemStack(Items.COD), new ItemStack(Items.EMERALD),
            3, 0.5D, 0.1D, 0.2D, 1, 2, false);
    var shared = new JeiCatalogSyncPayload.SharedCatalog(List.of(shop), List.of(pond));
    var geode = new JeiCatalogSyncPayload.GeodeEntry(
            new ItemStack(Items.AMETHYST_CLUSTER), new ItemStack(Items.DIAMOND));

    JeiCatalogSyncPayload payload = JeiCatalogSyncPayload.fromShared(shared, List.of(geode));

    assertEquals(1, payload.shops().size());
    assertEquals(1, payload.geodes().size());
    assertEquals(1, payload.fishPonds().size());
}
```

Also assert that `SharedCatalog` rejects more than `MAX_ENTRIES` shops or fish ponds and that returned `ItemStack` values remain defensive.

- [ ] **Step 2: Run focused JEI tests and verify RED**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.network.JeiCatalogSyncPayloadTest" --no-daemon
```

Expected: compilation fails because `SharedCatalog` and `fromShared` do not exist.

- [ ] **Step 3: Add the shared JEI boundary**

In `JeiCatalogSyncPayload`:

```java
static SharedCatalog currentSharedCatalog() {
    return new SharedCatalog(buildShops(), buildFishPonds());
}

static JeiCatalogSyncPayload current(ServerPlayer player, SharedCatalog shared) {
    Objects.requireNonNull(player, "player");
    return fromShared(shared, buildCustomGeodes(player));
}

static JeiCatalogSyncPayload fromShared(SharedCatalog shared, List<GeodeEntry> geodes) {
    Objects.requireNonNull(shared, "shared");
    return new JeiCatalogSyncPayload(shared.shops(), geodes, shared.fishPonds());
}

public static JeiCatalogSyncPayload current(ServerPlayer player) {
    return current(player, currentSharedCatalog());
}

record SharedCatalog(List<ShopEntry> shops, List<FishPondEntry> fishPonds) {
    SharedCatalog {
        shops = shops == null ? List.of() : List.copyOf(shops);
        fishPonds = fishPonds == null ? List.of() : List.copyOf(fishPonds);
        if (shops.size() > MAX_ENTRIES || fishPonds.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Shared JEI catalog exceeds " + MAX_ENTRIES + " entries");
        }
    }
}
```

Keep `buildCustomGeodes(ServerPlayer)` unchanged and private. Keep the existing public `current(ServerPlayer)` behavior for callers outside the optimized sync service.

- [ ] **Step 4: Run JEI and packet codec regression tests**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.network.JeiCatalogSyncPayloadTest" --tests "com.stardew.craft.integration.jei.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the JEI split**

```powershell
git add src/main/java/com/stardew/craft/network/JeiCatalogSyncPayload.java src/test/java/com/stardew/craft/network/JeiCatalogSyncPayloadTest.java
git commit -m "perf: separate shared JEI catalog construction"
```

---

### Task 3: Reuse Shared Snapshots During Login

**Files:**
- Modify: `src/main/java/com/stardew/craft/network/ClientContentSyncService.java`
- Modify: `src/test/java/com/stardew/craft/network/ClientContentSyncServiceContractTest.java`
- Modify: `src/test/java/com/stardew/craft/network/ClientContentSnapshotCacheTest.java`

- [ ] **Step 1: Add failing service lifecycle and order contracts**

Extend the service contract test to require:

- a static `ClientContentSnapshotCache<MinecraftServer, SharedSnapshot>`;
- `event.getPlayer() == null` selecting forced `rebuild` for `/reload`;
- login selecting `getOrBuild`;
- `FestivalAvailabilitySyncPayload.current()` remaining outside the cached snapshot;
- per-player JEI construction using `JeiCatalogSyncPayload.current(player, shared.jeiCatalog())`;
- unchanged send order `registry -> mail -> festival -> JEI`;
- matching-server cleanup from `ServerStoppedEvent`.

Add a cache test proving an empty-recipient forced rebuild still creates and publishes a snapshot. Run the focused tests and confirm they fail against the current service.

- [ ] **Step 2: Build and publish shared snapshots**

Update `ClientContentSyncService` around this structure:

```java
private static final ClientContentSnapshotCache<MinecraftServer, SharedSnapshot> SHARED_CONTENT =
        new ClientContentSnapshotCache<>();

@SubscribeEvent
public static void onDatapackSync(OnDatapackSyncEvent event) {
    MinecraftServer server = event.getPlayerList().getServer();
    ClientContentSnapshotCache.Entry<SharedSnapshot> cached = event.getPlayer() == null
            ? SHARED_CONTENT.rebuild(server, ClientContentSyncService::buildSharedSnapshot)
            : SHARED_CONTENT.getOrBuild(server, ClientContentSyncService::buildSharedSnapshot);
    SharedSnapshot shared = cached.value();
    FestivalAvailabilitySyncPayload festivalSnapshot = FestivalAvailabilitySyncPayload.current();
    List<ServerPlayer> recipients = event.getRelevantPlayers().toList();

    ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_RECIPIENTS, recipients.size());
    for (ServerPlayer player : recipients) {
        PacketDistributor.sendToPlayer(player, shared.registry());
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);
        ServerPerformanceRecorder.increment(
                PerformanceCounter.CONTENT_REGISTRY_BYTES, shared.registryEncodedBytes());
        PacketDistributor.sendToPlayer(player, shared.mail());
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);
        PacketDistributor.sendToPlayer(player, festivalSnapshot);
        ServerPerformanceRecorder.increment(PerformanceCounter.CONTENT_SYNC_PACKETS, 1L);
        JeiCatalogSyncPayload jeiSnapshot = ServerPerformanceRecorder.measure(
                PerformanceTiming.JEI_CATALOG_BUILD,
                () -> JeiCatalogSyncPayload.current(player, shared.jeiCatalog()));
        // Keep existing entry counter and JEI send here.
    }
}

private static SharedSnapshot buildSharedSnapshot(long generation) {
    return ServerPerformanceRecorder.measure(PerformanceTiming.CONTENT_SNAPSHOT_BUILD, () -> {
        DataRegistrySyncPayload registry = DataRegistrySyncPayload.current();
        return new SharedSnapshot(
                registry,
                registry.estimatedEncodedBytes(),
                MailIndexSyncPayload.current(),
                JeiCatalogSyncPayload.currentSharedCatalog());
    });
}

private record SharedSnapshot(
        DataRegistrySyncPayload registry,
        int registryEncodedBytes,
        MailIndexSyncPayload mail,
        JeiCatalogSyncPayload.SharedCatalog jeiCatalog
) {
    private SharedSnapshot {
        Objects.requireNonNull(registry, "registry");
        if (registryEncodedBytes < 0) throw new IllegalArgumentException("negative encoded size");
        Objects.requireNonNull(mail, "mail");
        Objects.requireNonNull(jeiCatalog, "jeiCatalog");
    }
}

@SubscribeEvent
public static void onServerStopped(ServerStoppedEvent event) {
    SHARED_CONTENT.clear(event.getServer());
}
```

Do not cache `FestivalAvailabilitySyncPayload`: its conditions depend on live time and world state. Rebuild shared data before collecting recipients so `/reload` with zero online players still warms the cache. Do not catch snapshot-build exceptions; failed rebuilds must remain invalid and surface instead of sending stale content.

- [ ] **Step 3: Run focused cache, sync, JEI, and performance tests**

```powershell
.\gradlew.bat test --tests "com.stardew.craft.network.ClientContentSnapshotCacheTest" --tests "com.stardew.craft.network.ClientContentSyncServiceContractTest" --tests "com.stardew.craft.network.JeiCatalogSyncPayloadTest" --tests "com.stardew.craft.server.performance.*" --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Compile production code and inspect scope**

```powershell
.\gradlew.bat classes --no-daemon
git diff --check
git status --short
```

Expected: production compilation succeeds, no whitespace errors, and only the six planned source/test files are changed.

- [ ] **Step 5: Commit login reuse integration**

```powershell
git add src/main/java/com/stardew/craft/network/ClientContentSyncService.java src/test/java/com/stardew/craft/network/ClientContentSyncServiceContractTest.java src/test/java/com/stardew/craft/network/ClientContentSnapshotCacheTest.java
git commit -m "perf: reuse shared content snapshots on login"
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

- [ ] **Step 3: Verify branch integrity**

```powershell
git diff --check
git status --short --branch
git log --oneline --decorate -8
```

Expected: clean worktree and only focused Phase 2A commits above `f46529b8`.

---

## Deferred Work

- Wire-level content revision negotiation and protocol changes.
- Caching dynamic festival availability across events.
- Deferring or coalescing player-specific login snapshots.
- Replacing full-farm offline catch-up, which can synchronously load hundreds of chunks.
- Historical mail replay watermarks and login NBT coalescing.

These remain separate phases because they change client readiness, progression timing, or chunk ownership semantics.
