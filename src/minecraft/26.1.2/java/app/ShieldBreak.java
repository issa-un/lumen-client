package app;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shield Break: on your bind, if the target under your crosshair is holding up a shield
 * toward you, swaps to a weapon that disables shields (an axe) and hits the shield up to
 * Axe hits times, one game tick apart, then with Mace follow-up hits with the mace and
 * swaps back. Each step can first wait a random time in its delay's range (Swap delay,
 * Hit delay, Between hits, Axe to mace, Swap back); at 0 they all go at once. The axe disables the shield for its weapon's time (5 s for axes), and the
 * blocked hit leaves the target's damage immunity at 0 damage, so the mace hit lands in
 * full; the mace's fall bonus is not cut by the attack cooldown. Extra axe hits are
 * spread over ticks because hits in one tick cannot catch a shield the first one missed;
 * as soon as the shield shows as down, the remaining axe hits are skipped for the mace.
 * With On attack click, your normal attack click is the trigger too: on a blocking
 * target it runs the shield break instead of the plain hit, otherwise it is a normal hit.
 * Your press or click is always the trigger; nothing fires on its own. With Trigger on Any
 * attack, every attack your client sends counts as that click (see AttackHook).
 */
final class ShieldBreak {
    // 0 is Auto: extra hits only when your ping is low enough for them to be skipped
    // when the first already worked.
    private static final String[] HIT_COUNTS = {"Auto", "1", "2", "3"};
    private static final int AUTO_LOW_PING_MS = 40;
    private static final int AUTO_LOW_PING_HITS = 3;
    // The whole press gives up after this long (target gone, lag spike).
    private static final long SEQUENCE_TIMEOUT_MS = 500;
    // A shield blocks hits within this horizontal angle of its holder's head (the shield's
    // damage reduction; LivingEntity.applyItemBlocking).
    private static final double BLOCKING_ANGLE = Math.toRadians(90);
    // After a break your client keeps showing the old shield until the server's update
    // arrives. Until the shield is seen dropping, or this long past your ping, that player
    // does not count as blocking; after that a shield is a real one (the break missed, or
    // it was raised again) and can be broken.
    private static final long CONFIRM_MARGIN_MS = 150;
    private static final long CONFIRM_MAX_MS = 600;
    // How long to watch for the shield going down before counting a break as missed.
    private static final long RESULT_WAIT_MS = 800;
    private static volatile boolean enabled;
    private static volatile int keyCode = 82; // R
    private static volatile int mouseButton = -1;
    private static volatile int axeHits = 0;
    private static volatile boolean maceFollowUp = true;
    private static volatile boolean onAttackClick = true;
    private static volatile boolean anyAttack;
    private static volatile boolean awaitingBinding;
    // Range: Normal is the axe's and mace's own 3 blocks; Extended reaches 4.5 blocks, past
    // your crosshair. The server takes melee hits up to a weapon's reach + 3 blocks.
    private static final String[] RANGES = {"Normal (3)", "4.5 blocks"};
    private static final double EXTENDED_EXTRA = 1.5;
    private static volatile int range;
    // This press is a ranged one: no delays (render thread only).
    private static boolean instant;
    // Click -> axe selected, item selected -> its hit (axe and mace), axe hit -> next axe
    // hit, last axe hit -> mace selected, last hit -> back to your slot.
    static final Num SWAP_DELAY = Num.ms(0, 0, 300);
    static final Num HIT_DELAY = Num.ms(0, 0, 300);
    static final Num BETWEEN_HITS = Num.ms(0, 0, 300);
    static final Num AXE_TO_MACE = Num.ms(0, 0, 500);
    static final Num SWAP_BACK = Num.ms(0, 0, 500);
    // Of your attack clicks on a blocking target; the bind always fires.
    static final Num CHANCE = Num.percent(100);
    // The steps of a press; each waits for its delay (dueMs).
    private static final int SWAP = 0;
    private static final int AXE = 1;
    private static final int MACE_SWAP = 2;
    private static final int MACE_HIT = 3;
    private static final int BACK = 4;
    // The press in progress, set only on the render thread (AttackHook reads it).
    private static volatile LivingEntity target;
    private static int phase;
    private static long dueMs;
    private static int heldSlot = -1;
    private static boolean maceHit;
    private static int axeSlot = -1;
    private static int maceSlot = -1;
    private static int previousSlot = -1;
    private static int hitsDone;
    private static int hitsWanted;
    private static int lastHitTick;
    private static long startedMs;
    // The last finished break: not treated as blocking again at once, and watched to see
    // whether its shield went down.
    private static LivingEntity lastBroken;
    private static long lastBrokenMs;
    private static boolean sawShieldDrop;
    private static boolean watchingResult;
    private static int breaksTried;
    private static int breaksLanded;

