package app;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;

/**
 * Chest Stealer: when you open a chest, barrel, ender chest or shulker box, shift-clicks its
 * items into your inventory one at a time, Delay apart, starting a moment after it opens so
 * its contents have arrived. Items that do not fit are skipped. With Close when done, the
 * screen closes once nothing more can be taken (an empty one is left open). It only works
 * in a container you opened, and goes through it once.
 */
final class ChestStealer {
    private static final int[] DELAYS_MS = {50, 100, 150, 250};
    // Contents arrive just after the screen opens.
    private static final long START_DELAY_MS = 150;
    private static volatile boolean enabled;
    private static volatile int delayIndex = 1;
    private static volatile boolean closeWhenDone = true;
    // Render thread only.
    private static int containerId = -1;
    private static int doneId = -1;
    private static long nextClickMs;
    private static final Set<Integer> TRIED = new HashSet<>();

    private ChestStealer() {}

    /** Called from the input loop. */
    static void update(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (!enabled || player == null || minecraft.gameMode == null
                || !(minecraft.screen instanceof AbstractContainerScreen<?> screen)
                || !(screen.getMenu() instanceof ChestMenu || screen.getMenu() instanceof ShulkerBoxMenu)) {
            containerId = -1;
            doneId = -1;
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        if (menu.containerId == doneId) {
            return;
        }
        long now = System.nanoTime() / 1_000_000;
        if (menu.containerId != containerId) {
            containerId = menu.containerId;
            TRIED.clear();
            nextClickMs = now + START_DELAY_MS;
            return;
        }
        if (now < nextClickMs || !menu.getCarried().isEmpty()) {
            return;
        }
        for (Slot slot : menu.slots) {
            // The container's own slots come before your inventory's.
            if (slot.container == player.getInventory()) {
                break;
            }
            if (slot.hasItem() && TRIED.add(slot.index)) {
                minecraft.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, player);
                nextClickMs = now + DELAYS_MS[delayIndex];
                return;
            }
        }
        if (closeWhenDone && !TRIED.isEmpty()) {
            SpearLog.log("chest stealer: took " + TRIED.size() + " stacks");
            player.closeContainer();
        }
        doneId = menu.containerId;
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

    static int delayMs() {
        return DELAYS_MS[delayIndex];
    }

    static void setDelayMs(int ms) {
        for (int i = 0; i < DELAYS_MS.length; i++) {
            if (DELAYS_MS[i] == ms) {
                delayIndex = i;
            }
        }
    }

    static void cycleDelay() {
        delayIndex = (delayIndex + 1) % DELAYS_MS.length;
    }

    static boolean isCloseWhenDone() {
        return closeWhenDone;
    }

    static void setCloseWhenDone(boolean on) {
        closeWhenDone = on;
    }

    static void toggleCloseWhenDone() {
        closeWhenDone = !closeWhenDone;
    }

    static void reset() {
        enabled = false;
        containerId = -1;
    }
}
