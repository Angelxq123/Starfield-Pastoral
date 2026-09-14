package com.stardew.craft.client.gui.common;

/** Intrinsic bounds for non-scrolling legacy pages, in design units including outer controls/margins.
 * Values must not depend on the available screen width/height, to avoid layout feedback loops.
 * Scrolling pages should keep the default canvas and fit their viewport instead.
 */
public interface StardewGuiContentSize {
    int minimumCanvasWidth();
    int minimumCanvasHeight();
}
