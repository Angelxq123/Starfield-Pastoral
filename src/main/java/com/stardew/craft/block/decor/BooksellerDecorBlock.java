package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.BooksellerDecorBlockEntity;
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

/** Two independently placed, year-round decorations; each extension owns one exact anchor. */
public final class BooksellerDecorBlock extends MapDecorStaticBlock implements EntityBlock {
    private static final VoxelShape STALL = stallShape();
    private static final VoxelShape BALLOON = balloonShape();
    private static final List<BlockPos> STALL_CELLS = cells(STALL);
    private static final List<BlockPos> BALLOON_CELLS = cells(BALLOON);
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0,
            Math.max(STALL_CELLS.size(), BALLOON_CELLS.size()) - 1);
    private final String assetId;
    private final boolean balloon;
    private final Map<Integer, VoxelShape> shapes = new ConcurrentHashMap<>();

    public BooksellerDecorBlock(Properties properties, boolean balloon) {
        super(properties, "stardewcraft:block/bookseller/" + (balloon ? "bookseller_balloon" : "bookseller_stall") + "/empty");
        this.balloon = balloon;
        this.assetId = balloon ? "bookseller_balloon" : "bookseller_stall";
        registerDefaultState(defaultBlockState().setValue(CELL, 0));
    }
    public String assetId() { return assetId; }
    private List<BlockPos> cells() { return balloon ? BALLOON_CELLS : STALL_CELLS; }
    @Override protected VoxelShape canonicalShape() { return balloon ? BALLOON : STALL; }

    private static VoxelShape stallShape() {
        return Shapes.or(
                Block.box(0, 0, -3, 64, 3, 48), // Platform; the vendor stands behind the counter.
                Block.box(0, 3, -2, 64, 17, 12), Block.box(0, 3, 44, 64, 49, 47),
                Block.box(4, 17, 34, 23, 38, 44), Block.box(41, 17, 34, 60, 38, 44),
                Block.box(0, 3, 0, 3, 36, 3), Block.box(61, 3, 0, 64, 36, 3),
                Block.box(-5, 43, -2, 8, 48, 48), Block.box(8, 47, -2, 24, 51, 48),
                Block.box(24, 49, -2, 40, 53, 48), Block.box(40, 47, -2, 56, 51, 48),
                Block.box(56, 43, -2, 69, 48, 48),
                Block.box(-13, 18, -1, 0, 35, 5), Block.box(64, 16, -3, 93, 36, 3),
                Block.box(-15, 1, -3, 0, 12, 9), Block.box(65, 1, -3, 94, 12, 9)).optimize();
    }
    private static VoxelShape balloonShape() {
        var boxes = new ArrayList<VoxelShape>();
        // Model centre is translated to (8,8); keep the basket hollow.
        boxes.add(Block.box(-4, 0, -4, 20, 7, 20));
        boxes.add(Block.box(-11, 7, -11, -6, 31, 27));
        boxes.add(Block.box(22, 7, -11, 27, 31, 27));
        boxes.add(Block.box(-6, 7, -11, 22, 31, -6));
        boxes.add(Block.box(-6, 7, 22, 22, 31, 27));
        // Four suspension ropes and burner assembly, with an open gap around them.
        for (int z : new int[] {-2, 16}) {
            boxes.add(Block.box(-10, 30, z, 1, 52, z + 2));
            boxes.add(Block.box(15, 30, z, 26, 52, z + 2));
        }
        boxes.add(Block.box(-4, 49, -4, 20, 55, 20));
        double[][] tiers = {{55,58,12},{58,66,19},{66,74,27},{74,84,34},{84,96,39},
                {96,120,42},{120,132,39},{132,142,35},{142,150,29},{150,156,22},
                {156,158,14},{158,160,7}};
        for (double[] tier : tiers) {
            double r = tier[2], q = r * .4142, mid = r * .70;
            // Three overlapping AABBs follow the octagonal envelope without filling its corners.
            boxes.add(Block.box(8-r, tier[0], 8-q, 8+r, tier[1], 8+q));
            boxes.add(Block.box(8-q, tier[0], 8-r, 8+q, tier[1], 8+r));
            boxes.add(Block.box(8-mid, tier[0], 8-mid, 8+mid, tier[1], 8+mid));
        }
        return Shapes.or(Shapes.empty(), boxes.toArray(VoxelShape[]::new)).optimize();
    }
    private static List<BlockPos> cells(VoxelShape shape) {
        var result = new LinkedHashSet<BlockPos>(); result.add(BlockPos.ZERO);
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            for (int y = (int)Math.floor(y0 + 1e-7); y < Math.ceil(y1 - 1e-7); y++)
                for (int z = (int)Math.floor(z0 + 1e-7); z < Math.ceil(z1 - 1e-7); z++)
                    for (int x = (int)Math.floor(x0 + 1e-7); x < Math.ceil(x1 - 1e-7); x++)
                        result.add(new BlockPos(x, y, z));
        });
        return List.copyOf(result);
    }
    private static BlockPos rotateOffset(BlockPos p, Direction facing) {
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
        for (BlockPos p : cells()) result.add(new CellOffset(p.getX(), p.getY(), p.getZ()));
        return result;
    }
    @Override protected BlockState extensionState(BlockState main, BlockPos offset) {
        Direction inverse = switch (main.getValue(FACING)) {
            case EAST -> Direction.WEST; case WEST -> Direction.EAST; default -> main.getValue(FACING);
        };
        return main.setValue(PART, Part.EXTENSION).setValue(CELL, cells().indexOf(rotateOffset(offset, inverse)));
    }
    @Override protected CellOffset findOffsetForExtension(BlockGetter level, BlockPos pos, BlockState state) {
        int cell = state.getValue(CELL);
        if (cell <= 0 || cell >= cells().size()) return null;
        BlockPos offset = rotateOffset(cells().get(cell), state.getValue(FACING));
        BlockState main = level.getBlockState(pos.subtract(offset));
        return main.is(this) && main.getValue(PART) == Part.MAIN && main.getValue(CELL) == 0
                && main.getValue(FACING) == state.getValue(FACING)
                ? new CellOffset(offset.getX(), offset.getY(), offset.getZ()) : null;
    }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int cell = state.getValue(CELL); Direction facing = state.getValue(FACING);
        if (cell >= cells().size()) return Shapes.empty();
        return shapes.computeIfAbsent(cell * 4 + facing.get2DDataValue(), ignored -> {
            BlockPos p = cells().get(cell);
            return rotateShapeForFacing(Shapes.join(canonicalShape().move(-p.getX(), -p.getY(), -p.getZ()),
                    Shapes.block(), BooleanOp.AND), facing);
        });
    }
    public AABB renderBounds(BlockState state, BlockPos pos) {
        VoxelShape bounds = balloon ? Block.box(-35, 0, -35, 51, 161, 51) : Block.box(-16, 0, -4, 95, 54, 49);
        return rotateShapeForFacing(bounds, state.getValue(FACING)).bounds().move(pos);
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
        return state.getValue(PART) == Part.MAIN ? new BooksellerDecorBlockEntity(pos, state) : null;
    }
}
