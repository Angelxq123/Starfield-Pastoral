package com.stardew.craft.client.gui.common;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ReadingZoomTest {
    private static final int[][] SIZES = {{640, 360}, {853, 479}, {1281, 721}, {1920, 1080},
            {1931, 1079}, {2560, 1440}, {3840, 2160}, {800, 1200}, {3440, 1440}};

    @Test void everySizeAndZoomKeepsHoverClickAndDragAlignedAfterPanning() {
        for (int percent = 75; percent <= 200; percent += 25) {
            for (var size : SIZES) for (int gui = 1; gui <= 8; gui++) {
                var base = GuiLayoutMath.viewport(size[0], size[1], gui);
                for (double pan : new double[]{0, .17, .5, .83, 1}) {
                    var zoom = ReadingZoomMath.calculate(base, size[0], size[1], ReadingTextLayout.scale(percent), pan, 1 - pan);
                    var v = zoom.viewport();
                    for (double fraction : new double[]{.05, .3, .5, .9}) {
                        double physicalX = zoom.viewWidth() * fraction;
                        double physicalY = zoom.viewHeight() * fraction;
                        // Include a retina window whose cursor coordinates are half the framebuffer size.
                        for (double retina : new double[]{1, 2}) {
                            int windowWidth = (int) Math.ceil(size[0] / retina), windowHeight = (int) Math.ceil(size[1] / retina);
                            double rawX = physicalX * windowWidth / size[0], rawY = physicalY * windowHeight / size[1];
                            double x = v.rawMouseX(rawX, windowWidth), y = v.rawMouseY(rawY, windowHeight);
                            assertEquals(physicalX, (v.x() + x * v.scale()) * gui, 1e-7);
                            assertEquals(physicalY, (v.y() + y * v.scale()) * gui, 1e-7);
                            assertEquals(x, v.windowMouseX(rawX, windowWidth) * v.width() / windowWidth, 1e-7);
                            assertEquals(y, v.windowMouseY(rawY, windowHeight) * v.height() / windowHeight, 1e-7);
                            double drag = v.windowDeltaX(7.25) * v.width() / windowWidth;
                            assertEquals(v.rawMouseX(rawX + 7.25, windowWidth) - x, drag, 1e-7);
                        }
                    }
                }
            }
        }
    }

    @Test void scrollingReachesEveryEdgeAndThumbDoesNotJumpWhenGrabbed() {
        for (var size : SIZES) for (int percent = 125; percent <= 200; percent += 25) {
            var base = GuiLayoutMath.viewport(size[0], size[1], 4);
            var first = ReadingZoomMath.calculate(base, size[0], size[1], ReadingTextLayout.scale(percent), 0, 0);
            var last = ReadingZoomMath.calculate(base, size[0], size[1], ReadingTextLayout.scale(percent), 1, 1);
            assertEquals(0, first.viewport().x(), 1e-7);
            assertEquals(0, first.viewport().y(), 1e-7);
            assertEquals(last.viewWidth(), (last.viewport().x() + last.viewport().width() * last.viewport().scale()) * 4, 1e-7);
            assertEquals(last.viewHeight(), (last.viewport().y() + last.viewport().height() * last.viewport().scale()) * 4, 1e-7);
            for (double position : new double[]{0, .2, .5, 1}) {
                var zoom = ReadingZoomMath.calculate(base, size[0], size[1], ReadingTextLayout.scale(percent), position, position);
                for (var bar : new ReadingZoomMath.Scrollbar[]{zoom.horizontal(), zoom.vertical()}) {
                    assertNotNull(bar);
                    double start = bar.horizontal() ? bar.x() : bar.y();
                    for (double grab : new double[]{0, bar.thumbLength() / 2, bar.thumbLength() - 1}) {
                        assertEquals(position, bar.fractionAt(start + bar.thumbStart() + grab, grab), 1e-7);
                    }
                    assertTrue(bar.contains(bar.x() + bar.width() / 2, bar.y() + bar.height() / 2));
                    assertFalse(bar.contains(bar.x() + bar.width(), bar.y()));
                    assertEquals(0, bar.fractionAt(-1000, 0));
                    assertEquals(1, bar.fractionAt(100000, 0));
                }
            }
        }
    }

    @Test void nestedClipsFollowTheZoomedAndPannedPage() {
        for (int percent = 75; percent <= 200; percent += 25) for (var size : SIZES) for (int gui = 1; gui <= 8; gui++) {
            var v = ReadingZoomMath.calculate(GuiLayoutMath.viewport(size[0], size[1], gui),
                    size[0], size[1], ReadingTextLayout.scale(percent), .3, .7).viewport();
            var pose = new Matrix4f().translate((float) v.x(), (float) v.y(), 0)
                    .scale((float) v.scale(), (float) v.scale(), 1).translate(17, 11, 0).scale(.75f, .75f, 1);
            var clip = GuiScissorMath.framebuffer(pose, gui, 10, 20, 100, 80);
            // A scissor must include both transformed corners, with subpixel edges rounded outward.
            double left = (v.x() + (17 + 10 * .75) * v.scale()) * gui;
            double top = (v.y() + (11 + 20 * .75) * v.scale()) * gui;
            double right = (v.x() + (17 + 100 * .75) * v.scale()) * gui;
            double bottom = (v.y() + (11 + 80 * .75) * v.scale()) * gui;
            assertTrue(Math.abs(clip.left() - left) <= 1.01 && Math.abs(clip.top() - top) <= 1.01);
            assertTrue(Math.abs(clip.right() - right) <= 1.01 && Math.abs(clip.bottom() - bottom) <= 1.01);
        }
    }

    @Test void defaultIsUnchangedAndHudFitsEveryConfiguredZoom() {
        assertEquals(1, ReadingTextLayout.scale(100));
        assertEquals(.75, ReadingTextLayout.scale(-100));
        assertEquals(2, ReadingTextLayout.scale(500));
        for (var size : SIZES) for (int gui = 1; gui <= 8; gui++) {
            var base = GuiLayoutMath.viewport(size[0], size[1], gui);
            var normal = ReadingZoomMath.calculate(base, size[0], size[1], 1, .1, .9);
            assertNull(normal.horizontal()); assertNull(normal.vertical());
            assertEquals(base.x(), normal.viewport().x(), 1e-7);
            assertEquals(base.y(), normal.viewport().y(), 1e-7);
            assertEquals(base.scale(), normal.viewport().scale(), 1e-7);
            for (int percent = 75; percent <= 200; percent += 25) for (int individual : new int[]{25, 100, 200}) {
                for (int[] bounds : new int[][]{{72, 90}, {278, 18}, {220, 48}, {280, 32}, {44, 90}}) {
                    float requested = ReadingTextLayout.scale(percent) * individual / 100.0F * 4 / gui;
                    int w = (size[0] + gui - 1) / gui, h = (size[1] + gui - 1) / gui;
                    float fitted = ReadingTextLayout.fitHudScale(requested, bounds[0], bounds[1], w, h);
                    assertTrue(fitted > 0 && fitted <= requested);
                    assertTrue(bounds[0] * fitted <= w + .001 && bounds[1] * fitted <= h + .001);
                }
            }
        }
    }

    @Test void settingsHaveEveryLanguageAndPreviewSpaceWithBothChineseFonts() throws Exception {
        for (String locale : new String[]{"zh_cn", "en_us", "de_de", "es_es", "fr_fr", "hu_hu", "it_it", "ja_jp", "ko_kr", "pt_br", "ru_ru", "tr_tr"}) {
            var lang = resource("assets/stardewcraft/lang/" + locale + ".json");
            for (String suffix : new String[]{"description", "preview", "smaller", "larger"}) {
                assertFalse(lang.get("stardewcraft.settings.reading." + suffix).getAsString().isBlank());
            }
            for (String fontLocale : locale.equals("zh_cn") ? new String[]{"zh_cn", "zh_cn_round"} : new String[]{locale.equals("it_it") ? "en_us" : locale}) {
                var metrics = resource("assets/stardewcraft/stardew_font_metrics/small/" + fontLocale + ".json");
                int lineHeight = (int) Math.ceil(metrics.get("line_height").getAsDouble() * metrics.get("scale").getAsDouble());
                // Upper bound by measuring each character independently, including left bearing.
                int lines = wrappedLines(metrics, lang.get("stardewcraft.settings.reading.description").getAsString(), 412);
                int previewTop = 26 + lineHeight + lines * (lineHeight + 2);
                assertTrue(242 - 62 - 8 - previewTop >= 24, locale + " settings must retain a usable preview viewport");
            }
        }
    }

    private static JsonObject resource(String path) throws Exception {
        try (var stream = ReadingZoomTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static int wrappedLines(JsonObject metrics, String text, int width) {
        var glyphs = new java.util.HashMap<Integer, Double>();
        double scale = metrics.get("scale").getAsDouble(), spacing = metrics.get("spacing").getAsDouble();
        for (var entry : metrics.getAsJsonArray("glyphs")) {
            var g = entry.getAsJsonObject();
            glyphs.put(g.get("cp").getAsInt(), (Math.max(0, g.get("left").getAsDouble()) + g.get("width").getAsDouble()
                    + g.get("right").getAsDouble() + spacing) * scale);
        }
        int lines = 1; double used = 0;
        for (int cp : text.codePoints().toArray()) {
            double advance = glyphs.getOrDefault(cp, 9.0);
            if (used + advance > width) { lines++; used = 0; }
            used += advance;
        }
        return lines;
    }

    @Test void clientMixinTargetsExistInTheActualMinecraftArtifact() throws Exception {
        assertEquals(1, verifyWrapOperations("StardewReadingHudTextMixin", "net/minecraft/client/gui/Gui"));
        assertEquals(2, verifyWrapOperations("StardewGuiScissorMixin", "net/minecraft/client/gui/GuiGraphics"));
    }

    // Read the actual compiled annotations, so a typo in their owner, descriptor or
    // return type cannot pass merely because Minecraft contains a similarly named call.
    private static int verifyWrapOperations(String mixin, String owner) throws Exception {
        var targetClass = readClass(owner);
        int checked = 0;
        for (var wrapper : readClass("com/stardew/craft/mixin/" + mixin).methods) {
            var annotations = new java.util.ArrayList<AnnotationNode>();
            if (wrapper.visibleAnnotations != null) annotations.addAll(wrapper.visibleAnnotations);
            if (wrapper.invisibleAnnotations != null) annotations.addAll(wrapper.invisibleAnnotations);
            for (var annotation : annotations) {
                if (!annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;")) continue;
                checked++;
                Object atValue = annotationValue(annotation, "at");
                var at = atValue instanceof AnnotationNode single ? single :
                        (AnnotationNode) ((java.util.List<?>) atValue).getFirst();
                String target = (String) annotationValue(at, "target");
                String descriptor = target.substring(target.indexOf('('));
                assertEquals(Type.getReturnType(descriptor), Type.getReturnType(wrapper.desc),
                        mixin + " wrapper must return the original invocation's value");
                for (Object method : (java.util.List<?>) annotationValue(annotation, "method")) {
                    int count = 0;
                    for (var m : targetClass.methods) if (m.name.equals(method) || (m.name + m.desc).equals(method)) {
                        for (var insn : m.instructions) if (insn instanceof MethodInsnNode call &&
                                ("L" + call.owner + ";" + call.name + call.desc).equals(target)) {
                            count++;
                            String args = call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC ? "" : "L" + call.owner + ";";
                            String expected = "(" + args + call.desc.substring(1, call.desc.indexOf(')')) +
                                    "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)" +
                                    Type.getReturnType(call.desc).getDescriptor();
                            assertEquals(expected, wrapper.desc, mixin + " callback signature");
                        }
                    }
                    assertTrue(count > 0, mixin + " cannot inject into " + owner + "." + method + ": " + target);
                }
            }
        }
        return checked;
    }

    private static Object annotationValue(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        }
        throw new AssertionError("Missing annotation value: " + name);
    }

    private static ClassNode readClass(String owner) throws Exception {
        var node = new ClassNode();
        try (var stream = ReadingZoomTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(stream, owner); new ClassReader(stream).accept(node, 0);
        }
        return node;
    }
}
