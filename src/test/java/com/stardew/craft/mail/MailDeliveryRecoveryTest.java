package com.stardew.craft.mail;

import com.stardew.craft.player.PlayerStardewData;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailDeliveryRecoveryTest {

    @Test
    void offlineTomorrowMailFlushRetainsDeliveryEffectsUntilLogin() {
        UUID playerId = UUID.randomUUID();
        PlayerStardewData data = new PlayerStardewData(playerId);
        int targetAbsoluteDay = 226;
        data.addMailForTomorrow("spring_2_1");
        data.addMailFlagForTomorrow("ccPantry", targetAbsoluteDay - 1);

        // Commit flushes persistent queues while the frozen player is offline.
        MailService.flushTomorrowMailbox(data, targetAbsoluteDay);
        PlayerStardewData restored = PlayerStardewData.fromNBT(data.toNBT(), playerId);

        assertTrue(restored.getMailbox().contains("spring_2_1"));
        assertTrue(restored.hasMailFlag("ccPantry"));
        assertEquals(List.of("spring_2_1", "ccPantry"),
                restored.getPendingMailDeliveryEffects());

        List<String> effects = new ArrayList<>();
        MailService.resumePendingDeliveryEffects(restored, effects::add);
        MailService.resumePendingDeliveryEffects(restored, effects::add);

        assertEquals(List.of("spring_2_1", "ccPantry"), effects);
        assertEquals(List.of(), restored.getPendingMailDeliveryEffects());
    }
}
