package app;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;

/**
 * Adapt: a fight coach. It watches the player you are fighting and learns how they play
 * (slams from height, shield, pearls when low, wind charges, totems, the weapon they use),
 * then shows a short callout under the wheel at the top of your screen with what to do about
 * it, a line of facts behind it ("Slam 3, 9 hp from 14 blocks up") and the fight at a
 * glance. Plain trades get no callout; only things worth knowing do. With Wheel on, an
 * eight-spoke wheel turns one notch every time a player hits you; your own pearl landings,
 * falls and fire don't count. It reads only what your game already shows. Callouts come
 * from AdaptCallouts, picked at random per situation without repeats.
 */
final class Adapt {
    private static final long CALLOUT_MS = 2800;
    private static final long CALLOUT_GAP_MS = 2500;
    private static final long FIGHT_MEMORY_MS = 10_000;
    private static final double OPPONENT_RANGE = 12;
    private static final int RECENT_PER_SITUATION = 6;
    private static final long WHEEL_TURN_MS = 450;
    private static final int RIM = 0xFFE8D9A8;
    private static final int GOLD = 0xFFB89B5E;
    private static final int DARK = 0xFF8A6F3A;
    private static volatile boolean enabled;
    private static volatile boolean wheel = true;

    /** What Adapt has learned about one opponent; kept for the whole session. */
    private static final class Profile {
        int slams;
        int bigHits;
        int hitsOnYou;
        int yourHits;
        int shieldTicks;
        int seenTicks;
        int pearls;
        int pearlsWhenLow;
        int windCharges;
        int pops;
        int gapples;
        int groundHits;
        float biggestHit;
        boolean weaponCalled;
    }

    // Render thread only.
    private static final Map<String, Profile> PROFILES = new HashMap<>();
    private static final Map<String, Deque<Integer>> RECENT = new HashMap<>();
    private static final Set<Integer> SEEN_PROJECTILES = new HashSet<>();
    private static Player opponent;
    private static long opponentSeenMs;
    private static float lastHealth = -1;
    private static float lastAbsorption;
    private static int lastHurtTime;
    private static float oppLastAbsorption;
    private static int oppLastHurtTime;
    private static boolean oppEating;
    private static double oppHighestAboveYou;
    private static long oppHighestAtMs;
    private static double lastDistance = -1;
    private static long distanceAtMs;
    private static int combo;
    private static int notches;
    private static long notchAtMs;
    private static String callout = "";
    private static String calloutDetail = "";
    private static long calloutAtMs;
    private static long lastCalloutMs;

    private Adapt() {}

    /** Called from the input loop: watches the fight and picks callouts. */
    static void update(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (!enabled || player == null || minecraft.level == null) {
            lastHealth = -1;
            return;
        }
        long now = nowMs();
        trackOpponent(minecraft, player, now);
        float health = player.getHealth();
        float absorption = player.getAbsorptionAmount();
        boolean hurt = lastHealth >= 0 && player.hurtTime > lastHurtTime;
        float lost = lastHealth < 0 ? 0 : (lastHealth + lastAbsorption) - (health + absorption);
        boolean youPopped = lastHealth >= 0 && health <= 2 && absorption >= 7.5F && absorption > lastAbsorption + 1;
        lastHealth = health;
        lastAbsorption = absorption;
        lastHurtTime = player.hurtTime;

        if (opponent != null) {
            watchOpponent(minecraft, player, now);
        }
        if (youPopped) {
            say(totems(player) <= 1 ? "LAST_TOTEM_YOU" : "YOU_POPPED", now, true);
        }
        if (hurt) {
            // Only a player's hit counts: pearl landings, falls, fire and the like are not
            // your opponent's doing, so they neither turn the wheel nor count as a combo.
            DamageSource source = player.getLastDamageSource();
            Entity by = source == null ? null : source.getEntity();
            if (by instanceof Player attacker && attacker != player) {
                if (attacker != opponent) {
                    newOpponent(attacker, now);
                }
                opponentSeenMs = now;
                notches++;
                notchAtMs = now;
                onHurt(player, lost, now);
            }
        }
    }

