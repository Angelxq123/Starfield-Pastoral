package com.stardew.craft.farm;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmChunkManagerTest {

    @Test
    void includesEveryChunkTouchedByFarmBounds() {
        Set<ChunkPos> chunks = FarmChunkManager.chunkPositionsForBounds(
            new BlockPos(-17, 40, -1),
            new BlockPos(16, 90, 32)
        );

        assertEquals(16, chunks.size());
        assertTrue(chunks.contains(new ChunkPos(-2, -1)));
        assertTrue(chunks.contains(new ChunkPos(1, 2)));
    }

    @Test
    void normalizesReversedBounds() {
        Set<ChunkPos> chunks = FarmChunkManager.chunkPositionsForBounds(
            new BlockPos(31, 90, 31),
            new BlockPos(0, 40, 0)
        );

        assertEquals(Set.of(
            new ChunkPos(0, 0), new ChunkPos(0, 1),
            new ChunkPos(1, 0), new ChunkPos(1, 1)
        ), chunks);
    }

    @Test
    void exposesPackagePrivateTargetedLeaseApiWithoutCheckedClose() throws Exception {
        Method method = FarmChunkManager.class.getDeclaredMethod(
            "acquireTemporaryChunks", ServerLevel.class, Collection.class);

        assertEquals(TemporaryChunkLeaseTracker.Lease.class, method.getReturnType());
        assertFalse(Modifier.isPublic(method.getModifiers()));
        assertEquals(0, TemporaryChunkLeaseTracker.Lease.class.getMethod("close").getExceptionTypes().length);
    }

    @Test
    void retainsLegacyTemporaryFarmChunkMethods() throws Exception {
        assertPublicVoidMethod("acquireTemporaryFarmChunks");
        assertPublicVoidMethod("releaseTemporaryFarmChunks");
        assertPublicVoidMethod("forceLoadFarmChunksForCatchUp");
        assertPublicVoidMethod("releaseTempChunks");
    }

    @Test
    void wrapperStateUsesServerLevelIdentityAndSlot() throws IOException {
        String source = managerSource();

        assertTrue(source.contains(
            "IdentityHashMap<ServerLevel, Map<Integer, TemporaryFarmLoad>> temporaryFarmLoads"));
        assertTrue(source.contains("temporaryFarmLoads.computeIfAbsent(level"));
        assertTrue(source.contains("temporaryFarmLoads.get(level)"));
    }

    @Test
    void targetedBackendSeparatesOwnershipLoadingAndRelease() throws IOException {
        String source = managerSource();
        Set<String> backendMethods = Arrays.stream(TemporaryChunkLeaseTracker.Backend.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());
        var loadMethod = Pattern.compile(
            "public void load\\(ServerLevel level, ChunkPos chunk\\)\\s*\\{(?<body>.*?)\\n\\s*}",
            Pattern.DOTALL).matcher(source);

        assertEquals(Set.of("acquire", "load", "release"), backendMethods);
        assertTrue(source.contains("if (level.getForcedChunks().contains(chunkKey))"));
        assertTrue(source.contains("return false;"));
        assertTrue(source.contains("return level.setChunkForced(chunk.x, chunk.z, true)"));
        assertTrue(loadMethod.find());
        assertTrue(loadMethod.group("body").contains("PerformanceCounter.FARM_SYNC_CHUNK_LOADS"));
        assertTrue(loadMethod.group("body").contains("PerformanceTiming.FARM_SYNC_CHUNK_LOAD"));
        assertTrue(loadMethod.group("body").contains("level.getChunk(chunk.x, chunk.z)"));
        assertTrue(source.contains("level.setChunkForced(chunk.x, chunk.z, false)"));
        assertFalse(source.contains("catch (RuntimeException exception)"));
    }

    @Test
    void serverStoppingOnlyClosesWrappersAndTrackerForProvidedLevel() throws IOException {
        String source = managerSource();

        assertTrue(source.contains("temporaryFarmLoads.remove(level)"));
        assertTrue(source.contains("temporaryChunkLeases.closeAll(level)"));
        assertFalse(source.contains("temporaryFarmLoads.clear()"));
        assertTrue(source.contains("playerCounts.clear()"));
    }

    @Test
    void serverStoppingAlwaysClearsPlayerCountsWhenTrackerCleanupFails() throws IOException {
        String source = managerSource();
        Pattern nestedCleanup = Pattern.compile(
            "finally\\s*\\{\\s*try\\s*\\{\\s*temporaryChunkLeases\\.closeAll\\(level\\);\\s*}"
                + "\\s*finally\\s*\\{\\s*playerCounts\\.clear\\(\\);\\s*}\\s*}",
            Pattern.DOTALL);

        assertTrue(nestedCleanup.matcher(source).find(),
            "playerCounts.clear() must run in a nested finally after tracker cleanup");
    }

    private static void assertPublicVoidMethod(String name) throws Exception {
        Method method = FarmChunkManager.class.getDeclaredMethod(name, ServerLevel.class, int.class);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertEquals(void.class, method.getReturnType());
    }

    private static String managerSource() throws IOException {
        Path projectDir = Path.of(System.getProperty("stardewcraft.projectDir"));
        return Files.readString(projectDir.resolve(
            "src/main/java/com/stardew/craft/farm/FarmChunkManager.java"));
    }
}
