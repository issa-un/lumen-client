package app;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.PiercingWeapon;

/**
 * The few game calls that differ between the versions Uranium runs on (compiled against
 * 26.1.2; also 26.2 and 26.3), each looked up once when the game starts:
 * - the open screen and setScreen: on Minecraft in 26.1, on minecraft.gui from 26.2;
 * - the HUD hidden by F1 and the chat: on Options/Gui in 26.1, on gui.hud from 26.2;
 * - the arm swing, the client-side swing source, the spear jab, keyboard keys and the swing
 *   packet, which 26.3 changed.
 */
final class Mc {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
    private static final MethodHandle SCREEN = screenGetter();
    private static final MethodHandle SET_SCREEN = screenSetter();
    private static final MethodHandle HUD_HIDDEN = hudHidden();
    private static final MethodHandle CHAT = chat();
    // 26.3: swing(hand, SwingAnimation, boolean) and piercingAttack(SwingAnimation, PiercingWeapon).
    private static final Method SWING_ANIMATED = find(LivingEntity.class, "swing", InteractionHand.class,
            type("net.minecraft.world.item.component.SwingAnimation"), boolean.class);
    private static final Method PIERCING_ANIMATED = find(MultiPlayerGameMode.class, "piercingAttack",
            type("net.minecraft.world.item.component.SwingAnimation"), PiercingWeapon.class);
    private static final Object DEFAULT_SWING = staticField("net.minecraft.world.item.component.SwingAnimation", "DEFAULT");
    private static final Object ATTACK_ANIMATION = staticField("net.minecraft.core.component.DataComponents", "ATTACK_ANIMATION");
    private static final Object INTERACT_ANIMATION = staticField("net.minecraft.core.component.DataComponents",
            "INTERACT_ANIMATION");
    // 26.3: InputConstants.isKeyDown(int) and the keyboard key type named KEYBOARD (KEYSYM before).
    private static final Method KEY_DOWN_NO_WINDOW = find(InputConstants.class, "isKeyDown", int.class);
    private static final InputConstants.Type KEYBOARD = keyboardType();
    private static final Class<?> SWING_PACKET = type("net.minecraft.network.protocol.game.ServerboundSwingPacket");

    private Mc() {}

    /** The open screen, or null in game. */
    static Screen screen(Minecraft minecraft) {
        try {
            return (Screen) SCREEN.invokeExact(minecraft);
        } catch (Throwable error) {
            throw new IllegalStateException(error);
        }
    }

    static void setScreen(Minecraft minecraft, Screen screen) {
        try {
            SET_SCREEN.invokeExact(minecraft, screen);
        } catch (Throwable error) {
            throw new IllegalStateException(error);
        }
    }

    /** True while F1 hides the HUD. */
    static boolean hudHidden(Minecraft minecraft) {
        try {
            return (boolean) HUD_HIDDEN.invokeExact(minecraft);
        } catch (Throwable error) {
            return false;
        }
    }

    static ChatComponent chat(Minecraft minecraft) {
        try {
            return (ChatComponent) CHAT.invokeExact(minecraft);
        } catch (Throwable error) {
            throw new IllegalStateException(error);
        }
    }

    /** Swings the arm as after using an item (26.3 picks the item's own use animation). */
    static void swing(LivingEntity entity, InteractionHand hand) {
        swing(entity, hand, false);
    }

    /** Swings the arm as after a hit (26.3 picks the item's own attack animation). */
    static void swingAttack(LivingEntity entity, InteractionHand hand) {
        swing(entity, hand, true);
    }

    private static void swing(LivingEntity entity, InteractionHand hand, boolean attack) {
        if (SWING_ANIMATED == null) {
            entity.swing(hand);
            return;
        }
        try {
            SWING_ANIMATED.invoke(entity, hand, animation(entity.getItemInHand(hand), attack), false);
        } catch (ReflectiveOperationException ignored) {
            // no swing animation; the action itself already happened
        }
    }

