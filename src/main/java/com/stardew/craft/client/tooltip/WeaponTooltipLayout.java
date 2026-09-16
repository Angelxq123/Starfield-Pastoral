package com.stardew.craft.client.tooltip;

import java.util.List;

/** Screen constraints and ownership matching, independent of a running client. */
public final class WeaponTooltipLayout {
    public static final int EDGE = 8;
    private WeaponTooltipLayout() {}

    public static int width(int screenWidth, boolean expanded) {
        return Math.max(1, Math.min(expanded ? 344 : 280, screenWidth - EDGE * 2));
    }

    public static int height(int screenHeight, int requested) {
        return Math.max(1, Math.min(requested, screenHeight - EDGE * 2));
    }

    public static int clampScroll(int scroll, int contentHeight, int viewportHeight) {
        return Math.max(0, Math.min(scroll, Math.max(0, contentHeight - viewportHeight)));
    }

    public static int ownedStart(List<String> all, List<String> owned) {
        if (owned.isEmpty()) return -1;
        for (int start = 1; start + owned.size() <= all.size(); start++) {
            if (all.subList(start, start + owned.size()).equals(owned)) return start;
        }
        return -1;
    }
}
