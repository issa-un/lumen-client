package app;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.player.LocalPlayer;

/**
 * One mouse flick the way a hand makes it, from what is known about human aiming:
 * - Fitts's law: the flick takes longer the farther and the smaller the target
 *   (about 70 ms + 90 ms per bit of log2(distance / width + 1)), a little different each time.
 * - Minimum jerk: speed rises and falls in a bell curve, slow start, fast middle, slow end,
 *   never a constant-speed slide.
 * - Submovements: the main flick lands short of the target most of the time (about 95% of the
 *   way) and past it now and then; the module makes a smaller correcting flick after a pause.
 * - The path bows a little to one side instead of a straight line.
 * - Hand tremor: a tiny jitter on top that never adds up to drift.
 * It turns the view with Entity.turn, like the mouse does, so your own mouse movement still
 * adds on top. One per module; render thread only.
 */
final class HumanAim {
    private static final double DEGREES_PER_TURN = 0.15;
    private static final float TREMOR_DEGREES = 0.025F;
    private float yaw;
    private float pitch;
    private float curve;
    private float doneYaw;
    private float donePitch;
    private float tremorYaw;
    private float tremorPitch;
    private long startMs;
    private long durationMs;
    private boolean active;

    boolean active() {
        return active;
    }

    void stop() {
        active = false;
    }

    /**
     * Starts a flick of this many degrees towards a target this many degrees wide. With
     * overshoot off it may still land short but never past (for turns that must end exactly).
     */
    void flick(float yawDegrees, float pitchDegrees, float widthDegrees, boolean overshoot) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float distance = (float) Math.hypot(yawDegrees, pitchDegrees);
        if (distance < 0.05F) {
            active = false;
            return;
        }
        double bits = Math.log(distance / Math.max(0.5F, widthDegrees) + 1) / Math.log(2);
        durationMs = Math.round((70 + 90 * bits) * random.nextDouble(0.85, 1.15));
        double reach = 0.95 + random.nextGaussian() * 0.04;
        reach = overshoot ? Math.min(1.08, Math.max(0.85, reach)) : Math.min(1.0, Math.max(0.88, reach));
        yaw = (float) (yawDegrees * reach);
        pitch = (float) (pitchDegrees * reach);
        curve = (float) (distance * random.nextDouble(-0.08, 0.08));
        doneYaw = 0;
        donePitch = 0;
        startMs = System.nanoTime() / 1_000_000;
        active = true;
    }

    /** A flick that ends exactly where asked (turning back to where you were looking). */
    void flickExact(float yawDegrees, float pitchDegrees) {
        flick(yawDegrees, pitchDegrees, 3.0F, false);
        yaw = yawDegrees;
        pitch = pitchDegrees;
    }

    /** Moves the view along the flick; true once it is done. */
    boolean step(LocalPlayer player) {
        if (!active) {
            return true;
        }
        float t = Math.min(1.0F, (System.nanoTime() / 1_000_000 - startMs) / (float) Math.max(1, durationMs));
        float s = t * t * t * (10 + t * (-15 + 6 * t)); // minimum jerk
        float bow = (float) Math.sin(Math.PI * t) * curve;
        float length = (float) Math.hypot(yaw, pitch);
        float wantYaw = yaw * s + (length > 0 ? -pitch / length * bow : 0);
        float wantPitch = pitch * s + (length > 0 ? yaw / length * bow : 0);
        float moveYaw = wantYaw - doneYaw;
        float movePitch = wantPitch - donePitch;
        doneYaw = wantYaw;
        donePitch = wantPitch;
        if (t < 1.0F) {
            // Tremor: each step adds the change in a small wobble, so it cancels out over time.
            ThreadLocalRandom random = ThreadLocalRandom.current();
            float newYaw = tremorYaw + (float) ((random.nextGaussian() * TREMOR_DEGREES - tremorYaw) * 0.5);
            float newPitch = tremorPitch + (float) ((random.nextGaussian() * TREMOR_DEGREES - tremorPitch) * 0.5);
            moveYaw += newYaw - tremorYaw;
            movePitch += newPitch - tremorPitch;
            tremorYaw = newYaw;
            tremorPitch = newPitch;
        } else {
            moveYaw -= tremorYaw;
            movePitch -= tremorPitch;
            tremorYaw = 0;
            tremorPitch = 0;
            active = false;
        }
        if (moveYaw != 0 || movePitch != 0) {
            player.turn(moveYaw / DEGREES_PER_TURN, movePitch / DEGREES_PER_TURN);
        }
        return !active;
    }
}
