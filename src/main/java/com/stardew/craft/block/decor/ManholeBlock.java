package com.stardew.craft.block.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Nine owned cells, one thin cast-iron cover and one item drop. */
public final class ManholeBlock extends MapDecorStaticBlock {
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 8);
    public static final int CELL_COUNT = 9;
    private static final VoxelShape CELL_SHAPE = Block.box(0, 0, 0, 16, 2, 16);

    public ManholeBlock(Properties properties) {
        super(properties, "stardewcraft:block/decor/manhole");
        registerDefaultState(defaultBlockState().setValue(CELL, 0));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CELL);
    }

    public static BlockPos offset(int cell, Direction facing) {
        // Keep the original four cell offsets; extend around the central anchor.
        int x, z;
        switch (cell) {
            case 0 -> { x=0; z=0; }
            case 1 -> { x=1; z=0; }
            case 2 -> { x=0; z=1; }
            case 3 -> { x=1; z=1; }
            case 4 -> { x=-1; z=-1; }
            case 5 -> { x=0; z=-1; }
            case 6 -> { x=1; z=-1; }
            case 7 -> { x=-1; z=0; }
            case 8 -> { x=-1; z=1; }
            default -> throw new IllegalArgumentException("Invalid manhole cell: "+cell);
        }
        return switch(facing) {
            case EAST -> new BlockPos(-z,0,x);
            case SOUTH -> new BlockPos(-x,0,-z);
            case WEST -> new BlockPos(z,0,-x);
            default -> new BlockPos(x,0,z);
        };
    }

    @Override protected java.util.Set<CellOffset> occupiedOffsets(Direction facing) {
        var offsets = new java.util.LinkedHashSet<CellOffset>();
        // Cleanup must remove the central owner before its surrounding cells.
        // Bounding-box iteration starts at (-1,-1), which would refund repeatedly.
        for (int cell=0;cell<CELL_COUNT;cell++) {
            var offset=offset(cell,facing);
            offsets.add(new CellOffset(offset.getX(),0,offset.getZ()));
        }
        return offsets;
    }

    @Override protected BlockState extensionState(BlockState main, BlockPos offset) {
        for(int cell=1;cell<CELL_COUNT;cell++) if(offset(cell,main.getValue(FACING)).equals(offset))
            return main.setValue(PART,Part.EXTENSION).setValue(CELL,cell);
        throw new IllegalArgumentException("Outside manhole footprint: "+offset);
    }

    @Override protected CellOffset findOffsetForExtension(BlockGetter level,BlockPos pos,BlockState state) {
        if(state.getValue(CELL)==0)return null;
        BlockPos offset=offset(state.getValue(CELL),state.getValue(FACING));
        BlockState main=level.getBlockState(pos.subtract(offset));
        return main.is(this)&&main.getValue(PART)==Part.MAIN&&main.getValue(CELL)==0
                &&main.getValue(FACING)==state.getValue(FACING)
                ?new CellOffset(offset.getX(),0,offset.getZ()):null;
    }

    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) {
        return CELL_SHAPE;
    }

    @Override protected boolean canPlaceAtFacing(Level level,BlockPos pos,Direction facing,BlockPlaceContext context) {
        if(!super.canPlaceAtFacing(level,pos,facing,context))return false;
        for(int cell=0;cell<CELL_COUNT;cell++) {
            BlockPos at=pos.offset(offset(cell,facing));
            if(!level.hasChunkAt(at))return false;
            if(context.getPlayer()!=null&&!context.getPlayer().mayUseItemAt(at,context.getClickedFace(),context.getItemInHand()))return false;
            if(!level.isUnobstructed(defaultBlockState(),at,CollisionContext.empty()))return false;
        }
        return true;
    }
}
