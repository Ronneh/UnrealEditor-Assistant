import java.awt.Component;
import java.awt.Font;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

/** Development-only WYSIWYG editor for repository-backed tutorial HTML. */
public final class TutorialEditorExtension implements EditorHelpAuthoringExtension {
    private Host host;
    private JButton edit;
    private JButton save;
    private JButton cancel;
    private JToggleButton bold;
    private JToggleButton italic;
    private JComboBox<Integer> fontSize;
    private Path editingFile;
    private Path originalFile;

    @Override public void install(Host host) {
        this.host = host;
        edit = new JButton("Edit");
        save = new JButton("Save");
        cancel = new JButton("Cancel");
        bold = new JToggleButton("B");
        italic = new JToggleButton("I");
        bold.setFont(bold.getFont().deriveFont(Font.BOLD));
        italic.setFont(italic.getFont().deriveFont(Font.ITALIC));
        fontSize = new JComboBox<>(new Integer[] {10, 12, 14, 16, 18, 20, 24, 28, 32});
        fontSize.setSelectedItem(14);

        edit.addActionListener(event -> beginEditing());
        save.addActionListener(event -> save());
        cancel.addActionListener(event -> finish(false));
        bold.addActionListener(event -> apply(new StyledEditorKit.BoldAction()));
        italic.addActionListener(event -> apply(new StyledEditorKit.ItalicAction()));
        fontSize.addActionListener(event -> {
            if (editingFile != null && fontSize.getSelectedItem() instanceof Integer size) {
                apply(new StyledEditorKit.FontSizeAction("font-size", size));
            }
        });

        host.toolbar().add(edit);
        host.toolbar().add(save);
        host.toolbar().add(cancel);
        host.toolbar().add(bold);
        host.toolbar().add(italic);
        host.toolbar().add(fontSize);
        showEditingControls(false);
        documentChanged(host.currentArticlePath());
    }

    @Override public void documentChanged(Path articlePath) {
        if (editingFile == null) edit.setEnabled(articlePath != null);
    }

    private void beginEditing() {
        Path file = host.currentArticlePath();
        Path savePath = host.authoringSavePath();
        if (file == null || savePath == null) {
            DarkDialogs.message(host.parent(), "Open a tutorial before starting the editor.",
                    "Nothing to edit", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            Path input = Files.isRegularFile(savePath) ? savePath : file;
            JEditorPane article = host.article();
            HTMLEditorKit kit = new HTMLEditorKit();
            HTMLDocument document = (HTMLDocument) kit.createDefaultDocument();
            document.setBase(file.toUri().toURL());
            document.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
            try (StringReader reader = new StringReader(Files.readString(input, StandardCharsets.UTF_8))) {
                kit.read(reader, document, 0);
            }
            article.setEditorKit(kit);
            article.setDocument(document);
            article.setEditable(true);
            article.requestFocusInWindow();
            editingFile = savePath;
            originalFile = file;
            host.setNavigationEnabled(false);
            showEditingControls(true);
            host.setStatus("Editing " + file.getFileName() + " (development mode)");
        } catch (Exception exception) {
            DarkDialogs.message(host.parent(), "The tutorial could not be opened for editing:\n"
                    + exception.getMessage(), "Cannot edit tutorial", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void save() {
        if (editingFile == null) return;
        try {
            JEditorPane article = host.article();
            StringWriter output = new StringWriter();
            ((HTMLEditorKit) article.getEditorKit()).write(
                    output, article.getDocument(), 0, article.getDocument().getLength());
            Files.createDirectories(editingFile.getParent());
            Files.writeString(editingFile, output.toString(), StandardCharsets.UTF_8);
            Files.writeString(originalFile, output.toString(), StandardCharsets.UTF_8);
            Path saved = editingFile;
            finish(true);
            host.setStatus("Saved repository tutorial: " + saved);
        } catch (Exception exception) {
            DarkDialogs.message(host.parent(), "The tutorial could not be saved:\n"
                    + exception.getMessage(), "Cannot save tutorial", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void finish(boolean saved) {
        if (editingFile == null) return;
        editingFile = null;
        originalFile = null;
        host.article().setEditable(false);
        host.setNavigationEnabled(true);
        showEditingControls(false);
        host.reloadCurrentArticle();
        if (!saved) host.setStatus("Tutorial changes discarded");
    }

    private void apply(javax.swing.Action action) {
        Component focus = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (editingFile != null) action.actionPerformed(new java.awt.event.ActionEvent(
                focus == null ? host.article() : focus,
                java.awt.event.ActionEvent.ACTION_PERFORMED, "format"));
        SwingUtilities.invokeLater(() -> host.article().requestFocusInWindow());
    }

    private void showEditingControls(boolean editing) {
        edit.setVisible(!editing);
        save.setVisible(editing);
        cancel.setVisible(editing);
        bold.setVisible(editing);
        italic.setVisible(editing);
        fontSize.setVisible(editing);
        host.toolbar().revalidate();
        host.toolbar().repaint();
    }
}
