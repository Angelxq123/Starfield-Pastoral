package com.stardew.craft.farm;

import java.util.ArrayDeque;

final class DeferredLeaseCleanup {
    private final ArrayDeque<TemporaryChunkLeaseTracker.Lease> pending =
            new ArrayDeque<>();

    void run(TemporaryChunkLeaseTracker.Lease lease, Runnable operation) {
        Throwable operationFailure = null;
        try {
            operation.run();
        } catch (RuntimeException | Error failure) {
            operationFailure = failure;
        }
        try {
            lease.close();
        } catch (RuntimeException | Error closeFailure) {
            pending.addLast(lease);
            if (operationFailure != null && operationFailure != closeFailure) {
                operationFailure.addSuppressed(closeFailure);
            }
        }
        rethrow(operationFailure);
    }

    boolean retryOne() {
        TemporaryChunkLeaseTracker.Lease lease = pending.pollFirst();
        if (lease == null) {
            return true;
        }
        try {
            lease.close();
            return true;
        } catch (RuntimeException | Error failure) {
            pending.addLast(lease);
            return false;
        }
    }

    int pendingCount() {
        return pending.size();
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }
}
