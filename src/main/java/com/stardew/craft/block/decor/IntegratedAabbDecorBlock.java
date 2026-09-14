package com.stardew.craft.block.decor;

/** Compatibility name; shape and multipart behaviour now live in MapDecorStaticBlock. */
public class IntegratedAabbDecorBlock extends MapDecorStaticBlock {
    public IntegratedAabbDecorBlock(Properties properties, String modelId,
                                    double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ) {
        super(properties, modelId, minX, minY, minZ, maxX, maxY, maxZ);
    }
}
