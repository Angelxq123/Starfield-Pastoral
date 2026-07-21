package com.stardew.craft.api.v1;

import com.stardew.craft.network.DataRegistrySyncPayload;
import com.stardew.craft.item.artisan.PreservesIngredientDataManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.VarInt;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRegistrySyncPayloadTest {
    private static final int MAX_DOCUMENT_BYTES = 4 * 1024 * 1024;
    private static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;

    @AfterEach
    void clearPreservesFixture() {
        PreservesIngredientDataManager.applyFromJson("{}");
    }

    @Test
    void allRegistryDocumentsRoundTripInProtocolOrder() {
        DataRegistrySyncPayload expected = new DataRegistrySyncPayload(
                "artisan", "cooking", "crafting", "preserves",
                "fishing", "npc-events", "unlock-sources", "festivals",
                "mastery-rewards", "locations", "professions", "secret-notes");
        ByteBuf buffer = Unpooled.buffer();
        try {
            DataRegistrySyncPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, DataRegistrySyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void estimatesEncodedRegistryDocumentBytes() {
        DataRegistrySyncPayload payload = new DataRegistrySyncPayload(
                "{}", "{}", "{}", "{}", "{}", "{}",
                "{}", "{}", "{}", "{}", "{}", "{}");

        assertEquals(36, payload.estimatedEncodedBytes());
    }

    @Test
    void estimatedBytesMatchCodecForUnicodeAndMalformedSurrogates() {
        assertEstimatedBytesMatchCodec("\u00E9\u4E2D");
        assertEstimatedBytesMatchCodec("\uD83D\uDE00");
        assertEstimatedBytesMatchCodec("\uD800");
        assertEstimatedBytesMatchCodec("\uDC00");
        assertEstimatedBytesMatchCodec("\uD800x\uDC00");
    }

    @Test
    void estimatedBytesMatchCodecAtVarIntBoundaries() {
        for (int length : new int[] {127, 128, 16_383, 16_384}) {
            assertEstimatedBytesMatchCodec("a".repeat(length));
        }
    }

    @Test
    void encodedSizeEstimationDoesNotMaterializeUtf8ByteArrays() throws IOException {
        Path sourcePath = Path.of(System.getProperty("stardewcraft.projectDir", "."))
                .resolve("src/main/java/com/stardew/craft/network/DataRegistrySyncPayload.java");
        String source = Files.readString(sourcePath);
        int methodStart = source.indexOf("private static int encodedStringBytes");
        int methodEnd = source.indexOf("private static int varIntBytes", methodStart);

        assertTrue(methodStart >= 0 && methodEnd > methodStart, "encodedStringBytes helper is missing");
        assertFalse(source.substring(methodStart, methodEnd).contains("getBytes("),
                "encodedStringBytes must not allocate a UTF-8 byte array");
    }

    @Test
    void rejectsAnOversizedDocumentBeforeAllocatingItsBody() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            VarInt.write(buffer, 4 * 1024 * 1024 + 1);
            assertThrows(DecoderException.class, () -> DataRegistrySyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsATruncatedDocument() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            VarInt.write(buffer, 8);
            buffer.writeByte(1);
            assertThrows(DecoderException.class, () -> DataRegistrySyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void blankDocumentsBecomeExplicitEmptySnapshots() {
        DataRegistrySyncPayload snapshot = new DataRegistrySyncPayload(
                "", "  ", null, "", "", "", "", "", "", "", "", "");

        assertEquals("{}", snapshot.artisanJson());
        assertEquals("{}", snapshot.cookingJson());
        assertEquals("{}", snapshot.craftingJson());
        assertEquals("{}", snapshot.preservesJson());
        assertEquals("{}", snapshot.fishingJson());
        assertEquals("{}", snapshot.npcEventsJson());
        assertEquals("{}", snapshot.unlockSourcesJson());
        assertEquals("{}", snapshot.festivalsJson());
        assertEquals("{}", snapshot.masteryRewardsJson());
        assertEquals("{}", snapshot.locationsJson());
        assertEquals("{}", snapshot.professionsJson());
        assertEquals("{}", snapshot.secretNotesJson());
    }

    @Test
    void emptyReplacementSnapshotDeletesEntriesFromThePreviousSnapshot() {
        ResourceLocation apple = ResourceLocation.withDefaultNamespace("apple");
        PreservesIngredientDataManager.applyFromJson(
                "{\"apple\":{\"price\":100,\"edibility\":20,\"color\":\"#FF0000\"}}");
        assertTrue(PreservesIngredientDataManager.hasData(apple));

        DataRegistrySyncPayload replacement = new DataRegistrySyncPayload(
                "", "", "", "", "", "", "", "", "", "", "", "");
        PreservesIngredientDataManager.applyFromJson(replacement.preservesJson());

        assertFalse(PreservesIngredientDataManager.hasData(apple));
    }

    @Test
    void acceptsADocumentAtTheSizeBoundary() {
        String boundaryDocument = "a".repeat(MAX_DOCUMENT_BYTES);
        DataRegistrySyncPayload expected = payloadWithArtisanDocuments(
                boundaryDocument, "{}", "{}", "{}");
        ByteBuf buffer = Unpooled.buffer();
        try {
            DataRegistrySyncPayload.STREAM_CODEC.encode(buffer, expected);
            assertEquals(expected, DataRegistrySyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void encoderRejectsADocumentAboveTheSizeBoundary() {
        DataRegistrySyncPayload payload = payloadWithArtisanDocuments(
                "a".repeat(MAX_DOCUMENT_BYTES + 1), "{}", "{}", "{}");
        ByteBuf buffer = Unpooled.buffer();
        try {
            assertThrows(EncoderException.class, () -> DataRegistrySyncPayload.STREAM_CODEC.encode(buffer, payload));
        } finally {
            buffer.release();
        }
    }

    @Test
    void encoderRejectsAnAggregatePayloadAboveTheSizeBoundary() {
        String maximumDocument = "a".repeat(MAX_DOCUMENT_BYTES);
        DataRegistrySyncPayload payload = payloadWithArtisanDocuments(
                maximumDocument, maximumDocument, maximumDocument, maximumDocument);
        ByteBuf buffer = Unpooled.buffer();
        try {
            assertThrows(EncoderException.class, () -> DataRegistrySyncPayload.STREAM_CODEC.encode(buffer, payload));
        } finally {
            buffer.release();
        }
    }

    @Test
    void decoderRejectsAnAggregatePayloadAboveTheSizeBoundary() {
        ByteBuf buffer = Unpooled.buffer(MAX_PAYLOAD_BYTES + 1);
        try {
            buffer.writeZero(MAX_PAYLOAD_BYTES + 1);
            assertThrows(DecoderException.class, () -> DataRegistrySyncPayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static DataRegistrySyncPayload payloadWithArtisanDocuments(
            String artisan, String cooking, String crafting, String preserves) {
        return new DataRegistrySyncPayload(
                artisan, cooking, crafting, preserves,
                "{}", "{}", "{}", "{}", "{}", "{}", "{}", "{}");
    }

    private static void assertEstimatedBytesMatchCodec(String document) {
        DataRegistrySyncPayload payload = payloadWithArtisanDocuments(document, "{}", "{}", "{}");
        ByteBuf buffer = Unpooled.buffer();
        try {
            DataRegistrySyncPayload.STREAM_CODEC.encode(buffer, payload);
            assertEquals(buffer.readableBytes(), payload.estimatedEncodedBytes());
        } finally {
            buffer.release();
        }
    }
}
