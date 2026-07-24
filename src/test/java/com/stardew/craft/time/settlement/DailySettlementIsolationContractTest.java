package com.stardew.craft.time.settlement;

import com.stardew.craft.Config;
import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import com.stardew.craft.time.StardewTimeManager;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailySettlementIsolationContractTest {
    private static final Pattern SERVER_REGISTRATION = Pattern.compile(
            "registrar\\.playToServer\\(\\s*([^,]+)\\.TYPE,\\s*[^,]+\\.STREAM_CODEC,\\s*([^\\n]+)\\s*\\)",
            Pattern.MULTILINE);
    private static final Set<String> UNGATED_SERVER_PAYLOADS = Set.of(
            "com.stardew.craft.network.payload.SleepCancelPayload",
            "com.stardew.craft.network.overnight.OvernightReadyAckPayload",
            "com.stardew.craft.network.payload.StardewPauseStatePayload");

    @Test
    void productionBudgetAndItemLimitUseTheDeclaredConfiguration() throws IOException {
        assertEquals(4, Config.DAILY_SETTLEMENT_BUDGET_MILLIS.get());
        assertEquals(256, Config.DAILY_SETTLEMENT_ITEM_LIMIT.get());

        String services = source("src/main/java/com/stardew/craft/time/settlement/DailySettlementServices.java");
        assertTrue(services.contains("Config.DAILY_SETTLEMENT_BUDGET_MILLIS.get()"));
        assertTrue(services.contains("TimeUnit.MILLISECONDS.toNanos"));
        assertTrue(services.contains("Config.DAILY_SETTLEMENT_ITEM_LIMIT.get()"));
        assertFalse(services.contains("() -> 2_000_000L"));
        assertFalse(services.contains("() -> 64"));
    }

    @Test
    void activeSettlementFreezesBothVirtualAndSimulationClocks() {
        StardewTimeManager time = new StardewTimeManager();
        time.initializeSimulationGameTime(90L);

        assertEquals(0L, time.advanceIndependentDayTime(1.0D, true));
        time.advanceSimulationGameTime(true);

        assertEquals(0L, time.getIndependentDayTime());
        assertEquals(90L, time.getSimulationGameTime());

        assertEquals(1L, time.advanceIndependentDayTime(1.0D, false));
        time.advanceSimulationGameTime(false);
        assertEquals(1L, time.getIndependentDayTime());
        assertEquals(91L, time.getSimulationGameTime());
    }

    @Test
    void pendingDateIsVisibleOnlyInsideItemScopedSettlementView() throws Exception {
        StardewTimeManager time = new StardewTimeManager();
        time.setCurrentYear(1);
        time.setCurrentSeason(0);
        time.setCurrentDay(28);
        time.setCurrentTime(1_560);
        DailySettlementContext target = new DailySettlementContext(
                29, 1, 1, 1, 1_560, true, List.of(), Set.of());

        assertDate(time, 1, 0, 28, 1_560);
        DailySettlementDateView.run(target, () -> assertDate(time, 1, 1, 1, 360));
        assertDate(time, 1, 0, 28, 1_560);
    }

    @Test
    void anchorSurvivesWrongAckAndReconnectsAtTheLoginPosition() {
        UUID playerId = UUID.randomUUID();
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        DailySettlementAccessGuard guard = new DailySettlementAccessGuard(barrier);
        barrier.lockAll(29, List.of(playerId));
        guard.captureAnchor(playerId, Level.OVERWORLD, new Vec3(1.0D, 64.0D, 2.0D), 10.0F, 20.0F);

        assertFalse(guard.isGameplayAllowed(playerId));
        assertEquals(new Vec3(1.0D, 64.0D, 2.0D), guard.anchor(playerId).orElseThrow().position());
        assertFalse(guard.acknowledge(playerId, 28));
        assertTrue(barrier.isLocked(playerId));
        assertTrue(guard.anchor(playerId).isPresent());

        guard.onLogout(playerId);
        assertTrue(barrier.isLocked(playerId));
        assertEquals(Optional.empty(), guard.anchor(playerId));
        guard.reconnectAnchor(
                playerId, Level.NETHER, new Vec3(8.0D, 70.0D, 9.0D), 30.0F, 40.0F);
        assertEquals(Level.NETHER, guard.anchor(playerId).orElseThrow().dimension());
        assertEquals(new Vec3(8.0D, 70.0D, 9.0D), guard.anchor(playerId).orElseThrow().position());

        DailySettlementBarrier.ReadyResult ready = new DailySettlementBarrier.ReadyResult(
                29, new OvernightSettlementPayload(29, List.of(), List.of()));
        assertTrue(barrier.publishReady(playerId, ready));
        assertTrue(guard.acknowledge(playerId, 29));
        assertFalse(barrier.isLocked(playerId));
        assertTrue(guard.anchor(playerId).isEmpty());
    }

    @Test
    void everyOrdinaryServerGameplayPayloadUsesTheSharedGuard() throws IOException {
        String packetHandler = source("src/main/java/com/stardew/craft/network/PacketHandler.java");
        Matcher registrations = SERVER_REGISTRATION.matcher(packetHandler);
        int serverRegistrations = 0;
        while (registrations.find()) {
            serverRegistrations++;
            String payload = registrations.group(1).strip();
            String handler = registrations.group(2).strip();
            if (UNGATED_SERVER_PAYLOADS.contains(payload)) {
                assertFalse(handler.contains("DailySettlementAccessGuard.gated"), payload);
            } else {
                assertTrue(handler.startsWith("DailySettlementAccessGuard.gated("), payload);
            }
        }
        assertTrue(serverRegistrations > 100, "expected the complete playToServer registry");

        Pattern clientRegistration = Pattern.compile(
                "registrar\\.playToClient\\((?s:.*?)\\)", Pattern.MULTILINE);
        Matcher clients = clientRegistration.matcher(packetHandler);
        while (clients.find()) {
            assertFalse(clients.group().contains("DailySettlementAccessGuard.gated"));
        }
    }

    @Test
    void persistedReadyRecoveryRestoresTheServerBarrierBeforeGameplayResumes()
            throws IOException {
        MethodTree login = parseMethod(
                sourcePath("time/settlement/DailySettlementEvents.java"),
                "DailySettlementEvents", "onPlayerLogin", 1);
        BlockTree recovery = asBlock(findDirectIf(login.getBody(), condition ->
                normalized(unwrapped(condition)).equals("services==null")).getThenStatement());

        assertTrue(hasInvocation(recovery, "DailySettlementServices", "get", "player.server"),
                "recovery must create the server-owned settlement services");
        assertTrue(hasInvocation(recovery, "services.players()", "onLogin",
                "player.getUUID()", "services.barrier()"),
                "recovery must restore the persisted result through the live barrier");
        assertTrue(hasInvocation(recovery, "services.accessGuard()", "reconnectAnchor", "player"),
                "recovery must anchor the reconnecting player before ACK");
        assertTrue(hasInvocation(recovery, "services.barrier()", "lockedDay", "player.getUUID()"),
                "recovery must send the retained day from the restored barrier");
    }

    @Test
    void gatedPayloadHandlerChecksAccessOnTheServerExecutorBeforeDelegating() {
        AtomicReference<Runnable> queuedWork = new AtomicReference<>();
        IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(
                IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("enqueueWork")) {
                        queuedWork.set((Runnable) arguments[0]);
                        return CompletableFuture.completedFuture(null);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        AtomicBoolean allowed = new AtomicBoolean();
        AtomicInteger accessChecks = new AtomicInteger();
        AtomicInteger handled = new AtomicInteger();
        IPayloadHandler<OvernightSettlementPayload> guarded =
                DailySettlementAccessGuard.gated(
                        (payload, payloadContext) -> handled.incrementAndGet(),
                        payloadContext -> {
                            accessChecks.incrementAndGet();
                            return allowed.get();
                        });
        OvernightSettlementPayload payload =
                new OvernightSettlementPayload(29, List.of(), List.of());

        guarded.handle(payload, context);
        assertEquals(0, accessChecks.get());
        assertEquals(0, handled.get());
        queuedWork.get().run();
        assertEquals(1, accessChecks.get());
        assertEquals(0, handled.get());

        allowed.set(true);
        guarded.handle(payload, context);
        assertEquals(1, accessChecks.get());
        assertEquals(0, handled.get());
        queuedWork.get().run();
        assertEquals(2, accessChecks.get());
        assertEquals(1, handled.get());
    }

    @Test
    void defaultGatedHandlerFailsClosedAndResolvesLiveServices() throws IOException {
        MethodTree gated = parseMethod(
                sourcePath("time/settlement/DailySettlementAccessGuard.java"),
                "DailySettlementAccessGuard", "gated", 1);
        assertTrue(hasInvocation(gated, "DailySettlementServices", "getForPlayer", "player"));

        AtomicReference<Runnable> queuedWork = new AtomicReference<>();
        IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(
                IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("enqueueWork")) {
                        queuedWork.set((Runnable) arguments[0]);
                        return CompletableFuture.completedFuture(null);
                    }
                    if (method.getName().equals("player")) {
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        AtomicInteger handled = new AtomicInteger();
        IPayloadHandler<OvernightSettlementPayload> guarded =
                DailySettlementAccessGuard.gated(
                        (payload, payloadContext) -> handled.incrementAndGet());

        guarded.handle(new OvernightSettlementPayload(29, List.of(), List.of()), context);
        queuedWork.get().run();
        assertEquals(0, handled.get());
    }

    @Test
    void teleportGuardRejectsOnlyLockedParticipants() {
        UUID locked = UUID.randomUUID();
        UUID free = UUID.randomUUID();
        DailySettlementBarrier barrier = new DailySettlementBarrier();
        DailySettlementAccessGuard guard = new DailySettlementAccessGuard(barrier);
        barrier.lockAll(29, List.of(locked));

        assertTrue(guard.rejectTeleport(locked));
        assertFalse(guard.rejectTeleport(free));
    }

    @Test
    void machineTimeReadersKeepUsingTheSharedClockWithoutSettlementPauseFlags()
            throws IOException {
        for (String file : List.of(
                "TimedProductionBlockEntity.java",
                "CaskBlockEntity.java",
                "CoffeeMakerBlockEntity.java",
                "SolarPanelBlockEntity.java",
                "CrabPotBlockEntity.java",
                "MasteryStatueBlockEntity.java",
                "HeaterBlockEntity.java")) {
            String machine = source("src/main/java/com/stardew/craft/blockentity/" + file);
            assertTrue(machine.contains("StardewTimeManager"), file);
            assertFalse(machine.contains("DailySettlementServices"), file);
            assertFalse(machine.contains("DailySettlementAccessGuard"), file);
        }
    }

    @Test
    void machineTickersUseDateViewProtectedStardewGetters() throws IOException {
        MethodTree timedReady = parseMethod(
                sourcePath("blockentity/TimedProductionBlockEntity.java"),
                "TimedProductionBlockEntity", "computeReady", 0);
        assertTrue(hasInvocation(timedReady, null, "getCurrentAbsMinute"));

        MethodTree absoluteMinute = parseMethod(
                sourcePath("blockentity/TimedProductionBlockEntity.java"),
                "TimedProductionBlockEntity", "getCurrentAbsMinute", 0);
        assertTrue(hasInvocation(absoluteMinute, "tm", "getCurrentTime"));
        assertTrue(hasInvocation(absoluteMinute, null, "getCurrentDayIndex"));

        for (String file : List.of("CaskBlockEntity.java", "SolarPanelBlockEntity.java")) {
            String className = file.substring(0, file.length() - ".java".length());
            MethodTree ticker = parseMethod(
                    sourcePath("blockentity/" + file), className, "tickServer", 3);
            assertTrue(hasInvocation(ticker, null, "getCurrentDayIndex"),
                    className + " ticker must read the settlement-visible day through its getter");
            MethodTree dayIndex = parseMethod(
                    sourcePath("blockentity/" + file), className, "getCurrentDayIndex", 0);
            assertTrue(hasInvocation(dayIndex, "tm", "getCurrentYear"));
            assertTrue(hasInvocation(dayIndex, "tm", "getCurrentSeason"));
            assertTrue(hasInvocation(dayIndex, "tm", "getCurrentDay"));
        }

        for (String getter : List.of(
                "getCurrentTime", "getCurrentYear", "getCurrentSeason", "getCurrentDay")) {
            MethodTree method = parseMethod(
                    sourcePath("time/StardewTimeManager.java"),
                    "StardewTimeManager", getter, 0);
            assertTrue(hasInvocation(method, "DailySettlementDateView", "current"), getter);
        }
    }

    @Test
    void neoforgeEarlyRejectionIsParticipantScopedAndUsesConcreteEvents()
            throws IOException {
        String events = source(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementEvents.java");
        String guard = source(
                "src/main/java/com/stardew/craft/time/settlement/DailySettlementAccessGuard.java");

        assertTrue(events.contains("PlayerTickEvent.Pre"));
        assertTrue(events.contains("EntityTeleportEvent"));
        assertTrue(events.contains("PlayerInteractEvent.LeftClickBlock"));
        assertTrue(events.contains("PlayerInteractEvent.RightClickBlock"));
        assertTrue(events.contains("PlayerInteractEvent.EntityInteract"));
        assertTrue(events.contains("LivingEntityUseItemEvent.Start"));
        assertTrue(events.contains("ItemEntityPickupEvent.Pre"));
        assertFalse(events.contains("onPlayerInteract(PlayerInteractEvent event)"));
        assertTrue(guard.contains("barrier.isLocked(playerId)"));
        assertTrue(guard.contains("player.setDeltaMovement(Vec3.ZERO)"));
        assertTrue(guard.contains("player.teleportTo("));
        assertFalse(guard.contains("server.pause"));
    }

    private static void assertDate(
            StardewTimeManager time, int year, int season, int day, int minute) {
        assertEquals(year, time.getCurrentYear());
        assertEquals(season, time.getCurrentSeason());
        assertEquals(day, time.getCurrentDay());
        assertEquals(minute, time.getCurrentTime());
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private static Path sourcePath(String relativePath) {
        return projectRoot().resolve("src/main/java/com/stardew/craft/").resolve(relativePath);
    }

    private static MethodTree parseMethod(
            Path sourcePath, String className, String methodName, int parameterCount)
            throws IOException {
        String source = Files.readString(sourcePath);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "tests require a JDK compiler");
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
            assertTrue(parseErrors.isEmpty(),
                    () -> "source did not parse: " + String.join("; ", parseErrors));

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

    private static com.sun.source.tree.IfTree findDirectIf(
            BlockTree block, Predicate<ExpressionTree> condition) {
        List<com.sun.source.tree.IfTree> matches = new ArrayList<>();
        block.getStatements().stream()
                .filter(com.sun.source.tree.IfTree.class::isInstance)
                .map(com.sun.source.tree.IfTree.class::cast)
                .filter(candidate -> condition.test(candidate.getCondition()))
                .forEach(matches::add);
        assertEquals(1, matches.size(), "expected exactly one matching if statement");
        return matches.getFirst();
    }

    private static boolean hasInvocation(
            Tree tree, String receiver, String methodName, String... arguments) {
        return invocations(tree).stream()
                .anyMatch(invocation -> isInvocation(invocation, receiver, methodName, arguments));
    }

    private static List<MethodInvocationTree> invocations(Tree tree) {
        List<MethodInvocationTree> result = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
                result.add(invocation);
                return super.visitMethodInvocation(invocation, unused);
            }
        }.scan(tree, null);
        return result;
    }

    private static boolean isInvocation(
            MethodInvocationTree invocation, String receiver, String methodName,
            String... arguments) {
        if (!(invocation.getMethodSelect() instanceof com.sun.source.tree.MemberSelectTree select)) {
            return receiver == null
                    && invocation.getMethodSelect().toString().equals(methodName)
                    && argumentsMatch(invocation, arguments);
        }
        return (receiver == null
                || normalized(select.getExpression()).equals(normalized(receiver)))
                && select.getIdentifier().contentEquals(methodName)
                && argumentsMatch(invocation, arguments);
    }

    private static boolean argumentsMatch(MethodInvocationTree invocation, String... arguments) {
        return invocation.getArguments().stream().map(DailySettlementIsolationContractTest::normalized)
                .toList().equals(List.of(arguments).stream()
                        .map(DailySettlementIsolationContractTest::normalized).toList());
    }

    private static ExpressionTree unwrapped(ExpressionTree expression) {
        ExpressionTree current = expression;
        while (current instanceof ParenthesizedTree parenthesized) {
            current = parenthesized.getExpression();
        }
        return current;
    }

    private static BlockTree asBlock(com.sun.source.tree.StatementTree statement) {
        assertTrue(statement instanceof BlockTree, "expected block statement");
        return (BlockTree) statement;
    }

    private static String normalized(Object syntaxTree) {
        return syntaxTree.toString().replaceAll("\\s+", "");
    }

    private static Path projectRoot() {
        return Path.of(System.getProperty("stardewcraft.projectDir"));
    }
}
