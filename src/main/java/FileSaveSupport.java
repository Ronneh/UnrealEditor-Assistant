import java.awt.Component;
import java.io.File;
import javax.swing.JOptionPane;

/** Shared safeguards for file exports and Save As operations. */
public final class FileSaveSupport {
    private FileSaveSupport() { }

    public static boolean confirmOverwrite(Component parent, File file) {
        if (!file.exists()) return true;
        return JOptionPane.showConfirmDialog(
                parent,
                "\"" + file.getName() + "\" already exists.\nReplace it?",
                "Replace existing file",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
