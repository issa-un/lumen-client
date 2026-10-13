package app;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Edge Sneak: while you walk on the ground (holding blocks, with Only holding blocks on),
 * holds sneak as soon as the way your movement keys push you would take you off the edge
 * of what you stand on, and lets go once it would not. The game's own sneak then stops you
 * at the edge, so you can bridge (walking backwards, placing blocks yourself) without
 * falling off. The direction is read from the movement keys you hold, not from your
 * motion, so the first step toward an edge is already covered. With no movement key held
 * it keeps what it had, so standing still at an edge stays crouched. Your own sneak key
 * always wins, and it does nothing with Toggle Sneak on.
 */
final class EdgeSneak {
    // How far ahead to look, at least, and in game ticks of your current speed.
    private static final double MIN_PROBE = 0.25;
    private static final double PROBE_TICKS = 3;
    private static volatile boolean enabled;
    private static volatile boolean onlyWithBlocks = true;
    // Render thread only.
    private static boolean holding;

    private EdgeSneak() {}

    /** Called from the input loop. */
    static void update(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            holding = false;
            return;
        }
        boolean want = enabled && minecraft.screen == null && SpearSettings.combatAllowed(minecraft)
                && !minecraft.options.toggleCrouch().get() && player.onGround()
                && !player.getAbilities().flying && !player.isPassenger()
                && (!onlyWithBlocks || player.getMainHandItem().getItem() instanceof BlockItem
                        || player.getOffhandItem().getItem() instanceof BlockItem)
                && headingOffEdge(minecraft, player);
        if (want) {
            minecraft.options.keyShift.setDown(true);
            holding = true;
        } else if (holding) {
            release(minecraft);
        }
    }

    /** Whether the movement keys you hold push you toward a drop; unchanged with none held. */
    private static boolean headingOffEdge(Minecraft minecraft, LocalPlayer player) {
        Options options = minecraft.options;
        double forward = impulse(minecraft, options.keyUp, options.keyDown);
        double left = impulse(minecraft, options.keyLeft, options.keyRight);
        if (forward == 0 && left == 0) {
            return holding;
        }
        // The game's own turn of key input into a world direction (Entity.getInputVector).
        double yaw = Math.toRadians(player.getYRot());
        double dx = left * Math.cos(yaw) - forward * Math.sin(yaw);
        double dz = forward * Math.cos(yaw) + left * Math.sin(yaw);
        double length = Math.hypot(dx, dz);
        Vec3 motion = player.getDeltaMovement();
        double probe = Math.max(MIN_PROBE, Math.hypot(motion.x, motion.z) * PROBE_TICKS) / length;
        // The game's own edge test: nothing within a step's height under you once moved there.
        AABB body = player.getBoundingBox();
        AABB below = new AABB(body.minX + dx * probe + 1.0E-7, body.minY - player.maxUpStep() - 1.0E-7,
                body.minZ + dz * probe + 1.0E-7, body.maxX + dx * probe - 1.0E-7, body.minY,
                body.maxZ + dz * probe - 1.0E-7);
        return minecraft.level.noCollision(player, below);
    }

    private static double impulse(Minecraft minecraft, KeyMapping positive, KeyMapping negative) {
        return (WTap.physicallyDown(minecraft, positive) ? 1 : 0) - (WTap.physicallyDown(minecraft, negative) ? 1 : 0);
    }

    private static void release(Minecraft minecraft) {
        minecraft.options.keyShift.setDown(WTap.physicallyDown(minecraft, minecraft.options.keyShift));
        holding = false;
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

    static boolean isOnlyWithBlocks() {
        return onlyWithBlocks;
    }

    static void setOnlyWithBlocks(boolean on) {
        onlyWithBlocks = on;
    }

    static void toggleOnlyWithBlocks() {
        onlyWithBlocks = !onlyWithBlocks;
    }

    /** Turns the module off when this injection is unloaded, giving sneak back. */
    static void reset(Minecraft minecraft) {
        if (holding) {
            release(minecraft);
        }
        enabled = false;
    }
}
