package com.stardew.craft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Consumer;

public final class FarmCategoryScreen extends FarmFolioScreen {
    private final List<String> values;
    private final Consumer<String> select;
    private final java.util.Map<String, Component> labels;
    private final Component all;
    private int page;

    public FarmCategoryScreen(Screen parent, List<String> values, Consumer<String> select) {
        this(
                parent,
                values.stream()
                        .collect(
                                java.util.stream.Collectors.toMap(
                                        s -> s,
                                        s -> {
                                            var id = ResourceLocation.tryParse(s);
                                            return id == null
                                                    ? Component.literal(s)
                                                    : Component.translatable(
                                                            "stardewcraft.manager.building."
                                                                    + id.getPath());
                                        },
                                        (a, b) -> a,
                                        java.util.LinkedHashMap::new)),
                ui("all_animals"),
                select);
    }

    public FarmCategoryScreen(
            Screen parent,
            java.util.Map<String, Component> labels,
            Component all,
            Consumer<String> select) {
        super(ui("category"), parent);
        this.values = List.copyOf(labels.keySet());
        this.labels = java.util.Map.copyOf(labels);
        this.all = all;
        this.select = select;
    }

    @Override
    protected int preferredWidth() {
        return 280;
    }

    @Override
    protected int preferredHeight() {
        return 280;
    }

    @Override
    protected void layout() {
        button(all, x + 20, y + 38, w - 40, 26, () -> choose(""));
        int count = Math.max(1, (h - 142) / 32);
        for (int i = page * count; i < Math.min(values.size(), (page + 1) * count); i++) {
            String value = values.get(i);
            button(
                    labels.get(value),
                    x + 20,
                    y + 72 + (i - page * count) * 32,
                    w - 40,
                    26,
                    () -> choose(value));
        }
        arrow(
                false,
                x + 24,
                y + h - 64,
                page > 0,
                () -> {
                    page--;
                    init();
                });
        arrow(
                true,
                x + w - 48,
                y + h - 64,
                (page + 1) * count < values.size(),
                () -> {
                    page++;
                    init();
                });
        button(Component.translatable("gui.back"), x + 20, y + h - 32, w - 40, 22, this::onClose);
    }

    private void choose(String value) {
        minecraft.setScreen(parent);
        select.accept(value);
    }

    @Override
    protected void paint(GuiGraphics g) {}
}
