package com.stardew.craft.templates;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.StairsShape;

public enum TemplateShape {
    SLOPE_EDGE("material_template_slope_edge", "Slope Edge Template", "斜坡边模板", MeshKind.SLOPE_EDGE,
            boxes(box(0, 0, 0, 16, 4, 8), box(0, 4, 0, 16, 8, 4))),
    ELEVATED_SLOPE_EDGE("material_template_elevated_slope_edge", "Elevated Slope Edge Template", "抬高斜坡边模板", MeshKind.ELEVATED_SLOPE_EDGE,
            boxes(box(0, 0, 0, 16, 8, 16), box(0, 8, 0, 16, 16, 8),
                    box(0, 8, 8, 16, 12, 16), box(0, 12, 8, 16, 16, 12))),
    SLAB("material_template_slab", "Slab Template", "半砖模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 16, 8, 16))),
    SNOW_LAYER("material_template_snow_layer", "Snow Layer Template", "雪片模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 16, 2, 16))),
    SLAB_EDGE("material_template_slab_edge", "Slab Edge Template", "半砖边模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 16, 8, 8))),
    SLAB_CORNER("material_template_slab_corner", "Slab Corner Template", "半砖角模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 8, 8, 8))),
    WALL("material_template_wall", "Wall Template", "墙模板", MeshKind.BOXES,
            boxes(box(5, 0, 0, 11, 16, 16))),
    THREEWAY_CORNER_PILLAR("material_template_threeway_corner_pillar", "Three-way Corner Pillar Template", "三向角柱模板", MeshKind.BOXES,
            boxes(box(8, 0, 8, 16, 16, 16), box(8, 0, 0, 16, 8, 8),
                    box(0, 0, 8, 8, 8, 16))),
    VERTICAL_STAIRS("material_template_vertical_stairs", "Vertical Stairs Template", "竖向楼梯模板", MeshKind.BOXES,
            boxes(box(0, 0, 8, 16, 16, 16), box(8, 0, 0, 16, 16, 8))),
    SIGN("material_template_sign", "Sign Template", "告示牌模板", MeshKind.BOXES,
            boxes(box(1, 8, 7, 15, 16, 9), box(7, 0, 7, 9, 8, 9))),
    FLOOR_BOARD("material_template_floor_board", "Floor Board Template", "地板模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 16, 1, 16))),
    LADDER("material_template_ladder", "Ladder Template", "梯子模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 2, 16, 2), box(14, 0, 0, 16, 16, 2),
                    box(2, 1.5F, 0.5F, 14, 2.5F, 1.5F),
                    box(2, 5.5F, 0.5F, 14, 6.5F, 1.5F),
                    box(2, 9.5F, 0.5F, 14, 10.5F, 1.5F),
                    box(2, 13.5F, 0.5F, 14, 14.5F, 1.5F))),
    BUTTON("material_template_button", "Button Template", "按钮模板", MeshKind.BOXES,
            boxes(box(5, 6, 0, 11, 10, 2))),
    TRAPDOOR("material_template_trapdoor", "Trapdoor Template", "活板门模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 16, 3, 16))),
    IRON_TRAPDOOR("material_template_iron_trapdoor", "Reinforced Trapdoor Template", "加固活板门模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 16, 3, 16))),
    PRESSURE_PLATE("material_template_pressure_plate", "Pressure Plate Template", "压力板模板", MeshKind.BOXES,
            boxes(box(1, 0, 1, 15, 1, 15))),
    WALL_BOARD("material_template_wall_board", "Wall Board Template", "墙板模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 16, 16, 1))),
    HANGING_SIGN("material_template_hanging_sign", "Hanging Sign Template", "悬挂告示牌模板", MeshKind.BOXES,
            boxes(box(1, 0, 7, 15, 10, 9),
                    box(2, 10, 7.5F, 4, 16, 8.5F), box(12, 10, 7.5F, 14, 16, 8.5F))),
    FENCE("material_template_fence", "Fence Template", "栅栏模板", MeshKind.BOXES,
            boxes(box(6, 0, 6, 10, 16, 10))),
    DOOR("material_template_door", "Door Template", "门模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 16, 16, 3))),
    HALF_STAIRS("material_template_half_stairs", "Half Stairs Template", "半楼梯模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 8, 8, 16), box(0, 8, 8, 8, 16, 16))),
    FENCE_GATE("material_template_fence_gate", "Fence Gate Template", "栅栏门模板", MeshKind.BOXES,
            boxes(box(0, 5, 7, 2, 16, 9), box(14, 5, 7, 16, 16, 9),
                    box(2, 6, 7, 14, 9, 9), box(2, 12, 7, 14, 15, 9),
                    box(6, 9, 7, 10, 12, 9))),
    PANEL("material_template_panel", "Panel Template", "竖板模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 16, 16, 8))),
    CORNER_PILLAR("material_template_corner_pillar", "Corner Pillar Template", "角柱模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 8, 16, 8))),
    SLICED_STAIRS_PANEL("material_template_sliced_stairs_panel", "Sliced Stairs Panel Template", "切片楼梯板模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 16, 8, 16), box(0, 8, 8, 16, 16, 16))),
    SLOPE("material_template_slope", "Slope Template", "斜坡模板", MeshKind.SLOPE,
            boxes(box(0, 0, 0, 16, 4, 16), box(0, 4, 0, 16, 8, 12),
                    box(0, 8, 0, 16, 12, 8), box(0, 12, 0, 16, 16, 4))),

    SIDE_QUARTER_SLAB("material_template_side_quarter_slab", "Side Quarter Slab Template", "侧置四分之一砖模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 16, 16, 4))),
    COLUMN("material_template_column", "Column Template", "梁柱模板", MeshKind.BOXES,
            boxes(box(4, 0, 4, 12, 16, 12))),
    POST("material_template_post", "Post Template", "立柱模板", MeshKind.BOXES,
            boxes(box(5, 0, 5, 11, 16, 11))),
    POLE("material_template_pole", "Pole Template", "细杆模板", MeshKind.BOXES,
            boxes(box(6, 0, 6, 10, 16, 10))),
    CHIMNEY("material_template_chimney", "Chimney Template", "通用烟囱模板", MeshKind.BOXES,
            ChimneyTemplateBlock.cappedBoxes()),
    STICK("material_template_stick", "Stick Template", "细柄模板", MeshKind.BOXES,
            boxes(box(7, 0, 7, 9, 16, 9))),
    HORIZONTAL_COLUMN("material_template_horizontal_column", "Horizontal Column Template", "横向梁模板", MeshKind.BOXES,
            boxes(box(4, 4, 0, 12, 12, 16))),
    HORIZONTAL_POST("material_template_horizontal_post", "Horizontal Post Template", "横向柱模板", MeshKind.BOXES,
            boxes(box(5, 5, 0, 11, 11, 16))),
    FRAME_BOTTOM("material_template_frame_bottom", "Bottom Frame Template", "底部框架模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 16, 4, 2), box(0, 4, 0, 16, 6, 3))),
    HORIZONTAL_STICK("material_template_horizontal_stick", "Horizontal Stick Template", "横向细柄模板", MeshKind.BOXES,
            boxes(box(7, 7, 0, 9, 9, 16))),
    HORIZONTAL_POLE("material_template_horizontal_pole", "Horizontal Pole Template", "横向细杆模板", MeshKind.BOXES,
            boxes(box(6, 6, 0, 10, 10, 16))),
    FRAME_TOP("material_template_frame_top", "Top Frame Template", "顶部框架模板", MeshKind.BOXES,
            boxes(box(0, 14, 0, 16, 16, 7), box(0, 10, 0, 16, 14, 2))),
    FRAME_RIGHT("material_template_frame_right", "Right Frame Template", "右侧框架模板", MeshKind.BOXES,
            boxes(box(12, 0, 0, 16, 16, 2))),
    FRAME_LEFT("material_template_frame_left", "Left Frame Template", "左侧框架模板", MeshKind.BOXES,
            boxes(box(0, 0, 0, 4, 16, 2))),

    WALL_BEAM("material_template_wall_beam", "Wall Beam Template", "贴墙横梁模板", MeshKind.BOXES,
            boxes(box(0, 6, 0, 16, 10, 3))),
    WALL_POST("material_template_wall_post", "Wall Post Template", "贴墙立梁模板", MeshKind.BOXES,
            boxes(box(6, 0, 0, 10, 16, 3))),
    WALL_BRACE_LEFT("material_template_wall_brace_left", "Rising Wall Brace Template", "左低右高斜撑模板", MeshKind.WALL_BRACE,
            braceBoxes(false)),
    WALL_BRACE_RIGHT("material_template_wall_brace_right", "Falling Wall Brace Template", "左高右低斜撑模板", MeshKind.WALL_BRACE,
            braceBoxes(true)),
    WALL_INFILL("material_template_wall_infill", "Recessed Wall Template", "内凹墙身模板", MeshKind.BOXES,
            boxes(box(0,0,3,16,16,16))),
    WALL_JUNCTION("material_template_wall_junction", "Wall Joint Template", "墙梁接头模板", MeshKind.BOXES,
            boxes(box(0,6,0,16,10,3),box(6,0,0,10,16,3))),
    WINDOW_FRAME("material_template_window_frame", "Window Frame Template", "拼接窗框模板", MeshKind.BOXES,
            boxes(box(0,0,0,2,16,8),box(14,0,0,16,16,8),box(2,0,0,14,2,8),box(2,14,0,14,16,8))),
    WINDOW_TRANSOM("material_template_window_transom", "Window Transom Template", "横窗棂模板", MeshKind.BOXES,
            boxes(box(0,7,0,16,9,8))),
    BALCONY_RAILING("material_template_balcony_railing", "Balcony Railing Template", "阳台栏杆模板", MeshKind.BOXES,
            boxes(box(0,0,0,4,16,4),box(12,0,0,16,16,4),box(4,12,0,12,14,4),box(4,1,1,12,3,3))),
    GRID_WINDOW("material_template_grid_window", "Divided Window Template", "分格窗模板", MeshKind.BOXES, GridWindowProfile.frame(0)),
    ROUND_WINDOW("material_template_round_window", "Round Window Template", "圆窗模板", MeshKind.BOXES,
            RoundWindowProfile.frame()),

    ROOF_EAVE("material_template_roof_eave", "Eave Fascia Template", "屋檐封边模板", MeshKind.BOXES,
            boxes(box(0, 12, 0, 16, 16, 3), box(0, 11, 0, 16, 12, 2))),
    GABLE_PANEL("material_template_gable_panel", "Gable Panel Template", "山墙三角板模板", MeshKind.GABLE_PANEL,
            boxes(box(0, 0, 0, 3, 16, 4), box(0, 0, 4, 3, 12, 8),
                    box(0, 0, 8, 3, 8, 12), box(0, 0, 12, 3, 4, 16))),

    ROOF_SLOPE("material_template_roof_slope", "Roof Slope Template", "斜屋面模板", RoofTemplateForm.SLOPE),
    ROOF_STEEP("material_template_roof_steep", "Steep Roof Template", "陡坡屋面模板", RoofTemplateForm.STEEP),
    ROOF_LOWER("material_template_roof_lower", "Lower Eave Template", "下檐缓坡模板", RoofTemplateForm.LOWER),
    ROOF_UPPER_LOW("material_template_roof_upper_low", "Upper Shallow Roof Template", "上段缓坡模板", RoofTemplateForm.UPPER_LOW),
    ROOF_UPPER_STEEP("material_template_roof_upper_steep", "Upper Steep Roof Template", "上段陡坡模板", RoofTemplateForm.UPPER_STEEP),
    ROOF_RIDGE("material_template_roof_ridge", "Roof Ridge Template", "直屋脊模板", RoofTemplateForm.RIDGE),
    ROOF_GAMBREL("material_template_roof_gambrel", "Gambrel Roof Template", "折线屋顶模板", RoofTemplateForm.GAMBREL);

    private final String registryPath;
    private final String englishName;
    private final String chineseName;
    private final MeshKind meshKind;
    private final RoofTemplateForm roofForm;
    private final List<TemplateBox> collisionBoxes;

    TemplateShape(String registryPath, String englishName, String chineseName, MeshKind meshKind,
                  List<TemplateBox> collisionBoxes) {
        this.registryPath = registryPath;
        this.englishName = englishName;
        this.chineseName = chineseName;
        this.meshKind = meshKind;
        this.roofForm = null;
        this.collisionBoxes = collisionBoxes;
    }

    TemplateShape(String registryPath, String englishName, String chineseName, RoofTemplateForm roofForm) {
        this.registryPath = registryPath;
        this.englishName = englishName;
        this.chineseName = chineseName;
        this.meshKind = MeshKind.ROOF;
        this.roofForm = roofForm;
        this.collisionBoxes = roofForm.collisionBoxes(StairsShape.STRAIGHT);
    }

    public boolean isCompositeWall() {
        return switch(this) {
            case WALL_BEAM,WALL_POST,WALL_BRACE_LEFT,WALL_BRACE_RIGHT,WALL_JUNCTION -> true;
            default -> false;
        };
    }

    public boolean isWindow() { return this == WINDOW_FRAME || this == WINDOW_TRANSOM; }
    public boolean isComposite() { return roofForm != null || isCompositeWall() || isWindow() || this == ROUND_WINDOW; }

    public String registryPath() {
        return registryPath;
    }

    public String englishName() {
        return englishName;
    }

    public String chineseName() {
        return chineseName;
    }

    public MeshKind meshKind() {
        return meshKind;
    }

    public RoofTemplateForm roofForm() {
        if (roofForm == null) {
            throw new IllegalStateException(this + " is not a roof template");
        }
        return roofForm;
    }

    public List<TemplateBox> collisionBoxes() {
        return collisionBoxes;
    }

    public List<TemplateBox> collisionBoxes(StairsShape roofShape) {
        return roofForm == null ? collisionBoxes : roofForm.collisionBoxes(roofShape);
    }

    public List<TemplateBox> collisionBoxes(StairsShape roofShape, int layers) {
        return this == SNOW_LAYER ? boxes(box(0, 0, 0, 16, layers * 2, 16)) : collisionBoxes(roofShape);
    }

    /**
     * Direction represented by the unrotated geometry above. FramedBlocks uses
     * north as the source direction for most face/corner shapes, but a handful
     * of stairs-derived shapes are authored facing south.
     */
    public Direction baseFacing() {
        if (roofForm != null && roofForm.isRidge()) {
            return Direction.SOUTH;
        }
        return switch (this) {
            case THREEWAY_CORNER_PILLAR, VERTICAL_STAIRS, HALF_STAIRS, SLICED_STAIRS_PANEL -> Direction.SOUTH;
            default -> Direction.NORTH;
        };
    }

    public PlacementMode placementMode() {
        if (roofForm != null) {
            return roofForm.usesFacing() ? PlacementMode.PLAYER : PlacementMode.NONE;
        }
        return switch (this) {
            case SLAB_CORNER, CORNER_PILLAR -> PlacementMode.HALF_OR_QUARTER;
            case THREEWAY_CORNER_PILLAR, VERTICAL_STAIRS -> PlacementMode.HALF;
            case PANEL, HALF_STAIRS,
                    SIDE_QUARTER_SLAB, COLUMN, POST, POLE, STICK,
                    HORIZONTAL_COLUMN, HORIZONTAL_POST, FRAME_BOTTOM,
                    HORIZONTAL_STICK, HORIZONTAL_POLE, FRAME_TOP,
                    FRAME_RIGHT, FRAME_LEFT, WALL_BEAM, WALL_POST, WALL_BRACE_LEFT,
                    WALL_BRACE_RIGHT, WALL_INFILL, WALL_JUNCTION, WINDOW_FRAME, WINDOW_TRANSOM, ROUND_WINDOW, GRID_WINDOW, ROOF_EAVE -> PlacementMode.TARGET_OR_PLAYER;
            case SLAB, SNOW_LAYER, WALL, FLOOR_BOARD, PRESSURE_PLATE, FENCE, CHIMNEY,
                    TRAPDOOR, IRON_TRAPDOOR -> PlacementMode.NONE;
            default -> PlacementMode.PLAYER;
        };
    }

    public FlipMode flipMode() {
        if (roofForm != null) {
            return roofForm.supportsFlip() ? FlipMode.SLOPE : FlipMode.NONE;
        }
        return switch (this) {
            case SLOPE, SLOPE_EDGE, ELEVATED_SLOPE_EDGE, GABLE_PANEL -> FlipMode.SLOPE;
            case SLAB, SLAB_EDGE, SLAB_CORNER, THREEWAY_CORNER_PILLAR,
                    HALF_STAIRS, SLICED_STAIRS_PANEL, TRAPDOOR, IRON_TRAPDOOR -> FlipMode.HALF;
            default -> FlipMode.NONE;
        };
    }

    public boolean canOccludeWithSolidMaterial() {
        if (roofForm != null) {
            return false;
        }
        return switch (this) {
            case SLOPE, ELEVATED_SLOPE_EDGE, SLAB, SNOW_LAYER, VERTICAL_STAIRS,
                    FLOOR_BOARD, TRAPDOOR, IRON_TRAPDOOR, WALL_BOARD,
                    DOOR, PANEL, SLICED_STAIRS_PANEL -> true;
            default -> false;
        };
    }

    public boolean visibleInCreativeTab() {
        return this != SIGN && this != HANGING_SIGN && this != DOOR && this != LADDER;
    }

    private static TemplateBox box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return new TemplateBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static List<TemplateBox> braceBoxes(boolean descending) {
        var result = new java.util.ArrayList<TemplateBox>();
        for (int x = 0; x < 16; x += 2) {
            float low = Math.max(0, x - 4);
            float high = Math.min(16, x + 6);
            result.add(box(x, descending ? 16 - high : low, 0,
                    x + 2, descending ? 16 - low : high, 3));
        }
        return List.copyOf(result);
    }

    private static List<TemplateBox> boxes(TemplateBox... boxes) {
        return List.of(boxes);
    }

    public enum MeshKind {
        BOXES,
        SLOPE,
        SLOPE_EDGE,
        ELEVATED_SLOPE_EDGE,
        ROOF,
        WALL_BRACE,
        GABLE_PANEL
    }

    public enum PlacementMode {
        NONE,
        PLAYER,
        TARGET_OR_PLAYER,
        HALF,
        HALF_OR_QUARTER
    }

    public enum FlipMode {
        NONE,
        HALF,
        SLOPE
    }
}
