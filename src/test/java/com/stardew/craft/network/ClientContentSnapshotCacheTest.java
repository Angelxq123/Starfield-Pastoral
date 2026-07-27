package com.stardew.craft.network;

import java.lang.reflect.Modifier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongFunction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientContentSnapshotCacheTest {

    @Test
    void cacheAndEntryHavePackagePrivateApiShape() throws NoSuchMethodException {
        Class<ClientContentSnapshotCache> cacheType = ClientContentSnapshotCache.class;
        Class<ClientContentSnapshotCache.Entry> entryType = ClientContentSnapshotCache.Entry.class;

        assertTrue(Modifier.isFinal(cacheType.getModifiers()));
        assertFalse(Modifier.isPublic(cacheType.getModifiers()));
        assertFalse(Modifier.isProtected(cacheType.getModifiers()));
        assertTrue(entryType.isRecord());
        assertFalse(Modifier.isPublic(entryType.getModifiers()));
        assertFalse(Modifier.isProtected(entryType.getModifiers()));
        assertFalse(Modifier.isPublic(cacheType.getDeclaredMethod(
            "getOrBuild", Object.class, LongFunction.class).getModifiers()));
        assertFalse(Modifier.isPublic(cacheType.getDeclaredMethod(
            "rebuild", Object.class, LongFunction.class).getModifiers()));
        assertFalse(Modifier.isPublic(cacheType.getDeclaredMethod("clear", Object.class).getModifiers()));
    }

    @Test
    void sameOwnerReusesExactEntryWithoutCallingBuilder() {
        ClientContentSnapshotCache<Object, String> cache = new ClientContentSnapshotCache<>();
        Object owner = new Object();
        AtomicInteger builds = new AtomicInteger();

        ClientContentSnapshotCache.Entry<String> first = cache.getOrBuild(owner, generation -> {
            builds.incrementAndGet();
            return "snapshot-" + generation;
        });
        ClientContentSnapshotCache.Entry<String> second = cache.getOrBuild(owner, generation -> {
            builds.incrementAndGet();
            return "unexpected";
        });

        assertSame(first, second);
        assertEquals(1, builds.get());
        assertEquals(1L, first.generation());
        assertEquals("snapshot-1", first.value());
    }

    @Test
    void equalButDistinctOwnerInvalidatesAndBuilds() {
        ClientContentSnapshotCache<AlwaysEqualOwner, String> cache = new ClientContentSnapshotCache<>();
        AlwaysEqualOwner firstOwner = new AlwaysEqualOwner();
        AlwaysEqualOwner secondOwner = new AlwaysEqualOwner();

        ClientContentSnapshotCache.Entry<String> first = cache.getOrBuild(firstOwner, generation -> "first");
        ClientContentSnapshotCache.Entry<String> second = cache.getOrBuild(secondOwner, generation -> "second");

        assertFalse(firstOwner == secondOwner);
        assertTrue(firstOwner.equals(secondOwner));
        assertFalse(first == second);
        assertEquals(2L, second.generation());
        assertEquals("second", second.value());
    }

    @Test
    void rebuildAlwaysPublishesANewEntryForSameOwner() {
        ClientContentSnapshotCache<Object, String> cache = new ClientContentSnapshotCache<>();
        Object owner = new Object();
        ClientContentSnapshotCache.Entry<String> first = cache.getOrBuild(owner, generation -> "first");

        ClientContentSnapshotCache.Entry<String> rebuilt = cache.rebuild(
            owner, generation -> "rebuilt-" + generation);

        assertFalse(first == rebuilt);
        assertEquals(2L, rebuilt.generation());
        assertEquals("rebuilt-2", rebuilt.value());
        assertSame(rebuilt, cache.getOrBuild(owner, generation -> "unexpected"));
    }

    @Test
    void recursiveGetOrBuildIsRejectedWithoutPublishingAndRetryUsesSameGeneration() {
        ClientContentSnapshotCache<Object, String> cache = new ClientContentSnapshotCache<>();
        Object owner = new Object();
        AtomicInteger nestedBuilds = new AtomicInteger();

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> cache.getOrBuild(owner, generation -> {
                assertEquals(1L, generation);
                return cache.getOrBuild(owner, nestedGeneration -> {
                    nestedBuilds.incrementAndGet();
                    return "nested-" + nestedGeneration;
                }).value();
            })
        );
        ClientContentSnapshotCache.Entry<String> retried = cache.getOrBuild(
            owner, generation -> "retry-" + generation);

        assertEquals("content snapshot build already in progress", thrown.getMessage());
        assertEquals(0, nestedBuilds.get());
        assertEquals(1L, retried.generation());
        assertEquals("retry-1", retried.value());
    }

    @Test
    void outerBuilderCanCatchRejectedRecursiveRebuildWithoutNestedPublication() {
        ClientContentSnapshotCache<Object, String> cache = new ClientContentSnapshotCache<>();
        Object owner = new Object();
        AtomicInteger nestedBuilds = new AtomicInteger();

        ClientContentSnapshotCache.Entry<String> outer = cache.getOrBuild(owner, generation -> {
            IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> cache.rebuild(owner, nestedGeneration -> {
                    nestedBuilds.incrementAndGet();
                    return "nested-" + nestedGeneration;
                })
            );
            assertEquals("content snapshot build already in progress", thrown.getMessage());
            return "outer-" + generation;
        });

        assertEquals(0, nestedBuilds.get());
        assertEquals(1L, outer.generation());
        assertEquals("outer-1", outer.value());
        assertSame(outer, cache.getOrBuild(owner, generation -> "unexpected"));
    }

    @Test
    void rethrownRecursiveRebuildPublishesNothingAndRetryUsesSameGeneration() {
        ClientContentSnapshotCache<Object, String> cache = new ClientContentSnapshotCache<>();
        Object owner = new Object();
        AtomicInteger nestedBuilds = new AtomicInteger();

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> cache.getOrBuild(owner, generation -> cache.rebuild(
                owner,
                nestedGeneration -> {
                    nestedBuilds.incrementAndGet();
                    return "nested-" + nestedGeneration;
                }
            ).value())
        );
        ClientContentSnapshotCache.Entry<String> retried = cache.getOrBuild(
            owner, generation -> "retry-" + generation);

        assertEquals("content snapshot build already in progress", thrown.getMessage());
        assertEquals(0, nestedBuilds.get());
        assertEquals(1L, retried.generation());
        assertEquals("retry-1", retried.value());
    }

    @Test
    void failedBuildKeepsTheLastCompleteSnapshotAndGeneration() {
        ClientContentSnapshotCache<Object, String> cache = new ClientContentSnapshotCache<>();
        Object firstOwner = new Object();
        Object secondOwner = new Object();
        ClientContentSnapshotCache.Entry<String> first = cache.getOrBuild(firstOwner, generation -> "first");
        IllegalStateException failure = new IllegalStateException("failed");

        IllegalStateException thrown = assertThrows(
            IllegalStateException.class,
            () -> cache.getOrBuild(secondOwner, generation -> {
                assertEquals(2L, generation);
                throw failure;
            })
        );
        ClientContentSnapshotCache.Entry<String> retried = cache.getOrBuild(
            firstOwner, generation -> "unexpected-" + generation);

        assertSame(failure, thrown);
        assertSame(first, retried);
        assertEquals(1L, retried.generation());
        assertEquals("first", retried.value());
    }

    @Test
    void nullBuildKeepsTheLastCompleteSnapshot() {
        ClientContentSnapshotCache<Object, String> cache = new ClientContentSnapshotCache<>();
        Object owner = new Object();
        ClientContentSnapshotCache.Entry<String> first = cache.getOrBuild(owner, generation -> "first");

        NullPointerException thrown = assertThrows(
            NullPointerException.class,
            () -> cache.rebuild(owner, generation -> null)
        );
        ClientContentSnapshotCache.Entry<String> retried = cache.getOrBuild(
            owner, generation -> "retry-" + generation);

        assertEquals("value", thrown.getMessage());
        assertSame(first, retried);
        assertEquals(1L, retried.generation());
        assertEquals("first", retried.value());
    }

    @Test
    void successfulGenerationsIncreaseAcrossRebuildAndClear() {
        ClientContentSnapshotCache<Object, Long> cache = new ClientContentSnapshotCache<>();
        Object owner = new Object();

        ClientContentSnapshotCache.Entry<Long> first = cache.getOrBuild(owner, generation -> generation);
        cache.clear(owner);
        ClientContentSnapshotCache.Entry<Long> second = cache.getOrBuild(owner, generation -> generation);
        ClientContentSnapshotCache.Entry<Long> third = cache.rebuild(owner, generation -> generation);

        assertEquals(1L, first.generation());
        assertEquals(1L, first.value());
        assertEquals(2L, second.generation());
        assertEquals(2L, second.value());
        assertEquals(3L, third.generation());
        assertEquals(3L, third.value());
    }

    @Test
    void clearOnlyInvalidatesIdenticalOwner() {
        ClientContentSnapshotCache<AlwaysEqualOwner, String> cache = new ClientContentSnapshotCache<>();
        AlwaysEqualOwner owner = new AlwaysEqualOwner();
        AlwaysEqualOwner equalOwner = new AlwaysEqualOwner();
        ClientContentSnapshotCache.Entry<String> first = cache.getOrBuild(owner, generation -> "first");

        cache.clear(equalOwner);
        assertSame(first, cache.getOrBuild(owner, generation -> "unexpected"));

        cache.clear(owner);
        ClientContentSnapshotCache.Entry<String> second = cache.getOrBuild(owner, generation -> "second");
        assertFalse(first == second);
        assertEquals(2L, second.generation());
    }

    @Test
    void containsUsesOwnerIdentityAndRequiresPublishedEntry() {
        ClientContentSnapshotCache<AlwaysEqualOwner, String> cache = new ClientContentSnapshotCache<>();
        AlwaysEqualOwner owner = new AlwaysEqualOwner();
        AlwaysEqualOwner equalOwner = new AlwaysEqualOwner();

        assertFalse(cache.contains(owner));
        cache.getOrBuild(owner, generation -> "snapshot");

        assertTrue(cache.contains(owner));
        assertFalse(cache.contains(equalOwner));
    }

    @Test
    void rejectsNullOwnersAndBuilders() {
        ClientContentSnapshotCache<Object, String> cache = new ClientContentSnapshotCache<>();
        Object owner = new Object();

        assertEquals("owner", assertThrows(
            NullPointerException.class,
            () -> cache.getOrBuild(null, generation -> "value")
        ).getMessage());
        assertEquals("builder", assertThrows(
            NullPointerException.class,
            () -> cache.getOrBuild(owner, null)
        ).getMessage());
        assertEquals("owner", assertThrows(
            NullPointerException.class,
            () -> cache.rebuild(null, generation -> "value")
        ).getMessage());
        assertEquals("builder", assertThrows(
            NullPointerException.class,
            () -> cache.rebuild(owner, null)
        ).getMessage());
        assertEquals("owner", assertThrows(
            NullPointerException.class,
            () -> cache.clear(null)
        ).getMessage());
    }

    @Test
    void entryRequiresPositiveGenerationAndNonNullValue() {
        ClientContentSnapshotCache.Entry<String> entry = new ClientContentSnapshotCache.Entry<>(1L, "value");

        assertEquals(1L, entry.generation());
        assertEquals("value", entry.value());
        assertThrows(
            IllegalArgumentException.class,
            () -> new ClientContentSnapshotCache.Entry<>(0L, "value")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> new ClientContentSnapshotCache.Entry<>(-1L, "value")
        );
        assertEquals("value", assertThrows(
            NullPointerException.class,
            () -> new ClientContentSnapshotCache.Entry<>(1L, null)
        ).getMessage());
    }

    @Test
    void failedRefreshKeepsTheLastCompleteSnapshot() {
        ClientContentSnapshotCache<Object, String> cache = new ClientContentSnapshotCache<>();
        Object owner = new Object();
        var committed = cache.getOrBuild(owner, generation -> "committed-" + generation);

        assertThrows(IllegalStateException.class,
                () -> cache.rebuild(owner, generation -> {
                    throw new IllegalStateException("candidate failed");
                }));

        assertTrue(cache.contains(owner));
        assertSame(committed, cache.getOrBuild(owner, generation -> "unexpected"));
        var replacement = cache.rebuild(owner, generation -> "replacement-" + generation);
        assertEquals(2L, replacement.generation());
        assertEquals("replacement-2", replacement.value());
    }

    private static final class AlwaysEqualOwner {
        @Override
        public boolean equals(Object other) {
            return other instanceof AlwaysEqualOwner;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
