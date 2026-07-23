package com.stardew.craft.time.settlement;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmSystemDailyWorkUnitTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir", "."));
    private static final long WORLD_SEED = 0x1357_9BDF_2468_ACEL;
    private static final int TARGET_DAY = 83;
    private static final List<SystemContract> SYSTEMS = List.of(
            new SystemContract("manager/AnimalGrowthManager.java", "AnimalGrowthManager",
                    "growDaily", 1, "processAnimalDay", "forId", "animal_growth", "animalId"),
            new SystemContract("fishpond/service/FishPondDailyUpdateService.java", "FishPondDailyUpdateService",
                    "onNewDay", 1, "processPondDay", "forId", "fish_pond", "entry.stableId()"),
            new SystemContract("manager/PastureGrassGrowthManager.java", "PastureGrassGrowthManager",
                    "growDaily", 1, "processPastureGrassDay", "forPosition", "pasture_grass", "pos"),
            new SystemContract("manager/WildTreeSeedManager.java", "WildTreeSeedManager",
                    "onNewDay", 2, "processTreeDay", "forPosition", "wild_tree_seed", "pos"));

    @Test
    void legacyEntrypointsDrainTheCreatedWorkUnitAsTheirArgument() throws IOException {
        for (SystemContract system : SYSTEMS) {
            MethodTree legacy = parse(system).method(system.legacyMethod(), system.legacyParameterCount());
            List<MethodInvocationTree> drains = invocationsNamed(legacy.getBody(), "drain");

            assertEquals(1, drains.size(), system.className() + " must drain exactly once");
            assertEquals(1, drains.getFirst().getArguments().size());
            Tree drainedArgument = drains.getFirst().getArguments().getFirst();
            assertTrue(drainedArgument instanceof MethodInvocationTree,
                    system.className() + " drain argument must be a method call");
            assertEquals("createDailyWorkUnit", methodName((MethodInvocationTree) drainedArgument),
                    system.className() + " must directly drain createDailyWorkUnit(...)");
        }
    }

    @Test
    void animalSnapshotCopiesEachStableRecordIdFromTheRegistryOnce() throws IOException {
        MethodTree create = parse(SYSTEMS.get(0)).method("createDailyWorkUnit", 2);
        List<EnhancedForLoopTree> animalLoops = scan(create.getBody(), EnhancedForLoopTree.class).stream()
                .filter(loop -> invokes(loop.getExpression(), "getAnimals"))
                .toList();

        assertEquals(1, invocationsNamed(create.getBody(), "getAnimals").size());
        assertEquals(1, animalLoops.size());
        EnhancedForLoopTree loop = animalLoops.getFirst();
        assertEquals("FarmAnimalRecord", loop.getVariable().getType().toString());
        assertTrue(invocationsNamed(loop.getStatement(), "add").stream()
                        .anyMatch(call -> call.getArguments().size() == 1
                                && call.getArguments().getFirst().toString().equals("record.animalId()")),
                "animal snapshot must copy record.animalId(), not retain records");
        assertCursorUsesSnapshot(create, "animalSnapshot", "processAnimalDay");
    }

    @Test
    void fishSnapshotMapsPondIdsOnceAndItemsUseOneIdLookupWithoutRegistryRescan() throws IOException {
        ParsedClass parsed = parse(SYSTEMS.get(1));
        MethodTree create = parsed.method("createDailyWorkUnit", 2);
        MethodTree item = parsed.method("processPondDay", -1);

        assertEquals(1, invocationsNamed(create.getBody(), "getPonds").size());
        assertTrue(scan(create.getBody(), NewClassTree.class).stream()
                        .filter(created -> created.getIdentifier().toString().equals("PondDailyEntry"))
                        .anyMatch(created -> created.getArguments().stream()
                                .map(Object::toString)
                                .toList()
                                .equals(List.of(
                                        "pond.pondId()",
                                        "FishPondDailyDecisions.stableId(pond.pondId())"))),
                "fish snapshot must copy pond id and its stable numeric id");
        assertCursorUsesSnapshot(create, "pondSnapshot", "processPondDay");
        assertEquals(1, invocationsNamed(item.getBody(), "getPond").size());
        assertEquals("entry.pondId()", invocationsNamed(item.getBody(), "getPond")
                .getFirst().getArguments().getFirst().toString());
        assertTrue(invocationsNamed(item.getBody(), "getPonds").isEmpty(),
                "pond item lookup must not rescan the registry");
        assertTrue(reachableMethods(parsed, List.of(item)).stream()
                        .allMatch(method -> invocationsNamed(method.getBody(), "getPonds").isEmpty()),
                "pond item helper graph must not rescan the registry");
    }

    @Test
    void grassEligibilityIsCollectedExactlyOnceAtCreationAndNeverByTheConsumerGraph() throws IOException {
        ParsedClass parsed = parse(SYSTEMS.get(2));
        MethodTree create = parsed.method("createDailyWorkUnit", 2);
        MethodTree item = parsed.method("processPastureGrassDay", -1);

        assertEquals(1, invocationsNamed(create.getBody(), "collectNearbyPastureGrass").size());
        assertCursorUsesSnapshot(create, "grassSnapshot", "processPastureGrassDay");
        assertTrue(reachableMethods(parsed, List.of(item)).stream()
                        .allMatch(method -> invocationsNamed(
                                method.getBody(), "collectNearbyPastureGrass").isEmpty()),
                "grass cursor consumer must not recollect eligible positions");
    }

    @Test
    void wildSnapshotCopiesEveryRequiredKeyAndValueFieldWithoutRetainingLiveEntries() throws IOException {
        MethodTree create = parse(SYSTEMS.get(3)).method("createDailyWorkUnit", 2);
        List<NewClassTree> snapshots = scan(create.getBody(), NewClassTree.class).stream()
                .filter(created -> created.getIdentifier().toString().equals("DailyTreeEntry"))
                .toList();

        assertEquals(1, snapshots.size());
        assertEquals(List.of(
                        "globalPos",
                        "entry.treeId",
                        "entry.hasSeed",
                        "entry.lastSeedRollAbsDay",
                        "entry.lastShakenAbsDay"),
                snapshots.getFirst().getArguments().stream().map(Object::toString).toList());
        assertFalse(scan(create.getBody(), VariableTree.class).stream()
                        .anyMatch(variable -> variable.getType() != null
                                && variable.getType().toString().contains("Map.Entry")),
                "wild snapshot must not retain live Map.Entry objects");
        assertCursorUsesSnapshot(create, "treeSnapshot", "processTreeDay");
    }

    @Test
    void cursorConsumersReceiveWorldSeedAndContextAbsoluteDayAndReachExpectedItemHelpers()
            throws IOException {
        for (SystemContract system : SYSTEMS) {
            ParsedClass parsed = parse(system);
            MethodTree create = parsed.method("createDailyWorkUnit", 2);
            MethodTree item = parsed.method(system.itemMethod(), -1);

            assertVariableInitializer(create, "worldSeed", "level.getSeed()");
            assertVariableInitializer(create, "absoluteDay", "context.absoluteDay()");
            List<LambdaExpressionTree> consumers = cursorConsumers(create);
            assertTrue(consumers.stream().anyMatch(lambda -> invocationsNamed(lambda, system.itemMethod()).stream()
                            .anyMatch(call -> call.getArguments().stream().map(Object::toString).toList()
                                    .containsAll(List.of("worldSeed", "absoluteDay")))),
                    system.className() + " cursor must pass captured seed/day into its item helper");
            assertTrue(reachableMethods(parsed, List.of(item)).contains(item));
        }
    }

    @Test
    void animalItemUsesLastProcessedDayToRejectRepeatsAndWritesTargetAfterProcessing()
            throws IOException {
        MethodTree item = parse(SYSTEMS.get(0)).method("processAnimalDay", -1);
        List<? extends StatementTree> statements = item.getBody().getStatements();
        int readIndex = statementIndexInvoking(statements, "lastProcessedAbsDay");
        int decisionIndex = statementIndexInvoking(statements, "firstAnimalDayToProcess");
        int applyIndex = statementIndexInvoking(statements, "applyDayUpdate");
        int writeIndex = statementIndexInvoking(statements, "setLastProcessedAbsDay");

        assertTrue(readIndex >= 0 && readIndex < decisionIndex);
        assertTrue(decisionIndex < applyIndex && applyIndex < writeIndex,
                "target day must be written only after successful item processing");
        MethodInvocationTree decision = invocationsNamed(
                statements.get(decisionIndex), "firstAnimalDayToProcess").getFirst();
        assertEquals(List.of("lastDay", "absoluteDay"),
                decision.getArguments().stream().map(Object::toString).toList());
        assertTrue(scan(item.getBody(), IfTree.class).stream()
                        .map(IfTree::getCondition)
                        .anyMatch(condition -> condition.toString().contains("firstDay")
                                && condition.toString().contains("absoluteDay")),
                "animal item must reject a record already processed for the target day");
        MethodInvocationTree write = invocationsNamed(item.getBody(), "setLastProcessedAbsDay").getFirst();
        assertEquals("absoluteDay", write.getArguments().getFirst().toString());
    }

    @Test
    void reachableCursorCodeUsesOnlySeededObjectDailyRandomStreams() throws IOException {
        for (SystemContract system : SYSTEMS) {
            ParsedClass parsed = parse(system);
            MethodTree create = parsed.method("createDailyWorkUnit", 2);
            List<MethodTree> roots = new ArrayList<>();
            for (LambdaExpressionTree consumer : cursorConsumers(create)) {
                for (MethodInvocationTree call : scan(consumer, MethodInvocationTree.class)) {
                    roots.addAll(parsed.methodsNamed(methodName(call), call.getArguments().size()));
                }
            }
            Set<MethodTree> reachable = reachableMethods(parsed, roots);

            for (MethodTree method : reachable) {
                assertTrue(invocationsNamed(method.getBody(), "getRandom").isEmpty(),
                        system.className() + " reachable helper calls getRandom: " + method.getName());
                assertTrue(invocationsNamed(method.getBody(), "createUnseeded").isEmpty(),
                        system.className() + " reachable helper calls createUnseeded: " + method.getName());
                assertFalse(scan(method.getBody(), MemberSelectTree.class).stream()
                                .anyMatch(FarmSystemDailyWorkUnitTest::isLevelRandomAccess),
                        system.className() + " reachable helper reads level.random: " + method.getName());
            }

            MethodTree item = parsed.method(system.itemMethod(), -1);
            assertDerivedRandomCall(item, system);
            assertAllReachableDailyRandomCalls(system, reachable);
        }
    }

    @Test
    void productionItemMethodsInvokeThePureDecisionHelpersUsedByBudgetTest() throws IOException {
        ParsedClass animal = parse(SYSTEMS.get(0));
        ParsedClass fish = parse(SYSTEMS.get(1));
        ParsedClass grass = parse(SYSTEMS.get(2));
        ParsedClass wild = parse(SYSTEMS.get(3));

        assertInvokesQualified(animal.method("processAnimalDay", -1),
                "FarmDailyDecisions.firstAnimalDayToProcess");
        assertInvokesQualified(fish.method("applySingleDay", -1),
                "FishPondDailyDecisions.rollChance");
        assertInvokesQualified(grass.method("processPastureGrassDay", -1),
                "FarmDailyDecisions.rollGrassSource",
                "FarmDailyDecisions.rollGrassNeighbor",
                "FarmDailyDecisions.rollGrassVariant");
        assertInvokesQualified(wild.method("processTreeDay", -1),
                "FarmDailyDecisions.rollWildSeed",
                "FarmDailyDecisions.rollWildSpread",
                "FarmDailyDecisions.rollWildOffset");
    }

    @Test
    void oneAndHundredItemLimitsProduceIdenticalBoundRecordAndBlockMaps() throws Exception {
        SimulatedSettlement one = runBoundSettlement(1);
        SimulatedSettlement hundred = runBoundSettlement(100);

        assertEquals(one, hundred);
        assertTrue(one.animals().values().stream().allMatch(state -> state.processCount() <= 1),
                "repeated animal IDs must remain idempotent for the target day");
        assertTrue(one.animals().values().stream()
                .filter(state -> state.initialLastDay() <= TARGET_DAY)
                .allMatch(state -> state.lastProcessedDay() == TARGET_DAY));
        assertEquals(137, one.fish().size());
        assertEquals(137, one.grassBlocks().size());
        assertEquals(137, one.wildTreeBlocks().size());
    }

    @Test
    void activeOwnershipIsRecoveredOnCreationFailureAndClose() throws IOException {
        for (SystemContract system : SYSTEMS) {
            MethodTree create = parse(system).method("createDailyWorkUnit", 2);
            List<TryTree> tries = scan(create.getBody(), TryTree.class);

            assertFalse(scan(create.getBody(), IfTree.class).isEmpty());
            assertFalse(tries.isEmpty());
            assertTrue(tries.stream().flatMap(tree -> tree.getCatches().stream())
                            .map(CatchTree::getBlock)
                            .anyMatch(block -> invokes(block, "finishDailyProcessing")));
            assertTrue(cursorOrSequenceCloseCallbacks(create).stream()
                            .anyMatch(callback -> invokes(callback, "finishDailyProcessing")
                                    || callback instanceof MemberReferenceTree reference
                                    && reference.getName().contentEquals("finishDailyProcessing")),
                    system.className() + " close callback must release active ownership");
        }
    }

    private static SimulatedSettlement runBoundSettlement(int itemLimit) throws Exception {
        Map<Long, AnimalDecision> animals = new LinkedHashMap<>();
        List<Long> animalItems = new ArrayList<>();
        for (long id = 1; id <= 137; id++) {
            int initialDay = switch ((int) (id % 4)) {
                case 0 -> 0;
                case 1 -> TARGET_DAY - 3;
                case 2 -> TARGET_DAY;
                default -> TARGET_DAY + 1;
            };
            animals.put(id, new AnimalDecision(initialDay, initialDay, 0));
            animalItems.add(id);
            animalItems.add(id);
        }

        List<String> fishItems = new ArrayList<>();
        List<BlockPos> positions = new ArrayList<>();
        for (int index = 0; index < 137; index++) {
            fishItems.add("fish_pond_" + index);
            positions.add(new BlockPos(index - 68, 64 + index % 3, index * 7 - 200));
        }

        Map<String, Boolean> fish = new LinkedHashMap<>();
        Map<BlockPos, GrassBlockDecision> grass = new LinkedHashMap<>();
        Map<BlockPos, WildTreeBlockDecision> wild = new LinkedHashMap<>();
        DailySettlementWorkUnit animalUnit = DailySettlementWorkUnits.cursor(
                "animal", animalItems, Object::toString, id -> {
                    AnimalDecision current = animals.get(id);
                    int firstDay = (int) invokePure(
                            "com.stardew.craft.manager.FarmDailyDecisions",
                            "firstAnimalDayToProcess",
                            new Class<?>[]{int.class, int.class},
                            current.lastProcessedDay(), TARGET_DAY);
                    if (firstDay <= TARGET_DAY) {
                        animals.put(id, new AnimalDecision(
                                current.initialLastDay(), TARGET_DAY, current.processCount() + 1));
                    }
                }, () -> {});
        DailySettlementWorkUnit fishUnit = DailySettlementWorkUnits.cursor(
                "fish", fishItems, value -> value, pondId -> {
                    long stableId = (long) invokePure(
                            "com.stardew.craft.fishpond.service.FishPondDailyDecisions",
                            "stableId", new Class<?>[]{String.class}, pondId);
                    RandomSource random = DailySettlementRandom.forId(
                            WORLD_SEED, TARGET_DAY, "fish_pond", stableId);
                    boolean produced = (boolean) invokePure(
                            "com.stardew.craft.fishpond.service.FishPondDailyDecisions",
                            "rollChance", new Class<?>[]{RandomSource.class, double.class},
                            random, 0.47D);
                    fish.put(pondId, produced);
                }, () -> {});
        DailySettlementWorkUnit grassUnit = DailySettlementWorkUnits.cursor(
                "grass", positions, BlockPos::toShortString, pos -> {
                    RandomSource random = DailySettlementRandom.forPosition(
                            WORLD_SEED, TARGET_DAY, "pasture_grass", pos);
                    boolean source = (boolean) invokeFarmDecision(
                            "rollGrassSource", new Class<?>[]{RandomSource.class}, random);
                    List<Integer> variants = new ArrayList<>();
                    if (source) {
                        for (int neighbor = 0; neighbor < 4; neighbor++) {
                            boolean spread = (boolean) invokeFarmDecision(
                                    "rollGrassNeighbor", new Class<?>[]{RandomSource.class}, random);
                            variants.add(spread
                                    ? (int) invokeFarmDecision(
                                            "rollGrassVariant", new Class<?>[]{RandomSource.class}, random)
                                    : -1);
                        }
                    }
                    grass.put(pos, new GrassBlockDecision(source, List.copyOf(variants)));
                }, () -> {});
        DailySettlementWorkUnit wildUnit = DailySettlementWorkUnits.cursor(
                "wild", positions, BlockPos::toShortString, pos -> {
                    RandomSource random = DailySettlementRandom.forPosition(
                            WORLD_SEED, TARGET_DAY, "wild_tree_seed", pos);
                    boolean seed = (boolean) invokeFarmDecision(
                            "rollWildSeed", new Class<?>[]{RandomSource.class, float.class}, random, 0.2F);
                    boolean spread = (boolean) invokeFarmDecision(
                            "rollWildSpread", new Class<?>[]{RandomSource.class, float.class}, random, 0.05F);
                    BlockPos target = spread
                            ? pos.offset(
                                    (int) invokeFarmDecision(
                                            "rollWildOffset", new Class<?>[]{RandomSource.class}, random),
                                    0,
                                    (int) invokeFarmDecision(
                                            "rollWildOffset", new Class<?>[]{RandomSource.class}, random))
                            : null;
                    wild.put(pos, new WildTreeBlockDecision(seed, spread, target));
                }, () -> {});
        DailySettlementWorkUnit sequence = DailySettlementWorkUnits.sequence(
                "farm", List.of(animalUnit, fishUnit, grassUnit, wildUnit), () -> {});

        BudgetedWorkRunner runner = new BudgetedWorkRunner(() -> 0L);
        try (sequence) {
            while (!sequence.isComplete()) {
                runner.run(sequence, 1_000_000L, itemLimit);
            }
        }
        return new SimulatedSettlement(
                Map.copyOf(animals), Map.copyOf(fish), Map.copyOf(grass), Map.copyOf(wild));
    }

    private static Object invokeFarmDecision(String method, Class<?>[] parameterTypes, Object... args)
            throws Exception {
        return invokePure("com.stardew.craft.manager.FarmDailyDecisions",
                method, parameterTypes, args);
    }

    private static Object invokePure(
            String className,
            String methodName,
            Class<?>[] parameterTypes,
            Object... args) throws Exception {
        Class<?> type = Class.forName(className);
        Method method = type.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private static void assertDerivedRandomCall(MethodTree item, SystemContract system) {
        List<MethodInvocationTree> matches = scan(item.getBody(), MethodInvocationTree.class).stream()
                .filter(call -> call.getMethodSelect().toString()
                        .equals("DailySettlementRandom." + system.randomFactory()))
                .filter(call -> call.getArguments().size() == 4)
                .filter(call -> call.getArguments().get(0).toString().equals("worldSeed"))
                .filter(call -> call.getArguments().get(1).toString().equals("absoluteDay"))
                .filter(call -> call.getArguments().get(2) instanceof LiteralTree literal
                        && system.subsystem().equals(literal.getValue()))
                .filter(call -> call.getArguments().get(3).toString().equals(system.stableKey()))
                .toList();
        assertEquals(1, matches.size(), system.className()
                + " must use worldSeed + context day + subsystem + stable object key");
    }

    private static void assertAllReachableDailyRandomCalls(
            SystemContract system,
            Set<MethodTree> reachable) {
        int actualCount = 0;
        for (MethodTree method : reachable) {
            for (MethodInvocationTree call : scan(method.getBody(), MethodInvocationTree.class)) {
                if (!call.getMethodSelect().toString().startsWith("DailySettlementRandom.")) {
                    continue;
                }
                actualCount++;
                assertEquals(4, call.getArguments().size());
                assertEquals("worldSeed", call.getArguments().get(0).toString());
                if (system.className().equals("AnimalGrowthManager")
                        && method.getName().contentEquals("processReproductionDay")) {
                    assertDailyRandomArguments(call, "absoluteDaysPlayed",
                            "animal_reproduction", "bestCandidate.animalId()");
                } else if (system.className().equals("AnimalGrowthManager")) {
                    assertTrue(Set.of("absoluteDay", "catchUpDay")
                            .contains(call.getArguments().get(1).toString()));
                    assertDailyRandomArguments(call, call.getArguments().get(1).toString(),
                            "animal_growth", "animalId");
                } else {
                    assertDailyRandomArguments(call, "absoluteDay",
                            system.subsystem(), system.stableKey());
                }
            }
        }
        int expectedCount = system.className().equals("AnimalGrowthManager") ? 3 : 1;
        assertEquals(expectedCount, actualCount,
                system.className() + " reachable daily-random call count changed");
    }

    private static void assertDailyRandomArguments(
            MethodInvocationTree call,
            String day,
            String subsystem,
            String stableKey) {
        assertEquals(day, call.getArguments().get(1).toString());
        assertTrue(call.getArguments().get(2) instanceof LiteralTree);
        assertEquals(subsystem, ((LiteralTree) call.getArguments().get(2)).getValue());
        assertEquals(stableKey, call.getArguments().get(3).toString());
    }

    private static boolean isLevelRandomAccess(MemberSelectTree select) {
        if (!select.getIdentifier().contentEquals("random")) {
            return false;
        }
        Tree expression = select.getExpression();
        return expression instanceof IdentifierTree identifier
                && identifier.getName().toString().toLowerCase().contains("level");
    }

    private static Set<MethodTree> reachableMethods(ParsedClass parsed, List<MethodTree> roots) {
        Set<MethodTree> reachable = new HashSet<>();
        ArrayDeque<MethodTree> pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            MethodTree method = pending.removeFirst();
            if (!reachable.add(method)) {
                continue;
            }
            for (MethodInvocationTree call : scan(method.getBody(), MethodInvocationTree.class)) {
                pending.addAll(parsed.methodsNamed(methodName(call), call.getArguments().size()));
            }
        }
        return reachable;
    }

    private static void assertCursorUsesSnapshot(
            MethodTree create,
            String snapshotName,
            String itemMethod) {
        assertTrue(invocationsNamed(create.getBody(), "cursor").stream().anyMatch(call ->
                        call.getArguments().size() >= 4
                                && call.getArguments().get(1).toString().equals(snapshotName)
                                && call.getArguments().get(3) instanceof LambdaExpressionTree lambda
                                && invokes(lambda, itemMethod)),
                "cursor must consume frozen " + snapshotName + " through " + itemMethod);
    }

    private static void assertVariableInitializer(MethodTree method, String variable, String expected) {
        VariableTree declaration = scan(method.getBody(), VariableTree.class).stream()
                .filter(candidate -> candidate.getName().contentEquals(variable))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing variable: " + variable));
        assertNotNull(declaration.getInitializer());
        assertEquals(expected, declaration.getInitializer().toString());
    }

    private static void assertInvokesQualified(MethodTree method, String... qualifiedNames) {
        Set<String> actual = scan(method.getBody(), MethodInvocationTree.class).stream()
                .map(call -> call.getMethodSelect().toString())
                .collect(java.util.stream.Collectors.toSet());
        for (String qualifiedName : qualifiedNames) {
            assertTrue(actual.contains(qualifiedName),
                    method.getName() + " must invoke production helper " + qualifiedName);
        }
    }

    private static int statementIndexInvoking(
            List<? extends StatementTree> statements,
            String methodName) {
        for (int index = 0; index < statements.size(); index++) {
            if (invokes(statements.get(index), methodName)) {
                return index;
            }
        }
        return -1;
    }

    private static List<LambdaExpressionTree> cursorConsumers(MethodTree create) {
        List<LambdaExpressionTree> consumers = new ArrayList<>();
        for (MethodInvocationTree call : invocationsNamed(create.getBody(), "cursor")) {
            if (call.getArguments().size() >= 4
                    && call.getArguments().get(3) instanceof LambdaExpressionTree lambda) {
                consumers.add(lambda);
            }
        }
        return consumers;
    }

    private static List<Tree> cursorOrSequenceCloseCallbacks(MethodTree create) {
        List<Tree> callbacks = new ArrayList<>();
        for (MethodInvocationTree call : scan(create.getBody(), MethodInvocationTree.class)) {
            if ((methodName(call).equals("cursor") || methodName(call).equals("sequence"))
                    && !call.getArguments().isEmpty()) {
                Tree callback = call.getArguments().getLast();
                if (callback instanceof LambdaExpressionTree
                        || callback instanceof MemberReferenceTree) {
                    callbacks.add(callback);
                }
            }
        }
        return callbacks;
    }

    private static ParsedClass parse(SystemContract system) throws IOException {
        Path sourcePath = PROJECT.resolve("src/main/java/com/stardew/craft/" + system.relativePath());
        String source = Files.readString(sourcePath);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests require a JDK compiler");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject sourceFile = new SimpleJavaFileObject(
                URI.create("string:///" + system.className() + JavaFileObject.Kind.SOURCE.extension),
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
            List<String> parseErrors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(Object::toString)
                    .toList();
            assertTrue(parseErrors.isEmpty(), () -> "source did not parse: " + String.join("; ", parseErrors));
            ClassTree classTree = unit.getTypeDecls().stream()
                    .filter(ClassTree.class::isInstance)
                    .map(ClassTree.class::cast)
                    .filter(candidate -> candidate.getSimpleName().contentEquals(system.className()))
                    .findFirst()
                    .orElseThrow();
            return new ParsedClass(classTree);
        }
    }

    private static List<MethodInvocationTree> invocationsNamed(Tree tree, String name) {
        return scan(tree, MethodInvocationTree.class).stream()
                .filter(call -> methodName(call).equals(name))
                .toList();
    }

    private static boolean invokes(Tree tree, String name) {
        return !invocationsNamed(tree, name).isEmpty();
    }

    private static String methodName(MethodInvocationTree invocation) {
        String select = invocation.getMethodSelect().toString();
        int separator = select.lastIndexOf('.');
        return separator < 0 ? select : select.substring(separator + 1);
    }

    private static <T extends Tree> List<T> scan(Tree tree, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (tree == null) {
            return matches;
        }
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

    private record AnimalDecision(int initialLastDay, int lastProcessedDay, int processCount) {
    }

    private record GrassBlockDecision(boolean sourceGrew, List<Integer> neighborVariants) {
    }

    private record WildTreeBlockDecision(boolean hasSeed, boolean spread, BlockPos target) {
    }

    private record SimulatedSettlement(
            Map<Long, AnimalDecision> animals,
            Map<String, Boolean> fish,
            Map<BlockPos, GrassBlockDecision> grassBlocks,
            Map<BlockPos, WildTreeBlockDecision> wildTreeBlocks) {
    }

    private record SystemContract(
            String relativePath,
            String className,
            String legacyMethod,
            int legacyParameterCount,
            String itemMethod,
            String randomFactory,
            String subsystem,
            String stableKey) {
    }

    private record ParsedClass(ClassTree classTree) {
        private MethodTree method(String name, int parameterCount) {
            return methodsNamed(name, parameterCount).stream()
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("method is missing: " + name));
        }

        private List<MethodTree> methodsNamed(String name, int parameterCount) {
            return classTree.getMembers().stream()
                    .filter(MethodTree.class::isInstance)
                    .map(MethodTree.class::cast)
                    .filter(candidate -> candidate.getName().contentEquals(name))
                    .filter(candidate -> parameterCount < 0
                            || candidate.getParameters().size() == parameterCount)
                    .toList();
        }
    }
}
