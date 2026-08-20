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
                            || screen instanceof SleepWaitingOverlayScreen
                            || (screen instanceof ShippingMenuScreen shipping
                                    && shipping.isWaitingScreen());
                }

                @Override
                public void showWaiting(int votedCount, int requiredCount) {
                    Minecraft minecraft = Minecraft.getInstance();
                    boolean reused = minecraft.screen instanceof ShippingMenuScreen shipping
                            && shipping.isVotePrelude();
                    if (minecraft.screen instanceof ShippingMenuScreen shipping
                            && shipping.isVotePrelude()) {
                        shipping.updateVoteProgress(votedCount, requiredCount);
                    } else {
                        minecraft.setScreen(ShippingMenuScreen.createVotePrelude(
                                votedCount, requiredCount));
                    }
                    StardewCraft.LOGGER.info(
                            "[OVERNIGHT_CLIENT_TRACE] Waiting UI voted={}/{} reused={} day={} locked={} ready={}",
                            votedCount, requiredCount, reused, FLOW.currentAbsoluteDay(),
                            FLOW.isLocked(), FLOW.isReady());
                }

                @Override
                public void showPrelude(
                        int absoluteDay, int votedCount, int requiredCount) {
                    Minecraft minecraft = Minecraft.getInstance();
                    boolean reused = minecraft.screen instanceof ShippingMenuScreen shipping
                            && shipping.isWaitingForDay(absoluteDay);
                    if (minecraft.screen instanceof ShippingMenuScreen shipping
                            && shipping.isWaitingForDay(absoluteDay)) {
                        shipping.beginSettlementWait(absoluteDay);
                    } else {
                        minecraft.setScreen(ShippingMenuScreen.createPrelude(absoluteDay));
                    }
                    StardewCraft.LOGGER.info(
                            "[OVERNIGHT_CLIENT_TRACE] Night prelude day={} voted={}/{} reused={}",
                            absoluteDay, votedCount, requiredCount, reused);
                }

                @Override
                public void showReady(int votedCount, int requiredCount) {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.screen instanceof ShippingMenuScreen shipping
                            && shipping.isWaitingScreen()) {
                        StardewCraft.LOGGER.info(
                                "[OVERNIGHT_CLIENT_TRACE] Ready UI voted={}/{} day={} screen={}",
                                votedCount, requiredCount, FLOW.currentAbsoluteDay(),
                                minecraft.screen.getClass().getSimpleName());
                        return;
                    }
                    SleepWaitingOverlayScreen waiting;
                    if (minecraft.screen instanceof SleepWaitingOverlayScreen current) {
                        waiting = current;
                    } else {
                        waiting = new SleepWaitingOverlayScreen(votedCount, requiredCount);
                        minecraft.setScreen(waiting);
                    }
                    waiting.updateProgress(votedCount, requiredCount);
                    waiting.markSettlementReady();
                    StardewCraft.LOGGER.info(
                            "[OVERNIGHT_CLIENT_TRACE] Ready UI voted={}/{} day={} screen={}",
                            votedCount, requiredCount, FLOW.currentAbsoluteDay(),
                            minecraft.screen == null
                                    ? "none" : minecraft.screen.getClass().getSimpleName());
                }

                @Override
                public void startSettlement(
                        int absoluteDay,
                        OvernightSettlementPayload payload
                ) {
                    StardewCraft.LOGGER.info(
                            "[OVERNIGHT_CLIENT_TRACE] Starting settlement animation day={} personal={} shipped={} levels={}",
                            absoluteDay, payload.personalSettlement(),
                            payload.shippedItems().size(), payload.levelUps().size());
                    if (Minecraft.getInstance().screen
                            instanceof SleepWaitingOverlayScreen waiting) {
                        waiting.onDayAdvanced();
                    }
                    OvernightCollapseClientState.acceptSettlement(payload);
                }

                @Override
                public void acknowledgeSettlement(int absoluteDay) {
                    StardewCraft.LOGGER.info(
                            "[OVERNIGHT_CLIENT_TRACE] Sending READY ACK after world completion day={}",
                            absoluteDay);
                    PacketDistributor.sendToServer(new OvernightReadyAckPayload(absoluteDay));
                }

                @Override
                public void startLegacy(OvernightSettlementPayload payload) {
                    OvernightCollapseClientState.acceptSettlement(payload);
                }

                @Override
                public void requestCancelWaiting() {
                    StardewCraft.LOGGER.info(
                            "[OVERNIGHT_CLIENT_TRACE] Sending sleep vote cancellation");
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
        StardewCraft.LOGGER.info(
                "[OVERNIGHT_CLIENT_TRACE] Reset session day={} locked={} ready={} sequenceActive={}",
                FLOW.currentAbsoluteDay(), FLOW.isLocked(), FLOW.isReady(), sequenceActive);
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

    public static boolean isWorldReady() {
        return FLOW.isWorldReady();
    }

    public static int currentAbsoluteDay() {
        return FLOW.currentAbsoluteDay();
    }

    public static void receiveBarrierState(OvernightBarrierPayload payload) {
        FLOW.receiveBarrierState(payload);
        StardewCraft.LOGGER.info(
                "[OVERNIGHT_CLIENT] Barrier received day={} locked={} stateDay={} stateLocked={} ready={}",
                payload.absoluteDay(), payload.locked(), FLOW.currentAbsoluteDay(),
                FLOW.isLocked(), FLOW.isReady());
    }

    public static void receiveSettlement(OvernightSettlementPayload payload) {
        if (payload.absoluteDay() < 0) {
            OvernightCollapseClientState.acceptSettlement(payload);
            StardewCraft.LOGGER.info(
                    "[OVERNIGHT_CLIENT] Legacy settlement received personal={} shipped={} levels={}",
                    payload.personalSettlement(), payload.shippedItems().size(),
                    payload.levelUps().size());
            return;
        }
        int previousDay = FLOW.currentAbsoluteDay();
        boolean previouslyLocked = FLOW.isLocked();
        FLOW.receiveSettlement(payload);
        StardewCraft.LOGGER.info(
                "[OVERNIGHT_CLIENT] Settlement received day={} previousDay={} previousLocked={} stateDay={} stateLocked={} ready={} personal={} shipped={} levels={}",
                payload.absoluteDay(), previousDay, previouslyLocked,
                FLOW.currentAbsoluteDay(), FLOW.isLocked(), FLOW.isReady(),
                payload.personalSettlement(), payload.shippedItems().size(),
                payload.levelUps().size());
    }

    public static void receiveWorldReady(OvernightWorldReadyPayload payload) {
        boolean sequenceWasActive = sequenceActive;
        FLOW.receiveWorldReady(payload);
        StardewCraft.LOGGER.info(
                "[OVERNIGHT_CLIENT] World ready received day={} stateDay={} locked={} ready={} worldReady={}",
                payload.absoluteDay(), FLOW.currentAbsoluteDay(), FLOW.isLocked(),
                FLOW.isReady(), FLOW.isWorldReady());
        if (!FLOW.isLocked()
                && (sequenceWasActive
                    || Minecraft.getInstance().screen instanceof ShippingMenuScreen shipping
                        && shipping.isAwaitingSettlement())) {
            PENDING_SCREENS.clear();
            sequenceActive = false;
            PacketDistributor.sendToServer(new PlayerWokeUpPayload());
            Minecraft.getInstance().setScreen(null);
        }
    }

    public static void receiveVoteProgress(int votedCount, int requiredCount) {
        StardewCraft.LOGGER.info(
                "[OVERNIGHT_CLIENT_TRACE] Vote progress voted={}/{} day={} locked={} ready={}",
                votedCount, requiredCount, FLOW.currentAbsoluteDay(),
                FLOW.isLocked(), FLOW.isReady());
        FLOW.receiveVoteProgress(votedCount, requiredCount);
    }

    public static boolean handleWaitingInput() {
        int absoluteDay = FLOW.currentAbsoluteDay();
        boolean ready = FLOW.isReady();
        boolean handled = FLOW.handleDismissInput();
        StardewCraft.LOGGER.info(
                "[OVERNIGHT_CLIENT_TRACE] Waiting input day={} readyBefore={} handled={} lockedAfter={} readyAfter={}",
                absoluteDay, ready, handled, FLOW.isLocked(), FLOW.isReady());
        return handled;
    }

    public static void logWaitingHeartbeat(
            int ticksOpen,
            int votedCount,
            int requiredCount,
            boolean screenReady) {
        StardewCraft.LOGGER.info(
                "[OVERNIGHT_CLIENT_TRACE] Waiting heartbeat ticks={} voted={}/{} screenReady={} day={} locked={} flowReady={}",
                ticksOpen, votedCount, requiredCount, screenReady,
                FLOW.currentAbsoluteDay(), FLOW.isLocked(), FLOW.isReady());
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
        if (!FLOW.isLocked() && (minecraft.screen instanceof SleepWaitingOverlayScreen
                || (minecraft.screen instanceof ShippingMenuScreen shipping
                        && shipping.isVotePrelude()))) {
            minecraft.setScreen(null);
        }
    }

    public static boolean startReadySequence(int absoluteDay) {
        return FLOW.startReadySequence(absoluteDay);
    }

    public static boolean finishSettlementSequence() {
        return FLOW.finishSettlementSequence();
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
        FLOW.markSettlementSequenceFinished();
        if (FLOW.isLocked() && !FLOW.finishSettlementSequence()) {
            StardewCraft.LOGGER.info(
                    "[OVERNIGHT_CLIENT] World settlement is still pending; keeping player locked");
            PENDING_SCREENS.clear();
            sequenceActive = false;
            Minecraft.getInstance().setScreen(
                    ShippingMenuScreen.createPrelude(FLOW.currentAbsoluteDay()));
            return;
        }
        PENDING_SCREENS.clear();
        sequenceActive = false;
        PacketDistributor.sendToServer(new PlayerWokeUpPayload());
        Minecraft.getInstance().setScreen(null);
    }

    public static void startSequence(OvernightSettlementPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ShippingMenuScreen prelude = minecraft.screen instanceof ShippingMenuScreen shipping
                && shipping.isAwaitingSettlement()
                && shipping.isPreludeForDay(payload.absoluteDay())
                && !payload.hasPassOut()
                ? shipping
                : null;
        beginSequence();
        if (!payload.personalSettlement()) {
            minecraft.setScreen(null);
            return;
        }
        // The shipping starfield is the overnight transition surface. Do not
        // add a second global fade on top of it; that was the source of the
        // black interval between the vote and the settlement animation.
        com.stardew.craft.cutscene.runtime.EventScreenFade.clear();

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
                case SHIPPING -> {
                    if (prelude != null) {
                        prelude.acceptSettlement(payload);
                        screenStack.add(prelude);
                        prelude = null;
                    } else {
                        screenStack.add(new ShippingMenuScreen(
                                payload.shippedItems(), payload.context(), screenStack));
                    }
                }
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
