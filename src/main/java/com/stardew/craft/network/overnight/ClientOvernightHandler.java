package com.stardew.craft.network.overnight;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.overnight.SaveGameMenuScreen;
import com.stardew.craft.client.gui.overnight.SleepWaitingOverlayScreen;
import com.stardew.craft.cutscene.network.PlayerWokeUpPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.client.gui.overnight.ShippingMenuScreen;
import com.stardew.craft.client.gui.overnight.LevelUpMenuScreen;
import com.stardew.craft.player.ProfessionType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class ClientOvernightHandler {
    private static final Set<Integer> LOCAL_OVERNIGHT_PROFESSIONS = new HashSet<>();
    private static final Deque<Screen> PENDING_SCREENS = new ArrayDeque<>();
    private static final ClientOvernightFlow FLOW = new ClientOvernightFlow(
            new ClientOvernightUiGateway(new ClientOvernightUiGateway.ClientAccess() {
                @Override
                public boolean isPlayerSleeping() {
                    Minecraft minecraft = Minecraft.getInstance();
                    return minecraft.player != null && minecraft.player.isSleeping();
                }

                @Override
                public boolean isInBedOrWaitingScreen() {
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
                        int absoluteDay,
                        OvernightSettlementPayload payload
                ) {
                    PacketDistributor.sendToServer(new OvernightReadyAckPayload(absoluteDay));
                    if (Minecraft.getInstance().screen
                            instanceof SleepWaitingOverlayScreen waiting) {
                        waiting.onDayAdvanced();
                    }
                    OvernightCollapseClientState.acceptSettlement(payload);
                }

                @Override
                public void startLegacy(OvernightSettlementPayload payload) {
                    OvernightCollapseClientState.acceptSettlement(payload);
                }

                @Override
                public void requestCancelWaiting() {
                    PacketDistributor.sendToServer(
                            new com.stardew.craft.network.payload.SleepCancelPayload());
                }
            }));
    private static boolean sequenceActive;

    public static void beginSequence() {
        LOCAL_OVERNIGHT_PROFESSIONS.clear();
        PENDING_SCREENS.clear();
        sequenceActive = false;
    }

    /** Clears menu/fade state on disconnect or an aborted settlement. */
    public static void resetSession() {
        FLOW.resetConnectionState();
        LOCAL_OVERNIGHT_PROFESSIONS.clear();
        PENDING_SCREENS.clear();
        sequenceActive = false;
        com.stardew.craft.cutscene.runtime.EventScreenFade.clear();
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

    public static void receiveBarrierState(OvernightBarrierPayload payload) {
        FLOW.receiveBarrierState(payload);
    }

    public static void receiveSettlement(OvernightSettlementPayload payload) {
        if (payload.absoluteDay() < 0 || !FLOW.isLocked()) {
            OvernightCollapseClientState.acceptSettlement(payload);
            return;
        }
        FLOW.receiveSettlement(payload);
    }

    public static void receiveVoteProgress(int votedCount, int requiredCount) {
        FLOW.receiveVoteProgress(votedCount, requiredCount);
    }

    public static boolean handleWaitingInput() {
        return FLOW.handleDismissInput();
    }

    public static boolean canCancelWaiting() {
        return FLOW.canCancelWaiting();
    }

    public static boolean requestCancelWaiting() {
        return FLOW.requestCancelWaiting();
    }

    public static void receiveCancellationAccepted() {
        FLOW.receiveCancellationAccepted();
        Minecraft minecraft = Minecraft.getInstance();
        if (!FLOW.isLocked() && minecraft.screen instanceof SleepWaitingOverlayScreen) {
            minecraft.setScreen(null);
        }
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
        StardewCraft.LOGGER.info("[OVERNIGHT_CLIENT] Opening next settlement screen from {}: {} (remaining={})",
            source, next.getClass().getSimpleName(), PENDING_SCREENS.size());
        minecraft.setScreen(next);
        return true;
    }

    public static void completeSequence(String source) {
        if (!sequenceActive) {
            return;
        }
        StardewCraft.LOGGER.info("[OVERNIGHT_CLIENT] Settlement sequence completed by {}", source);
        PENDING_SCREENS.clear();
        sequenceActive = false;
        PacketDistributor.sendToServer(new PlayerWokeUpPayload());
        Minecraft.getInstance().setScreen(null);
    }

    public static void startSequence(OvernightSettlementPayload payload) {
        beginSequence();
        if (!payload.personalSettlement()) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        // Game1.NewDay keeps the screen black while the new-day task runs, then
        // fades back in over the end-of-night menus.
        com.stardew.craft.cutscene.runtime.EventScreenFade.startFadeFromBlack(12);

        // 如果玩家正在睡觉（原版 InBedChatScreen），关闭该界面
        if (Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.InBedChatScreen) {
            Minecraft.getInstance().setScreen(null);
        }

        StardewCraft.LOGGER.info("[OVERNIGHT_CLIENT] startSequence: hasPassOut={}, passOutType={}, moneyLost={}, levelUps={}, shippedItems={}",
            payload.hasPassOut(), payload.passOutType(), payload.passOutMoneyLost(),
            payload.levelUps().size(), payload.shippedItems().size());

        List<Screen> screenStack = new java.util.ArrayList<>();

        // 原版 pass-out 罚款通过次日邮件说明，不在 showEndOfNightStuff
        // 中插入自定义摘要页。夜间菜单链只包含升级页和 Shipping/Save。
        int levelIndex = 0;
        for (SettlementStage stage : settlementStages(payload)) {
            switch (stage) {
                case LEVEL_UP -> screenStack.add(
                    new LevelUpMenuScreen(payload.levelUps().get(levelIndex++), screenStack));
                case SHIPPING -> screenStack.add(
                    new ShippingMenuScreen(payload.shippedItems(), payload.context(), screenStack));
                case SAVE -> screenStack.add(new SaveGameMenuScreen(screenStack));
            }
        }

        long levelUpScreenCount = screenStack.stream().filter(LevelUpMenuScreen.class::isInstance).count();
        if (levelUpScreenCount != payload.levelUps().size()) {
            StardewCraft.LOGGER.error("[OVERNIGHT_CLIENT] Level-up settlement self-check failed: payload={}, screens={}",
                payload.levelUps().size(), levelUpScreenCount);
        }

        if (!payload.levelUps().isEmpty()) {
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(ModSounds.LEVEL_UP.get(), 1.0f, 1.0f));
        }

        PENDING_SCREENS.addAll(screenStack);
        sequenceActive = true;
        StardewCraft.LOGGER.info("[OVERNIGHT_CLIENT] Screen chain size={}",
                PENDING_SCREENS.size());
        openNextScreen("start");
    }

    static List<SettlementStage> settlementStages(OvernightSettlementPayload payload) {
        if (!payload.personalSettlement()) {
            return List.of();
        }
        return OvernightSequencePlanner.plan(
                        payload.levelUps().size(), !payload.shippedItems().isEmpty())
                .stream()
                .map(stage -> switch (stage) {
                    case LEVEL_UP -> SettlementStage.LEVEL_UP;
                    case SHIPPING -> SettlementStage.SHIPPING;
                    case SAVE -> SettlementStage.SAVE;
                })
                .toList();
    }

    enum SettlementStage {
        LEVEL_UP,
        SHIPPING,
        SAVE
    }
}
