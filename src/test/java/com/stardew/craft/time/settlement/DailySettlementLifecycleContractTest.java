package com.stardew.craft.time.settlement;

import com.stardew.craft.event.DimensionEventHandler;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PassOutService;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.time.StardewTimeManager;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySettlementLifecycleContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir"));
    private static final HolderLookup.Provider REGISTRIES = VanillaRegistries.createLookup();

    @Test
    void dateViewExposesTheFrozenMorningAndAlwaysClearsItsThreadLocal() throws Exception {
        StardewTimeManager time = oldNight();
        DailySettlementContext target = context(226, 3, 0, 2);

        DailySettlementDateView.run(target, () -> {
            assertEquals(3, time.getCurrentYear());
            assertEquals(0, time.getCurrentSeason());
            assertEquals(2, time.getCurrentDay());
            assertEquals(StardewTimeManager.MORNING_START, time.getCurrentTime());
            assertSame(target, DailySettlementDateView.current().orElseThrow());
        });

        assertTrue(DailySettlementDateView.current().isEmpty());
        assertBackingDate(time, 2, 3, 28, 1550);

        assertThrows(IllegalStateException.class, () -> DailySettlementDateView.run(target, () -> {
            throw new IllegalStateException("body failed");
        }));
        assertTrue(DailySettlementDateView.current().isEmpty());
    }

    @Test
    void aPlayerWithoutPendingSettlementHasNoUnacknowledgedReadyToBlockSleepCancel() {
        UUID playerId = UUID.randomUUID();
        Map<UUID, PlayerStardewData> playerData = new HashMap<>();
        playerData.put(playerId, unsettledPlayer(playerId));
        RecordingSettlementBackend backend = new RecordingSettlementBackend(playerData);
        PlayerDailySettlementService service = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));

        assertFalse(service.hasUnacknowledgedReady(playerId));
        assertEquals(0, backend.persistenceWrites);
    }

    @Test
    void sameTargetMayNestButDifferentTargetIsRejectedWithoutLosingTheOuterScope() throws Exception {
        DailySettlementContext target = context(226, 3, 0, 2);
        DailySettlementContext sameTarget = new DailySettlementContext(
                226, 3, 0, 2, 1200, false, List.of(UUID.randomUUID()), Set.of());
        DailySettlementContext differentTarget = context(227, 3, 0, 3);

        DailySettlementDateView.run(target, () -> {
            DailySettlementDateView.run(sameTarget, () ->
                    assertSame(target, DailySettlementDateView.current().orElseThrow()));
            assertThrows(IllegalStateException.class,
                    () -> DailySettlementDateView.run(differentTarget, () -> {}));
            assertSame(target, DailySettlementDateView.current().orElseThrow());
        });

        assertTrue(DailySettlementDateView.current().isEmpty());
    }

    @Test
    void coordinatorScopesEveryItemAndPublishesTheBackingDateOnlyAtTheEnd() throws Exception {
        StardewTimeManager time = oldNight();
        DailySettlementContext target = context(226, 3, 0, 2);
        List<String> scoped = new ArrayList<>();
        AtomicInteger readyCalls = new AtomicInteger();
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> 0L), () -> 1_000_000L, () -> 1,
                (context, builder) -> {
                    builder.addPrepare(readDate("prepare", time, scoped));
                    builder.addWorld(readDate("world", time, scoped));
                    builder.addPlayer(readDate("player", time, scoped));
                    builder.addCommit(DailySettlementWorkUnits.atomic("date_publication", () -> {
                        readTargetDate(time);
                        time.publishSettlementDate(context);
                        scoped.add("publication");
                    }, () -> {}));
                },
                new DailySettlementCoordinator.LifecycleListener() {
                    @Override
                    public void phaseChanged(DailySettlementContext context, DailySettlementPhase phase) {
                    }

                    @Override
                    public void itemFailure(DailySettlementContext context, String unitName,
                            String itemIdentity, int attempt, boolean permanent) {
                    }

                    @Override
                    public void ready(DailySettlementContext context) {
                        readyCalls.incrementAndGet();
                    }
                });

        coordinator.start(target);
        coordinator.tick();
        assertBackingDate(time, 2, 3, 28, 1550);
        coordinator.tick();
        assertBackingDate(time, 2, 3, 28, 1550);
        coordinator.tick();
        assertBackingDate(time, 2, 3, 28, 1550);
        coordinator.tick();

        assertEquals(List.of("prepare", "player", "world", "publication"), scoped);
        assertEquals(1, readyCalls.get());
        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertBackingDate(time, 3, 0, 2, StardewTimeManager.MORNING_START);
        assertTrue(time.isDirty());
        assertFalse(booleanField(time, "event1800Triggered"));
        assertFalse(booleanField(time, "event2200Triggered"));
        assertFalse(booleanField(time, "event0000Triggered"));
        assertFalse(booleanField(time, "event0130Triggered"));
        assertEquals(-1, intField(time, "lastTenMinuteBucket"));
        assertTrue(DailySettlementDateView.current().isEmpty());
    }

    @Test
    void productionDatePublicationDoesNotRepeatBackingOrVirtualAdvanceWhenHooksRetry()
            throws Exception {
        StardewTimeManager time = oldNight();
        time.setVirtualDayTime(12_000L);
        DailySettlementContext target = context(226, 3, 0, 2);
        AtomicInteger virtualPublications = new AtomicInteger();
        AtomicInteger hookCalls = new AtomicInteger();
        AtomicInteger readyCalls = new AtomicInteger();
        List<Integer> backingDaysSeenByHooks = new ArrayList<>();
        DailySettlementWorkUnit publication = DailySettlementPlanFactory.publishDate(
                target,
                time,
                () -> {
                    virtualPublications.incrementAndGet();
                    DimensionEventHandler.publishSettlementVirtualTime(
                            time, target.absoluteDay());
                },
                () -> {
                    backingDaysSeenByHooks.add(intField(time, "currentDay"));
                    if (hookCalls.incrementAndGet() == 1) {
                        throw new IllegalStateException("injected post-publication failure");
                    }
                });
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> 0L), () -> 1_000_000L, () -> 64,
                (context, builder) -> builder.addCommit(publication),
                new DailySettlementCoordinator.LifecycleListener() {
                    @Override
                    public void phaseChanged(
                            DailySettlementContext context, DailySettlementPhase phase) {
                    }

                    @Override
                    public void itemFailure(DailySettlementContext context, String unitName,
                            String itemIdentity, int attempt, boolean permanent) {
                    }

                    @Override
                    public void ready(DailySettlementContext context) {
                        readyCalls.incrementAndGet();
                    }
                });

        coordinator.start(target);
        coordinator.tick();
        assertEquals(DailySettlementPhase.COMMIT, coordinator.phase());
        assertBackingDate(time, 2, 3, 28, 1550);
        assertEquals((target.absoluteDay() - 1L) * 24_000L, time.getVirtualDayTime());
        assertEquals(1, virtualPublications.get());
        assertEquals(1, hookCalls.get());
        assertEquals(List.of(28), backingDaysSeenByHooks);
        assertEquals(0, readyCalls.get());

        coordinator.tick();

        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertBackingDate(time, 3, 0, 2, StardewTimeManager.MORNING_START);
        assertEquals((target.absoluteDay() - 1L) * 24_000L, time.getVirtualDayTime());
        assertEquals(1, virtualPublications.get());
        assertEquals(2, hookCalls.get());
        assertEquals(List.of(28, 28), backingDaysSeenByHooks);
        assertEquals(1, readyCalls.get());
    }

    @Test
    void readyResultsRemainLockedAfterCoordinatorReturnsToIdleInThePublicationTick() {
        UUID player = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(player), Set.of());
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        OvernightSettlementPayload payload = new OvernightSettlementPayload(
                target.absoluteDay(), List.of(), List.of());
        barrier.lockAll(target.absoluteDay(), target.playerIds());
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> 0L), () -> 1_000_000L, () -> 1,
                (context, builder) -> builder.addCommit(DailySettlementWorkUnits.atomic(
                        "date_publication", () -> {}, () -> {})),
                listenerPublishing(barrier, player, payload));

        coordinator.start(target);
        coordinator.tick();

        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertTrue(barrier.isLocked(player));
        assertEquals(payload, barrier.readyResult(player, target.absoluteDay()).payload());
    }

    @Test
    void advanceEntryOnlyCapturesImmutableInputsAndStartsTheCoordinator() throws Exception {
        ParsedClass time = parse("src/main/java/com/stardew/craft/time/StardewTimeManager.java");
        MethodTree advance = time.method("advanceDayWithSleepTime", 1);
        List<String> calls = invocationNames(advance);
        String body = advance.getBody().toString();

        assertEquals(1, frequency(calls, "captureNextDay"));
        assertEquals(1, frequency(calls, "start"));
        assertTrue(body.contains("::participates"));
        assertTrue(calls.contains("getAllFarms"));
        assertTrue(body.contains("farmOwnerAudience"));
        assertTrue(body.contains("getOwnerForPlayer"));
        assertTrue(body.contains("shouldSettleFarm"));
        assertTrue(body.contains("Set.copyOf"));
        assertFalse(body.contains("currentDay++"));
        assertFalse(body.contains("currentDay ="));
        assertFalse(body.contains("currentSeason ="));
        assertFalse(body.contains("currentYear ="));
        assertFalse(body.contains("currentTime ="));
        assertFalse(body.contains("GrowthManager"));
        assertTrue(body.contains("WeatherManager.getCurrentWeather"));
        assertFalse(body.contains("applyWeatherForNewDay"));
        assertFalse(body.contains("MailService"));
        assertFalse(body.contains("SpecialOrderManager"));
        assertFalse(body.contains("ShippingBinBlockEntity"));
    }

    @Test
    void customVotesOwnSleepAdvanceWhileVanillaCompletionOnlySuppressesItsClock() throws Exception {
        ParsedClass dimension = parse("src/main/java/com/stardew/craft/event/DimensionEventHandler.java");

        assertEquals(1, frequency(invocationNames(dimension.method("advanceToNextMorning", 3)),
                "advanceDayWithSleepTime"));
        assertTrue(invocationNames(dimension.method("requestPassOutAdvance", 1))
                .contains("schedulePassOutAdvance"));
        assertTrue(invocationNames(dimension.method("schedulePassOutAdvance", 4))
                .contains("advanceToNextMorning"));
        assertTrue(invocationNames(dimension.method("requestSleepAdvance", 3))
                .contains("advanceToNextMorning"));
        assertFalse(invocationNames(dimension.method("onSleepFinished", 1))
                .contains("advanceToNextMorning"));
        assertTrue(invocationNames(dimension.method("onSleepFinished", 1))
                .contains("setTimeAddition"));
    }

    @Test
    void lifecycleEventsTickOnceReconnectWithoutCreationAndDrainBeforeRemoval() throws Exception {
        ParsedClass events = parse("src/main/java/com/stardew/craft/time/settlement/DailySettlementEvents.java");
        ParsedClass players = parse("src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java");
        ParsedClass services = parse("src/main/java/com/stardew/craft/time/settlement/DailySettlementServices.java");

        assertEquals(1, frequency(invocationNames(events.method("onServerTick", 1)), "tick"));
        assertEquals(0, frequency(invocationNames(events.method("onServerTick", 1)), "get"));
        assertTrue(invocationNames(events.method("onServerTick", 1)).contains("find"));

        String login = players.method("onPlayerLogin", 1).getBody().toString();
        assertTrue(login.contains("DailySettlementEvents.onPlayerLogin(player)"));
        String logout = players.method("onPlayerLogout", 1).getBody().toString();
        assertTrue(logout.contains("DailySettlementEvents.onPlayerLogout(player)"));
        assertFalse(logout.contains("coordinator().cancel"));
        assertFalse(logout.contains("DailySettlementServices.remove"));

        MethodTree stop = services.method("stop", 0);
        List<String> stopCalls = invocationNames(stop);
        assertTrue(stopCalls.indexOf("drain") >= 0);
        assertTrue(stopCalls.indexOf("drain") < stopCalls.indexOf("clear"));
        MethodTree remove = services.method("remove", 1);
        List<String> removeCalls = invocationNames(remove);
        assertTrue(removeCalls.contains("removeRegistered"));
        List<String> removeRegisteredCalls = invocationNames(
                services.method("removeRegistered", 3));
        assertTrue(removeRegisteredCalls.indexOf("get")
                < removeRegisteredCalls.indexOf("accept"));
        assertTrue(removeRegisteredCalls.indexOf("accept")
                < removeRegisteredCalls.indexOf("remove"));
    }

    @Test
    void servicesOwnOneWeakSynchronizedPerServerGraphWithoutCoordinatorSingleton() throws Exception {
        String source = source("src/main/java/com/stardew/craft/time/settlement/DailySettlementServices.java");
        ParsedClass services = parseSource("DailySettlementServices", source);

        assertTrue(source.contains("WeakKeyRegistry<MinecraftServer, Services>"));
        assertTrue(services.method("get", 1).getModifiers().getFlags()
                .contains(javax.lang.model.element.Modifier.SYNCHRONIZED));
        assertTrue(services.method("find", 1).getModifiers().getFlags()
                .contains(javax.lang.model.element.Modifier.SYNCHRONIZED));
        assertTrue(services.method("remove", 1).getModifiers().getFlags()
                .contains(javax.lang.model.element.Modifier.SYNCHRONIZED));
        assertTrue(services.method("get", 1).getBody().toString().contains("getOrCreate"));
        assertFalse(services.method("find", 1).getBody().toString().contains("getOrCreate"));

        String coordinator = source("src/main/java/com/stardew/craft/time/settlement/DailySettlementCoordinator.java");
        assertFalse(coordinator.contains("static DailySettlementCoordinator"));
    }

    @Test
    void playerBatchDefersDisconnectedConsumptionAndPreservesOnlinePayloadOrder() throws Exception {
        ParsedClass service = parse(
                "src/main/java/com/stardew/craft/time/settlement/PlayerDailySettlementService.java");
        MethodTree create = service.method("createDailyWorkUnit", 1);
        MethodTree settle = service.method("settlePlayer", 2);
        MethodTree prepare = service.method("prepareOnlineSettlement", 5);
        MethodTree finalize = service.method("finalizeOnlineSettlement", 5);
        MethodTree production = service.methods("shippingPayload").stream()
                .filter(method -> method.getBody() != null
                        && method.getBody().toString().contains("peekPayload"))
                .findFirst()
                .orElseThrow();
        MethodTree consume = service.methods("consumeShipping").stream()
                .filter(method -> method.getBody() != null
                        && method.getBody().toString().contains("consumePayload"))
                .findFirst()
                .orElseThrow();

        assertTrue(create.getBody().toString().contains("context.playerIds()"));
        assertTrue(settle.getBody().toString().contains("settleIfOnline(context, playerId)"));
        assertTrue(settle.getBody().toString().contains("pending.save(context, playerId)"));
        assertFalse(settle.getBody().toString().contains("consumePayload"));
        assertFalse(settle.getBody().toString().contains("consumePassOutResult"));
        assertTrue(settle.getBody().toString().contains("readyResults.put"));

        List<String> prepareCalls = invocationNames(prepare);
        assertTrue(prepareCalls.indexOf("shippingPayload") >= 0);
        assertTrue(prepareCalls.indexOf("shippingPayload")
                < prepareCalls.indexOf("applyShippingHistory"));
        assertTrue(prepareCalls.indexOf("applyShippingHistory")
                < prepareCalls.indexOf("applyShippingMoney"));
        assertTrue(prepareCalls.indexOf("applyLevels")
                < prepareCalls.indexOf("buildPayload"));
        assertTrue(invocationNames(production).contains("peekPayload"));
        assertTrue(invocationNames(finalize).indexOf("consumeShipping") >= 0);
        assertTrue(invocationNames(consume).contains("consumePayload"));
        assertTrue(settle.getBody().toString().indexOf("readyResults.put")
                < settle.getBody().toString().lastIndexOf("finishPreparedSettlement"));
    }

    @Test
    void disconnectedPlayerSettlementSurvivesPersistenceAndCompletesExactlyOnceOnReconnect() {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        PlayerStardewData original = unsettledPlayer(playerId);
        Map<UUID, PlayerStardewData> playerData = new HashMap<>();
        playerData.put(playerId, original);
        RecordingSettlementBackend backend = new RecordingSettlementBackend(playerData);
        PlayerDailySettlementService.PlayerDataPendingStore pending =
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++);
        PlayerDailySettlementService service =
                new PlayerDailySettlementService(backend, pending);

        service.settlePlayer(target, playerId);

        assertEquals(0, backend.settlementCalls);
        assertTrue(backend.shippingLedgerAvailable);
        assertTrue(backend.passOutAvailable);
        assertEquals(500, original.getMoney());
        assertEquals(0, original.getTotalShippingGold());
        assertEquals(1, backend.persistenceWrites);
        assertEquals(target.absoluteDay(), pending.find(playerId).orElseThrow().absoluteDay());

        PlayerStardewData restored = PlayerStardewData.fromNBT(original.toNBT(), playerId);
        playerData.put(playerId, restored);
        PlayerDailySettlementService recovered = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(target.absoluteDay(), List.of(playerId));
        assertTrue(barrier.publishReady(
                playerId, recovered.readyResultOrCreate(target, playerId)));

        backend.online = true;
        recovered.onLogin(playerId, barrier);
        recovered.onLogin(playerId, barrier);

        assertEquals(1, backend.settlementCalls);
        assertFalse(backend.shippingLedgerAvailable);
        assertFalse(backend.passOutAvailable);
        assertEquals(620, restored.getMoney());
        assertEquals(120, restored.getTotalShippingGold());
        assertEquals(restored.getMaxEnergy(), restored.getEnergy());
        assertEquals(restored.getMaxHealth(), restored.getHealth());
        assertEquals(1, restored.getDaysLeftForToolUpgrade());
        assertEquals(5, restored.getRawSkillLevel(SkillType.FARMING));
        assertTrue(restored.isRecipeUnlocked("test_recipe"));
        assertTrue(restored.hasPendingProfessionChoices());
        assertEquals(1, backend.questDayStartedCalls);
        assertEquals(1, backend.masteryMorningCalls);
        assertEquals(4, backend.persistenceWrites);
        assertTrue(recovered.pendingSettlement(playerId).orElseThrow()
                .completedPayload().isPresent());
        assertEquals(5, barrier.readyResult(playerId, target.absoluteDay())
                .payload().levelUps().size());
    }

    @Test
    void reconnectBeforeReadyPublicationCompletesOnceWithoutPublishingEarly() {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        Map<UUID, PlayerStardewData> playerData = new HashMap<>();
        playerData.put(playerId, unsettledPlayer(playerId));
        RecordingSettlementBackend backend = new RecordingSettlementBackend(playerData);
        PlayerDailySettlementService service = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(target.absoluteDay(), List.of(playerId));

        service.settlePlayer(target, playerId);
        backend.online = true;
        service.onLogin(playerId, barrier);

        assertEquals(1, backend.settlementCalls);
        assertTrue(service.pendingSettlement(playerId).orElseThrow()
                .completedPayload().isPresent());
        assertNull(barrier.readyResult(playerId, target.absoluteDay()));

        assertTrue(barrier.publishReady(
                playerId, service.readyResultOrCreate(target, playerId)));
        assertEquals(5, barrier.readyResult(playerId, target.absoluteDay())
                .payload().levelUps().size());
        service.onLogin(playerId, barrier);
        assertEquals(1, backend.settlementCalls);
    }

    @Test
    void persistedPendingSettlementRecoversThroughTheLiveBarrierAndServiceGraph()
            throws Exception {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        Map<UUID, PlayerStardewData> playerData = new HashMap<>();
        PlayerStardewData original = unsettledPlayer(playerId);
        playerData.put(playerId, original);
        RecordingSettlementBackend backend = new RecordingSettlementBackend(playerData);
        PlayerDailySettlementService firstService = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        firstService.settlePlayer(target, playerId);

        playerData.put(playerId, PlayerStardewData.fromNBT(original.toNBT(), playerId));
        PlayerDailySettlementService recoveredService = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        backend.online = true;

        DailySettlementBarrier.ReadyResult recovered =
                recoveredService.onLogin(playerId, null).orElseThrow();

        assertEquals(target.absoluteDay(), recovered.absoluteDay());
        assertEquals(5, recovered.payload().levelUps().size());
        assertEquals(1, backend.settlementCalls);
        assertEquals(recovered.payload(),
                recoveredService.onLogin(playerId, null).orElseThrow().payload());
        assertEquals(1, backend.settlementCalls);

        ParsedClass events = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementEvents.java");
        String login = events.method("onPlayerLogin", 1).getBody().toString();
        assertTrue(login.contains("DailySettlementServices.get"));
        assertTrue(login.contains("services.players().onLogin"));
        assertTrue(login.contains("services.barrier()"));
        assertTrue(login.contains("services.accessGuard().reconnectAnchor"));
        assertTrue(login.indexOf("new OvernightBarrierPayload")
                < login.indexOf("recovered.payload()"));
        assertFalse(login.contains("recoverPending"));
    }

    @Test
    void persistedReadySurvivesRepeatedReconnectsUntilMatchingAcknowledgement()
            throws Exception {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        Map<UUID, PlayerStardewData> playerData = new HashMap<>();
        PlayerStardewData original = unsettledPlayer(playerId);
        playerData.put(playerId, original);
        RecordingSettlementBackend backend = new RecordingSettlementBackend(playerData);
        PlayerDailySettlementService first = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));

        first.settlePlayer(target, playerId);
        backend.online = true;
        DailySettlementBarrier.ReadyResult firstReady =
                first.onLogin(playerId, null).orElseThrow();
        assertEquals(1, backend.settlementCalls);
        assertTrue(first.pendingSettlement(playerId).orElseThrow().completedPayload().isPresent());

        PlayerStardewData afterFirstReconnect = PlayerStardewData.fromNBT(
                original.toNBT(REGISTRIES), playerId, REGISTRIES);
        playerData.put(playerId, afterFirstReconnect);
        PlayerDailySettlementService second = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        DailySettlementBarrier.ReadyResult secondReady =
                second.onLogin(playerId, null).orElseThrow();

        assertEquals(firstReady.payload(), secondReady.payload());
        assertEquals(1, backend.settlementCalls,
                "a persisted READY must not rebuild stage zero on reconnect");
        assertFalse(second.acknowledgeReady(playerId, target.absoluteDay() - 1));

        PlayerStardewData afterStaleAck = PlayerStardewData.fromNBT(
                afterFirstReconnect.toNBT(REGISTRIES), playerId, REGISTRIES);
        playerData.put(playerId, afterStaleAck);
        PlayerDailySettlementService third = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        assertEquals(secondReady.payload(), third.onLogin(playerId, null).orElseThrow().payload());
        assertTrue(third.acknowledgeReady(playerId, target.absoluteDay()));
        assertTrue(third.pendingSettlement(playerId).isEmpty());
        assertTrue(third.onLogin(playerId, null).isEmpty());

        ParsedClass ack = parse(
                "src/main/java/com/stardew/craft/network/overnight/OvernightReadyAckPayload.java");
        String ackHandler = ack.method("handle", 2).getBody().toString();
        assertTrue(ackHandler.contains("acknowledgeReady"));
        ParsedClass cancel = parse(
                "src/main/java/com/stardew/craft/network/payload/SleepCancelPayload.java");
        assertTrue(cancel.method("handle", 2).getBody().toString()
                .contains("hasUnacknowledgedReady"));
    }

    @Test
    void frozenCommitHookAudiencesExcludeLateLoginsAndDoNotReplaceLogouts() {
        AudiencePlayer mainWorldAtStart = audience("main_world_start", false);
        AudiencePlayer valleyAtStart = audience("valley_start", true);
        AudiencePlayer valleyLoggedOut = audience("valley_logout", true);
        AudiencePlayer lateValleyLogin = audience("valley_late", true);
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false,
                List.of(valleyAtStart.id()), Set.of(), List.of(),
                List.of(mainWorldAtStart.id(), valleyAtStart.id(), valleyLoggedOut.id()),
                List.of(valleyAtStart.id(), valleyLoggedOut.id()));
        assertEquals(
                List.of(mainWorldAtStart.id(), valleyAtStart.id(), valleyLoggedOut.id()),
                target.allOnlinePlayerIds());
        assertEquals(
                List.of(valleyAtStart.id(), valleyLoggedOut.id()),
                target.valleyOnlinePlayerIds());
        assertFalse(target.allOnlinePlayerIds().contains(lateValleyLogin.id()));
        assertFalse(target.valleyOnlinePlayerIds().contains(lateValleyLogin.id()));
    }

    @Test
    void lateLoginJoinsTheActiveBarrierWithoutEnteringTheFrozenRewardAudience() {
        UUID participant = UUID.randomUUID();
        UUID lateLogin = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(participant), Set.of());
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(target.absoluteDay(), target.playerIds());

        assertTrue(DailySettlementEvents.lockLateJoinForActiveDay(
                target, barrier, lateLogin));

        assertTrue(barrier.isLocked(lateLogin));
        assertEquals(target.absoluteDay(), barrier.lockedDay(lateLogin));
        assertFalse(target.playerIds().contains(lateLogin));
        assertFalse(DailySettlementEvents.lockLateJoinForActiveDay(
                target, barrier, lateLogin));
        assertFalse(DailySettlementEvents.lockLateJoinForActiveDay(
                target, barrier, participant));
    }

    @Test
    void enteringASettlementDimensionJoinsTheActiveBarrierAfterAutomaticRouting()
            throws Exception {
        ParsedClass dimension = parse(
                "src/main/java/com/stardew/craft/event/DimensionEventHandler.java");
        List<String> changedCalls = invocationNames(
                dimension.method("onPlayerChangeDimension", 1));

        assertEquals(1, frequency(changedCalls, "onPlayerEnteredSettlementDimension"));
        assertTrue(changedCalls.indexOf("onPlayerEnteredSettlementDimension")
                > changedCalls.indexOf("teleportTo"));
        assertTrue(changedCalls.indexOf("onPlayerEnteredSettlementDimension")
                > changedCalls.indexOf("teleportPlayerToFloor"));

        ParsedClass events = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementEvents.java");
        String entry = events.method("onPlayerEnteredSettlementDimension", 1)
                .getBody().toString();
        assertTrue(entry.contains("DailySettlementServices.find"));
        assertTrue(entry.contains("players().participates(player)"));
        assertTrue(entry.contains("lockLateJoinForActiveDay"));
        assertTrue(entry.contains("OvernightBarrierPayload"));
    }

    @Test
    void readyAckDistinguishesPersonalAndBarrierOnlyResults() throws Exception {
        ParsedClass ack = parse(
                "src/main/java/com/stardew/craft/network/overnight/OvernightReadyAckPayload.java");
        String body = ack.method("handle", 2).getBody().toString();

        assertTrue(body.contains("readyResult"));
        assertTrue(body.contains("canAcknowledge"));
        assertTrue(body.contains("hasCompletedReady"));
        assertTrue(body.contains("personalSettlement"));
        assertTrue(body.contains("onReadyAcknowledged"));
    }

    @Test
    void settlementParticipationDependsOnDimensionInsteadOfFarmOwnership() throws Exception {
        ParsedClass players = parse(
                "src/main/java/com/stardew/craft/time/settlement/PlayerDailySettlementService.java");
        String participates = players.method("participates", 1).getBody().toString();
        String cleanup = players.method("requiresNonParticipantCleanup", 1)
                .getBody().toString();

        assertTrue(participates.contains("isInSettlementDimension"));
        assertFalse(participates.contains("hasFarm"));
        assertFalse(cleanup.contains("hasFarm"));
    }

    @Test
    void frozenFarmOwnersDriveFarmCursorAndCavesWithoutRegistryRescans()
            throws Exception {
        ParsedClass time = parse("src/main/java/com/stardew/craft/time/StardewTimeManager.java");
        String advance = time.method("advanceDayWithSleepTime", 1).getBody().toString();
        assertTrue(advance.contains("getAllFarms()"),
                "farms visited during the settled day must enter this rollover");
        assertTrue(advance.contains("farmOwnerAudience"));
        assertTrue(advance.contains("getOwnerForPlayer"));
        assertTrue(advance.contains("shouldSettleFarm"));

        ParsedClass factory = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java");
        String cursor = factory.method("updateFarmCursor", 1).getBody().toString();
        assertFalse(cursor.contains("getAllFarms()"),
                "farm cursor must not rescan the registry during commit");
        assertTrue(cursor.contains("frozenFarms"),
                "farm cursor must consume the start-time farm snapshot");

        ParsedClass caves = parse(
                "src/main/java/com/stardew/craft/manager/FarmCaveDailyService.java");
        String caveWork = caves.method("createDailyWorkUnit", 3).getBody().toString();
        assertFalse(caveWork.contains("getAllFarms()"),
                "farm caves must not rescan the registry");
        assertTrue(caveWork.contains("context.farmOwnerIds()"),
                "farm caves must be bounded by the frozen owner ids");
    }

    @Test
    void frozenAllOnlineAudienceDerivesFarmOwnersAndIgnoresLaterAudienceChanges() {
        UUID sharedOwner = UUID.randomUUID();
        UUID mainWorldSharedMember = UUID.randomUUID();
        UUID soloOwner = UUID.randomUUID();
        UUID noFarm = UUID.randomUUID();
        UUID lateLogin = UUID.randomUUID();
        Map<UUID, UUID> ownerByPlayer = Map.of(
                sharedOwner, sharedOwner,
                mainWorldSharedMember, sharedOwner,
                soloOwner, soloOwner);
        List<UUID> startAllOnline = new ArrayList<>(
                List.of(mainWorldSharedMember, sharedOwner, soloOwner, noFarm));
        List<UUID> participants = List.of(sharedOwner, soloOwner);

        Set<UUID> owners = DailySettlementPlanFactory.farmOwnerAudience(
                startAllOnline, ownerByPlayer::get);
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false,
                participants, owners, List.of(), startAllOnline, List.of(sharedOwner));
        startAllOnline.clear();
        startAllOnline.add(lateLogin);

        assertEquals(participants, target.playerIds());
        assertEquals(
                List.of(mainWorldSharedMember, sharedOwner, soloOwner, noFarm),
                target.allOnlinePlayerIds());
        assertEquals(Set.of(sharedOwner, soloOwner), target.farmOwnerIds());
        assertFalse(target.allOnlinePlayerIds().contains(lateLogin));
    }

    @Test
    void noFarmCleanupConsumesFrozenOldResultsWithoutEnteringThePlayerBatch()
            throws Exception {
        UUID noFarmPlayer = UUID.randomUUID();
        UUID lateLogin = UUID.randomUUID();
        List<UUID> cleanupAudience = new ArrayList<>(List.of(noFarmPlayer));
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false,
                List.of(), Set.of(), cleanupAudience);
        Map<UUID, Integer> oldShippingGold = new HashMap<>();
        oldShippingGold.put(noFarmPlayer, 120);
        Set<UUID> oldPassOut = new java.util.HashSet<>(Set.of(noFarmPlayer));
        DailySettlementWorkUnit cleanup =
                DailySettlementPlanFactory.createNonParticipantCleanupWorkUnit(
                        target,
                        playerId -> {
                            oldShippingGold.remove(playerId);
                            oldPassOut.remove(playerId);
                        });

        cleanupAudience.clear();
        cleanupAudience.add(lateLogin);
        cleanup.runNext();

        assertTrue(cleanup.isComplete());
        assertEquals(List.of(noFarmPlayer), target.nonParticipantCleanupIds());
        assertTrue(target.playerIds().isEmpty());
        assertTrue(oldShippingGold.isEmpty());
        assertTrue(oldPassOut.isEmpty());
        assertEquals(0, oldShippingGold.getOrDefault(noFarmPlayer, 0),
                "creating a farm later must not make old shipping payable");

        ParsedClass factory = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java");
        String productionCleanup = factory.method(
                "cleanupNonParticipant", 2).getBody().toString();
        assertTrue(productionCleanup.contains("consumePayload"));
        assertTrue(productionCleanup.contains("consumePassOutResult"));
        assertTrue(productionCleanup.contains("context.absoluteDay()"));
        String create = factory.methods("create").stream()
                .filter(method -> method.getBody() != null
                        && method.getBody().toString().contains("shipping_bin_flush"))
                .findFirst().orElseThrow().getBody().toString();
        assertTrue(create.contains("non_participant_cleanup"));

        ParsedClass time = parse("src/main/java/com/stardew/craft/time/StardewTimeManager.java");
        String advance = time.method("advanceDayWithSleepTime", 1).getBody().toString();
        assertTrue(advance.contains("requiresNonParticipantCleanup"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void startTimeFarmSnapshotSurvivesRegistryAddsAndDeletes() throws Exception {
        FarmInstanceRegistry registry = new FarmInstanceRegistry();
        UUID frozenOwner = UUID.randomUUID();
        UUID lateOwner = UUID.randomUUID();
        FarmInstance frozenFarm = createFarm(registry, frozenOwner, "Frozen");
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(), Set.of(frozenOwner));
        Method snapshotMethod = DailySettlementPlanFactory.class.getDeclaredMethod(
                "snapshotFarms", DailySettlementContext.class, FarmInstanceRegistry.class);
        snapshotMethod.setAccessible(true);
        Map<UUID, FarmInstance> snapshot = (Map<UUID, FarmInstance>) snapshotMethod.invoke(
                null, target, registry);

        registry.deleteFarm(frozenOwner);
        createFarm(registry, lateOwner, "Late");

        assertEquals(Set.of(frozenOwner), snapshot.keySet());
        assertSame(frozenFarm, snapshot.get(frozenOwner));
        assertFalse(snapshot.containsKey(lateOwner));
    }

    @Test
    void onlineSettlementFailurePersistsProgressAndReconnectCompletesEachStageOnce() {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        PlayerStardewData data = unsettledPlayer(playerId);
        Map<UUID, PlayerStardewData> playerData = new HashMap<>();
        playerData.put(playerId, data);
        FailingOnlineSettlementBackend backend = new FailingOnlineSettlementBackend(data);
        PlayerDailySettlementService service = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));

        backend.online = true;
        assertThrows(IllegalStateException.class, () -> service.settlePlayer(target, playerId));
        assertTrue(service.pendingSettlement(playerId).isPresent(),
                "an interrupted online batch must remain recoverable");

        PlayerStardewData restored = PlayerStardewData.fromNBT(
                data.toNBT(REGISTRIES), playerId, REGISTRIES);
        assertEquals(2, restored.getPendingDailySettlement().orElseThrow().stage());
        playerData.put(playerId, restored);
        backend.data = restored;
        PlayerDailySettlementService recovered = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        backend.failAfterShipping = false;
        assertTrue(recovered.onLogin(playerId, null).isPresent());
        assertTrue(recovered.onLogin(playerId, null).isPresent(),
                "unacknowledged READY must remain available after completion");
        assertTrue(recovered.pendingSettlement(playerId).orElseThrow()
                .completedPayload().isPresent());
        assertEquals(1, backend.shippingApplications);
        assertEquals(1, backend.levelApplications);
        assertEquals(1, backend.recipeApplications);
        assertEquals(1, backend.questDayStartedCalls);
        assertEquals(1, backend.masteryMorningCalls);
        assertEquals(620, restored.getMoney());
        assertEquals(120, restored.getTotalShippingGold());
    }

    @Test
    void completedSettlementRoundTripPreservesOfficialContextAndBarrierSemantics() {
        UUID playerId = UUID.randomUUID();
        PlayerStardewData data = new PlayerStardewData(playerId);
        OvernightSettlementPayload.OvernightContext overnightContext =
                OvernightSettlementPayload.OvernightContext.forTargetDate(
                        3, 0, 2, "Rain");
        OvernightSettlementPayload payload = new OvernightSettlementPayload(
                226, List.of(), List.of(), -1, 0, List.of(),
                overnightContext, false);
        assertTrue(data.schedulePendingDailySettlement(
                new PlayerStardewData.PendingDailySettlement(
                        226, 3, 0, 2, 1_560, false, "Rain", 12,
                        List.of(), Optional.of(payload))));

        PlayerStardewData restored = PlayerStardewData.fromNBT(
                data.toNBT(REGISTRIES), playerId, REGISTRIES);
        PlayerStardewData.PendingDailySettlement restoredPending =
                restored.getPendingDailySettlement().orElseThrow();
        OvernightSettlementPayload restoredPayload =
                restoredPending.completedPayload().orElseThrow();

        assertEquals("Rain", restoredPending.previousWeather());
        assertEquals(226, restoredPayload.absoluteDay());
        assertEquals(overnightContext, restoredPayload.context());
        assertFalse(restoredPayload.personalSettlement());
    }

    @Test
    void coordinatorRetryResumesThePersistedOnlineSettlementStage() {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        PlayerStardewData data = unsettledPlayer(playerId);
        Map<UUID, PlayerStardewData> playerData = new HashMap<>();
        playerData.put(playerId, data);
        FailingOnlineSettlementBackend backend = new FailingOnlineSettlementBackend(data);
        backend.online = true;
        PlayerDailySettlementService service = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));

        assertThrows(IllegalStateException.class, () -> service.settlePlayer(target, playerId));
        backend.failAfterShipping = false;
        service.settlePlayer(target, playerId);

        assertTrue(service.pendingSettlement(playerId).orElseThrow()
                .completedPayload().isPresent());
        assertEquals(1, backend.shippingApplications);
        assertEquals(1, backend.levelApplications);
        assertEquals(1, backend.recipeApplications);
        assertEquals(1, backend.questDayStartedCalls);
        assertEquals(1, backend.masteryMorningCalls);
    }

    @Test
    void missingServicesRestoreTheLiveBarrierBeforeAReadyAckCanReleaseIt()
            throws Exception {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        Map<UUID, PlayerStardewData> playerData = new HashMap<>();
        PlayerStardewData original = unsettledPlayer(playerId);
        playerData.put(playerId, original);
        RecordingSettlementBackend backend = new RecordingSettlementBackend(playerData);
        PlayerDailySettlementService first = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        first.settlePlayer(target, playerId);

        playerData.put(playerId, PlayerStardewData.fromNBT(
                original.toNBT(REGISTRIES), playerId, REGISTRIES));
        PlayerDailySettlementService recovered = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        backend.online = true;
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> 0L), () -> 1L, () -> 1,
                (context, builder) -> {}, DailySettlementCoordinator.LifecycleListener.NOOP);

        DailySettlementBarrier.ReadyResult ready =
                DailySettlementServices.restorePlayerBarrier(
                        recovered, barrier, coordinator, playerId).orElseThrow();
        assertTrue(barrier.isLocked(playerId));
        assertTrue(barrier.readyResult(playerId, target.absoluteDay()) != null);
        assertFalse(barrier.acknowledge(playerId, target.absoluteDay() - 1));
        assertTrue(barrier.acknowledge(playerId, ready.absoluteDay()));
        assertTrue(recovered.acknowledgeReady(playerId, ready.absoluteDay()));
        assertTrue(recovered.pendingSettlement(playerId).isEmpty());

        ParsedClass ack = parse(
                "src/main/java/com/stardew/craft/network/overnight/OvernightReadyAckPayload.java");
        List<String> selects = invocationSelects(ack.method("handle", 2));
        assertTrue(selects.contains("DailySettlementServices.getForPlayer"));
        assertFalse(selects.contains(
                "PlayerDailySettlementService.acknowledgeReady"));
        ParsedClass services = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementServices.java");
        assertTrue(invocationNames(services.method("getForPlayer", 1))
                .contains("restorePlayerBarrier"));
    }

    @Test
    void activeCoordinatorKeepsRetryOwnershipAcrossLogoutAndLogin() {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        PlayerStardewData data = unsettledPlayer(playerId);
        Map<UUID, PlayerStardewData> playerData = new HashMap<>();
        playerData.put(playerId, data);
        FailingOnlineSettlementBackend backend = new FailingOnlineSettlementBackend(data);
        backend.online = true;
        PlayerDailySettlementService service = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(target.absoluteDay(), List.of(playerId));

        assertThrows(IllegalStateException.class, () -> service.settlePlayer(target, playerId));
        service.onLogout(playerId);
        assertTrue(DailySettlementEvents.resumePlayerSettlement(
                service, barrier, playerId, true).isEmpty(),
                "login must leave an active coordinator's failed item pending");
        assertTrue(service.pendingSettlement(playerId).isPresent());
        assertEquals(1, backend.shippingApplications);

        backend.failAfterShipping = false;
        service.settlePlayer(target, playerId);
        DailySettlementBarrier.ReadyResult ready =
                service.readyResult(playerId, target.absoluteDay());
        assertTrue(barrier.publishReady(playerId, ready));

        service.settlePlayer(target, playerId);

        assertSame(ready, service.readyResult(playerId, target.absoluteDay()));
        assertSame(ready, barrier.readyResult(playerId, target.absoluteDay()));
        assertTrue(service.pendingSettlement(playerId).orElseThrow()
                .completedPayload().isPresent());
        assertEquals(1, backend.shippingApplications);
        assertEquals(1, backend.levelApplications);
        assertEquals(1, backend.recipeApplications);
        assertEquals(1, backend.questDayStartedCalls);
        assertEquals(1, backend.masteryMorningCalls);
    }

    @Test
    void coordinatorReadyPublicationCompletesAnOfflineItemThatReconnectedBeforeReady()
            throws Exception {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        PlayerStardewData data = unsettledPlayer(playerId);
        Map<UUID, PlayerStardewData> playerData = new HashMap<>();
        playerData.put(playerId, data);
        RecordingSettlementBackend backend = new RecordingSettlementBackend(playerData);
        PlayerDailySettlementService service = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(target.absoluteDay(), target.playerIds());

        service.settlePlayer(target, playerId);
        assertEquals(0, backend.settlementCalls);
        assertEquals(0, service.pendingSettlement(playerId).orElseThrow().stage());

        backend.online = true;
        assertTrue(DailySettlementEvents.resumePlayerSettlement(
                service, barrier, playerId, true).isEmpty(),
                "login must not race the active coordinator");
        DailySettlementBarrier.ReadyResult ready =
                service.readyResultOrCreate(target, playerId);

        assertEquals(1, backend.settlementCalls);
        assertEquals(620, data.getMoney());
        assertEquals(data.getMaxEnergy(), data.getEnergy());
        assertEquals(1, data.getDaysLeftForToolUpgrade());
        assertEquals(5, ready.payload().levelUps().size());
        assertTrue(service.pendingSettlement(playerId).orElseThrow()
                .completedPayload().isPresent());
        assertTrue(barrier.publishReady(playerId, ready));

        ParsedClass ack = parse(
                "src/main/java/com/stardew/craft/network/overnight/OvernightReadyAckPayload.java");
        String ackHandler = ack.method("handle", 2).getBody().toString();
        assertTrue(ackHandler.indexOf("hasCompletedReady")
                < ackHandler.indexOf("accessGuard().acknowledge"),
                "an ACK must not unlock a fallback without a completed payload");
        assertTrue(barrier.acknowledge(playerId, target.absoluteDay()));
        assertTrue(service.acknowledgeReady(playerId, target.absoluteDay()));
        assertTrue(service.pendingSettlement(playerId).isEmpty());
        assertEquals(1, backend.settlementCalls);
        assertEquals(1, backend.questDayStartedCalls);
        assertEquals(1, backend.masteryMorningCalls);
    }

    @Test
    void readyPublicationRetriesFinalCleanupBeforeReadyCanBeAcknowledged() {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        PlayerStardewData data = unsettledPlayer(playerId);
        Map<UUID, PlayerStardewData> playerData = new HashMap<>();
        playerData.put(playerId, data);
        FailingReadyCleanupBackend backend = new FailingReadyCleanupBackend(playerData);
        PlayerDailySettlementService service = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        barrier.lockAll(target.absoluteDay(), target.playerIds());

        service.settlePlayer(target, playerId);
        backend.online = true;
        assertThrows(IllegalStateException.class,
                () -> service.readyResultOrCreate(target, playerId));

        assertEquals(1, backend.settlementCalls);
        assertEquals(1, backend.cleanupAttempts);
        assertTrue(service.pendingSettlement(playerId).orElseThrow()
                .completedPayload().isPresent());
        assertFalse(service.hasCompletedReady(playerId, target.absoluteDay()),
                "a retained payload is not publishable until final cleanup completes");
        assertFalse(barrier.acknowledge(playerId, target.absoluteDay()));

        DailySettlementBarrier.ReadyResult ready =
                service.readyResultOrCreate(target, playerId);

        assertEquals(1, backend.settlementCalls,
                "cleanup retry must reuse the retained payload without repeating settlement");
        assertEquals(2, backend.cleanupAttempts);
        assertEquals(12, service.pendingSettlement(playerId).orElseThrow().stage());
        assertTrue(service.hasCompletedReady(playerId, target.absoluteDay()));
        assertTrue(barrier.publishReady(playerId, ready));
        assertTrue(barrier.acknowledge(playerId, target.absoluteDay()));
        assertTrue(service.acknowledgeReady(playerId, target.absoluteDay()));
        assertTrue(service.pendingSettlement(playerId).isEmpty());
    }

    @Test
    void productionSettlementRetriesInsideShippingAndFinalCleanupWithoutDuplicateEffects()
            throws Exception {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());
        Class<?> operationsType = List.of(PlayerDailySettlementService.class.getDeclaredClasses()).stream()
                .filter(type -> type.getSimpleName().equals("SettlementOperations"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("production settlement operations are missing"));
        InternalFailureState state = new InternalFailureState(target.absoluteDay());
        Object operations = Proxy.newProxyInstance(
                operationsType.getClassLoader(), new Class<?>[]{operationsType}, state::invoke);
        AtomicReference<PlayerDailySettlementService.PendingSettlement> progress =
                new AtomicReference<>(new PlayerDailySettlementService.PendingSettlement(
                        target.absoluteDay(), target.year(), target.season(), target.day(),
                        target.sleepMinute(), target.seasonChanged()));
        Method prepare = PlayerDailySettlementService.class.getDeclaredMethod(
                "prepareOnlineSettlement", DailySettlementContext.class, UUID.class,
                PlayerDailySettlementService.PendingSettlement.class, Consumer.class, operationsType);
        Method finalize = PlayerDailySettlementService.class.getDeclaredMethod(
                "finalizeOnlineSettlement", DailySettlementContext.class, UUID.class,
                PlayerDailySettlementService.PendingSettlement.class, Consumer.class, operationsType);
        prepare.setAccessible(true);
        finalize.setAccessible(true);

        assertThrows(IllegalStateException.class, () -> invokeSettlement(
                prepare, target, playerId, progress, operations));
        OvernightSettlementPayload payload = (OvernightSettlementPayload) invokeSettlement(
                prepare, target, playerId, progress, operations);

        assertEquals(120, state.money);
        assertEquals(1, state.historyApplications);
        assertEquals(1, state.orderApplications);
        assertEquals(1, state.moneyApplications);
        assertEquals(1, state.levelApplications);
        assertEquals(1, state.recipeApplications);
        assertEquals(1, state.questApplications);
        assertEquals(1, state.masteryApplications);
        assertEquals(1, payload.shippedItems().size());
        assertTrue(payload.hasPassOut());

        assertThrows(IllegalStateException.class, () -> invokeSettlement(
                finalize, target, playerId, progress, operations));
        invokeSettlement(finalize, target, playerId, progress, operations);

        assertEquals(1, state.shippingConsumes);
        assertEquals(1, state.passOutConsumes);
        assertEquals(1, state.levelClears);
        assertEquals(1, payload.shippedItems().size(), "prepared READY payload must survive cleanup retry");
        assertTrue(payload.hasPassOut(), "prepared READY payload must retain pass-out data");
    }

    @Test
    void productionReceiptsAreClaimedBeforeNonIdempotentSettlementEffects() throws Exception {
        ParsedClass data = parse(
                "src/main/java/com/stardew/craft/player/PlayerStardewData.java");
        String history = data.method("recordOvernightShippingHistory", 2).getBody().toString();
        assertTrue(history.indexOf("lastOvernightShippingHistoryDay = absoluteDay")
                < history.indexOf("for ("),
                "shipping history must claim its day before incrementing counters");

        ParsedClass api = parse(
                "src/main/java/com/stardew/craft/player/PlayerStardewDataAPI.java");
        String orders = api.method("applyOvernightShippingOrders", 3).getBody().toString();
        assertTrue(orders.indexOf("markOvernightShippingOrdersApplied")
                < orders.indexOf("recordOvernightShippedCore"),
                "special-order hooks must be claimed before they can mutate progress");
        String money = api.method("applyOvernightShippingMoney", 3).getBody().toString();
        int moneyReceipt = money.indexOf("markOvernightShippingMoneyApplied");
        assertTrue(moneyReceipt < money.indexOf("addMoneyWithoutSync"),
                "shared money must be claimed before payment");
        assertTrue(moneyReceipt < money.indexOf("addTotalShippingGold"),
                "shipping totals must be claimed before incrementing history");

        ParsedClass service = parse(
                "src/main/java/com/stardew/craft/time/settlement/PlayerDailySettlementService.java");
        MethodTree quest = service.methods("applyQuest").stream()
                .filter(method -> method.getBody() != null
                        && method.getBody().toString().contains("fireDayStarted"))
                .findFirst().orElseThrow();
        assertTrue(quest.getBody().toString().indexOf("markDailySettlementQuestApplied")
                < quest.getBody().toString().indexOf("fireDayStarted"));
        MethodTree mastery = service.methods("applyMastery").stream()
                .filter(method -> method.getBody() != null
                        && method.getBody().toString().contains("checkOnMorning"))
                .findFirst().orElseThrow();
        assertTrue(mastery.getBody().toString().indexOf("markDailySettlementMasteryApplied")
                < mastery.getBody().toString().indexOf("checkOnMorning"));
    }

    @Test
    void settlementReceiptsRoundTripAndRejectTheSameDayTwice() {
        UUID playerId = UUID.randomUUID();
        PlayerStardewData data = new PlayerStardewData(playerId);
        int absoluteDay = 226;
        List<OvernightSettlementPayload.ShippedItem> items = List.of(
                new OvernightSettlementPayload.ShippedItem(
                        new ItemStack(Items.DIAMOND, 2), 3, 60));

        assertTrue(data.recordOvernightShippingHistory(absoluteDay, items));
        assertFalse(data.recordOvernightShippingHistory(absoluteDay, items));
        assertTrue(data.markOvernightShippingOrdersApplied(absoluteDay));
        assertFalse(data.markOvernightShippingOrdersApplied(absoluteDay));
        assertTrue(data.markOvernightShippingMoneyApplied(absoluteDay));
        assertFalse(data.markOvernightShippingMoneyApplied(absoluteDay));
        assertTrue(data.markDailySettlementQuestApplied(absoluteDay));
        assertFalse(data.markDailySettlementQuestApplied(absoluteDay));
        assertTrue(data.markDailySettlementMasteryApplied(absoluteDay));
        assertFalse(data.markDailySettlementMasteryApplied(absoluteDay));

        PlayerStardewData restored = PlayerStardewData.fromNBT(data.toNBT(), playerId);
        assertEquals(2, restored.getItemsShippedCount("minecraft:diamond"));
        assertFalse(restored.recordOvernightShippingHistory(absoluteDay, items));
        assertTrue(restored.isOvernightShippingOrdersApplied(absoluteDay));
        assertTrue(restored.isOvernightShippingMoneyApplied(absoluteDay));
        assertTrue(restored.isDailySettlementQuestApplied(absoluteDay));
        assertTrue(restored.isDailySettlementMasteryApplied(absoluteDay));
    }

    @Test
    void frozenParticipantOwnsItsLedgerBeforeTheBarrierLockAndDuringPendingRecovery()
            throws Exception {
        UUID playerId = UUID.randomUUID();
        DailySettlementContext target = new DailySettlementContext(
                226, 3, 0, 2, 1560, false, List.of(playerId), Set.of());

        assertTrue(DailySettlementServices.ownsSettlement(
                playerId, Optional.of(target), false, false));
        assertTrue(DailySettlementServices.ownsSettlement(
                playerId, Optional.empty(), true, false));
        assertTrue(DailySettlementServices.ownsSettlement(
                playerId, Optional.empty(), false, true));
        assertFalse(DailySettlementServices.ownsSettlement(
                playerId, Optional.empty(), false, false));

        ParsedClass players = parse(
                "src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java");
        String login = players.method("onPlayerLogin", 1).getBody().toString();
        assertTrue(login.contains("DailySettlementServices.ownsSettlement"));
        assertTrue(login.indexOf("DailySettlementServices.ownsSettlement")
                < login.indexOf("OvernightSettlementTracker.consumePayload"));
    }

    @Test
    void dailyProcessCleanupRejectsTheWrongActiveLevelBeforeClosingItsScope() throws Exception {
        ParsedClass helper = parse("src/main/java/com/stardew/craft/farm/FarmDailyProcessHelper.java");
        String body = helper.method("endDailyProcess", 1).getBody().toString();

        int identityCheck = body.indexOf("dailySettlementLevel != level");
        int close = body.indexOf("scope.close()");
        assertTrue(identityCheck >= 0, "cleanup must compare ServerLevel by identity");
        assertTrue(identityCheck < close, "wrong-level rejection must happen before lease close");
    }

    @Test
    void prepareScopeCreatesWorldSnapshotsAsSeparateBudgetedItems() throws Exception {
        ParsedClass factory = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java");
        String source = source(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java");
        String scope = factory.method("createDailyProcessScope", 1).getBody().toString();
        String begin = factory.method("beginDailyProcess", 1).getBody().toString();
        String prepareOne = factory.method("prepareWorldSnapshot", 2).getBody().toString();

        assertTrue(source.contains(
                "case \"daily_process_scope\" -> createDailyProcessScope(context)"));
        assertTrue(scope.contains("DailySettlementWorkUnits.sequence"));
        assertTrue(scope.contains("daily_process_scope_begin"));
        assertTrue(scope.contains("snapshot_"));
        assertFalse(begin.contains("prepareWorldSnapshots"),
                "opening the daily scope must not build every snapshot in one atomic item");
        assertTrue(prepareOne.contains("preparedWorld.put"));
        assertTrue(prepareOne.contains("createWorldSnapshot"));
    }

    @Test
    void weatherResetSeparatesWeatherFromDeferredNpcCursor() throws Exception {
        ParsedClass factory = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java");
        String source = source(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java");
        String reset = factory.method("createWeatherAndNpcWorkUnit", 1).getBody().toString();

        assertTrue(source.contains(
                "case \"weather_npc_reset\" -> createWeatherAndNpcWorkUnit(context)"));
        assertTrue(reset.contains("DailySettlementWorkUnits.sequence"));
        assertTrue(reset.contains("weather_npc_reset_weather"));
        assertTrue(reset.contains("npc_daily_reset"));
        assertTrue(reset.contains("DailySettlementWorkUnits.deferred"));
    }

    @Test
    void reconnectReadyPublicationRestoresTheLockBeforeSendingTheRetainedPayload()
            throws Exception {
        ParsedClass events = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementEvents.java");
        String body = events.method("onPlayerLogin", 1).getBody().toString();

        int lock = body.indexOf("new OvernightBarrierPayload");
        int ready = body.indexOf("ready.payload()");
        assertTrue(lock >= 0);
        assertTrue(ready >= 0);
        assertTrue(lock < ready, "client must become LOCKED before it accepts retained READY");
    }

    @Test
    void reconnectAfterCompletedSettlementResendsWorldReadyAfterTheRetainedPayload()
            throws Exception {
        ParsedClass events = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementEvents.java");
        String body = events.method("onPlayerLogin", 1).getBody().toString();

        String worldReady = "new OvernightWorldReadyPayload";
        assertEquals(2, body.split(worldReady, -1).length - 1,
                "both cold-service recovery and live-service recovery must restore WORLD_READY");
        assertTrue(body.indexOf("recovered.payload()")
                        < body.indexOf(worldReady),
                "cold-service recovery must send the retained result before WORLD_READY");
        assertTrue(body.lastIndexOf("ready.payload()")
                        < body.lastIndexOf(worldReady),
                "live-service recovery must send the retained result before WORLD_READY");
        assertTrue(body.contains("!services.coordinator().isActive()"),
                "an active coordinator must retain ownership until global settlement completes");
    }

    @Test
    void cleanupAndPublicationCanUseRequiredAtomicWorkThatCannotBePermanentlySkipped() {
        DailySettlementWorkUnit required = DailySettlementWorkUnits.atomic(
                "required", () -> {}, () -> {}, Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, required.maxRetries());
    }

    @Test
    void weakServiceValuesDoNotStronglyRetainTheirMinecraftServerKey() throws Exception {
        String plan = source(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java");
        String players = source(
                "src/main/java/com/stardew/craft/time/settlement/PlayerDailySettlementService.java");
        String services = source(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementServices.java");
        String readyPublisher = source(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementReadyPublisher.java");

        assertTrue(plan.contains("WeakReference<MinecraftServer>"));
        assertTrue(players.contains("WeakReference<MinecraftServer>"));
        assertTrue(readyPublisher.contains("WeakReference<MinecraftServer>"));
        assertFalse(plan.contains("private final MinecraftServer server;"));
        assertFalse(players.contains("private final MinecraftServer server;"));
        assertFalse(services.contains("private final MinecraftServer server;"));
        assertFalse(readyPublisher.contains("private final MinecraftServer server;"));
    }

    @Test
    void cleanupClosesEveryUnclaimedSnapshotEvenWhenOneCloseFails() throws Exception {
        ParsedClass factory = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java");
        String cleanup = factory.method("cleanupDailyProcess", 0).getBody().toString();
        String closeAll = factory.method("closePreparedWorld", 0).getBody().toString();

        assertTrue(cleanup.contains("closePreparedWorld()"));
        int clearFrozen = cleanup.indexOf("clearFrozenFarms()");
        assertTrue(clearFrozen >= 0, "cleanup must explicitly release the frozen farm snapshot");
        assertTrue(clearFrozen < cleanup.indexOf("if (!dailyProcessActive)"),
                "plan-build cleanup must release a snapshot before the daily scope starts");
        assertTrue(closeAll.contains("for (DailySettlementWorkUnit work"));
        assertTrue(closeAll.contains("catch (RuntimeException | Error"));
        assertTrue(closeAll.contains("addSuppressed"));
    }

    @Test
    void planBuildFailureCleansFrozenFactoryStateAndSuppressesCleanupFailure() {
        class FailingSnapshotFactory implements DailySettlementPlanFactory.WorkUnitFactory {
            private int frozenDay = Integer.MIN_VALUE;
            private boolean failBuild = true;
            private RuntimeException cleanupFailure;

            @Override
            public DailySettlementWorkUnit create(
                    String name, DailySettlementContext context) {
                if (frozenDay != Integer.MIN_VALUE && frozenDay != context.absoluteDay()) {
                    throw new IllegalStateException("stale frozen day " + frozenDay);
                }
                frozenDay = context.absoluteDay();
                if (failBuild && name.equals("daily_process_scope")) {
                    throw new IllegalStateException("injected build failure");
                }
                return DailySettlementWorkUnits.atomic(name, () -> {}, () -> {});
            }

            @Override
            public void cleanup() {
                frozenDay = Integer.MIN_VALUE;
                if (cleanupFailure != null) {
                    throw cleanupFailure;
                }
            }
        }

        FailingSnapshotFactory workUnits = new FailingSnapshotFactory();
        DailySettlementCoordinator coordinator = new DailySettlementCoordinator(
                new BudgetedWorkRunner(() -> 0L), () -> 1_000_000L, () -> 64,
                new DailySettlementPlanFactory(workUnits),
                DailySettlementCoordinator.LifecycleListener.NOOP);
        IllegalStateException buildFailure = assertThrows(
                IllegalStateException.class, () -> coordinator.start(context(226, 3, 0, 2)));
        assertEquals("injected build failure", buildFailure.getMessage());
        assertEquals(Integer.MIN_VALUE, workUnits.frozenDay);

        workUnits.failBuild = false;
        assertTrue(coordinator.start(context(227, 3, 0, 3)));
        assertEquals(227, workUnits.frozenDay);
        coordinator.drain();
        workUnits.cleanup();

        workUnits.failBuild = true;
        RuntimeException cleanupFailure = new IllegalArgumentException("cleanup failed");
        workUnits.cleanupFailure = cleanupFailure;
        IllegalStateException withSuppressed = assertThrows(
                IllegalStateException.class, () -> coordinator.start(context(228, 3, 0, 4)));
        assertEquals(1, withSuppressed.getSuppressed().length);
        assertSame(cleanupFailure, withSuppressed.getSuppressed()[0]);
        assertFalse(withSuppressed == cleanupFailure);
    }

    @Test
    void virtualDayTimeAdvancesOnlyFromFinalDatePublication() throws Exception {
        ParsedClass dimension = parse(
                "src/main/java/com/stardew/craft/event/DimensionEventHandler.java");

        assertEquals(0, frequency(
                invocationNames(dimension.method("advanceToNextMorning", 3)),
                "setVirtualDayTime"));
        assertEquals(0, frequency(
                invocationNames(dimension.method("onSettlementDatePublished", 2)),
                "setVirtualDayTime"));
        assertEquals(1, frequency(
                invocationNames(dimension.method("publishSettlementVirtualTime", 2)),
                "setVirtualDayTime"));
    }

    @Test
    void stopDrainsBeforeCleanupAndRegistryRemoval() throws Exception {
        ParsedClass services = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementServices.java");
        List<String> stopCalls = invocationNames(services.method("stop", 0));
        List<String> removeCalls = invocationNames(services.method("remove", 1));
        List<String> removeRegisteredCalls = invocationNames(
                services.method("removeRegistered", 3));

        assertTrue(stopCalls.indexOf("drain") >= 0);
        assertTrue(stopCalls.indexOf("drain") < stopCalls.indexOf("clear"));
        assertTrue(removeCalls.contains("removeRegistered"));
        assertTrue(removeRegisteredCalls.indexOf("accept")
                < removeRegisteredCalls.indexOf("remove"));
    }

    @Test
    void logoutNeverRemovesOrCancelsTheSharedSettlementServices() throws Exception {
        ParsedClass players = parse(
                "src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java");
        String logout = players.method("onPlayerLogout", 1).getBody().toString();

        assertFalse(logout.contains("coordinator().cancel"));
        assertFalse(logout.contains("DailySettlementServices.remove"));
    }

    private static DailySettlementCoordinator.LifecycleListener listenerPublishing(
            DailySettlementBarrier barrier, UUID player, OvernightSettlementPayload payload) {
        return new DailySettlementCoordinator.LifecycleListener() {
            @Override
            public void phaseChanged(DailySettlementContext context, DailySettlementPhase phase) {
            }

            @Override
            public void itemFailure(DailySettlementContext context, String unitName,
                    String itemIdentity, int attempt, boolean permanent) {
            }

            @Override
            public void ready(DailySettlementContext context) {
                barrier.publishReady(player,
                        new DailySettlementBarrier.ReadyResult(context.absoluteDay(), payload));
            }
        };
    }

    private static DailySettlementWorkUnit readDate(
            String name, StardewTimeManager time, List<String> scoped) {
        return DailySettlementWorkUnits.atomic(name, () -> {
            readTargetDate(time);
            scoped.add(name);
        }, () -> {});
    }

    private static void readTargetDate(StardewTimeManager time) {
        assertEquals(3, time.getCurrentYear());
        assertEquals(0, time.getCurrentSeason());
        assertEquals(2, time.getCurrentDay());
        assertEquals(StardewTimeManager.MORNING_START, time.getCurrentTime());
    }

    private static PlayerStardewData unsettledPlayer(UUID playerId) {
        PlayerStardewData data = new PlayerStardewData(playerId);
        data.setMoney(500);
        data.setEnergy(10);
        data.setHealth(10);
        data.setToolBeingUpgraded("stardewcraft:copper_axe");
        data.setDaysLeftForToolUpgrade(2);
        data.addExperience(SkillType.FARMING, 2_150);
        assertEquals(5, data.getRawSkillLevel(SkillType.FARMING));
        return data;
    }

    private static AudiencePlayer audience(String name, boolean valley) {
        return new AudiencePlayer(UUID.nameUUIDFromBytes(name.getBytes()), valley);
    }

    private static FarmInstance createFarm(
            FarmInstanceRegistry registry, UUID ownerId, String ownerName) throws Exception {
        Method create = FarmInstanceRegistry.class.getDeclaredMethod(
                "createFarmAtDate", UUID.class, String.class, String.class,
                FarmType.class, int.class, int.class);
        create.setAccessible(true);
        return (FarmInstance) create.invoke(
                registry, ownerId, ownerName, ownerName + " Farm", FarmType.STANDARD, 225, 3);
    }

    private static Object invokeSettlement(
            Method method,
            DailySettlementContext context,
            UUID playerId,
            AtomicReference<PlayerDailySettlementService.PendingSettlement> progress,
            Object operations) throws Exception {
        try {
            return method.invoke(
                    null, context, playerId, progress.get(),
                    (Consumer<PlayerDailySettlementService.PendingSettlement>) progress::set,
                    operations);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static final class InternalFailureState {
        private final int absoluteDay;
        private final Set<String> receipts = new java.util.HashSet<>();
        private boolean failMoney = true;
        private boolean failShippingCleanup = true;
        private int money;
        private int historyApplications;
        private int orderApplications;
        private int moneyApplications;
        private int levelApplications;
        private int recipeApplications;
        private int questApplications;
        private int masteryApplications;
        private int shippingConsumes;
        private int passOutConsumes;
        private int levelClears;

        private InternalFailureState(int absoluteDay) {
            this.absoluteDay = absoluteDay;
        }

        private Object invoke(Object proxy, Method method, Object[] arguments) {
            return switch (method.getName()) {
                case "applyBase" -> once("base", () -> {});
                case "shippingPayload" -> new OvernightSettlementPayload(
                        absoluteDay,
                        List.of(new OvernightSettlementPayload.ShippedItem(
                                new ItemStack(Items.DIAMOND, 2), 3, 60)),
                        List.of());
                case "applyShippingHistory" -> once(
                        "history", () -> historyApplications++);
                case "applyShippingOrders" -> once(
                        "orders", () -> orderApplications++);
                case "applyShippingMoney" -> {
                    once("money", () -> {
                        moneyApplications++;
                        money += 120;
                    });
                    if (failMoney) {
                        failMoney = false;
                        throw new IllegalStateException("injected inside shipping money");
                    }
                    yield null;
                }
                case "applyLevels" -> {
                    once("levels", () -> levelApplications++);
                    yield List.of(new PlayerStardewData.SkillLevelUp(SkillType.FARMING, 5));
                }
                case "applyRecipes" -> once("recipes", () -> recipeApplications++);
                case "syncPlayer" -> null;
                case "applyQuest" -> once("quest", () -> questApplications++);
                case "applyMastery" -> once("mastery", () -> masteryApplications++);
                case "previousWeather" -> "Sun";
                case "passOutResult" -> new PassOutService.PassOutResult(
                        PassOutService.PassOutType.EXHAUSTION_2AM, 25, List.of(), absoluteDay);
                case "consumeShipping" -> {
                    once("consume_shipping", () -> shippingConsumes++);
                    if (failShippingCleanup) {
                        failShippingCleanup = false;
                        throw new IllegalStateException("injected inside final shipping cleanup");
                    }
                    yield null;
                }
                case "consumePassOut" -> once("consume_passout", () -> passOutConsumes++);
                case "clearLevels" -> once("clear_levels", () -> levelClears++);
                case "toString" -> "InternalFailureState";
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new AssertionError("Unexpected operation: " + method.getName());
            };
        }

        private Object once(String receipt, Runnable action) {
            if (receipts.add(receipt)) {
                action.run();
            }
            return null;
        }
    }

    private record AudiencePlayer(UUID id, boolean valley) {
    }

    private static class RecordingSettlementBackend
            implements PlayerDailySettlementService.SettlementBackend {
        private final Map<UUID, PlayerStardewData> playerData;
        protected boolean online;
        private boolean shippingLedgerAvailable = true;
        private boolean passOutAvailable = true;
        protected int settlementCalls;
        protected int persistenceWrites;
        private int questDayStartedCalls;
        private int masteryMorningCalls;

        private RecordingSettlementBackend(Map<UUID, PlayerStardewData> playerData) {
            this.playerData = playerData;
        }

        @Override
        public Optional<OvernightSettlementPayload> settleIfOnline(
                DailySettlementContext context, UUID playerId) {
            if (!online) {
                return Optional.empty();
            }
            settlementCalls++;
            PlayerStardewData data = playerData.get(playerId);
            if (shippingLedgerAvailable) {
                shippingLedgerAvailable = false;
                data.setMoney(data.getMoney() + 120);
                data.addTotalShippingGold(120);
            }
            passOutAvailable = false;
            data.setEnergy(data.getMaxEnergy());
            data.setHealth(data.getMaxHealth());
            data.setDaysLeftForToolUpgrade(data.getDaysLeftForToolUpgrade() - 1);
            List<PlayerStardewData.SkillLevelUp> levels = data.applyPendingSkillLevelUps();
            data.unlockRecipe("test_recipe");
            questDayStartedCalls++;
            masteryMorningCalls++;
            List<OvernightSettlementPayload.LevelUpData> payloadLevels = levels.stream()
                    .map(level -> new OvernightSettlementPayload.LevelUpData(
                            level.skill().getId(), level.newLevel()))
                    .toList();
            return Optional.of(new OvernightSettlementPayload(
                    context.absoluteDay(), List.of(), payloadLevels));
        }
    }

    private static final class FailingOnlineSettlementBackend
            implements PlayerDailySettlementService.SettlementBackend {
        private PlayerStardewData data;
        private boolean online;
        private boolean failAfterShipping = true;
        private int shippingApplications;
        private int levelApplications;
        private int recipeApplications;
        private int questDayStartedCalls;
        private int masteryMorningCalls;
        private int persistenceWrites;

        private FailingOnlineSettlementBackend(PlayerStardewData data) {
            this.data = data;
        }

        @Override
        public Optional<OvernightSettlementPayload> settleIfOnline(
                DailySettlementContext context, UUID playerId) {
            throw new AssertionError("the service must use the recoverable settlement path");
        }

        @Override
        public Optional<OvernightSettlementPayload> settleIfOnline(
                DailySettlementContext context,
                UUID playerId,
                PlayerDailySettlementService.PendingSettlement progress,
                Consumer<PlayerDailySettlementService.PendingSettlement> checkpoint) {
            if (!online) {
                return Optional.empty();
            }
            if (progress.stage() < 2) {
                shippingApplications++;
                data.setMoney(data.getMoney() + 120);
                data.addTotalShippingGold(120);
                checkpoint.accept(progress.atStage(2));
            }
            if (failAfterShipping) {
                throw new IllegalStateException("injected after shipping stage");
            }
            if (progress.stage() < 3) {
                levelApplications++;
                recipeApplications++;
                data.applyPendingSkillLevelUps();
                data.unlockRecipe("test_recipe");
                checkpoint.accept(progress.atStage(3));
            }
            if (progress.stage() < 5) {
                questDayStartedCalls++;
                checkpoint.accept(progress.atStage(5));
            }
            if (progress.stage() < 6) {
                masteryMorningCalls++;
                checkpoint.accept(progress.atStage(6));
            }
            return Optional.of(new OvernightSettlementPayload(context.absoluteDay(), List.of(), List.of()));
        }
    }

    private static final class FailingReadyCleanupBackend
            extends RecordingSettlementBackend {
        private int cleanupAttempts;

        private FailingReadyCleanupBackend(Map<UUID, PlayerStardewData> playerData) {
            super(playerData);
        }

        @Override
        public void finalizeSettlement(
                DailySettlementContext context,
                UUID playerId,
                PlayerDailySettlementService.PendingSettlement progress,
                Consumer<PlayerDailySettlementService.PendingSettlement> checkpoint) {
            cleanupAttempts++;
            if (progress.stage() < 10) {
                progress = progress.atStage(10);
                checkpoint.accept(progress);
            }
            if (cleanupAttempts == 1) {
                throw new IllegalStateException("injected final cleanup failure");
            }
            checkpoint.accept(progress.atStage(12));
        }
    }

    private static StardewTimeManager oldNight() {
        StardewTimeManager time = new StardewTimeManager();
        time.setCurrentYear(2);
        time.setCurrentSeason(3);
        time.setCurrentDay(28);
        time.setCurrentTime(1550);
        time.setDirty(false);
        return time;
    }

    private static void assertBackingDate(
            StardewTimeManager time, int year, int season, int day, int minute) throws Exception {
        assertEquals(year, intField(time, "currentYear"));
        assertEquals(season, intField(time, "currentSeason"));
        assertEquals(day, intField(time, "currentDay"));
        assertEquals(minute, intField(time, "currentTime"));
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static boolean booleanField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static DailySettlementContext context(int absoluteDay, int year, int season, int day) {
        return new DailySettlementContext(
                absoluteDay, year, season, day, 1560, day == 1, List.of(), Set.of());
    }

    private static int frequency(List<String> values, String expected) {
        return (int) values.stream().filter(expected::equals).count();
    }

    private static List<String> invocationNames(Tree tree) {
        List<String> result = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                String selected = node.getMethodSelect().toString();
                int separator = selected.lastIndexOf('.');
                result.add(separator < 0 ? selected : selected.substring(separator + 1));
                return super.visitMethodInvocation(node, unused);
            }
        }.scan(tree, null);
        return result;
    }

    private static List<String> invocationSelects(Tree tree) {
        List<String> result = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                result.add(node.getMethodSelect().toString());
                return super.visitMethodInvocation(node, unused);
            }
        }.scan(tree, null);
        return result;
    }

    private static ParsedClass parse(String relativePath) throws IOException {
        String source = source(relativePath);
        String filename = Path.of(relativePath).getFileName().toString();
        return parseSource(filename.substring(0, filename.length() - ".java".length()), source);
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(PROJECT.resolve(relativePath));
    }

    private static ParsedClass parseSource(String className, String source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        SimpleJavaFileObject file = new SimpleJavaFileObject(
                URI.create("string:///" + className + ".java"),
                javax.tools.JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        JavacTask task = (JavacTask) compiler.getTask(
                null, null, null, List.of("-proc:none"), null, List.of(file));
        CompilationUnitTree unit = task.parse().iterator().next();
        ClassTree type = unit.getTypeDecls().stream()
                .filter(ClassTree.class::isInstance)
                .map(ClassTree.class::cast)
                .filter(candidate -> candidate.getSimpleName().contentEquals(className))
                .findFirst()
                .orElseThrow();
        return new ParsedClass(type);
    }

    private record ParsedClass(ClassTree type) {
        List<MethodTree> methods(String name) {
            List<MethodTree> methods = new ArrayList<>();
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitMethod(MethodTree method, Void unused) {
                    if (method.getName().contentEquals(name)) {
                        methods.add(method);
                    }
                    return super.visitMethod(method, unused);
                }
            }.scan(type, null);
            return methods;
        }

        MethodTree method(String name, int parameterCount) {
            return methods(name).stream()
                    .filter(method -> method.getParameters().size() == parameterCount)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Missing method " + type.getSimpleName() + "." + name
                                    + "/" + parameterCount));
        }
    }
}
