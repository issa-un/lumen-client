package app;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * What the menu shows: every category, its modules and their settings, read live from the
 * modules so the menu always shows the current state. The menu only lays these out.
 */
final class Modules {
    /** A setting row under a module. */
    sealed interface Row permits Toggle, Choice, Bind, Action, Slider {}

    /** On or off, shown as a switch. */
    record Toggle(String label, BooleanSupplier on, Runnable flip) implements Row {}

    /** One of a few values; clicking steps to the next. */
    record Choice(String label, Supplier<String> value, Runnable next) implements Row {}

    /** A key or mouse button; clicking waits for the next press. */
    record Bind(String label, Supplier<String> value, BooleanSupplier awaiting, Runnable await) implements Row {}

    /** A button. */
    record Action(Supplier<String> label, Runnable run) implements Row {}

    /** A number on a track: drag it (a range has a min and a max knob); right-click resets it. */
    record Slider(String label, Num num) implements Row {}

    record Module(String name, String about, BooleanSupplier on, Runnable flip, List<Row> settings) {}

    record Category(String name, String icon, int color, List<Module> modules) {}

    private Modules() {}

    static List<Category> categories(Minecraft minecraft) {
        boolean hud = VelocityHud.isAvailable();
        String server = SpearSettings.currentServer(minecraft);
        return List.of(
                new Category("Combat", "C", 0xFFFF453A, List.of(
                        new Module("Spear Dash", "Swap, lunge and swap back on a key",
                                MinecraftSpearDash::isEnabled, MinecraftSpearDash::toggleEnabled, List.of(
                                new Bind("Key", () -> bindLabel(MinecraftSpearDash.keyCode(), MinecraftSpearDash.mouseButton()),
                                        MinecraftSpearDash::isAwaitingBinding, MinecraftSpearDash::awaitBinding),
                                new Toggle("Only from wind charge", MinecraftSpearDash::isOnlyFromWindCharge,
                                        MinecraftSpearDash::toggleOnlyFromWindCharge),
                                new Choice("Dashes", MinecraftSpearDash::dashCountLabel, MinecraftSpearDash::cycleDashCount),
                                new Choice("Flat lunge", MinecraftSpearDash::flatModeLabel, MinecraftSpearDash::cycleFlatMode),
                                new Toggle("Ready sound", MinecraftSpearDash::isReadySound, MinecraftSpearDash::toggleReadySound),
                                new Slider("Swap delay", MinecraftSpearDash.SWAP_DELAY),
                                new Slider("Jab delay", MinecraftSpearDash.JAB_DELAY),
                                new Slider("Return", MinecraftSpearDash.RETURN),
                                new Slider("Chain gap", MinecraftSpearDash.CHAIN_GAP))),
                        new Module("Shield Break", "Axe, then mace, through a raised shield",
                                ShieldBreak::isEnabled, ShieldBreak::toggleEnabled, List.of(
                                new Bind("Key", () -> bindLabel(ShieldBreak.keyCode(), ShieldBreak.mouseButton()),
                                        ShieldBreak::isAwaitingBinding, ShieldBreak::awaitBinding),
                                new Toggle("On attack click", ShieldBreak::isOnAttackClick, ShieldBreak::toggleOnAttackClick),
                                new Choice("Trigger", ShieldBreak::triggerLabel, ShieldBreak::toggleAnyAttack),
                                new Choice("Axe hits", () -> ShieldBreak.axeHitsLabel(minecraft), ShieldBreak::cycleAxeHits),
                                new Toggle("Mace follow-up", ShieldBreak::isMaceFollowUp, ShieldBreak::toggleMaceFollowUp),
                                new Choice("Range", ShieldBreak::rangeLabel, ShieldBreak::cycleRange),
                                new Slider("Chance", ShieldBreak.CHANCE),
                                new Slider("Swap delay", ShieldBreak.SWAP_DELAY),
                                new Slider("Hit delay", ShieldBreak.HIT_DELAY),
                                new Slider("Between hits", ShieldBreak.BETWEEN_HITS),
                                new Slider("Axe to mace", ShieldBreak.AXE_TO_MACE),
                                new Slider("Swap back", ShieldBreak.SWAP_BACK))),
                        new Module("Mace Swap", "The right mace for every hit",
                                MaceSwap::isEnabled, MaceSwap::toggleEnabled, List.of(
                                new Choice("Trigger", MaceSwap::triggerLabel, MaceSwap::toggleAnyAttack),
                                new Choice("On ground", MaceSwap::groundModeLabel, MaceSwap::cycleGroundMode),
                                new Choice("Dropping", () -> MaceSwap.isFallingDensity() ? "Density" : "Off",
                                        MaceSwap::toggleFallingDensity),
                                new Slider("Chance", MaceSwap.CHANCE),
                                new Slider("Swap delay", MaceSwap.SWAP_DELAY),
                                new Slider("Hit delay", MaceSwap.HIT_DELAY),
                                new Slider("Swap back", MaceSwap.SWAP_BACK))),
                        new Module("Auto Totem", "Keeps a totem in your offhand",
                                AutoTotem::isEnabled, AutoTotem::toggleEnabled, List.of(
                                new Choice("Mode", AutoTotem::modeLabel, AutoTotem::cycleMode),
                                new Slider("Reaction", AutoTotem.REACTION),
                                new Slider("Step delay", AutoTotem.STEP_DELAY))),
                        new Module("Auto Weapon", "Your best weapon when you hit holding a tool",
                                AutoWeapon::isEnabled, AutoWeapon::toggleEnabled, List.of(
                                new Choice("Trigger", AutoWeapon::triggerLabel, AutoWeapon::toggleAnyAttack),
                                new Slider("Chance", AutoWeapon.CHANCE),
                                new Slider("Swap delay", AutoWeapon.SWAP_DELAY),
                                new Slider("Hit delay", AutoWeapon.HIT_DELAY))),
                        new Module("Reach Swap", "The spear's 4.5-block reach for players",
                                ReachSwap::isEnabled, ReachSwap::toggleEnabled, List.of(
                                new Choice("Mode", ReachSwap::modeLabel, ReachSwap::cycleMode),
                                new Slider("Switch back", ReachSwap.SWITCH_BACK))),
                        new Module("Quick Throw", "A pearl or wind charge on a key",
                                QuickThrow::isEnabled, QuickThrow::toggleEnabled, List.of(
                                new Bind("Key", () -> bindLabel(QuickThrow.keyCode(), QuickThrow.mouseButton()),
                                        QuickThrow::isAwaitingBinding, QuickThrow::awaitBinding),
                                new Choice("Item", QuickThrow::itemLabel, QuickThrow::cycleItem),
                                new Slider("Swap delay", QuickThrow.SWAP_DELAY),
                                new Slider("Throw delay", QuickThrow.THROW_DELAY),
                                new Slider("Swap back", QuickThrow.SWAP_BACK))),
                        new Module("Chest Swap", "Elytra or chestplate on a key",
                                ChestSwap::isEnabled, ChestSwap::toggleEnabled, List.of(
                                new Bind("Key", () -> bindLabel(ChestSwap.keyCode(), ChestSwap.mouseButton()),
                                        ChestSwap::isAwaitingBinding, ChestSwap::awaitBinding),
                                new Slider("Swap delay", ChestSwap.SWAP_DELAY),
                                new Slider("Use delay", ChestSwap.USE_DELAY),
                                new Slider("Swap back", ChestSwap.SWAP_BACK))))),
                new Category("UHC", "U", 0xFFFF9F0A, List.of(
                        new Module("Web Escape", "Water out of cobwebs and fire",
                                WebEscape::isEnabled, WebEscape::toggleEnabled, List.of(
                                new Toggle("On fire too", WebEscape::isOnFire, WebEscape::toggleOnFire),
                                new Toggle("Pick up water", WebEscape::isPickUp, WebEscape::togglePickUp))),
                        new Module("Web Trap", "Webs the player you look at, on a key",
                                WebTrap::isEnabled, WebTrap::toggleEnabled, List.of(
                                new Bind("Key", () -> bindLabel(WebTrap.keyCode(), WebTrap.mouseButton()),
                                        WebTrap::isAwaitingBinding, WebTrap::awaitBinding),
                                new Toggle("Drain with lava", WebTrap::isDrain, WebTrap::toggleDrain))),
                        new Module("MLG Water", "Water down before a hard landing",
                                MlgWater::isEnabled, MlgWater::toggleEnabled, List.of(
                                new Choice("Min damage", MlgWater::minDamageLabel, MlgWater::cycleMinDamage),
                                new Toggle("Pick up water", MlgWater::isPickUp, MlgWater::togglePickUp))),
                        new Module("W-Tap", "Sprint reset after every hit",
                                WTap::isEnabled, WTap::toggleEnabled, List.of(
                                new Choice("Trigger", WTap::triggerLabel, WTap::toggleAnyAttack),
                                new Choice("Mode", WTap::modeLabel, WTap::cycleMode),
                                new Toggle("Only sprint hits", WTap::isOnlySprintHits, WTap::toggleOnlySprintHits),
                                new Slider("Chance", WTap.CHANCE),
                                new Slider("Reaction", WTap.REACTION),
                                new Slider("Length", WTap.LENGTH))),
                        new Module("Jump Reset", "Jumps as knockback lands",
                                JumpReset::isEnabled, JumpReset::toggleEnabled, List.of(
                                new Toggle("Only sprinting", JumpReset::isOnlyWhileSprinting,
                                        JumpReset::toggleOnlyWhileSprinting))),
                        new Module("Auto Gapple", "A golden apple when health is low",
                                AutoGapple::isEnabled, AutoGapple::toggleEnabled, List.of(
                                new Choice("Health", AutoGapple::healthLabel, AutoGapple::cycleHealth))))),
                new Category("Bedwars", "B", 0xFFBF5AF2, List.of(
                        new Module("Clutch", "A block under you when knocked off",
                                Clutch::isEnabled, Clutch::toggleEnabled, List.of(
                                new Choice("When", Clutch::whenLabel, Clutch::cycleWhen))),
                        new Module("Edge Sneak", "Crouches at edges while you bridge",
                                EdgeSneak::isEnabled, EdgeSneak::toggleEnabled, List.of(
                                new Toggle("Only holding blocks", EdgeSneak::isOnlyWithBlocks, EdgeSneak::toggleOnlyWithBlocks))),
                        new Module("Chest Stealer", "Empties the chests you open",
                                ChestStealer::isEnabled, ChestStealer::toggleEnabled, List.of(
                                new Choice("Delay", () -> ChestStealer.delayMs() + " ms", ChestStealer::cycleDelay),
                                new Toggle("Close when done", ChestStealer::isCloseWhenDone, ChestStealer::toggleCloseWhenDone))),
                        new Module("Auto Armor", "Puts on better armor",
                                AutoArmor::isEnabled, AutoArmor::toggleEnabled, List.of(
                                new Toggle("From hotbar", AutoArmor::isFromHotbar, AutoArmor::toggleFromHotbar))))),
                new Category("Utility", "T", 0xFF30B0C7, List.of(
                        new Module("Auto Tool", "The fastest tool while you mine",
                                AutoTool::isEnabled, AutoTool::toggleEnabled, List.of(
                                new Toggle("Swap back", AutoTool::isSwapBack, AutoTool::toggleSwapBack))),
                        new Module("Auto Sprint", "Sprints whenever you walk forward",
                                Utilities::isAutoSprint, Utilities::toggleAutoSprint, List.of()),
                        new Module("Auto Walk", "Walks forward for you",
                                Utilities::isAutoWalk, Utilities::toggleAutoWalk, List.of()),
                        new Module("Auto Eat", "Eats when you get hungry",
                                AutoEat::isEnabled, AutoEat::toggleEnabled, List.of(
                                new Choice("Hunger", AutoEat::hungerLabel, AutoEat::cycleHunger))),
                        new Module("Auto Fish", "Reels in on a bite and casts again",
                                AutoFish::isEnabled, AutoFish::toggleEnabled, List.of()),
                        new Module("Fullbright", "Lights up caves and nights",
                                Utilities::isFullbright, Utilities::toggleFullbright, List.of()),
                        new Module("Zoom", "Hold a key to zoom in",
                                Utilities::isZoom, Utilities::toggleZoom, List.of(
                                new Bind("Hold", () -> bindLabel(Utilities.zoomKey(), Utilities.zoomMouse()),
                                        Utilities::isAwaitingZoomBinding, Utilities::awaitZoomBinding),
                                new Choice("Zoom", () -> Utilities.zoomFactor() + "x", Utilities::cycleZoomFactor))),
                        new Module("Clear Weather", "No rain or thunder on your screen",
                                Utilities::isClearWeather, Utilities::toggleClearWeather, List.of()),
                        new Module("Auto Respawn", "Skips the death screen",
                                Utilities::isAutoRespawn, Utilities::toggleAutoRespawn, List.of(
                                new Toggle("Death coords", Utilities::isDeathCoords, Utilities::toggleDeathCoords))),
                        new Module("Hit Sound", "A ding when your hit lands",
                                Utilities::isHitSound, Utilities::toggleHitSound, List.of()),
                        new Module("Middle Click Friend", "Middle-click a player to friend them",
                                Utilities::isMiddleClickFriend, Utilities::toggleMiddleClickFriend, List.of()))),
                new Category("HUD", "H", 0xFF0A84FF, List.of(
                        new Module("Speed HUD", hud ? "Speed ring above the hotbar" : "Needs Fabric API's HUD module",
                                VelocityHud::isSpeedHudOn, VelocityHud::toggleSpeedHud, List.of(
                                new Choice("Position", VelocityHud::positionLabel, VelocityHud::togglePosition),
                                new Toggle("Warnings", VelocityHud::isWarningsOn, VelocityHud::toggleWarnings),
                                toggleKey(VelocityHud.TOGGLE_SPEED))),
                        new Module("Radar", hud ? "Nearby players around the crosshair" : "Needs Fabric API's HUD module",
                                VelocityHud::isRadarOn, VelocityHud::toggleRadar, List.of(
                                new Choice("Range", () -> VelocityHud.radarRange() + " m", VelocityHud::cycleRadarRange),
                                new Choice("Team", () -> VelocityHud.isIgnoreTeam() ? "Ignore" : "Show",
                                        VelocityHud::toggleIgnoreTeam),
                                new Action(() -> friendLabel(minecraft), () -> toggleFriend(minecraft)),
                                toggleKey(VelocityHud.TOGGLE_RADAR))),
                        new Module("Landing", hud ? "Fall damage before you land" : "Needs Fabric API's HUD module",
                                VelocityHud::isLandingOn, VelocityHud::toggleLanding, List.of(
                                toggleKey(VelocityHud.TOGGLE_LANDING))))),
                new Category("Server", "S", 0xFF8E8E93, List.of(
                        new Module("Combat here", server,
                                () -> SpearSettings.combatAllowed(minecraft), () -> SpearSettings.toggleCombatHere(minecraft),
                                List.of(new Toggle("Own settings", () -> SpearSettings.hasOwnSettings(minecraft),
                                        () -> SpearSettings.toggleOwnSettingsHere(minecraft)))))));
    }

