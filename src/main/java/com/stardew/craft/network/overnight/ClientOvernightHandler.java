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
    private static boolean sequenceActive;
    private static Screen activeScreen;
    private static int currentAbsoluteDay = -1;
    private static int lastAcknowledgedAbsoluteDay = -1;
    private static boolean locked;
    private static OvernightSettlementPayload pendingReadyPayload;

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
        return locked;
    }

    public static boolean isReady() {
        return locked
                && pendingReadyPayload != null
                && pendingReadyPayload.absoluteDay() == currentAbsoluteDay;
    }

    public static int currentAbsoluteDay() {
        return currentAbsoluteDay;
    }

    public static void receiveBarrierState(OvernightBarrierPayload payload) {
        if (payload.absoluteDay() <= lastAcknowledgedAbsoluteDay) {
            return;
        }
        if (payload.absoluteDay() <= 0
                || (locked && payload.absoluteDay() < currentAbsoluteDay)) {
            return;
        }
        if (!locked || payload.absoluteDay() > currentAbsoluteDay) {
            currentAbsoluteDay = payload.absoluteDay();
            locked = payload.locked();
            pendingReadyPayload = null;
            if (!locked) {
                currentAbsoluteDay = -1;
            }
            return;
        }
        if (currentAbsoluteDay != payload.absoluteDay()) {
            return;
        }
        locked = payload.locked();
        if (!locked) {
            currentAbsoluteDay = -1;
            pendingReadyPayload = null;
        }
    }

    public static void receiveSettlement(OvernightSettlementPayload payload) {
        if (payload.absoluteDay() < 0) {
            if (!locked) {
                startSequence(payload);
            }
            return;
        }
        if (!locked || payload.absoluteDay() != currentAbsoluteDay) {
            return;
        }
        if (pendingReadyPayload == null) {
            pendingReadyPayload = payload;
        }
    }

    public static boolean startReadySequence(int absoluteDay) {
        if (!locked || absoluteDay != currentAbsoluteDay || !isReady()) {
            return false;
        }

        OvernightSettlementPayload payload = pendingReadyPayload;
        lastAcknowledgedAbsoluteDay = absoluteDay;
        locked = false;
        currentAbsoluteDay = -1;
        pendingReadyPayload = null;

        PacketDistributor.sendToServer(new OvernightReadyAckPayload(absoluteDay));
        if (Minecraft.getInstance().screen instanceof SleepWaitingOverlayScreen waitingScreen) {
            waitingScreen.onDayAdvanced();
        }
        startSequence(payload);
        return true;
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

        // 如果是 2AM 晕倒，先展示渐黑 + 惩罚摘要画面，再进入正常结算流程
        if (payload.hasPassOut()) {
            PassOutPayload passOutPayload = new PassOutPayload(
                PassOutService.PassOutType.fromId(payload.passOutType()),
                payload.passOutMoneyLost(),
                payload.passOutLostItems()
            );
            screenStack.add(new PassOutOverlayScreen(passOutPayload, screenStack));
            screenStack.add(new PassOutSummaryScreen(passOutPayload, screenStack));
        }

        // 技能升级画面
        for (OvernightSettlementPayload.LevelUpData levelData : payload.levelUps()) {
            screenStack.add(new com.stardew.craft.client.gui.overnight.LevelUpMenuScreen(levelData, screenStack));
        }

        long levelUpScreenCount = screenStack.stream().filter(LevelUpMenuScreen.class::isInstance).count();
        if (levelUpScreenCount != payload.levelUps().size()) {
            StardewCraft.LOGGER.error("[OVERNIGHT_CLIENT] Level-up settlement self-check failed: payload={}, screens={}",
                payload.levelUps().size(), levelUpScreenCount);
        }

        // 始终添加出货结算画面（即使没有出货物品也要展示夜间过渡动画）
        screenStack.add(new ShippingMenuScreen(payload.shippedItems(), screenStack));
        PENDING_SCREENS.addAll(screenStack);
        sequenceActive = true;
        StardewCraft.LOGGER.info("[OVERNIGHT_CLIENT] Screen chain size={}, opening first screen: {}",
            PENDING_SCREENS.size(), PENDING_SCREENS.peekFirst().getClass().getSimpleName());
        openNextScreen("start");
    }
}
