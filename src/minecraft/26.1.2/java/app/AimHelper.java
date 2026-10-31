package app;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Aim Helper: while its key works (hold Left Alt by default), moves your view onto one player
 * you have locked onto, with any item in your hand. It moves like your own aim does in
 * recordings of it, only firmer:
 * - it aims where they will be when your hit arrives (their speed times one tick plus your
 *   ping), and keeps up with them sliding across your view (FOLLOW_AIR of their speed in
 *   the air, FOLLOW_GROUND on the ground, at Strength 50%);
 * - only players within RANGE blocks of you (straight-line) are locked, and the lock drops
 *   the moment they get further;
 * - it leaves the last couple of degrees alone (DEAD_YAW / DEAD_PITCH): that bit is yours;
 * - bigger gaps ease in, CLOSE_NEAR of the gap per tenth of a second at NEAR degrees off,
 *   rising to CLOSE_FAR past FAR, never a snap.
 * It runs every frame, scaled by the time since the last one, so it glides instead of
 * stepping. Strength 50% is the default; 100% follows fully and closes twice as fast.
 * - Air only: nothing happens while you stand on the ground, unless Ground too is on.
 * - Key: Hold (only while held), Toggle (press on, press off) or Always (no key needed).
 * - One target, and no flick back: outside PULL_ANGLE it simply stops.
 * Friends are never locked.
 */
final class AimHelper {
    private static final double RANGE = 6;
    private static final double MAX_LEAD_TICKS = 6;
    private static final double LOCK_ANGLE = 25;
    private static final double PULL_ANGLE = 45;
    private static final long FORGET_MS = 2000;
    private static final double FOLLOW_AIR = 1;
    private static final double FOLLOW_GROUND = 0.8;
    private static final double DEAD_YAW = 1;
    private static final double DEAD_PITCH = 2;
    private static final double CLOSE_NEAR = 0.5;
    private static final double CLOSE_FAR = 0.85;
    private static final double NEAR = 3;
    private static final double FAR = 30;
    private static final double TURN_PER_SECOND = 720;
    private static final double FLICK_PER_SECOND = 1800;
    private static final long LOG_GAP_MS = 1000;
    private static final String[] KEY_MODES = {"Hold", "Toggle", "Always"};
    static final Num STRENGTH = Num.percent(50);
    private static volatile boolean enabled;
    private static volatile boolean ground;
    private static volatile int keyCode = GLFW.GLFW_KEY_LEFT_ALT;
    private static volatile int mouseButton = -1;
    private static volatile boolean awaitingBinding;
    private static volatile int keyMode;
    private static volatile boolean toggledOn;
    // Render thread only.
    private static Player locked;
    private static long lastNearMs;
    private static long lastStepMs;
    private static float lastWantYaw;
    private static float lastWantPitch;
    private static boolean haveLast;
    private static String lastNoteKind = "";
    private static long lastNoteMs;

    private AimHelper() {}

