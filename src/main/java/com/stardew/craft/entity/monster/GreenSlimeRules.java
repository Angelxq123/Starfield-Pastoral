package com.stardew.craft.entity.monster;

/** GreenSlime.cs / Monster.cs, ordinary Green Slime only. See docs/monster-model-handoff/绿色史莱姆游戏接入.md. */
public final class GreenSlimeRules {
    private GreenSlimeRules() {}
    public static final int HEALTH = 24, DAMAGE = 5, RESILIENCE = 1;
    public static final int CHARGE_TICKS = 16;
    public static final double PIXELS_PER_BLOCK = 64.0;
    public static final int SOURCE_STEPS_PER_TICK = 3;
    public static final double WALK_PIXELS = 2;
    // The actual gel hull is 13.6 x 9.6 x 11.6 model units. Contact follows its
    // grounded footprint, not the swept volume of hops, antennae and death rolls.
    public static final float COLLISION_WIDTH = .93F;
    public static final float COLLISION_BODY_HEIGHT = .65F;
    public static final float COLLISION_TOP_MARGIN = 1F / 32;
    public static net.minecraft.world.phys.AABB collisionBox(double x,double y,double z,float yaw,float growth,int antenna) {
        double angle=Math.toRadians(yaw),c=Math.abs(Math.cos(angle)),s=Math.abs(Math.sin(angle));
        double halfX=((6.8*c+5.8*s)/16+.04)*growth;
        double halfZ=((5.8*c+6.8*s)/16+.04)*growth;
        return new net.minecraft.world.phys.AABB(x-halfX,y,z-halfZ,x+halfX,
                y+collisionHeight(antenna)*growth+COLLISION_TOP_MARGIN,z+halfZ);
    }

    /** GreenSlime.draw: only adults; special-item dongle takes precedence over the male tip. */
    public static int antenna(boolean male, boolean marked, boolean adult) {
        return !adult ? 0 : marked ? 2 : male ? 1 : 0;
    }
    public static float collisionWidth(int antenna) { return COLLISION_WIDTH; }
    public static float collisionHeight(int antenna) { return antenna == 0 ? COLLISION_BODY_HEIGHT : antenna == 1 ? .99F : 1.055F; }

    public static int health(int base, boolean marked, boolean male) {
        int value = base * (marked ? 3 : 1);
        return male ? value + value / 4 : value;
    }
    public static int damage(int base, boolean marked, boolean male) {
        return base * (marked ? 2 : 1) + (male ? 1 : 0);
    }
    public static boolean canCharge(int elapsedTicks, boolean focused) {
        return elapsedTicks > (focused ? 20 : 80);
    }
    public static boolean canBeMarked(int floor) {
        return floor % 5 != 0 && floor % 5 != 1;
    }
    public static int slimedTicks(int milliseconds) {
        // Buff("13") chooses an inclusive 2500..3000 ms duration. Round up to MC ticks.
        return (milliseconds + 49) / 50;
    }
    public static double decay(double velocity, int slipperiness, boolean blocked) {
        double result = velocity * (1.0 - 1.0 / slipperiness / (blocked && slipperiness >= 8 ? 4 : 1));
        return Math.abs(result) <= .05 ? 0 : result;
    }
}
