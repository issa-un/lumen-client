package app;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Clutch: when you are knocked off an edge over a drop that would hurt (more than 3 blocks,
 * or the void), builds you a block to land on, one block per game tick (the fastest
 * anyone can click), so you land on it instead of falling.
 *
 * First choice: a straight bridge out from the block you fell off, at its level, toward
 * where you will come down through that level (along the longer way first, then the
 * shorter, so at most one turn). Your view steps along that one line instead of jumping
 * between sides. That works whenever the bridge can reach you before you drop below that
 * level, which is the usual knockback case. Otherwise it finds the nearest solid block in
 * reach and places its way to the highest block under your fall it can still finish.
 *
 * It follows your fall with the game's own gravity and air drag (knockback carries you
 * sideways and fades). Nothing more is placed once your path lands on something within 3
 * blocks of fall, which does no damage; at most 6 blocks go down per clutch. It only acts
 * while you are falling: it starts once you are coming down and stops the moment you are
 * not (landed, or knocked up again), handing your view back.
 *
 * Blocks come from your hotbar (the one in your hand first): full solid blocks only,
 * nothing that falls (sand, gravel) or does something when clicked. Your view really
 * snaps onto each face it clicks (only faces you can see), and back to where you were
 * looking once it stops; it never clicks a block that reacts to use. With When on After a
 * hit, it only starts if you were hit in the last 1.5 s (pushed off); Any fall also catches
 * walking off an edge. MLG Water still catches a fall it cannot.
 */
final class Clutch {
    private static final String[] WHEN = {"After a hit", "Any fall"};
    // Falls of up to 3 blocks do no damage.
    private static final double MIN_DROP = 3.0;
    private static final double SCAN_DEPTH = 64;
    private static final long HIT_WINDOW_MS = 1500;
    private static final long TIMEOUT_MS = 2000;
    // Blocks placed in a row from a support to the landing block, at most.
    private static final int MAX_CHAIN = 4;
    private static final int MAX_BLOCKS = 6;
    // How far ahead your fall is followed, in game ticks (about 6 blocks of fall).
    private static final int FOLLOW_TICKS = 20;
    // A fall of up to this much does no damage.
    private static final double SAFE_FALL = 3.0;
    // Half the width of your body.
    private static final double HALF_WIDTH = 0.3;
    // How long the view stays on the last block before turning back, so you see it.
    private static final long VIEW_HOLD_MS = 100;
    private static final Direction[] SUPPORT_ORDER = {
        Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP};
    private static volatile boolean enabled;
    private static volatile int when;
    // Render thread only.
    private static int lastHurtTime;
    private static long lastHitMs = Long.MIN_VALUE / 2;
    private static boolean clutching;
    private static long startedMs;
    private static int lastPlaceTick = -1;
    private static int placed;
    private static BlockPos goal;
    // The block you last stood on: the clutch builds out from it.
    private static BlockPos lastStoodOn;

    private Clutch() {}

    /** A block to place and the face of its neighbour to click for it. */
    private record Placement(BlockPos block, BlockPos support, Direction face) {}

    /** The next block to place toward a goal, and how many blocks the whole chain takes. */
    private record Chain(Placement next, int blocks) {}