    /**
     * Called every frame by the HUD, and from the input loop for when the HUD is hidden: one
     * step, as big as the time since the last one.
     */
    static void update(Minecraft minecraft) {
        long now = System.nanoTime() / 1_000_000;
        long elapsed = lastStepMs == 0 ? 0 : now - lastStepMs;
        lastStepMs = now;
        LocalPlayer player = minecraft.player;
        if (!enabled || player == null || Mc.screen(minecraft) != null || !SpearSettings.combatAllowed(minecraft)
                || !keyActive(minecraft)) {
            unlock();
            return;
        }
        if (locked != null && (!locked.isAlive() || locked.isRemoved() || !inRange(player, locked)
                || now - lastNearMs > FORGET_MS)) {
            unlock();
        }
        if (player.onGround() && !ground) {
            haveLast = false;
            note("waiting: on the ground (turn on Ground too to help here)", now);
            return;
        }
        if (locked == null) {
            locked = pick(minecraft, player);
            if (locked == null) {
                note("waiting: no player within " + (int) LOCK_ANGLE + " degrees of your crosshair and "
                        + (int) RANGE + " blocks", now);
                return;
            }
            lastNearMs = now;
            note("locked on " + locked.getName().getString(), now);
        }
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 eye = player.getEyePosition(partial);
        float[] want = angles(eye, predicted(minecraft, locked, partial));
        // How far they slid across your view since the last step.
        double slideYaw = haveLast ? Mth.wrapDegrees(want[0] - lastWantYaw) : 0;
        double slidePitch = haveLast ? want[1] - lastWantPitch : 0;
        lastWantYaw = want[0];
        lastWantPitch = want[1];
        haveLast = true;
        double yawOff = Mth.wrapDegrees(want[0] - player.getYRot());
        double pitchOff = want[1] - player.getXRot();
        double off = Math.hypot(yawOff, pitchOff);
        if (off > PULL_ANGLE) {
            note("not helping: " + locked.getName().getString() + " is " + (int) off + " degrees away (helps within "
                    + (int) PULL_ANGLE + ")", now);
            return; // out of the zone: no pull, and never a flick back
        }
        lastNearMs = now;
        double seconds = Math.min(0.1, elapsed / 1000.0);
        double strength = STRENGTH.low() / 50.0;
        double follow = Math.min(1.2, (player.onGround() ? FOLLOW_GROUND : FOLLOW_AIR) * strength);
        double stepYaw = slideYaw * follow + correct(yawOff, DEAD_YAW, strength, seconds);
        double stepPitch = slidePitch * follow + correct(pitchOff, DEAD_PITCH, strength, seconds);
        double limit = (off > FAR ? FLICK_PER_SECOND : TURN_PER_SECOND) * strength * seconds;
        stepYaw = Mth.clamp(stepYaw, -limit, limit);
        stepPitch = Mth.clamp(stepPitch, -limit, limit);
        float pitch = Mth.clamp(player.getXRot() + (float) stepPitch, -90F, 90F);
        float turnedPitch = pitch - player.getXRot();
        // Moved like a mouse turn (Entity.turn): the previous rotation goes along, so the
        // step shows at once instead of being blended in over the next frames.
        player.setYRot(player.getYRot() + (float) stepYaw);
        player.setXRot(pitch);
        player.yRotO += (float) stepYaw;
        player.xRotO += turnedPitch;
        note(String.format(java.util.Locale.ROOT, "helping onto %s: %.0f degrees off, turned %.1f",
                locked.getName().getString(), off, Math.hypot(stepYaw, turnedPitch)), now);
    }

    /**
     * The part of a gap you would close this step: nothing inside the dead zone, then a share
     * of what is beyond it that grows from CLOSE_NEAR at NEAR degrees to CLOSE_FAR at FAR.
     */
    private static double correct(double gap, double dead, double strength, double seconds) {
        double beyond = Math.abs(gap) - dead;
        if (beyond <= 0) {
            return 0;
        }
        double share = Mth.lerp(Mth.clamp((Math.abs(gap) - NEAR) / (FAR - NEAR), 0, 1), CLOSE_NEAR, CLOSE_FAR);
        // The shares are per tenth of a second; compounded over this step's time, so many
        // short frames close the same amount as a few long ones.
        double perTenth = Math.min(0.95, share * strength);
        double part = 1 - Math.pow(1 - perTenth, seconds * 10);
        return Math.signum(gap) * beyond * part;
    }

