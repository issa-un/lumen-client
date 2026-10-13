package app;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Auto Armor: puts on better armor than you are wearing, one piece every 0.15 s. Better is
 * more armor points plus toughness (durability left breaks ties). With your inventory open
 * it takes pieces from anywhere in it, by the clicks you would make: shift-click onto an
 * empty slot, or pick up, swap with the worn piece and put that one back. With From hotbar,
 * a better piece in your hotbar is put on with no screen open the way right-clicking it does
 * (the worn piece goes to that hotbar slot). An elytra you wear is never replaced, nor is a
 * piece with Curse of Binding.
 */
final class AutoArmor {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET};
    // Armor slots in your inventory screen, in the same order.
    private static final int[] MENU_SLOTS = {5, 6, 7, 8};
    private static final long STEP_MS = 150;
    private static volatile boolean enabled;
    private static volatile boolean fromHotbar = true;
    // Render thread only.
    private static long nextStepMs;

    private AutoArmor() {}

    /** Called from the input loop. */
    static void update(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        long now = System.nanoTime() / 1_000_000;
        if (!enabled || player == null || minecraft.gameMode == null || now < nextStepMs
                || !SpearSettings.combatAllowed(minecraft)) {
            return;
        }
        if (minecraft.screen instanceof InventoryScreen screen && screen.getMenu().getCarried().isEmpty()) {
            if (fromInventory(minecraft, player, screen.getMenu())) {
                nextStepMs = now + STEP_MS;
            }
        } else if (minecraft.screen == null && fromHotbar && !player.isUsingItem()) {
            if (fromHotbar(minecraft, player)) {
                nextStepMs = now + STEP_MS;
            }
        }
    }

    private static boolean fromInventory(Minecraft minecraft, LocalPlayer player, InventoryMenu menu) {
        for (int piece = 0; piece < ARMOR.length; piece++) {
            ItemStack worn = player.getItemBySlot(ARMOR[piece]);
            if (locked(worn)) {
                continue;
            }
            Slot best = null;
            double bestScore = score(worn, ARMOR[piece]);
            for (Slot slot : menu.slots) {
                if (slot.index < InventoryMenu.INV_SLOT_START || slot.index >= InventoryMenu.USE_ROW_SLOT_END) {
                    continue;
                }
                double score = score(slot.getItem(), ARMOR[piece]);
                if (score > bestScore) {
                    best = slot;
                    bestScore = score;
                }
            }
            if (best == null) {
                continue;
            }
            int id = menu.containerId;
            if (worn.isEmpty()) {
                minecraft.gameMode.handleContainerInput(id, best.index, 0, ContainerInput.QUICK_MOVE, player);
            } else {
                minecraft.gameMode.handleContainerInput(id, best.index, 0, ContainerInput.PICKUP, player);
                minecraft.gameMode.handleContainerInput(id, MENU_SLOTS[piece], 0, ContainerInput.PICKUP, player);
                minecraft.gameMode.handleContainerInput(id, best.index, 0, ContainerInput.PICKUP, player);
            }
            SpearLog.log("auto armor: " + ARMOR[piece].getName() + " from inventory slot " + best.index);
            return true;
        }
        return false;
    }

    private static boolean fromHotbar(Minecraft minecraft, LocalPlayer player) {
        for (EquipmentSlot piece : ARMOR) {
            ItemStack worn = player.getItemBySlot(piece);
            if (locked(worn)) {
                continue;
            }
            int best = -1;
            double bestScore = score(worn, piece);
            for (int slot = 0; slot < 9; slot++) {
                double score = score(player.getInventory().getItem(slot), piece);
                if (score > bestScore) {
                    best = slot;
                    bestScore = score;
                }
            }
            if (best < 0) {
                continue;
            }
            int previousSlot = player.getInventory().getSelectedSlot();
            try {
                player.getInventory().setSelectedSlot(best);
                minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            } finally {
                player.getInventory().setSelectedSlot(previousSlot);
                MinecraftSpearDash.sendSelectedSlot(minecraft, false);
            }
            MinecraftInputController.stopGuard();
            SpearLog.log("auto armor: " + piece.getName() + " from hotbar " + (best + 1));
            return true;
        }
        return false;
    }

    /** A worn piece to leave alone: an elytra, or one with Curse of Binding (it cannot come off). */
    private static boolean locked(ItemStack worn) {
        return worn.is(Items.ELYTRA) || EnchantmentHelper.has(worn, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE);
    }

    /** How good the item is worn in that slot; -1 if it does not go there. */
    private static double score(ItemStack stack, EquipmentSlot slot) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (stack.isEmpty() || equippable == null || equippable.slot() != slot) {
            return -1;
        }
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        double armor = modifiers == null ? 0 : modifiers.compute(Attributes.ARMOR, 0, slot)
                + modifiers.compute(Attributes.ARMOR_TOUGHNESS, 0, slot);
        if (armor <= 0) {
            return -1; // pumpkins, heads and the like are not armor
        }
        double durabilityLeft = stack.isDamageableItem()
                ? 1 - (double) stack.getDamageValue() / stack.getMaxDamage() : 1;
        return armor + durabilityLeft * 0.01;
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

    static boolean isFromHotbar() {
        return fromHotbar;
    }

    static void setFromHotbar(boolean on) {
        fromHotbar = on;
    }

    static void toggleFromHotbar() {
        fromHotbar = !fromHotbar;
    }

    static void reset() {
        enabled = false;
    }
}
