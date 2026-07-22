package com.stardew.craft.farm;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
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
import java.util.List;
import java.util.Set;
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
    void publicCreateFarmReadsOneTimeManagerAndDelegatesBothCursorValues() throws Exception {
        Method api = FarmInstanceRegistry.class.getDeclaredMethod(
                "createFarm", UUID.class, String.class, String.class, FarmType.class);
        assertTrue(Modifier.isPublic(api.getModifiers()));

        String source = normalized(parseMethod(
                REGISTRY_SOURCE, "FarmInstanceRegistry", "createFarm", 4));
        assertEquals(1, occurrences(source, "StardewTimeManager.get()"));
        assertEquals(1, occurrences(source, "tm.getAbsoluteDay()"));
        assertEquals(1, occurrences(source, "tm.getCurrentSeason()"));
        assertOrdered(source,
                "StardewTimeManagertm=StardewTimeManager.get();",
                "returncreateFarmAtDate(playerUUID,playerName,farmName,farmType,"
                        + "tm.getAbsoluteDay(),tm.getCurrentSeason());");
    }

    @Test
    void logoutFarmBlockOnlyNotifiesChunkManager() throws IOException {
        MethodTree logout = parseMethod(
                PLAYER_HANDLER_SOURCE, "PlayerDataEventHandler", "onPlayerLogout", 1);
        String farmBlock = normalized(smallestBlockContaining(
                logout, "FarmInstanceRegistry.get()", "onPlayerLeaveFarm"));

        assertTrue(farmBlock.contains("onPlayerLeaveFarm"));
        assertFalse(farmBlock.contains("OfflineFarmCatchUp.computeAbsoluteDay"));
        assertFalse(farmBlock.contains("StardewTimeManager"));
        assertFalse(farmBlock.contains("setLastOnlineDay"));
        assertFalse(farmBlock.contains("setLastOnlineSeason"));
        assertFalse(farmBlock.contains("registry.setDirty()"));
    }

    @Test
    void transferAndLoadContinuePreservingExistingCursor() throws IOException {
        String transfer = normalized(parseMethod(
                REGISTRY_SOURCE, "FarmInstanceRegistry", "transferFarm", 3));
        assertTrue(transfer.contains(
                "transferred.setLastOnlineDay(farm.getLastOnlineDay());"));
        assertTrue(transfer.contains(
                "transferred.setLastOnlineSeason(farm.getLastOnlineSeason());"));

        String load = normalized(parseMethod(
                REGISTRY_SOURCE, "FarmInstanceRegistry", "load", 2));
        assertOrdered(load,
                "FarmInstanceinstance=FarmInstance.load(list.getCompound(i));",
                "registry.instances.put(instance.getOwnerUUID(),instance);",
                "registry.slotToOwner.put(instance.getSlotIndex(),instance.getOwnerUUID());");
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

    private static BlockTree smallestBlockContaining(MethodTree method, String... fragments) {
        List<BlockTree> matches = new ArrayList<>();
        method.accept(new TreeScanner<Void, Void>() {
            @Override
            public Void visitBlock(BlockTree block, Void unused) {
                String source = normalized(block);
                if (Set.of(fragments).stream().allMatch(source::contains)) {
                    matches.add(block);
                }
                return super.visitBlock(block, unused);
            }
        }, null);
        return matches.stream()
                .min(java.util.Comparator.comparingInt(block -> normalized(block).length()))
                .orElseThrow(() -> new AssertionError(
                        "missing block containing " + String.join(", ", fragments)));
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

    private static String normalized(Object syntaxTree) {
        return syntaxTree.toString().replaceAll("\\s+", "");
    }

    private static void assertOrdered(String source, String... operations) {
        int cursor = 0;
        for (String operation : operations) {
            int operationIndex = source.indexOf(operation, cursor);
            assertTrue(operationIndex >= cursor, () -> "missing or out-of-order operation: " + operation);
            cursor = operationIndex + operation.length();
        }
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        int cursor = 0;
        while ((cursor = source.indexOf(value, cursor)) >= 0) {
            count++;
            cursor += value.length();
        }
        return count;
    }
}
