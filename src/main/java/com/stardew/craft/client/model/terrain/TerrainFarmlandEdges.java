package com.stardew.craft.client.model.terrain;

/** Discrete authored edge bands, independent of rendering and saved block state. */
public final class TerrainFarmlandEdges {
    private static final String[] EDGE = {"3333333333333333", "3223322233222332", "1102211122111221", "0001100011000110"};
    private static final String[] CORNER = {"3320", "3210", "2100", "0000"};
    private static final int[][] WEIGHTS = new int[256][];
    static {
        for (int mask = 0; mask < 256; mask++) {
            int[] weights = new int[256];
            int canonical = GrassConnectionMask.canonical(mask);
            for (int turn = 0; turn < 4; turn++) {
                if ((canonical & (1 << turn)) != 0) stamp(weights, EDGE, turn, false);
                if ((canonical & (16 << turn)) != 0) stamp(weights, CORNER, turn, true);
            }
            WEIGHTS[mask] = weights;
        }
    }
    private TerrainFarmlandEdges() {}
    private static void stamp(int[] weights, String[] rows, int turn, boolean corner) {
        for (int y = 0; y < rows.length; y++) for (int x = 0; x < rows[y].length(); x++) {
            int px = corner ? 15 - x : x, py = y;
            for (int i = 0; i < turn; i++) { int previous = px; px = 15 - py; py = previous; }
            weights[py * 16 + px] = Math.max(weights[py * 16 + px], rows[y].charAt(x) - '0');
        }
    }
    public static int weight(int mask, int x, int z) { return WEIGHTS[mask & 255][z * 16 + x]; }
    public static int blendIndex(boolean wet, int soilMask, int moistureMask, int x, int z) {
        return (wet ? 0 : weight(moistureMask, x, z)) * 4 + weight(soilMask, x, z);
    }
}
