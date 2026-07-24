package com.stardew.craft.time.settlement;

import org.junit.jupiter.api.Test;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WeakKeyRegistryTest {

    @Test
    void entryDisappearsAfterItsOnlyStrongKeyReferenceIsReleased() {
        WeakKeyRegistry<Object, String> registry = new WeakKeyRegistry<>();
        ReferenceQueue<Object> collected = new ReferenceQueue<>();
        WeakReference<Object> key = registerTemporaryKey(registry, collected);

        long deadline = System.nanoTime() + 3_000_000_000L;
        while (collected.poll() == null && System.nanoTime() < deadline) {
            System.gc();
            registry.size();
            LockSupport.parkNanos(10_000_000L);
        }

        assertNull(key.get(), "temporary registry key was not collected within 3 seconds");
        assertEquals(0, registry.size());
    }

    private static WeakReference<Object> registerTemporaryKey(
            WeakKeyRegistry<Object, String> registry,
            ReferenceQueue<Object> collected) {
        Object key = new Object();
        assertNotNull(registry.getOrCreate(key, ignored -> "service"));
        assertEquals(1, registry.size());
        return new WeakReference<>(key, collected);
    }
}