    /** Whether the key lets the helper work right now. */
    private static boolean keyActive(Minecraft minecraft) {
        return switch (keyMode) {
            case 1 -> toggledOn;
            case 2 -> true;
            default -> mouseButton >= 0
                    ? GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), mouseButton) == GLFW.GLFW_PRESS
                    : keyCode >= 0 && Mc.isKeyDown(minecraft, keyCode);
        };
    }

    /** The key was pressed: in Toggle mode, flips the helper on or off. Never swallows the key. */
    static void onKey(Minecraft minecraft) {
        if (!enabled || keyMode != 1 || Mc.screen(minecraft) != null) {
            return;
        }
        toggledOn = !toggledOn;
        MinecraftSpearDash.setStatus(toggledOn ? "Aim Helper on" : "Aim Helper off");
    }

    private static void unlock() {
        locked = null;
        haveLast = false;
    }

    /** Logs what the helper is doing, when that changes or once a second while it keeps on. */
    private static void note(String what, long now) {
        String kind = what.contains(":") ? what.substring(0, what.indexOf(':')) : what;
        if (!kind.equals(lastNoteKind) || now - lastNoteMs >= LOG_GAP_MS) {
            SpearLog.log("aim helper: " + what);
            lastNoteKind = kind;
            lastNoteMs = now;
        }
    }

    /** The player to lock: the one under your crosshair, else the closest to it within LOCK_ANGLE. */
    private static Player pick(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player under
                && lockable(player, under)) {
            return under;
        }
        Vec3 eye = player.getEyePosition();
        Player best = null;
        double bestOff = LOCK_ANGLE;
        for (Player other : minecraft.level.players()) {
            if (!lockable(player, other)) {
                continue;
            }
            float[] want = angles(eye, aimPoint(eye, other.getBoundingBox()));
            double off = Math.hypot(Mth.wrapDegrees(want[0] - player.getYRot()), want[1] - player.getXRot());
            if (off < bestOff) {
                bestOff = off;
                best = other;
            }
        }
        return best;
    }

    private static boolean lockable(LocalPlayer player, Player other) {
        return other != player && other.isAlive() && !other.isSpectator() && inRange(player, other)
                && !VelocityHud.isFriend(other);
    }

    private static boolean inRange(LocalPlayer player, Player other) {
        return player.distanceTo(other) <= RANGE;
    }

    /**
     * The middle of their hitbox where it will be when your hit lands: drawn smoothly between
     * ticks, then moved ahead by their speed for one tick plus your ping.
     */
    private static Vec3 predicted(Minecraft minecraft, Player target, float partial) {
        Vec3 speed = new Vec3(target.getX() - target.xo, target.getY() - target.yo, target.getZ() - target.zo);
        double lead = Math.min(MAX_LEAD_TICKS, 1 + ShieldBreak.pingMs(minecraft) / 50.0);
        return target.getPosition(partial).add(0, target.getBbHeight() / 2, 0).add(speed.scale(lead));
    }

    /** Their middle (where your recordings were measured to): the center of their hitbox. */
    private static Vec3 aimPoint(Vec3 eye, AABB box) {
        return box.getCenter();
    }

    /** The view {yaw, pitch} from the eye to the point. */
    private static float[] angles(Vec3 eye, Vec3 point) {
        Vec3 to = point.subtract(eye);
        return new float[] {
            (float) Math.toDegrees(Math.atan2(-to.x, to.z)),
            (float) Math.toDegrees(-Math.atan2(to.y, Math.hypot(to.x, to.z)))};
    }

    static boolean isEnabled() {
        return enabled;
    }

    static void setEnabled(boolean on) {
        enabled = on;
    }

    static void toggleEnabled() {
        enabled = !enabled;
    }

    static boolean isGround() {
        return ground;
    }

    static void setGround(boolean on) {
        ground = on;
    }

    static void toggleGround() {
        ground = !ground;
    }

    static String keyModeLabel() {
        return KEY_MODES[keyMode];
    }

    static int keyMode() {
        return keyMode;
    }

    static void setKeyMode(int value) {
        keyMode = Math.floorMod(value, KEY_MODES.length);
        toggledOn = false;
    }

    static void cycleKeyMode() {
        setKeyMode(keyMode + 1);
    }

    static int keyCode() {
        return keyCode;
    }

    static int mouseButton() {
        return mouseButton;
    }

    static void setBinding(int key, int button) {
        keyCode = key;
        mouseButton = button;
    }

    static void bindKeyboard(int key) {
        keyCode = key;
        mouseButton = -1;
        awaitingBinding = false;
    }

    static void bindMouse(int button) {
        mouseButton = button;
        awaitingBinding = false;
    }

    static boolean isAwaitingBinding() {
        return awaitingBinding;
    }

    static void awaitBinding() {
        awaitingBinding = true;
    }

    static void cancelBinding() {
        awaitingBinding = false;
    }

    /** Turns the module off when this injection is unloaded. */
    static void reset() {
        enabled = false;
        toggledOn = false;
        awaitingBinding = false;
        unlock();
    }
}
