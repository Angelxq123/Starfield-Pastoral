package com.stardew.craft.npc.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcDialogueEventDataTest {
    @Test
    void playerSnapshotSupportsOnePlayerAtATimeDailyAdvancement() {
        NpcDialogueEventData data = new NpcDialogueEventData();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        data.activate(first, "first_topic", 1);
        data.activate(second, "second_topic", 1);

        assertEquals(List.of(first, second), data.playerIdsSnapshot());
        data.onNewDay(first);

        assertEquals(List.of("first_topic", "first_topic_memory_oneday"),
                data.activeKeys(first, "npc"));
        assertEquals(List.of("second_topic"), data.activeKeys(second, "npc"));
    }
}
