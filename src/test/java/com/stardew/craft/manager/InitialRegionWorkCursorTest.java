package com.stardew.craft.manager;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InitialRegionWorkCursorTest {

    @Test
    void issuesEachIntersectingChunkOnceBeforeColumnWork() {
        InitialRegionWorkCursor cursor = new InitialRegionWorkCursor(15, 17, 31, 33);
        List<ChunkPos> chunks = new ArrayList<>();

        while (cursor.hasChunkRequest()) {
            chunks.add(cursor.pollChunkRequest());
        }

        assertEquals(
                List.of(
                        new ChunkPos(0, 1), new ChunkPos(1, 1),
                        new ChunkPos(0, 2), new ChunkPos(1, 2)),
                chunks);
        assertEquals(new InitialRegionWorkCursor.Column(15, 31), cursor.currentColumn());
    }

    @Test
    void visitsEveryColumnInStableOrder() {
        InitialRegionWorkCursor cursor = new InitialRegionWorkCursor(2, 3, 8, 9);
        while (cursor.hasChunkRequest()) {
            cursor.pollChunkRequest();
        }
        List<InitialRegionWorkCursor.Column> columns = new ArrayList<>();

        while (cursor.hasColumn()) {
            columns.add(cursor.currentColumn());
            cursor.advanceColumn();
        }

        assertEquals(
                List.of(
                        new InitialRegionWorkCursor.Column(2, 8),
                        new InitialRegionWorkCursor.Column(2, 9),
                        new InitialRegionWorkCursor.Column(3, 8),
                        new InitialRegionWorkCursor.Column(3, 9)),
                columns);
        assertFalse(cursor.hasColumn());
    }
}
