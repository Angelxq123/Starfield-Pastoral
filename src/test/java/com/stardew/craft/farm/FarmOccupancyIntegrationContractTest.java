package com.stardew.craft.farm;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmOccupancyIntegrationContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir", "."));
    private static final Path MANAGER_SOURCE = sourcePath("farm/FarmChunkManager.java");
    private static final Path PLAYER_HANDLER_SOURCE = sourcePath("player/PlayerDataEventHandler.java");
    private static final Path DIMENSION_HANDLER_SOURCE = sourcePath("event/DimensionEventHandler.java");
    private static final Path ENTRY_PAYLOAD_SOURCE = sourcePath("network/payload/FarmEntryRequestPayload.java");
    private static final Path PORTAL_HANDLER_SOURCE = sourcePath("event/InteriorPortalInteractionEvents.java");

    @Test
    void entryHandlerTracksTheFarmSelectedByTargetOwner() throws IOException {
        MethodTree handle = parseMethod(ENTRY_PAYLOAD_SOURCE, "FarmEntryRequestPayload", "handle", 2);
        List<VariableTree> farms = variables(handle).stream()
                .filter(variable -> variable.getName().contentEquals("farm"))
                .toList();

        assertEquals(1, farms.size());
        assertInvocation(farms.getFirst().getInitializer(), "registry", "getFarm", "payload.targetOwner");
        assertInvocation(handle, "FarmChunkManager.get()", "onPlayerEnterFarm",
                "stardewLevel", "player", "farm");
    }

    @Test
    void loginInValleyReconcilesOccupancyFromCurrentPosition() throws IOException {
        MethodTree login = parseMethod(PLAYER_HANDLER_SOURCE, "PlayerDataEventHandler", "onPlayerLogin", 1);
        IfTree valleyLogin = findIf(login, condition -> normalized(unwrapped(condition)).equals(
                "player.serverLevel().dimension()==com.stardew.craft.core.ModDimensions.STARDEW_VALLEY"));
        BlockTree body = asBlock(valleyLogin.getThenStatement());

        assertDirectInvocation(body, "com.stardew.craft.farm.FarmChunkManager.get()",
                "updatePlayerFarmOccupancy", "player.serverLevel()", "player");
    }

    @Test
    void dimensionLifecycleLeavesAndReconcilesAfterOptionalAutoRouting() throws IOException {
        MethodTree changed = parseMethod(
                DIMENSION_HANDLER_SOURCE, "DimensionEventHandler", "onPlayerChangeDimension", 1);
        IfTree leaveValley = findIf(changed, condition -> isInvocation(
                condition, "ModDimensions.STARDEW_VALLEY", "equals", "event.getFrom()"));
        assertDirectInvocation(asBlock(leaveValley.getThenStatement()),
                "com.stardew.craft.farm.FarmChunkManager.get()", "onPlayerLeaveFarm",
                "player.serverLevel()", "player");

        IfTree enterValley = findIf(changed, condition -> isInvocation(
                condition, "ModDimensions.STARDEW_VALLEY", "equals", "event.getTo()"));
        BlockTree valleyBody = asBlock(enterValley.getThenStatement());
        int autoRoute = statementIndex(valleyBody, statement -> statement instanceof IfTree candidate
                && hasInvocation(candidate.getCondition(), null, "consumeSkipAutoTeleport", "player.getUUID()"));
        int reconcile = statementIndex(valleyBody, statement -> isDirectInvocation(
                statement, "com.stardew.craft.farm.FarmChunkManager.get()",
                "updatePlayerFarmOccupancy", "level", "player"));

        assertTrue(autoRoute >= 0, "Stardew entry must retain optional auto-routing");
        assertTrue(reconcile > autoRoute,
                "occupancy reconciliation must run after auto-routing and outside its condition");
    }

    @Test
    void currentPositionLookupScansFarmBoundsWithoutMembershipResolution() throws IOException {
        MethodTree update = parseMethod(MANAGER_SOURCE, "FarmChunkManager", "updatePlayerFarmOccupancy", 2);
        MethodTree find = parseMethod(MANAGER_SOURCE, "FarmChunkManager", "findContainingFarm", 2);

        assertInvocation(update, "FarmInstanceRegistry.get()", "getAllFarms");
        assertInvocation(update, null, "blockPosition");
        assertInvocation(find, "farm", "contains", "position");
        assertFalse(hasInvocation(update, null, "getFarmForPlayer"));
        assertFalse(hasInvocation(update, null, "getOwnerForPlayer"));
        assertInvocation(update, null, "onPlayerEnterFarm", "level", "player", "farm");
        assertInvocation(update, null, "onPlayerLeaveFarm", "level", "player");
    }

    @Test
    void farmExitUsesTrackedLeaveWithoutRegistryLookup() throws IOException {
        MethodTree exit = parseMethod(
                PORTAL_HANDLER_SOURCE, "InteriorPortalInteractionEvents", "handleFarmExit", 2);

        assertFalse(hasInvocation(exit, null, "getFarmForPlayer"));
        assertInvocation(exit, "com.stardew.craft.farm.FarmChunkManager.get()", "onPlayerLeaveFarm",
                "player.serverLevel()", "player");
    }

    @Test
    void logoutCleanupIsTheFirstPlayerSpecificOperation() throws IOException {
        MethodTree logout = parseMethod(
                PLAYER_HANDLER_SOURCE, "PlayerDataEventHandler", "onPlayerLogout", 1);
        assertEquals(1, logout.getBody().getStatements().size());
        IfTree serverPlayer = (IfTree) logout.getBody().getStatements().getFirst();
        BlockTree body = asBlock(serverPlayer.getThenStatement());

        assertTrue(isDirectInvocation(body.getStatements().getFirst(),
                "com.stardew.craft.farm.FarmChunkManager.get()", "onPlayerLogout", "player"),
                "tracked occupancy must be removed before unrelated fallible logout calls");
    }

    @Test
    void managerLeaveAndLogoutUseOnlyTrackedOccupancy() throws IOException {
        MethodTree leave = parseMethod(MANAGER_SOURCE, "FarmChunkManager", "onPlayerLeaveFarm", 2);
        MethodTree logout = parseMethod(MANAGER_SOURCE, "FarmChunkManager", "onPlayerLogout", 1);

        assertFalse(hasInvocation(leave, null, "getFarmForPlayer"));
        assertFalse(hasInvocation(logout, null, "getFarmForPlayer"));
        assertInvocation(logout, null, "onPlayerLeaveFarm", "player.serverLevel()", "player");
    }

    @Test
    void legacyOverloadsDelegateToTrackedOperations() throws IOException {
        MethodTree leave = parseMethod(MANAGER_SOURCE, "FarmChunkManager", "onPlayerLeaveFarm", 3);
        MethodTree logout = parseMethod(MANAGER_SOURCE, "FarmChunkManager", "onPlayerLogout", 2);

        assertEquals(1, leave.getBody().getStatements().size());
        assertTrue(isDirectInvocation(leave.getBody().getStatements().getFirst(),
                null, "onPlayerLeaveFarm", "level", "player"));
        assertEquals(1, logout.getBody().getStatements().size());
        assertTrue(isDirectInvocation(logout.getBody().getStatements().getFirst(),
                null, "onPlayerLogout", "player"));
    }

    @Test
    void managerShutdownAlwaysClearsOccupancyInNestedFinally() throws IOException {
        MethodTree stop = parseMethod(MANAGER_SOURCE, "FarmChunkManager", "onServerStopping", 1);
        assertTrue(stop.getParameters().getFirst().getModifiers().getAnnotations().stream()
                .anyMatch(annotation -> annotation.getAnnotationType().toString().equals("Nullable")));

        List<TryTree> outerCandidates = topLevelTries(stop).stream()
                .filter(candidate -> candidate.getFinallyBlock() != null)
                .filter(candidate -> hasInvocation(candidate.getFinallyBlock(), "occupancy", "clear"))
                .toList();
        assertEquals(1, outerCandidates.size());
        TryTree outer = outerCandidates.getFirst();
        assertNotNull(outer.getFinallyBlock());
        BlockTree outerFinally = outer.getFinallyBlock();
        List<TryTree> leaseCandidates = outerFinally.getStatements().stream()
                .filter(TryTree.class::isInstance)
                .map(TryTree.class::cast)
                .filter(candidate -> candidate.getFinallyBlock() != null)
                .filter(candidate -> hasInvocation(candidate.getFinallyBlock(), "occupancy", "clear"))
                .toList();
        assertEquals(1, leaseCandidates.size());
        TryTree leaseCleanup = leaseCandidates.getFirst();
        assertNotNull(leaseCleanup.getFinallyBlock());
        BlockTree occupancyFinally = leaseCleanup.getFinallyBlock();

        assertInvocation(outer.getBlock(), "temporaryFarmLoads", "remove", "level");
        assertInvocation(leaseCleanup.getBlock(), "temporaryChunkLeases", "closeAll", "level");
        assertDirectInvocation(occupancyFinally, "occupancy", "clear");
    }

    @Test
    void subscribedServerStopDelegatesFarmCleanupUnconditionally() throws IOException {
        MethodTree stop = parseMethod(
                PLAYER_HANDLER_SOURCE, "PlayerDataEventHandler", "onServerStopping", 1);
        List<TryTree> cleanupCandidates = topLevelTries(stop).stream()
                .filter(candidate -> candidate.getFinallyBlock() != null)
                .filter(candidate -> hasInvocation(candidate.getFinallyBlock(),
                        "com.stardew.craft.farm.FarmChunkManager.get()",
                        "onServerStopping", "stardewLevel"))
                .toList();
        assertEquals(1, cleanupCandidates.size());
        TryTree cacheCleanup = cleanupCandidates.getFirst();
        assertNotNull(cacheCleanup.getFinallyBlock());
        BlockTree finallyBlock = cacheCleanup.getFinallyBlock();

        assertDirectInvocation(finallyBlock,
                "com.stardew.craft.farm.FarmChunkManager.get()", "onServerStopping", "stardewLevel");
        assertFalse(finallyBlock.getStatements().stream().anyMatch(IfTree.class::isInstance),
                "farm shutdown must not be conditional on the Stardew level being present");
    }

    @Test
    void entryLoggingIgnoresDuplicatesAndReportsSwitches() throws IOException {
        MethodTree enter = parseMethod(MANAGER_SOURCE, "FarmChunkManager", "onPlayerEnterFarm", 3);
        List<? extends StatementTree> statements = enter.getBody().getStatements();
        assertTrue(statements.get(2) instanceof IfTree,
                "entry logging must be guarded by Transition.changed()");
        IfTree unchanged = (IfTree) statements.get(2);

        assertTrue(normalized(unwrapped(unchanged.getCondition())).equals("!transition.changed()"));
        assertTrue(asBlock(unchanged.getThenStatement()).getStatements().getFirst() instanceof ReturnTree);
        assertInvocation(enter, "transition", "previous");
        assertTrue(invocations(enter).stream().anyMatch(invocation ->
                invocation.getMethodSelect() instanceof MemberSelectTree select
                        && normalized(select.getExpression()).equals("transition.previous()")
                        && select.getIdentifier().contentEquals("ifPresent")
                        && invocation.getArguments().size() == 1
                        && invocation.getArguments().getFirst() instanceof LambdaExpressionTree));
    }

    private static Path sourcePath(String relativePath) {
        return PROJECT.resolve("src/main/java/com/stardew/craft/").resolve(relativePath);
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

    private static IfTree findIf(com.sun.source.tree.Tree tree, Predicate<ExpressionTree> condition) {
        List<IfTree> matches = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitIf(IfTree candidate, Void unused) {
                if (condition.test(candidate.getCondition())) {
                    matches.add(candidate);
                }
                return super.visitIf(candidate, unused);
            }
        }.scan(tree, null);
        assertEquals(1, matches.size(), "expected exactly one matching if statement");
        return matches.getFirst();
    }

    private static List<VariableTree> variables(com.sun.source.tree.Tree tree) {
        List<VariableTree> variables = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitVariable(VariableTree variable, Void unused) {
                variables.add(variable);
                return super.visitVariable(variable, unused);
            }
        }.scan(tree, null);
        return variables;
    }

    private static List<TryTree> topLevelTries(MethodTree method) {
        return method.getBody().getStatements().stream()
                .filter(TryTree.class::isInstance)
                .map(TryTree.class::cast)
                .toList();
    }

    private static List<MethodInvocationTree> invocations(com.sun.source.tree.Tree tree) {
        List<MethodInvocationTree> invocations = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
                invocations.add(invocation);
                return super.visitMethodInvocation(invocation, unused);
            }
        }.scan(tree, null);
        return invocations;
    }

    private static void assertInvocation(
            com.sun.source.tree.Tree tree, String receiver, String methodName, String... arguments) {
        assertTrue(hasInvocation(tree, receiver, methodName, arguments),
                () -> "missing invocation: " + invocationDescription(receiver, methodName, arguments));
    }

    private static boolean hasInvocation(
            com.sun.source.tree.Tree tree, String receiver, String methodName, String... arguments) {
        return invocations(tree).stream()
                .anyMatch(invocation -> isInvocation(invocation, receiver, methodName, arguments));
    }

    private static boolean isInvocation(
            com.sun.source.tree.Tree tree, String receiver, String methodName, String... arguments) {
        com.sun.source.tree.Tree unwrapped = tree instanceof ExpressionTree expression
                ? unwrapped(expression)
                : tree;
        return unwrapped instanceof MethodInvocationTree invocation
                && isInvocation(invocation, receiver, methodName, arguments);
    }

    private static ExpressionTree unwrapped(ExpressionTree expression) {
        ExpressionTree current = expression;
        while (current instanceof ParenthesizedTree parenthesized) {
            current = parenthesized.getExpression();
        }
        return current;
    }

    private static boolean isInvocation(
            MethodInvocationTree invocation, String receiver, String methodName, String... arguments) {
        if (!(invocation.getMethodSelect() instanceof MemberSelectTree select)) {
            return receiver == null
                    && invocation.getMethodSelect().toString().equals(methodName)
                    && argumentsMatch(invocation, arguments);
        }
        return (receiver == null || normalized(select.getExpression()).equals(normalized(receiver)))
                && select.getIdentifier().contentEquals(methodName)
                && argumentsMatch(invocation, arguments);
    }

    private static boolean argumentsMatch(MethodInvocationTree invocation, String... arguments) {
        return invocation.getArguments().stream().map(FarmOccupancyIntegrationContractTest::normalized).toList()
                .equals(List.of(arguments).stream().map(FarmOccupancyIntegrationContractTest::normalized).toList());
    }

    private static void assertDirectInvocation(
            BlockTree block, String receiver, String methodName, String... arguments) {
        assertTrue(block.getStatements().stream()
                        .anyMatch(statement -> isDirectInvocation(statement, receiver, methodName, arguments)),
                () -> "missing direct invocation: " + invocationDescription(receiver, methodName, arguments));
    }

    private static boolean isDirectInvocation(
            StatementTree statement, String receiver, String methodName, String... arguments) {
        return statement instanceof ExpressionStatementTree expression
                && isInvocation(expression.getExpression(), receiver, methodName, arguments);
    }

    private static int statementIndex(BlockTree block, Predicate<StatementTree> predicate) {
        for (int index = 0; index < block.getStatements().size(); index++) {
            if (predicate.test(block.getStatements().get(index))) {
                return index;
            }
        }
        return -1;
    }

    private static BlockTree asBlock(StatementTree statement) {
        assertTrue(statement instanceof BlockTree, "expected block statement");
        return (BlockTree) statement;
    }

    private static String normalized(Object syntaxTree) {
        return syntaxTree.toString().replaceAll("\\s+", "");
    }

    private static String invocationDescription(String receiver, String methodName, String... arguments) {
        String prefix = receiver == null ? "" : receiver + ".";
        return prefix + methodName + "(" + String.join(", ", arguments) + ")";
    }
}
