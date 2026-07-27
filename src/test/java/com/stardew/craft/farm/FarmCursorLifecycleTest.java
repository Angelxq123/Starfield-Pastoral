package com.stardew.craft.farm;

import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class FarmCursorLifecycleTest {
    private static final Path PROJECT = Path.of(System.getProperty("stardewcraft.projectDir", "."));
    private static final Path REGISTRY_SOURCE = PROJECT.resolve(
            "src/main/java/com/stardew/craft/farm/FarmInstanceRegistry.java");
    private static final Path PLAYER_HANDLER_SOURCE = PROJECT.resolve(
            "src/main/java/com/stardew/craft/player/PlayerDataEventHandler.java");

    @Test
    void createFarmAtDateInitializesCursorFromSuppliedDate() {
        FarmInstanceRegistry registry = new FarmInstanceRegistry();

        FarmInstance farm = createFarmAtDate(
                registry, UUID.randomUUID(), "Leah", "Forest", FarmType.STANDARD, 47, 2);

        assertEquals(47, farm.getLastOnlineDay());
        assertEquals(2, farm.getLastOnlineSeason());
    }

    @Test
    void createFarmAtDateReturnsExistingFarmWithoutOverwritingCursor() {
        FarmInstanceRegistry registry = new FarmInstanceRegistry();
        UUID owner = UUID.randomUUID();
        FarmInstance original = createFarmAtDate(
                registry, owner, "Sam", "First", FarmType.STANDARD, 12, 1);

        FarmInstance repeated = createFarmAtDate(
                registry, owner, "Renamed", "Second", FarmType.STANDARD, 80, 3);

        assertSame(original, repeated);
        assertEquals(12, repeated.getLastOnlineDay());
        assertEquals(1, repeated.getLastOnlineSeason());
    }

    @Test
    void publicCreateFarmReadsOneTimeManagerAndDirectlyDelegatesCursorValues() throws Exception {
        Method api = FarmInstanceRegistry.class.getDeclaredMethod(
                "createFarm", UUID.class, String.class, String.class, FarmType.class);
        assertTrue(Modifier.isPublic(api.getModifiers()));

        MethodTree create = parseMethod(REGISTRY_SOURCE, "FarmInstanceRegistry", "createFarm", 5);
        List<VariableTree> timeManagers = directVariables(create.getBody()).stream()
                .filter(variable -> isInvocation(
                        variable.getInitializer(), "StardewTimeManager", "get"))
                .toList();
        assertEquals(1, timeManagers.size());
        assertEquals(1, invocations(create).stream()
                .filter(invocation -> isInvocation(
                        invocation, "StardewTimeManager", "get"))
                .count());
        String managerName = timeManagers.getFirst().getName().toString();

        List<ReturnTree> returns = create.getBody().getStatements().stream()
                .filter(ReturnTree.class::isInstance)
                .map(ReturnTree.class::cast)
                .toList();
        assertEquals(1, returns.size());
        assertTrue(returns.getFirst().getExpression() instanceof MethodInvocationTree);
        MethodInvocationTree delegation = (MethodInvocationTree) returns.getFirst().getExpression();
        assertTrue(isInvocationNamed(delegation, null, "createFarmAtDate"));
        assertEquals(7, delegation.getArguments().size());
        assertTrue(isInvocation(delegation.getArguments().get(5),
                managerName, "getAbsoluteDay"));
        assertTrue(isInvocation(delegation.getArguments().get(6),
                managerName, "getCurrentSeason"));
    }

    @Test
    void explicitCreationInitializesBothCursorsBeforePublication() throws IOException {
        MethodTree create = parseMethod(
                REGISTRY_SOURCE, "FarmInstanceRegistry", "createFarmAtDate", 7);
        BlockTree body = create.getBody();
        int daySetter = directInvocationIndex(
                body, "instance", "setLastOnlineDay", "absoluteDay");
        int seasonSetter = directInvocationIndex(
                body, "instance", "setLastOnlineSeason", "season");
        int publication = directInvocationIndex(
                body, "instances", "put", "playerUUID", "instance");

        assertTrue(daySetter >= 0, "explicit creation must set the day cursor directly");
        assertTrue(seasonSetter >= 0, "explicit creation must set the season cursor directly");
        assertTrue(publication > daySetter, "day cursor must be set before publication");
        assertTrue(publication > seasonSetter, "season cursor must be set before publication");
    }

    @Test
    void logoutDirectlyNotifiesOnlyTheOccupancyManagerForFarmSessionState() throws IOException {
        MethodTree logout = parseMethod(
                PLAYER_HANDLER_SOURCE, "PlayerDataEventHandler", "onPlayerLogout", 1);
        IfTree serverPlayer = logout.getBody().getStatements().stream()
                .filter(IfTree.class::isInstance)
                .map(IfTree.class::cast)
                .filter(candidate -> bindsServerPlayer(candidate.getCondition()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("server-player logout branch is missing"));
        BlockTree playerBody = asBlock(serverPlayer.getThenStatement());

        int farmLogout = directInvocationIndex(playerBody,
                "com.stardew.craft.farm.FarmChunkManager.get()",
                "onPlayerLogout", "player");
        int settlementLogout = directInvocationIndex(playerBody,
                "com.stardew.craft.time.settlement.DailySettlementEvents",
                "onPlayerLogout", "player");
        assertEquals(0, farmLogout, "farm occupancy cleanup must be the first player-specific operation");
        assertTrue(settlementLogout > farmLogout,
                "settlement logout handling must remain after farm occupancy cleanup");
        assertEquals(1, invocations(playerBody).stream()
                .filter(invocation -> isInvocation(invocation,
                        "com.stardew.craft.farm.FarmChunkManager.get()",
                        "onPlayerLogout", "player"))
                .count());

        List<MethodInvocationTree> calls = invocations(logout);
        assertFalse(calls.stream().anyMatch(invocation ->
                isInvocationNamed(invocation, "OfflineFarmCatchUp", "computeAbsoluteDay")));
        assertFalse(calls.stream().anyMatch(invocation ->
                Objects.equals(receiver(invocation), "StardewTimeManager")
                        || Objects.equals(receiver(invocation),
                                "com.stardew.craft.time.StardewTimeManager")));
        assertFalse(calls.stream().anyMatch(invocation ->
                methodName(invocation).equals("setLastOnlineDay")
                        || methodName(invocation).equals("setLastOnlineSeason")));

        List<String> registryLocals = variables(logout).stream()
                .filter(variable -> isFarmRegistryGet(variable.getInitializer()))
                .map(variable -> variable.getName().toString())
                .toList();
        assertFalse(calls.stream().anyMatch(invocation ->
                isFarmRegistryDirtyInvocation(invocation, registryLocals)));
    }

    @Test
    void transferFarmCopiesAllFarmStateThroughTheSharedTransferHelper() throws IOException {
        MethodTree transfer = parseMethod(REGISTRY_SOURCE, "FarmInstanceRegistry", "transferFarm", 3);
        List<MethodInvocationTree> directCalls = directInvocations(transfer.getBody());
        MethodInvocationTree copy = singleInvocation(
                directCalls, "transferred", "copyTransferStateFrom");
        assertEquals(List.of("farm"), copy.getArguments().stream()
                .map(Object::toString).toList());

        FarmInstance source = new FarmInstance(
                UUID.randomUUID(), "Leah", "Forest", 1,
                new BlockPos(0, 64, 0), FarmType.STANDARD);
        source.setLastOnlineDay(73);
        source.setLastOnlineSeason(3);
        FarmInstance transferred = new FarmInstance(
                UUID.randomUUID(), "Robin", "Forest", 1,
                new BlockPos(0, 64, 0), FarmType.STANDARD);
        transferred.copyTransferStateFrom(source);

        assertEquals(73, transferred.getLastOnlineDay());
        assertEquals(3, transferred.getLastOnlineSeason());
    }

    @Test
    void farmInstanceNbtRoundTripPreservesCursor() {
        FarmInstance original = new FarmInstance(
                UUID.randomUUID(), "Penny", "River", 7, new BlockPos(128, 64, -96), FarmType.STANDARD);
        original.setLastOnlineDay(73);
        original.setLastOnlineSeason(3);

        CompoundTag tag = original.save();
        FarmInstance restored = FarmInstance.load(tag);

        assertEquals(73, restored.getLastOnlineDay());
        assertEquals(3, restored.getLastOnlineSeason());
    }

    private static FarmInstance createFarmAtDate(
            FarmInstanceRegistry registry,
            UUID owner,
            String ownerName,
            String farmName,
            FarmType farmType,
            int absoluteDay,
            int season) {
        try {
            Method method = FarmInstanceRegistry.class.getDeclaredMethod(
                    "createFarmAtDate", UUID.class, String.class, String.class,
                    FarmType.class, int.class, int.class);
            int modifiers = method.getModifiers();
            assertFalse(Modifier.isPublic(modifiers));
            assertFalse(Modifier.isProtected(modifiers));
            assertFalse(Modifier.isPrivate(modifiers));
            return (FarmInstance) method.invoke(
                    registry, owner, ownerName, farmName, farmType, absoluteDay, season);
        } catch (NoSuchMethodException exception) {
            fail("FarmInstanceRegistry.createFarmAtDate is missing", exception);
        } catch (IllegalAccessException exception) {
            fail("createFarmAtDate must be package-private", exception);
        } catch (InvocationTargetException exception) {
            fail("createFarmAtDate threw an exception", exception.getCause());
        }
        throw new AssertionError("unreachable");
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

    private static List<VariableTree> directVariables(BlockTree block) {
        return block.getStatements().stream()
                .filter(VariableTree.class::isInstance)
                .map(VariableTree.class::cast)
                .toList();
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

    private static List<MethodInvocationTree> invocations(Tree tree) {
        List<MethodInvocationTree> calls = new ArrayList<>();
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
                calls.add(invocation);
                return super.visitMethodInvocation(invocation, unused);
            }
        }.scan(tree, null);
        return calls;
    }

    private static List<VariableTree> variables(Tree tree) {
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

    private static boolean isFarmRegistryDirtyInvocation(
            MethodInvocationTree invocation, List<String> registryLocals) {
        if (!methodName(invocation).equals("setDirty")
                || !(invocation.getMethodSelect() instanceof MemberSelectTree select)) {
            return false;
        }
        ExpressionTree target = unwrapped(select.getExpression());
        if (target instanceof IdentifierTree identifier) {
            return registryLocals.stream()
                    .anyMatch(identifier.getName()::contentEquals);
        }
        return isFarmRegistryGet(target);
    }

    private static boolean isFarmRegistryGet(Tree tree) {
        return isInvocation(tree, "FarmInstanceRegistry", "get")
                || isInvocation(tree, "com.stardew.craft.farm.FarmInstanceRegistry", "get");
    }

    private static MethodInvocationTree singleInvocation(
            List<MethodInvocationTree> calls, String expectedReceiver, String expectedMethod) {
        List<MethodInvocationTree> matches = calls.stream()
                .filter(invocation -> isInvocationNamed(
                        invocation, expectedReceiver, expectedMethod))
                .toList();
        assertEquals(1, matches.size());
        return matches.getFirst();
    }

    private static int directInvocationIndex(
            BlockTree block, String expectedReceiver, String expectedMethod, String... arguments) {
        for (int index = 0; index < block.getStatements().size(); index++) {
            if (isDirectInvocation(
                    block.getStatements().get(index), expectedReceiver, expectedMethod, arguments)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isDirectInvocation(
            StatementTree statement, String expectedReceiver, String expectedMethod, String... arguments) {
        return statement instanceof ExpressionStatementTree expression
                && isInvocation(expression.getExpression(), expectedReceiver, expectedMethod, arguments);
    }

    private static boolean isInvocation(
            Tree tree, String expectedReceiver, String expectedMethod, String... arguments) {
        Tree candidate = tree instanceof ExpressionTree expression ? unwrapped(expression) : tree;
        if (!(candidate instanceof MethodInvocationTree invocation)
                || !isInvocationNamed(invocation, expectedReceiver, expectedMethod)) {
            return false;
        }
        List<String> actualArguments = invocation.getArguments().stream()
                .map(Object::toString)
                .toList();
        return actualArguments.equals(Arrays.asList(arguments));
    }

    private static boolean isInvocationNamed(
            MethodInvocationTree invocation, String expectedReceiver, String expectedMethod) {
        return Objects.equals(expectedReceiver, receiver(invocation))
                && methodName(invocation).equals(expectedMethod);
    }

    private static String receiver(MethodInvocationTree invocation) {
        return invocation.getMethodSelect() instanceof MemberSelectTree select
                ? select.getExpression().toString()
                : null;
    }

    private static String methodName(MethodInvocationTree invocation) {
        return invocation.getMethodSelect() instanceof MemberSelectTree select
                ? select.getIdentifier().toString()
                : invocation.getMethodSelect().toString();
    }

    private static ExpressionTree unwrapped(ExpressionTree expression) {
        ExpressionTree current = expression;
        while (current instanceof ParenthesizedTree parenthesized) {
            current = parenthesized.getExpression();
        }
        return current;
    }

    private static boolean bindsServerPlayer(ExpressionTree condition) {
        ExpressionTree expression = unwrapped(condition);
        if (!(expression instanceof InstanceOfTree instanceOf)
                || !(instanceOf.getPattern() instanceof BindingPatternTree binding)) {
            return false;
        }
        return binding.getVariable().getType().toString().equals("ServerPlayer")
                && binding.getVariable().getName().contentEquals("player");
    }

    private static BlockTree asBlock(StatementTree statement) {
        assertTrue(statement instanceof BlockTree, "expected block statement");
        return (BlockTree) statement;
    }
}
