import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import java.awt.Window;
import java.util.Locale;

/** Applies native Windows dark-mode styling to an already displayable Swing window. */
final class WindowsTitleBar {
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19;
    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;

    private WindowsTitleBar() { }

    static void enableDark(Window window) {
        if (!isWindows(System.getProperty("os.name", "")) || window == null) return;
        try {
            if (!window.isDisplayable()) window.addNotify();
            Pointer handle = Native.getComponentPointer(window);
            try (Memory enabled = new Memory(Integer.BYTES)) {
                enabled.setInt(0, 1);
                int result = DwmApi.INSTANCE.DwmSetWindowAttribute(
                        handle, DWMWA_USE_IMMERSIVE_DARK_MODE, enabled, Integer.BYTES);
                if (result != 0) {
                    DwmApi.INSTANCE.DwmSetWindowAttribute(
                            handle, DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1,
                            enabled, Integer.BYTES);
                }
            }
        } catch (Throwable ignored) {
            // Unsupported Windows builds keep their normal system title bar.
        }
    }

    static boolean isWindows(String osName) {
        return osName.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private interface DwmApi extends Library {
        DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);

        int DwmSetWindowAttribute(Pointer windowHandle, int attribute,
                Pointer attributeValue, int attributeSize);
    }
}
