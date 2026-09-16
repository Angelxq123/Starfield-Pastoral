package com.stardew.craft.entity.monster;

import net.minecraft.util.RandomSource;

/** GreenSlime(Vector2, mineLevel): color rolls precede marked/cute stat modifiers. */
public enum SlimeVariant {
    GREEN("green_slime", 0x8FB200), FROST("frost_jelly", 0), SLUDGE("sludge", 0x230723);
    private final String id;
    private final int markedColor;
    SlimeVariant(String id, int markedColor) { this.id = id; this.markedColor = markedColor; }
    public String id() { return id; }
    public net.minecraft.world.entity.EntityType<GreenSlimeEntity> type() {
        return switch (this) {
            case GREEN -> com.stardew.craft.entity.ModEntities.GREEN_SLIME.get();
            case FROST -> com.stardew.craft.entity.ModEntities.FROST_JELLY.get();
            case SLUDGE -> com.stardew.craft.entity.ModEntities.SLUDGE.get();
        };
    }
    public static SlimeVariant offspringStats(int color) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        if (r > 100 && b > 100 && g < 50 || r >= 200 && g < 75) return SLUDGE;
        return b >= 200 && r < 100 ? FROST : GREEN;
    }
    public int markedColor() { return markedColor; }
    public boolean skull(int floor) { return this == SLUDGE && floor > 120; }
    public int rollColor(RandomSource random, int floor) {
        if (skull(floor)) return rgb(138 + random.nextInt(41) - 20,
                43 + random.nextInt(41) - 20, 226 + random.nextInt(41) - 20);
        int primary = 200 + random.nextInt(56);
        return switch (this) {
            case GREEN -> rgb(primary / (2 + random.nextInt(8)), primary, random.nextDouble() < .01 ? 255 : 255 - primary);
            case FROST -> rgb(random.nextDouble() < .01 ? 180 : primary / (2 + random.nextInt(8)),
                    random.nextDouble() < .1 ? 255 : 255 - primary / 3, primary);
            case SLUDGE -> rgb(primary, random.nextDouble() < .01 ? 255 : 255 - primary, primary / (2 + random.nextInt(8)));
        };
    }
    public boolean rushInsteadOfJump(double roll) { return this == FROST && roll < .25; }
    private static int rgb(int r, int g, int b) { return r << 16 | g << 8 | b; }
}