    private ShieldBreak() {}

    /** Runs a shield break from its bind or an attack click; true if it started. */
    static boolean activate(Minecraft minecraft) {
        return activate(minecraft, blockingTarget(minecraft));
    }

    /** Runs a shield break on a target found blocking toward you (null if none). */
    private static boolean activate(Minecraft minecraft, LivingEntity blocking) {
        LocalPlayer player = minecraft.player;
        if (!enabled || player == null || minecraft.gameMode == null || target != null) {
            return false;
        }
        if (!SpearSettings.combatAllowed(minecraft)) {
            MinecraftSpearDash.setStatus("Off on this server");
            return false;
        }
        if (blocking == null) {
            MinecraftSpearDash.setStatus("Not blocking");
            return false;
        }
        int axe = findSlot(player, ShieldBreak::disablesShields);
        if (axe < 0) {
            MinecraftSpearDash.setStatus("No axe in hotbar");
            return false;
        }
        if (!inRange(player, axe, blocking)) {
            MinecraftSpearDash.setStatus("Too far for the axe");
            return false;
        }
        // Past normal reach the hits only register when the axe, the mace and the swap back
        // all go out together, so a ranged break skips every delay.
        instant = !player.isWithinAttackRange(player.getInventory().getItem(axe), blocking.getBoundingBox(), 0);
        target = blocking;
        axeSlot = axe;
        // Density for a slam, else Breach, else any mace: the same pick as Mace Swap.
        maceSlot = maceFollowUp ? MaceSwap.bestMace(player) : -1;
        previousSlot = player.getInventory().getSelectedSlot();
        heldSlot = previousSlot;
        hitsDone = 0;
        // Extra axe hits wait a game tick each, which a ranged break cannot.
        hitsWanted = instant ? 1 : effectiveAxeHits(minecraft);
        maceHit = false;
        startedMs = nowMs();
        phase = SWAP;
        dueMs = startedMs + delay(SWAP_DELAY);
        advance(minecraft);
        return true;
    }

    /**
     * For your attack click, with On attack click on: true means the click started (or
     * belongs to) a shield break and the game must not also turn it into a normal hit.
     * Clicks on anything that is not blocking toward you stay normal attacks.
     */
    static boolean handleAttackClick(Minecraft minecraft) {
        if (!enabled || !onAttackClick || minecraft.player == null || minecraft.screen != null
                || !SpearSettings.combatAllowed(minecraft)) {
            return false;
        }
        // Any attack catches attack packets, but a click past your crosshair's reach sends
        // none, so with Range on 4.5 blocks those clicks are taken here.
        if (anyAttack && (range == 0 || minecraft.hitResult instanceof EntityHitResult)) {
            return false;
        }
        if (target != null) {
            return true; // extra clicks during a break would hit with whatever is selected
        }
        return blockingTarget(minecraft) != null && CHANCE.roll() && activate(minecraft);
    }

    /**
     * Any attack your client sends, with Trigger on Any attack, on the network thread (see
     * AttackHook): true if it starts a shield break, so the plain hit is held back. During a
     * break every other attack is held back too, like extra clicks.
     */
    static boolean takesAnyAttack(Minecraft minecraft, LivingEntity entity) {
        if (!enabled || !onAttackClick || !anyAttack || minecraft.screen != null) {
            return false;
        }
        return target != null || wouldBreak(minecraft, entity);
    }

    /** A held-back attack, on the render thread: the shield break, or the attack after all. */
    static void runFromAttack(Minecraft minecraft, LivingEntity entity, ServerboundAttackPacket attack) {
        if (target != null) {
            return; // an extra hit during a break, dropped like an extra click
        }
        if (!CHANCE.roll() || !activate(minecraft, blockingToward(minecraft, entity) ? entity : null)) {
            AttackHook.resend(minecraft, attack);
        }
    }

