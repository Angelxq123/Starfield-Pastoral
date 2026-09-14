package com.stardew.craft.block.decor;


/** Two-block-wide, two-block-tall bookshelf with an integer placement footprint. */
public class BookshelfStaticDecorBlock extends IntegratedAabbDecorBlock {
    public BookshelfStaticDecorBlock(Properties properties, String modelId,
                                     double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ) {
        super(properties, modelId, minX, minY, minZ, maxX, maxY, maxZ);
    }

}
