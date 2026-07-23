package com.stardew.craft.time.settlement;

import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmSystemDailyWorkUnitTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir", "."));
    private static final List<SystemContract> SYSTEMS = List.of(
            new SystemContract("manager/AnimalGrowthManager.java", "AnimalGrowthManager",
                    "growDaily", 1, "processAnimalDay", "forId"),
            new SystemContract("fishpond/service/FishPondDailyUpdateService.java", "FishPondDailyUpdateService",
                    "onNewDay", 1, "processPondDay", "forId"),
            new SystemContract("manager/PastureGrassGrowthManager.java", "PastureGrassGrowthManager",
                    "growDaily", 1, "processPastureGrassDay", "forPosition"),
            new SystemContract("manager/WildTreeSeedManager.java", "WildTreeSeedManager",
                    "onNewDay", 2, "processTreeDay", "forPosition"));

    @Test
    void oneAndHundredItemBudgetsProduceIdenticalObjectDecisions() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (long id = 1; id <= 137; id++) {
            ids.add(id);
        }

        assertEquals(runBudgeted(ids, 1), runBudgeted(ids, 100));
    }

    @Test
    void legacyEntrypointsOnlyDrainTheirCreatedWorkUnit() throws IOException {
        for (SystemContract system : SYSTEMS) {
            MethodTree legacy = parse(system).method(system.legacyMethod(), system.legacyParameterCount());
            List<MethodInvocationTree> calls = scan(legacy.getBody(), MethodInvocationTree.class);

            assertEquals(1, calls.stream().filter(call -> methodName(call).equals("drain")).count(),
                    system.className() + " must drain once");
            assertEquals(1, calls.stream().filter(call -> methodName(call).equals("createDailyWorkUnit")).count(),
                    system.className() + " must create the drained unit");
            assertFalse(calls.stream().anyMatch(call -> methodName(call).equals(system.itemMethod())),
                    system.className() + " legacy entrypoint must not process items directly");
        }
    }

    @Test
    void systemsExposeContextualCursorFactoriesWithFrozenSnapshots() throws IOException {
        for (SystemContract system : SYSTEMS) {
            ParsedClass parsed = parse(system);
            MethodTree create = parsed.method("createDailyWorkUnit", 2);

            assertEquals(List.of("ServerLevel", "DailySettlementContext"), create.getParameters().stream()
                    .map(parameter -> parameter.getType().toString()).toList());
            assertTrue(invokes(create, "cursor"), system.className() + " must create cursor work");
            assertTrue(invokes(create, "absoluteDay"), system.className() + " must use context.absoluteDay()");
            assertTrue(scan(create.getBody(), VariableTree.class).stream()
                            .anyMatch(variable -> variable.getName().toString().toLowerCase().contains("snapshot")),
                    system.className() + " must name and freeze a snapshot at creation");
        }
    }

    @Test
    void cursorBodiesUseOnlyObjectDerivedDailyRandomStreams() throws IOException {
        for (SystemContract system : SYSTEMS) {
            MethodTree item = parse(system).method(system.itemMethod(), -1);
            List<MethodInvocationTree> calls = scan(item.getBody(), MethodInvocationTree.class);

            assertTrue(calls.stream().anyMatch(call -> methodName(call).equals(system.randomFactory())
                            && call.getMethodSelect().toString().startsWith("DailySettlementRandom.")),
                    system.className() + " item body must derive its object random stream");
            assertFalse(calls.stream().anyMatch(call -> methodName(call).equals("getRandom")),
                    system.className() + " item body must not call level.getRandom()");
            assertFalse(scan(item.getBody(), MemberSelectTree.class).stream()
                            .anyMatch(select -> select.getIdentifier().contentEquals("random")
                                    && select.getExpression().toString().equals("level")),
                    system.className() + " item body must not read level.random");
        }
    }

    @Test
    void activeOwnershipIsGuardedAndRecoveredOnCreationFailureAndClose() throws IOException {
        for (SystemContract system : SYSTEMS) {
            MethodTree create = parse(system).method("createDailyWorkUnit", 2);

            assertFalse(scan(create.getBody(), IfTree.class).isEmpty(),
                    system.className() + " must reject repeat active creation");
            List<TryTree> tries = scan(create.getBody(), TryTree.class);
            assertFalse(tries.isEmpty(), system.className() + " must protect snapshot creation");
            assertTrue(tries.stream().flatMap(tree -> tree.getCatches().stream())
                            .map(CatchTree::getBlock)
                            .anyMatch(block -> invokes(block, "finishDailyProcessing")),
                    system.className() + " must recover ownership after creation failure");
            assertTrue(invokes(create, "finishDailyProcessing"),
                    system.className() + " cursor close must release ownership");
        }
    }

    @Test
    void wildTreeSnapshotDoesNotRetainLiveMapEntries() throws IOException {
        MethodTree create = parse(SYSTEMS.get(3)).method("createDailyWorkUnit", 2);

        assertFalse(scan(create.getBody(), VariableTree.class).stream()
                        .anyMatch(variable -> variable.getType() != null
                                && variable.getType().toString().contains("Map.Entry")),
                "wild tree cursor snapshot must copy entry fields instead of retaining live Map.Entry values");
    }

    private static Map<Long, ObjectDecision> runBudgeted(List<Long> ids, int budget) throws Exception {
        Map<Long, ObjectDecision> decisions = new LinkedHashMap<>();
        DailySettlementWorkUnit unit = DailySettlementWorkUnits.cursor(
                "objects", ids, Object::toString, id -> {
                    var random = DailySettlementRandom.forId(9918273L, 47, "budget_test", id);
                    decisions.put(id, new ObjectDecision(
                            random.nextDouble() < 0.65D,
                            random.nextInt(5),
                            random.nextLong()));
                }, () -> {});
        try (unit) {
            while (!unit.isComplete()) {
                for (int spent = 0; spent < budget && !unit.isComplete(); spent++) {
                    unit.runNext();
                }
            }
        }
        return decisions;
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

    private static boolean invokes(Tree tree, String name) {
        return scan(tree, MethodInvocationTree.class).stream()
                .anyMatch(call -> methodName(call).equals(name));
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

    private record ObjectDecision(boolean recordChanged, int blockVariant, long placementRoll) {
    }

    private record SystemContract(
            String relativePath,
            String className,
            String legacyMethod,
            int legacyParameterCount,
            String itemMethod,
            String randomFactory) {
    }

    private record ParsedClass(ClassTree classTree) {
        private MethodTree method(String name, int parameterCount) {
            return classTree.getMembers().stream()
                    .filter(MethodTree.class::isInstance)
                    .map(MethodTree.class::cast)
                    .filter(candidate -> candidate.getName().contentEquals(name))
                    .filter(candidate -> parameterCount < 0 || candidate.getParameters().size() == parameterCount)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("method is missing: " + name));
        }
    }
}
