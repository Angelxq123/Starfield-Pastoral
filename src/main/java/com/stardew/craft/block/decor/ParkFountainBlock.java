package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.ParkFountainBlockEntity;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Native stone cells plus one client-animated water controller for the whole fountain. */
public final class ParkFountainBlock extends MapDecorStaticBlock implements EntityBlock {
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 99);
    public static final int CENTER = 12;
    private final Map<Integer, VoxelShape> shapes = new ConcurrentHashMap<>();

    public ParkFountainBlock(Properties properties) {
        super(properties, "stardewcraft:block/park_fountain/collision");
        registerDefaultState(defaultBlockState().setValue(CELL, CENTER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CELL);
    }

    public static BlockPos localOffset(int cell) {
        return new BlockPos(cell % 5 - 2, cell / 25, cell / 5 % 5 - 2);
    }

    public static BlockPos rotateOffset(BlockPos offset, Direction facing) {
        int x = offset.getX(), y = offset.getY(), z = offset.getZ();
        return switch (facing) {
            case EAST -> new BlockPos(-z, y, x);
            case SOUTH -> new BlockPos(-x, y, -z);
            case WEST -> new BlockPos(z, y, -x);
            default -> offset;
        };
    }

    @Override
    protected Set<CellOffset> localOccupiedOffsets() {
        Set<CellOffset> cells = new LinkedHashSet<>();
        cells.add(new CellOffset(0, 0, 0));
        for (int y = 0; y < 2; y++) for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            cells.add(new CellOffset(x, y, z));
        for (Direction direction : Direction.Plane.HORIZONTAL)
            cells.add(new CellOffset(direction.getStepX(), 2, direction.getStepZ()));
        cells.add(new CellOffset(0, 2, 0));
        cells.add(new CellOffset(0, 3, 0));
        return cells;
    }

    @Override
    protected BlockState extensionState(BlockState mainState, BlockPos offset) {
        Direction inverse = switch (mainState.getValue(FACING)) {
            case EAST -> Direction.WEST;
            case WEST -> Direction.EAST;
            default -> mainState.getValue(FACING);
        };
        BlockPos local = rotateOffset(offset, inverse);
        int cell = local.getX() + 2 + 5 * (local.getZ() + 2) + 25 * local.getY();
        return mainState.setValue(PART, Part.EXTENSION).setValue(CELL, cell);
    }

    @Override
    protected CellOffset findOffsetForExtension(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos offset = rotateOffset(localOffset(state.getValue(CELL)), state.getValue(FACING));
        if (offset.equals(BlockPos.ZERO)) return null;
        BlockState main = level.getBlockState(pos.subtract(offset));
        return main.is(this) && main.getValue(PART) == Part.MAIN && main.getValue(CELL) == CENTER
                && main.getValue(FACING) == state.getValue(FACING)
                ? new CellOffset(offset.getX(), offset.getY(), offset.getZ()) : null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int cell = state.getValue(CELL);
        Direction facing = state.getValue(FACING);
        return shapes.computeIfAbsent(cell * 4 + facing.get2DDataValue(), unused -> {
            BlockPos offset = localOffset(cell);
            // Clip first: rotating the entire 5x5 structure for every cell is needlessly expensive.
            VoxelShape local = Shapes.join(canonicalShape()
                    .move(-offset.getX(), -offset.getY(), -offset.getZ()), Shapes.block(), BooleanOp.AND);
            return rotateShapeForFacing(local, facing);
        });
    }

    @Override
    protected boolean canPlaceAtFacing(Level level, BlockPos pos, Direction facing, BlockPlaceContext context) {
        if (!super.canPlaceAtFacing(level, pos, facing, context)) return false;
        BlockState main = defaultBlockState().setValue(FACING, facing);
        for (CellOffset cell : occupiedOffsets(facing)) {
            BlockPos offset = new BlockPos(cell.dx(), cell.dy(), cell.dz());
            BlockPos target = pos.offset(offset);
            if (!level.hasChunkAt(target)) return false;
            if (context.getPlayer() != null && !context.getPlayer().mayUseItemAt(target,
                    context.getClickedFace(), context.getItemInHand())) return false;
            BlockState state = offset.equals(BlockPos.ZERO) ? main : extensionState(main, offset);
            if (!level.isUnobstructed(state, target, CollisionContext.empty())) return false;
        }
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new ParkFountainBlockEntity(pos, state) : null;
    }
}
