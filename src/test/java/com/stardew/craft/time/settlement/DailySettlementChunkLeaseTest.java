package com.stardew.craft.time.settlement;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
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
                    List.of("isLoaded", "getBlockState", "removeCrop", "growOneDay", "tryRoll")),
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
                    List.of("resolveBuildingUtilities")),
            new ManagerMethod("AnimalGrowthManager", "processReproductionDay", -1, "leaseBounds",
                    List.of("queueAnimalBirth")),
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
        VariableTree entityWork = uniqueVariable(create, "entityWork");
        MethodInvocationTree finalizeCursor = asInvocation(entityWork.getInitializer());
        assertMemberCall(finalizeCursor, "DailySettlementWorkUnits", "cursor", 5);
        assertStringLiteral(finalizeCursor.getArguments().getFirst(), "animal_entity_sync");
        assertIdentifier(finalizeCursor.getArguments().get(1), "animalSnapshot");
        LambdaExpressionTree finalizeConsumer = asLambda(finalizeCursor.getArguments().get(3));
        MethodInvocationTree finalizeCall = asInvocation(finalizeConsumer.getBody());
        assertUnqualifiedCall(finalizeCall, "syncAnimalEntityDay", 3);
        assertIdentifiers(finalizeCall.getArguments(), "level", "worldData", "animalId");
        assertFalse(invocations(create).stream()
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
    void animalFinalizeBindsAllBuildingStatesAndPreservesOrphanRecords() throws IOException {
        ParsedClass animal = parseManager("AnimalGrowthManager");
        MethodTree sync = animal.method("syncAnimalEntityDay", -1);
        VariableTree building = uniqueVariable(sync, "building");
        assertOptionalBuildingLookup(building, "getBuilding");
        VariableTree includingInactive = uniqueVariable(sync, "includingInactive");
        assertOptionalBuildingLookup(includingInactive, "getBuildingIncludingInactive");

        VariableTree disposition = uniqueVariable(sync, "disposition");
        MethodInvocationTree dispositionCall = asInvocation(disposition.getInitializer());
        assertMemberCall(dispositionCall, "AnimalEntitySyncService", "settlementDisposition", 2);
        assertNotNullCheck(dispositionCall.getArguments().get(0), "building");
        assertNotNullCheck(dispositionCall.getArguments().get(1), "includingInactive");

        IfTree keepInactive = dispositionBranch(sync, "KEEP_INACTIVE");
        assertDirectReturnsOnly(keepInactive.getThenStatement());
        IfTree removeOrphan = dispositionBranch(sync, "REMOVE_ORPHAN");
        BlockTree removeBlock = asBlock(removeOrphan.getThenStatement());
        TryTree orphanLease = scan(removeBlock, TryTree.class).stream()
                .filter(tree -> methodName(resourceInvocation(tree)).equals("leasePosition"))
                .findFirst().orElseThrow();
        MethodInvocationTree removeLoaded = uniqueInvocation(orphanLease.getBlock(), "removeLoaded");
        assertMemberCall(removeLoaded, "AnimalEntitySyncService", "removeLoaded", 2);
        assertIdentifier(removeLoaded.getArguments().get(0), "level");
        assertIdentifier(removeLoaded.getArguments().get(1), "animalId");
        assertEquals(Tree.Kind.RETURN, removeBlock.getStatements().getLast().getKind());
        assertTrue(invocations(sync.getBody()).stream()
                        .noneMatch(call -> methodName(call).equals("removeAnimal")),
                "official 0.5.3 quarantine behavior must preserve authoritative orphan records");

        TryTree activeLease = leaseTry(sync, "leaseBounds");
        assertTrue(statementIndex(sync, keepInactive) < statementIndex(sync, removeOrphan));
        assertTrue(statementIndex(sync, removeOrphan) < statementIndex(sync, activeLease));
        MethodInvocationTree syncOne = uniqueInvocation(activeLease.getBlock(), "syncOne");
        assertMemberCall(syncOne, "AnimalEntitySyncService", "syncOne", 3);
        assertIdentifiers(syncOne.getArguments(), "level", "worldData", "record");
    }

    @Test
    void reproductionUsesOneFrozenFarmPerCursorItemAndQueuesBirthInsideLease() throws IOException {
        ParsedClass animal = parseManager("AnimalGrowthManager");
        MethodTree create = animal.method("createDailyWorkUnit", 2);
        VariableTree snapshot = uniqueVariable(create, "reproductionSnapshot");
        assertTrue(snapshot.getInitializer().toString().contains("snapshotReproductionFarms"));

        VariableTree reproductionWork = uniqueVariable(create, "reproductionWork");
        MethodInvocationTree reproductionCursor = asInvocation(reproductionWork.getInitializer());
        assertIdentifier(reproductionCursor.getArguments().get(1), "reproductionSnapshot");
        LambdaExpressionTree reproductionConsumer =
                asLambda(reproductionCursor.getArguments().get(3));
        MethodInvocationTree reproductionCall = asInvocation(reproductionConsumer.getBody());
        assertUnqualifiedCall(reproductionCall, "processReproductionDay", 4);

        MethodTree reproduction = animal.method("processReproductionDay", -1);
        TryTree reproductionLease = leaseTry(reproduction, "leaseBounds");
        MethodInvocationTree queueBirth = uniqueInvocation(reproductionLease.getBlock(), "queueAnimalBirth");
        assertMemberCall(queueBirth, "worldData", "queueAnimalBirth", 5);
        assertTrue(invocations(reproduction).stream()
                        .noneMatch(call -> methodName(call).equals("syncOne")),
                "reproduction must leave projection work to later lifecycle handling");

        MethodInvocationTree sequence = uniqueInvocation(create, "sequence");
        MethodInvocationTree children = asInvocation(sequence.getArguments().get(1));
        assertMemberCall(children, "List", "of", 7);
        assertIdentifiers(children.getArguments(),
                "constructionWork", "animalWork", "reproductionWork", "reproductionPublishWork",
                "projectionWork", "entityWork", "publishWork");
    }

    @Test
    void dailyRootScopeOwnsFallbackCleanupAndLegacyTimeLifecycleHasNoGlobalInteriorForce()
            throws IOException {
        ParsedClass helper = parse(
                PROJECT.resolve("src/main/java/com/stardew/craft/farm/FarmDailyProcessHelper.java"),
                "FarmDailyProcessHelper");
        MethodTree begin = helper.method("beginDailyProcess", 3);
        MethodTree end = helper.method("endDailyProcess", 1);
        assertTrue(invocations(begin).stream().anyMatch(call ->
                methodName(call).equals("beginDailySettlementChunkLeaseScope")));
        assertTrue(invocations(end).stream().anyMatch(call -> methodName(call).equals("close")));
        assertTrue(end.getBody().toString().contains("finally"));

        String timeSource = Files.readString(
                PROJECT.resolve("src/main/java/com/stardew/craft/time/StardewTimeManager.java"));
        String planSource = Files.readString(
                PROJECT.resolve("src/main/java/com/stardew/craft/time/settlement/DailySettlementPlanFactory.java"));
        assertFalse(timeSource.contains("setInteriorChunksForced(stardewLevel, true, \"daily_settlement\")"));
        assertFalse(timeSource.contains("setInteriorChunksForced(stardewLevel, false, \"daily_settlement_done\")"));
        assertFalse(timeSource.contains("FarmDailyProcessHelper.beginDailyProcess"));
        assertFalse(timeSource.contains("FarmDailyProcessHelper.endDailyProcess"));
        assertTrue(planSource.contains("FarmDailyProcessHelper.beginDailyProcess"));
        assertTrue(planSource.contains("FarmDailyProcessHelper.endDailyProcess"));
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

    private static void assertOptionalBuildingLookup(VariableTree variable, String lookupName) {
        MethodInvocationTree orElse = asInvocation(variable.getInitializer());
        MemberSelectTree orElseSelect = asMemberSelect(orElse.getMethodSelect());
        assertEquals("orElse", orElseSelect.getIdentifier().toString());
        assertEquals(1, orElse.getArguments().size());
        assertEquals(Tree.Kind.NULL_LITERAL, unwrap(orElse.getArguments().getFirst()).getKind());

        MethodInvocationTree lookup = asInvocation(orElseSelect.getExpression());
        assertMemberCall(lookup, "worldData", lookupName, 1);
        assertNoArgMemberCall(lookup.getArguments().getFirst(), "record", "buildingId");
    }

    private static IfTree dispositionBranch(MethodTree method, String constant) {
        List<IfTree> matches = scan(method.getBody(), IfTree.class).stream()
                .filter(branch -> isDispositionComparison(branch.getCondition(), constant))
                .toList();
        assertEquals(1, matches.size(), "expected one disposition branch for " + constant);
        return matches.getFirst();
    }

    private static boolean isDispositionComparison(ExpressionTree expression, String constant) {
        ExpressionTree unwrapped = unwrap(expression);
        if (!(unwrapped instanceof BinaryTree binary)
                || binary.getKind() != Tree.Kind.EQUAL_TO) {
            return false;
        }
        return isIdentifier(binary.getLeftOperand(), "disposition")
                && qualifiedName(binary.getRightOperand()).equals(
                        "AnimalEntitySyncService.SettlementDisposition." + constant);
    }

    private static IfTree identifierNotNullBranch(MethodTree method, String identifier) {
        List<IfTree> matches = method.getBody().getStatements().stream()
                .filter(IfTree.class::isInstance)
                .map(IfTree.class::cast)
                .filter(branch -> isNotNullCheck(branch.getCondition(), identifier))
                .toList();
        assertEquals(1, matches.size(), "expected one top-level null check for " + identifier);
        return matches.getFirst();
    }

    private static IfTree invocationConditionBranch(
            MethodTree method, String receiver, String invocationName) {
        List<IfTree> matches = method.getBody().getStatements().stream()
                .filter(IfTree.class::isInstance)
                .map(IfTree.class::cast)
                .filter(branch -> isMemberCall(
                        unwrap(branch.getCondition()), receiver, invocationName))
                .toList();
        assertEquals(1, matches.size(),
                "expected one top-level branch for " + receiver + "." + invocationName);
        return matches.getFirst();
    }

    private static void assertDirectReturnsOnly(StatementTree statement) {
        BlockTree block = asBlock(statement);
        assertEquals(1, block.getStatements().size());
        assertTrue(block.getStatements().getFirst() instanceof ReturnTree);
    }

    private static VariableTree uniqueVariable(Tree tree, String name) {
        List<VariableTree> matches = scan(tree, VariableTree.class).stream()
                .filter(variable -> variable.getName().contentEquals(name))
                .toList();
        assertEquals(1, matches.size(), "expected one variable named " + name);
        return matches.getFirst();
    }

    private static MethodInvocationTree uniqueInvocation(Tree tree, String name) {
        List<MethodInvocationTree> matches = invocations(tree).stream()
                .filter(invocation -> methodName(invocation).equals(name))
                .toList();
        assertEquals(1, matches.size(), "expected one invocation named " + name);
        return matches.getFirst();
    }

    private static MethodInvocationTree asInvocation(Tree tree) {
        assertTrue(tree instanceof MethodInvocationTree,
                () -> "expected method invocation, got " + tree.getKind());
        return (MethodInvocationTree) tree;
    }

    private static MemberSelectTree asMemberSelect(Tree tree) {
        assertTrue(tree instanceof MemberSelectTree,
                () -> "expected member select, got " + tree.getKind());
        return (MemberSelectTree) tree;
    }

    private static LambdaExpressionTree asLambda(Tree tree) {
        assertTrue(tree instanceof LambdaExpressionTree,
                () -> "expected lambda, got " + tree.getKind());
        return (LambdaExpressionTree) tree;
    }

    private static NewClassTree asNewClass(Tree tree) {
        assertTrue(tree instanceof NewClassTree,
                () -> "expected new class expression, got " + tree.getKind());
        return (NewClassTree) tree;
    }

    private static BlockTree asBlock(Tree tree) {
        assertTrue(tree instanceof BlockTree,
                () -> "expected block, got " + tree.getKind());
        return (BlockTree) tree;
    }

    private static void assertMemberCall(
            MethodInvocationTree invocation, String receiver, String name, int argumentCount) {
        MemberSelectTree select = asMemberSelect(invocation.getMethodSelect());
        assertEquals(receiver, qualifiedName(select.getExpression()));
        assertEquals(name, select.getIdentifier().toString());
        assertEquals(argumentCount, invocation.getArguments().size());
    }

    private static boolean isMemberCall(Tree tree, String receiver, String name) {
        if (!(tree instanceof MethodInvocationTree invocation)
                || !(invocation.getMethodSelect() instanceof MemberSelectTree select)) {
            return false;
        }
        return select.getIdentifier().contentEquals(name)
                && qualifiedName(select.getExpression()).equals(receiver);
    }

    private static void assertUnqualifiedCall(
            MethodInvocationTree invocation, String name, int argumentCount) {
        assertTrue(invocation.getMethodSelect() instanceof IdentifierTree);
        assertEquals(name, ((IdentifierTree) invocation.getMethodSelect()).getName().toString());
        assertEquals(argumentCount, invocation.getArguments().size());
    }

    private static void assertNoArgMemberCall(
            ExpressionTree expression, String receiver, String name) {
        MethodInvocationTree invocation = asInvocation(unwrap(expression));
        assertMemberCall(invocation, receiver, name, 0);
    }

    private static void assertRecordIdCall(ExpressionTree expression) {
        assertNoArgMemberCall(expression, "record", "animalId");
    }

    private static void assertNotNullCheck(ExpressionTree expression, String identifier) {
        assertTrue(isNotNullCheck(expression, identifier),
                () -> "expected " + identifier + " != null");
    }

    private static boolean isNotNullCheck(ExpressionTree expression, String identifier) {
        ExpressionTree unwrapped = unwrap(expression);
        if (!(unwrapped instanceof BinaryTree binary)
                || binary.getKind() != Tree.Kind.NOT_EQUAL_TO) {
            return false;
        }
        return isIdentifier(binary.getLeftOperand(), identifier)
                && unwrap(binary.getRightOperand()).getKind() == Tree.Kind.NULL_LITERAL;
    }

    private static void assertIdentifier(ExpressionTree expression, String expected) {
        assertTrue(isIdentifier(expression, expected), () -> "expected identifier " + expected);
    }

    private static boolean isIdentifier(ExpressionTree expression, String expected) {
        ExpressionTree unwrapped = unwrap(expression);
        return unwrapped instanceof IdentifierTree identifier
                && identifier.getName().contentEquals(expected);
    }

    private static void assertIdentifiers(
            List<? extends ExpressionTree> expressions, String... expected) {
        assertEquals(expected.length, expressions.size());
        for (int index = 0; index < expected.length; index++) {
            assertIdentifier(expressions.get(index), expected[index]);
        }
    }

    private static void assertIntLiteral(ExpressionTree expression, int expected) {
        ExpressionTree unwrapped = unwrap(expression);
        assertTrue(unwrapped instanceof LiteralTree);
        assertEquals(expected, ((LiteralTree) unwrapped).getValue());
    }

    private static void assertStringLiteral(ExpressionTree expression, String expected) {
        ExpressionTree unwrapped = unwrap(expression);
        assertTrue(unwrapped instanceof LiteralTree);
        assertEquals(expected, ((LiteralTree) unwrapped).getValue());
    }

    private static void assertListOfLong(Tree type) {
        assertTrue(type instanceof ParameterizedTypeTree,
                () -> "expected parameterized List<Long>, got " + type.getKind());
        ParameterizedTypeTree parameterized = (ParameterizedTypeTree) type;
        assertEquals("List", qualifiedName(parameterized.getType()));
        assertEquals(1, parameterized.getTypeArguments().size());
        assertEquals("Long", qualifiedName(parameterized.getTypeArguments().getFirst()));
    }

    private static String rawTypeName(Tree type) {
        return type instanceof ParameterizedTypeTree parameterized
                ? qualifiedName(parameterized.getType())
                : qualifiedName(type);
    }

    private static String qualifiedName(Tree tree) {
        if (tree instanceof IdentifierTree identifier) {
            return identifier.getName().toString();
        }
        if (tree instanceof MemberSelectTree select) {
            return qualifiedName(select.getExpression()) + "." + select.getIdentifier();
        }
        return "";
    }

    private static ExpressionTree unwrap(ExpressionTree expression) {
        ExpressionTree current = expression;
        while (current instanceof ParenthesizedTree parenthesized) {
            current = parenthesized.getExpression();
        }
        return current;
    }

    private static int statementIndex(MethodTree method, StatementTree statement) {
        return statementIndex(method.getBody(), statement);
    }

    private static int statementIndex(BlockTree block, StatementTree statement) {
        int index = block.getStatements().indexOf(statement);
        assertTrue(index >= 0, "expected a direct statement in the enclosing block");
        return index;
    }

    private static int containingStatementIndex(BlockTree block, Tree nested) {
        for (int index = 0; index < block.getStatements().size(); index++) {
            StatementTree statement = block.getStatements().get(index);
            if (statement == nested || scan(statement, nested.getClass()).stream()
                    .anyMatch(candidate -> candidate == nested)) {
                return index;
            }
        }
        throw new AssertionError("nested tree is not contained by a direct statement");
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
        ExpressionTree select = invocation.getMethodSelect();
        if (select instanceof IdentifierTree identifier) {
            return identifier.getName().toString();
        }
        if (select instanceof MemberSelectTree memberSelect) {
            return memberSelect.getIdentifier().toString();
        }
        throw new AssertionError("unsupported method select: " + select.getKind());
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
