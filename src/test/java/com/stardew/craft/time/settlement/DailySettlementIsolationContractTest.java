package com.stardew.craft.time.settlement;

import com.stardew.craft.Config;
import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import com.stardew.craft.time.StardewTimeManager;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySettlementIsolationContractTest {
    private static final Pattern SERVER_REGISTRATION = Pattern.compile(
            "registrar\\.playToServer\\(\\s*([^,]+)\\.TYPE,\\s*[^,]+\\.STREAM_CODEC,\\s*([^\\n]+)\\s*\\)",
            Pattern.MULTILINE);
    private static final Set<String> UNGATED_SERVER_PAYLOADS = Set.of(
            "com.stardew.craft.network.payload.SleepCancelPayload",
            "com.stardew.craft.network.overnight.OvernightReadyAckPayload",
            "com.stardew.craft.network.payload.StardewPauseStatePayload");

    @Test
    void productionBudgetAndItemLimitUseTheDeclaredConfiguration() throws IOException {
        assertEquals(4, Config.DAILY_SETTLEMENT_BUDGET_MILLIS.get());
        assertEquals(256, Config.DAILY_SETTLEMENT_ITEM_LIMIT.get());

        String services = source("src/main/java/com/stardew/craft/time/settlement/DailySettlementServices.java");
        assertTrue(services.contains("Config.DAILY_SETTLEMENT_BUDGET_MILLIS.get()"));
        assertTrue(services.contains("TimeUnit.MILLISECONDS.toNanos"));
        assertTrue(services.contains("Config.DAILY_SETTLEMENT_ITEM_LIMIT.get()"));
        assertFalse(services.contains("() -> 2_000_000L"));
        assertFalse(services.contains("() -> 64"));
    }

    @Test
    void activeSettlementFreezesBothVirtualAndSimulationClocks() {
        StardewTimeManager time = new StardewTimeManager();
        time.initializeSimulationGameTime(90L);

        assertEquals(0L, time.advanceIndependentDayTime(1.0D, true));
        time.advanceSimulationGameTime(true);

        assertEquals(0L, time.getIndependentDayTime());
        assertEquals(90L, time.getSimulationGameTime());

        assertEquals(1L, time.advanceIndependentDayTime(1.0D, false));
        time.advanceSimulationGameTime(false);
        assertEquals(1L, time.getIndependentDayTime());
        assertEquals(91L, time.getSimulationGameTime());
    }

    @Test
    void pendingDateIsVisibleOnlyInsideItemScopedSettlementView() throws Exception {
        StardewTimeManager time = new StardewTimeManager();
        time.setCurrentYear(1);
        time.setCurrentSeason(0);
        time.setCurrentDay(28);
        time.setCurrentTime(1_560);
        DailySettlementContext target = new DailySettlementContext(
                29, 1, 1, 1, 1_560, true, List.of(), Set.of());

        assertDate(time, 1, 0, 28, 1_560);
        DailySettlementDateView.run(target, () -> assertDate(time, 1, 1, 1, 360));
        assertDate(time, 1, 0, 28, 1_560);
    }

    @Test
    void anchorSurvivesWrongAckAndReconnectsAtTheLoginPosition() {
        UUID playerId = UUID.randomUUID();
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        DailySettlementAccessGuard guard = new DailySettlementAccessGuard(barrier);
        barrier.lockAll(29, List.of(playerId));
        guard.captureAnchor(playerId, Level.OVERWORLD, new Vec3(1.0D, 64.0D, 2.0D), 10.0F, 20.0F);

        assertFalse(guard.isGameplayAllowed(playerId));
        assertEquals(new Vec3(1.0D, 64.0D, 2.0D), guard.anchor(playerId).orElseThrow().position());
        assertFalse(guard.acknowledge(playerId, 28));
        assertTrue(barrier.isLocked(playerId));
        assertTrue(guard.anchor(playerId).isPresent());

        guard.onLogout(playerId);
        assertTrue(barrier.isLocked(playerId));
        assertEquals(Optional.empty(), guard.anchor(playerId));
        guard.reconnectAnchor(
                playerId, Level.NETHER, new Vec3(8.0D, 70.0D, 9.0D), 30.0F, 40.0F);
        assertEquals(Level.NETHER, guard.anchor(playerId).orElseThrow().dimension());
        assertEquals(new Vec3(8.0D, 70.0D, 9.0D), guard.anchor(playerId).orElseThrow().position());

        DailySettlementBarrier.ReadyResult ready = new DailySettlementBarrier.ReadyResult(
                29, new OvernightSettlementPayload(29, List.of(), List.of()));
        assertTrue(barrier.publishReady(playerId, ready));
        assertTrue(guard.acknowledge(playerId, 29));
        assertFalse(barrier.isLocked(playerId));
        assertTrue(guard.anchor(playerId).isEmpty());
    }

    @Test
    void everyOrdinaryServerGameplayPayloadUsesTheSharedGuard() throws IOException {
        String packetHandler = source("src/main/java/com/stardew/craft/network/PacketHandler.java");
        Matcher registrations = SERVER_REGISTRATION.matcher(packetHandler);
        int serverRegistrations = 0;
        while (registrations.find()) {
            serverRegistrations++;
            String payload = registrations.group(1).strip();
            String handler = registrations.group(2).strip();
            if (UNGATED_SERVER_PAYLOADS.contains(payload)) {
                assertFalse(handler.contains("DailySettlementAccessGuard.gated"), payload);
            } else {
                assertTrue(handler.startsWith("DailySettlementAccessGuard.gated("), payload);
            }
        }
        assertTrue(serverRegistrations > 100, "expected the complete playToServer registry");

        Pattern clientRegistration = Pattern.compile(
                "registrar\\.playToClient\\((?s:.*?)\\)", Pattern.MULTILINE);
        Matcher clients = clientRegistration.matcher(packetHandler);
        while (clients.find()) {
            assertFalse(clients.group().contains("DailySettlementAccessGuard.gated"));
        }
    }

    @Test
    void gatedPayloadHandlerChecksAccessOnTheServerExecutorBeforeDelegating() {
        AtomicReference<Runnable> queuedWork = new AtomicReference<>();
        IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(
                IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("enqueueWork")) {
                        queuedWork.set((Runnable) arguments[0]);
                        return CompletableFuture.completedFuture(null);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        AtomicBoolean allowed = new AtomicBoolean();
        AtomicInteger accessChecks = new AtomicInteger();
        AtomicInteger handled = new AtomicInteger();
        IPayloadHandler<OvernightSettlementPayload> guarded =
                DailySettlementAccessGuard.gated(
                        (payload, payloadContext) -> handled.incrementAndGet(),
                        payloadContext -> {
                            accessChecks.incrementAndGet();
                            return allowed.get();
                        });
        OvernightSettlementPayload payload =
                new OvernightSettlementPayload(29, List.of(), List.of());

        guarded.handle(payload, context);
        assertEquals(0, accessChecks.get());
        assertEquals(0, handled.get());
        queuedWork.get().run();
        assertEquals(1, accessChecks.get());
        assertEquals(0, handled.get());

        allowed.set(true);
        guarded.handle(payload, context);
        assertEquals(1, accessChecks.get());
        assertEquals(0, handled.get());
        queuedWork.get().run();
        assertEquals(2, accessChecks.get());
        assertEquals(1, handled.get());
    }

    @Test
    void machineTimeReadersKeepUsingTheSharedClockWithoutSettlementPauseFlags()
            throws IOException {
        for (String file : List.of(
                "TimedProductionBlockEntity.java",
                "CaskBlockEntity.java",
                "CoffeeMakerBlockEntity.java",
                "SolarPanelBlockEntity.java",
                "CrabPotBlockEntity.java",
                "MasteryStatueBlockEntity.java",
                "HeaterBlockEntity.java")) {
            String machine = source("src/main/java/com/stardew/craft/blockentity/" + file);
            assertTrue(machine.contains("StardewTimeManager"), file);
            assertFalse(machine.contains("DailySettlementServices"), file);
            assertFalse(machine.contains("DailySettlementAccessGuard"), file);
        }
    }

    @Test
    void neoforgeEarlyRejectionIsParticipantScopedAndUsesConcreteEvents()
            throws IOException {
        String events = source(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementEvents.java");
        String guard = source(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementAccessGuard.java");

        assertTrue(events.contains("PlayerTickEvent.Pre"));
        assertTrue(events.contains("EntityTeleportEvent"));
        assertTrue(events.contains("PlayerInteractEvent.LeftClickBlock"));
        assertTrue(events.contains("PlayerInteractEvent.RightClickBlock"));
        assertTrue(events.contains("PlayerInteractEvent.EntityInteract"));
        assertTrue(events.contains("LivingEntityUseItemEvent.Start"));
        assertTrue(events.contains("ItemEntityPickupEvent.Pre"));
        assertFalse(events.contains("onPlayerInteract(PlayerInteractEvent event)"));
        assertTrue(guard.contains("barrier.isLocked(playerId)"));
        assertTrue(guard.contains("player.setDeltaMovement(Vec3.ZERO)"));
        assertTrue(guard.contains("player.teleportTo("));
        assertFalse(guard.contains("server.pause"));
    }

    private static void assertDate(
            StardewTimeManager time, int year, int season, int day, int minute) {
        assertEquals(year, time.getCurrentYear());
        assertEquals(season, time.getCurrentSeason());
        assertEquals(day, time.getCurrentDay());
        assertEquals(minute, time.getCurrentTime());
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("stardewcraft.projectDir"));
    }
}
