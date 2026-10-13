package app;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Turns your real view (the camera) for Web Escape, MLG Water, Clutch and Web Trap: they
 * snap it to where they place, the game sends that view like any mouse movement, and once
 * they are done it snaps back to where you were looking. If you move the mouse while it is
 * turned, the view is yours from then on and is not turned back.
 */
final class View {
    // Render thread only.
    private static boolean saved;
    private static float savedYaw;
    private static float savedPitch;
    private static float setYaw;
    private static float setPitch;
    private static long releaseAtMs = -1;

    private View() {}

    /** Snaps your view to this direction, remembering where you were looking first. */
    static void turnTo(LocalPlayer player, float yaw, float pitch) {
        if (!saved || movedByYou(player)) {
            savedYaw = player.getYRot();
            savedPitch = player.getXRot();
            saved = true;
        }
        snap(player, yaw, pitch);
        releaseAtMs = -1;
    }

    /** Turns back to where you were looking after this long, unless you move the mouse first. */
    static void release(long delayMs) {
        if (saved) {
            releaseAtMs = System.nanoTime() / 1_000_000 + delayMs;
        }
    }

    /** Called from the input loop. */
    static void update(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (!saved) {
            return;
        }
        if (player == null || movedByYou(player)) {
            saved = false; // you took the view over
            releaseAtMs = -1;
            return;
        }
        if (releaseAtMs >= 0 && System.nanoTime() / 1_000_000 >= releaseAtMs) {
            snap(player, savedYaw, savedPitch);
            saved = false;
            releaseAtMs = -1;
        }
    }

    /**
     * Sets the view in one step: the turn is taken the short way round from where you face,
     * and the previous-frame angles are set too, so it does not sweep across the screen.
     */
    private static void snap(LocalPlayer player, float yaw, float pitch) {
        float newYaw = player.getYRot() + Mth.wrapDegrees(yaw - player.getYRot());
        float newPitch = Mth.clamp(pitch, -90.0F, 90.0F);
        player.setYRot(newYaw);
        player.setXRot(newPitch);
        player.yRotO = newYaw;
        player.xRotO = newPitch;
        setYaw = newYaw;
        setPitch = newPitch;
    }

    private static boolean movedByYou(LocalPlayer player) {
        return Math.abs(player.getYRot() - setYaw) > 0.01F || Math.abs(player.getXRot() - setPitch) > 0.01F;
    }

    /** Forgets the remembered view when this injection is unloaded. */
    static void reset() {
        saved = false;
        releaseAtMs = -1;
    }
}
