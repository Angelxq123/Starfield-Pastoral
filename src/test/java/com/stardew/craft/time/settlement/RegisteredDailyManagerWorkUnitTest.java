package com.stardew.craft.time.settlement;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisteredDailyManagerWorkUnitTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir", "."));
    private static final List<ManagerContract> MANAGERS = List.of(
            new ManagerContract("CropGrowthManager", "growDaily", "cropPositions",
                    "isProcessing", "applyPendingChanges", "processCropDay"),
            new ManagerContract("TreeGrowthManager", "growDaily", "saplingPositions",
                    "isProcessing", "applyPendingChanges", "processRegisteredSaplingDay"),
            new ManagerContract("FruitTreeGrowthManager", "growDaily", "saplings",
                    "processing", "applyPendingRemoves", "processRegisteredTreeDay"),
            new ManagerContract("SprinklerManager", "waterDaily", "sprinklerPositions",
                    "isProcessing", "applyPendingChanges", "processSprinklerDay"));

    @Test
    void sequenceRunsOneChildItemAtATimeAndDefersAtomicUntilCursorCompletes() throws Exception {
        List<String> events = new ArrayList<>();
        DailySettlementWorkUnit cursor = DailySettlementWorkUnits.cursor(
                "entries", List.of("a", "b"), value -> value,
                events::add, () -> events.add("cursor-close"));
        DailySettlementWorkUnit atomic = DailySettlementWorkUnits.atomic(
                "farmland_scan", () -> events.add("farmland"),
                () -> events.add("atomic-close"));
        DailySettlementWorkUnit sequence = DailySettlementWorkUnits.sequence(
                "crop", List.of(cursor, atomic), () -> events.add("outer-close"));

        assertEquals("a", sequence.currentItemIdentity());
        sequence.runNext();
        assertEquals(List.of("a"), events);
        sequence.runNext();
        assertEquals(List.of("a", "b", "cursor-close"), events);
        assertEquals("farmland_scan", sequence.currentItemIdentity());
        assertEquals(2, sequence.maxRetries());
        sequence.runNext();

        assertEquals(List.of("a", "b", "cursor-close", "farmland", "atomic-close"), events);
        assertTrue(sequence.isComplete());
        sequence.close();
        sequence.close();
        assertEquals(List.of(
                "a", "b", "cursor-close", "farmland", "atomic-close", "outer-close"), events);
    }

    @Test
    void sequenceKeepsFailedChildCurrentUntilSkipThenResumesFollowingChildren() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        DailySettlementWorkUnit failingCursor = DailySettlementWorkUnits.cursor(
                "entries", List.of("bad", "good"), value -> value, value -> {
                    if (value.equals("bad")) {
                        attempts.incrementAndGet();
                        throw new Exception("failure");
                    }
                    events.add(value);
                }, () -> events.add("cursor-close"));
        DailySettlementWorkUnit atomic = DailySettlementWorkUnits.atomic(
                "tail", () -> events.add("tail"), () -> events.add("atomic-close"));
        DailySettlementWorkUnit sequence = DailySettlementWorkUnits.sequence(
                "sequence", List.of(failingCursor, atomic), () -> events.add("outer-close"));

        assertThrows(Exception.class, sequence::runNext);
        assertEquals("bad", sequence.currentItemIdentity());
        assertEquals(0, sequence.maxRetries());
        sequence.skipFailedItem();
        assertEquals("good", sequence.currentItemIdentity());
        sequence.runNext();
        assertEquals("tail", sequence.currentItemIdentity());
        sequence.runNext();
        sequence.close();

        assertEquals(1, attempts.get());
        assertEquals(List.of("good", "cursor-close", "tail", "atomic-close", "outer-close"), events);
    }

    @Test
    void sequenceSnapshotsChildrenAndClosesEveryChildAndOuterExactlyOnce() {
        AtomicInteger firstCloses = new AtomicInteger();
        AtomicInteger secondCloses = new AtomicInteger();
        AtomicInteger outerCloses = new AtomicInteger();
        List<DailySettlementWorkUnit> children = new ArrayList<>();
        children.add(DailySettlementWorkUnits.atomic("first", () -> {}, firstCloses::incrementAndGet));
        DailySettlementWorkUnit sequence = DailySettlementWorkUnits.sequence(
                "sequence", children, outerCloses::incrementAndGet);
        children.add(DailySettlementWorkUnits.atomic("late", () -> {}, secondCloses::incrementAndGet));

        sequence.close();
        sequence.close();

        assertEquals(1, firstCloses.get());
        assertEquals(0, secondCloses.get());
        assertEquals(1, outerCloses.get());
    }

    @Test
    void sequenceDefersCompletedChildCloseFailureUntilFinalCloseWithoutSkippingNextChild()
            throws Exception {
        List<String> events = new ArrayList<>();
        RuntimeException closeFailure = new RuntimeException("close-a");
        DailySettlementWorkUnit first = DailySettlementWorkUnits.atomic(
                "a", () -> events.add("run-a"), () -> {
                    events.add("close-a");
                    throw closeFailure;
                });
        DailySettlementWorkUnit second = DailySettlementWorkUnits.atomic(
                "b", () -> events.add("run-b"), () -> events.add("close-b"));
        DailySettlementWorkUnit sequence = DailySettlementWorkUnits.sequence(
                "sequence", List.of(first, second), () -> events.add("outer-cleanup"));

        assertDoesNotThrow(sequence::runNext);
        assertEquals("b", sequence.currentItemIdentity());
        sequence.runNext();
        assertEquals(List.of("run-a", "close-a", "run-b", "close-b"), events);

        RuntimeException actual = assertThrows(RuntimeException.class, sequence::close);
        assertSame(closeFailure, actual);
        assertEquals(List.of(
                "run-a", "close-a", "run-b", "close-b", "outer-cleanup"), events);
        assertDoesNotThrow(sequence::close);
    }

    @Test
    void sequenceClosePreservesErrorAfterClosingRemainingChildrenAndOuter() {
        List<String> events = new ArrayList<>();
        Error firstFailure = new AssertionError("close-a");
        RuntimeException secondFailure = new RuntimeException("close-b");
        Error outerFailure = new AssertionError("outer");
        DailySettlementWorkUnit first = DailySettlementWorkUnits.atomic("a", () -> {}, () -> {
            events.add("close-a");
            throw firstFailure;
        });
        DailySettlementWorkUnit second = DailySettlementWorkUnits.atomic("b", () -> {}, () -> {
            events.add("close-b");
            throw secondFailure;
        });
        DailySettlementWorkUnit sequence = DailySettlementWorkUnits.sequence(
                "sequence", List.of(first, second), () -> {
                    events.add("outer-cleanup");
                    throw outerFailure;
                });

        Error actual = assertThrows(Error.class, sequence::close);

        assertSame(firstFailure, actual);
        assertEquals(List.of(secondFailure, outerFailure), List.of(actual.getSuppressed()));
        assertEquals(List.of("close-a", "close-b", "outer-cleanup"), events);
        assertDoesNotThrow(sequence::close);
    }

    @Test
    void emptySequenceClosesOuterExactlyOnce() {
        AtomicInteger outerCloses = new AtomicInteger();
        DailySettlementWorkUnit sequence = DailySettlementWorkUnits.sequence(
                "empty", List.of(), outerCloses::incrementAndGet);

        assertTrue(sequence.isComplete());
        sequence.close();
        sequence.close();

        assertEquals(1, outerCloses.get());
    }

    @Test
    void deferredCreatesItsChildOnceOnlyAfterItsSequencePhaseIsReached() throws Exception {
        AtomicInteger supplierCalls = new AtomicInteger();
        AtomicInteger childCloses = new AtomicInteger();
        List<String> events = new ArrayList<>();
        DailySettlementWorkUnit first = DailySettlementWorkUnits.cursor(
                "first", List.of("sapling"), value -> value, events::add, () -> {});
        DailySettlementWorkUnit deferred = DailySettlementWorkUnits.deferred("mature-phase", () -> {
            supplierCalls.incrementAndGet();
            return DailySettlementWorkUnits.cursor(
                    "mature", List.of("bad", "good"), value -> value, value -> {
                        if (value.equals("bad")) {
                            throw new Exception("failure");
                        }
                        events.add(value);
                    }, childCloses::incrementAndGet);
        });
        DailySettlementWorkUnit sequence = DailySettlementWorkUnits.sequence(
                "fruit", List.of(first, deferred), () -> {});

        assertEquals(0, supplierCalls.get());
        assertEquals("sapling", sequence.currentItemIdentity());
        assertEquals(0, supplierCalls.get());
        sequence.runNext();
        assertEquals(1, supplierCalls.get());
        assertEquals("bad", sequence.currentItemIdentity());
        assertEquals(0, sequence.maxRetries());
        assertThrows(Exception.class, sequence::runNext);
        sequence.skipFailedItem();
        sequence.runNext();
        sequence.close();

        assertEquals(List.of("sapling", "good"), events);
        assertEquals(1, supplierCalls.get());
        assertEquals(1, childCloses.get());
    }

    @Test
    void closingDeferredBeforeItsPhaseDoesNotCallSupplier() {
        AtomicInteger supplierCalls = new AtomicInteger();
        DailySettlementWorkUnit deferred = DailySettlementWorkUnits.deferred("unused", () -> {
            supplierCalls.incrementAndGet();
            return DailySettlementWorkUnits.atomic("late", () -> {}, () -> {});
        });

        deferred.close();
        deferred.close();

        assertEquals(0, supplierCalls.get());
        assertTrue(deferred.isComplete());
    }

    @Test
    void legacyEntrypointsOnlyDrainTheCreatedCurrentDayWorkUnit() throws IOException {
        for (ManagerContract manager : MANAGERS) {
            MethodTree legacy = parseMethod(manager, manager.legacyMethod(), 1);
            assertEquals(1, legacy.getBody().getStatements().size(),
                    manager.className() + " legacy entrypoint must only drain");
            assertTrue(scan(legacy, EnhancedForLoopTree.class).isEmpty(),
                    manager.className() + " legacy entrypoint must not retain a registry loop");
            assertTrue(scan(legacy, ForLoopTree.class).isEmpty(),
                    manager.className() + " legacy entrypoint must not retain a registry loop");
            List<MethodInvocationTree> calls = invocations(legacy);
            assertEquals(1, calls.stream().filter(call -> methodName(call).equals("drain")).count());
            MethodInvocationTree drain = calls.stream()
                    .filter(call -> methodName(call).equals("drain"))
                    .findFirst().orElseThrow();
            assertEquals(1, drain.getArguments().size());
            assertInvocation(drain.getArguments().getFirst(), "createDailyWorkUnit");
            MethodInvocationTree create = (MethodInvocationTree) drain.getArguments().getFirst();
            assertEquals(2, create.getArguments().size());
            assertEquals(legacy.getParameters().getFirst().getName().toString(),
                    create.getArguments().getFirst().toString());
            assertInvocation(create.getArguments().get(1), "captureCurrentDay");
            MethodInvocationTree capture = (MethodInvocationTree) create.getArguments().get(1);
            assertInvocation(capture.getArguments().getFirst(), "get");
        }
    }

    @Test
    void managersFreezeRegistrySnapshotsAfterEnteringProcessingAndRecoverCreationFailure()
            throws IOException {
        for (ManagerContract manager : MANAGERS) {
            MethodTree create = parseMethod(manager, "createDailyWorkUnit", 2);
            assertTrue(create.getModifiers().getFlags().contains(javax.lang.model.element.Modifier.PUBLIC));
            assertEquals("DailySettlementWorkUnit", create.getReturnType().toString());
            assertEquals(List.of("ServerLevel", "DailySettlementContext"), create.getParameters().stream()
                    .map(parameter -> parameter.getType().toString()).toList());

            List<AssignmentTree> assignments = scan(create, AssignmentTree.class);
            int processingTrue = indexOfAssignment(assignments, manager.processingField(), "true");
            assertTrue(processingTrue >= 0, manager.className() + " must enter processing at creation");
            List<IfTree> ownershipGuards = create.getBody().getStatements().stream()
                    .filter(IfTree.class::isInstance)
                    .map(IfTree.class::cast)
                    .filter(candidate -> isIdentifier(
                            candidate.getCondition(), manager.processingField()))
                    .toList();
            assertEquals(1, ownershipGuards.size(),
                    manager.className() + " must reject overlapping work units");
            assertTrue(ownershipGuards.getFirst().getThenStatement().toString()
                    .contains("new IllegalStateException"));
            int ownershipGuard = create.getBody().getStatements().indexOf(ownershipGuards.getFirst());
            int processingStatement = directStatementIndexContaining(
                    create, manager.processingField() + " = true");
            int snapshotTry = directStatementIndex(create, TryTree.class);
            assertTrue(ownershipGuard >= 0 && ownershipGuard < processingStatement,
                    manager.className() + " must guard before claiming processing ownership");
            assertTrue(processingStatement < snapshotTry,
                    manager.className() + " must claim ownership before snapshot creation");

            List<NewClassTree> arrayLists = scan(create, NewClassTree.class).stream()
                    .filter(node -> node.getIdentifier().toString().endsWith("ArrayList<>"))
                    .filter(node -> node.getArguments().stream()
                            .anyMatch(argument -> argument.toString().equals(manager.registryField())
                                    || argument.toString().equals(manager.registryField() + ".entrySet()")))
                    .toList();
            assertFalse(arrayLists.isEmpty(), manager.className() + " must snapshot its registry at creation");
            assertTrue(invocations(create).stream().filter(call -> methodName(call).equals("requireNonNull")).count() >= 2,
                    manager.className() + " must reject null level/context");
            List<CatchTree> catches = scan(create, CatchTree.class);
            assertFalse(catches.isEmpty(), manager.className() + " must recover processing on creation failure");
            assertTrue(catches.stream().flatMap(catchTree -> invocations(catchTree).stream())
                    .anyMatch(call -> methodName(call).equals("finishDailyProcessing")));
        }
    }

    @Test
    void managerCursorsExposeStableLocationIdentitiesAndCloseThroughCleanup() throws IOException {
        for (ManagerContract manager : MANAGERS) {
            MethodTree create = parseMethod(manager, "createDailyWorkUnit", 2);
            List<MethodInvocationTree> factories = invocations(create).stream()
                    .filter(call -> methodName(call).equals("cursor") || methodName(call).equals("sequence"))
                    .toList();
            assertFalse(factories.isEmpty(), manager.className() + " must create cursor-based work");
            assertTrue(invocations(create).stream()
                    .anyMatch(call -> methodName(call).equals(manager.entryMethod())),
                    manager.className() + " cursor must delegate one registered entry at a time");
            assertTrue(create.toString().contains("finishDailyProcessing"),
                    manager.className() + " close callback must finish processing");

            MethodTree finish = parseMethod(manager, "finishDailyProcessing", 0);
            assertTrue(invocations(finish).stream()
                    .anyMatch(call -> methodName(call).equals(manager.cleanupMethod())),
                    manager.className() + " finish callback must apply pending changes");
            assertTrue(scan(finish, AssignmentTree.class).stream()
                    .anyMatch(assignment -> assignment.getVariable().toString().equals(manager.processingField())
                            && assignment.getExpression().toString().equals("false")));
            MethodTree entryMethod = parseMethod(manager, manager.entryMethod(),
                    manager.className().equals("TreeGrowthManager") ? 3 : 2);
            assertFalse(invocations(entryMethod).stream()
                    .anyMatch(call -> methodName(call).equals(manager.cleanupMethod())));
            assertFalse(scan(entryMethod, AssignmentTree.class).stream()
                    .anyMatch(assignment -> assignment.getVariable().toString().equals(manager.processingField())
                            && assignment.getExpression().toString().equals("false")));

            MethodTree identity = parseMethod(manager, "dailyItemIdentity", 1);
            List<String> identityCalls = invocations(identity).stream().map(RegisteredDailyManagerWorkUnitTest::methodName).toList();
            assertTrue(identityCalls.contains("dimension"));
            assertTrue(identityCalls.contains("pos"));
        }
    }

    @Test
    void entryMethodsPreserveDimensionFarmLoadedAndValidationOrder() throws IOException {
        for (ManagerContract manager : MANAGERS) {
            MethodTree process = parseMethod(manager, manager.entryMethod(),
                    manager.className().equals("TreeGrowthManager") ? 3 : 2);
            String body = process.getBody().toString();
            int dimension = body.indexOf("dimension()");
            int farm = body.indexOf("shouldProcessPosition");
            int loaded = body.indexOf("isLoaded");
            int state = body.indexOf("getBlockState");
            assertTrue(dimension >= 0 && dimension < farm,
                    manager.className() + " must filter dimension before farm eligibility");
            assertTrue(farm < loaded, manager.className() + " must filter farm before loaded state");
            assertTrue(loaded < state, manager.className() + " must validate the block only after loaded state");
        }
    }

    @Test
    void cropSequencesFarmlandAtomicAfterEntriesAndNeverFromClose() throws IOException {
        ManagerContract crop = MANAGERS.getFirst();
        MethodTree create = parseMethod(crop, "createDailyWorkUnit", 2);
        List<VariableTree> variables = scan(create, VariableTree.class);
        int cursor = variableIndexWithInvocation(variables, "cursor");
        int atomic = variableIndexWithInvocation(variables, "atomic");
        assertTrue(cursor >= 0 && cursor < atomic);
        VariableTree atomicVariable = variables.get(atomic);
        assertTrue(atomicVariable.getInitializer().toString().contains("dryAllFarmland"));
        assertTrue(atomicVariable.getInitializer().toString().contains("farmland_scan"));
        MethodInvocationTree sequence = invocations(create).stream()
                .filter(call -> methodName(call).equals("sequence"))
                .findFirst().orElseThrow();
        assertTrue(sequence.getArguments().stream().anyMatch(argument -> argument.toString().contains(
                variables.get(cursor).getName() + ", " + atomicVariable.getName())));
        List<LambdaExpressionTree> lambdas = scan(create, LambdaExpressionTree.class);
        assertTrue(lambdas.stream().filter(lambda -> lambda.toString().contains("dryAllFarmland")).count() == 1);
        assertFalse(lambdas.stream()
                .filter(lambda -> lambda.toString().contains(crop.cleanupMethod()))
                .anyMatch(lambda -> lambda.toString().contains("dryAllFarmland")));
    }

    @Test
    void fruitDefersMatureSnapshotUntilAfterFrozenSaplingCursor() throws IOException {
        ManagerContract fruit = MANAGERS.get(2);
        MethodTree create = parseMethod(fruit, "createDailyWorkUnit", 2);
        List<EnhancedForLoopTree> createLoops = scan(create, EnhancedForLoopTree.class);
        assertEquals(1, createLoops.size());
        assertTrue(createLoops.getFirst().getExpression().toString().contains("saplings.entrySet()"));
        String saplingLoop = createLoops.getFirst().toString();
        assertTrue(saplingLoop.contains("SAPLING"));
        assertTrue(saplingLoop.contains("daysRemaining"));
        assertTrue(saplingLoop.contains("type"));

        List<MethodInvocationTree> deferredCalls = invocations(create).stream()
                .filter(call -> methodName(call).equals("deferred"))
                .toList();
        assertEquals(1, deferredCalls.size());
        assertEquals(2, deferredCalls.getFirst().getArguments().size());
        assertTrue(deferredCalls.getFirst().getArguments().get(1) instanceof LambdaExpressionTree);
        LambdaExpressionTree matureFactory =
                (LambdaExpressionTree) deferredCalls.getFirst().getArguments().get(1);
        assertTrue(invocations(matureFactory).stream()
                .anyMatch(call -> methodName(call).equals("createMatureDailyWorkUnit")));

        MethodTree createMature = parseMethod(fruit, "createMatureDailyWorkUnit", 1);
        List<EnhancedForLoopTree> matureLoops = scan(createMature, EnhancedForLoopTree.class);
        assertEquals(1, matureLoops.size());
        assertTrue(matureLoops.getFirst().getExpression().toString().contains("matureTrees"));
        String matureLoop = matureLoops.getFirst().toString();
        assertTrue(matureLoop.contains("MATURE"));
        assertEquals(1, scan(createMature, NewClassTree.class).stream()
                .filter(node -> node.getIdentifier().toString().endsWith("ArrayList<>"))
                .filter(node -> node.getArguments().stream()
                        .anyMatch(argument -> argument.toString().equals("matureTrees")))
                .count());

        MethodTree identity = parseMethod(fruit, "dailyItemIdentity", 1);
        assertTrue(identity.toString().contains("kind"));
    }

    @Test
    void treeDailyCursorUsesFrozenContextSeasonInsteadOfGlobalTime() throws IOException {
        ManagerContract tree = MANAGERS.get(1);
        MethodTree create = parseMethod(tree, "createDailyWorkUnit", 2);
        assertTrue(invocations(create).stream().anyMatch(call -> methodName(call).equals("season")
                && call.getMethodSelect().toString().equals("context.season")));
        MethodTree process = parseMethod(tree, tree.entryMethod(), 3);
        assertFalse(invocations(process).stream().anyMatch(call -> methodName(call).equals("currentSeason")));
        assertTrue(invocations(process).stream().anyMatch(call -> methodName(call).equals("processSaplingDay")
                && call.getArguments().stream().anyMatch(argument -> argument.toString().equals("season"))));
    }

    @Test
    void managerEntryMethodsRetainBusinessMutationsAndDirtyRegistrationCleanup() throws IOException {
        MethodTree crop = parseMethod(MANAGERS.get(0), "processCropDay", 2);
        assertCalls(crop, "growCropOneDay", "setDirty", "tryRoll", "removeCrop");
        assertTrue(crop.toString().contains("MOISTURE"));

        MethodTree tree = parseMethod(MANAGERS.get(1), "processRegisteredSaplingDay", 3);
        assertCalls(tree, "processSaplingDay", "removeSapling");

        MethodTree fruit = parseMethod(MANAGERS.get(2), "processRegisteredTreeDay", 2);
        assertCalls(fruit, "processSaplingDay", "processMatureTreeDay");
        assertTrue(fruit.toString().contains("SAPLING"));
        assertTrue(fruit.toString().contains("MATURE"));

        MethodTree sprinkler = parseMethod(MANAGERS.get(3), "processSprinklerDay", 2);
        assertCalls(sprinkler, "removeSprinkler", "waterNow");
        assertTrue(sprinkler.toString().contains("SprinklerBlock"));
    }

    private static void assertCalls(MethodTree method, String... expectedNames) {
        List<String> names = invocations(method).stream()
                .map(RegisteredDailyManagerWorkUnitTest::methodName).toList();
        for (String expected : expectedNames) {
            assertTrue(names.contains(expected), method.getName() + " must call " + expected);
        }
    }

    private static int indexOfAssignment(List<AssignmentTree> assignments, String field, String value) {
        for (int index = 0; index < assignments.size(); index++) {
            AssignmentTree assignment = assignments.get(index);
            if (assignment.getVariable().toString().equals(field)
                    && assignment.getExpression().toString().equals(value)) {
                return index;
            }
        }
        return -1;
    }

    private static int variableIndexWithInvocation(List<VariableTree> variables, String invocationName) {
        for (int index = 0; index < variables.size(); index++) {
            Tree initializer = variables.get(index).getInitializer();
            if (initializer != null && invocations(initializer).stream()
                    .anyMatch(call -> methodName(call).equals(invocationName))) {
                return index;
            }
        }
        return -1;
    }

    private static int directStatementIndexContaining(MethodTree method, String text) {
        List<? extends Tree> statements = method.getBody().getStatements();
        for (int index = 0; index < statements.size(); index++) {
            if (statements.get(index).toString().contains(text)) {
                return index;
            }
        }
        return -1;
    }

    private static int directStatementIndex(MethodTree method, Class<? extends Tree> type) {
        List<? extends Tree> statements = method.getBody().getStatements();
        for (int index = 0; index < statements.size(); index++) {
            if (type.isInstance(statements.get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static void assertInvocation(Tree tree, String expectedName) {
        assertTrue(tree instanceof MethodInvocationTree, () -> "expected invocation: " + tree);
        assertEquals(expectedName, methodName((MethodInvocationTree) tree));
    }

    private static boolean isIdentifier(Tree tree, String expectedName) {
        Tree current = tree;
        while (current instanceof ParenthesizedTree parenthesized) {
            current = parenthesized.getExpression();
        }
        return current instanceof IdentifierTree identifier
                && identifier.getName().contentEquals(expectedName);
    }

    private static String methodName(MethodInvocationTree invocation) {
        String select = invocation.getMethodSelect().toString();
        int separator = select.lastIndexOf('.');
        return separator < 0 ? select : select.substring(separator + 1);
    }

    private static List<MethodInvocationTree> invocations(Tree tree) {
        return scan(tree, MethodInvocationTree.class);
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

    private static MethodTree parseMethod(
            ManagerContract manager, String methodName, int parameterCount) throws IOException {
        Path source = PROJECT.resolve("src/main/java/com/stardew/craft/manager/" + manager.className() + ".java");
        return parseMethod(source, manager.className(), methodName, parameterCount);
    }

    private static MethodTree parseMethod(
            Path sourcePath, String className, String methodName, int parameterCount) throws IOException {
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
            List<String> parseErrors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(Object::toString)
                    .toList();
            assertTrue(parseErrors.isEmpty(), () -> "source did not parse: " + String.join("; ", parseErrors));

            ClassTree targetClass = unit.getTypeDecls().stream()
                    .filter(ClassTree.class::isInstance)
                    .map(ClassTree.class::cast)
                    .filter(candidate -> candidate.getSimpleName().contentEquals(className))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("class is missing: " + className));
            return targetClass.getMembers().stream()
                    .filter(MethodTree.class::isInstance)
                    .map(MethodTree.class::cast)
                    .filter(candidate -> candidate.getName().contentEquals(methodName))
                    .filter(candidate -> candidate.getParameters().size() == parameterCount)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "method is missing: " + methodName + "/" + parameterCount));
        }
    }

    private record ManagerContract(
            String className,
            String legacyMethod,
            String registryField,
            String processingField,
            String cleanupMethod,
            String entryMethod) {
    }
}
