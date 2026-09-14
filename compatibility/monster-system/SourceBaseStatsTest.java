package com.stardew.craft.monster;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.*;
/** Tracked snapshot of original Monster.parseMonsterInfo fields; no dependency on local source files. */
class SourceBaseStatsTest {
    private JsonObject read(String path){return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(path),path))).getAsJsonObject();}
    @Test void nativeDefinitionsUseMissChanceNotJitter(){
        var source=read("/source-base-stats.json");
        for(var e:source.entrySet()){
            var expected=e.getValue().getAsJsonObject();var actual=read("/data/stardewcraft/monsters/"+e.getKey()+".json");
            assertEquals(expected.get("source_name").getAsString(),actual.get("source_name").getAsString());
            for(String field:new String[]{"health","damage","resilience","miss_chance","experience"})assertEquals(expected.get(field).getAsDouble(),actual.get(field).getAsDouble(),e.getKey()+"/"+field);
        }
    }
}
