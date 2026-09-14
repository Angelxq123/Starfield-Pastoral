package com.stardew.craft.client.model.terrain;

/** Clockwise cardinal bits N/E/S/W, followed by diagonal bits NE/SE/SW/NW. */
public final class GrassConnectionMask {
    private GrassConnectionMask() {}

    /** Rank: dirt 0, ordinary grass 1, dark grass 2. Low/high bytes are grass/dark. */
    public static int connections(int targetRank, int grassNeighbors, int darkNeighbors) {
        if (targetRank < 0) return 0;
        int grass = targetRank < 1 ? canonical(grassNeighbors) : 0;
        int dark = targetRank < 2 ? canonical(darkNeighbors) : 0;
        return grass | (dark << 8);
    }

    public static int canonical(int neighbors) {
        int result = neighbors & 255;
        for (int corner = 0; corner < 4; corner++) {
            int adjacentEdges = (1 << corner) | (1 << ((corner + 1) % 4));
            // An edge already covers its corner; don't draw a second overlapping corner tuft.
            if ((neighbors & adjacentEdges) != 0) {
                result &= ~(16 << corner);
            }
        }
        return result;
    }
}
