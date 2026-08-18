package com.stardew.craft.farm;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.Tree;
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
    private static final Path MOD_TELEPORT_SOURCE = sourcePath("warp/ModTeleport.java");
    private static final Path LOCATION_GUARD_SOURCE = sourcePath("event/PlayerLocationStateGuardEvents.java");
    private static final Path FARM_ADMIN_SOURCE = sourcePath("network/payload/FarmAdminPayload.java");
    private static final Path RETURN_SCEPTER_SOURCE = sourcePath("warp/ReturnScepterService.java");

    @Test
    void entryHandlerRoutesSelectedFarmThroughUnifiedTeleportGuard() throws IOException {
        MethodTree handle = parseMethod(ENTRY_PAYLOAD_SOURCE, "FarmEntryRequestPayload", "handle", 2);
        BlockTree work = lambdaBlockOfDirectInvocation(handle.getBody(), "context", "enqueueWork");
        List<VariableTree> farms = directVariables(work).stream()
                .filter(variable -> variable.getName().contentEquals("farm"))
                .toList();

        assertEquals(1, farms.size());
        assertTrue(isInvocation(farms.getFirst().getInitializer(),
                "registry", "getFarm", "payload.targetOwner"));
        assertDirectInvocation(work, "ModTeleport", "to",
                "player", "stardewLevel", "targetPos.getX()+0.5", "targetPos.getY()",
                "targetPos.getZ()+0.5", "yaw", "0.0F");
        assertFalse(hasDirectInvocation(work, "FarmChunkManager.get()", "onPlayerEnterFarm",
                "stardewLevel", "player", "farm"));
    }

    @Test
    void locationGuardReconcilesOccupancyBeforeAnyEarlyReturn() throws IOException {
        MethodTree reconcile = parseMethod(
                LOCATION_GUARD_SOURCE, "PlayerLocationStateGuardEvents", "reconcileLocationState", 2);
        BlockTree body = reconcile.getBody();
        int occupancyIndex = directInvocationIndex(body,
                "com.stardew.craft.farm.FarmChunkManager.get()",
                "reconcilePlayerOccupancy", "player");
        int firstEarlyReturnOwner = statementIndex(body,
                FarmOccupancyIntegrationContractTest::hasReturn);

        assertTrue(occupancyIndex >= 0, "location guard must directly reconcile farm occupancy");
        assertTrue(occupancyIndex < firstEarlyReturnOwner,
                "farm occupancy must reconcile before interior-state early returns");
    }

    @Test
    void deferredTeleportReconciliationRequiresTheSameConnectedPlayer() throws IOException {
        MethodTree teleport = parseMethod(
                LOCATION_GUARD_SOURCE, "PlayerLocationStateGuardEvents", "onEntityTeleport", 1);
        boolean lowestPriority = teleport.getModifiers().getAnnotations().stream()
                .filter(annotation -> annotation.getAnnotationType().toString().equals("SubscribeEvent"))
                .flatMap(annotation -> annotation.getArguments().stream())
                .filter(AssignmentTree.class::isInstance)
                .map(AssignmentTree.class::cast)
                .anyMatch(argument -> argument.getVariable().toString().equals("priority")
                        && argument.getExpression().toString().equals("EventPriority.LOWEST"));
        assertTrue(lowestPriority, "teleport guard must remain subscribed at LOWEST priority");

        IfTree canceledOrNonPlayer = findDirectIf(teleport.getBody(), condition ->
                hasInvocation(condition, "event", "isCanceled"));
        assertTrue(asBlock(canceledOrNonPlayer.getThenStatement())
                .getStatements().getFirst() instanceof ReturnTree);

        List<VariableTree> serverCaptures = directVariables(teleport.getBody()).stream()
                .filter(variable -> isMemberSelect(variable.getInitializer(), "player", "server"))
                .toList();
        assertEquals(1, serverCaptures.size(), "teleport callback must capture the player's server");
        String serverLocal = serverCaptures.getFirst().getName().toString();
        List<VariableTree> playerIdCaptures = directVariables(teleport.getBody()).stream()
                .filter(variable -> isInvocation(variable.getInitializer(), "player", "getUUID"))
                .toList();
        assertEquals(1, playerIdCaptures.size(), "teleport callback must capture the player's UUID");
        String playerIdLocal = playerIdCaptures.getFirst().getName().toString();

        MethodInvocationTree tell = directInvocations(teleport.getBody()).stream()
                .filter(invocation -> invocation.getMethodSelect() instanceof MemberSelectTree select
                        && select.getExpression() instanceof IdentifierTree identifier
                        && identifier.getName().contentEquals(serverLocal)
                        && select.getIdentifier().contentEquals("tell"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("direct server tell scheduling is missing"));
        assertEquals(1, tell.getArguments().size());
        assertTrue(tell.getArguments().getFirst() instanceof NewClassTree,
                "server.tell must directly schedule a TickTask");
        NewClassTree tickTask = (NewClassTree) tell.getArguments().getFirst();
        assertEquals("net.minecraft.server.TickTask", tickTask.getIdentifier().toString());
        assertEquals(2, tickTask.getArguments().size());
        assertTrue(tickTask.getArguments().get(1) instanceof LambdaExpressionTree);
        LambdaExpressionTree callback = (LambdaExpressionTree) tickTask.getArguments().get(1);
        assertTrue(callback.getBody() instanceof BlockTree, "teleport callback must be a block lambda");
        BlockTree callbackBody = (BlockTree) callback.getBody();

        IfTree stalePlayer = findDirectIf(callbackBody, condition -> {
            ExpressionTree expression = unwrapped(condition);
            if (!(expression instanceof BinaryTree identity)
                    || identity.getKind() != Tree.Kind.NOT_EQUAL_TO) {
                return false;
            }
            return isInvocation(identity.getLeftOperand(),
                    serverLocal + ".getPlayerList()", "getPlayer", playerIdLocal)
                    && identity.getRightOperand() instanceof IdentifierTree identifier
                    && identifier.getName().contentEquals("player");
        });
        assertTrue(asBlock(stalePlayer.getThenStatement())
                .getStatements().getFirst() instanceof ReturnTree);
        int identityIndex = callbackBody.getStatements().indexOf(stalePlayer);
        int reconcileIndex = directInvocationIndex(callbackBody,
                null, "reconcileLocationState", "player", "true");
        assertTrue(reconcileIndex > identityIndex,
                "callback must verify the exact connected player before reconciliation");
    }

    @Test
    void modTeleportInvokesLocationGuardAfterTeleportWithoutDuplicateOccupancyCall() throws IOException {
        MethodTree teleport = parseMethod(MOD_TELEPORT_SOURCE, "ModTeleport", "to", 7);
        BlockTree body = teleport.getBody();
        int teleportIndex = directInvocationIndex(
                body, "player", "teleportTo", "target", "x", "y", "z", "yaw", "pitch");
        int guardIndex = directInvocationIndex(body,
                "com.stardew.craft.event.PlayerLocationStateGuardEvents",
                "reconcileLocationState", "player", "true");

        assertTrue(teleportIndex >= 0, "teleportTo must remain a direct statement");
        assertTrue(guardIndex > teleportIndex,
                "location state guard must reconcile after teleportTo");
        assertFalse(hasDirectInvocation(body,
                "com.stardew.craft.farm.FarmChunkManager.get()",
                "reconcilePlayerOccupancy", "player"),
                "ModTeleport must delegate occupancy to the location guard");
    }

    @Test
    void farmChangingRawTeleportPathsUseModTeleport() throws IOException {
        MethodTree admin = parseMethod(FARM_ADMIN_SOURCE, "FarmAdminPayload", "handle", 2);
        BlockTree adminWork = lambdaBlockOfDirectInvocation(admin.getBody(), "context", "enqueueWork");
        SwitchTree actionSwitch = adminWork.getStatements().stream()
                .filter(SwitchTree.class::isInstance)
                .map(SwitchTree.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("farm admin action switch is missing"));
        CaseTree teleportCase = actionSwitch.getCases().stream()
                .filter(candidate -> candidate.getLabels().stream()
                        .anyMatch(label -> normalized(label).equals("3")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("farm admin teleport case is missing"));
        assertTrue(teleportCase.getBody() instanceof BlockTree, "expected block case body");
        IfTree farmFound = findDirectIf((BlockTree) teleportCase.getBody(),
                condition -> normalized(unwrapped(condition)).equals("farm!=null"));
        BlockTree farmFoundBody = asBlock(farmFound.getThenStatement());

        assertDirectInvocation(farmFoundBody, "com.stardew.craft.warp.ModTeleport",
                "to", "player", "player.serverLevel()",
                "tp.getX()+0.5", "tp.getY()", "tp.getZ()+0.5", "0", "0");
        assertFalse(hasDirectInvocation(farmFoundBody, "player", "teleportTo",
                "player.serverLevel()", "tp.getX()+0.5", "tp.getY()", "tp.getZ()+0.5", "0", "0"));

        MethodTree scepter = parseMethod(
                RETURN_SCEPTER_SOURCE, "ReturnScepterService", "warpHome", 1);
        assertDirectInvocation(scepter.getBody(), "ModTeleport", "to",
                "player", "targetLevel", "frontDoor", "0.0F", "0.0F");
        assertFalse(hasDirectInvocation(scepter.getBody(), "player", "teleportTo",
                "targetLevel", "frontDoor", "0.0F", "0.0F"));
    }

    @Test
    void loginInValleyReconcilesOccupancyFromCurrentPosition() throws IOException {
        MethodTree login = parseMethod(PLAYER_HANDLER_SOURCE, "PlayerDataEventHandler", "onPlayerLogin", 1);
        IfTree serverPlayer = findDirectIf(login.getBody(), condition -> normalized(unwrapped(condition)).equals(
                "event.getEntity()instanceofServerPlayerplayer"));
        IfTree valleyLogin = findDirectIf(asBlock(serverPlayer.getThenStatement()),
                condition -> normalized(unwrapped(condition)).equals(
                "player.serverLevel().dimension()==com.stardew.craft.core.ModDimensions.STARDEW_VALLEY"));
        BlockTree body = asBlock(valleyLogin.getThenStatement());

        assertDirectInvocation(body, "com.stardew.craft.farm.FarmChunkManager.get()",
                "reconcilePlayerOccupancy", "player");
    }

    @Test
    void dimensionLifecycleLeavesAndReconcilesAfterOptionalOrDeferredAutoRouting() throws IOException {
        MethodTree changed = parseMethod(
                DIMENSION_HANDLER_SOURCE, "DimensionEventHandler", "onPlayerChangeDimension", 1);
        IfTree leaveValley = findDirectIf(changed.getBody(), condition -> isInvocation(
                condition, "ModDimensions.STARDEW_VALLEY", "equals", "event.getFrom()"));
        assertDirectInvocation(asBlock(leaveValley.getThenStatement()),
                "com.stardew.craft.farm.FarmChunkManager.get()", "onPlayerLeaveFarm",
                "player.serverLevel()", "player");

        IfTree stardewTimeBranch = findDirectIf(changed.getBody(), condition ->
                normalized(unwrapped(condition)).equals(
                        "ModDimensions.STARDEW_VALLEY.equals(event.getTo())"
                                + "||ModMiningDimensions.STARDEW_MINING.equals(event.getTo())"));
        IfTree enterValley = findDirectIf(asBlock(stardewTimeBranch.getThenStatement()),
                condition -> isInvocation(
                        condition, "ModDimensions.STARDEW_VALLEY", "equals", "event.getTo()"));
        BlockTree valleyBody = asBlock(enterValley.getThenStatement());
        int autoRoute = statementIndex(valleyBody, statement -> statement instanceof IfTree candidate
                && hasInvocation(candidate.getCondition(), null, "consumeSkipAutoTeleport", "player.getUUID()"));
        int reconcileGuard = statementIndex(valleyBody, statement -> statement instanceof IfTree candidate
                && normalized(unwrapped(candidate.getCondition())).equals("!farmTeleportQueued")
                && hasInvocation(candidate.getThenStatement(),
                        "com.stardew.craft.farm.FarmChunkManager.get()",
                        "reconcilePlayerOccupancy", "player"));

        assertTrue(autoRoute >= 0, "Stardew entry must retain optional auto-routing");
        assertTrue(reconcileGuard > autoRoute,
                "non-deferred occupancy reconciliation must run after optional auto-routing");
        assertTrue(hasInvocation(changed, "FARM_ENTRY_TELEPORTS", "enqueue",
                "player.getUUID()", "level", "player", "spawnPos"),
                "farm auto-routing must enqueue the deferred center-chunk teleport");
    }

    @Test
    void currentPositionLookupUsesRegistrySpatialIndexWithoutMembershipResolution() throws IOException {
        MethodTree update = parseMethod(MANAGER_SOURCE, "FarmChunkManager", "reconcilePlayerOccupancy", 1);
        BlockTree updateBody = update.getBody();
        IfTree outsideValley = findDirectIf(updateBody, condition -> normalized(unwrapped(condition)).equals(
                "!ModDimensions.STARDEW_VALLEY.equals(level.dimension())"));
        IfTree outsideFarm = findDirectIf(updateBody, condition -> normalized(unwrapped(condition)).equals(
                "farm==null||!farm.contains(player.blockPosition())"));
        VariableTree farm = directVariables(updateBody).stream()
                .filter(variable -> variable.getName().contentEquals("farm"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("direct farm lookup is missing"));

        assertTrue(hasInvocation(update, "registry", "getOwnerAt", "player.blockPosition()"));
        assertTrue(hasInvocation(update, "registry", "getFarm", "owner"));
        assertFalse(hasInvocation(update, null, "getFarmForPlayer"));
        assertFalse(hasInvocation(update, null, "getOwnerForPlayer"));
        assertDirectInvocation(asBlock(outsideValley.getThenStatement()),
                null, "onPlayerLeaveFarm", "level", "player");
        assertDirectInvocation(asBlock(outsideFarm.getThenStatement()),
                null, "onPlayerLeaveFarm", "level", "player");
        assertDirectInvocation(updateBody, null, "onPlayerEnterFarm", "level", "player", "farm");

    }

    @Test
    void farmExitUsesTrackedLeaveWithoutRegistryLookup() throws IOException {
        MethodTree exit = parseMethod(
                PORTAL_HANDLER_SOURCE, "InteriorPortalInteractionEvents", "handleFarmExit", 2);

        assertFalse(hasInvocation(exit, null, "getFarmForPlayer"));
        assertDirectInvocation(exit.getBody(),
                "com.stardew.craft.farm.FarmChunkManager.get()", "onPlayerLeaveFarm",
                "player.serverLevel()", "player");
    }

    @Test
    void logoutCleanupIsTheFirstPlayerSpecificOperation() throws IOException {
        MethodTree logout = parseMethod(
                PLAYER_HANDLER_SOURCE, "PlayerDataEventHandler", "onPlayerLogout", 1);
        IfTree serverPlayer = findDirectIf(logout.getBody(), condition -> normalized(unwrapped(condition)).equals(
                "event.getEntity()instanceofServerPlayerplayer"));
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
        assertDirectInvocation(logout.getBody(),
                null, "onPlayerLeaveFarm", "player.serverLevel()", "player");
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

        assertEquals(1, directTries(stop.getBody()).size());
        TryTree outer = directTries(stop.getBody()).getFirst();
        IfTree wrapperLevelGuard = findDirectIf(outer.getBlock(), condition ->
                normalized(unwrapped(condition)).equals("level!=null"));
        BlockTree wrapperLevelBody = asBlock(wrapperLevelGuard.getThenStatement());
        VariableTree loads = directVariables(wrapperLevelBody).stream()
                .filter(variable -> variable.getName().contentEquals("loadsForLevel"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("level wrapper removal is missing"));
        assertTrue(isInvocation(loads.getInitializer(),
                "temporaryFarmLoads", "remove", "level"));
        IfTree loadsPresent = findDirectIf(wrapperLevelBody, condition ->
                normalized(unwrapped(condition)).equals("loadsForLevel!=null"));
        EnhancedForLoopTree wrapperCloseLoop = asBlock(loadsPresent.getThenStatement()).getStatements().stream()
                .filter(EnhancedForLoopTree.class::isInstance)
                .map(EnhancedForLoopTree.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("wrapper lease close loop is missing"));
        assertDirectInvocation(asBlock(wrapperCloseLoop.getStatement()),
                "load.lease", "close");

        assertNotNull(outer.getFinallyBlock());
        BlockTree outerFinally = outer.getFinallyBlock();
        assertEquals(1, directTries(outerFinally).size());
        TryTree leaseCleanup = directTries(outerFinally).getFirst();
        IfTree trackerLevelGuard = findDirectIf(leaseCleanup.getBlock(), condition ->
                normalized(unwrapped(condition)).equals("level!=null"));
        assertDirectInvocation(asBlock(trackerLevelGuard.getThenStatement()),
                "temporaryChunkLeases", "closeAll", "level");
        assertNotNull(leaseCleanup.getFinallyBlock());
        BlockTree occupancyFinally = leaseCleanup.getFinallyBlock();

        assertDirectInvocation(occupancyFinally, "occupancy", "clear");
    }

    @Test
    void subscribedServerStopDelegatesFarmCleanupUnconditionally() throws IOException {
        MethodTree stop = parseMethod(
                PLAYER_HANDLER_SOURCE, "PlayerDataEventHandler", "onServerStopping", 1);
        assertTrue(hasInvocation(stop,
                "com.stardew.craft.farm.FarmChunkManager.get()", "onServerStopping", "stardewLevel"));
    }

    @Test
    void entryLoggingIgnoresDuplicatesAndReportsSwitches() throws IOException {
        MethodTree enter = parseMethod(MANAGER_SOURCE, "FarmChunkManager", "onPlayerEnterFarm", 3);
        BlockTree body = enter.getBody();
        IfTree unchanged = findDirectIf(enter.getBody(), condition ->
                normalized(unwrapped(condition)).equals("!transition.changed()"));
        int unchangedIndex = body.getStatements().indexOf(unchanged);

        assertTrue(asBlock(unchanged.getThenStatement()).getStatements().getFirst() instanceof ReturnTree);
        StatementTree switchLog = body.getStatements().get(unchangedIndex + 1);
        assertTrue(switchLog instanceof ExpressionStatementTree,
                "switch logging must directly follow the duplicate guard");
        ExpressionTree switchLogExpression = ((ExpressionStatementTree) switchLog).getExpression();
        assertTrue(switchLogExpression instanceof MethodInvocationTree,
                "switch logging must directly invoke transition.previous().ifPresent");
        MethodInvocationTree ifPresent = (MethodInvocationTree) switchLogExpression;
        assertTrue(ifPresent.getMethodSelect() instanceof MemberSelectTree select
                && normalized(select.getExpression()).equals("transition.previous()")
                && select.getIdentifier().contentEquals("ifPresent"));
        assertEquals(1, ifPresent.getArguments().size());
        assertTrue(ifPresent.getArguments().getFirst() instanceof LambdaExpressionTree);
        LambdaExpressionTree previousLog = (LambdaExpressionTree) ifPresent.getArguments().getFirst();
        assertTrue(previousLog.getBody() instanceof MethodInvocationTree,
                "previous-slot logger must be the lambda body");
        MethodInvocationTree debug = (MethodInvocationTree) previousLog.getBody();
        assertTrue(isInvocation(debug, "StardewCraft.LOGGER", "debug",
                "\"[FARM_CHUNK] Player {} left farm slot {}, players={}\"",
                "player.getName().getString()", "previous.slot()", "previous.count()"));
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

    private static IfTree findDirectIf(BlockTree block, Predicate<ExpressionTree> condition) {
        List<IfTree> matches = new ArrayList<>();
        block.getStatements().stream()
                .filter(IfTree.class::isInstance)
                .map(IfTree.class::cast)
                .filter(candidate -> condition.test(candidate.getCondition()))
                .forEach(matches::add);
        assertEquals(1, matches.size(), "expected exactly one matching if statement");
        return matches.getFirst();
    }

    private static List<VariableTree> directVariables(BlockTree block) {
        return block.getStatements().stream()
                .filter(VariableTree.class::isInstance)
                .map(VariableTree.class::cast)
                .toList();
    }

    private static List<TryTree> directTries(BlockTree block) {
        return block.getStatements().stream()
                .filter(TryTree.class::isInstance)
                .map(TryTree.class::cast)
                .toList();
    }

    private static BlockTree lambdaBlockOfDirectInvocation(
            BlockTree block, String receiver, String methodName) {
        MethodInvocationTree invocation = directInvocations(block).stream()
                .filter(candidate -> isInvocation(candidate, receiver, methodName,
                        candidate.getArguments().stream().map(Object::toString).toArray(String[]::new)))
                .filter(candidate -> candidate.getArguments().size() == 1)
                .filter(candidate -> candidate.getArguments().getFirst() instanceof LambdaExpressionTree)
                .findFirst()
                .orElseThrow(() -> new AssertionError("direct lambda invocation is missing: " + methodName));
        LambdaExpressionTree lambda = (LambdaExpressionTree) invocation.getArguments().getFirst();
        assertTrue(lambda.getBody() instanceof BlockTree, "expected block lambda");
        return (BlockTree) lambda.getBody();
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

    private static boolean isMemberSelect(
            Tree tree, String expectedReceiver, String expectedMember) {
        if (!(tree instanceof ExpressionTree expression)
                || !(unwrapped(expression) instanceof MemberSelectTree select)
                || !(select.getExpression() instanceof IdentifierTree identifier)) {
            return false;
        }
        return identifier.getName().contentEquals(expectedReceiver)
                && select.getIdentifier().contentEquals(expectedMember);
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

    private static boolean hasDirectInvocation(
            BlockTree block, String receiver, String methodName, String... arguments) {
        return block.getStatements().stream()
                .anyMatch(statement -> isDirectInvocation(statement, receiver, methodName, arguments));
    }

    private static List<MethodInvocationTree> directInvocations(BlockTree block) {
        return block.getStatements().stream()
                .filter(ExpressionStatementTree.class::isInstance)
                .map(ExpressionStatementTree.class::cast)
                .map(ExpressionStatementTree::getExpression)
                .filter(MethodInvocationTree.class::isInstance)
                .map(MethodInvocationTree.class::cast)
                .toList();
    }

    private static int directInvocationIndex(
            BlockTree block, String receiver, String methodName, String... arguments) {
        return statementIndex(block,
                statement -> isDirectInvocation(statement, receiver, methodName, arguments));
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

    private static boolean hasReturn(StatementTree statement) {
        final boolean[] found = {false};
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitReturn(ReturnTree node, Void unused) {
                found[0] = true;
                return null;
            }
        }.scan(statement, null);
        return found[0];
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
