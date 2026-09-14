package com.stardew.craft.compat.ctm;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.junit.jupiter.api.Test;
import team.chisel.ctm.client.model.ModelBakedCTM;
import team.chisel.ctm.client.model.ModelCTM;
import team.chisel.ctm.client.util.TextureMetadataHandler;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the reported CTM binary and its transformed BakingCompleted handler, without a GPU atlas. */
class CtmModelInitializationTest {
    @Test void wrappedCtmModelsInitializeWithoutRemovingSurfaceFloors() throws Exception {
        for (int depth : new int[]{1, 2}) {
            ProbeModel model = new ProbeModel();
            BakedModel ctm = new ModelBakedCTM(model, new PlainModel(), null);
            BakedModel surface = ctm;
            for (int i = 0; i < depth; i++) surface = surface(surface);
            assertThrows(NullPointerException.class, model::getCTMTextures);
            var event = event(surface);
            TextureMetadataHandler.INSTANCE.onModelBake(event);
            assertEquals(1, model.bakes, "Surface wrapper hid CTM from its initialization pass");
            assertTrue(model.isInitialized());
            assertDoesNotThrow(model::getCTMTextures);
            assertSame(surface, event.getModels().values().iterator().next(), "Initialization removed floor rendering");
            TextureMetadataHandler.INSTANCE.onModelBake(event);
            assertEquals(1, model.bakes, "Already initialized textures were baked twice");
        }
    }

    @Test void unwrappedCtmAndOrdinaryModelsRetainTheirLifecycle() throws Exception {
        ProbeModel model = new ProbeModel();
        BakedModel ctm = new ModelBakedCTM(model, new PlainModel(), null);
        TextureMetadataHandler.INSTANCE.onModelBake(event(ctm));
        assertEquals(1, model.bakes, "The CTM wrapper itself must remain visible");
        var plain = surface(new PlainModel());
        var event = event(plain);
        assertDoesNotThrow(() -> TextureMetadataHandler.INSTANCE.onModelBake(event));
        assertSame(plain, event.getModels().values().iterator().next());
    }

    @Test void eachResourceReloadInitializesItsNewModelInstance() throws Exception {
        for (int reload = 0; reload < 3; reload++) {
            var model = new ProbeModel();
            TextureMetadataHandler.INSTANCE.onModelBake(event(surface(new ModelBakedCTM(model, new PlainModel(), null))));
            assertEquals(1, model.bakes);
            assertDoesNotThrow(model::getCTMTextures);
        }
    }

    private static ModelEvent.BakingCompleted event(BakedModel model) throws Exception {
        ModelBakery bakery = allocate(ModelBakery.class);
        field(ModelBakery.class, "bakedCache").set(bakery, new HashMap<>());
        var id = new ModelResourceLocation(ResourceLocation.fromNamespaceAndPath("yuushya", "reinforced_concrete"), "facing=west");
        return new ModelEvent.BakingCompleted(null, Map.of(id, model), bakery);
    }

    private static BakedModel surface(BakedModel model) throws Exception {
        Class<?> type = Class.forName("com.stardew.craft.client.model.terrain.SurfaceFloorModels$Surface");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return (BakedModel) constructor.newInstance(model, Map.of(), Map.of(), new BakedQuad[0],
                new BakedQuad[0][], new TextureAtlasSprite[0], null);
    }

    private static final class ProbeModel extends ModelCTM {
        int bakes;
        ProbeModel() { super((UnbakedModel) null); }
        @Override public BakedModel bake(ModelBaker bakery, Function<Material, TextureAtlasSprite> sprites, ModelState state) {
            // Record texture baking without an atlas; the CTM handler and null-sensitive lookup are real.
            bakes++;
            spriteOverrides = new Int2ObjectOpenHashMap<>();
            textureOverrides = new HashMap<>();
            return new PlainModel();
        }
        @Override public boolean isInitialized() { return textureOverrides != null; }
    }

    private static final class PlainModel implements BakedModel {
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) { return List.of(); }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return null; }
        @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        var unsafe = Class.forName("sun.misc.Unsafe");
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field(unsafe, "theUnsafe").get(null), type));
    }
}
