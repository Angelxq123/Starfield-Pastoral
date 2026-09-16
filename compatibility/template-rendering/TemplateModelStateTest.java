package com.stardew.craft.templates.client;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.client.model.terrain.ShapedMaterialContext;
import com.stardew.craft.templates.GridWindowTemplateBlock;
import com.stardew.craft.templates.BalconyRailingTemplateBlock;
import com.stardew.craft.templates.RoofTemplateBlock;
import com.stardew.craft.templates.MaterialTemplateBlock;
import com.stardew.craft.templates.TemplateBlockEntity;
import com.stardew.craft.templates.TemplateShape;
import java.lang.reflect.Field;
import java.util.List;
import java.util.EnumMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.MultiPartBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the production wrapper and real multipart selectors without creating a game window. */
class TemplateModelStateTest {
    private static Minecraft previousMinecraft;
    private static TextureAtlasSprite sprite;
    private static final EnumMap<TemplateShape, Block> templates = new EnumMap<>(TemplateShape.class);

    @BeforeAll static void bootstrap() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze();
        for (TemplateShape shape : List.of(TemplateShape.GRID_WINDOW, TemplateShape.BALCONY_RAILING, TemplateShape.ROOF_SLOPE)) {
            Block block = switch (shape) {
                case GRID_WINDOW -> new GridWindowTemplateBlock(Block.Properties.of());
                case BALCONY_RAILING -> new BalconyRailingTemplateBlock(Block.Properties.of());
                default -> new RoofTemplateBlock(shape, Block.Properties.of());
            };
            Registry.register(registry, ResourceLocation.fromNamespaceAndPath("template_test", shape.name().toLowerCase(java.util.Locale.ROOT)), block);
            block.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
            templates.put(shape, block);
        }
        registry.freeze();
        // Only model lookup and three material identities are needed, not the mod loading lifecycle.
        bind(ModBlocks.BLUE_GRAY_TIMBER, Blocks.OAK_LOG);
        bind(ModBlocks.PALE_BLUE_WINDOW_GLASS, Blocks.GLASS);
        bind(ModBlocks.GRAY_VIOLET_ROOF_TILES, Blocks.STONE);
        previousMinecraft = Minecraft.getInstance();
        Minecraft client = allocate(Minecraft.class);
        field(Minecraft.class, "instance").set(null, client);
        field(Minecraft.class, "blockRenderer").set(client, allocate(MaterialModels.class));
        sprite = allocate(TextureAtlasSprite.class);
        field(TextureAtlasSprite.class, "u1").setFloat(sprite, 1);
        field(TextureAtlasSprite.class, "v1").setFloat(sprite, 1);
    }

    @AfterAll static void restoreClient() throws Exception {
        field(Minecraft.class, "instance").set(null, previousMinecraft);
    }

    @Test void defaultMultipartWindowsAndRailingsUseTemplateProperties() {
        for (TemplateShape shape : List.of(TemplateShape.GRID_WINDOW, TemplateShape.BALCONY_RAILING)) {
            var block = templates.get(shape);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                BakedModel authored = multipart(state);
                var wrapper = new TemplateBakedModel(authored, shape, state, false);
                var types = wrapper.getRenderTypes(state, RandomSource.create(42), ModelData.EMPTY);
                assertTrue(types.contains(RenderType.solid()));
                if (shape == TemplateShape.GRID_WINDOW) assertTrue(types.contains(RenderType.translucent()));
                assertFalse(wrapper.getQuads(state, null, RandomSource.create(42), ModelData.EMPTY, RenderType.solid()).isEmpty());
            }
        }
    }

    @Test void materialWithNoFacingReproducesTheReportedMultipartFailure() {
        BlockState state = templates.get(TemplateShape.GRID_WINDOW).defaultBlockState();
        var error = assertThrows(IllegalArgumentException.class, () -> multipart(state)
                .getRenderTypes(Blocks.OAK_PLANKS.defaultBlockState(), RandomSource.create(42), ModelData.EMPTY));
        assertTrue(error.getMessage().contains("facing"));
    }

    @Test void defaultWindowInventoryRenderLayersUseTemplateState() {
        BlockState state = templates.get(TemplateShape.GRID_WINDOW).defaultBlockState();
        var wrapper = new TemplateBakedModel(multipart(state), TemplateShape.GRID_WINDOW, state, true);
        assertFalse(wrapper.getRenderTypes(ItemStack.EMPTY, false).isEmpty());
        assertFalse(wrapper.getRenderTypes(ItemStack.EMPTY, true).isEmpty());
    }

    @Test void replacementMaterialsKeepTheirOwnStatesAndTransparentFill() {
        for (TemplateShape shape : List.of(TemplateShape.GRID_WINDOW, TemplateShape.BALCONY_RAILING, TemplateShape.ROOF_SLOPE)) {
            BlockState state = templates.get(shape).defaultBlockState();
            var wrapper = new TemplateBakedModel(multipart(state), shape, state, false);
            for (Block material : List.of(Blocks.OAK_PLANKS, Blocks.OAK_LOG, Blocks.GLASS)) {
                ModelData data = ModelData.builder().with(TemplateBlockEntity.MATERIAL_PROPERTY, material.defaultBlockState())
                        .with(TemplateBlockEntity.FILL_MATERIAL_PROPERTY, Blocks.GLASS.defaultBlockState()).build();
                var types = wrapper.getRenderTypes(state, RandomSource.create(42), data);
                boolean transparent = material == Blocks.GLASS || shape.isComposite() || shape == TemplateShape.GRID_WINDOW;
                assertEquals(transparent, types.contains(RenderType.translucent()), "Incorrect transparent layer: " + shape);
                if (material != Blocks.GLASS) assertTrue(types.contains(RenderType.solid()));
            }
        }
    }

    @Test void studyRoofUsesTemplateStateForBothLayerAndQuadQueries() {
        BlockState state = templates.get(TemplateShape.ROOF_SLOPE).defaultBlockState();
        for (ModelData data : List.of(ModelData.EMPTY, ModelData.builder()
                .with(TemplateBlockEntity.MATERIAL_PROPERTY, Blocks.STONE.defaultBlockState()).build())) {
            var wrapper = new TemplateBakedModel(multipart(state), TemplateShape.ROOF_SLOPE, state, false);
            assertTrue(wrapper.getRenderTypes(state, RandomSource.create(42), data).contains(RenderType.solid()));
            for (RenderType layer : new RenderType[]{null, RenderType.solid()}) {
                assertFalse(wrapper.getQuads(state, null, RandomSource.create(42), data, layer).isEmpty());
                wrapper.getQuads(state, Direction.UP, RandomSource.create(42), data, layer);
            }
        }
    }

    @Test void nestedModelQueriesCanPopulateOrTouchTheSameLruCache() {
        BlockState state = templates.get(TemplateShape.GRID_WINDOW).defaultBlockState();
        for (boolean cached : List.of(false, true)) {
            TemplateBakedModel[] model = new TemplateBakedModel[1];
            model[0] = new TemplateBakedModel(queryingModel(state, () -> quads(model[0], state, Direction.UP)),
                    TemplateShape.GRID_WINDOW, state, false);
            if (cached) {
                quads(model[0], state, Direction.UP);
                quads(model[0], state, Direction.DOWN); // Reading UP now changes the LRU order.
            }
            List<BakedQuad> result = quads(model[0], state, null);
            assertFalse(result.isEmpty());
            assertSame(result, quads(model[0], state, null));
        }
    }

    @Test void meshWorkersCanQueryEachOthersModelsWithoutHoldingCacheLocks() throws Exception {
        BlockState state = templates.get(TemplateShape.GRID_WINDOW).defaultBlockState();
        TemplateBakedModel[] models = new TemplateBakedModel[2];
        CountDownLatch building = new CountDownLatch(2);
        for (int i = 0; i < models.length; i++) {
            int other = 1 - i;
            models[i] = new TemplateBakedModel(queryingModel(state, () -> {
                building.countDown();
                try {
                    assertTrue(building.await(5, TimeUnit.SECONDS), "Both workers must enter model generation");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                quads(models[other], state, Direction.UP);
            }), TemplateShape.GRID_WINDOW, state, false);
        }
        var workers = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "template-cache-test");
            thread.setDaemon(true);
            return thread;
        });
        try {
            var first = workers.submit(() -> quads(models[0], state, null));
            var second = workers.submit(() -> quads(models[1], state, null));
            assertFalse(first.get(10, TimeUnit.SECONDS).isEmpty());
            assertFalse(second.get(10, TimeUnit.SECONDS).isEmpty());
        } finally {
            workers.shutdownNow();
        }
    }

    @Test void shapedTerrainSourceQueriesCanReenterTheCache() throws Exception {
        BlockState state = Blocks.STONE.defaultBlockState();
        ModelData data = ModelData.builder().with(ShapedMaterialContext.PROPERTY,
                new ShapedMaterialContext(BlockPos.ZERO, List.of())).build();
        BakedModel[] model = new BakedModel[1];
        boolean[] querying = {false};
        BakedModel source = queryingModel(state, () -> {
            if (querying[0]) return;
            querying[0] = true;
            try {
                model[0].getQuads(state, Direction.UP, RandomSource.create(42), data, RenderType.solid());
            } finally {
                querying[0] = false;
            }
        });
        var constructor = Class.forName("com.stardew.craft.client.model.terrain.ShapedTerrainModels$Surface")
                .getDeclaredConstructor(BakedModel.class, BlockState.class, boolean.class, boolean.class);
        constructor.setAccessible(true);
        model[0] = (BakedModel) constructor.newInstance(source, state, false, true);
        List<BakedQuad> result = model[0].getQuads(state, null, RandomSource.create(42), data, RenderType.solid());
        assertSame(result, model[0].getQuads(state, null, RandomSource.create(42), data, RenderType.solid()));
    }

    private static List<BakedQuad> quads(BakedModel model, BlockState state, Direction side) {
        return model.getQuads(state, side, RandomSource.create(42), ModelData.EMPTY, RenderType.solid());
    }

    private static BakedModel queryingModel(BlockState state, Runnable nestedQuery) {
        return new BakedModelWrapper<BakedModel>(new StateModel(state)) {
            @Override public List<BakedQuad> getQuads(BlockState actual, Direction side, RandomSource random,
                    ModelData data, RenderType layer) {
                if (side == null) nestedQuery.run();
                return originalModel.getQuads(actual, side, random);
            }
        };
    }

    private static BakedModel multipart(BlockState expected) {
        Predicate<BlockState> selector = state -> state.getValue(MaterialTemplateBlock.FACING)
                == expected.getValue(MaterialTemplateBlock.FACING)
                && expected.getProperties().stream().allMatch(property -> state.getValue(property).equals(expected.getValue(property)));
        return new MultiPartBakedModel(List.of(Pair.of(selector, new StateModel(expected))));
    }

    private static final class MaterialModels extends BlockRenderDispatcher {
        private MaterialModels() { super(null, null, null); }
        @Override public BakedModel getBlockModel(BlockState state) { return new StateModel(state); }
    }

    private record StateModel(BlockState expected) implements BakedModel {
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
            assertSame(expected, state, "A model was queried with another block's state");
            return ChunkRenderTypeSet.of(expected.is(Blocks.GLASS) ? RenderType.translucent() : RenderType.solid());
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            assertSame(expected, state, "Source quad lookup lost the model's state");
            return List.of(new BakedQuad(new int[32], -1, side == null ? Direction.NORTH : side, sprite, true));
        }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return sprite; }
        @Override public ItemTransforms getTransforms() { return ItemTransforms.NO_TRANSFORMS; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }

    private static void bind(DeferredHolder<?, ?> deferred, Block block) throws Exception {
        field(DeferredHolder.class, "holder").set(deferred, Holder.direct(block));
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(field(unsafe, "theUnsafe").get(null), type));
    }
}
