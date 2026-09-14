package com.stardew.craft.templates;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.state.properties.StairsShape;

/** Original StardewCraft roof profiles; no reference-mod model data is used. */
public enum RoofTemplateForm {
    // Grid-aligned profiles: a complete slope rises exactly one block.
    SLOPE(0F, 1F, 1F, 0F, true),
    STEEP(0F, 1F, 0.5F, 0F, true),
    LOWER(0F, 0.5F, 1F, 0F, true),
    UPPER_LOW(0.5F, 0.5F, 1F, 0F, true),
    UPPER_STEEP(0F, 1F, 0.5F, 0.5F, true),
    RIDGE(0F, 0.5F, 1F, 0F, false),
    GAMBREL(0F, 1F, 1F, 0F, false);

    // Vertical thickness above the grid profile, including the eave end.
    // The roof never cuts into a block below; gable verges add two more pixels.
    public static final float SHELL_THICKNESS = 6F / 16F;
    public static final float TILE_RELIEF = 2F / 16F;
    public static final float EDGE_RELIEF = 4F / 16F;
    public static final float VERGE_WIDTH = 2F / 16F;
    public static final float VERGE_HEIGHT = 2F / 16F;

    private final float base;
    private final float rise;
    private final float run;
    private final float start;
    private final boolean connectsAsSlope;

    RoofTemplateForm(float base, float rise, float run, float start, boolean connectsAsSlope) {
        this.base = base;
        this.rise = rise;
        this.run = run;
        this.start = start;
        this.connectsAsSlope = connectsAsSlope;
    }

    public float base() {
        return base;
    }

    public float rise() {
        return rise;
    }

    public float run() {
        return run;
    }

    public boolean connectsAsSlope() {
        return connectsAsSlope;
    }

    public boolean isRidge() {
        return this == RIDGE;
    }

    public boolean supportsFlip() {
        return connectsAsSlope;
    }

    public boolean usesFacing() {
        return this != RIDGE;
    }

    public float start() {
        return start;
    }

    public float heightAtDistance(float distance) {
        return base + rise * clamp(1F - (distance - start) / run);
    }

    public float height(float x, float z, StairsShape shape) {
        if (this == RIDGE) {
            return base + rise * (1F - clamp(Math.abs(x - 0.5F) * 2F));
        }
        if (this == GAMBREL) {
            float distance = Math.abs(x - 0.5F);
            return distance <= 0.25F ? 1F - distance : 1.5F - distance * 3F;
        }

        float distance = switch (shape) {
            case STRAIGHT -> z;
            case INNER_LEFT -> Math.min(z, x);
            case INNER_RIGHT -> Math.min(z, 1F - x);
            case OUTER_LEFT -> Math.max(z, x);
            case OUTER_RIGHT -> Math.max(z, 1F - x);
        };
        return heightAtDistance(distance);
    }

    public List<TemplateBox> collisionBoxes(StairsShape shape) {
        return collisionBoxes(shape, 0);
    }

    public List<TemplateBox> collisionBoxes(StairsShape shape, int ridgeMask) {
        return collisionBoxes(shape, ridgeMask, true);
    }

    public List<TemplateBox> collisionBoxes(StairsShape shape, int ridgeMask, boolean filled) {
        return collisionBoxes(shape, ridgeMask, filled, 15);
    }

    public List<TemplateBox> collisionBoxes(StairsShape shape, int ridgeMask, boolean filled, int edges) {
        int cells = 16;
        ArrayList<TemplateBox> boxes = new ArrayList<>(cells * cells);
        for (int xCell = 0; xCell < cells; xCell++) {
            for (int zCell = 0; zCell < cells; zCell++) {
                float x0 = xCell / (float) cells;
                float x1 = (xCell + 1) / (float) cells;
                float z0 = zCell / (float) cells;
                float z1 = (zCell + 1) / (float) cells;
                float maximum = Math.max(Math.max(collisionHeight(x0, z0, shape, ridgeMask), collisionHeight(x1, z0, shape, ridgeMask)),
                        Math.max(collisionHeight(x1, z1, shape, ridgeMask), collisionHeight(x0, z1, shape, ridgeMask)));
                float minimum = Math.min(Math.min(collisionHeight(x0, z0, shape, ridgeMask), collisionHeight(x1, z0, shape, ridgeMask)),
                        Math.min(collisionHeight(x1, z1, shape, ridgeMask), collisionHeight(x0, z1, shape, ridgeMask)));
                if (maximum < 1E-6F) continue;
                float roundedHeight = maximum + SHELL_THICKNESS;
                if (!isRidge()) for(int side=0;side<4;side++) {
                    if((edges&(1<<side))==0)continue;
                    boolean near=switch(side) {case 0 -> z0<VERGE_WIDTH;case 1 -> x1>1-VERGE_WIDTH;case 2 -> z1>1-VERGE_WIDTH;default -> x0<VERGE_WIDTH;};
                    float a=collisionHeight(side==1?1:side==3?0:x0,side==2?1:side==0?0:z0,shape,ridgeMask);
                    float b=collisionHeight(side==1?1:side==3?0:x1,side==2?1:side==0?0:z1,shape,ridgeMask);
                    if(near && Math.abs(a-b)>1E-6)roundedHeight=Math.max(roundedHeight,maximum+SHELL_THICKNESS+VERGE_HEIGHT);
                }
                float bottom = filled ? 0F : minimum;
                if (roundedHeight > 0.001F) {
                    boxes.add(new TemplateBox(x0 * 16F, bottom * 16F, z0 * 16F,
                            x1 * 16F, roundedHeight * 16F, z1 * 16F));
                }
            }
        }
        return List.copyOf(boxes);
    }

    public float collisionHeight(float x, float z, StairsShape shape, int mask) {
        if (this != RIDGE) return height(x, z, shape);
        float dx = Math.abs(x - 0.5F), dz = Math.abs(z - 0.5F);
        float height = 0.5F - Math.max(dx, dz);
        if (((mask & 1) != 0 && z <= 0.5F) || ((mask & 4) != 0 && z >= 0.5F))
            height = Math.max(height, 0.5F - dx);
        if (((mask & 2) != 0 && x >= 0.5F) || ((mask & 8) != 0 && x <= 0.5F))
            height = Math.max(height, 0.5F - dz);
        return height;
    }

    private static float clamp(float value) {
        return Math.max(0F, Math.min(1F, value));
    }
}
