package com.stardew.craft.farm;

/** Compact resumable cursor for farm debris scanning and generation. */
final class FarmDebrisCursor {
    enum Phase {
        SCAN,
        SPREAD,
        RANDOM,
        SPRING_RANDOM,
        SPRING_WEEDS,
        COMPLETE
    }

    record Step(Phase phase, int x, int z, int attempt) {
        String identity() {
            return phase == Phase.SCAN
                    ? "scan:" + x + ":" + z
                    : phase.name().toLowerCase(java.util.Locale.ROOT) + ":" + attempt;
        }
    }

    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int spreadAttempts;
    private final int randomAttempts;
    private final int springAttempts;
    private final int minChunkX;
    private final int maxChunkX;
    private final int minChunkZ;
    private final int maxChunkZ;
    private Phase phase;
    private int chunkX;
    private int chunkZ;
    private int x;
    private int z;
    private int attempt;

    FarmDebrisCursor(
            int minX,
            int maxX,
            int minZ,
            int maxZ,
            int spreadAttempts,
            int randomAttempts,
            int springAttempts) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.spreadAttempts = requireNonNegative(spreadAttempts, "spreadAttempts");
        this.randomAttempts = requireNonNegative(randomAttempts, "randomAttempts");
        this.springAttempts = requireNonNegative(springAttempts, "springAttempts");
        minChunkX = minX >> 4;
        maxChunkX = maxX >> 4;
        minChunkZ = minZ >> 4;
        maxChunkZ = maxZ >> 4;
        chunkX = minChunkX;
        chunkZ = minChunkZ;
        resetScanCoordinates();
        phase = minX <= maxX && minZ <= maxZ ? Phase.SCAN : Phase.COMPLETE;
        normalizePhase();
    }

    boolean hasCurrent() {
        return !isComplete();
    }

    boolean isComplete() {
        return phase == Phase.COMPLETE;
    }

    Step current() {
        if (isComplete()) {
            throw new IllegalStateException("Farm debris cursor is complete");
        }
        return new Step(phase, x, z, attempt);
    }

    void advance() {
        if (isComplete()) {
            throw new IllegalStateException("Farm debris cursor is complete");
        }
        if (phase == Phase.SCAN) {
            if (chunkZ < maxChunkZ) {
                chunkZ++;
                resetScanCoordinates();
            } else if (chunkX < maxChunkX) {
                chunkX++;
                chunkZ = minChunkZ;
                resetScanCoordinates();
            } else {
                phase = Phase.SPREAD;
                attempt = 0;
            }
        } else {
            attempt++;
        }
        normalizePhase();
    }

    private void resetScanCoordinates() {
        x = Math.max(minX, chunkX << 4);
        z = Math.max(minZ, chunkZ << 4);
    }

    void skipSpread() {
        if (phase != Phase.SPREAD) {
            throw new IllegalStateException("Current phase is not spread");
        }
        phase = Phase.RANDOM;
        attempt = 0;
        normalizePhase();
    }

    private void normalizePhase() {
        while (phase != Phase.COMPLETE && phase != Phase.SCAN
                && attempt >= attemptsFor(phase)) {
            phase = switch (phase) {
                case SPREAD -> Phase.RANDOM;
                case RANDOM -> Phase.SPRING_RANDOM;
                case SPRING_RANDOM -> Phase.SPRING_WEEDS;
                case SPRING_WEEDS -> Phase.COMPLETE;
                default -> throw new IllegalStateException("Unexpected phase " + phase);
            };
            attempt = 0;
        }
    }

    private int attemptsFor(Phase current) {
        return switch (current) {
            case SPREAD -> spreadAttempts;
            case RANDOM -> randomAttempts;
            case SPRING_RANDOM, SPRING_WEEDS -> springAttempts;
            default -> 0;
        };
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }
}
