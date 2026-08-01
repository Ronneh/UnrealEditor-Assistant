import java.awt.BorderLayout;
import java.awt.Component;
import java.util.prefs.Preferences;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** Shared, permanently suppressible confirmation for deleting notes and folders. */
public final class DeleteConfirmationSupport {
    private static final String SKIP_KEY = "skipNoteDeleteConfirmation";
    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(DeleteConfirmationSupport.class);
    private DeleteConfirmationSupport() { }

    public static boolean confirm(Component parent, String name) {
        if (PREFERENCES.getBoolean(SKIP_KEY, false)) return true;
        JCheckBox neverAsk = new JCheckBox("Don't ask me again");
        JPanel message = new JPanel(new BorderLayout(0, 10));
        message.add(new JLabel("Delete \"" + name + "\"? This cannot be undone."), BorderLayout.NORTH);
        message.add(neverAsk, BorderLayout.SOUTH);
        int result = DarkDialogs.confirm(parent, message, "Delete item",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result == JOptionPane.YES_OPTION && neverAsk.isSelected())
            PREFERENCES.putBoolean(SKIP_KEY, true);
        return result == JOptionPane.YES_OPTION;
    }
}
