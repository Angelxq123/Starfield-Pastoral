package com.stardew.craft.block.terrain;

import com.stardew.craft.block.ModBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Neighbor topology in each native cube face's UV frame, including inside wall/floor corners. */
public final class TerrainFaceConnections {
    private static final int[] DEPTH = {2,3,3,2,1,2,3,2,2,1,2,3,3,2,1,2};

    private TerrainFaceConnections() {}

    public record Frame(Direction u, Direction v, Vec3 origin) {
        public Vec3 point(double x, double y) {
            return origin.add(Vec3.atLowerCornerOf(u.getNormal()).scale(x))
                    .add(Vec3.atLowerCornerOf(v.getNormal()).scale(y));
        }
        public double x(Vec3 point) { return point.subtract(origin).dot(Vec3.atLowerCornerOf(u.getNormal())); }
        public double y(Vec3 point) { return point.subtract(origin).dot(Vec3.atLowerCornerOf(v.getNormal())); }
    }

    public static Frame frame(Direction face) {
        return switch (face) {
            case UP -> new Frame(Direction.EAST, Direction.SOUTH, new Vec3(0,1,0));
            case DOWN -> new Frame(Direction.EAST, Direction.NORTH, new Vec3(0,0,1));
            case NORTH -> new Frame(Direction.WEST, Direction.DOWN, new Vec3(1,1,0));
            case SOUTH -> new Frame(Direction.EAST, Direction.DOWN, new Vec3(0,1,1));
            case WEST -> new Frame(Direction.SOUTH, Direction.DOWN, new Vec3(0,1,0));
            case EAST -> new Frame(Direction.NORTH, Direction.DOWN, new Vec3(1,1,1));
        };
    }

    public static Direction tangent(Direction face, int edge) {
        Frame frame = frame(face);
        return switch (edge) {
            case 0 -> frame.v().getOpposite();
            case 1 -> frame.u();
            case 2 -> frame.v();
            case 3 -> frame.u().getOpposite();
            default -> throw new IllegalArgumentException("Edge must be 0..3");
        };
    }

    public static int rank(BlockState state) {
        if (state.is(ModBlocks.DARK_GRASS_BLOCK.get())) return 5;
        if (state.is(ModBlocks.GRASS_BLOCK.get())) return 4;
        if (state.is(ModBlocks.FARMLAND.get())) return state.getValue(FarmBlock.MOISTURE) > 0 ? 3 : 2;
        if (state.is(ModBlocks.DIRT.get())) return 1;
        return state.is(ModBlocks.CLIFF.get()) ? 0 : -1;
    }

    public record Connection(BlockState state, Direction face, BlockPos offset, int edge, boolean folded, boolean corner) {
        public boolean covers(int x, int y) {
            if (corner) {
                int dx = edge < 2 ? 15 - x : x;
                int dy = edge == 1 || edge == 2 ? 15 - y : y;
                return dx + dy < 3;
            }
            int depth = switch (edge) { case 0 -> y; case 1 -> 15-x; case 2 -> 15-y; default -> x; };
            return depth < DEPTH[edge % 2 == 0 ? x : y];
        }

        /** Map target pixel corners into the donor's native face, without stretching a texel. */
        public Vec3 sample(Direction target, double x, double y) {
            Vec3 point = frame(target).point(x, y);
            if (corner) {
                double dx = edge < 2 ? 1-x : -x;
                double dy = edge == 1 || edge == 2 ? 1-y : -y;
                point = frame(target).point(x + 2*dx, y + 2*dy);
            } else {
                double depth = switch (edge) { case 0 -> y; case 1 -> 1-x; case 2 -> 1-y; default -> x; };
                point = point.add(Vec3.atLowerCornerOf(tangent(target, edge).getNormal()).scale(depth * (folded ? 1 : 2)));
                if (folded) point = point.add(Vec3.atLowerCornerOf(target.getNormal()).scale(depth));
            }
            Vec3 local = point.subtract(Vec3.atLowerCornerOf(offset));
            Frame source = frame(face);
            return new Vec3(source.x(local), source.y(local), 0);
        }
    }

    private static boolean open(BlockGetter level, BlockPos pos, Direction face) {
        BlockPos outside = pos.relative(face);
        BlockState adjacent = level.getBlockState(outside);
        return adjacent.getFluidState().isEmpty() && !adjacent.isCollisionShapeFullBlock(level, outside);
    }

    private static boolean insetTop(BlockState state, Direction face) {
        return state.is(ModBlocks.FARMLAND.get()) && face == Direction.UP;
    }

    public static List<Connection> collect(BlockGetter level, BlockPos pos, BlockState target, Direction face) {
        return collect(level, pos, target, face, TerrainFaceConnections::rank, (p, f) -> open(level, p, f));
    }

    /** Shared native-face geometry with each material family's priority and exposure rules. */
    public static List<Connection> collect(BlockGetter level, BlockPos pos, BlockState target, Direction face,
            ToIntFunction<BlockState> priority, BiPredicate<BlockPos, Direction> exposed) {
        if (priority.applyAsInt(target) < 0 || !exposed.test(pos, face)) return List.of();
        List<Connection> result = new ArrayList<>();
        for (int edge = 0; edge < 4; edge++) {
            Direction tangent = tangent(face, edge);
            BlockPos neighbor = pos.relative(tangent);
            BlockState source = level.getBlockState(neighbor);
            if (priority.applyAsInt(source) > priority.applyAsInt(target) && exposed.test(neighbor, face)
                    && insetTop(source, face) == insetTop(target, face)) {
                result.add(new Connection(source, face, neighbor.subtract(pos), edge, false, false));
            } else {
                neighbor = neighbor.relative(face);
                source = level.getBlockState(neighbor);
                Direction foldedFace = tangent.getOpposite();
                // An inset farm top is physically below this cube edge: do not bridge that air gap.
                if (priority.applyAsInt(source) > priority.applyAsInt(target) && exposed.test(neighbor, foldedFace)
                        && !insetTop(source, foldedFace) && !insetTop(target, face))
                    result.add(new Connection(source, foldedFace, neighbor.subtract(pos), edge, true, false));
            }
        }
        for (int corner = 0; corner < 4; corner++) {
            BlockPos neighbor = pos.relative(tangent(face, corner)).relative(tangent(face, (corner + 1) % 4));
            BlockState source = level.getBlockState(neighbor);
            if (priority.applyAsInt(source) <= priority.applyAsInt(target) || !exposed.test(neighbor, face)
                    || insetTop(source, face) != insetTop(target, face)) continue;
            boolean covered = false;
            for (Connection edge : result) if (!edge.corner() && (edge.edge() == corner || edge.edge() == (corner + 1) % 4)
                    && priority.applyAsInt(edge.state()) >= priority.applyAsInt(source)) covered = true;
            if (!covered) result.add(new Connection(source, face, neighbor.subtract(pos), corner, false, true));
        }
        result.sort(Comparator.comparingInt(connection -> priority.applyAsInt(connection.state())));
        return List.copyOf(result);
    }
}
