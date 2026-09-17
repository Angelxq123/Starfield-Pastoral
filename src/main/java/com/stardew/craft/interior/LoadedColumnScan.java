package com.stardew.craft.interior;

import net.minecraft.core.BlockPos;
import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** One block read per step; an unavailable column rotates without being lost. */
final class LoadedColumnScan {
    private static final class Column {
        final int x, z;
        int y;
        Column(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }
    private final ArrayDeque<Column> columns = new ArrayDeque<>();
    private final int minY;

    LoadedColumnScan(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        this.minY = minY;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) columns.addLast(new Column(x, maxY, z));
    }

    boolean isComplete() { return columns.isEmpty(); }

    void step(Predicate<BlockPos> loaded, Consumer<BlockPos> visit) {
        Column column = columns.peekFirst();
        if (column == null) return;
        BlockPos pos = new BlockPos(column.x, column.y, column.z);
        if (!loaded.test(pos)) {
            columns.addLast(columns.removeFirst());
            return;
        }
        visit.accept(pos);
        if (--column.y < minY) columns.removeFirst();
    }
}
