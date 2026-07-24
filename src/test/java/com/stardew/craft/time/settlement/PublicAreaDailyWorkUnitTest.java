package com.stardew.craft.time.settlement;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class PublicAreaDailyWorkUnitTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir", "."));
    private static final Path MANAGERS = PROJECT.resolve("src/main/java/com/stardew/craft/manager");
    private static final long WORLD_SEED = 0x51A7_F13D_24B6_8ACEL;
    private static final int ABSOLUTE_DAY = 137;

    @Test
    void rectangleCursorVisitsExactFirstAndLastColumns() throws Exception {
        List<String> visited = new ArrayList<>();
        DailySettlementWorkUnit unit = rectangle(
                -2, 7, 1, 9,
                (x, z) -> visited.add(x + "," + z),
                () -> false,
                () -> {});

        drainWithBudget(unit, 64);

        assertEquals(12, visited.size());
        assertEquals("-2,7", visited.getFirst());
        assertEquals("1,9", visited.getLast());
        assertEquals(List.of(
                "-2,7", "-1,7", "0,7", "1,7",
                "-2,8", "-1,8", "0,8", "1,8",
                "-2,9", "-1,9", "0,9", "1,9"), visited);
    }

    @Test
    void rectangleCursorResumesAcrossTicksWithoutRepeatingAColumn() throws Exception {
        List<String> visited = new ArrayList<>();
        DailySettlementWorkUnit unit = rectangle(
                10, -4, 12, -2,
                (x, z) -> visited.add(x + "," + z),
                () -> false,
                () -> {});

        runBudget(unit, 2);
        assertEquals(List.of("10,-4", "11,-4"), visited);
        runBudget(unit, 1);
        assertEquals(List.of("10,-4", "11,-4", "12,-4"), visited);
        runBudget(unit, 3);
        runBudget(unit, 3);
        unit.close();

        assertTrue(unit.isComplete());
        assertEquals(9, visited.size());
        assertEquals(9, new HashSet<>(visited).size());
    }

    @Test
    void dailyCapSurvivesTickBoundariesAndClosesTheChildEarly() throws Exception {
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        DailySettlementWorkUnit rectangle = rectangle(
                0, 0, 99, 99,
                (x, z) -> accepted.incrementAndGet(),
                () -> accepted.get() >= 3,
                closes::incrementAndGet);
        DailySettlementWorkUnit sequence = DailySettlementWorkUnits.sequence(
                "cap_test", List.of(rectangle), () -> {});

        runBudget(sequence, 1);
        runBudget(sequence, 1);
        assertFalse(sequence.isComplete());
        runBudget(sequence, 1);

        assertEquals(3, accepted.get());
        assertTrue(sequence.isComplete());
        assertEquals(1, closes.get(), "sequence must close a child that reaches its cap");
        sequence.close();
        rectangle.close();
        assertEquals(1, closes.get(), "cleanup must be idempotent");
    }

    @Test
    void deterministicPositionOutputDoesNotDependOnItemBudget() throws Exception {
        assertEquals(runDeterministicScan(1), runDeterministicScan(7));
        assertEquals(runDeterministicScan(7), runDeterministicScan(31));
    }

    @Test
    void explicitLegacyDatesOverrideOnlyTheCapturedDate() throws Exception {
        UUID player = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID owner = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        DailySettlementContext base = new DailySettlementContext(
                117, 2, 0, 5, 600, false, List.of(player), Set.of(owner));

        DailySettlementContext summer = invokeContextOverride("withSeason", base, 1);
        assertEquals(145, summer.absoluteDay());
        assertEquals(2, summer.year());
        assertEquals(1, summer.season());
        assertEquals(5, summer.day());
        assertEquals(base.playerIds(), summer.playerIds());
        assertEquals(base.farmOwnerIds(), summer.farmOwnerIds());

        DailySettlementContext yearFour = invokeContextOverride("withYear", base, 4);
        assertEquals(341, yearFour.absoluteDay());
        assertEquals(4, yearFour.year());
        assertEquals(0, yearFour.season());
        assertEquals(base.playerIds(), yearFour.playerIds());
        assertEquals(base.farmOwnerIds(), yearFour.farmOwnerIds());
    }

    @Test
    void explicitSeasonAndYearArgumentsFlowIntoProductionFactories() throws IOException {
        assertLegacyDateOverride(
                "ForageSpawnService", "onNewDay", "season", "withSeason", "createDailyWorkUnit");
        assertLegacyDateOverride(
                "ForageSpawnService", "onNewDayForestFarms", "season", "withSeason",
                "createForestFarmDailyWorkUnit");
        assertLegacyDateOverride(
                "ArtifactSpotSpawnService", "onNewDay", "season", "withSeason",
                "createDailyWorkUnit");
        assertLegacyDateOverride(
                "QuarrySpawnService", "onNewDay", "year", "withYear", "createDailyWorkUnit");

        assertInitialEntrypointForwards("ForageSpawnService", "season");
        assertInitialEntrypointForwards("ArtifactSpotSpawnService", "season");
        MethodTree quarryInitial = parse("QuarrySpawnService.java", "QuarrySpawnService")
                .method("ensureInitialSpawn", 2);
        assertTrue(quarryInitial.toString().contains("year"));
    }

    @Test
    void farmCaveLegacyEntrypointCapturesOnlinePlayersBeforeCreatingWork() throws IOException {
        ParsedClass caves = parse("FarmCaveDailyService.java", "FarmCaveDailyService");
        MethodTree legacy = caves.method("onNewDay", 1);
        MethodTree capture = caves.method("captureLegacyContext", 1);
        MethodInvocationTree create = invocationsNamed(legacy, "createDailyWorkUnit").getFirst();

        assertEquals("captureLegacyContext(level)", create.getArguments().get(1).toString());
        assertEquals(1, invocationsNamed(capture, "getPlayers").size());
        assertTrue(invocationsNamed(capture, "getUUID").size() >= 1);
        assertTrue(capture.toString().contains("withPlayers"));
        assertFalse(capture.toString().contains("List.of()"));
    }

    @Test
    void forageProductionAttemptsResumeAndStayBudgetDeterministic() throws Exception {
        AttemptRun oneAtATime = runForageAttempts(1);
        AttemptRun batched = runForageAttempts(7);

        assertEquals(oneAtATime.identities(), batched.identities());
        assertEquals(oneAtATime.outputs(), batched.outputs());
        assertEquals(List.of("0:2", "1:2", "2:2"), oneAtATime.outputs());
        assertEquals(9, oneAtATime.identities().size());
    }

    @Test
    void artifactProductionCapSurvivesTicksAndIsBudgetDeterministic() throws Exception {
        AttemptRun oneAtATime = runArtifactCap(1);
        AttemptRun batched = runArtifactCap(11);

        assertEquals(oneAtATime.identities(), batched.identities());
        assertEquals(oneAtATime.outputs(), batched.outputs());
        assertEquals(4, oneAtATime.outputs().size());
        assertTrue(oneAtATime.identities().size() < 100,
                "production cap must stop the large rectangle early");
    }

    @Test
    void productionStateMachinesCloseExactlyOnceOnCompleteCapAndAbort() throws Exception {
        AtomicInteger normalChildClose = new AtomicInteger();
        AtomicInteger normalParentClose = new AtomicInteger();
        DailySettlementWorkUnit normalChild = forageAttempts(
                1, 30, (slot, attempt) -> true, normalChildClose::incrementAndGet);
        DailySettlementWorkUnit normalParent = DailySettlementWorkUnits.sequence(
                "normal_parent", List.of(normalChild), normalParentClose::incrementAndGet);
        normalParent.runNext();
        assertTrue(normalParent.isComplete());
        normalParent.close();
        normalParent.close();
        assertEquals(1, normalChildClose.get());
        assertEquals(1, normalParentClose.get());

        AtomicInteger capChildClose = new AtomicInteger();
        DailySettlementWorkUnit capChild = cappedRectangle(
                0, 0, 99, 99, 2, (x, z) -> true, capChildClose::incrementAndGet);
        DailySettlementWorkUnit capParent = DailySettlementWorkUnits.sequence(
                "cap_parent", List.of(capChild), () -> {});
        runBudget(capParent, 1);
        runBudget(capParent, 1);
        assertTrue(capParent.isComplete());
        assertEquals(1, capChildClose.get());
        capParent.close();
        assertEquals(1, capChildClose.get());

        AtomicInteger abortChildClose = new AtomicInteger();
        AtomicInteger abortParentClose = new AtomicInteger();
        DailySettlementWorkUnit abortChild = forageAttempts(
                2, 30, (slot, attempt) -> false, abortChildClose::incrementAndGet);
        DailySettlementWorkUnit abortParent = DailySettlementWorkUnits.sequence(
                "abort_parent", List.of(abortChild), abortParentClose::incrementAndGet);
        abortParent.runNext();
        assertFalse(abortParent.isComplete());
        abortParent.close();
        abortParent.close();
        assertEquals(1, abortChildClose.get());
        assertEquals(1, abortParentClose.get());
    }

    @Test
    void forageSkipAdvancesAttemptThenSlotAndEventuallyCompletes() throws Exception {
        DailySettlementWorkUnit unit = forageAttempts(
                2, 2, (slot, attempt) -> false, () -> {});

        assertEquals("test_forage:0:0", unit.currentItemIdentity());
        unit.skipFailedItem();
        assertEquals("test_forage:0:1", unit.currentItemIdentity());
        unit.skipFailedItem();
        assertEquals("test_forage:1:0", unit.currentItemIdentity());
        unit.skipFailedItem();
        assertEquals("test_forage:1:1", unit.currentItemIdentity());
        unit.skipFailedItem();
        assertTrue(unit.isComplete());
        assertEquals("test_forage", unit.currentItemIdentity());
        unit.close();
    }

    @Test
    void cappedRectangleSkipMovesCursorWithoutConsumingPlacementCap() throws Exception {
        AtomicInteger placements = new AtomicInteger();
        DailySettlementWorkUnit unit = cappedRectangle(
                4, 8, 6, 8, 1,
                (x, z) -> {
                    placements.incrementAndGet();
                    return true;
                },
                () -> {});

        assertEquals("test_artifact_cap:4,8", unit.currentItemIdentity());
        unit.skipFailedItem();
        assertFalse(unit.isComplete(), "skip must not consume the placement cap");
        assertEquals("test_artifact_cap:5,8", unit.currentItemIdentity());
        unit.skipFailedItem();
        assertEquals("test_artifact_cap:6,8", unit.currentItemIdentity());
        unit.skipFailedItem();
        assertTrue(unit.isComplete());
        assertEquals(0, placements.get(), "skip must not invoke or count placement");
        unit.close();
    }

    @Test
    void decayingAttemptSkipAdvancesIdentityAndAppliesWinterDecay() throws Exception {
        List<String> observations = new ArrayList<>();
        DailySettlementWorkUnit unit = decayingAttempts(
                true,
                (cursor, chance) -> {
                    observations.add(cursor + ":" + chance);
                    return cursor == 1;
                },
                () -> {});

        assertEquals("test_decay:0", unit.currentItemIdentity());
        unit.skipFailedItem();
        assertEquals("test_decay:1", unit.currentItemIdentity());
        unit.runNext();
        assertEquals("test_decay:2", unit.currentItemIdentity());
        unit.runNext();

        assertTrue(unit.isComplete());
        assertEquals("test_decay", unit.currentItemIdentity());
        assertEquals(2, observations.size());
        assertEquals(1, Integer.parseInt(observations.get(0).split(":")[0]));
        assertEquals(0.85D, Double.parseDouble(observations.get(0).split(":")[1]), 0.0000001D);
        assertEquals(2, Integer.parseInt(observations.get(1).split(":")[0]));
        assertEquals(0.7375D, Double.parseDouble(observations.get(1).split(":")[1]), 0.0000001D);
        unit.close();
    }

    @Test
    void forageAndArtifactCallTheTestedProductionStateFactories() throws IOException {
        assertReachableCall("ForageSpawnService", "createDailyWorkUnit", "forageAttempts");
        assertReachableCall("ForageSpawnService", "createForestFarmDailyWorkUnit", "forageAttempts");
        assertReachableCall("ArtifactSpotSpawnService", "createDailyWorkUnit", "cappedRectangle");
    }

    @Test
    void coalForestInitializationMarksOnlyACompletedAttemptAndAlwaysReleasesChunks()
            throws Exception {
        ParsedClass coal = parse("CoalForestClumpSpawnService.java", "CoalForestClumpSpawnService");
        MethodTree initial = coal.method("ensureInitialSpawn", 1);
        List<TryTree> tries = scan(initial, TryTree.class);
        assertEquals(1, tries.size());
        TryTree guarded = tries.getFirst();
        assertTrue(invocationsNamed(guarded.getFinallyBlock(), "releaseRegionChunks").size() == 1);
        assertEquals(1, invocationsNamed(initial, "runInitialSpawn").size());

        List<IfTree> completionGuards = scan(initial, IfTree.class).stream()
                .filter(candidate -> invocationsNamed(candidate.getThenStatement(), "setInitialized").size() == 1)
                .toList();
        assertEquals(1, completionGuards.size(), "initialization mark must be success-guarded");
        assertTrue(completionGuards.getFirst().getCondition().toString().contains("initialSpawnComplete"));

        Method decision = Class.forName("com.stardew.craft.manager.CoalForestClumpSpawnService")
                .getDeclaredMethod("initialSpawnComplete", int.class);
        decision.setAccessible(true);
        assertEquals(false, decision.invoke(null, 0));
        assertEquals(true, decision.invoke(null, 1));
    }

    @Test
    void rectangleFactoryUsesAnIntegerCursorAndDoesNotMaterializeBlockPositions() throws IOException {
        ParsedClass helper = parse("PublicAreaDailyWorkUnits.java", "PublicAreaDailyWorkUnits");
        MethodTree rectangle = helper.method("rectangle", 8);
        String body = rectangle.getBody().toString();

        assertTrue(body.contains("int width = maxX - minX + 1"));
        assertTrue(body.contains("int x = minX + cursor % width"));
        assertTrue(body.contains("int z = minZ + cursor / width"));
        assertTrue(scan(rectangle, NewClassTree.class).stream()
                        .noneMatch(created -> created.getIdentifier().toString().contains("BlockPos")),
                "rectangle creation must not materialize BlockPos entries");
        assertFalse(body.contains("IntStream"));
    }

    @Test
    void legacyEntrypointsDrainTheirProductionFactories() throws IOException {
        assertLegacyDrain("ForageSpawnService", "onNewDay", 2, "createDailyWorkUnit");
        assertLegacyDrain("ForageSpawnService", "onNewDayForestFarms", 2,
                "createForestFarmDailyWorkUnit");
        assertLegacyDrain("ArtifactSpotSpawnService", "onNewDay", 2, "createDailyWorkUnit");
        assertLegacyDrain("QuarrySpawnService", "onNewDay", 2, "createDailyWorkUnit");
        assertLegacyDrain("CoalForestClumpSpawnService", "onNewDay", 1, "createDailyWorkUnit");
        assertLegacyDrain("FarmCaveDailyService", "onNewDay", 1, "createDailyWorkUnit");
    }

    @Test
    void publicAreaFactoriesReachTheSharedRectangleCursor() throws IOException {
        assertReachableCall("ForageSpawnService", "createDailyWorkUnit", "rectangle");
        assertReachableCall("ForageSpawnService", "createForestFarmDailyWorkUnit", "rectangle");
        assertReachableCall("ArtifactSpotSpawnService", "createDailyWorkUnit", "rectangle");
        assertReachableCall("CoalForestClumpSpawnService", "createDailyWorkUnit", "rectangle");
    }

    @Test
    void dailyProductionGraphsUseStableDailyRandomInsteadOfLevelRandom() throws IOException {
        assertDailyRandom("ForageSpawnService", "createDailyWorkUnit");
        assertDailyRandom("ForageSpawnService", "createForestFarmDailyWorkUnit");
        assertDailyRandom("ArtifactSpotSpawnService", "createDailyWorkUnit");
        assertDailyRandom("QuarrySpawnService", "createDailyWorkUnit");
        assertDailyRandom("CoalForestClumpSpawnService", "createDailyWorkUnit");
        assertDailyRandom("FarmCaveDailyService", "createDailyWorkUnit");
    }

    @Test
    void forageSnapshotsZonesAndEligibleForestFarmsOnlyOnce() throws IOException {
        ParsedClass forage = parse("ForageSpawnService.java", "ForageSpawnService");
        MethodTree publicFactory = forage.method("createDailyWorkUnit", 2);
        MethodTree forestFactory = forage.method("createForestFarmDailyWorkUnit", 2);

        assertEquals(1, invocationsNamed(publicFactory, "runtimeZones").size());
        assertEquals(1, invocationsNamed(forestFactory, "getAllFarms").size());
        assertTrue(invocationsNamed(forestFactory, "cursor").size()
                        + invocationsNamed(forestFactory, "sequence").size() > 0);
        assertTrue(forestFactory.toString().contains("FOREST"));
    }

    @Test
    void quarryAndCoalForestItemsPerformOneAttemptWithoutLooping() throws IOException {
        assertSingleAttemptItem("QuarrySpawnService", "processDailyAttempt", "trySpawnOne");
        assertSingleAttemptItem("CoalForestClumpSpawnService", "processStumpAttempt", "tryPlaceAt");

        ParsedClass quarry = parse("QuarrySpawnService.java", "QuarrySpawnService");
        MethodTree factory = quarry.method("createDailyWorkUnit", 2);
        assertTrue(factory.toString().contains("Math.min(16, 5 + context.year() * 2)"));
    }

    @Test
    void farmCavesSnapshotEligibilityAndProcessExactlyOneFarmPerItem() throws IOException {
        ParsedClass caves = parse("FarmCaveDailyService.java", "FarmCaveDailyService");
        MethodTree factory = caves.method("createDailyWorkUnit", 3);
        MethodTree item = caves.method("processFarmCave", -1);

        assertEquals(0, invocationsNamed(factory, "playerIds").size());
        assertEquals(0, invocationsNamed(factory, "getAllFarms").size());
        assertEquals(1, invocationsNamed(factory, "farmOwnerIds").size());
        assertTrue(factory.toString().contains("frozenFarms"));
        assertEquals(1, invocationsNamed(factory, "cursor").size());
        assertTrue(factory.toString().contains("FarmCaveDailyEntry"));
        assertTrue(invocationsNamed(factory, "processFarmCave").size() == 1);
        assertFalse(hasLoop(item), "the cursor item delegates one snapshotted farm without a farm loop");
        assertTrue(invocationsNamed(item, "processFruitBats").size()
                + invocationsNamed(item, "processMushrooms").size() >= 2);
    }

    @Test
    void creatingDailyUnitsCannotSynchronouslyLoadAreaChunks() throws IOException {
        for (String[] contract : List.of(
                new String[]{"ForageSpawnService", "createDailyWorkUnit"},
                new String[]{"ForageSpawnService", "createForestFarmDailyWorkUnit"},
                new String[]{"ArtifactSpotSpawnService", "createDailyWorkUnit"},
                new String[]{"QuarrySpawnService", "createDailyWorkUnit"},
                new String[]{"CoalForestClumpSpawnService", "createDailyWorkUnit"},
                new String[]{"FarmCaveDailyService", "createDailyWorkUnit"})) {
            ParsedClass parsed = parse(contract[0] + ".java", contract[0]);
            Set<MethodTree> graph = reachableMethods(parsed, parsed.method(contract[1], 2));
            assertTrue(graph.stream().flatMap(method -> invocations(method).stream())
                            .noneMatch(call -> methodName(call).equals("getChunk")
                                    || methodName(call).equals("setChunkForced")),
                    contract[0] + " daily graph must not synchronously acquire chunks");
        }
    }

    @Test
    void allDailyWorldAccessUsesTheNonblockingChunkNowGate() throws IOException {
        ParsedClass helper = parse("PublicAreaDailyWorkUnits.java", "PublicAreaDailyWorkUnits");
        MethodTree gate = helper.method("isChunkLoadedNow", 3);
        assertEquals(1, invocationsNamed(gate, "getChunkNow").size());
        assertTrue(invocationsNamed(gate, "hasChunk").isEmpty());

        for (String[] contract : List.of(
                new String[]{"ForageSpawnService", "createDailyWorkUnit"},
                new String[]{"ForageSpawnService", "createForestFarmDailyWorkUnit"},
                new String[]{"ArtifactSpotSpawnService", "createDailyWorkUnit"},
                new String[]{"QuarrySpawnService", "createDailyWorkUnit"},
                new String[]{"CoalForestClumpSpawnService", "createDailyWorkUnit"},
                new String[]{"FarmCaveDailyService", "createDailyWorkUnit"})) {
            ParsedClass parsed = parse(contract[0] + ".java", contract[0]);
            Set<MethodTree> graph = reachableMethods(parsed, parsed.method(contract[1], 2));
            List<MethodInvocationTree> calls = graph.stream()
                    .flatMap(method -> invocations(method).stream())
                    .toList();
            assertTrue(calls.stream().noneMatch(call -> methodName(call).equals("hasChunk")),
                    contract[0] + " daily graph must not use the potentially-blocking hasChunk gate");
            assertTrue(calls.stream().anyMatch(call -> methodName(call).equals("isChunkLoadedNow")),
                    contract[0] + " daily graph must guard world access with getChunkNow");
        }
    }

    private static List<String> runDeterministicScan(int budget) throws Exception {
        List<String> output = new ArrayList<>();
        DailySettlementWorkUnit unit = rectangle(
                -8, 20, 9, 35,
                (x, z) -> {
                    double roll = DailySettlementRandom.forPosition(
                            WORLD_SEED, ABSOLUTE_DAY, "public_area_test", new BlockPos(x, 0, z))
                            .nextDouble();
                    if (roll < 0.2D) {
                        output.add(x + "," + z);
                    }
                },
                () -> false,
                () -> {});
        drainWithBudget(unit, budget);
        return output;
    }

    private static DailySettlementContext invokeContextOverride(
            String methodName,
            DailySettlementContext base,
            int value) throws Exception {
        Method method;
        try {
            method = DailySettlementContextFactory.class.getMethod(
                    methodName, DailySettlementContext.class, int.class);
        } catch (NoSuchMethodException missing) {
            fail("production context override is missing: " + methodName, missing);
            return null;
        }
        return (DailySettlementContext) method.invoke(null, base, value);
    }

    private static AttemptRun runForageAttempts(int budget) throws Exception {
        List<String> identities = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        DailySettlementWorkUnit unit = forageAttempts(
                3,
                30,
                (slot, attempt) -> {
                    if (attempt == 2) {
                        outputs.add(slot + ":" + attempt);
                        return true;
                    }
                    return false;
                },
                () -> {});
        try (unit) {
            while (!unit.isComplete()) {
                for (int item = 0; item < budget && !unit.isComplete(); item++) {
                    identities.add(unit.currentItemIdentity());
                    unit.runNext();
                }
            }
        }
        return new AttemptRun(List.copyOf(identities), List.copyOf(outputs));
    }

    private static AttemptRun runArtifactCap(int budget) throws Exception {
        List<String> identities = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        DailySettlementWorkUnit unit = cappedRectangle(
                -20,
                30,
                29,
                79,
                4,
                (x, z) -> {
                    boolean accepted = DailySettlementRandom.forPosition(
                                    WORLD_SEED,
                                    ABSOLUTE_DAY,
                                    "artifact_state_test",
                                    new BlockPos(x, 0, z))
                            .nextDouble() < 0.35D;
                    if (accepted) {
                        outputs.add(x + "," + z);
                    }
                    return accepted;
                },
                () -> {});
        try (unit) {
            while (!unit.isComplete()) {
                for (int item = 0; item < budget && !unit.isComplete(); item++) {
                    identities.add(unit.currentItemIdentity());
                    unit.runNext();
                }
            }
        }
        return new AttemptRun(List.copyOf(identities), List.copyOf(outputs));
    }

    private static DailySettlementWorkUnit forageAttempts(
            int slots,
            int maxAttempts,
            IndexedAttempt operation,
            Runnable onClose) throws Exception {
        Class<?> helper = productionHelper();
        Class<?> operationType = nestedType(helper, "IndexedAttempt");
        Object operationProxy = Proxy.newProxyInstance(
                helper.getClassLoader(),
                new Class<?>[]{operationType},
                (proxy, method, arguments) -> method.getName().equals("trySpawn")
                        ? operation.trySpawn((int) arguments[0], (int) arguments[1])
                        : null);
        Method factory = helper.getMethod(
                "forageAttempts",
                String.class,
                int.class,
                int.class,
                operationType,
                Runnable.class);
        return (DailySettlementWorkUnit) factory.invoke(
                null, "test_forage", slots, maxAttempts, operationProxy, onClose);
    }

    private static DailySettlementWorkUnit cappedRectangle(
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            int cap,
            PlacementAttempt operation,
            Runnable onClose) throws Exception {
        Class<?> helper = productionHelper();
        Class<?> operationType = nestedType(helper, "PlacementAttempt");
        Object operationProxy = Proxy.newProxyInstance(
                helper.getClassLoader(),
                new Class<?>[]{operationType},
                (proxy, method, arguments) -> method.getName().equals("tryPlace")
                        ? operation.tryPlace((int) arguments[0], (int) arguments[1])
                        : null);
        Method factory = helper.getMethod(
                "cappedRectangle",
                String.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                operationType,
                Runnable.class);
        return (DailySettlementWorkUnit) factory.invoke(
                null,
                "test_artifact_cap",
                minX,
                minZ,
                maxX,
                maxZ,
                cap,
                operationProxy,
                onClose);
    }

    private static DailySettlementWorkUnit decayingAttempts(
            boolean winter,
            DecayingProbe operation,
            Runnable onClose) throws Exception {
        Class<?> helper = productionHelper();
        Class<?> operationType = nestedType(helper, "DecayingAttempt");
        Object operationProxy = Proxy.newProxyInstance(
                helper.getClassLoader(),
                new Class<?>[]{operationType},
                (proxy, method, arguments) -> method.getName().equals("tryRun")
                        ? operation.tryRun((int) arguments[0], (double) arguments[1])
                        : null);
        Method factory = helper.getMethod(
                "decayingAttempts",
                String.class,
                boolean.class,
                operationType,
                Runnable.class);
        return (DailySettlementWorkUnit) factory.invoke(
                null, "test_decay", winter, operationProxy, onClose);
    }

    private static Class<?> productionHelper() {
        try {
            return Class.forName("com.stardew.craft.manager.PublicAreaDailyWorkUnits");
        } catch (ClassNotFoundException missing) {
            fail("production public-area state helper is missing", missing);
            return null;
        }
    }

    private static Class<?> nestedType(Class<?> owner, String name) {
        return List.of(owner.getDeclaredClasses()).stream()
                .filter(type -> type.getSimpleName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " is missing"));
    }

    private static DailySettlementWorkUnit rectangle(
            int minX,
            int minZ,
            int maxX,
            int maxZ,
            ColumnAction action,
            BooleanSupplier stopEarly,
            Runnable onClose) throws Exception {
        Class<?> helper;
        try {
            helper = Class.forName("com.stardew.craft.manager.PublicAreaDailyWorkUnits");
        } catch (ClassNotFoundException missing) {
            fail("production rectangle cursor helper is missing", missing);
            return null;
        }
        Class<?> consumerType = List.of(helper.getDeclaredClasses()).stream()
                .filter(type -> type.getSimpleName().equals("ColumnConsumer"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ColumnConsumer is missing"));
        Object consumer = Proxy.newProxyInstance(
                helper.getClassLoader(),
                new Class<?>[]{consumerType},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("accept")) {
                        action.accept((int) arguments[0], (int) arguments[1]);
                    }
                    return null;
                });
        Method factory = helper.getMethod(
                "rectangle",
                String.class,
                int.class,
                int.class,
                int.class,
                int.class,
                consumerType,
                BooleanSupplier.class,
                Runnable.class);
        return (DailySettlementWorkUnit) factory.invoke(
                null, "test_rectangle", minX, minZ, maxX, maxZ,
                consumer, stopEarly, onClose);
    }

    private static void drainWithBudget(DailySettlementWorkUnit unit, int budget) throws Exception {
        try (unit) {
            while (!unit.isComplete()) {
                runBudget(unit, budget);
            }
        }
    }

    private static void runBudget(DailySettlementWorkUnit unit, int budget) throws Exception {
        for (int item = 0; item < budget && !unit.isComplete(); item++) {
            unit.runNext();
        }
    }

    private static void assertLegacyDrain(
            String className, String methodName, int parameterCount, String factoryName)
            throws IOException {
        MethodTree legacy = parse(className + ".java", className).method(methodName, parameterCount);
        List<MethodInvocationTree> drains = invocationsNamed(legacy, "drain");
        assertEquals(1, drains.size(), className + "." + methodName + " must drain once");
        Tree argument = drains.getFirst().getArguments().getFirst();
        assertTrue(argument instanceof MethodInvocationTree);
        assertEquals(factoryName, methodName((MethodInvocationTree) argument));
    }

    private static void assertLegacyDateOverride(
            String className,
            String legacyMethod,
            String explicitArgument,
            String overrideMethod,
            String factoryMethod) throws IOException {
        MethodTree legacy = parse(className + ".java", className).method(legacyMethod, 2);
        MethodInvocationTree factory = invocationsNamed(legacy, factoryMethod).getFirst();
        assertEquals(2, factory.getArguments().size());
        Tree contextArgument = factory.getArguments().get(1);
        assertTrue(contextArgument instanceof MethodInvocationTree);
        MethodInvocationTree override = (MethodInvocationTree) contextArgument;
        assertEquals(overrideMethod, methodName(override));
        assertEquals(explicitArgument, override.getArguments().get(1).toString());
        assertTrue(override.getArguments().getFirst() instanceof MethodInvocationTree);
        assertEquals("captureCurrentDay",
                methodName((MethodInvocationTree) override.getArguments().getFirst()));
    }

    private static void assertInitialEntrypointForwards(String className, String explicitArgument)
            throws IOException {
        MethodTree initial = parse(className + ".java", className).method("ensureInitialSpawn", 2);
        List<MethodInvocationTree> dailyCalls = invocationsNamed(initial, "onNewDay");
        assertEquals(1, dailyCalls.size());
        assertEquals(explicitArgument, dailyCalls.getFirst().getArguments().get(1).toString());
    }

    private static void assertReachableCall(String className, String factory, String expected)
            throws IOException {
        ParsedClass parsed = parse(className + ".java", className);
        Set<MethodTree> graph = reachableMethods(parsed, parsed.method(factory, 2));
        assertTrue(graph.stream().flatMap(method -> invocations(method).stream())
                        .anyMatch(call -> methodName(call).equals(expected)),
                className + "." + factory + " must reach " + expected);
    }

    private static void assertDailyRandom(String className, String factory) throws IOException {
        ParsedClass parsed = parse(className + ".java", className);
        Set<MethodTree> graph = reachableMethods(parsed, parsed.method(factory, 2));
        List<MethodInvocationTree> calls = graph.stream().flatMap(method -> invocations(method).stream()).toList();
        assertTrue(calls.stream().anyMatch(call -> {
            String select = call.getMethodSelect().toString();
            return select.equals("DailySettlementRandom.forPosition")
                    || select.equals("DailySettlementRandom.forId");
        }), className + " must use DailySettlementRandom");
        assertTrue(calls.stream().noneMatch(call -> call.getMethodSelect().toString().equals("level.getRandom")),
                className + " daily graph must not depend on interleaved level random state");
    }

    private static void assertSingleAttemptItem(
            String className, String itemMethodName, String attemptMethodName) throws IOException {
        MethodTree item = parse(className + ".java", className).method(itemMethodName, -1);
        assertFalse(hasLoop(item), className + " item must not contain an attempt loop");
        assertEquals(1, invocationsNamed(item, attemptMethodName).size());
    }

    private static boolean hasLoop(Tree tree) {
        return !scan(tree, ForLoopTree.class).isEmpty()
                || !scan(tree, EnhancedForLoopTree.class).isEmpty();
    }

    private static Set<MethodTree> reachableMethods(ParsedClass parsed, MethodTree root) {
        Set<MethodTree> reached = new HashSet<>();
        ArrayDeque<MethodTree> pending = new ArrayDeque<>();
        pending.add(root);
        while (!pending.isEmpty()) {
            MethodTree method = pending.removeFirst();
            if (!reached.add(method)) {
                continue;
            }
            for (MethodInvocationTree call : invocations(method)) {
                parsed.methodsNamed(methodName(call)).stream()
                        .filter(candidate -> !reached.contains(candidate))
                        .forEach(pending::addLast);
            }
        }
        return reached;
    }

    private static List<MethodInvocationTree> invocationsNamed(Tree tree, String name) {
        return invocations(tree).stream().filter(call -> methodName(call).equals(name)).toList();
    }

    private static List<MethodInvocationTree> invocations(Tree tree) {
        return scan(tree, MethodInvocationTree.class);
    }

    private static String methodName(MethodInvocationTree invocation) {
        String select = invocation.getMethodSelect().toString();
        int separator = select.lastIndexOf('.');
        return separator < 0 ? select : select.substring(separator + 1);
    }

    private static <T extends Tree> List<T> scan(Tree tree, Class<T> type) {
        List<T> matches = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void scan(Tree node, Void unused) {
                if (type.isInstance(node)) {
                    matches.add(type.cast(node));
                }
                return super.scan(node, unused);
            }
        }.scan(tree, null);
        return matches;
    }

    private static ParsedClass parse(String fileName, String className) throws IOException {
        Path sourcePath = MANAGERS.resolve(fileName);
        assertTrue(Files.exists(sourcePath), () -> "production source is missing: " + sourcePath);
        String source = Files.readString(sourcePath);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests require a JDK compiler");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject sourceFile = new SimpleJavaFileObject(
                URI.create("string:///" + className + JavaFileObject.Kind.SOURCE.extension),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(
                    null, fileManager, diagnostics, List.of("-proc:none"), null, List.of(sourceFile));
            CompilationUnitTree unit = task.parse().iterator().next();
            List<String> errors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(Object::toString)
                    .toList();
            assertTrue(errors.isEmpty(), () -> "source did not parse: " + String.join("; ", errors));
            ClassTree type = unit.getTypeDecls().stream()
                    .filter(ClassTree.class::isInstance)
                    .map(ClassTree.class::cast)
                    .filter(candidate -> candidate.getSimpleName().contentEquals(className))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("class is missing: " + className));
            return new ParsedClass(type);
        }
    }

    @FunctionalInterface
    private interface ColumnAction {
        void accept(int x, int z);
    }

    @FunctionalInterface
    private interface IndexedAttempt {
        boolean trySpawn(int slot, int attempt);
    }

    @FunctionalInterface
    private interface PlacementAttempt {
        boolean tryPlace(int x, int z);
    }

    @FunctionalInterface
    private interface DecayingProbe {
        boolean tryRun(int cursor, double chance);
    }

    private record AttemptRun(List<String> identities, List<String> outputs) {
    }

    private record ParsedClass(ClassTree type) {
        MethodTree method(String name, int parameterCount) {
            return methodsNamed(name).stream()
                    .filter(method -> parameterCount < 0 || method.getParameters().size() == parameterCount)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "method is missing: " + type.getSimpleName() + "." + name + "/" + parameterCount));
        }

        List<MethodTree> methodsNamed(String name) {
            return type.getMembers().stream()
                    .filter(MethodTree.class::isInstance)
                    .map(MethodTree.class::cast)
                    .filter(method -> method.getName().contentEquals(name))
                    .toList();
        }
    }
}
