package com.stardew.craft.client.model.terrain;

import com.stardew.craft.block.terrain.TerrainFaceConnections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.phys.Vec3;

/** Replaces native face rectangles with neighboring native UVs; no overlapping render layer. */
final class TerrainFaceQuads {
    record Paint(TerrainFaceConnections.Connection connection, BakedQuad source) {}
    private record Key(BakedQuad face, List<Paint> paints) {}
    private final Map<Key, List<BakedQuad>> cache = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, List<BakedQuad>> entry) { return size() > 2048; }
    };

    synchronized List<BakedQuad> get(BakedQuad face, List<Paint> paints) {
        return cache.computeIfAbsent(new Key(face, List.copyOf(paints)), this::build);
    }

    private List<BakedQuad> build(Key key) {
        BakedQuad target = key.face();
        List<Paint> paints = key.paints();
        Bounds bounds = new Bounds(target);
        List<Bounds> sourceBounds = paints.stream().map(paint -> new Bounds(paint.source())).toList();
        int[] grid = new int[256];
        boolean[] used = new boolean[256];
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            double px = (x + .5) / 16, py = (y + .5) / 16;
            if (!bounds.contains(px, py)) { used[y*16+x] = true; continue; }
            for (int i = 0; i < paints.size(); i++) {
                Paint paint = paints.get(i);
                if (!paint.connection().covers(x, y)) continue;
                Vec3 uv = paint.connection().sample(target.getDirection(), px, py);
                if (sourceBounds.get(i).contains(uv.x, uv.y)) grid[y*16+x] = i+1;
            }
        }
        List<BakedQuad> result = new ArrayList<>();
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            int index = y*16+x;
            if (used[index]) continue;
            int type = grid[index], width = 1, height = 1;
            while (x+width < 16 && !used[index+width] && grid[index+width] == type) width++;
            outer: while (y+height < 16) {
                for (int dx = 0; dx < width; dx++)
                    if (used[(y+height)*16+x+dx] || grid[(y+height)*16+x+dx] != type) break outer;
                height++;
            }
            for (int dy = 0; dy < height; dy++) for (int dx = 0; dx < width; dx++) used[(y+dy)*16+x+dx] = true;
            Paint paint = type == 0 ? null : paints.get(type-1);
            result.add(crop(target, paint, x/16., y/16., (x+width)/16., (y+height)/16.));
        }
        return List.copyOf(result);
    }

    private static BakedQuad crop(BakedQuad target, Paint paint, double x0, double y0, double x1, double y1) {
        int[] original = target.getVertices(), vertices = original.clone();
        int stride = original.length/4;
        var frame = TerrainFaceConnections.frame(target.getDirection());
        Bounds targetBounds = new Bounds(target);
        BakedQuad source = paint == null ? target : paint.source();
        Bounds sourceBounds = new Bounds(source);
        for (int i = 0; i < 4; i++) {
            int at = i*stride;
            Vec3 old = point(original, at);
            double x = frame.x(old) > (targetBounds.x0+targetBounds.x1)/2 ? x1 : x0;
            double y = frame.y(old) > (targetBounds.y0+targetBounds.y1)/2 ? y1 : y0;
            Vec3 position = old.add(Vec3.atLowerCornerOf(frame.u().getNormal()).scale(x-frame.x(old)))
                    .add(Vec3.atLowerCornerOf(frame.v().getNormal()).scale(y-frame.y(old)));
            vertices[at] = Float.floatToRawIntBits((float)position.x);
            vertices[at+1] = Float.floatToRawIntBits((float)position.y);
            vertices[at+2] = Float.floatToRawIntBits((float)position.z);
            Vec3 uv = paint == null ? new Vec3(x,y,0) : paint.connection().sample(target.getDirection(),x,y);
            for (int component = 4; component <= 5; component++)
                vertices[at+component] = Float.floatToRawIntBits(sourceBounds.uv(source,component,uv.x,uv.y));
        }
        return new BakedQuad(vertices, source.getTintIndex(), target.getDirection(), source.getSprite(), target.isShade(), target.hasAmbientOcclusion());
    }

    private static Vec3 point(int[] vertices, int at) {
        return new Vec3(Float.intBitsToFloat(vertices[at]), Float.intBitsToFloat(vertices[at+1]), Float.intBitsToFloat(vertices[at+2]));
    }

    private static final class Bounds {
        double x0 = Double.POSITIVE_INFINITY, y0 = Double.POSITIVE_INFINITY, x1 = Double.NEGATIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
        int origin, east, south;
        Bounds(BakedQuad source) {
            int[] vertices = source.getVertices();
            var frame = TerrainFaceConnections.frame(source.getDirection());
            for (int at = 0; at < vertices.length; at += vertices.length/4) {
                Vec3 p = point(vertices,at);
                x0 = Math.min(x0,frame.x(p)); x1 = Math.max(x1,frame.x(p));
                y0 = Math.min(y0,frame.y(p)); y1 = Math.max(y1,frame.y(p));
            }
            for (int at = 0; at < vertices.length; at += vertices.length/4) {
                Vec3 p = point(vertices,at);
                boolean x = frame.x(p) > (x0+x1)/2, y = frame.y(p) > (y0+y1)/2;
                if (!x && !y) origin = at;
                if (x && !y) east = at;
                if (!x && y) south = at;
            }
        }
        boolean contains(double x, double y) { return x >= x0 && x <= x1 && y >= y0 && y <= y1; }
        float uv(BakedQuad source, int component, double x, double y) {
            int[] vertices = source.getVertices();
            float a = Float.intBitsToFloat(vertices[origin+component]);
            float b = Float.intBitsToFloat(vertices[east+component]);
            float c = Float.intBitsToFloat(vertices[south+component]);
            return (float)(a+(b-a)*(x-x0)/(x1-x0)+(c-a)*(y-y0)/(y1-y0));
        }
    }
}
