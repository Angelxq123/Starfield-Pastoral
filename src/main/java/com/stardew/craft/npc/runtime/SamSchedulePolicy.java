package com.stardew.craft.npc.runtime;

/** Original unmarried schedule precedence. Rain is selected once per world day. */
public final class SamSchedulePolicy {
    private SamSchedulePolicy() {}

    public static String select(String season, int day, int year, String weather,
                                int samHearts, int pennyHearts, long seed, int absoluteDay) {
        if (year == 1 && weather.contains("greenrain")) return "GreenRain";
        if (season.equals("fall") && day == 11) return "fall_11";
        if (day == 9 || day == 23) return samHearts >= 6 || pennyHearts >= 6 ? "spring" : "9";
        if (weather.contains("rain") || weather.contains("storm")) {
            return new java.util.Random(seed ^ (absoluteDay * 0x9E3779B97F4A7C15L)).nextBoolean() ? "rain2" : "rain";
        }
        return switch ((day - 1) % 7) {
            case 0, 2 -> "Mon";
            case 4 -> "Fri";
            case 5 -> "Sat";
            default -> season;
        };
    }
}
