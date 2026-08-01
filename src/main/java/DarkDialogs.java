import java.awt.Component;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

/** JOptionPane helpers that style the native title bar before showing a dialog. */
public final class DarkDialogs {
    private DarkDialogs() { }

    public static void message(Component parent, Object message, String title, int messageType) {
        JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.DEFAULT_OPTION);
        show(pane, parent, title);
    }

    public static void message(Component parent, Object message) {
        message(parent, message, "Message", JOptionPane.INFORMATION_MESSAGE);
    }

    public static int confirm(Component parent, Object message, String title,
                              int optionType, int messageType) {
        JOptionPane pane = new JOptionPane(message, messageType, optionType);
        show(pane, parent, title);
        Object value = pane.getValue();
        return value instanceof Integer number ? number : JOptionPane.CLOSED_OPTION;
    }

    public static int confirm(Component parent, Object message, String title, int optionType) {
        return confirm(parent, message, title, optionType, JOptionPane.QUESTION_MESSAGE);
    }

    public static Object input(Component parent, Object message, String title, int messageType,
                               Icon icon, Object[] choices, Object initial) {
        JOptionPane pane = new JOptionPane(message, messageType, JOptionPane.OK_CANCEL_OPTION,
                icon, null, null);
        pane.setWantsInput(true);
        pane.setSelectionValues(choices);
        pane.setInitialSelectionValue(initial);
        show(pane, parent, title);
        return pane.getInputValue() == JOptionPane.UNINITIALIZED_VALUE ? null : pane.getInputValue();
    }

    private static void show(JOptionPane pane, Component parent, String title) {
        JDialog dialog = pane.createDialog(parent, title);
        WindowsTitleBar.enableDark(dialog);
        dialog.setVisible(true);
        dialog.dispose();
    }
}