    /** A key that turns a HUD module on or off without opening the menu. */
    private static Bind toggleKey(int module) {
        return new Bind("Toggle key", () -> VelocityHud.awaitingToggle() == module ? "Press a key (Esc: none)"
                : bindLabel(VelocityHud.toggleKey(module), VelocityHud.toggleMouse(module)),
                () -> VelocityHud.awaitingToggle() == module, () -> VelocityHud.awaitToggleBinding(module));
    }

    /** The player you look at, else the nearest one; friends never set off the Radar. */
    private static String friendLabel(Minecraft minecraft) {
        Player candidate = VelocityHud.friendCandidate(minecraft);
        if (candidate == null) {
            return "Friends: " + VelocityHud.friendCount();
        }
        return (VelocityHud.isFriend(candidate) ? "Unfriend " : "Friend ") + candidate.getName().getString();
    }

    private static void toggleFriend(Minecraft minecraft) {
        Player candidate = VelocityHud.friendCandidate(minecraft);
        if (candidate != null) {
            VelocityHud.toggleFriend(candidate);
        }
    }

    /** A binding's name as the game shows it ("R", "Left Shift", "Mouse 4"), or "None". */
    static String bindLabel(int keyCode, int mouseButton) {
        if (mouseButton >= 0) {
            return MinecraftInputController.mouseLabel(mouseButton);
        }
        if (keyCode < 0) {
            return "None";
        }
        return InputConstants.Type.KEYSYM.getOrCreate(keyCode).getDisplayName().getString();
    }
}
