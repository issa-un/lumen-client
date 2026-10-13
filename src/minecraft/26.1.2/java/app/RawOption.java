package app;

import java.lang.reflect.Field;
import net.minecraft.client.OptionInstance;

/**
 * Sets a video option past the range its slider allows (brightness for Fullbright, field of
 * view for Zoom). The option's set() turns anything outside its range back into the default,
 * so the stored value is written directly; the game reads it every frame. The modules put
 * the old value back when they turn off (or on Unload).
 */
final class RawOption {
    private static Field value;

    private RawOption() {}

    static <T> void set(OptionInstance<T> option, T newValue) {
        try {
            if (value == null) {
                value = OptionInstance.class.getDeclaredField("value");
                value.setAccessible(true);
            }
            value.set(option, newValue);
        } catch (ReflectiveOperationException ignored) {
            // The option just keeps its normal value.
        }
    }
}
