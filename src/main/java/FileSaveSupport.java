import java.awt.Component;
import java.io.File;
import javax.swing.JOptionPane;

/** Shared safeguards for file exports and Save As operations. */
public final class FileSaveSupport {
    private FileSaveSupport() { }

    public static boolean confirmOverwrite(Component parent, File file) {
        if (!file.exists()) return true;
        return DarkDialogs.confirm(
                parent,
                "\"" + file.getName() + "\" already exists.\nReplace it?",
                "Replace existing file",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public static File preferredDirectory(String savedDirectory, File userHome) {
        if (savedDirectory != null) {
            File saved = new File(savedDirectory);
            if (saved.isDirectory()) return saved;
        }
        File desktop = new File(userHome, "Desktop");
        return desktop.isDirectory() ? desktop : userHome;
    }

    public static File ensureImageExtension(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".png") || name.endsWith(".bmp")) return file;
        return new File(file.getParentFile(), file.getName() + ".png");
    }

    public static String imageFormat(File file) {
        return file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".bmp") ? "bmp" : "png";
    }
}
