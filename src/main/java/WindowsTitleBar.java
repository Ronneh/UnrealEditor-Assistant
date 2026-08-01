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
    private static final int DWMWA_BORDER_COLOR = 34;
    private static final int DWMWA_CAPTION_COLOR = 35;
    private static final int DWMWA_TEXT_COLOR = 36;
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_FRAMECHANGED = 0x0020;

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
                setColor(handle, DWMWA_CAPTION_COLOR, AssistantTheme.BACKGROUND);
                setColor(handle, DWMWA_TEXT_COLOR, AssistantTheme.TEXT);
                setColor(handle, DWMWA_BORDER_COLOR, AssistantTheme.BORDER);
                DwmApi.INSTANCE.DwmFlush();
                User32.INSTANCE.SetWindowPos(handle, null, 0, 0, 0, 0,
                        SWP_NOSIZE | SWP_NOMOVE | SWP_NOZORDER
                                | SWP_NOACTIVATE | SWP_FRAMECHANGED);
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
        int DwmFlush();
    }

    private static void setColor(Pointer windowHandle, int attribute, java.awt.Color color) {
        int colorRef = color.getRed() | color.getGreen() << 8 | color.getBlue() << 16;
        try (Memory value = new Memory(Integer.BYTES)) {
            value.setInt(0, colorRef);
            DwmApi.INSTANCE.DwmSetWindowAttribute(
                    windowHandle, attribute, value, Integer.BYTES);
        }
    }

    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);

        boolean SetWindowPos(Pointer windowHandle, Pointer insertAfter,
                int x, int y, int width, int height, int flags);
    }
}