    /** The spear jab with the spear's piercing settings (26.3 also wants its attack animation). */
    static void piercingAttack(MultiPlayerGameMode gameMode, ItemStack spear, PiercingWeapon weapon) {
        if (PIERCING_ANIMATED == null) {
            gameMode.piercingAttack(weapon);
            return;
        }
        try {
            PIERCING_ANIMATED.invoke(gameMode, animation(spear, true), weapon);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
    }

    /** True if the game swings the arm on our side for this result (CLIENT, or PREDICTED in 26.3). */
    static boolean swingsOnClient(InteractionResult result) {
        if (!(result instanceof InteractionResult.Success success)) {
            return false;
        }
        String source = success.swingSource().name();
        return source.equals("CLIENT") || source.equals("PREDICTED");
    }

    static boolean isKeyDown(Minecraft minecraft, int key) {
        if (KEY_DOWN_NO_WINDOW == null) {
            return InputConstants.isKeyDown(minecraft.getWindow(), key);
        }
        try {
            return (boolean) KEY_DOWN_NO_WINDOW.invoke(null, key);
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    /** The keyboard key type (KEYSYM, or KEYBOARD in 26.3). */
    static InputConstants.Type keyboard() {
        return KEYBOARD;
    }

    /** An arm swing packet (gone in 26.3, where the swing rides on the attack or use). */
    static boolean isSwingPacket(Object message) {
        return SWING_PACKET != null && SWING_PACKET.isInstance(message);
    }

    private static Object animation(ItemStack stack, boolean attack) {
        Object component = attack ? ATTACK_ANIMATION : INTERACT_ANIMATION;
        Object animation = component == null ? null : stack.get((DataComponentType<?>) component);
        return animation != null ? animation : DEFAULT_SWING;
    }

    private static MethodHandle screenGetter() {
        try {
            return LOOKUP.findGetter(Minecraft.class, "screen", Screen.class);
        } catch (ReflectiveOperationException old) {
            try {
                return MethodHandles.filterReturnValue(gui(),
                        LOOKUP.findVirtual(Gui.class, "screen", MethodType.methodType(Screen.class)));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("No screen getter in this Minecraft version", error);
            }
        }
    }

    private static MethodHandle screenSetter() {
        try {
            return LOOKUP.findVirtual(Minecraft.class, "setScreen", MethodType.methodType(void.class, Screen.class));
        } catch (ReflectiveOperationException old) {
            try {
                MethodHandle set = LOOKUP.findVirtual(Gui.class, "setScreen",
                        MethodType.methodType(void.class, Screen.class));
                return MethodHandles.filterArguments(set, 0, gui());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("No setScreen in this Minecraft version", error);
            }
        }
    }

    private static MethodHandle hudHidden() {
        try {
            MethodHandle options = LOOKUP.findGetter(Minecraft.class, "options", Options.class);
            return MethodHandles.filterReturnValue(options, LOOKUP.findGetter(Options.class, "hideGui", boolean.class));
        } catch (ReflectiveOperationException old) {
            try {
                Class<?> hud = Class.forName("net.minecraft.client.gui.Hud", false, Gui.class.getClassLoader());
                MethodHandle toHud = MethodHandles.filterReturnValue(gui(), LOOKUP.findGetter(Gui.class, "hud", hud));
                return MethodHandles.filterReturnValue(toHud,
                        LOOKUP.findVirtual(hud, "isHidden", MethodType.methodType(boolean.class)));
            } catch (ReflectiveOperationException error) {
                return MethodHandles.dropArguments(MethodHandles.constant(boolean.class, false), 0, Minecraft.class);
            }
        }
    }

    private static MethodHandle chat() {
        MethodType getChat = MethodType.methodType(ChatComponent.class);
        try {
            return MethodHandles.filterReturnValue(gui(), LOOKUP.findVirtual(Gui.class, "getChat", getChat));
        } catch (ReflectiveOperationException old) {
            try {
                Class<?> hud = Class.forName("net.minecraft.client.gui.Hud", false, Gui.class.getClassLoader());
                MethodHandle toHud = MethodHandles.filterReturnValue(gui(), LOOKUP.findGetter(Gui.class, "hud", hud));
                return MethodHandles.filterReturnValue(toHud, LOOKUP.findVirtual(hud, "getChat", getChat));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("No chat in this Minecraft version", error);
            }
        }
    }

    private static MethodHandle gui() throws ReflectiveOperationException {
        return LOOKUP.findGetter(Minecraft.class, "gui", Gui.class);
    }

    private static InputConstants.Type keyboardType() {
        for (InputConstants.Type type : InputConstants.Type.values()) {
            if (type.name().equals("KEYSYM") || type.name().equals("KEYBOARD")) {
                return type;
            }
        }
        throw new IllegalStateException("No keyboard key type in this Minecraft version");
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name, false, Minecraft.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            return null;
        }
    }

    private static Method find(Class<?> owner, String name, Class<?>... parameters) {
        for (Class<?> parameter : parameters) {
            if (parameter == null) {
                return null; // a type this version does not have
            }
        }
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException missing) {
            return null;
        }
    }

    private static Object staticField(String owner, String name) {
        Class<?> type = type(owner);
        if (type == null) {
            return null;
        }
        try {
            return type.getField(name).get(null);
        } catch (ReflectiveOperationException missing) {
            return null;
        }
    }
}