    /** Whether an attack on the entity would start a shield break: blocking, axe in reach. */
    static boolean wouldBreak(Minecraft minecraft, LivingEntity entity) {
        LocalPlayer player = minecraft.player;
        if (!enabled || !onAttackClick || player == null || minecraft.gameMode == null
                || !SpearSettings.combatAllowed(minecraft) || !blockingToward(minecraft, entity)) {
            return false;
        }
        int axe = findSlot(player, ShieldBreak::disablesShields);
        return axe >= 0 && inRange(player, axe, entity);
    }

    /**
     * Called from the input loop: the next step of the press once its delay is over. Axe
     * hits also wait for a new game tick, and stop early once the shield shows as down.
     */
    static void update(Minecraft minecraft) {
        watchResult();
        if (target == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            abort(minecraft, "Shield break cancelled");
            return;
        }
        if (player.getInventory().getSelectedSlot() != heldSlot) {
            // You picked another slot mid-press; leave your choice alone.
            target = null;
            MinecraftSpearDash.setStatus("Shield break stopped");
            return;
        }
        advance(minecraft);
    }

    /** Runs every step that is due; with all delays at 0 the whole press runs at once. */
    private static void advance(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        while (target != null) {
            long now = nowMs();
            if (now < dueMs) {
                return;
            }
            switch (phase) {
                case SWAP -> {
                    select(minecraft, axeSlot);
                    phase = AXE;
                    dueMs = now + delay(HIT_DELAY);
                }
                case AXE -> {
                    if (hitsDone > 0 && player.tickCount <= lastHitTick) {
                        return; // the next axe hit goes in a later game tick
                    }
                    boolean timedOut = now - startedMs > SEQUENCE_TIMEOUT_MS + SWAP_DELAY.high() + HIT_DELAY.high()
                            + 2L * BETWEEN_HITS.high();
                    boolean shieldDown = hitsDone > 0 && !target.isBlocking();
                    if (timedOut || shieldDown || !target.isAlive() || !inRange(player, axeSlot, target)
                            || hitsDone >= hitsWanted) {
                        if (hitsDone == 0) {
                            abort(minecraft, "Target gone");
                            return;
                        }
                        phase = MACE_SWAP;
                        dueMs = now + delay(AXE_TO_MACE);
                    } else {
                        axeHit(minecraft);
                        if (hitsDone < hitsWanted) {
                            dueMs = now + delay(BETWEEN_HITS);
                        } else {
                            phase = MACE_SWAP;
                            dueMs = now + delay(AXE_TO_MACE);
                        }
                    }
                }
                case MACE_SWAP -> {
                    if (maceSlot >= 0 && target.isAlive() && inRange(player, maceSlot, target)) {
                        select(minecraft, maceSlot);
                        phase = MACE_HIT;
                        dueMs = now + delay(HIT_DELAY);
                    } else {
                        phase = BACK;
                        dueMs = now + delay(SWAP_BACK);
                    }
                }
                case MACE_HIT -> {
                    if (target.isAlive() && inRange(player, maceSlot, target)) {
                        hit(minecraft);
                        maceHit = true;
                    }
                    phase = BACK;
                    dueMs = now + delay(SWAP_BACK);
                }
                default -> finish(minecraft);
            }
        }
    }

    private static void axeHit(Minecraft minecraft) {
        hit(minecraft);
        hitsDone++;
        lastHitTick = minecraft.player.tickCount;
    }

    /** The swap back and the bookkeeping, once every hit is done. */
    private static void finish(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        int lastSlot = heldSlot;
        swapBack(minecraft);
        MinecraftInputController.guardSwitchBack(previousSlot, lastSlot);
        String axePart = hitsDone == 1 ? "Shield hit" : "Shield hit x" + hitsDone;
        MinecraftSpearDash.setStatus(maceHit ? axePart + " + mace" : axePart);
        lastBroken = target;
        lastBrokenMs = nowMs();
        sawShieldDrop = false;
        watchingResult = true;
        breaksTried++;
        SpearLog.log("shield break: hotbar " + (previousSlot + 1) + " -> axe " + (axeSlot + 1)
                + " x" + hitsDone + (maceHit ? " -> mace " + (maceSlot + 1) : "") + " -> back to "
                + (player.getInventory().getSelectedSlot() + 1) + " on " + target.getName().getString()
                + " (" + (lastBrokenMs - startedMs) + " ms)");
        target = null;
    }

