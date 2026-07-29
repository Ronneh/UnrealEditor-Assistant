import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;

/** Shared access to plain text copied to the system clipboard. */
public final class ClipboardTextSupport {
    private ClipboardTextSupport() { }

    public static String readText() throws Exception {
        Object value = Toolkit.getDefaultToolkit().getSystemClipboard()
                .getData(DataFlavor.stringFlavor);
        if (!(value instanceof String text) || text.isEmpty()) {
            throw new IllegalArgumentException("The clipboard does not contain text.");
        }
        return text;
    }
}
