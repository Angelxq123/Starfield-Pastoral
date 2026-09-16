package com.stardew.craft.npc.data;

import com.google.gson.JsonParser;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpcSocialRulesTest {
    @Test void builtInSocialEligibilityAndTabBehaviorMatchCharactersData() throws Exception {
        try (var input = getClass().getResourceAsStream("/social-eligibility.json")) {
            assertNotNull(input);
            var characters = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("characters");
            for (var row : characters) {
                var data = row.getAsJsonObject();
                String id = data.get("npcId").getAsString();
                boolean eligible = data.get("canSocialize").getAsBoolean();
                String tab = data.get("socialTab").getAsString();
                assertEquals(eligible, NpcSocialRules.canSocialize(id), id);
                assertEquals(eligible && tab.equals("AlwaysShown"), NpcSocialRules.shouldCreateFriendshipForSocialPage(id), id);
                for (boolean met : List.of(false, true)) {
                    var friendship = met ? new NpcFriendshipDataManager.FriendshipState() : null;
                    boolean visible = eligible && !id.equals("sandy") && !tab.equals("HiddenAlways")
                            && (!tab.equals("HiddenUntilMet") || met);
                    assertEquals(visible, NpcSocialRules.shouldShowOnSocialPage(id, profile(id), friendship, null), id + " met=" + met);
                }
            }
        }
    }

    @Test void implementationAndOldFriendshipDoNotOptActorsIntoSocialFeatures() {
        for (String id : List.of("club_seller", "joja_cashier", "unknown_actor", "addon:sam", "mister_qi", "bouncer")) {
            assertFalse(NpcSocialRules.canSocialize(id), id);
            assertFalse(NpcSocialRules.canReceiveGifts(id), id);
            assertFalse(NpcSocialRules.shouldShowOnSocialPage(id, profile(id),
                    new NpcFriendshipDataManager.FriendshipState(), null), id);
            assertFalse(NpcSocialRules.shouldCreateFriendshipForSocialPage(id), id);
            assertFalse(NpcSocialRules.isIntroductionsNpc(id, profile(id)), id);
        }
    }

    @Test void canonicalCoreIdsKeepTheirEligibilityAndDelayedVillagersAreNotIntroductions() {
        assertTrue(NpcSocialRules.canSocialize("stardewcraft:sam"));
        assertFalse(NpcSocialRules.canSocialize("stardewcraft:mister_qi"));
        for (String id : List.of("kent", "leo")) assertFalse(NpcSocialRules.isIntroductionsNpc(id, profile(id)), id);
    }

    private static NpcCapabilityProfile profile(String id) {
        return new NpcCapabilityProfile(id, true, true, "idle_walk", 0, 0, 0, 0, 0, false);
    }
}
