import java.awt.Component;
import java.io.File;
import javax.swing.JDialog;
import javax.swing.JFileChooser;

/** JFileChooser that applies the native dark title bar before becoming visible. */
public final class DarkFileChooser extends JFileChooser {
    public DarkFileChooser() { super(); }
    public DarkFileChooser(File directory) { super(directory); }

    @Override protected JDialog createDialog(Component parent) {
        JDialog dialog = super.createDialog(parent);
        WindowsTitleBar.enableDark(dialog);
        return dialog;
    }
}
