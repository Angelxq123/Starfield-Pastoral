package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.JojaBillboardBlockEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Four-block-wide billboard: a low plinth, two posts and a raised sign with an open gap below. */
public final class JojaBillboardBlock extends MapDecorStaticBlock implements EntityBlock {
    private static final List<BlockPos> CELLS = createCells();
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, CELLS.size() - 1);
    private static final VoxelShape COLLISION = Shapes.or(
            Block.box(0, 0, 0, 64, 2, 12), Block.box(0, 32, 2, 64, 80, 9),
            Block.box(6, 2, 5, 10, 32, 8), Block.box(54, 2, 5, 58, 32, 8),
            Block.box(5, 2, 4, 11, 3, 9), Block.box(53, 2, 4, 59, 3, 9));
    private final Map<Integer, VoxelShape> shapes = new ConcurrentHashMap<>();

    public JojaBillboardBlock(Properties properties) {
        super(properties, "stardewcraft:block/joja_billboard/spring/empty");
        registerDefaultState(defaultBlockState().setValue(CELL, 0));
    }
    private static List<BlockPos> createCells() {
        var cells = new ArrayList<BlockPos>();
        for (int y = 0; y < 5; y++) for (int x = 0; x < 4; x++) {
            // Leave the two middle cells between the posts available for passage.
            if (y != 1 || x == 0 || x == 3) cells.add(new BlockPos(x, y, 0));
        }
        return List.copyOf(cells);
    }
    public static int cellCount() { return CELLS.size(); }
    public static BlockPos localOffset(int cell) { return CELLS.get(cell); }
    public static BlockPos rotateOffset(BlockPos p, Direction facing) {
        return switch (facing) {
            case EAST -> new BlockPos(-p.getZ(), p.getY(), p.getX());
            case SOUTH -> new BlockPos(-p.getX(), p.getY(), -p.getZ());
            case WEST -> new BlockPos(p.getZ(), p.getY(), -p.getX());
            default -> p;
        };
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder); builder.add(CELL);
    }
    @Override protected Set<CellOffset> localOccupiedOffsets() {
        var result = new LinkedHashSet<CellOffset>();
        for (BlockPos p : CELLS) result.add(new CellOffset(p.getX(), p.getY(), p.getZ()));
        return result;
    }
    @Override protected BlockState extensionState(BlockState main, BlockPos offset) {
        Direction inverse = switch (main.getValue(FACING)) {
            case EAST -> Direction.WEST; case WEST -> Direction.EAST; default -> main.getValue(FACING);
        };
        return main.setValue(PART, Part.EXTENSION).setValue(CELL, CELLS.indexOf(rotateOffset(offset, inverse)));
    }
    @Override protected CellOffset findOffsetForExtension(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos offset = rotateOffset(localOffset(state.getValue(CELL)), state.getValue(FACING));
        if (offset.equals(BlockPos.ZERO)) return null;
        BlockState main = level.getBlockState(pos.subtract(offset));
        return main.is(this) && main.getValue(PART) == Part.MAIN && main.getValue(CELL) == 0
                && main.getValue(FACING) == state.getValue(FACING)
                ? new CellOffset(offset.getX(), offset.getY(), offset.getZ()) : null;
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int cell = state.getValue(CELL); Direction facing = state.getValue(FACING);
        return shapes.computeIfAbsent(cell * 4 + facing.get2DDataValue(), ignored -> {
            BlockPos p = localOffset(cell);
            return rotateShapeForFacing(Shapes.join(COLLISION.move(-p.getX(), -p.getY(), -p.getZ()),
                    Shapes.block(), BooleanOp.AND), facing);
        });
    }
    public AABB renderBounds(BlockState state, BlockPos pos) {
        return rotateShapeForFacing(Block.box(0, 0, 0, 64, 80, 12), state.getValue(FACING)).bounds().move(pos);
    }
    @Override protected boolean canPlaceAtFacing(Level level, BlockPos pos, Direction facing, BlockPlaceContext context) {
        if (!super.canPlaceAtFacing(level, pos, facing, context)) return false;
        BlockState main = defaultBlockState().setValue(FACING, facing);
        for (CellOffset cell : occupiedOffsets(facing)) {
            BlockPos offset = new BlockPos(cell.dx(), cell.dy(), cell.dz()), target = pos.offset(offset);
            if (!level.hasChunkAt(target)) return false;
            if (context.getPlayer() != null && !context.getPlayer().mayUseItemAt(target, context.getClickedFace(), context.getItemInHand())) return false;
            BlockState state = offset.equals(BlockPos.ZERO) ? main : extensionState(main, offset);
            if (!level.isUnobstructed(state, target, CollisionContext.empty())) return false;
            if (cell.dy() == 0 && !level.getBlockState(target.below()).isFaceSturdy(level, target.below(), Direction.UP)) return false;
        }
        return true;
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.ENTITYBLOCK_ANIMATED; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MAIN ? new JojaBillboardBlockEntity(pos, state) : null;
    }
}
