package com.stardew.craft.farm;

import com.stardew.craft.time.settlement.DailySettlementWorkUnit;
import com.stardew.craft.time.settlement.DailySettlementWorkUnits;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmChunkManagerTest {

    private static final ChunkPos A = new ChunkPos(1, 2);
    private static final ChunkPos B = new ChunkPos(2, 2);
    private static final ChunkPos C = new ChunkPos(3, 2);

    @Test
    void includesEveryChunkTouchedByFarmBounds() {
        Set<ChunkPos> chunks = FarmChunkManager.chunkPositionsForBounds(
            new BlockPos(-17, 40, -1),
            new BlockPos(16, 90, 32)
        );

        assertEquals(16, chunks.size());
        assertTrue(chunks.contains(new ChunkPos(-2, -1)));
        assertTrue(chunks.contains(new ChunkPos(1, 2)));
    }

    @Test
    void normalizesReversedBounds() {
        Set<ChunkPos> chunks = FarmChunkManager.chunkPositionsForBounds(
            new BlockPos(31, 90, 31),
            new BlockPos(0, 40, 0)
        );

        assertEquals(Set.of(
            new ChunkPos(0, 0), new ChunkPos(0, 1),
            new ChunkPos(1, 0), new ChunkPos(1, 1)
        ), chunks);
    }

    @Test
    void settlementFootprintsUseOnlyActuallyCoveredBoundaryChunks() {
        assertEquals(Set.of(
                new ChunkPos(0, 0), new ChunkPos(0, 1),
                new ChunkPos(1, 0), new ChunkPos(1, 1)),
                FarmChunkManager.chunkPositionsForPosition(new BlockPos(15, 64, 15), 1));
        assertEquals(Set.of(
                new ChunkPos(0, 0), new ChunkPos(0, 1),
                new ChunkPos(1, 0), new ChunkPos(1, 1)),
                FarmChunkManager.chunkPositionsForPosition(new BlockPos(14, 64, 14), 2));
        assertEquals(Set.of(
                new ChunkPos(0, 0), new ChunkPos(0, 1),
                new ChunkPos(1, 0), new ChunkPos(1, 1)),
                FarmChunkManager.chunkPositionsForPosition(new BlockPos(8, 64, 8), 8));

        assertEquals(Set.of(
                new ChunkPos(0, 0), new ChunkPos(0, 1), new ChunkPos(0, 2),
                new ChunkPos(1, 0), new ChunkPos(1, 1), new ChunkPos(1, 2),
                new ChunkPos(2, 0), new ChunkPos(2, 1), new ChunkPos(2, 2)),
                FarmChunkManager.chunkPositionsForBounds(
                        new BlockPos(15, 40, 15), new BlockPos(32, 90, 32)));
    }

    @Test
    void settlementEntryLeasesShareReferencesAndReleaseImmediately() {
        RecordingBackend backend = new RecordingBackend();
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel();
        FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> scope =
                new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level);

        TemporaryChunkLeaseTracker.Lease first = scope.lease(List.of(A, B));
        TemporaryChunkLeaseTracker.Lease second = scope.lease(List.of(B, C));

        first.close();
        assertEquals(List.of(A), backend.releases);
        second.close();
        assertEquals(List.of(A, B, C), backend.releases);
        scope.close();
        assertEquals(List.of(A, B, C), backend.releases);
    }

    @Test
    void settlementScopeLeavesPreforcedAndExternalLeaseChunksOwned() {
        RecordingBackend backend = new RecordingBackend();
        backend.forced.add(C);
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel();
        TemporaryChunkLeaseTracker.Lease external = tracker.acquire(level, List.of(A));
        FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> scope =
                new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level);

        scope.lease(List.of(A, B, C));
        scope.close();

        assertEquals(List.of(B), backend.releases);
        assertTrue(backend.forced.contains(A));
        assertTrue(backend.forced.contains(C));
        external.close();
        assertEquals(List.of(B, A), backend.releases);
    }

    @Test
    void settlementScopeClosesLeakedEntriesOnAbortAndIsIdempotent() {
        RecordingBackend backend = new RecordingBackend();
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel();
        RuntimeException failure = new RuntimeException("abort");

        RuntimeException actual = org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class, () -> {
                    try (FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> scope =
                                 new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level)) {
                        scope.lease(List.of(A));
                        scope.lease(List.of(B));
                        throw failure;
                    }
                });

        assertTrue(actual == failure);
        assertEquals(Set.of(A, B), new HashSet<>(backend.releases));
        assertEquals(2, backend.releases.size());
    }

    @Test
    void staleEntryFromClosedScopeCannotAffectNextGeneration() {
        RecordingBackend backend = new RecordingBackend();
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel();
        FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> firstScope =
                new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level);
        TemporaryChunkLeaseTracker.Lease stale = firstScope.lease(List.of(A));
        firstScope.close();

        FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> secondScope =
                new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level);
        TemporaryChunkLeaseTracker.Lease current = secondScope.lease(List.of(A));
        stale.close();

        assertEquals(1, backend.releases.size());
        current.close();
        assertEquals(2, backend.releases.size());
        assertEquals(2, backend.acquires.stream().filter(A::equals).count());
        secondScope.close();
    }

    @Test
    void runtimeReleaseFailureOnEntryCloseDoesNotFailItemAndIsRetriedByRootFallback() {
        RecordingBackend backend = new RecordingBackend();
        backend.runtimeReleaseFailures.put(A, 1);
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel();
        FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> scope =
                new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level);
        TemporaryChunkLeaseTracker.Lease lease = scope.lease(List.of(A));

        assertDoesNotThrow(lease::close);
        assertTrue(backend.forced.contains(A));
        scope.close();

        assertFalse(backend.forced.contains(A));
        assertEquals(2, backend.releaseCount(A));
    }

    @Test
    void errorReleaseFailureOnEntryCloseDoesNotFailItemAndIsRetriedByRootFallback() {
        RecordingBackend backend = new RecordingBackend();
        backend.errorReleaseFailures.put(A, 1);
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel();
        FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> scope =
                new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level);
        TemporaryChunkLeaseTracker.Lease lease = scope.lease(List.of(A));

        assertDoesNotThrow(lease::close);
        assertTrue(backend.forced.contains(A));
        scope.close();

        assertFalse(backend.forced.contains(A));
        assertEquals(2, backend.releaseCount(A));
    }

    @Test
    void entryCloseCanExplicitlyRetryAReleaseFailure() {
        RecordingBackend backend = new RecordingBackend();
        backend.runtimeReleaseFailures.put(A, 1);
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel();
        FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> scope =
                new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level);
        TemporaryChunkLeaseTracker.Lease lease = scope.lease(List.of(A));

        assertDoesNotThrow(lease::close);
        assertTrue(backend.forced.contains(A));
        assertDoesNotThrow(lease::close);
        assertFalse(backend.forced.contains(A));
        scope.close();

        assertEquals(2, backend.releaseCount(A));
    }

    @Test
    void rootCloseContinuesAfterFailuresAndRetainsEveryFailedEntryForRetry() {
        RecordingBackend backend = new RecordingBackend();
        backend.runtimeReleaseFailures.put(A, 1);
        backend.errorReleaseFailures.put(B, 1);
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel();
        FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> scope =
                new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level);
        scope.lease(List.of(A));
        scope.lease(List.of(B));

        RuntimeException failure = assertThrows(RuntimeException.class, scope::close);

        assertEquals("release failed 1,2", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("release error 2,2", failure.getSuppressed()[0].getMessage());
        assertTrue(backend.forced.containsAll(Set.of(A, B)));
        scope.close();
        assertTrue(backend.forced.isEmpty());
        assertEquals(2, backend.releaseCount(A));
        assertEquals(2, backend.releaseCount(B));
    }

    @Test
    void successfulCursorItemAdvancesOnceWhenEntryReleaseInitiallyFails() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        backend.runtimeReleaseFailures.put(A, 1);
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel();
        FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> scope =
                new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level);
        AtomicInteger mutations = new AtomicInteger();
        DailySettlementWorkUnit work = DailySettlementWorkUnits.cursor(
                "irreversible_mutation", List.of(A), ChunkPos::toString, chunk -> {
                    try (var lease = scope.lease(List.of(chunk))) {
                        mutations.incrementAndGet();
                    }
                }, () -> {});

        work.runNext();

        assertTrue(work.isComplete());
        assertEquals(1, mutations.get());
        assertTrue(backend.forced.contains(A));
        scope.close();
        assertFalse(backend.forced.contains(A));
        assertEquals(2, backend.releaseCount(A));
    }

    @Test
    void bodyFailureRemainsPrimaryWhenEntryReleaseAlsoFails() {
        RecordingBackend backend = new RecordingBackend();
        backend.runtimeReleaseFailures.put(A, 1);
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel();
        FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> scope =
                new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level);
        RuntimeException bodyFailure = new RuntimeException("body failed");

        RuntimeException actual = assertThrows(RuntimeException.class, () -> {
            try (var lease = scope.lease(List.of(A))) {
                throw bodyFailure;
            }
        });

        assertSame(bodyFailure, actual);
        assertEquals(0, actual.getSuppressed().length);
        assertTrue(backend.forced.contains(A));
        scope.close();
        assertFalse(backend.forced.contains(A));
    }

    @Test
    void rootCloseDoesNotSelfSuppressSharedFailureAndContinuesCleanup() {
        RecordingBackend backend = new RecordingBackend();
        RuntimeException shared = new RuntimeException("shared release failure");
        backend.sharedReleaseFailure = shared;
        backend.sharedFailureChunks.addAll(Set.of(A, B));
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel();
        FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> scope =
                new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level);
        scope.lease(List.of(A));
        scope.lease(List.of(B));
        scope.lease(List.of(C));

        RuntimeException actual = assertThrows(RuntimeException.class, scope::close);

        assertSame(shared, actual);
        assertEquals(0, actual.getSuppressed().length);
        assertEquals(1, backend.releaseCount(A));
        assertEquals(1, backend.releaseCount(B));
        assertEquals(1, backend.releaseCount(C));
        assertFalse(backend.forced.contains(C));
    }

    @Test
    void cursorFailureReleasesEntryBeforeRetryOrSkip() throws Exception {
        RecordingBackend backend = new RecordingBackend();
        TemporaryChunkLeaseTracker<TestLevel> tracker = new TemporaryChunkLeaseTracker<>(backend);
        TestLevel level = new TestLevel();
        FarmChunkManager.DailySettlementChunkLeaseScope<TestLevel> scope =
                new FarmChunkManager.DailySettlementChunkLeaseScope<>(tracker, level);
        AtomicInteger attempts = new AtomicInteger();
        DailySettlementWorkUnit work = DailySettlementWorkUnits.cursor(
                "lease_retry", List.of(A, B), ChunkPos::toString, chunk -> {
                    try (var lease = scope.lease(List.of(chunk))) {
                        if (chunk.equals(A) && attempts.incrementAndGet() <= 2) {
                            throw new RuntimeException("retry");
                        }
                    }
                }, () -> {});

        assertThrows(RuntimeException.class, work::runNext);
        assertFalse(backend.forced.contains(A));
        assertThrows(RuntimeException.class, work::runNext);
        assertFalse(backend.forced.contains(A));
        work.skipFailedItem();
        assertFalse(backend.forced.contains(A));
        work.runNext();
        assertFalse(backend.forced.contains(B));

        work.close();
        scope.close();
        assertEquals(2, backend.releaseCount(A));
        assertEquals(1, backend.releaseCount(B));
    }

    @Test
    void exposesPackagePrivateTargetedLeaseApiWithoutCheckedClose() throws Exception {
        Method method = FarmChunkManager.class.getDeclaredMethod(
            "acquireTemporaryChunks", ServerLevel.class, Collection.class);

        assertEquals(TemporaryChunkLeaseTracker.Lease.class, method.getReturnType());
        assertFalse(Modifier.isPublic(method.getModifiers()));
        assertEquals(0, TemporaryChunkLeaseTracker.Lease.class.getMethod("close").getExceptionTypes().length);
    }

    @Test
    void retainsLegacyTemporaryFarmChunkMethods() throws Exception {
        assertPublicVoidMethod("acquireTemporaryFarmChunks");
        assertPublicVoidMethod("releaseTemporaryFarmChunks");
        assertPublicVoidMethod("forceLoadFarmChunksForCatchUp");
        assertPublicVoidMethod("releaseTempChunks");
    }

    @Test
    void wrapperStateUsesServerLevelIdentityAndSlot() throws IOException {
        String source = managerSource();

        assertTrue(source.contains(
            "IdentityHashMap<ServerLevel, Map<Integer, TemporaryFarmLoad>> temporaryFarmLoads"));
        assertTrue(source.contains("temporaryFarmLoads.computeIfAbsent(level"));
        assertTrue(source.contains("temporaryFarmLoads.get(level)"));
    }

    @Test
    void targetedBackendSeparatesOwnershipLoadingAndRelease() throws IOException {
        String source = managerSource();
        Set<String> backendMethods = Arrays.stream(TemporaryChunkLeaseTracker.Backend.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());
        var loadMethod = Pattern.compile(
            "public void load\\(ServerLevel level, ChunkPos chunk\\)\\s*\\{(?<body>.*?)\\n\\s*}",
            Pattern.DOTALL).matcher(source);

        assertEquals(Set.of("acquire", "load", "release"), backendMethods);
        assertTrue(source.contains("if (level.getForcedChunks().contains(chunkKey))"));
        assertTrue(source.contains("return false;"));
        assertTrue(source.contains("return level.setChunkForced(chunk.x, chunk.z, true)"));
        assertTrue(loadMethod.find());
        assertTrue(loadMethod.group("body").contains("PerformanceCounter.FARM_SYNC_CHUNK_LOADS"));
        assertTrue(loadMethod.group("body").contains("PerformanceTiming.FARM_SYNC_CHUNK_LOAD"));
        assertTrue(loadMethod.group("body").contains("level.getChunk(chunk.x, chunk.z)"));
        assertTrue(source.contains("level.setChunkForced(chunk.x, chunk.z, false)"));
        assertFalse(source.contains("catch (RuntimeException exception)"));
    }

    @Test
    void serverStoppingOnlyClosesWrappersAndTrackerForProvidedLevel() throws IOException {
        String source = managerSource();

        assertTrue(source.contains("temporaryFarmLoads.remove(level)"));
        assertTrue(source.contains("temporaryChunkLeases.closeAll(level)"));
        assertFalse(source.contains("temporaryFarmLoads.clear()"));
        assertTrue(source.contains("occupancy.clear()"));
    }

    private static void assertPublicVoidMethod(String name) throws Exception {
        Method method = FarmChunkManager.class.getDeclaredMethod(name, ServerLevel.class, int.class);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    private static String managerSource() throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir"));
        return Files.readString(projectDir.resolve(
            "src/main/java/com/stardew/craft/farm/FarmChunkManager.java"));
    }

    private static final class TestLevel {
    }

    private static final class RecordingBackend
            implements TemporaryChunkLeaseTracker.Backend<TestLevel> {
        private final List<ChunkPos> acquires = new ArrayList<>();
        private final List<ChunkPos> releases = new ArrayList<>();
        private final Set<ChunkPos> forced = new HashSet<>();
        private final Map<ChunkPos, Integer> runtimeReleaseFailures = new java.util.HashMap<>();
        private final Map<ChunkPos, Integer> errorReleaseFailures = new java.util.HashMap<>();
        private final Set<ChunkPos> sharedFailureChunks = new HashSet<>();
        private RuntimeException sharedReleaseFailure;

        @Override
        public boolean acquire(TestLevel level, ChunkPos chunk) {
            acquires.add(chunk);
            return forced.add(chunk);
        }

        @Override
        public void load(TestLevel level, ChunkPos chunk) {
        }

        @Override
        public void release(TestLevel level, ChunkPos chunk) {
            releases.add(chunk);
            if (consumeFailure(runtimeReleaseFailures, chunk)) {
                throw new RuntimeException("release failed " + chunk.x + "," + chunk.z);
            }
            if (consumeFailure(errorReleaseFailures, chunk)) {
                throw new AssertionError("release error " + chunk.x + "," + chunk.z);
            }
            if (sharedReleaseFailure != null && sharedFailureChunks.contains(chunk)) {
                throw sharedReleaseFailure;
            }
            forced.remove(chunk);
        }

        private long releaseCount(ChunkPos chunk) {
            return releases.stream().filter(chunk::equals).count();
        }

        private static boolean consumeFailure(Map<ChunkPos, Integer> failures, ChunkPos chunk) {
            int remaining = failures.getOrDefault(chunk, 0);
            if (remaining <= 0) {
                return false;
            }
            failures.put(chunk, remaining - 1);
            return true;
        }
    }
}