    private static void abort(Minecraft minecraft, String reason) {
        if (minecraft.player != null) {
            swapBack(minecraft);
        }
        target = null;
        MinecraftSpearDash.setStatus(reason);
    }

    private static void swapBack(Minecraft minecraft) {
        select(minecraft, previousSlot);
    }

    private static void select(Minecraft minecraft, int slot) {
        minecraft.player.getInventory().setSelectedSlot(slot);
        MinecraftSpearDash.sendSelectedSlot(minecraft, false);
        heldSlot = slot;
    }

    /** Attacks with the selected slot, in the same order as a normal left-click. */
    private static void hit(Minecraft minecraft) {
        AttackHook.markOwn(target.getId());
        minecraft.gameMode.attack(minecraft.player, target);
        minecraft.player.swing(InteractionHand.MAIN_HAND);
    }

    /** A step's delay for this press: none for a ranged break. */
    private static long delay(Num num) {
        return instant ? 0 : num.pick();
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000;
    }

    /** Counts whether the last break's shield went down; your client sees it after your ping. */
    private static void watchResult() {
        if (!watchingResult) {
            return;
        }
        long afterMs = nowMs() - lastBrokenMs;
        if (!lastBroken.isBlocking()) {
            watchingResult = false;
            sawShieldDrop = true;
            breaksLanded++;
            MinecraftSpearDash.setStatus("Shield down (" + breaksLanded + "/" + breaksTried + ")");
            SpearLog.log("shield break result: shield went down " + afterMs + " ms after the break");
        } else if (afterMs > RESULT_WAIT_MS) {
            watchingResult = false;
            MinecraftSpearDash.setStatus("Shield stayed up (" + breaksLanded + "/" + breaksTried + ")");
            SpearLog.log("shield break result: shield still up " + afterMs + " ms after the break");
        }
    }

    /**
     * The target under the crosshair, if its shield would block your hit: it is blocking,
     * and you are within 90 degrees of where its head faces, measured flat. That is the
     * game's own test, so being above it (a mace drop) counts the same as being level.
     * A target you just broke does not count until your client has caught up.
     */
    static LivingEntity blockingTarget(Minecraft minecraft) {
        if (minecraft.player == null) {
            return null;
        }
        LivingEntity entity = minecraft.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living ? living : null;
        if (entity == null && range == 1) {
            entity = farTarget(minecraft.player);
        }
        return entity != null && blockingToward(minecraft, entity) ? entity : null;
    }

