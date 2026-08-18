package com.stardew.craft.network;

import com.stardew.craft.communitycenter.network.BundleClientData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleProgressDeltaPayloadContractTest {
    private static final Path PROJECT_ROOT = Path.of(
            System.getProperty("stardewcraft.projectDir", "."));
    private static final Path NETWORK_ROOT = PROJECT_ROOT.resolve(
            "src/main/java/com/stardew/craft/communitycenter/network");

    @AfterEach
    void clearClientState() {
        BundleClientData.INSTANCE.clear();
    }

    @Test
    void clientDeltaReplacesOnlyTheChangedBundleSlots() throws Exception {
        BundleClientData data = BundleClientData.INSTANCE;
        data.clear();
        data.update(
                Map.of(1, new boolean[]{true, false}, 2, new boolean[]{false}),
                new boolean[7],
                Map.of(1, true),
                false);

        Method applyDelta = Arrays.stream(BundleClientData.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("applyProgressDelta"))
                .findFirst()
                .orElse(null);
        assertNotNull(applyDelta, "incremental client update method is missing");
        applyDelta.invoke(
                data,
                1,
                new boolean[]{true, true},
                new boolean[]{true, false, false, false, false, false, false},
                Map.of(2, true),
                true);

        assertTrue(data.isBundleComplete(1));
        assertFalse(data.isSlotComplete(2, 0), "unrelated bundle slots must be retained");
        assertTrue(data.isAreaComplete(0));
        assertFalse(data.isRewardAvailable(1), "reward snapshot must replace stale entries");
        assertTrue(data.isRewardAvailable(2));
        assertTrue(data.canReadJunimoText());
    }

    @Test
    void deltaPayloadCarriesOneBundleAndSharedLightweightProgress() throws IOException {
        Path payload = NETWORK_ROOT.resolve("BundleProgressDeltaPayload.java");
        assertTrue(Files.exists(payload), "bundle progress delta payload is missing");
        String source = normalized(payload);

        assertTrue(source.contains("intbundleId"));
        assertTrue(source.contains("boolean[]bundleSlots"));
        assertTrue(source.contains("boolean[]areasComplete"));
        assertTrue(source.contains("Map<Integer,Boolean>bundleRewards"));
        assertTrue(source.contains("BundleClientData.INSTANCE.applyProgressDelta("));
        assertTrue(source.contains("BundleDefinitionSyncPayload.sendIfChanged("));
        assertFalse(source.contains("Map<Integer,boolean[]>bundleSlots"));
    }

    @Test
    void frequentActionsUseDeltaWhileFullRefreshPathsRemainAvailable() throws IOException {
        String actions = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/api/v1/communitycenter/StardewCommunityCenterActions.java"));
        String menu = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/communitycenter/menu/BundleMenu.java"));
        String packetHandler = normalized(PROJECT_ROOT.resolve(
                "src/main/java/com/stardew/craft/network/PacketHandler.java"));

        assertTrue(actions.contains("BundleProgressDeltaPayload.send(player,bundleId)"));
        assertFalse(actions.contains("BundleSyncPayload.sendFullSync(player)"));
        assertFalse(menu.contains("BundleSyncPayload.sendFullSync(player)"));
        assertFalse(menu.contains("BundleSyncPayload.sendFullSync(sp)"));
        assertTrue(packetHandler.contains("BundleProgressDeltaPayload.TYPE"));
        assertTrue(packetHandler.contains("BundleProgressDeltaPayload.STREAM_CODEC"));
    }

    private static String normalized(Path source) throws IOException {
        if (!Files.exists(source)) {
            return "";
        }
        return Files.readString(source).replaceAll("\\s+", "");
    }
}
