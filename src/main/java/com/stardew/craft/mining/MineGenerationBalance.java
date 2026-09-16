package com.stardew.craft.mining;

/** Population limits retained for the native layout runtime. */
public final class MineGenerationBalance {
    private static final int REGULAR_MONSTER_MIN = 2;
    private static final int REGULAR_MONSTER_MAX = 8;
    private static final int SKULL_MONSTER_MIN = 3;
    private static final int SKULL_MONSTER_MAX = 10;
    private static final int NEAR_ENTRANCE_MIN_DISTANCE = 8;
    private static final int NEAR_ENTRANCE_MAX_DISTANCE = 28;
    private static final int NEAR_ENTRANCE_MAX_VERTICAL_DISTANCE = 4;

    private MineGenerationBalance() {}

    static int normalizeMonsterCount(
            int floor,
            int rolledCount,
            boolean monsterMusk
    ) {
        if (floor <= 1 || (floor < 121 && floor % 10 == 0)) {
            return 0;
        }
        int minimum = floor > 120 ? SKULL_MONSTER_MIN : REGULAR_MONSTER_MIN;
        int maximum = floor > 120 ? SKULL_MONSTER_MAX : REGULAR_MONSTER_MAX;
        int population = Math.clamp(rolledCount, minimum, maximum);
        return monsterMusk
                ? Math.min(maximum * 2, population * 2)
                : population;
    }

    static int expectedReusedFloorPopulation(int floor, int persistedCount) {
        return normalizeMonsterCount(floor, Math.max(0, persistedCount), false);
    }

    static int nearEntranceSpawnTarget(int totalCount) {
        return Math.max(0, totalCount);
    }

    static boolean isNearEntranceSpawnCandidate(
            int x,
            int z,
            int centerX,
            int centerZ
    ) {
        long dx = (long) x - centerX;
        long dz = (long) z - centerZ;
        long distanceSquared = dx * dx + dz * dz;
        return distanceSquared >= (long) NEAR_ENTRANCE_MIN_DISTANCE
                * NEAR_ENTRANCE_MIN_DISTANCE
                && distanceSquared <= (long) NEAR_ENTRANCE_MAX_DISTANCE
                * NEAR_ENTRANCE_MAX_DISTANCE;
    }

    static boolean isNearEntranceSpawnCandidate(
            int x,
            int y,
            int z,
            int centerX,
            int centerY,
            int centerZ
    ) {
        return Math.abs(y - centerY) <= NEAR_ENTRANCE_MAX_VERTICAL_DISTANCE
                && isNearEntranceSpawnCandidate(x, z, centerX, centerZ);
    }

}
