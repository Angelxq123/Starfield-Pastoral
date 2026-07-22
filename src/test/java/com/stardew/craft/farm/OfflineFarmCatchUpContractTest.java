package com.stardew.craft.farm;

import com.stardew.craft.manager.CropGrowthManager;
import com.stardew.craft.manager.TreeGrowthManager;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineFarmCatchUpContractTest {
    private static final Path SOURCE = Path.of(System.getProperty("stardewcraft.projectDir", "."))
            .resolve("src/main/java/com/stardew/craft/farm/OfflineFarmCatchUp.java");

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
        String catchUp = catchUpMethod(normalizedSource());
        String leaseHeader = "try(TemporaryChunkLeaseTracker.Leaseignored="
                + "FarmChunkManager.get().acquireTemporaryChunks(level,plan.requiredChunks())){";
        int leaseBodyStart = catchUp.indexOf(leaseHeader) + leaseHeader.length();
        int leaseBodyEnd = catchUp.indexOf("}", leaseBodyStart);
        assertTrue(leaseBodyStart >= leaseHeader.length() && leaseBodyEnd > leaseBodyStart,
                "targeted lease block is missing");
        String leaseBody = catchUp.substring(leaseBodyStart, leaseBodyEnd);
        String afterLease = catchUp.substring(leaseBodyEnd + 1);

        assertOrdered(leaseBody,
                "catchUpCrops(level,cropMgr,plan.crops(),daysMissed);",
                "catchUpTrees(level,treeMgr,plan.trees(),daysMissed);",
                "catchUpSprinklers(level,plan.sprinklers());");
        assertFalse(leaseBody.contains("farm.setLastOnlineDay"));
        assertFalse(leaseBody.contains("farm.setLastOnlineSeason"));
        assertFalse(leaseBody.contains("registry.setDirty"));
        assertOrdered(afterLease,
                "farm.setLastOnlineDay(currentAbsDay);",
                "farm.setLastOnlineSeason(currentSeason);",
                "registry.setDirty();",
                "StardewCraft.LOGGER.info(\"[FARM-CATCHUP]Catch-upcompleteforplayer{}\"");
        assertFalse(catchUp.contains("acquireTemporaryFarmChunks"));
        assertFalse(catchUp.contains("releaseTemporaryFarmChunks"));
        assertFalse(catchUp.contains("catch("));
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
