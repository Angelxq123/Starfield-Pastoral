package com.stardew.craft.time.settlement;

import org.junit.jupiter.api.Test;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class WeakKeyRegistryTest {

    @Test
    void entryDisappearsAfterItsOnlyStrongKeyReferenceIsReleased() {
        WeakKeyRegistry<Object, ServiceGraph> registry = new WeakKeyRegistry<>();
        ReferenceQueue<Object> collected = new ReferenceQueue<>();
        Registration registration = registerTemporaryKey(registry, collected);

        long deadline = System.nanoTime() + 3_000_000_000L;
        while (collected.poll() == null && System.nanoTime() < deadline) {
            System.gc();
            registry.size();
            LockSupport.parkNanos(10_000_000L);
        }

        assertNull(registration.key().get(),
                "temporary registry key was not collected within 3 seconds");
        assertNull(registration.service().server().get());
        assertEquals(0, registry.size());
    }

    private static Registration registerTemporaryKey(
            WeakKeyRegistry<Object, ServiceGraph> registry,
            ReferenceQueue<Object> collected) {
        Object key = new Object();
        ServiceGraph service = registry.getOrCreate(
                key, server -> new ServiceGraph(new WeakReference<>(server)));
        assertNotNull(service);
        assertSame(key, service.server().get());
        assertEquals(1, registry.size());
        return new Registration(new WeakReference<>(key, collected), service);
    }

    private record Registration(
            WeakReference<Object> key, ServiceGraph service) {
    }

    private record ServiceGraph(WeakReference<Object> server) {
    }
}
