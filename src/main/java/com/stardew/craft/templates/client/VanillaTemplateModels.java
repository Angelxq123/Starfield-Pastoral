package com.stardew.craft.templates.client;

import com.stardew.craft.templates.TemplateShape;
import java.util.Optional;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Maps functional templates to Mojang-maintained states and models. */
final class VanillaTemplateModels {
    static boolean supports(TemplateShape shape) {
        return referenceBlock(shape) != null;
    }

    static Block referenceBlock(TemplateShape shape) {
        return switch (shape) {
            case BUTTON -> Blocks.OAK_BUTTON;
            case FENCE -> Blocks.OAK_FENCE;
            case FENCE_GATE -> Blocks.OAK_FENCE_GATE;
            case WALL -> Blocks.COBBLESTONE_WALL;
            case DOOR -> Blocks.OAK_DOOR;
            case TRAPDOOR -> Blocks.OAK_TRAPDOOR;
            case IRON_TRAPDOOR -> Blocks.IRON_TRAPDOOR;
            case PRESSURE_PLATE -> Blocks.OAK_PRESSURE_PLATE;
            default -> null;
        };
    }

    static BlockState referenceState(TemplateShape shape, BlockState templateState) {
        BlockState reference = referenceBlock(shape).defaultBlockState();
        for (Property<?> property : reference.getProperties()) {
            reference = copyByName(reference, templateState, property);
        }
        return reference;
    }

    private static <T extends Comparable<T>> BlockState copyByName(
            BlockState target, BlockState source, Property<T> targetProperty) {
        Property<?> sourceProperty = source.getProperties().stream()
                .filter(property -> property.getName().equals(targetProperty.getName()))
                .findFirst().orElse(null);
        if (sourceProperty == null) return target;
        String serialized = valueName(source, sourceProperty);
        Optional<T> parsed = targetProperty.getValue(serialized);
        return parsed.map(value -> target.setValue(targetProperty, value)).orElse(target);
    }

    private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private VanillaTemplateModels() {
    }
}
