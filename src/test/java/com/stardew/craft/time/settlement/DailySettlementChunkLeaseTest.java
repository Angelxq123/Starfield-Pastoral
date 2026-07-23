package com.stardew.craft.time.settlement;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
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
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySettlementChunkLeaseTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir", "."));
    private static final Path MANAGERS = PROJECT.resolve("src/main/java/com/stardew/craft/manager");
    private static final List<ManagerMethod> LEASED_MUTATIONS = List.of(
            new ManagerMethod("CropGrowthManager", "processCropDay", 2, "leasePosition",
                    List.of("isLoaded", "getBlockState", "removeCrop", "growCropOneDay", "tryRoll")),
            new ManagerMethod("SprinklerManager", "processSprinklerDay", 2, "leasePosition",
                    List.of("isLoaded", "getBlockState", "removeSprinkler", "waterNow")),
            new ManagerMethod("TreeGrowthManager", "processRegisteredSaplingDay", 3, "leasePosition",
                    List.of("isLoaded", "getBlockState", "removeSapling", "processSaplingDay")),
            new ManagerMethod("FruitTreeGrowthManager", "processRegisteredTreeDay", 2, "leasePosition",
                    List.of("isLoaded", "getBlockState", "removeSapling", "processSaplingDay",
                            "removeMatureTree", "processMatureTreeDay")),
            new ManagerMethod("WildTreeSeedManager", "processTreeDay", 4, "leasePosition",
                    List.of("isLoaded", "getBlockState", "tryMigrateGeneratedTreeMarker", "isFullTree",
                            "tryPlaceSapling", "addSapling")),
            new ManagerMethod("AnimalGrowthManager", "processAnimalDay", -1, "leaseBounds",
                    List.of("applyDayUpdate")),
            new ManagerMethod("AnimalGrowthManager", "processReproductionDay", -1, "leaseBounds",
                    List.of("createAnimal")),
            new ManagerMethod("AnimalGrowthManager", "syncAnimalEntityDay", -1, "leaseBounds",
                    List.of("syncOne")));

    @Test
    void animalSettlementDispositionDistinguishesActiveInactiveAndMissingBuildings() {
        assertEquals("SYNC_ACTIVE", settlementDisposition(true, true));
        assertEquals("KEEP_INACTIVE", settlementDisposition(false, true));
        assertEquals("REMOVE_ORPHAN", settlementDisposition(false, false));
    }

    @Test
    void cropLeaseCoversGiantCropFootprint() throws IOException {
        assertPositionLease("CropGrowthManager", "processCropDay", 2, 1);
    }

    @Test
    void sprinklerLeaseCoversIridiumWateringFootprint() throws IOException {
        assertPositionLease("SprinklerManager", "processSprinklerDay", 2, 2);
    }

    @Test
    void treeLeaseKeepsExistingStructureRadius() throws IOException {
        assertPositionLease("TreeGrowthManager", "processRegisteredSaplingDay", 3, 8);
    }

    @Test
    void fruitTreeLeaseKeepsExistingStructureRadius() throws IOException {
        assertPositionLease("FruitTreeGrowthManager", "processRegisteredTreeDay", 2, 8);
    }

    @Test
    void wildTreeLeaseKeepsExistingStructureRadius() throws IOException {
        assertPositionLease("WildTreeSeedManager", "processTreeDay", 4, 8);
    }

    @Test
    void animalProcessLeaseCoversDoorFallbackBounds() throws IOException {
        assertExpandedAnimalLease("processAnimalDay");
    }

    @Test
    void animalReproductionLeaseCoversDoorFallbackBounds() throws IOException {
        assertExpandedAnimalLease("processReproductionDay");
    }

    @Test
    void animalFinalizeLeaseCoversDoorFallbackBounds() throws IOException {
        assertExpandedAnimalLease("syncAnimalEntityDay");
    }

    @Test
    void eligibilityChecksDoNotLoadAndAllRegisteredWorldAccessIsInsideLeaseTry()
            throws IOException {
        ParsedClass helper = parse(
                PROJECT.resolve("src/main/java/com/stardew/craft/farm/FarmDailyProcessHelper.java"),
                "FarmDailyProcessHelper");
        MethodTree eligibility = helper.method("shouldProcessPosition", 2);
        List<String> eligibilityCalls = invocations(eligibility).stream()
                .map(DailySettlementChunkLeaseTest::methodName).toList();
        assertFalse(eligibilityCalls.contains("ensurePositionLoaded"));
        assertFalse(eligibilityCalls.contains("ensurePositionNeighborhoodLoaded"));
        assertFalse(eligibilityCalls.contains("leasePosition"));
        assertFalse(eligibilityCalls.contains("getChunk"));

        for (ManagerMethod contract : LEASED_MUTATIONS) {
            MethodTree method = parseManager(contract.className()).method(
                    contract.methodName(), contract.parameterCount());
            TryTree leaseTry = leaseTry(method, contract.leaseMethod());
            String body = method.getBody().toString();
            int eligibilityIndex = body.indexOf(contract.className().equals("AnimalGrowthManager")
                    ? "shouldProcessBuildingToday" : "shouldProcessPosition");
            int leaseIndex = body.indexOf(contract.leaseMethod());
            assertTrue(eligibilityIndex >= 0 && eligibilityIndex < leaseIndex,
                    contract.className() + "." + contract.methodName()
                            + " must filter eligibility before acquiring");
            assertEveryNamedCallIsInsideLease(method, leaseTry, contract.mutationCalls());
        }
    }

    @Test
    void animalFinalizeIsPerFrozenAnimalItemAndSyncsOnlyOneEntityInsideItsLease()
            throws IOException {
        ParsedClass animal = parseManager("AnimalGrowthManager");
        MethodTree create = animal.method("createDailyWorkUnit", 2);
        MethodInvocationTree finalizeCursor = invocations(create).stream()
                .filter(call -> methodName(call).equals("cursor"))
                .filter(call -> !call.getArguments().isEmpty()
                        && call.getArguments().getFirst().toString().equals("\"animal_daily_finalize\""))
                .findFirst().orElseThrow();
        assertEquals("animalSnapshot", finalizeCursor.getArguments().get(1).toString());
        assertTrue(finalizeCursor.getArguments().get(3).toString().contains("syncAnimalEntityDay"));
        assertFalse(invocations(animal.type()).stream()
                .anyMatch(call -> methodName(call).equals("syncAll")));

        ParsedClass service = parse(
                PROJECT.resolve("src/main/java/com/stardew/craft/animal/service/AnimalEntitySyncService.java"),
                "AnimalEntitySyncService");
        MethodTree syncOne = service.method("syncOne", 3);
        assertTrue(scan(syncOne, EnhancedForLoopTree.class).isEmpty());
        assertTrue(scan(syncOne, ForLoopTree.class).isEmpty());
        List<String> calls = invocations(syncOne).stream()
                .map(DailySettlementChunkLeaseTest::methodName).toList();
        assertTrue(calls.contains("findLoaded"));
        assertTrue(calls.contains("spawnEntityForRecord"));
        assertTrue(calls.contains("applyAuthoritativeState"));
        assertFalse(calls.contains("syncAll"));
    }

    @Test
    void animalFinalizeBindsAllBuildingStatesAndRemovesOrphansPerItem() throws IOException {
        ParsedClass animal = parseManager("AnimalGrowthManager");
        MethodTree sync = animal.method("syncAnimalEntityDay", -1);
        List<String> syncCalls = invocations(sync).stream()
                .map(DailySettlementChunkLeaseTest::methodName).toList();
        assertTrue(syncCalls.contains("getBuilding"));
        assertTrue(syncCalls.contains("getBuildingIncludingInactive"));
        assertTrue(syncCalls.contains("settlementDisposition"));
        assertTrue(syncCalls.contains("removeOrphanAnimal"),
                "missing buildings must remove the orphan instead of returning");
        assertTrue(scan(sync, IfTree.class).stream()
                .filter(branch -> branch.getCondition().toString().contains("KEEP_INACTIVE"))
                .anyMatch(branch -> scan(branch.getThenStatement(), com.sun.source.tree.ReturnTree.class)
                        .size() == 1));
        assertTrue(scan(sync, IfTree.class).stream()
                .filter(branch -> branch.getCondition().toString().contains("REMOVE_ORPHAN"))
                .anyMatch(branch -> invocations(branch.getThenStatement()).stream()
                        .anyMatch(call -> methodName(call).equals("removeOrphanAnimal"))));

        MethodTree remove = animal.method("removeOrphanAnimal", -1);
        TryTree leaseTry = leaseTry(remove, "leasePosition");
        assertEquals("0", resourceInvocation(leaseTry).getArguments().get(2).toString());
        assertEveryNamedCallIsInsideLease(remove, leaseTry, List.of("removeLoaded"));
        assertTrue(invocations(remove).stream()
                .anyMatch(call -> methodName(call).equals("removeAnimal")),
                "orphan cleanup must remove the authoritative data record");
    }

    @Test
    void reproductionDefersNewbornProjectionUntilAStableCursorAfterCreation() throws IOException {
        ParsedClass animal = parseManager("AnimalGrowthManager");
        MethodTree create = animal.method("createDailyWorkUnit", 2);
        List<MethodInvocationTree> createCalls = invocations(create);
        assertTrue(createCalls.stream()
                .filter(call -> methodName(call).equals("deferred"))
                .anyMatch(call -> call.getArguments().getFirst().toString()
                        .equals("\"animal_newborn_sync\"")));

        MethodTree reproduction = animal.method("processReproductionDay", -1);
        List<String> reproductionCalls = invocations(reproduction).stream()
                .map(DailySettlementChunkLeaseTest::methodName).toList();
        assertTrue(reproductionCalls.contains("createAnimal"));
        assertTrue(reproductionCalls.contains("add"));
        assertFalse(reproductionCalls.contains("syncOne"),
                "a retryable reproduction item must not sync after creating its newborn");
    }

    @Test
    void dailyRootScopeOwnsFallbackCleanupAndLegacyTimeLifecycleHasNoGlobalInteriorForce()
            throws IOException {
        ParsedClass helper = parse(
                PROJECT.resolve("src/main/java/com/stardew/craft/farm/FarmDailyProcessHelper.java"),
                "FarmDailyProcessHelper");
        MethodTree begin = helper.method("beginDailyProcess", 1);
        MethodTree end = helper.method("endDailyProcess", 1);
        assertTrue(invocations(begin).stream().anyMatch(call ->
                methodName(call).equals("beginDailySettlementChunkLeaseScope")));
        assertTrue(invocations(end).stream().anyMatch(call -> methodName(call).equals("close")));
        assertTrue(end.getBody().toString().contains("finally"));

        String timeSource = Files.readString(
                PROJECT.resolve("src/main/java/com/stardew/craft/time/StardewTimeManager.java"));
        assertFalse(timeSource.contains("setInteriorChunksForced(stardewLevel, true, \"daily_settlement\")"));
        assertFalse(timeSource.contains("setInteriorChunksForced(stardewLevel, false, \"daily_settlement_done\")"));
        assertTrue(timeSource.contains("FarmDailyProcessHelper.beginDailyProcess(stardewLevel)"));
        assertTrue(timeSource.contains("FarmDailyProcessHelper.endDailyProcess(stardewLevel)"));
    }

    private static void assertPositionLease(
            String className, String methodName, int parameterCount, int radius) throws IOException {
        MethodTree method = parseManager(className).method(methodName, parameterCount);
        TryTree leaseTry = leaseTry(method, "leasePosition");
        MethodInvocationTree leaseCall = resourceInvocation(leaseTry);
        assertEquals(List.of("level", "pos", Integer.toString(radius)),
                leaseCall.getArguments().stream().map(Object::toString).toList());
        assertTrue(method.getBody().toString().indexOf("globalPos.pos()")
                        < method.getBody().toString().indexOf("leasePosition"),
                className + " must lease the registered GlobalPos position");
    }

    private static void assertExpandedAnimalLease(String methodName) throws IOException {
        MethodTree method = parseManager("AnimalGrowthManager").method(methodName, -1);
        TryTree leaseTry = leaseTry(method, "leaseBounds");
        MethodInvocationTree leaseCall = resourceInvocation(leaseTry);
        assertEquals(List.of(
                "level",
                "new BlockPos(building.minX() - 1, building.minY(), building.minZ() - 1)",
                "new BlockPos(building.maxX() + 1, building.maxY(), building.maxZ() + 1)"),
                leaseCall.getArguments().stream().map(Object::toString).toList(),
                methodName + " must cover the legacy door scan without loading unrelated chunks");
    }

    private static String settlementDisposition(boolean active, boolean includingInactive) {
        return org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            Class<?> service = Class.forName(
                    "com.stardew.craft.animal.service.AnimalEntitySyncService");
            Method method = service.getDeclaredMethod(
                    "settlementDisposition", boolean.class, boolean.class);
            method.setAccessible(true);
            return method.invoke(null, active, includingInactive).toString();
        });
    }

    private static void assertEveryNamedCallIsInsideLease(
            MethodTree method, TryTree leaseTry, List<String> mutationCalls) {
        List<MethodInvocationTree> allCalls = invocations(method);
        List<MethodInvocationTree> leasedCalls = invocations(leaseTry.getBlock());
        for (String mutationCall : mutationCalls) {
            long total = allCalls.stream().filter(call -> methodName(call).equals(mutationCall)).count();
            long inside = leasedCalls.stream().filter(call -> methodName(call).equals(mutationCall)).count();
            assertTrue(total > 0, method.getName() + " must invoke " + mutationCall);
            assertEquals(total, inside,
                    method.getName() + " must keep every " + mutationCall + " call inside its lease");
        }
    }

    private static TryTree leaseTry(MethodTree method, String leaseMethod) {
        return scan(method, TryTree.class).stream()
                .filter(candidate -> candidate.getResources().stream()
                        .filter(VariableTree.class::isInstance)
                        .map(VariableTree.class::cast)
                        .map(VariableTree::getInitializer)
                        .filter(MethodInvocationTree.class::isInstance)
                        .map(MethodInvocationTree.class::cast)
                        .anyMatch(call -> methodName(call).equals(leaseMethod)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        method.getName() + " is missing try-with-resources " + leaseMethod));
    }

    private static MethodInvocationTree resourceInvocation(TryTree leaseTry) {
        VariableTree resource = (VariableTree) leaseTry.getResources().getFirst();
        return (MethodInvocationTree) resource.getInitializer();
    }

    private static ParsedClass parseManager(String className) throws IOException {
        return parse(MANAGERS.resolve(className + ".java"), className);
    }

    private static ParsedClass parse(Path sourcePath, String className) throws IOException {
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
                    .map(Object::toString).toList();
            assertTrue(errors.isEmpty(), () -> "source did not parse: " + String.join("; ", errors));
            ClassTree type = unit.getTypeDecls().stream()
                    .filter(ClassTree.class::isInstance)
                    .map(ClassTree.class::cast)
                    .filter(candidate -> candidate.getSimpleName().contentEquals(className))
                    .findFirst().orElseThrow();
            return new ParsedClass(type);
        }
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

    private record ManagerMethod(
            String className,
            String methodName,
            int parameterCount,
            String leaseMethod,
            List<String> mutationCalls) {
    }

    private record ParsedClass(ClassTree type) {
        MethodTree method(String name, int parameterCount) {
            return type.getMembers().stream()
                    .filter(MethodTree.class::isInstance)
                    .map(MethodTree.class::cast)
                    .filter(method -> method.getName().contentEquals(name))
                    .filter(method -> parameterCount < 0 || method.getParameters().size() == parameterCount)
                    .findFirst().orElseThrow(() -> new AssertionError(
                            "method is missing: " + type.getSimpleName() + "." + name));
        }
    }
}
