package com.stardew.craft.farm;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.JavacTask;
import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.manager.TreeGrowthManager;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineFarmCatchUpContractTest {
    private static final Path SOURCE = Path.of(System.getProperty("stardewcraft.projectDir", "."))
            .resolve("src/main/java/com/stardew/craft/farm/OfflineFarmCatchUp.java");

    @Test
    void astMethodExtractionKeepsTopLevelTryWholeAfterNestedBlocks() throws IOException {
        MethodTree method = parseMethod("""
                class Example {
                    void catchUp() {
                        if (ready()) {
                            nested();
                        }
                        try (var ignored = resource()) {
                            first();
                            second();
                        }
                        advance();
                    }
                }
                """, "Example", "catchUp");

        List<? extends StatementTree> statements = method.getBody().getStatements();
        assertEquals(3, statements.size());
        assertTrue(statements.get(1) instanceof TryTree);
        TryTree targetTry = (TryTree) statements.get(1);
        assertEquals(List.of("first();", "second();"),
                targetTry.getBlock().getStatements().stream().map(Object::toString).toList());
        assertEquals("advance();", statements.get(2).toString());
    }

    @Test
    void helpersAcceptPlannedPositionsAndOnlyTheManagersTheyNeed() throws ReflectiveOperationException {
        Method crops = OfflineFarmCatchUp.class.getDeclaredMethod(
                "catchUpCrops", ServerLevel.class, CropGrowthManager.class, List.class, int.class);
        Method trees = OfflineFarmCatchUp.class.getDeclaredMethod(
                "catchUpTrees", ServerLevel.class, TreeGrowthManager.class, List.class, int.class);
        Method sprinklers = OfflineFarmCatchUp.class.getDeclaredMethod(
                "catchUpSprinklers", ServerLevel.class, List.class);

        assertPrivateStaticVoid(crops);
        assertPrivateStaticVoid(trees);
        assertPrivateStaticVoid(sprinklers);
        assertArrayEquals(new java.lang.reflect.Type[]{
                        ServerLevel.class,
                        CropGrowthManager.class,
                        parameterizedListOfGlobalPos(crops, 2),
                        int.class
                }, crops.getGenericParameterTypes());
        assertArrayEquals(new java.lang.reflect.Type[]{
                        ServerLevel.class,
                        TreeGrowthManager.class,
                        parameterizedListOfGlobalPos(trees, 2),
                        int.class
                }, trees.getGenericParameterTypes());
        assertArrayEquals(new java.lang.reflect.Type[]{
                        ServerLevel.class,
                        parameterizedListOfGlobalPos(sprinklers, 1)
                }, sprinklers.getGenericParameterTypes());
    }

    @Test
    void catchUpSnapshotsEachIndexOnceAndBuildsPlanBeforeTargetedLease() throws IOException {
        String source = normalizedSource();
        String catchUp = catchUpMethod(source);

        assertEquals(1, occurrences(source, "getAllCropPositions()"));
        assertEquals(1, occurrences(source, "getAllSaplingPositions()"));
        assertEquals(1, occurrences(source, "getAllSprinklerPositions()"));
        assertOrdered(catchUp,
                "if(daysMissed<=0)return;",
                "if(farm.getLastOnlineSeason()!=currentSeason)",
                "CropGrowthManagercropMgr=CropGrowthManager.get(level);",
                "TreeGrowthManagertreeMgr=TreeGrowthManager.get(level);",
                "SprinklerManagersprMgr=SprinklerManager.get(level);",
                "OfflineFarmCatchUpPlanplan=OfflineFarmCatchUpPlan.create(",
                "level.dimension(),",
                "farm.getFarmBoundsMin(),",
                "farm.getFarmBoundsMax(),",
                "cropMgr.getAllCropPositions(),",
                "treeMgr.getAllSaplingPositions(),",
                "sprMgr.getAllSprinklerPositions()",
                "FarmChunkManager.get().acquireTemporaryChunks(level,plan.requiredChunks())");
    }

    @Test
    void targetedLeaseOwnsOnlyProcessingAndCursorAdvancesAfterClose() throws IOException {
        String source = Files.readString(SOURCE);
        String catchUpSource = catchUpMethod(source.replaceAll("\\s+", ""));
        MethodTree catchUp = parseMethod(source, "OfflineFarmCatchUp", "catchUp");
        List<? extends StatementTree> topLevelStatements = catchUp.getBody().getStatements();
        List<TryTree> targetedTries = topLevelStatements.stream()
                .filter(TryTree.class::isInstance)
                .map(TryTree.class::cast)
                .filter(candidate -> normalized(candidate.getResources()).contains(
                        "acquireTemporaryChunks(level,plan.requiredChunks())"))
                .toList();

        assertEquals(1, targetedTries.size(), "catchUp must have one targeted temporary chunk lease");
        TryTree targetedTry = targetedTries.getFirst();
        assertTrue(normalized(targetedTry.getResources()).contains(
                "FarmChunkManager.get().acquireTemporaryChunks(level,plan.requiredChunks())"));
        assertEquals(List.of(
                "catchUpCrops(level,cropMgr,plan.crops(),daysMissed);",
                "catchUpTrees(level,treeMgr,plan.trees(),daysMissed);",
                "catchUpSprinklers(level,plan.sprinklers());"),
                normalizedStatements(targetedTry.getBlock().getStatements()));

        int targetedTryIndex = topLevelStatements.indexOf(targetedTry);
        assertEquals(List.of(
                "farm.setLastOnlineDay(currentAbsDay);",
                "farm.setLastOnlineSeason(currentSeason);",
                "registry.setDirty();",
                "StardewCraft.LOGGER.info(\"[FARM-CATCHUP]Catch-upcompleteforplayer{}\",playerUUID);"),
                normalizedStatements(topLevelStatements.subList(targetedTryIndex + 1, topLevelStatements.size())));
        assertFalse(catchUpSource.contains("acquireTemporaryFarmChunks"));
        assertFalse(catchUpSource.contains("releaseTemporaryFarmChunks"));
        assertFalse(catchUpSource.contains("catch("));
    }

    @Test
    void helpersReusePlanListsWithoutScanningIndexesOrFarmBounds() throws IOException {
        String source = normalizedSource();
        String crops = substringBetween(source,
                "privatestaticvoidcatchUpCrops(", "privatestaticvoidcatchUpTrees(");
        String trees = substringBetween(source,
                "privatestaticvoidcatchUpTrees(", "privatestaticvoidcatchUpSprinklers(");
        String sprinklers = source.substring(source.indexOf("privatestaticvoidcatchUpSprinklers("));

        for (String helper : List.of(crops, trees, sprinklers)) {
            assertFalse(helper.contains("FarmInstance"));
            assertFalse(helper.contains("getFarmBoundsMin"));
            assertFalse(helper.contains("getFarmBoundsMax"));
            assertFalse(helper.contains("getAllCropPositions"));
            assertFalse(helper.contains("getAllSaplingPositions"));
            assertFalse(helper.contains("getAllSprinklerPositions"));
        }
        assertTrue(crops.contains("for(GlobalPosgp:farmCrops)"));
        assertTrue(trees.contains("for(GlobalPosgp:farmTrees)"));
        assertTrue(sprinklers.contains("for(GlobalPosgp:farmSprinklers)"));
    }

    private static java.lang.reflect.Type parameterizedListOfGlobalPos(Method method, int index) {
        java.lang.reflect.ParameterizedType type =
                (java.lang.reflect.ParameterizedType) method.getGenericParameterTypes()[index];
        assertEquals(List.class, type.getRawType());
        assertArrayEquals(new java.lang.reflect.Type[]{GlobalPos.class}, type.getActualTypeArguments());
        return type;
    }

    private static MethodTree parseMethod(String source, String className, String methodName) throws IOException {
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
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("method is missing: " + methodName));
        }
    }

    private static List<String> normalizedStatements(List<? extends StatementTree> statements) {
        return statements.stream().map(OfflineFarmCatchUpContractTest::normalized).toList();
    }

    private static String normalized(Object syntaxTree) {
        return syntaxTree.toString().replaceAll("\\s+", "");
    }

    private static void assertPrivateStaticVoid(Method method) {
        assertTrue(Modifier.isPrivate(method.getModifiers()));
        assertTrue(Modifier.isStatic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    private static String catchUpMethod(String source) {
        return substringBetween(source,
                "publicstaticvoidcatchUp(ServerLevellevel,UUIDplayerUUID)",
                "privatestaticvoidcatchUpCrops(");
    }

    private static String normalizedSource() throws IOException {
        return Files.readString(SOURCE).replaceAll("\\s+", "");
    }

    private static String substringBetween(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue(startIndex >= 0 && endIndex > startIndex,
                () -> "missing source section between " + start + " and " + end);
        return source.substring(startIndex, endIndex);
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
