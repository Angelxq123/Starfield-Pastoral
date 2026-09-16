package com.stardew.craft.api.v1.festival;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.festival.FestivalRegistry;
import com.stardew.craft.network.payload.FestivalSessionsSyncPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.locale.Language;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FestivalDisplayNameTest {
    @Test void builtInNamesFollowAllShippedLanguagesIncludingLanguageSwitches() throws Exception {
        String oldDefinitions = FestivalRegistry.getCachedJson();
        Language oldLanguage = Language.getInstance();
        try {
            var definitions = new JsonObject();
            for (String id : List.of("spring13", "spring24", "summer11", "summer28", "fall16", "fall27",
                    "winter8", "winter25", "desert_festival", "night_market", "trout_derby", "squid_fest")) {
                try (var stream = getClass().getResourceAsStream("/data/stardewcraft/festivals/" + id + ".json")) {
                    assertNotNull(stream, id);
                    definitions.add("stardewcraft:" + id, JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
                }
            }
            FestivalRegistry.applyFromJson(definitions.toString());
            var names = new HashMap<String, Component>();
            for (var definition : FestivalRegistry.all()) {
                names.put("stardewcraft.festival.calendar." + definition.id().toLowerCase(Locale.ROOT),
                        session(definition.resourceId().toString(), definition.id()).displayName());
            }
            assertEquals(12, names.size());
            for (String language : List.of("en_us", "zh_cn", "de_de", "es_es", "fr_fr", "hu_hu", "it_it",
                    "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr")) {
                var translations = new HashMap<String, String>();
                try (var stream = getClass().getResourceAsStream("/assets/stardewcraft/lang/" + language + ".json")) {
                    assertNotNull(stream, language);
                    Language.loadFromJson(stream, translations::put);
                }
                Language.inject(new Language() {
                    public String getOrDefault(String key, String fallback) { return translations.getOrDefault(key, fallback); }
                    public boolean has(String key) { return translations.containsKey(key); }
                    public boolean isDefaultRightToLeft() { return false; }
                    public FormattedCharSequence getVisualOrder(FormattedText text) {
                        return FormattedCharSequence.forward(text.getString(), Style.EMPTY);
                    }
                });
                names.forEach((key, component) -> {
                    assertTrue(translations.containsKey(key), language + ": " + key);
                    assertEquals(translations.get(key), component.getString(), language + ": " + key);
                });
            }
        } finally {
            Language.inject(oldLanguage);
            FestivalRegistry.applyFromJson(oldDefinitions);
        }
    }

    @Test void addonNamesResolveAfterSyncAndReloadAndMissingDefinitionsHaveAnIdFallback() {
        String oldDefinitions = FestivalRegistry.getCachedJson();
        try {
            var session = session("example:apple_day", "AppleDay");
            FestivalRegistry.applyFromJson("{}");
            assertEquals("example:apple_day", session.displayName().getString());
            String definition = """
                    {"example:apple_day":{"type":"active","display_name":"Apple Day","season":0,
                    "start_day":1,"end_day":1,"start_time":900,"end_time":1400}}
                    """;
            FestivalRegistry.applyFromJson(definition);
            assertEquals("Apple Day", session.displayName().getString());
            FestivalRegistry.applyFromJson(definition.replace("Apple Day", "Harvest Picnic"));
            assertEquals("Harvest Picnic", session.displayName().getString());
            FestivalRegistry.applyFromJson("{}");
            assertEquals("example:apple_day", session.displayName().getString());
            assertEquals("stardewcraft:unknown", session("stardewcraft:unknown", "unknown").displayName().getString());
        } finally {
            FestivalRegistry.applyFromJson(oldDefinitions);
        }
    }

    @Test void existingConstructorAndNetworkRoundTripRemainCompatible() {
        var original = new FestivalSessionsSyncPayload(UUID.randomUUID(), 3,
                List.of(session("stardewcraft:spring13", "spring13")));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            FestivalSessionsSyncPayload.STREAM_CODEC.encode(buffer, original);
            assertEquals(original, FestivalSessionsSyncPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static StardewFestivalClientSessionSnapshot session(String id, String runtimeId) {
        return new StardewFestivalClientSessionSnapshot(ResourceLocation.parse(id), runtimeId, 1, 0, 13,
                StardewFestivalSessionSnapshot.Phase.OPEN, StardewFestivalSessionSnapshot.MapPhase.APPLIED, 1, true);
    }
}
