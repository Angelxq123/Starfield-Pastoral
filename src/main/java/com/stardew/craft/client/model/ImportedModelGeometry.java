package com.stardew.craft.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.math.Transformation;
import com.stardew.craft.model.ModelGeometry;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.IModelBuilder;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.SimpleModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.SimpleUnbakedGeometry;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Function;

/** Static baked rest pose for generic/Geo imports; standard item display transforms apply unchanged. */
public final class ImportedModelGeometry extends SimpleUnbakedGeometry<ImportedModelGeometry> {
    public static final IGeometryLoader<ImportedModelGeometry> LOADER = ImportedModelGeometry::read;
    private final List<Part> parts;

    private ImportedModelGeometry(List<Part> parts) {
        this.parts = List.copyOf(parts);
    }

    private static ImportedModelGeometry read(JsonObject json, JsonDeserializationContext context) {
        List<Part> parts = new ArrayList<>();
        for (var value : json.getAsJsonArray("parts")) {
            JsonObject part = value.getAsJsonObject();
            var faces = new EnumMap<Direction, BlockElementFace>(Direction.class);
            for (var face : part.getAsJsonObject("faces").entrySet()) {
                faces.put(Direction.byName(face.getKey()), context.deserialize(face.getValue(), BlockElementFace.class));
            }
            BlockElement element = new BlockElement(ModelGeometry.vector(part.getAsJsonArray("from")),
                ModelGeometry.vector(part.getAsJsonArray("to")), faces, null,
                !part.has("shade") || part.get("shade").getAsBoolean(),
                net.neoforged.neoforge.client.model.ExtraFaceData.read(part.get("neoforge_data"),
                    net.neoforged.neoforge.client.model.ExtraFaceData.DEFAULT));
            // Convert pixel-space translation to block units; rotation and scale are unchanged.
            Matrix4f transform = ModelGeometry.transform(part);
            transform.m30(transform.m30() / 16).m31(transform.m31() / 16).m32(transform.m32() / 16);
            parts.add(new Part(element, transform));
        }
        return new ImportedModelGeometry(parts);
    }

    @Override
    protected void addQuads(IGeometryBakingContext context, IModelBuilder<?> builder, ModelBaker baker,
                            Function<Material, TextureAtlasSprite> sprites, ModelState state) {
        Matrix4f outer = state.getRotation().compose(context.getRootTransform()).blockCenterToCorner().getMatrix();
        for (Part part : parts) {
            var transformer = QuadTransformers.applying(new Transformation(new Matrix4f(outer).mul(part.transform())));
            for (var entry : part.element().faces.entrySet()) {
                var face = entry.getValue();
                BakedQuad quad = BlockModel.bakeFace(part.element(), face, sprites.apply(context.getMaterial(face.texture())),
                    entry.getKey(), new SimpleModelState(Transformation.identity()));
                transformer.processInPlace(quad);
                // Recalculate the lighting direction after arbitrary bone/cube rotations.
                builder.addUnculledFace(new BakedQuad(quad.getVertices(), quad.getTintIndex(),
                    FaceBakery.calculateFacing(quad.getVertices()), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion()));
            }
        }
    }

    private record Part(BlockElement element, Matrix4f transform) {}
}
