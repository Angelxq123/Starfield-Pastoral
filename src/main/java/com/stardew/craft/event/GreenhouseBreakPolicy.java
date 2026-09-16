package com.stardew.craft.event;

/** Pure part of the greenhouse structure protection rule. */
final class GreenhouseBreakPolicy {
    private GreenhouseBreakPolicy() {
    }

    /** Player facilities such as sprinklers can be removed from original soil. */
    static boolean shouldProtectOriginalGreenhouseBlock(boolean originalStructureBlock, boolean sprinkler) {
        return originalStructureBlock && !sprinkler;
    }
}
