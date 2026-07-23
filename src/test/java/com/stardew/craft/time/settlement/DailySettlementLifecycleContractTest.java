package com.stardew.craft.time.settlement;

import com.stardew.craft.event.DimensionEventHandler;
import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import com.stardew.craft.player.PlayerStardewData;
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

import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Field;
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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySettlementLifecycleContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir"));

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

        assertEquals(List.of("prepare", "world", "player", "publication"), scoped);
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
        DailySettlementWorkUnit publication = DailySettlementPlanFactory.publishDate(
                target,
                time,
                () -> {
                    virtualPublications.incrementAndGet();
                    DimensionEventHandler.publishSettlementVirtualTime(
                            time, target.absoluteDay());
                },
                () -> {
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
        assertBackingDate(time, 3, 0, 2, StardewTimeManager.MORNING_START);
        assertEquals((target.absoluteDay() - 1L) * 24_000L, time.getVirtualDayTime());
        assertEquals(1, virtualPublications.get());
        assertEquals(1, hookCalls.get());

        coordinator.tick();

        assertEquals(DailySettlementPhase.IDLE, coordinator.phase());
        assertBackingDate(time, 3, 0, 2, StardewTimeManager.MORNING_START);
        assertEquals((target.absoluteDay() - 1L) * 24_000L, time.getVirtualDayTime());
        assertEquals(1, virtualPublications.get());
        assertEquals(2, hookCalls.get());
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
        assertTrue(body.contains("Set.copyOf"));
        assertFalse(body.contains("currentDay++"));
        assertFalse(body.contains("currentDay ="));
        assertFalse(body.contains("currentSeason ="));
        assertFalse(body.contains("currentYear ="));
        assertFalse(body.contains("currentTime ="));
        assertFalse(body.contains("GrowthManager"));
        assertFalse(body.contains("WeatherManager"));
        assertFalse(body.contains("MailService"));
        assertFalse(body.contains("SpecialOrderManager"));
        assertFalse(body.contains("ShippingBinBlockEntity"));
    }

    @Test
    void sleepPassOutAndVanillaCompletionShareTheSingleAdvanceEntry() throws Exception {
        ParsedClass dimension = parse("src/main/java/com/stardew/craft/event/DimensionEventHandler.java");

        assertEquals(1, frequency(invocationNames(dimension.method("advanceToNextMorning", 3)),
                "advanceDayWithSleepTime"));
        assertTrue(invocationNames(dimension.method("requestPassOutAdvance", 1))
                .contains("advanceToNextMorning"));
        assertTrue(invocationNames(dimension.method("requestSleepAdvance", 3))
                .contains("advanceToNextMorning"));
        assertTrue(invocationNames(dimension.method("onSleepFinished", 1))
                .contains("advanceToNextMorning"));
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
        assertTrue(removeCalls.indexOf("stop") < removeCalls.indexOf("remove"));
    }

    @Test
    void servicesOwnOneWeakSynchronizedPerServerGraphWithoutCoordinatorSingleton() throws Exception {
        String source = source("src/main/java/com/stardew/craft/time/settlement/DailySettlementServices.java");
        ParsedClass services = parseSource("DailySettlementServices", source);

        assertTrue(source.contains("WeakHashMap<MinecraftServer, Services>"));
        assertTrue(services.method("get", 1).getModifiers().getFlags()
                .contains(javax.lang.model.element.Modifier.SYNCHRONIZED));
        assertTrue(services.method("find", 1).getModifiers().getFlags()
                .contains(javax.lang.model.element.Modifier.SYNCHRONIZED));
        assertTrue(services.method("remove", 1).getModifiers().getFlags()
                .contains(javax.lang.model.element.Modifier.SYNCHRONIZED));
        assertTrue(services.method("get", 1).getBody().toString().contains("computeIfAbsent"));
        assertFalse(services.method("find", 1).getBody().toString().contains("computeIfAbsent"));

        String coordinator = source("src/main/java/com/stardew/craft/time/settlement/DailySettlementCoordinator.java");
        assertFalse(coordinator.contains("static DailySettlementCoordinator"));
    }

    @Test
    void playerBatchDefersDisconnectedConsumptionAndPreservesOnlinePayloadOrder() throws Exception {
        ParsedClass service = parse(
                "src/main/java/com/stardew/craft/time/settlement/PlayerDailySettlementService.java");
        MethodTree create = service.method("createDailyWorkUnit", 1);
        MethodTree settle = service.method("settlePlayer", 2);
        MethodTree online = service.method("settleOnlinePlayer", 4);

        assertTrue(create.getBody().toString().contains("context.playerIds()"));
        assertTrue(settle.getBody().toString().contains("settleIfOnline(context, playerId)"));
        assertTrue(settle.getBody().toString().contains("pending.save(context, playerId)"));
        assertFalse(settle.getBody().toString().contains("consumePayload"));
        assertFalse(settle.getBody().toString().contains("consumePassOutResult"));
        assertTrue(settle.getBody().toString().contains("readyResults.put"));

        List<String> calls = invocationNames(online);
        assertTrue(calls.indexOf("peekPayload") >= 0);
        assertTrue(calls.indexOf("peekPayload") < calls.indexOf("recordOvernightShippedItems"));
        assertTrue(calls.indexOf("recordOvernightShippedItems")
                < calls.indexOf("buildPayload"));
        assertTrue(calls.indexOf("consumePayload") >= 0);
        assertTrue(calls.indexOf("buildPayload") < calls.indexOf("consumePayload"));
        assertTrue(calls.indexOf("applyPendingSkillLevelUpsForSettlement")
                < calls.indexOf("buildPayload"));
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
        assertEquals(2, backend.persistenceWrites);
        assertTrue(recovered.pendingSettlement(playerId).isEmpty());
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
        assertTrue(service.pendingSettlement(playerId).isEmpty());
        assertNull(barrier.readyResult(playerId, target.absoluteDay()));

        assertTrue(barrier.publishReady(
                playerId, service.readyResultOrCreate(target, playerId)));
        assertEquals(5, barrier.readyResult(playerId, target.absoluteDay())
                .payload().levelUps().size());
        service.onLogin(playerId, barrier);
        assertEquals(1, backend.settlementCalls);
    }

    @Test
    void persistedPendingSettlementRecoversWithoutALiveBarrierOrServiceGraph()
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
        assertTrue(recoveredService.onLogin(playerId, null).isEmpty());
        assertEquals(1, backend.settlementCalls);

        ParsedClass events = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementEvents.java");
        String login = events.method("onPlayerLogin", 1).getBody().toString();
        assertTrue(login.contains("recoverPending"));
        assertTrue(login.indexOf("new OvernightBarrierPayload")
                < login.indexOf("recovered.payload()"));
        assertFalse(login.contains("DailySettlementServices.get"));
    }

    @Test
    void productionAudienceSelectorsKeepLegacyMailValleyAndFarmScopeCoverage()
            throws Exception {
        AudiencePlayer overworldFarmMember = audience("overworld_farm", false);
        AudiencePlayer valleyWithoutFarm = audience("valley_no_farm", true);
        AudiencePlayer miningFarmMember = audience("mining_farm", false);
        AudiencePlayer valleyFarmMember = audience("valley_farm", true);
        List<AudiencePlayer> online = List.of(
                overworldFarmMember, valleyWithoutFarm, miningFarmMember, valleyFarmMember);

        assertEquals(online, DailySettlementPlanFactory.mailAudience(online));
        assertEquals(
                List.of(valleyWithoutFarm, valleyFarmMember),
                DailySettlementPlanFactory.valleyAudience(online, AudiencePlayer::valley));
        assertEquals(
                Set.of(
                        overworldFarmMember.id(), valleyWithoutFarm.id(),
                        miningFarmMember.id(), valleyFarmMember.id()),
                DailySettlementPlanFactory.dailyScopeAudience(online, AudiencePlayer::id));

        ParsedClass factory = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java");
        String mail = factory.method("mail", 1).getBody().toString();
        String valley = factory.method("onlineValleyPlayers", 0).getBody().toString();
        String scope = factory.method("beginDailyProcess", 1).getBody().toString();
        assertTrue(mail.contains("mailAudience"));
        assertFalse(mail.contains("context.playerIds()"));
        assertTrue(valley.contains("valleyAudience"));
        assertTrue(scope.contains("dailyScopeAudience"));
        assertFalse(scope.contains("context.playerIds()"));
    }

    @Test
    void registeredFarmOwnersDriveFarmCursorAndCavesOutsideSettlementParticipants()
            throws Exception {
        ParsedClass time = parse("src/main/java/com/stardew/craft/time/StardewTimeManager.java");
        String advance = time.method("advanceDayWithSleepTime", 1).getBody().toString();
        assertTrue(advance.contains("getAllFarms()"),
                "farm owner scope must include every registered farm owner");

        ParsedClass factory = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java");
        String cursor = factory.method("updateFarmCursor", 1).getBody().toString();
        assertTrue(cursor.contains("getAllFarms()"),
                "farm cursor must derive its owners from the registry");
        assertFalse(cursor.contains("context.farmOwnerIds()"),
                "farm cursor must not reuse participant-derived owners");

        ParsedClass caves = parse(
                "src/main/java/com/stardew/craft/manager/FarmCaveDailyService.java");
        String caveWork = caves.method("createDailyWorkUnit", 2).getBody().toString();
        assertTrue(caveWork.contains("getAllFarms()"),
                "farm caves must include registered farms even without participants");
        assertFalse(caveWork.contains("context.playerIds()"),
                "farm caves must not be filtered by settlement participants");
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

        PlayerStardewData restored = PlayerStardewData.fromNBT(data.toNBT(), playerId);
        assertEquals(2, restored.getPendingDailySettlement().orElseThrow().stage());
        playerData.put(playerId, restored);
        backend.data = restored;
        PlayerDailySettlementService recovered = new PlayerDailySettlementService(
                backend,
                new PlayerDailySettlementService.PlayerDataPendingStore(
                        playerData::get, () -> backend.persistenceWrites++));
        backend.failAfterShipping = false;
        assertTrue(recovered.onLogin(playerId, null).isPresent());
        assertTrue(recovered.onLogin(playerId, null).isEmpty(),
                "reconnect retry must be idempotent after completion");
        assertTrue(recovered.pendingSettlement(playerId).isEmpty());
        assertEquals(1, backend.shippingApplications);
        assertEquals(1, backend.levelApplications);
        assertEquals(1, backend.recipeApplications);
        assertEquals(1, backend.questDayStartedCalls);
        assertEquals(1, backend.masteryMorningCalls);
        assertEquals(620, restored.getMoney());
        assertEquals(120, restored.getTotalShippingGold());
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

        assertTrue(service.pendingSettlement(playerId).isEmpty());
        assertEquals(1, backend.shippingApplications);
        assertEquals(1, backend.levelApplications);
        assertEquals(1, backend.recipeApplications);
        assertEquals(1, backend.questDayStartedCalls);
        assertEquals(1, backend.masteryMorningCalls);
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
    void prepareScopeCreatesWorldSnapshotsBeforeTheBarrierCanLock() throws Exception {
        ParsedClass factory = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java");
        List<String> calls = invocationNames(factory.method("beginDailyProcess", 1));

        assertTrue(calls.indexOf("beginDailyProcess") >= 0);
        assertTrue(calls.indexOf("beginDailyProcess") < calls.indexOf("prepareWorldSnapshots"));
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

        assertTrue(plan.contains("WeakReference<MinecraftServer>"));
        assertTrue(players.contains("WeakReference<MinecraftServer>"));
        assertTrue(services.contains("WeakReference<MinecraftServer>"));
        assertFalse(plan.contains("private final MinecraftServer server;"));
        assertFalse(players.contains("private final MinecraftServer server;"));
        assertFalse(services.contains("private final MinecraftServer server;"));
    }

    @Test
    void cleanupClosesEveryUnclaimedSnapshotEvenWhenOneCloseFails() throws Exception {
        ParsedClass factory = parse(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java");
        String cleanup = factory.method("cleanupDailyProcess", 0).getBody().toString();
        String closeAll = factory.method("closePreparedWorld", 0).getBody().toString();

        assertTrue(cleanup.contains("closePreparedWorld()"));
        assertTrue(closeAll.contains("for (DailySettlementWorkUnit work"));
        assertTrue(closeAll.contains("catch (RuntimeException | Error"));
        assertTrue(closeAll.contains("addSuppressed"));
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

        assertTrue(stopCalls.indexOf("drain") >= 0);
        assertTrue(stopCalls.indexOf("drain") < stopCalls.indexOf("clear"));
        assertTrue(removeCalls.indexOf("stop") < removeCalls.indexOf("remove"));
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

    private record AudiencePlayer(UUID id, boolean valley) {
    }

    private static final class RecordingSettlementBackend
            implements PlayerDailySettlementService.SettlementBackend {
        private final Map<UUID, PlayerStardewData> playerData;
        private boolean online;
        private boolean shippingLedgerAvailable = true;
        private boolean passOutAvailable = true;
        private int settlementCalls;
        private int persistenceWrites;
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
        MethodTree method(String name, int parameterCount) {
            List<MethodTree> methods = new ArrayList<>();
            new TreeScanner<Void, Void>() {
                @Override
                public Void visitMethod(MethodTree method, Void unused) {
                    methods.add(method);
                    return super.visitMethod(method, unused);
                }
            }.scan(type, null);
            return methods.stream()
                    .filter(method -> method.getName().contentEquals(name))
                    .filter(method -> method.getParameters().size() == parameterCount)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "Missing method " + type.getSimpleName() + "." + name
                                    + "/" + parameterCount));
        }
    }
}
