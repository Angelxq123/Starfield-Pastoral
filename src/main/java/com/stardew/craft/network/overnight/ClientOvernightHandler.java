package com.stardew.craft.network.overnight;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.overnight.SleepWaitingOverlayScreen;
import com.stardew.craft.network.payload.PassOutPayload;
import com.stardew.craft.player.PassOutService;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.client.gui.overnight.ShippingMenuScreen;
import com.stardew.craft.client.gui.overnight.PassOutOverlayScreen;
import com.stardew.craft.client.gui.overnight.PassOutSummaryScreen;
import com.stardew.craft.client.gui.overnight.LevelUpMenuScreen;
import com.stardew.craft.player.ProfessionType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public class ClientOvernightHandler {
    private static final Set<Integer> LOCAL_OVERNIGHT_PROFESSIONS = new HashSet<>();
    private static final Deque<Screen> PENDING_SCREENS = new ArrayDeque<>();
    private static final ClientOvernightFlow FLOW = new ClientOvernightFlow(
            new ClientOvernightFlow.UiGateway() {
                @Override
                public boolean isLocalSleeperOrWaiting() {
                    Screen screen = Minecraft.getInstance().screen;
                    return screen instanceof net.minecraft.client.gui.screens.InBedChatScreen
                            || screen instanceof SleepWaitingOverlayScreen;
                }

                @Override
                public void showWaiting(int votedCount, int requiredCount) {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof SleepWaitingOverlayScreen waiting) {
                        waiting.updateProgress(votedCount, requiredCount);
                    } else {
                        minecraft.setScreen(
                                new SleepWaitingOverlayScreen(votedCount, requiredCount));
                    }
                }

                @Override
                public void acknowledgeAndStart(
                        int absoluteDay, OvernightSettlementPayload payload) {
                    PacketDistributor.sendToServer(new OvernightReadyAckPayload(absoluteDay));
                    if (Minecraft.getInstance().screen
                            instanceof SleepWaitingOverlayScreen waiting) {
                        waiting.onDayAdvanced();
                    }
                    startSequence(payload);
                }

                @Override
                public void startLegacy(OvernightSettlementPayload payload) {
                    startSequence(payload);
                }
            });
    private static boolean sequenceActive;
    private static Screen activeScreen;

    public static void beginSequence() {
        LOCAL_OVERNIGHT_PROFESSIONS.clear();
        PENDING_SCREENS.clear();
        sequenceActive = false;
        activeScreen = null;
    }

    public static void recordLocalProfessionChoice(int professionId) {
        LOCAL_OVERNIGHT_PROFESSIONS.add(professionId);
    }

    public static boolean hasLocalProfession(ProfessionType profession) {
        return profession != null && LOCAL_OVERNIGHT_PROFESSIONS.contains(profession.getId());
    }

    public static boolean isSequenceActive() {
        return sequenceActive;
    }

    public static boolean isLocked() {
        return FLOW.isLocked();
    }

    public static boolean isReady() {
        return FLOW.isReady();
    }

    public static int currentAbsoluteDay() {
        return FLOW.currentAbsoluteDay();
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        resetConnectionState();
    }

    static void resetConnectionState() {
        FLOW.resetConnectionState();
        sequenceActive = false;
        PENDING_SCREENS.clear();
        activeScreen = null;
        LOCAL_OVERNIGHT_PROFESSIONS.clear();
    }

    public static void receiveBarrierState(OvernightBarrierPayload payload) {
        FLOW.receiveBarrierState(payload);
    }

    public static void receiveSettlement(OvernightSettlementPayload payload) {
        FLOW.receiveSettlement(payload);
    }

    public static void receiveVoteProgress(int votedCount, int requiredCount) {
        FLOW.receiveVoteProgress(votedCount, requiredCount);
    }

    public static boolean handleWaitingInput() {
        return FLOW.handleDismissInput();
    }

    public static boolean startReadySequence(int absoluteDay) {
        return FLOW.startReadySequence(absoluteDay);
    }

    public static boolean openNextScreen(String source) {
        if (!sequenceActive) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen next = PENDING_SCREENS.pollFirst();
        if (next == null) {
            completeSequence(source);
            return false;
        }
        activeScreen = next;
        StardewCraft.LOGGER.info("[OVERNIGHT_CLIENT] Opening next settlement screen from {}: {} (remaining={})",
            source, next.getClass().getSimpleName(), PENDING_SCREENS.size());
        minecraft.setScreen(next);
        return true;
    }

    public static void completeSequence(String source) {
        if (sequenceActive) {
            StardewCraft.LOGGER.info("[OVERNIGHT_CLIENT] Settlement sequence completed by {}", source);
        }
        PENDING_SCREENS.clear();
        sequenceActive = false;
        activeScreen = null;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!sequenceActive || activeScreen == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.screen;
        if (current == activeScreen) {
            return;
        }
        StardewCraft.LOGGER.warn("[OVERNIGHT_CLIENT] Settlement screen was interrupted by {}; restoring {}",
            current == null ? "null" : current.getClass().getSimpleName(),
            activeScreen.getClass().getSimpleName());
        minecraft.setScreen(activeScreen);
    }

    public static void startSequence(OvernightSettlementPayload payload) {
        beginSequence();

        // 如果玩家正在睡觉（原版 InBedChatScreen），关闭该界面
        if (Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.InBedChatScreen) {
            Minecraft.getInstance().setScreen(null);
        }

        StardewCraft.LOGGER.info("[OVERNIGHT_CLIENT] startSequence: hasPassOut={}, passOutType={}, moneyLost={}, levelUps={}, shippedItems={}",
            payload.hasPassOut(), payload.passOutType(), payload.passOutMoneyLost(),
            payload.levelUps().size(), payload.shippedItems().size());

        List<Screen> screenStack = new java.util.ArrayList<>();

        PassOutPayload passOutPayload = payload.hasPassOut()
                ? new PassOutPayload(
                        PassOutService.PassOutType.fromId(payload.passOutType()),
                        payload.passOutMoneyLost(), payload.passOutLostItems())
                : null;
        int levelIndex = 0;
        for (SettlementStage stage : settlementStages(payload)) {
            switch (stage) {
                case PASS_OUT_OVERLAY ->
                        screenStack.add(new PassOutOverlayScreen(passOutPayload, screenStack));
                case PASS_OUT_SUMMARY ->
                        screenStack.add(new PassOutSummaryScreen(passOutPayload, screenStack));
                case LEVEL_UP -> screenStack.add(new LevelUpMenuScreen(
                        payload.levelUps().get(levelIndex++), screenStack));
                case SHIPPING ->
                        screenStack.add(new ShippingMenuScreen(payload.shippedItems(), screenStack));
            }
        }

        long levelUpScreenCount = screenStack.stream().filter(LevelUpMenuScreen.class::isInstance).count();
        if (levelUpScreenCount != payload.levelUps().size()) {
            StardewCraft.LOGGER.error("[OVERNIGHT_CLIENT] Level-up settlement self-check failed: payload={}, screens={}",
                payload.levelUps().size(), levelUpScreenCount);
        }

        PENDING_SCREENS.addAll(screenStack);
        sequenceActive = true;
        StardewCraft.LOGGER.info("[OVERNIGHT_CLIENT] Screen chain size={}, opening first screen: {}",
            PENDING_SCREENS.size(), PENDING_SCREENS.peekFirst().getClass().getSimpleName());
        openNextScreen("start");
    }

    static List<SettlementStage> settlementStages(OvernightSettlementPayload payload) {
        List<SettlementStage> stages = new java.util.ArrayList<>();
        if (payload.hasPassOut()) {
            stages.add(SettlementStage.PASS_OUT_OVERLAY);
            stages.add(SettlementStage.PASS_OUT_SUMMARY);
        }
        for (int ignored = 0; ignored < payload.levelUps().size(); ignored++) {
            stages.add(SettlementStage.LEVEL_UP);
        }
        stages.add(SettlementStage.SHIPPING);
        return List.copyOf(stages);
    }

    enum SettlementStage {
        PASS_OUT_OVERLAY,
        PASS_OUT_SUMMARY,
        LEVEL_UP,
        SHIPPING
    }
}
