package com.stardew.craft.cutscene.server;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single authoring table for every 3D point used by combat rescue scenes.
 *
 * <p>{@link Status#AUTHOR_CONFIRMED} means the scene was authored against its actual
 * Minecraft destination (captured in game or checked against the shipped structure). {@link Status#PENDING_AUTHORING}
 * means the value is only a visible development fallback and must not be used
 * by the normal gameplay entry point.</p>
 */
public final class CombatRescuePoints {
    public enum Status {
        AUTHOR_CONFIRMED,
        PENDING_AUTHORING
    }

    public enum Role {
        PLAYER,
        RESCUER,
        RESCUER_EXIT,
        CAMERA,
        DESTINATION
    }

    public record Point(
            String id,
            Role role,
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            Status status,
            String note
    ) {
        public Point {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("point id cannot be blank");
            }
            if (role == null || dimension == null || status == null) {
                throw new IllegalArgumentException("point metadata cannot be null");
            }
            note = note == null ? "" : note;
        }

        public boolean isAuthorConfirmed() {
            return status == Status.AUTHOR_CONFIRMED;
        }
    }

    // Current earth_lobby: SDV farmer tile (19,10), rescuer (18,10), exit (18,13).
    // origin=(-20,63,-14), tile_origin=(2,3,2). All three stand on plain soil.
    // These are newly authored template points, NOT the obsolete point-wand captures.
    public static final Point M01 = confirmed(
            "M01", Role.PLAYER, ModMiningDimensions.STARDEW_MINING,
            1.5D, 66.0D, -1.5D, 0.0F, 0.0F,
            "earth_lobby: SDV farmer tile 19,10; south");
    public static final Point M02 = confirmed(
            "M02", Role.RESCUER, ModMiningDimensions.STARDEW_MINING,
            0.5D, 66.0D, -1.5D, -90.0F, 0.0F,
            "earth_lobby: SDV rescuer tile 18,10; east");
    public static final Point M03 = confirmed(
            "M03", Role.RESCUER_EXIT, ModMiningDimensions.STARDEW_MINING,
            0.5D, 66.0D, 1.5D, 0.0F, 0.0F,
            "earth_lobby: SDV rescuer exit tile 18,13; south");
    public static final Point M04 = confirmed(
            "M04", Role.CAMERA, ModMiningDimensions.STARDEW_MINING,
            3.5D, 69.0D, 0.5D, 129.0F, 36.0F,
            "earth_lobby: open chamber, looking northwest at the rescue pair");

    // Clinic checked against the current saved room: bed_1 main (22,43,-15), south.
    // H01 is the clear floor beside the bed; the skin actor animates onto/off its mattress.
    public static final Point H01 = confirmed(
            "H01", Role.PLAYER, ModDimensions.STARDEW_VALLEY,
            23.65D, 43.0D, -14.625D, -90.0F, 0.0F,
            "clinic bedside: safe standing handoff, east");
    public static final Point H02 = confirmed(
            "H02", Role.RESCUER, ModDimensions.STARDEW_VALLEY,
            24.8D, 43.0D, -15.65D, 90.0F, 0.0F,
            "Harvey beside the pillow, clear of bed and tables");
    public static final Point H03 = confirmed(
            "H03", Role.CAMERA, ModDimensions.STARDEW_VALLEY,
            25.8D, 45.2D, -13.5D, 120.0F, 25.0F,
            "clinic: wide view of pillow, Harvey and bedside exit");

    /*
     * IslandSouth — Minecraft captures have not been supplied.
     *
     * These fallbacks preserve the relative arrangement of the original SDV
     * tile event (farmer 13,33; rescuer 15,33; viewport centred on 13,33) only
     * for authoring/debug. Normal gameplay refuses to start this scene until
     * all three statuses are changed to AUTHOR_CONFIRMED.
     */
    public static final Point I01 = pending(
            "I01", Role.PLAYER, ModDimensions.STARDEW_VALLEY,
            13.0D, 64.0D, 33.0D, 0.0F, 0.0F,
            "PENDING: SDV IslandSouth farmer tile fallback, not a Minecraft capture");
    public static final Point I02 = pending(
            "I02", Role.RESCUER, ModDimensions.STARDEW_VALLEY,
            15.0D, 64.0D, 33.0D, 90.0F, 0.0F,
            "PENDING: SDV IslandSouth rescuer tile fallback, not a Minecraft capture");
    public static final Point I03 = pending(
            "I03", Role.CAMERA, ModDimensions.STARDEW_VALLEY,
            13.0D, 66.0D, 37.0D, 180.0F, 20.0F,
            "PENDING: provisional camera looking at I01/I02, must be recaptured");

    // Desert festival D01 came from the user's point-wand export and has no NPC event.
    public static final Point D01 = confirmed(
            "D01", Role.DESTINATION, ModDimensions.STARDEW_VALLEY,
            -221.0D, 64.0D, -193.0D, 0.0F, 0.0F,
            "user point-wand export: festival recovery, south");

    public static final List<Point> MINE = List.of(M01, M02, M03, M04);
    public static final List<Point> HOSPITAL = List.of(H01, H02, H03);
    public static final List<Point> ISLAND = List.of(I01, I02, I03);
    public static final List<Point> DESERT = List.of(D01);
    public static final Map<String, Point> ALL = indexAll();

    private CombatRescuePoints() {
    }

    public static boolean allAuthorConfirmed(List<Point> points) {
        return points != null && !points.isEmpty()
                && points.stream().allMatch(Point::isAuthorConfirmed);
    }

    public static List<String> pendingPointIds(List<Point> points) {
        if (points == null) {
            return List.of();
        }
        return points.stream()
                .filter(point -> !point.isAuthorConfirmed())
                .map(Point::id)
                .toList();
    }

    private static Point confirmed(
            String id,
            Role role,
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String note
    ) {
        return new Point(id, role, dimension, x, y, z, yaw, pitch, Status.AUTHOR_CONFIRMED, note);
    }

    private static Point pending(
            String id,
            Role role,
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String note
    ) {
        return new Point(id, role, dimension, x, y, z, yaw, pitch, Status.PENDING_AUTHORING, note);
    }

    private static Map<String, Point> indexAll() {
        Map<String, Point> result = new LinkedHashMap<>();
        for (Point point : List.of(M01, M02, M03, M04, H01, H02, H03, I01, I02, I03, D01)) {
            if (result.putIfAbsent(point.id(), point) != null) {
                throw new IllegalStateException("Duplicate combat rescue point ID: " + point.id());
            }
        }
        return Map.copyOf(result);
    }
}
