package app;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Mace Swap: when your attack click lands on something, it swaps to the right mace for that
 * hit, hits with it, and swaps straight back, all in one step like Spear Dash (or, with
 * Swap delay, Hit delay and Swap back set, with a random pause in that range before each step). Dropping onto
 * them (the hit will be a slam) picks a Density mace, or any mace if you have no Density
 * one, since every mace slams. On the ground, On ground decides: Any mace (a Breach mace if
 * you have one, else any mace), Breach only, or Off.
 * Missed swings and hits you already make with that mace are left alone.
 * With Trigger on Any attack, it acts on every attack your client sends (see AttackHook)
 * instead of your click.
 */
final class MaceSwap {
    // A mace hit is a slam once you have fallen more than this (MaceItem.canSmashAttack).
    private static final double SMASH_FALL_DISTANCE = 1.5;
    private static volatile boolean enabled;
    private static final String[] GROUND_MODES = {"Any mace", "Breach only", "Off"};
    private static final int GROUND_ANY = 0;
    private static final int GROUND_BREACH_ONLY = 1;
    private static volatile int groundMode = GROUND_ANY;
    private static volatile boolean fallingDensity = true;
    // Any attack: every attack your client sends, caught by AttackHook, instead of your click.
    private static volatile boolean anyAttack;
    // Click -> mace selected, mace selected -> hit, hit -> back to your slot.
    static final Num SWAP_DELAY = Num.ms(0, 0, 300);
    static final Num HIT_DELAY = Num.ms(0, 0, 300);
    static final Num SWAP_BACK = Num.ms(0, 0, 500);
    static final Num CHANCE = Num.percent(100);
    private static final SwapAction ACTION = new SwapAction();

    private MaceSwap() {}