    private static void trackOpponent(Minecraft minecraft, LocalPlayer player, long now) {
        Player attacker = player.getLastHurtByMob() instanceof Player p && p != player ? p : null;
        if (attacker != null && attacker.isAlive()) {
            if (opponent != attacker) {
                newOpponent(attacker, now);
            }
            opponentSeenMs = now;
        }
        if (opponent != null && (!opponent.isAlive() || opponent.isRemoved()
                || player.distanceTo(opponent) > OPPONENT_RANGE * 2 || now - opponentSeenMs > FIGHT_MEMORY_MS)) {
            opponent = null;
        }
        if (opponent == null) {
            Player closest = null;
            double best = OPPONENT_RANGE;
            for (Player other : minecraft.level.players()) {
                if (other != player && other.isAlive() && !other.isSpectator() && !VelocityHud.isFriend(other)
                        && player.distanceTo(other) < best) {
                    best = player.distanceTo(other);
                    closest = other;
                }
            }
            if (closest != null && closest.getMainHandItem().getCount() > 0 && looksArmed(closest.getMainHandItem())) {
                newOpponent(closest, now);
            }
        }
        if (opponent != null && player.distanceTo(opponent) < OPPONENT_RANGE) {
            opponentSeenMs = now;
        }
    }

    private static void newOpponent(Player player, long now) {
        opponent = player;
        opponentSeenMs = now;
        oppLastAbsorption = player.getAbsorptionAmount();
        oppLastHurtTime = player.hurtTime;
        lastDistance = -1;
        combo = 0;
        Profile profile = profile();
        if (profile.seenTicks == 0) {
            say("FIGHT_START", now, false);
        }
    }

