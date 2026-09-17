package com.stardew.craft.client.model.terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;

/** Clips baked native top faces into rectangular pixel groups, preserving UV scale. */
final class TerrainFarmlandQuads {
    private final BakedQuad[] blends;
    private final boolean wet;
    private BakedQuad[] peers;
    private final Map<Long, List<BakedQuad>> cache = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, List<BakedQuad>> entry) { return size() > 512; }
    };

    TerrainFarmlandQuads(BakedQuad[] blends, boolean wet) {
        this.blends = blends;
        this.wet = wet;
    }

    void setPeers(BakedQuad[] peers) { this.peers = peers; cache.clear(); }

    List<BakedQuad> get(int soil, int moisture) { return get(soil, moisture, 0); }

    synchronized List<BakedQuad> get(int soil, int moisture, int peerMasks) {
        long key = GrassConnectionMask.canonical(soil) | (wet ? 0 : GrassConnectionMask.canonical(moisture) << 8)
                | ((long) peerMasks << 16);
        return cache.computeIfAbsent(key, this::build);
    }

    /** Shared grass textures sit one model pixel lower when crossing onto farmland. */
    static BakedQuad lowerOverlay(BakedQuad source) {
        int[] vertices = source.getVertices().clone();
        int stride = vertices.length / 4;
        for (int v = 0; v < vertices.length; v += stride)
            vertices[v + 1] = Float.floatToRawIntBits(Float.intBitsToFloat(vertices[v + 1]) - 1 / 16f);
        return new BakedQuad(vertices, source.getTintIndex(), source.getDirection(), source.getSprite(), source.isShade(), source.hasAmbientOcclusion());
    }

    private List<BakedQuad> build(long key) {
        int[] grid = new int[256];
        boolean[] used = new boolean[256];
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
        {
            int type = TerrainFarmlandEdges.blendIndex(wet, (int) key & 255, (int) (key >>> 8) & 255, x, z);
            // Bare-ground finishing and dry/wet transitions keep priority at mixed junctions.
            if (type == 0 && peers != null) {
                int strongest = 0;
                for (int family=0;family<3;family++) {
                    int weight = TerrainFarmlandEdges.weight((int) (key >>> (16 + family*8)), x, z);
                    if (weight > strongest) { strongest = weight; type = 16 + family*3 + weight-1; }
                }
            }
            grid[z * 16 + x] = type;
        }
        List<BakedQuad> result = new ArrayList<>();
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            int i = z * 16 + x;
            if (used[i]) continue;
            int type = grid[i], width = 1, height = 1;
            while (x + width < 16 && !used[i + width] && grid[i + width] == type) width++;
            outer: while (z + height < 16) {
                for (int dx = 0; dx < width; dx++)
                    if (used[(z + height) * 16 + x + dx] || grid[(z + height) * 16 + x + dx] != type) break outer;
                height++;
            }
            for (int dz = 0; dz < height; dz++) for (int dx = 0; dx < width; dx++) used[(z + dz) * 16 + x + dx] = true;
            result.add(crop(type < 16 ? blends[type] : peers[type-16], x / 16f, z / 16f, (x + width) / 16f, (z + height) / 16f));
        }
        return List.copyOf(result);
    }

    private static BakedQuad crop(BakedQuad source, float x0, float z0, float x1, float z1) {
        int[] original = source.getVertices(), vertices = original.clone();
        int stride = original.length / 4;
        int origin = 0, east = 0, south = 0;
        for (int i = 0; i < 4; i++) {
            int v = i * stride;
            boolean x = Float.intBitsToFloat(original[v]) > .5f;
            boolean z = Float.intBitsToFloat(original[v + 2]) > .5f;
            if (!x && !z) origin = v;
            if (x && !z) east = v;
            if (!x && z) south = v;
        }
        for (int i = 0; i < 4; i++) {
            int v = i * stride;
            float x = Float.intBitsToFloat(original[v]) > .5f ? x1 : x0;
            float z = Float.intBitsToFloat(original[v + 2]) > .5f ? z1 : z0;
            vertices[v] = Float.floatToRawIntBits(x);
            vertices[v + 2] = Float.floatToRawIntBits(z);
            for (int uv = 4; uv <= 5; uv++) {
                float a = Float.intBitsToFloat(original[origin + uv]);
                float b = Float.intBitsToFloat(original[east + uv]);
                float c = Float.intBitsToFloat(original[south + uv]);
                vertices[v + uv] = Float.floatToRawIntBits(a + (b - a) * x + (c - a) * z);
            }
        }
        return new BakedQuad(vertices, source.getTintIndex(), source.getDirection(), source.getSprite(), source.isShade(), source.hasAmbientOcclusion());
    }
}