    /** Your attack click: true if it became a mace hit, so the game must not also hit. */
    static boolean handleAttackClick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (!enabled || anyAttack || player == null || minecraft.gameMode == null || minecraft.screen != null
                || !SpearSettings.combatAllowed(minecraft)
                || !(minecraft.hitResult instanceof EntityHitResult hit)) {
            return false;
        }
        if (ACTION.busy()) {
            return true; // extra clicks during a delayed swap would hit with whatever is selected
        }
        // Only living things: a projectile in your sights (a wind charge) is not worth a swap.
        if (!(hit.getEntity() instanceof LivingEntity target)) {
            return false;
        }
        // A Shield Break catching every attack still goes first on a blocking target.
        if (ShieldBreak.isAnyAttack() && ShieldBreak.wouldBreak(minecraft, target)) {
            return false;
        }
        int maceSlot = maceFor(player, target, player.getInventory().getSelectedSlot());
        if (maceSlot < 0 || !CHANCE.roll()) {
            return false;
        }
        ACTION.start(minecraft, maceSlot, true, SWAP_DELAY, HIT_DELAY, SWAP_BACK, game -> {
            if (!target.isAlive() || !game.player.isWithinAttackRange(
                    game.player.getMainHandItem(), target.getBoundingBox(), 0)) {
                return false;
            }
            AttackHook.markOwn(target.getId());
            game.gameMode.attack(game.player, target);
            game.player.swing(InteractionHand.MAIN_HAND);
            return true;
        }, (game, acted, from, mace) -> finished(game, acted, from, mace, target, "mace swap"));
        return true;
    }

    /**
     * With delays on, an attack AttackHook held back, on the render thread: runs the same
     * delayed swap, and the held attack goes out as the hit.
     */
    static void runFromAttack(Minecraft minecraft, LivingEntity target, int maceSlot,
            ServerboundAttackPacket attack) {
        if (ACTION.busy() || minecraft.player == null || minecraft.screen != null) {
            AttackHook.resend(minecraft, attack);
            return;
        }
        ACTION.start(minecraft, maceSlot, true, SWAP_DELAY, HIT_DELAY, SWAP_BACK, game -> {
            AttackHook.resend(game, attack);
            return true;
        }, (game, acted, from, mace) -> finished(game, acted, from, mace, target, "mace swap (any attack)"));
    }

    /** Called from the input loop: the steps of a delayed swap. */
    static void update(Minecraft minecraft) {
        ACTION.update(minecraft);
    }

    /** True if any delay is on, so an attack caught by AttackHook has to be held back. */
    static boolean isDelayed() {
        return !SWAP_DELAY.isZero() || !HIT_DELAY.isZero() || !SWAP_BACK.isZero();
    }

    private static void finished(Minecraft minecraft, boolean acted, int previousSlot, int maceSlot,
            LivingEntity target, String source) {
        if (acted && minecraft.player != null) {
            MinecraftInputController.guardSwitchBack(previousSlot, maceSlot);
            report(minecraft.player, previousSlot, maceSlot, target, source);
        }
    }

    /**
     * Any attack your client sends, with Trigger on Any attack, on the network thread (see
     * AttackHook): the mace to select for that one packet, or -1 to let it go out as it is.
     */
    static int maceForAnyAttack(Minecraft minecraft, LivingEntity target, int heldSlot) {
        LocalPlayer player = minecraft.player;
        if (!enabled || !anyAttack || player == null || minecraft.screen != null || ACTION.busy()
                || !SpearSettings.combatAllowed(minecraft)) {
            return -1;
        }
        int mace = maceFor(player, target, heldSlot);
        return mace >= 0 && CHANCE.roll() ? mace : -1;
    }

    /** After AttackHook sent an attack with the mace, on the render thread. */
    static void afterAnyAttack(Minecraft minecraft, int previousSlot, int maceSlot, LivingEntity target) {
        if (minecraft.player != null) {
            MinecraftInputController.guardSwitchBack(previousSlot, maceSlot);
            report(minecraft.player, previousSlot, maceSlot, target, "mace swap (any attack)");
        }
    }

    /** The mace to hit the target with instead of the held slot, or -1 to leave the hit alone. */
    private static int maceFor(LocalPlayer player, LivingEntity target, int heldSlot) {
        int maceSlot = chosenMace(player);
        if (maceSlot < 0 || maceSlot == heldSlot || !player.isWithinAttackRange(
                player.getInventory().getItem(maceSlot), target.getBoundingBox(), 0)) {
            return -1;
        }
        return maceSlot;
    }

    private static void report(LocalPlayer player, int previousSlot, int maceSlot, LivingEntity target,
            String source) {
        String kind = describe(player.getInventory().getItem(maceSlot));
        MinecraftSpearDash.setStatus(kind + " hit");
        SpearLog.log(source + ": hotbar " + (previousSlot + 1) + " -> " + kind + " " + (maceSlot + 1)
                + " -> back to " + (player.getInventory().getSelectedSlot() + 1)
                + (isSlam(player) ? " (slam)" : "") + " on " + target.getName().getString());
    }

    /** The mace this module's settings pick for your next hit, or -1 for none. */
    private static int chosenMace(LocalPlayer player) {
        if (isSlam(player)) {
            if (!fallingDensity) {
                return -1;
            }
            int density = findMace(player, Enchantments.DENSITY);
            return density >= 0 ? density : findMace(player, null);
        }
        if (groundMode == GROUND_ANY) {
            int breach = findMace(player, Enchantments.BREACH);
            return breach >= 0 ? breach : findMace(player, null);
        }
        return groundMode == GROUND_BREACH_ONLY ? findMace(player, Enchantments.BREACH) : -1;
    }

    /**
     * The best mace for a hit regardless of this module's settings, for Shield Break's
     * follow-up: Density for a slam, else Breach, else any mace; -1 if you have none.
     */
    static int bestMace(LocalPlayer player) {
        int preferred = findMace(player, isSlam(player) ? Enchantments.DENSITY : Enchantments.BREACH);
        return preferred >= 0 ? preferred : findMace(player, null);
    }

    private static boolean isSlam(LocalPlayer player) {
        return player.fallDistance > SMASH_FALL_DISTANCE && !player.isFallFlying();
    }

    /** First hotbar mace with the enchantment, or with any enchantments if it is null. */
    private static int findMace(LocalPlayer player, ResourceKey<Enchantment> enchantment) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.MACE) && (enchantment == null || hasEnchantment(stack, enchantment))) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean hasEnchantment(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        return enchantmentLevel(stack, enchantment) > 0;
    }

    static int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        ItemEnchantments enchantments = stack.getEnchantments();
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.unwrapKey().filter(enchantment::equals).isPresent()) {
                return enchantments.getLevel(holder);
            }
        }
        return 0;
    }

    private static String describe(ItemStack mace) {
        if (hasEnchantment(mace, Enchantments.DENSITY)) {
            return "Density mace";
        }
        return hasEnchantment(mace, Enchantments.BREACH) ? "Breach mace" : "Mace";
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

    static int groundMode() {
        return groundMode;
    }

    static String groundModeLabel() {
        return GROUND_MODES[groundMode];
    }

    static void setGroundMode(int mode) {
        groundMode = Math.floorMod(mode, GROUND_MODES.length);
    }

    static void cycleGroundMode() {
        groundMode = (groundMode + 1) % GROUND_MODES.length;
    }

    static boolean isFallingDensity() {
        return fallingDensity;
    }

    static void setFallingDensity(boolean on) {
        fallingDensity = on;
    }

    static void toggleFallingDensity() {
        fallingDensity = !fallingDensity;
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

    /** Turns the module off when this injection is unloaded, swapping back mid-swap. */
    static void reset(Minecraft minecraft) {
        ACTION.cancel(minecraft);
        enabled = false;
    }
}
