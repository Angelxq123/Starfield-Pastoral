package com.stardew.craft.time.settlement;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import org.junit.jupiter.api.Test;

import javax.lang.model.element.Modifier;
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
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OvernightBarrierIntegrationContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir", "."));
    private static final Path SCREEN = source("client/gui/overnight/SleepWaitingOverlayScreen.java");
    private static final Path CLIENT_HANDLER = source("network/overnight/ClientOvernightHandler.java");
    private static final Path COLLAPSE_CLIENT_STATE =
            source("network/overnight/OvernightCollapseClientState.java");
    private static final Path SETTLEMENT = source("network/overnight/OvernightSettlementPayload.java");
    private static final Path CANCEL = source("network/payload/SleepCancelPayload.java");
    private static final Path ACK = source("network/overnight/OvernightReadyAckPayload.java");
    private static final Path BARRIER_PAYLOAD = source("network/overnight/OvernightBarrierPayload.java");
    private static final Path PACKETS = source("network/PacketHandler.java");
    private static final Path BARRIER = source("time/settlement/DailySettlementBarrier.java");
    private static final Path SERVICES = source("time/settlement/DailySettlementServices.java");
    private static final Path SLEEP_VOTES = source("event/SleepVoteTracker.java");
    private static final Path SLEEP_HANDLER = source("event/SleepInteractionHandler.java");
    private static final Path DIMENSION = source("event/DimensionEventHandler.java");

    @Test
    void waitingScreenRoutesAllInputThroughTheBarrierGate() throws IOException {
        MethodTree key = method(SCREEN, "SleepWaitingOverlayScreen", "keyPressed", 3);
        MethodTree mouse = method(SCREEN, "SleepWaitingOverlayScreen", "mouseClicked", 3);
        assertTrue(hasInvocation(key.getBody(), "handleDismissInput"));
        assertTrue(hasInvocation(mouse.getBody(), "handleDismissInput"));

        MethodTree gate = method(SCREEN, "SleepWaitingOverlayScreen", "handleDismissInput", 0);
        assertTrue(hasInvocationWithSelect(
                gate.getBody(), "ClientOvernightHandler.handleWaitingInput"));
    }

    @Test
    void singlePlayerSleepPublishesValidProgressBeforeSettlementLocks() throws IOException {
        String source = Files.readString(SLEEP_VOTES).replaceAll("\\s+", "");
        int methodStart = source.indexOf("castVoteInternal(ServerPlayerplayer,intsleepMinute,booleanbroadcastProgress)");
        int methodEnd = source.indexOf("publicstaticintgetLatestSleepMinute()", methodStart);
        String method = source.substring(methodStart, methodEnd);

        assertFalse(method.contains("if(totalStardewPlayers<=1){returntrue;}"));
        int progress = method.indexOf("broadcastVoteProgress(server,votedCount,required)");
        int result = method.indexOf("returnvotedCount>=required");
        assertTrue(progress >= 0 && progress < result,
                "the client must receive 1/1 before the barrier opens its waiting screen");
    }

    @Test
    void waitingScreenHasCancelableVoteButtonBeforeBarrierLock() throws IOException {
        MethodTree init = method(SCREEN, "SleepWaitingOverlayScreen", "init", 0);
        MethodTree mouse = method(SCREEN, "SleepWaitingOverlayScreen", "mouseClicked", 3);
        String screen = Files.readString(SCREEN);

        assertTrue(hasInvocation(init.getBody(), "addRenderableWidget"));
        assertTrue(screen.contains("ClientOvernightHandler.requestCancelWaiting()"));
        assertTrue(screen.contains("ClientOvernightHandler.canCancelWaiting()"));
        assertTrue(hasInvocationWithSelect(mouse.getBody(), "super.mouseClicked"));
    }

    @Test
    void vanillaWakeCannotConsumeCustomVotesBeforeThirdConfirmation() throws IOException {
        MethodTree finished = method(DIMENSION, "DimensionEventHandler", "onSleepFinished", 1);
        MethodTree request = method(DIMENSION, "DimensionEventHandler", "requestSleepAdvance", 3);
        String wakeHandler = Files.readString(SLEEP_HANDLER).replaceAll("\\s+", "");

        assertTrue(hasInvocation(finished.getBody(), "setTimeAddition"));
        assertTrue(hasInvocation(finished.getBody(), "preserveVotesForVanillaWake"));
        assertFalse(hasInvocation(finished.getBody(), "advanceToNextMorning"));
        assertFalse(request.getBody().toString().contains("!player.isSleeping()"));
        assertTrue(wakeHandler.contains("if(preserveVoteOnNextWake.remove(player.getUUID()))"));
    }

    @Test
    void clientLogoutEventDelegatesToTheSingleConnectionResetMethod() throws IOException {
        MethodTree logout = method(
                COLLAPSE_CLIENT_STATE, "OvernightCollapseClientState", "onLogout", 1);
        assertEquals("ClientPlayerNetworkEvent.LoggingOut",
                logout.getParameters().getFirst().getType().toString());
        assertTrue(logout.getModifiers().getAnnotations().stream()
                .anyMatch(annotation -> annotation.getAnnotationType().toString().equals("SubscribeEvent")));
        assertEquals(2, logout.getBody().getStatements().size(),
                "logout handler must only clear collapse and settlement session state");
        assertTrue(hasInvocation(logout.getBody(), "resetSession"));
        assertTrue(hasInvocationWithSelect(
                logout.getBody(), "ClientOvernightHandler.resetSession"));
        assertFalse(hasInvocation(logout.getBody(), "sendToServer"));
        assertFalse(hasInvocation(logout.getBody(), "setScreen"));
    }

    @Test
    void serverCancelChecksBarrierBeforeChangingSleepOrVoteState() throws IOException {
        MethodTree handle = method(CANCEL, "SleepCancelPayload", "handle", 2);
        BlockTree work = enqueueBlock(handle);
        int guard = directIfIndex(work, "isGameplayAllowed");
        int ready = directIfIndex(work, "hasUnacknowledgedReady");
        int get = invocationIndex(work, "getForPlayer");
        int stopSleeping = invocationIndex(work, "stopSleeping");
        int revoke = invocationIndex(work, "revokeVoteAndBroadcast");

        assertTrue(guard >= 0);
        assertTrue(get >= 0 && get < guard);
        assertTrue(ready >= 0 && ready < stopSleeping);
        assertTrue(hasReturn(work.getStatements().get(guard)));
        assertTrue(guard < stopSleeping);
        assertTrue(guard < revoke);
    }

    @Test
    void packetDirectionsMatchTheBarrierProtocol() throws IOException {
        MethodTree register = method(PACKETS, "PacketHandler", "register", 1);
        assertRegistration(register, "playToServer", "OvernightReadyAckPayload");
        assertRegistration(register, "playToClient", "OvernightBarrierPayload");
        assertRegistration(register, "playToClient", "OvernightSettlementPayload");
    }

    @Test
    void readyAckAcknowledgesOnlyItsPayloadDay() throws IOException {
        ClassTree ackClass = classTree(ACK, "OvernightReadyAckPayload");
        assertEquals(List.of("absoluteDay"), recordComponents(ackClass));
        List<? extends ExpressionTree> codec = compositeCodecArguments(ackClass);
        assertEquals(3, codec.size());
        assertMemberSelect(codec.get(0), "ByteBufCodecs", "VAR_INT");
        assertMemberReference(codec.get(1), "OvernightReadyAckPayload", "absoluteDay");
        assertConstructorReference(codec.get(2), "OvernightReadyAckPayload");

        MethodTree handle = method(ackClass, "handle", 2);
        assertEnqueues(handle, "acknowledge");
        BlockTree work = enqueueBlock(handle);
        MethodInvocationTree acknowledge = invocations(work).stream()
                .filter(invocation -> invocationName(invocation).equals("acknowledge"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ACK handler must acknowledge the barrier"));
        assertEquals(List.of("player.getUUID()", "payload.absoluteDay()"),
                acknowledge.getArguments().stream().map(Object::toString).toList());
        assertTrue(hasInvocation(work, "getForPlayer"));
        assertFalse(hasInvocation(work, "find"));
    }

    @Test
    void barrierPayloadCarriesDayAndLockStateToTheClientHandler() throws IOException {
        ClassTree payload = classTree(BARRIER_PAYLOAD, "OvernightBarrierPayload");
        assertEquals(List.of("absoluteDay", "locked"), recordComponents(payload));
        List<? extends ExpressionTree> codec = compositeCodecArguments(payload);
        assertEquals(5, codec.size());
        assertMemberSelect(codec.get(0), "ByteBufCodecs", "VAR_INT");
        assertMemberReference(codec.get(1), "OvernightBarrierPayload", "absoluteDay");
        assertMemberSelect(codec.get(2), "ByteBufCodecs", "BOOL");
        assertMemberReference(codec.get(3), "OvernightBarrierPayload", "locked");
        assertConstructorReference(codec.get(4), "OvernightBarrierPayload");

        MethodTree handle = method(payload, "handle", 2);
        assertEnqueues(handle, "handleClient");
        MethodTree handleClient = method(payload, "handleClient", 1);
        assertTrue(hasInvocationWithSelect(
                handleClient.getBody(), "ClientOvernightHandler.receiveBarrierState"));
    }

    @Test
    void settlementCodecStartsWithAbsoluteDayAndKeepsLegacyConstructors() throws IOException {
        ClassTree payload = classTree(SETTLEMENT, "OvernightSettlementPayload");
        assertEquals("absoluteDay", recordComponents(payload).getFirst());
        String source = Files.readString(SETTLEMENT).replaceAll("\\s+", "");
        int dayEncode = source.indexOf(
                "ByteBufCodecs.VAR_INT.encode(buffer,payload.absoluteDay())");
        int shippedEncode = source.indexOf(
                "shipped.encode(buffer,payload.shippedItems())");
        int personalEncode = source.indexOf(
                "ByteBufCodecs.BOOL.encode(buffer,payload.personalSettlement())");
        assertTrue(dayEncode >= 0);
        assertTrue(dayEncode < shippedEncode);
        assertTrue(shippedEncode < personalEncode);
        assertTrue(source.contains("ByteBufCodecs.BOOL.decode(buffer)"));
        assertTrue(constructors(payload).stream().anyMatch(constructor -> constructor.getParameters().size() == 2));
        assertTrue(constructors(payload).stream().anyMatch(constructor -> constructor.getParameters().size() == 5));
        assertTrue(constructors(payload).stream().anyMatch(constructor -> constructor.getParameters().size() == 3));
    }

    @Test
    void servicesRegistryUsesWeakServerKeysAndOwnsTheCoordinatorAndBarrier() throws IOException {
        ClassTree services = classTree(SERVICES, "DailySettlementServices");
        assertTrue(hasIdentifier(services, "WeakKeyRegistry"));
        assertNotNull(method(services, "get", 1));
        assertNotNull(method(services, "find", 1));
        assertNotNull(method(services, "remove", 1));
        assertTrue(hasIdentifier(services, "DailySettlementCoordinator"));
        assertTrue(hasIdentifier(services, "DailySettlementBarrier"));
    }

    private static Path source(String relative) {
        return PROJECT.resolve("src/main/java/com/stardew/craft").resolve(relative);
    }

    private static CompilationUnitTree unit(Path path) throws IOException {
        String source = Files.readString(path);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "tests require a JDK compiler");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject file = new SimpleJavaFileObject(
                URI.create("string:///" + path.getFileName()), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return source;
            }
        };
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(
                    null, manager, diagnostics, List.of("-proc:none"), null, List.of(file));
            CompilationUnitTree unit = task.parse().iterator().next();
            List<String> errors = diagnostics.getDiagnostics().stream()
                    .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                    .map(Object::toString)
                    .toList();
            assertTrue(errors.isEmpty(), () -> "source did not parse: " + String.join("; ", errors));
            return unit;
        }
    }

    private static ClassTree classTree(Path path, String name) throws IOException {
        return unit(path).getTypeDecls().stream()
                .filter(ClassTree.class::isInstance)
                .map(ClassTree.class::cast)
                .filter(type -> type.getSimpleName().contentEquals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("class is missing: " + name));
    }

    private static MethodTree method(Path path, String owner, String name, int parameters) throws IOException {
        return method(classTree(path, owner), name, parameters);
    }

    private static MethodTree method(ClassTree owner, String name, int parameters) {
        return owner.getMembers().stream()
                .filter(MethodTree.class::isInstance)
                .map(MethodTree.class::cast)
                .filter(candidate -> candidate.getName().contentEquals(name))
                .filter(candidate -> candidate.getParameters().size() == parameters)
                .findFirst()
                .orElseThrow(() -> new AssertionError("method is missing: " + name + "/" + parameters));
    }

    private static List<MethodTree> constructors(ClassTree owner) {
        return owner.getMembers().stream()
                .filter(MethodTree.class::isInstance)
                .map(MethodTree.class::cast)
                .filter(candidate -> candidate.getName().contentEquals("<init>"))
                .toList();
    }

    private static VariableTree field(ClassTree owner, String name) {
        return owner.getMembers().stream()
                .filter(VariableTree.class::isInstance)
                .map(VariableTree.class::cast)
                .filter(candidate -> candidate.getName().contentEquals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("field is missing: " + name));
    }

    private static List<String> recordComponents(ClassTree owner) {
        return owner.getMembers().stream()
                .filter(VariableTree.class::isInstance)
                .map(VariableTree.class::cast)
                .filter(component -> component.getInitializer() == null)
                .filter(component -> component.getModifiers().getFlags()
                        .equals(EnumSet.of(Modifier.PRIVATE, Modifier.FINAL)))
                .map(component -> component.getName().toString())
                .toList();
    }

    private static BlockTree enqueueBlock(MethodTree handle) {
        MethodInvocationTree enqueue = invocations(handle.getBody()).stream()
                .filter(invocation -> invocationName(invocation).equals("enqueueWork"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("enqueueWork call is missing"));
        assertEquals(1, enqueue.getArguments().size());
        LambdaExpressionTree lambda = (LambdaExpressionTree) enqueue.getArguments().getFirst();
        return (BlockTree) lambda.getBody();
    }

    private static List<IfTree> directIfs(BlockTree block) {
        return block.getStatements().stream()
                .filter(IfTree.class::isInstance)
                .map(IfTree.class::cast)
                .toList();
    }

    private static int directIfIndex(BlockTree block, String invocation) {
        for (int i = 0; i < block.getStatements().size(); i++) {
            StatementTree statement = block.getStatements().get(i);
            if (statement instanceof IfTree tree && hasInvocation(tree.getCondition(), invocation)) {
                return i;
            }
        }
        return -1;
    }

    private static int invocationIndex(BlockTree block, String name) {
        for (int i = 0; i < block.getStatements().size(); i++) {
            if (hasInvocation(block.getStatements().get(i), name)) {
                return i;
            }
        }
        return -1;
    }

    private static int directInvocationStatement(BlockTree block, String name) {
        for (int i = 0; i < block.getStatements().size(); i++) {
            StatementTree statement = block.getStatements().get(i);
            if (statement instanceof ExpressionStatementTree && hasInvocation(statement, name)) {
                return i;
            }
        }
        return -1;
    }

    private static AssignmentTree directAssignment(BlockTree block, String variableName) {
        return block.getStatements().stream()
                .filter(ExpressionStatementTree.class::isInstance)
                .map(ExpressionStatementTree.class::cast)
                .map(ExpressionStatementTree::getExpression)
                .filter(AssignmentTree.class::isInstance)
                .map(AssignmentTree.class::cast)
                .filter(assignment -> assignment.getVariable().toString().equals(variableName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("assignment is missing: " + variableName));
    }

    private static int directAssignmentIndex(BlockTree block, String variableName) {
        AssignmentTree assignment = directAssignment(block, variableName);
        for (int i = 0; i < block.getStatements().size(); i++) {
            StatementTree statement = block.getStatements().get(i);
            if (statement instanceof ExpressionStatementTree expression
                    && expression.getExpression() == assignment) {
                return i;
            }
        }
        throw new AssertionError("assignment statement is missing: " + variableName);
    }

    private static int assignmentOwnerIndex(BlockTree block, String variableName) {
        for (int i = 0; i < block.getStatements().size(); i++) {
            if (hasAssignmentTo(block.getStatements().get(i), variableName)) {
                return i;
            }
        }
        throw new AssertionError("assignment owner is missing: " + variableName);
    }

    private static void assertRegistration(MethodTree register, String direction, String payload) {
        String owner = "com.stardew.craft.network.overnight." + payload;
        MethodInvocationTree registration = invocations(register.getBody()).stream()
                .filter(invocation -> invocationName(invocation).equals(direction))
                .filter(invocation -> invocation.getArguments().size() == 3)
                .filter(invocation -> isMemberSelect(invocation.getArguments().get(0), owner, "TYPE"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(payload + " must be registered with " + direction));

        assertMemberSelect(registration.getArguments().get(0), owner, "TYPE");
        assertMemberSelect(registration.getArguments().get(1), owner, "STREAM_CODEC");
        assertMemberReference(registration.getArguments().get(2), owner, "handle");
    }

    private static List<? extends ExpressionTree> compositeCodecArguments(ClassTree payload) {
        ExpressionTree initializer = field(payload, "STREAM_CODEC").getInitializer();
        assertTrue(initializer instanceof MethodInvocationTree,
                "STREAM_CODEC must be initialized by StreamCodec.composite");
        MethodInvocationTree composite = (MethodInvocationTree) initializer;
        assertEquals("StreamCodec.composite", composite.getMethodSelect().toString());
        return composite.getArguments();
    }

    private static boolean isMemberSelect(Tree tree, String owner, String member) {
        return tree instanceof MemberSelectTree select
                && select.getExpression().toString().equals(owner)
                && select.getIdentifier().contentEquals(member);
    }

    private static void assertMemberSelect(Tree tree, String owner, String member) {
        assertTrue(isMemberSelect(tree, owner, member),
                () -> "expected member select " + owner + "." + member + ", got " + tree);
    }

    private static void assertMemberReference(Tree tree, String owner, String member) {
        assertTrue(tree instanceof MemberReferenceTree,
                () -> "expected member reference " + owner + "::" + member + ", got " + tree);
        MemberReferenceTree reference = (MemberReferenceTree) tree;
        assertEquals(MemberReferenceTree.ReferenceMode.INVOKE, reference.getMode());
        assertEquals(owner, reference.getQualifierExpression().toString());
        assertTrue(reference.getName().contentEquals(member));
    }

    private static void assertConstructorReference(Tree tree, String owner) {
        assertTrue(tree instanceof MemberReferenceTree,
                () -> "expected constructor reference " + owner + "::new, got " + tree);
        MemberReferenceTree reference = (MemberReferenceTree) tree;
        assertEquals(MemberReferenceTree.ReferenceMode.NEW, reference.getMode());
        assertEquals(owner, reference.getQualifierExpression().toString());
    }

    private static void assertEnqueues(MethodTree handler, String enclosedInvocation) {
        List<MethodInvocationTree> enqueueCalls = invocations(handler.getBody()).stream()
                .filter(invocation -> invocation.getMethodSelect().toString().equals("context.enqueueWork"))
                .toList();
        assertEquals(1, enqueueCalls.size(), "handler must enqueue exactly one work item");
        MethodInvocationTree enqueue = enqueueCalls.getFirst();
        assertEquals(1, enqueue.getArguments().size());
        assertTrue(enqueue.getArguments().getFirst() instanceof LambdaExpressionTree);
        LambdaExpressionTree work = (LambdaExpressionTree) enqueue.getArguments().getFirst();
        assertTrue(hasInvocation(work.getBody(), enclosedInvocation));
    }

    private static boolean hasInvocation(Tree tree, String name) {
        return invocations(tree).stream().anyMatch(invocation -> invocationName(invocation).equals(name));
    }

    private static boolean hasInvocationWithSelect(Tree tree, String methodSelect) {
        return invocations(tree).stream()
                .anyMatch(invocation -> invocation.getMethodSelect().toString().equals(methodSelect));
    }

    private static List<MethodInvocationTree> invocations(Tree tree) {
        List<MethodInvocationTree> found = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
                found.add(node);
                return super.visitMethodInvocation(node, unused);
            }
        }.scan(tree, null);
        return found;
    }

    private static String invocationName(MethodInvocationTree invocation) {
        String select = invocation.getMethodSelect().toString();
        int dot = select.lastIndexOf('.');
        return dot >= 0 ? select.substring(dot + 1) : select;
    }

    private static boolean hasAssignmentTo(Tree tree, String fieldName) {
        final boolean[] found = {false};
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitAssignment(com.sun.source.tree.AssignmentTree node, Void unused) {
                if (node.getVariable().toString().equals(fieldName)) {
                    found[0] = true;
                }
                return super.visitAssignment(node, unused);
            }
        }.scan(tree, null);
        return found[0];
    }

    private static boolean hasComparison(Tree tree, Tree.Kind kind) {
        final boolean[] found = {false};
        new TreeScanner<Void, Void>() {
            @Override
            public Void scan(Tree node, Void unused) {
                if (node != null && node.getKind() == kind) {
                    found[0] = true;
                }
                return super.scan(node, unused);
            }
        }.scan(tree, null);
        return found[0];
    }

    private static boolean hasComparisonBetween(
            Tree tree, Tree.Kind kind, String leftOperand, String rightOperand) {
        final boolean[] found = {false};
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitBinary(BinaryTree node, Void unused) {
                if (node.getKind() == kind
                        && node.getLeftOperand().toString().equals(leftOperand)
                        && node.getRightOperand().toString().equals(rightOperand)) {
                    found[0] = true;
                }
                return super.visitBinary(node, unused);
            }
        }.scan(tree, null);
        return found[0];
    }

    private static boolean hasReturn(Tree tree) {
        final boolean[] found = {false};
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitReturn(ReturnTree node, Void unused) {
                found[0] = true;
                return super.visitReturn(node, unused);
            }
        }.scan(tree, null);
        return found[0];
    }

    private static boolean hasNewClass(Tree tree, String simpleName) {
        return newClasses(tree).stream().anyMatch(name -> name.equals(simpleName));
    }

    private static List<String> newClasses(Tree tree) {
        List<String> names = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitNewClass(NewClassTree node, Void unused) {
                names.add(node.getIdentifier().toString());
                return super.visitNewClass(node, unused);
            }
        }.scan(tree, null);
        return names;
    }

    private static boolean hasMemberReference(Tree tree, String name) {
        return memberReferences(tree).contains(name);
    }

    private static List<String> memberReferences(Tree tree) {
        List<String> names = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMemberReference(MemberReferenceTree node, Void unused) {
                names.add(node.getName().toString());
                return super.visitMemberReference(node, unused);
            }
        }.scan(tree, null);
        return names;
    }

    private static boolean hasFieldReference(Tree tree, String name) {
        return invocations(tree).stream().anyMatch(invocation -> invocationName(invocation).equals(name));
    }

    private static boolean hasIdentifier(Tree tree, String name) {
        final boolean[] found = {false};
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitIdentifier(com.sun.source.tree.IdentifierTree node, Void unused) {
                if (node.getName().contentEquals(name)) {
                    found[0] = true;
                }
                return super.visitIdentifier(node, unused);
            }
        }.scan(tree, null);
        return found[0];
    }
}
