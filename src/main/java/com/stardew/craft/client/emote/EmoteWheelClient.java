package com.stardew.craft.client.emote;

import com.stardew.craft.client.ModKeyMappings;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

@SuppressWarnings("null")
public final class EmoteWheelClient {

	private static boolean wasDown;

	private EmoteWheelClient() {
	}

	public static void onClientTick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || !mc.isWindowActive()) {
			wasDown = false;
			if (mc.screen instanceof EmoteWheelScreen) {
				mc.setScreen(null);
			}
			return;
		}

		boolean down = isWheelKeyHeld();
		if (down && !wasDown && mc.screen == null) {
			mc.setScreen(new EmoteWheelScreen());
		}
		if (!down && wasDown && mc.screen instanceof EmoteWheelScreen screen) {
			screen.confirmAndClose();
		}

		wasDown = down;
	}

	public static boolean isWheelKeyHeld() {
		Minecraft mc = Minecraft.getInstance();
		var mapping = ModKeyMappings.EMOTE_WHEEL;
		if (!mc.isWindowActive() || mapping.isUnbound()) {
			return false;
		}
		// setScreen clears KeyMapping state, and IN_GAME is inactive inside the wheel.
		// Read physical input so opening the screen does not count as releasing the key.
		var key = mapping.getKey();
		long window = mc.getWindow().getWindow();
		boolean held = switch (key.getType()) {
			case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
			case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
			case SCANCODE -> mc.screen instanceof EmoteWheelScreen screen
					? screen.isScanCodeHeld() : mapping.isDown();
		};
		return held && mapping.getKeyModifier().isActive(mapping.getKeyConflictContext());
	}

	public static void render(net.minecraft.client.gui.GuiGraphics guiGraphics) {
	}
}
