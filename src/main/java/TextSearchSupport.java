import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/** Adds a reusable, modeless Ctrl+F search window to a Swing text component. */
public final class TextSearchSupport {
    private final JTextComponent textComponent;
    private final Component ownerReference;
    private final String containerName;
    private String searchText = "";
    private JDialog dialog;
    private JTextField searchField;

    private TextSearchSupport(JTextComponent textComponent, Component ownerReference, String containerName) {
        this.textComponent = textComponent;
        this.ownerReference = ownerReference;
        this.containerName = containerName;
        installActions();
    }

    public static void install(JTextComponent textComponent, Component ownerReference, String containerName) {
        new TextSearchSupport(textComponent, ownerReference, containerName);
    }

    private void installActions() {
        textComponent.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("control F"), "findText");
        textComponent.getActionMap().put("findText", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                showDialog();
            }
        });
        textComponent.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("F3"), "findNextText");
        textComponent.getActionMap().put("findNextText", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                findOrShow(true);
            }
        });
        textComponent.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("shift F3"), "findPreviousText");
        textComponent.getActionMap().put("findPreviousText", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                findOrShow(false);
            }
        });
    }

    private void showDialog() {
        if (dialog == null) createDialog();
        String selectedText = textComponent.getSelectedText();
        if (selectedText != null && !selectedText.isBlank()) {
            searchField.setText(selectedText);
        } else if (searchField.getText().isEmpty()) {
            searchField.setText(searchText);
        }
        dialog.setLocationRelativeTo(ownerReference);
        dialog.setVisible(true);
        dialog.toFront();
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    private void createDialog() {
        Window owner = SwingUtilities.getWindowAncestor(ownerReference);
        dialog = new JDialog(owner, "Find in " + containerName, Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
        searchField = new JTextField(28);
        searchField.addActionListener(event -> searchFromDialog(true));

        JButton previous = new JButton("Previous");
        previous.addActionListener(event -> searchFromDialog(false));
        JButton next = new JButton("Next");
        next.addActionListener(event -> searchFromDialog(true));
        JButton close = new JButton("Close");
        close.addActionListener(event -> dialog.setVisible(false));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(searchField, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(previous);
        buttons.add(next);
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.pack();
    }

    private void findOrShow(boolean forward) {
        if (searchText.isEmpty()) {
            showDialog();
        } else {
            findMatch(forward);
        }
    }

    private void searchFromDialog(boolean forward) {
        searchText = searchField.getText();
        if (searchText.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        findMatch(forward);
        searchField.requestFocusInWindow();
        SwingUtilities.invokeLater(() -> textComponent.getCaret().setSelectionVisible(true));
    }

    private void findMatch(boolean forward) {
        String content = textComponent.getText();
        String haystack = content.toLowerCase(Locale.ROOT);
        String needle = searchText.toLowerCase(Locale.ROOT);
        int match;

        if (forward) {
            int start = Math.max(textComponent.getSelectionEnd(), textComponent.getCaretPosition());
            match = haystack.indexOf(needle, start);
            if (match < 0 && start > 0) match = haystack.indexOf(needle);
        } else {
            int start = Math.min(textComponent.getSelectionStart(), textComponent.getCaretPosition()) - 1;
            match = start >= 0 ? haystack.lastIndexOf(needle, start) : -1;
            if (match < 0 && start < haystack.length() - 1) match = haystack.lastIndexOf(needle);
        }

        if (match < 0) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        textComponent.select(match, match + searchText.length());
    }
}