    /** Called from the input loop. */
    static void update(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null || minecraft.level == null) {
            clutching = false;
            return;
        }
        long now = System.nanoTime() / 1_000_000;
        if (player.hurtTime > lastHurtTime) {
            lastHitMs = now;
        }
        lastHurtTime = player.hurtTime;
        if (player.onGround()) {
            lastStoodOn = player.mainSupportingBlockPos.orElse(lastStoodOn);
        }
        if (clutching) {
            continueClutch(minecraft, player, now);
            return;
        }
        if (!enabled || minecraft.screen != null || !SpearSettings.combatAllowed(minecraft)
                || !falling(player) || (when == 0 && now - lastHitMs > HIT_WINDOW_MS)
                || !hurtingDrop(minecraft, player)) {
            return;
        }
        if (blockSlot(minecraft, player) < 0) {
            MinecraftSpearDash.setStatus("Clutch: no blocks in hotbar");
            return;
        }
        clutching = true;
        startedMs = now;
        placed = 0;
        lastPlaceTick = -1;
        goal = null;
        MinecraftSpearDash.setStatus("Clutching...");
        continueClutch(minecraft, player, now);
    }

    private static void continueClutch(Minecraft minecraft, LocalPlayer player, long now) {
        if (!falling(player) || minecraft.screen != null || now - startedMs > TIMEOUT_MS) {
            boolean landed = player.onGround();
            SpearLog.log("clutch: " + (landed ? "landed" : "stopped") + " after " + placed + " block"
                    + (placed == 1 ? "" : "s") + ", " + (now - startedMs) + " ms");
            MinecraftSpearDash.setStatus(landed && placed > 0 ? "Clutched" : "Clutch ended");
            clutching = false;
            goal = null;
            View.release(VIEW_HOLD_MS);
            return;
        }
        if (player.tickCount == lastPlaceTick || placed >= MAX_BLOCKS) {
            return;
        }
        Vec3[] path = fallPath(player);
        if (landsSafely(minecraft, player, path)) {
            return;
        }
        int slot = blockSlot(minecraft, player);
        if (slot < 0) {
            return;
        }
        Placement next = bridgeFromEdge(minecraft, player, path);
        if (next == null) {
            if (goal == null || !minecraft.level.getBlockState(goal).canBeReplaced() || !crosses(path, goal)) {
                goal = chooseGoal(minecraft, player, path);
            }
            Chain chain = goal == null ? null : chainTo(minecraft, player, goal);
            if (chain == null) {
                goal = null;
                return;
            }
            next = chain.next();
        }
        lastPlaceTick = player.tickCount;
        if (place(minecraft, player, slot, next)) {
            placed++;
        }
    }

    /**
     * The next block of a straight bridge out from the block you fell off, at its level,
     * toward where you come down through that level; null if there is no such block to
     * build from, or the bridge cannot reach you before you drop below it.
     */
    private static Placement bridgeFromEdge(Minecraft minecraft, LocalPlayer player, Vec3[] path) {
        BlockPos edge = lastStoodOn;
        if (edge == null || minecraft.level.getBlockState(edge).canBeReplaced()
                || edge.distToCenterSqr(player.position()) > 36) {
            return null;
        }
        double top = edge.getY() + 1;
        int crossTick = -1;
        for (int tick = 1; tick < path.length && crossTick < 0; tick++) {
            if (path[tick - 1].y >= top && path[tick].y < top) {
                crossTick = tick;
            }
        }
        if (crossTick < 0) {
            return null; // already below its level
        }
        int dx = Mth.floor(path[crossTick].x) - edge.getX();
        int dz = Mth.floor(path[crossTick].z) - edge.getZ();
        boolean xFirst = Math.abs(dx) >= Math.abs(dz);
        BlockPos previous = edge;
        Placement next = null;
        int missing = 0;
        for (int leg = 0; leg < 2; leg++) {
            boolean alongX = (leg == 0) == xFirst;
            int steps = alongX ? dx : dz;
            Direction step = alongX ? (steps > 0 ? Direction.EAST : Direction.WEST)
                    : (steps > 0 ? Direction.SOUTH : Direction.NORTH);
            for (int i = 0; i < Math.abs(steps); i++) {
                BlockPos cell = previous.relative(step);
                if (minecraft.level.getBlockState(cell).canBeReplaced()) {
                    if (next == null) {
                        if (!open(minecraft, player, cell) || !clickable(minecraft, player, previous)
                                || !facesEye(player, previous, step)) {
                            return null;
                        }
                        next = new Placement(cell, previous, step);
                    }
                    missing++;
                }
                previous = cell;
            }
        }
        // One block per tick, and the last one has to be there before you come down through it.
        return next != null && missing <= crossTick && placed + missing <= MAX_BLOCKS ? next : null;
    }

    /**
     * Where your feet will be over the next game ticks, in the game's order: move by the
     * current motion, then gravity (0.08) and drag (0.98 down, 0.91 sideways in the air).
     * Index 0 is now.
     */
    private static Vec3[] fallPath(LocalPlayer player) {
        Vec3[] path = new Vec3[FOLLOW_TICKS + 1];
        Vec3 position = player.position();
        Vec3 motion = player.getDeltaMovement();
        path[0] = position;
        for (int tick = 1; tick <= FOLLOW_TICKS; tick++) {
            position = position.add(motion);
            motion = new Vec3(motion.x * 0.91, (motion.y - 0.08) * 0.98, motion.z * 0.91);
            path[tick] = position;
        }
        return path;
    }

    /** Whether your path comes down on something (placed or not) without a damaging fall. */
    private static boolean landsSafely(Minecraft minecraft, LocalPlayer player, Vec3[] path) {
        double feetNow = player.getY();
        for (int tick = 1; tick < path.length; tick++) {
            double from = path[tick - 1].y;
            double to = path[tick].y;
            if (to >= from) {
                continue; // still going up
            }
            // The slice your feet sweep through this tick.
            AABB feet = new AABB(path[tick].x - HALF_WIDTH, to, path[tick].z - HALF_WIDTH,
                    path[tick].x + HALF_WIDTH, from, path[tick].z + HALF_WIDTH);
            if (!minecraft.level.noCollision(player, feet)) {
                return player.fallDistance + (feetNow - to) <= SAFE_FALL;
            }
        }
        return false;
    }

    /** Whether your path comes down through the top of this block with your body over it. */
    private static boolean crosses(Vec3[] path, BlockPos block) {
        double top = block.getY() + 1;
        for (int tick = 1; tick < path.length; tick++) {
            if (path[tick - 1].y >= top && path[tick].y < top) {
                return Math.abs(path[tick].x - (block.getX() + 0.5)) < 0.5 + HALF_WIDTH
                        && Math.abs(path[tick].z - (block.getZ() + 0.5)) < 0.5 + HALF_WIDTH;
            }
        }
        return false;
    }

    /**
     * The block to land on: the highest one under your path that can be built before you
     * reach it, one block per tick; failing that, the one needing the fewest blocks.
     */
    private static BlockPos chooseGoal(Minecraft minecraft, LocalPlayer player, Vec3[] path) {
        BlockPos fewest = null;
        int fewestBlocks = Integer.MAX_VALUE;
        BlockPos last = null;
        for (int tick = 1; tick < path.length; tick++) {
            // The block under your feet as this tick starts, in the column you will be in.
            BlockPos candidate = BlockPos.containing(path[tick].x, path[tick - 1].y, path[tick].z).below();
            if (candidate.equals(last) || path[tick].y >= candidate.getY() + 1) {
                continue; // same as before, or you do not reach its top this tick
            }
            last = candidate;
            Chain chain = chainTo(minecraft, player, candidate);
            if (chain == null) {
                continue;
            }
            if (chain.blocks() <= tick && chain.blocks() <= MAX_BLOCKS - placed) {
                return candidate;
            }
            if (chain.blocks() < fewestBlocks) {
                fewest = candidate;
                fewestBlocks = chain.blocks();
            }
        }
        return fewestBlocks <= MAX_BLOCKS - placed ? fewest : null;
    }

    /**
     * The next block toward the target: a search outward from it, through empty blocks in
     * reach, for the nearest one with a solid block beside it to click. Placing that one
     * brings the chain one block closer.
     */
    private static Chain chainTo(Minecraft minecraft, LocalPlayer player, BlockPos target) {
        if (!open(minecraft, player, target)) {
            return null;
        }
        Map<BlockPos, Integer> depth = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        depth.put(target, 0);
        queue.add(target);
        while (!queue.isEmpty()) {
            BlockPos node = queue.poll();
            int nodeDepth = depth.get(node);
            for (Direction side : SUPPORT_ORDER) {
                BlockPos support = node.relative(side);
                if (clickable(minecraft, player, support) && facesEye(player, support, side.getOpposite())) {
                    return new Chain(new Placement(node, support, side.getOpposite()), nodeDepth + 1);
                }
            }
            if (nodeDepth + 1 >= MAX_CHAIN) {
                continue;
            }
            for (Direction side : Direction.values()) {
                BlockPos next = node.relative(side);
                if (!depth.containsKey(next) && open(minecraft, player, next)) {
                    depth.put(next, nodeDepth + 1);
                    queue.add(next);
                }
            }
        }
        return null;
    }

    /** In the air on your own and coming down. */
    private static boolean falling(LocalPlayer player) {
        return airborne(player) && player.getDeltaMovement().y < 0;
    }

    /** In the air on your own: not standing, swimming, climbing, flying or gliding. */
    private static boolean airborne(LocalPlayer player) {
        return !player.onGround() && !player.isInWater() && !player.isInLava() && !player.onClimbable()
                && !player.getAbilities().flying && !player.isFallFlying() && !player.isPassenger();
    }

    /** Whether the ground straight below is far enough down to hurt (or missing); water is safe. */
    private static boolean hurtingDrop(Minecraft minecraft, LocalPlayer player) {
        Vec3 feet = player.position();
        BlockHitResult below = minecraft.level.clip(new ClipContext(feet, feet.subtract(0, SCAN_DEPTH, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
        if (below.getType() == HitResult.Type.MISS) {
            return true; // the void, or a very long fall
        }
        if (minecraft.level.getFluidState(below.getBlockPos()).is(FluidTags.WATER)) {
            return false;
        }
        return feet.y - below.getLocation().y > MIN_DROP;
    }

    /** An empty block in reach that a block could go in: not where you are. */
    private static boolean open(Minecraft minecraft, LocalPlayer player, BlockPos pos) {
        return minecraft.level.getBlockState(pos).canBeReplaced()
                && !new AABB(pos).intersects(player.getBoundingBox())
                && player.isWithinBlockInteractionRange(pos, 0.0);
    }

    /** A solid block in reach whose face can be clicked to place against it. */
    private static boolean clickable(Minecraft minecraft, LocalPlayer player, BlockPos pos) {
        Block block = minecraft.level.getBlockState(pos).getBlock();
        return !minecraft.level.getBlockState(pos).canBeReplaced()
                && player.isWithinBlockInteractionRange(pos, 0.0)
                && !WebTrap.reactsToUse(block);
    }

    /** Whether that face of the block points toward your eyes, so you could see and click it. */
    private static boolean facesEye(LocalPlayer player, BlockPos block, Direction face) {
        Vec3 fromFace = player.getEyePosition().subtract(Vec3.atCenterOf(block)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5));
        return fromFace.x * face.getStepX() + fromFace.y * face.getStepY() + fromFace.z * face.getStepZ() > 0;
    }

    /** The hotbar slot with blocks to build with: the one in your hand first. */
    private static int blockSlot(Minecraft minecraft, LocalPlayer player) {
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (buildable(minecraft, inventory.getItem(selected))) {
            return selected;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (buildable(minecraft, inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean buildable(Minecraft minecraft, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) {
            return false;
        }
        Block block = item.getBlock();
        return !(block instanceof FallingBlock) && !WebTrap.reactsToUse(block)
                && block.defaultBlockState().isCollisionShapeFullBlock(minecraft.level, BlockPos.ZERO);
    }

    /**
     * Snaps your view onto the support's face, clicks it with the block where the crosshair
     * lands, then swaps back; false if the face is hidden from here.
     */
    private static boolean place(Minecraft minecraft, LocalPlayer player, int slot, Placement placement) {
        float[] aim = BucketUse.aimAtFace(minecraft, player, placement.support(), placement.face());
        if (aim == null) {
            return false;
        }
        View.turnTo(player, aim[0], aim[1]);
        BlockHitResult click = BucketUse.ray(minecraft, player, player.getYRot(), player.getXRot(),
                ClipContext.Fluid.NONE);
        if (click.getType() != HitResult.Type.BLOCK || !click.getBlockPos().equals(placement.support())) {
            return false;
        }
        Inventory inventory = player.getInventory();
        int previousSlot = inventory.getSelectedSlot();
        InteractionResult result;
        try {
            inventory.setSelectedSlot(slot);
            result = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, click);
            if (result instanceof InteractionResult.Success success
                    && success.swingSource() == InteractionResult.SwingSource.CLIENT) {
                player.swing(InteractionHand.MAIN_HAND);
            }
        } finally {
            inventory.setSelectedSlot(previousSlot);
            MinecraftSpearDash.sendSelectedSlot(minecraft, false);
        }
        MinecraftInputController.stopGuard();
        return result.consumesAction();
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

    static String whenLabel() {
        return WHEN[when];
    }

    static int when() {
        return when;
    }

    static void setWhen(int newWhen) {
        when = Math.floorMod(newWhen, WHEN.length);
    }

    static void cycleWhen() {
        when = (when + 1) % WHEN.length;
    }

    /** Turns the module off when this injection is unloaded. */
    static void reset() {
        enabled = false;
        clutching = false;
        goal = null;
        lastStoodOn = null;
    }
}
