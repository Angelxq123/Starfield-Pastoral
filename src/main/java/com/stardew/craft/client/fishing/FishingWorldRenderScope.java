package com.stardew.craft.client.fishing;

import java.util.UUID;

/** Render-thread scope: GUI portraits never borrow the world's replacement arms. */
public final class FishingWorldRenderScope {
    private static UUID actor;
    private FishingWorldRenderScope() {}

    public static boolean owns(UUID id) { return id != null && id.equals(actor); }

    public static void render(UUID id, Runnable draw) {
        UUID previous=actor;
        actor=id;
        try { draw.run(); }
        finally { actor=previous; }
    }
}
