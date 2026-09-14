package com.stardew.craft.block.shape;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.LockSupport;

/** Failed/partial reads are not missing models and must never become cached empty geometry. */
public final class ModelResourceReader {
    private ModelResourceReader() {}
    @FunctionalInterface
    public interface Source { InputStream open(String path) throws IOException; }

    public static JsonObject read(String path, Source source) {
        Exception failure = null;
        // Development resource copies can briefly expose an absent or incomplete file.
        // Retry the real asset, never synthesize substitute geometry. Broken packaged assets fail clearly.
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) LockSupport.parkNanos(1_000_000L);
            try (InputStream stream = source.open(path)) {
                if (stream == null) continue;
                return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException | JsonParseException | IllegalStateException exception) {
                failure = exception;
            }
        }
        if (failure != null) throw new IllegalStateException("Cannot read model JSON: " + path, failure);
        // Optional parent/fallback lookups still use null for genuinely absent resources.
        return null;
    }
}
