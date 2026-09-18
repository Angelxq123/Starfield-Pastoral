package com.stardew.craft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public final class FarmRenameScreen extends FarmFolioScreen {
    private String value;
    private EditBox input;
    private Button save;
    private final Consumer<String> submit;
    private final int maximumLength;
    private final Component hint;

    public FarmRenameScreen(Screen parent, Component title, String value, Consumer<String> submit) {
        this(parent, title, value, 32, ui("name_hint"), submit);
    }

    public FarmRenameScreen(Screen parent, Component title, String value, int maximumLength, Component hint, Consumer<String> submit) {
        super(title, parent);
        this.value = value;
        this.submit = submit;
        this.maximumLength = maximumLength;
        this.hint = hint;
    }

    @Override
    protected int preferredWidth() {
        return 340;
    }

    @Override
    protected int preferredHeight() {
        return 172;
    }

    @Override
    protected void layout() {
        input = field(title, value, x + 48, y + 48, w - 64, 32);
        input.setMaxLength(maximumLength);
        input.setResponder(
                s -> {
                    value = s;
                    if (save != null) save.active = valid(s);
                });
        button(Component.translatable("gui.cancel"), x + 16, y + h - 38, 100, 26, this::onClose);
        save =
                button(
                        Component.translatable("gui.done"),
                        x + 128,
                        y + h - 38,
                        w - 144,
                        26,
                        () -> {
                            minecraft.setScreen(parent);
                            submit.accept(value.strip());
                        });
        save.active = valid(value);
        setInitialFocus(input);
    }

    public static boolean valid(String text) {
        return !text.isBlank()
                && text.length() <= 32
                && text.codePoints().noneMatch(c -> Character.isISOControl(c) || c == 0xA7);
    }

    @Override
    protected void paint(GuiGraphics g) {
        icon(g, "pencil", x + 20, y + 55, 16);
        paragraph(g, hint, x + 48, y + 92, w - 64, y + h - 44, CANVAS_MUTED);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && save.active) {
            save.onPress();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
