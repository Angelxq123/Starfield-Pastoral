package com.stardew.craft.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataSyncPacketTest {
    @Test
    void clientViewKeepsRenderedStateAndDropsServerOnlyPersistence() {
        CompoundTag source = new CompoundTag();
        source.putInt("Health", 80);
        source.putInt("Money", 1234);
        ListTag flags = new ListTag();
        flags.add(StringTag.valueOf("ccPantry"));
        source.put("MailFlags", flags);
        source.put("QuestManager", new CompoundTag());
        source.put("PendingDailySettlement", new CompoundTag());
        source.putInt("HandledPregenRelocationVersion", 10);

        CompoundTag client = PlayerDataSyncPacket.clientView(source);

        assertEquals(80, client.getInt("Health"));
        assertEquals(1234, client.getInt("Money"));
        assertTrue(client.contains("MailFlags"));
        assertFalse(client.contains("QuestManager"));
        assertFalse(client.contains("PendingDailySettlement"));
        assertFalse(client.contains("HandledPregenRelocationVersion"));
    }

    @Test
    void clientViewOwnsNestedTagsInsteadOfSharingMutablePersistenceState() {
        CompoundTag source = new CompoundTag();
        ListTag recipes = new ListTag();
        recipes.add(StringTag.valueOf("fried_egg"));
        source.put("UnlockedRecipes", recipes);

        CompoundTag client = PlayerDataSyncPacket.clientView(source);
        recipes.add(StringTag.valueOf("pizza"));

        assertEquals(1, client.getList("UnlockedRecipes", StringTag.TAG_STRING).size());
    }

    @Test
    void everyTopLevelFieldReadByTheClientIsAllowedOnTheWire() throws Exception {
        Path project = Path.of(System.getProperty("stardewcraft.projectDir", "."));
        String source = Files.readString(project.resolve(
                "src/main/java/com/stardew/craft/client/ClientPlayerDataCache.java"));
        String update = source.substring(
                source.indexOf("public static void updateFromNBT"),
                source.indexOf("// Getters"));
        var matcher = Pattern.compile("nbt\\.[a-zA-Z]+\\(\\\"([^\\\"]+)\\\"").matcher(update);
        Set<String> clientReads = new HashSet<>();
        while (matcher.find()) {
            clientReads.add(matcher.group(1));
        }

        Field field = PlayerDataSyncPacket.class.getDeclaredField("CLIENT_FIELDS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Set<String> allowed = (Set<String>) field.get(null);

        assertTrue(allowed.containsAll(clientReads), () -> "Missing client fields: "
                + difference(clientReads, allowed));
    }

    private static Set<String> difference(Set<String> required, Set<String> allowed) {
        Set<String> missing = new HashSet<>(required);
        missing.removeAll(allowed);
        return missing;
    }
}