    /**
     * With Range on 4.5 blocks: the player or mob along your view past your crosshair's
     * reach, up to 4.5 blocks and not through blocks.
     */
    private static LivingEntity farTarget(LocalPlayer player) {
        double reach = 3 + EXTENDED_EXTRA + 1; // a little past, the range test decides
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).scale(reach));
        HitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        if (block.getType() != HitResult.Type.MISS) {
            end = block.getLocation();
        }
        AABB around = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, around,
                entity -> entity instanceof LivingEntity && entity.isPickable() && !entity.isSpectator(),
                eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /** Whether the entity's shield would block your hit; see blockingTarget. */
    static boolean blockingToward(Minecraft minecraft, LivingEntity entity) {
        if (minecraft.player == null) {
            return false;
        }
        int pingMs = pingMs(minecraft);
        if (!blockingWhenHitArrives(entity, pingMs)) {
            return false;
        }
        if (entity == lastBroken && !sawShieldDrop && System.nanoTime() / 1_000_000 - lastBrokenMs
                < Math.min(CONFIRM_MAX_MS, 2L * pingMs + CONFIRM_MARGIN_MS)) {
            return false; // your client still shows the shield the last break hit
        }
        double headYaw = Math.toRadians(entity.getYHeadRot());
        Vec3 facing = new Vec3(-Math.sin(headYaw), 0, Math.cos(headYaw));
        Vec3 offset = minecraft.player.position().subtract(entity.position());
        // Straight overhead normalizes to zero, which the game treats as 90 degrees: blocked.
        Vec3 toAttacker = new Vec3(offset.x, 0, offset.z).normalize();
        double angle = Math.acos(Math.max(-1, Math.min(1, facing.dot(toAttacker))));
        return angle <= BLOCKING_ANGLE;
    }

    /**
     * Whether the server will count the shield as up when your hit gets there. A shield only
     * blocks once it has been raised for its block delay (0.25 s for shields), and the
     * server's count runs about your ping ahead of what you see, so a shield that is still
     * going up on your screen is often already blocking there.
     */
    private static boolean blockingWhenHitArrives(LivingEntity entity, int pingMs) {
        if (entity.isBlocking()) {
            return true;
        }
        if (!entity.isUsingItem()) {
            return false;
        }
        BlocksAttacks blocks = entity.getUseItem().get(DataComponents.BLOCKS_ATTACKS);
        int pingTicks = (pingMs + 49) / 50;
        return blocks != null && entity.getTicksUsingItem() + pingTicks >= blocks.blockDelayTicks();
    }

    /** Your ping as the server reports it in the player list, or 0 if unknown. */
    static int pingMs(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return 0;
        }
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(minecraft.player.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency());
    }

    /** The Axe hits setting, with Auto resolved from your ping. */
    static int effectiveAxeHits(Minecraft minecraft) {
        if (axeHits != 0) {
            return axeHits;
        }
        return pingMs(minecraft) <= AUTO_LOW_PING_MS ? AUTO_LOW_PING_HITS : 1;
    }

    static boolean isRunning() {
        return target != null;
    }

    static boolean hasAxe(Minecraft minecraft) {
        return minecraft.player != null && findSlot(minecraft.player, ShieldBreak::disablesShields) >= 0;
    }

    private static boolean disablesShields(ItemStack stack) {
        Weapon weapon = stack.get(DataComponents.WEAPON);
        return weapon != null && weapon.disableBlockingForSeconds() > 0;
    }

    private static boolean inRange(LocalPlayer player, int slot, LivingEntity entity) {
        return player.isWithinAttackRange(player.getInventory().getItem(slot), entity.getBoundingBox(),
                range == 1 ? EXTENDED_EXTRA : 0);
    }

    private static int findSlot(LocalPlayer player, Predicate<ItemStack> wanted) {
        for (int slot = 0; slot < 9; slot++) {
            if (wanted.test(player.getInventory().getItem(slot))) {
                return slot;
            }
        }
        return -1;
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

    /** 0 for Auto, else 1 to 3. */
    static int axeHits() {
        return axeHits;
    }

    static String axeHitsLabel(Minecraft minecraft) {
        return axeHits == 0
                ? "Auto (" + effectiveAxeHits(minecraft) + ", " + pingMs(minecraft) + "ms)"
                : HIT_COUNTS[axeHits];
    }

    static void setAxeHits(int hits) {
        axeHits = Math.max(0, Math.min(HIT_COUNTS.length - 1, hits));
    }

    static void cycleAxeHits() {
        axeHits = (axeHits + 1) % HIT_COUNTS.length;
    }

    static boolean isOnAttackClick() {
        return onAttackClick;
    }

    static void setOnAttackClick(boolean on) {
        onAttackClick = on;
    }

    static void toggleOnAttackClick() {
        onAttackClick = !onAttackClick;
    }

    static boolean isAnyAttack() {
        return anyAttack;
    }

    static void setAnyAttack(boolean on) {
        anyAttack = on;
    }

    static void toggleAnyAttack() {
        anyAttack = !anyAttack;
    }

    static String triggerLabel() {
        return AttackHook.triggerLabel(anyAttack);
    }

    static boolean isMaceFollowUp() {
        return maceFollowUp;
    }

    static void setMaceFollowUp(boolean on) {
        maceFollowUp = on;
    }

    static void toggleMaceFollowUp() {
        maceFollowUp = !maceFollowUp;
    }

    static int range() {
        return range;
    }

    static String rangeLabel() {
        return RANGES[range];
    }

    static void setRange(int newRange) {
        range = Math.floorMod(newRange, RANGES.length);
    }

    static void cycleRange() {
        range = (range + 1) % RANGES.length;
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

    /** Turns the module off when this injection is unloaded, swapping back mid-press. */
    static void reset(Minecraft minecraft) {
        if (target != null) {
            abort(minecraft, "Ready");
        }
        watchingResult = false;
        lastBroken = null;
        enabled = false;
        awaitingBinding = false;
    }
}