    private static void watchOpponent(Minecraft minecraft, LocalPlayer player, long now) {
        Profile profile = profile();
        profile.seenTicks++;
        if (opponent.isBlocking()) {
            profile.shieldTicks++;
        }
        double above = opponent.getY() - player.getY();
        if (above > oppHighestAboveYou || now - oppHighestAtMs > 1200) {
            oppHighestAboveYou = above;
            oppHighestAtMs = now;
        }
        if (above > 8 && opponent.getDeltaMovement().y > 0.3) {
            say("THEY_HIGH", now, false);
        }
        if (player.getY() - opponent.getY() > 6 && player.fallDistance > 3) {
            say("YOU_HIGH", now, false);
        }
        // Their hits taken: the hurt animation starting while you are close.
        if (opponent.hurtTime > oppLastHurtTime && player.distanceTo(opponent) < 6) {
            profile.yourHits++;
            combo = 0;
        }
        oppLastHurtTime = opponent.hurtTime;
        float total = opponent.getHealth() + opponent.getAbsorptionAmount();
        if (opponent.getHealth() <= 2 && opponent.getAbsorptionAmount() >= 7.5F
                && opponent.getAbsorptionAmount() > oppLastAbsorption + 1) {
            profile.pops++;
            say(profile.pops >= 2 ? "THEY_POPPED_MANY" : "THEY_POPPED", now, true,
                    "Totem " + profile.pops + " popped on them");
        }
        oppLastAbsorption = opponent.getAbsorptionAmount();
        boolean eating = opponent.isUsingItem() && (opponent.getUseItem().is(Items.GOLDEN_APPLE)
                || opponent.getUseItem().is(Items.ENCHANTED_GOLDEN_APPLE));
        if (eating && !oppEating) {
            profile.gapples++;
            say("THEY_EATING", now, false, "Gapple " + profile.gapples + " this fight");
        }
        oppEating = eating;
        if (total <= 6 && total > 0) {
            say("THEY_LOW", now, false, "Them " + hp(total));
        }
        double distance = player.distanceTo(opponent);
        if (lastDistance < 0 || now - distanceAtMs > 1000) {
            if (lastDistance >= 0 && distance - lastDistance > 3 && total < opponent.getMaxHealth() * 0.5) {
                say("THEY_RUNNING", now, false);
            }
            lastDistance = distance;
            distanceAtMs = now;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof ThrownEnderpearl) && !(entity instanceof WindCharge)) {
                continue;
            }
            if (!SEEN_PROJECTILES.add(entity.getId())) {
                continue;
            }
            Entity owner = entity instanceof ThrownEnderpearl pearl ? pearl.getOwner() : ((WindCharge) entity).getOwner();
            if (owner != opponent) {
                continue;
            }
            if (entity instanceof ThrownEnderpearl) {
                profile.pearls++;
                if (total < opponent.getMaxHealth() * 0.4) {
                    profile.pearlsWhenLow++;
                }
                say(profile.pearlsWhenLow >= 2 ? "THEY_PEARL_HABIT" : "THEY_PEARLED", now, false,
                        profile.pearlsWhenLow >= 2 ? "Pearled away low " + profile.pearlsWhenLow + " times" : null);
            } else {
                profile.windCharges++;
                if (profile.windCharges % 3 == 0) {
                    say("THEY_WINDCHARGE", now, false);
                }
            }
        }
        if (SEEN_PROJECTILES.size() > 2000) {
            SEEN_PROJECTILES.clear();
        }
    }

    /** You took damage: the situation that matters most right now. */
    private static void onHurt(LocalPlayer player, float lost, long now) {
        Profile profile = opponent == null ? null : profile();
        float you = player.getHealth() + player.getAbsorptionAmount();
        int totems = totems(player);
        float them = opponent == null ? 20 : opponent.getHealth() + opponent.getAbsorptionAmount();
        if (profile != null) {
            profile.hitsOnYou++;
            combo++;
            if (lost >= 6) {
                profile.bigHits++;
            }
        }
        boolean slam = profile != null && lost >= 5 && oppHighestAboveYou > 3 && now - oppHighestAtMs < 1500;
        if (slam) {
            profile.slams++;
        } else if (profile != null && opponent.onGround()) {
            profile.groundHits++;
        }
        if (profile != null && lost > profile.biggestHit) {
            profile.biggestHit = lost;
        }
        // Only callouts that say something about this fight; plain trades say nothing.
        String situation = null;
        String detail = null;
        if (you <= 6 && (totems == 0 || them > you * 2)) {
            situation = "RUN";
            detail = "You " + hp(you) + " vs them " + hp(them) + ", " + totems + " totem" + (totems == 1 ? "" : "s");
        } else if (totems == 0 && you <= 10) {
            situation = "NO_TOTEMS_YOU";
            detail = "You " + hp(you) + ", no totems";
        } else if (combo >= 3) {
            situation = "COMBOED";
            detail = combo + " hits in a row, none back";
        } else if (slam) {
            situation = profile.slams >= 3 ? "THEY_SLAM_HABIT" : "THEY_SLAM";
            detail = "Slam " + profile.slams + ", " + hp(lost) + " from " + Math.round(oppHighestAboveYou) + " blocks up";
        } else if (you <= 8) {
            situation = "YOU_LOW";
            detail = "You " + hp(you) + " vs them " + hp(them);
        } else if (profile == null) {
            return;
        } else if (opponent.getMainHandItem().is(ItemTags.AXES) && player.isBlocking()) {
            situation = "THEY_AXE";
        } else if (profile.seenTicks > 100 && profile.shieldTicks * 10 > profile.seenTicks * 3) {
            situation = "THEY_SHIELD";
            detail = "Shield up " + Math.round(100.0 * profile.shieldTicks / profile.seenTicks) + "% of the fight";
        } else if (profile.pearlsWhenLow >= 2 && them < opponent.getMaxHealth() * 0.5) {
            situation = "THEY_PEARL_HABIT";
            detail = "Pearled away low " + profile.pearlsWhenLow + " times";
        } else if (profile.groundHits >= 4 && profile.groundHits > profile.slams * 3) {
            situation = "THEY_GROUND";
            detail = profile.groundHits + " ground hits, " + profile.slams + " slams";
        } else if (you + 8 < them) {
            situation = "LOSING";
            detail = "You " + hp(you) + " vs them " + hp(them);
        } else if (!profile.weaponCalled) {
            profile.weaponCalled = true;
            ItemStack weapon = opponent.getMainHandItem();
            situation = weapon.is(Items.MACE) ? "THEY_MACE" : weapon.is(ItemTags.SPEARS) ? "THEY_SPEAR"
                    : weapon.is(ItemTags.SWORDS) ? "THEY_SWORD" : null;
        }
        if (notches % 8 == 0 && situation == null) {
            situation = "ADAPTED";
            detail = profile.hitsOnYou + " hits taken, " + profile.yourHits + " landed";
        }
        if (situation != null) {
            say(situation, now, situation.equals("RUN"), detail);
        }
    }

    private static String hp(float value) {
        return String.format(java.util.Locale.ROOT, "%.0f hp", value);
    }

    private static void say(String situation, long now, boolean urgent) {
        say(situation, now, urgent, null);
    }

    /**
     * Shows a random callout for the situation, with a line of facts under it when there is
     * one, unless a callout went up too recently.
     */
    private static void say(String situation, long now, boolean urgent, String detail) {
        if (!urgent && now - lastCalloutMs < CALLOUT_GAP_MS) {
            return;
        }
        String[] lines = AdaptCallouts.lines(situation);
        if (lines.length == 0) {
            return;
        }
        Deque<Integer> recent = RECENT.computeIfAbsent(situation, key -> new ArrayDeque<>());
        int pick = ThreadLocalRandom.current().nextInt(lines.length);
        for (int tries = 0; tries < 10 && recent.contains(pick); tries++) {
            pick = ThreadLocalRandom.current().nextInt(lines.length);
        }
        recent.addLast(pick);
        while (recent.size() > Math.min(RECENT_PER_SITUATION, lines.length - 1)) {
            recent.removeFirst();
        }
        callout = lines[pick];
        calloutDetail = detail == null ? "" : detail;
        calloutAtMs = now;
        lastCalloutMs = now;
    }

    private static Profile profile() {
        return PROFILES.computeIfAbsent(opponent.getName().getString(), key -> new Profile());
    }

    private static int totems(LocalPlayer player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(Items.TOTEM_OF_UNDYING)) {
                count++;
            }
        }
        return count;
    }

    private static boolean looksArmed(ItemStack stack) {
        return stack.is(Items.MACE) || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS);
    }

    /** Drawn by the HUD every frame: the wheel at the top and the callout in the middle. */
    static void draw(GuiGraphicsExtractor graphics, Minecraft minecraft) {
        if (!enabled) {
            return;
        }
        long now = nowMs();
        int centerX = graphics.guiWidth() / 2;
        Font font = minecraft.font;
        boolean showWheel = wheel && notches > 0;
        if (showWheel) {
            drawWheel(graphics, centerX, 36, now);
        }
        // Small and out of the way: right under the wheel (or the top of the screen).
        int y = showWheel ? 74 : 6;
        long age = now - calloutAtMs;
        if (!callout.isEmpty() && age < CALLOUT_MS) {
            int alpha = age > CALLOUT_MS - 400 ? (int) (255 * (CALLOUT_MS - age) / 400) : 255;
            int fade = Math.max(8, alpha) << 24;
            int w = font.width(callout);
            graphics.fill(centerX - w / 2 - 3, y - 2, centerX + w / 2 + 3, y + 9, Math.max(0, alpha * 120 / 255) << 24);
            graphics.text(font, callout, centerX - w / 2, y, fade | 0xFFD60A);
            y += 11;
            if (!calloutDetail.isEmpty()) {
                small(graphics, font, calloutDetail, centerX, y, fade | 0xD8D8DC);
                y += 9;
            }
        }
        if (opponent != null) {
            small(graphics, font, stats(minecraft), centerX, y, 0xC898989F);
        }
    }

    /** A line at 3/4 size, centered on x. */
    private static void small(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(0.75F, 0.75F);
        graphics.text(font, text, -font.width(text) / 2, 0, color);
        pose.popMatrix();
    }

    /** The fight at a glance: who, both health totals, your totems, and what they've done. */
    private static String stats(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        Profile profile = profile();
        String line = "vs " + opponent.getName().getString()
                + "  them " + hp(opponent.getHealth() + opponent.getAbsorptionAmount())
                + "  you " + hp(player.getHealth() + player.getAbsorptionAmount())
                + "  totems " + totems(player);
        if (profile.slams > 0) {
            line += "  slams " + profile.slams;
        }
        if (profile.pops > 0) {
            line += "  their pops " + profile.pops;
        }
        return line;
    }

    private static void drawWheel(GuiGraphicsExtractor graphics, int x, int y, long now) {
        double turn = Math.min(1, (now - notchAtMs) / (double) WHEEL_TURN_MS);
        // A little overshoot, like a heavy wheel clicking into place.
        double eased = 1 + 2.2 * Math.pow(turn - 1, 3) + 1.2 * Math.pow(turn - 1, 2);
        float angle = (float) ((notches - 1 + Math.min(1.1, eased)) * Math.PI / 4);
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        drawWheel(graphics, angle, RIM, GOLD, DARK);
        pose.popMatrix();
    }

    /** The eight-spoke wheel at the pose's origin, turned by angle (radians). */
    private static void drawWheel(GuiGraphicsExtractor graphics, float angle, int rim, int gold, int dark) {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.rotate(angle);
        for (int segment = 0; segment < 32; segment++) {
            pose.pushMatrix();
            pose.rotate((float) (segment * Math.PI * 2 / 32));
            graphics.fill(-3, -23, 3, -20, rim);
            graphics.fill(-2, -19, 2, -18, gold);
            pose.popMatrix();
        }
        for (int spoke = 0; spoke < 8; spoke++) {
            pose.pushMatrix();
            pose.rotate((float) (spoke * Math.PI / 4));
            graphics.fill(-1, 4, 1, 27, rim);
            graphics.fill(-3, 27, 3, 32, gold);
            graphics.fill(-2, 28, 2, 31, rim);
            pose.popMatrix();
        }
        graphics.fill(-5, -5, 5, 5, rim);
        graphics.fill(-2, -2, 2, 2, dark);
        pose.popMatrix();
    }

    private static long nowMs() {
        return System.nanoTime() / 1_000_000;
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

    static boolean isWheel() {
        return wheel;
    }

    static void setWheel(boolean on) {
        wheel = on;
    }

    static void toggleWheel() {
        wheel = !wheel;
    }

    /** Turns the module off when this injection is unloaded. */
    static void reset() {
        enabled = false;
        opponent = null;
        callout = "";
        notches = 0;
        PROFILES.clear();
        SEEN_PROJECTILES.clear();
    }
}
