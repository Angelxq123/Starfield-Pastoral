package com.stardew.craft.client.gui.menu;

import com.stardew.craft.client.gui.common.GuiLayoutMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuPageLayoutTest {
    @Test void actualMenuKeepsDirectoryAndRowsInsideTheirOwnRegions() {
        for (int line : new int[]{9, 10, 12, 15}) {
            for (int refreshWidth : new int[]{18, 49, 78}) {
                var l = MenuPageLayout.leaderboard(17, 13, 216, 191, line, refreshWidth);
                assertTrue(l.sidebar(), "Actual menu must never fall back to cycling metrics");
                assertTrue(l.metricVisible() >= (line <= 10 ? 6 : 4));
                assertTrue(l.visibleRows() >= 2);
                assertTrue(l.listY() >= l.periodY() + l.refreshH());
                assertTrue(l.listY() + l.visibleRows() * l.rowH() <= l.listBottom());
                assertTrue(l.listBottom() < l.selfY());
                assertTrue(l.selfY() + l.rowH() <= l.refreshY());
                assertTrue(l.refreshY() + l.refreshH() <= 13 + 191 - 8);
                assertTrue(l.metricX() + l.metricW() < l.contentX());
                assertTrue(l.refreshX() + l.refreshW() <= 17 + 216 - 8);
                for (int i = 0; i < l.metricVisible(); i++) {
                    var b = MenuPageLayout.metricButton(l, i);
                    assertTrue(b.y() + b.height() <= 13 + 191 - 8 - line - 5);
                    assertTrue(b.contains(b.x() + b.width() / 2.0, b.y() + b.height() / 2.0));
                    assertFalse(b.contains(b.x() + b.width(), b.y()));
                    assertFalse(b.contains(l.contentX(), b.y()));
                }
            }
        }
    }

    @Test void directoryCanReachEveryMetricWithoutChangingSelection() {
        for (int line : new int[]{10, 15}) {
            var l = MenuPageLayout.leaderboard(0, 0, 216, 191, line, 49);
            for (int total : new int[]{26, 40}) {
                var bar = MenuPageLayout.metricScrollbar(l, total, 0);
                int end = bar.scrollAt(bar.y() + bar.height(), bar.thumbHeight());
                assertEquals(total - l.metricVisible(), end);
                assertEquals(0, bar.scrollAt(bar.y() - 100, 0));
                var last = MenuPageLayout.metricScrollbar(l, total, end);
                assertEquals(last.y() + last.height(), last.thumbY() + last.thumbHeight());
                for (int selected = 0; selected < total; selected++) {
                    int scroll = MenuPageLayout.revealMetric(end, selected, total, l.metricVisible());
                    assertTrue(selected >= scroll && selected < scroll + l.metricVisible());
                }
                assertEquals(end, MenuPageLayout.revealMetric(end, total - 1, total, l.metricVisible()));
            }
        }
    }

    @Test void oddFramebuffersAndAllGuiScalesKeepRenderAndHitCoordinatesAligned() {
        for (int[] size : new int[][]{{853, 479}, {1281, 721}, {1921, 1081}, {3441, 1441}}) {
            for (int scale = 1; scale <= 6; scale++) {
                var viewport = GuiLayoutMath.viewport(size[0], size[1], scale);
                int mx = (viewport.width() - 216) / 2, my = (viewport.height() - 191) / 2;
                var l = MenuPageLayout.leaderboard(mx, my, 216, 191, 10, 49);
                for (int i = 0; i < l.metricVisible(); i++) {
                    var b = MenuPageLayout.metricButton(l, i);
                    double x = b.x() + b.width() / 2.0, y = b.y() + b.height() / 2.0;
                    double physicalX = (viewport.x() + x * viewport.scale()) * scale;
                    double physicalY = (viewport.y() + y * viewport.scale()) * scale;
                    double localX = viewport.rawMouseX(physicalX, size[0]);
                    double localY = viewport.rawMouseY(physicalY, size[1]);
                    assertEquals(x, localX, 1e-8);
                    assertEquals(y, localY, 1e-8);
                    assertTrue(b.contains(localX, localY));
                }
            }
        }
    }
}
