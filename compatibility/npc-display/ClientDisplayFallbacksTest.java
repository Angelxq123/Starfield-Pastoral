package com.stardew.craft.client;

import com.stardew.craft.api.v1.npc.StardewNpcDisplay;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClientDisplayFallbacksTest {
    private static StardewNpcDisplay display(String namespace, String name) {
        return new StardewNpcDisplay(ResourceLocation.fromNamespaceAndPath(namespace, name), "npc.name",
                ResourceLocation.fromNamespaceAndPath(namespace, "textures/portraits/" + name + ".png"), 128, 320,
                ResourceLocation.fromNamespaceAndPath(namespace, "textures/mugshots/" + name + ".png"), 16, 24,
                "npc.relationship", false);
    }

    @Test void nativeMugshotRemainsPreferred() {
        var npc = display("stardewcraft", "sam");
        assertEquals(npc.mugshotTexture(), ClientDisplayFallbacks.socialPortrait(npc, resource -> true));
    }

    @Test void missingMugshotUsesOnlyTheSameNpcsPortrait() {
        var npc = display("stardewcraft", "kent");
        Set<ResourceLocation> available = Set.of(npc.portraitTexture(), display("stardewcraft", "lewis").mugshotTexture());
        assertEquals(npc.portraitTexture(), ClientDisplayFallbacks.socialPortrait(npc, available::contains));
    }

    @Test void missingAddonArtworkDoesNotBorrowLewisOrAnotherNamespace() {
        var npc = display("addon", "sam");
        Set<ResourceLocation> available = Set.of(display("stardewcraft", "sam").mugshotTexture(),
                display("stardewcraft", "lewis").mugshotTexture());
        assertNull(ClientDisplayFallbacks.socialPortrait(npc, available::contains));
        assertNull(ClientDisplayFallbacks.socialPortrait(null, available::contains));
    }
}
