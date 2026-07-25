import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreePath;

/** Persistent per-map folders with rich-text notes and a read-only preview. */
public final class NotesPanel extends JPanel {
    private static final String EMPTY_HTML = "<html><body><p></p></body></html>";
    private final Path storageRoot;
    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(new Entry("Maps", null, true));
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree tree = new JTree(treeModel);
    private final JEditorPane editor = htmlPane(true);
    private final JLabel editorTitle = new JLabel("Select or create a note");
    private Path selectedNote;
    private boolean loading;

    public NotesPanel() {
        super(new BorderLayout(6, 6));
        setOpaque(false);
        setPreferredSize(new Dimension(445, 220));
        storageRoot = resolveStorageRoot();
        initializeStorage();
        add(createHeader(), BorderLayout.NORTH);
        add(createWorkspace(), BorderLayout.CENTER);
        installShortcuts();
        reloadTree(null);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("To-Do List");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        header.add(title, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        actions.add(button("+ Map", event -> createMap()));
        actions.add(button("+ Note", event -> createNote()));
        actions.add(button("Rename", event -> renameSelection()));
        actions.add(button("Delete", event -> deleteSelection()));
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private javax.swing.JComponent createWorkspace() {
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setBackground(AssistantTheme.PANEL_ALT);
        tree.setForeground(AssistantTheme.TEXT);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public java.awt.Component getTreeCellRendererComponent(
                    JTree tree, Object value, boolean selected, boolean expanded,
                    boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                if (value instanceof DefaultMutableTreeNode node
                        && node.getUserObject() instanceof Entry entry && entry.folder) {
                    setIcon(UIManager.getIcon(expanded ? "Tree.openIcon" : "Tree.closedIcon"));
                }
                return this;
            }
        });
        tree.addTreeSelectionListener(event -> selectEntry());

        JPanel editorPanel = new JPanel(new BorderLayout(0, 3));
        editorPanel.setOpaque(false);
        editorTitle.setForeground(AssistantTheme.MUTED);
        editorPanel.add(editorTitle, BorderLayout.NORTH);
        editorPanel.add(createEditorArea(), BorderLayout.CENTER);

        JSplitPane all = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(tree), editorPanel);
        all.setResizeWeight(0.28);
        all.setDividerSize(5);
        all.setBorder(BorderFactory.createEmptyBorder());
        return all;
    }

    private JPanel createEditorArea() {
        JPanel panel = new JPanel(new BorderLayout(0, 3));
        panel.setOpaque(false);
        JPanel toolbar = new JPanel();
        toolbar.setOpaque(false);
        toolbar.setLayout(new FlowLayout(FlowLayout.LEFT, 3, 0));
        JComboBox<Integer> sizes = new JComboBox<>(new Integer[] { 10, 12, 14, 16, 18, 24, 32 });
        sizes.setSelectedItem(14);
        sizes.setPreferredSize(new Dimension(48, 23));
        sizes.addActionListener(event -> applyFontSize((Integer) sizes.getSelectedItem()));
        toolbar.add(sizes);
        toolbar.add(button("B", event -> toggleStyle("bold")));
        toolbar.add(button("I", event -> toggleStyle("italic")));
        toolbar.add(button("U", event -> toggleStyle("underline")));
        toolbar.add(button("A", event -> chooseTextColor()));
        toolbar.add(button("•", event -> insertBullet()));
        toolbar.add(button("Save", event -> saveCurrent()));
        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(editor), BorderLayout.CENTER);

        editor.setEnabled(false);
        return panel;
    }

    private static JEditorPane htmlPane(boolean editable) {
        JEditorPane pane = new JEditorPane();
        HTMLEditorKit kit = new HTMLEditorKit();
        StyleSheet styles = new StyleSheet();
        styles.addRule("body { color: #e8edf4; font-family: Verdana; font-size: 14pt; }");
        styles.addRule("p { margin-top: 0; margin-bottom: 0; }");
        styles.addRule("ul { margin-top: 0; margin-bottom: 0; padding-left: 0; }");
        styles.addRule("li { margin-top: 0; margin-bottom: 0; }");
        kit.setStyleSheet(styles);
        pane.setEditorKit(kit);
        pane.setContentType("text/html");
        pane.setEditable(editable);
        pane.setBackground(new Color(17, 21, 27));
        pane.setForeground(AssistantTheme.TEXT);
        pane.setCaretColor(AssistantTheme.TEXT);
        pane.setText(EMPTY_HTML);
        return pane;
    }

    private JButton button(String text, java.util.function.Consumer<ActionEvent> action) {
        JButton button = new JButton(text);
        button.setMargin(new java.awt.Insets(2, 6, 2, 6));
        button.addActionListener(action::accept);
        return button;
    }

    private JButton actionButton(String text, javax.swing.Action action) {
        JButton button = new JButton(action);
        button.setText(text);
        button.setMargin(new java.awt.Insets(2, 6, 2, 6));
        return button;
    }

    private void applyFontSize(int size) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setFontSize(attributes, size);
        applyCharacterAttributes(attributes);
    }

    private void applyCharacterAttributes(SimpleAttributeSet attributes) {
        if (!editor.isEnabled() || !(editor.getDocument() instanceof javax.swing.text.StyledDocument document)) return;
        int start = editor.getSelectionStart();
        int length = editor.getSelectionEnd() - start;
        if (length > 0) document.setCharacterAttributes(start, length, attributes, false);
        else if (editor.getEditorKit() instanceof StyledEditorKit kit)
            kit.getInputAttributes().addAttributes(attributes);
        editor.requestFocusInWindow();
    }

    private void chooseTextColor() {
        Color color = RgbColorPicker.show(this, "Text Color", AssistantTheme.TEXT);
        if (color == null) return;
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setForeground(attributes, color);
        applyCharacterAttributes(attributes);
    }

    private void toggleStyle(String style) {
        if (!(editor.getEditorKit() instanceof StyledEditorKit kit)) return;
        javax.swing.text.AttributeSet current = kit.getInputAttributes();
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        if ("bold".equals(style)) StyleConstants.setBold(attributes, !StyleConstants.isBold(current));
        else if ("italic".equals(style)) StyleConstants.setItalic(attributes, !StyleConstants.isItalic(current));
        else StyleConstants.setUnderline(attributes, !StyleConstants.isUnderline(current));
        applyCharacterAttributes(attributes);
    }

    private void insertBullet() {
        if (!editor.isEnabled()) return;
        try {
            editor.getDocument().insertString(editor.getCaretPosition(), "• ", null);
            editor.requestFocusInWindow();
        } catch (javax.swing.text.BadLocationException exception) {
            throw new IllegalStateException("Could not insert bullet.", exception);
        }
    }

    private void installShortcuts() {
        tree.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "deleteEntry");
        tree.getActionMap().put("deleteEntry", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { deleteSelection(); }
        });
        editor.getInputMap().put(KeyStroke.getKeyStroke("control S"), "saveNote");
        editor.getActionMap().put("saveNote", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { saveCurrent(); }
        });
    }

    private void createMap() {
        String name = askName("New map", "Map name:");
        if (name == null) return;
        try {
            Path folder = uniquePath(storageRoot, safeName(name), "");
            Files.createDirectories(folder);
            reloadTree(folder);
        } catch (IOException exception) {
            showError("Could not create the map folder.", exception);
        }
    }

    private void createNote() {
        Path folder = selectedFolder();
        if (folder == null || folder.equals(storageRoot)) {
            JOptionPane.showMessageDialog(this, "Select or create a map folder first.",
                    "No map selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = askName("New note", "Note name:");
        if (name == null) return;
        try {
            Path note = uniquePath(folder, safeName(name), ".html");
            Files.writeString(note, EMPTY_HTML, StandardCharsets.UTF_8);
            reloadTree(note);
        } catch (IOException exception) {
            showError("Could not create the note.", exception);
        }
    }

    private void selectEntry() {
        saveCurrent();
        Entry entry = selectedEntry();
        if (entry == null || entry.folder || entry.path == null) {
            selectedNote = null;
            loading = true;
            editor.setText(EMPTY_HTML);
            loading = false;
            editor.setEnabled(false);
            editorTitle.setText(entry != null && entry.folder ? entry.name : "Select or create a note");
            return;
        }
        try {
            selectedNote = entry.path;
            loading = true;
            String html = Files.readString(selectedNote, StandardCharsets.UTF_8);
            editor.setText(html);
            editor.setCaretPosition(0);
            loading = false;
            editor.setEnabled(true);
            editorTitle.setText(entry.name);
        } catch (IOException exception) {
            loading = false;
            showError("Could not open the note.", exception);
        }
    }

    private void saveCurrent() {
        if (selectedNote == null || loading || !editor.isEnabled()) return;
        try {
            Files.writeString(selectedNote, editor.getText(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            showError("Could not save the note.", exception);
        }
    }

    private void renameSelection() {
        Entry entry = selectedEntry();
        if (entry == null || entry.path == null) return;
        String name = askName("Rename", "New name:", entry.name);
        if (name == null) return;
        saveCurrent();
        String suffix = entry.folder ? "" : ".html";
        Path target = entry.path.resolveSibling(safeName(name) + suffix);
        if (Files.exists(target)) {
            JOptionPane.showMessageDialog(this, "An item with this name already exists.",
                    "Rename", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Files.move(entry.path, target, StandardCopyOption.ATOMIC_MOVE);
            selectedNote = null;
            reloadTree(target);
        } catch (IOException exception) {
            showError("Could not rename the item.", exception);
        }
    }

    private void deleteSelection() {
        Entry entry = selectedEntry();
        if (entry == null || entry.path == null) return;
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete \"" + entry.name + "\"? This cannot be undone.",
                "Delete item", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;
        try {
            if (entry.folder) {
                try (var paths = Files.walk(entry.path)) {
                    paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try { Files.delete(path); }
                        catch (IOException exception) { throw new DeleteFailure(exception); }
                    });
                } catch (DeleteFailure failure) {
                    throw (IOException) failure.getCause();
                }
            } else {
                Files.deleteIfExists(entry.path);
            }
            selectedNote = null;
            reloadTree(null);
        } catch (IOException exception) {
            showError("Could not delete the item.", exception);
        }
    }

    private void reloadTree(Path select) {
        rootNode.removeAllChildren();
        try (var folders = Files.list(storageRoot)) {
            folders.filter(Files::isDirectory).sorted().forEach(folder -> {
                DefaultMutableTreeNode mapNode = new DefaultMutableTreeNode(
                        new Entry(folder.getFileName().toString(), folder, true));
                try (var notes = Files.list(folder)) {
                    notes.filter(path -> path.getFileName().toString().endsWith(".html")).sorted()
                            .forEach(note -> mapNode.add(new DefaultMutableTreeNode(new Entry(
                                    stripExtension(note.getFileName().toString()), note, false))));
                } catch (IOException ignored) { }
                rootNode.add(mapNode);
            });
        } catch (IOException exception) {
            showError("Could not load map notes.", exception);
        }
        treeModel.reload();
        for (int row = 0; row < tree.getRowCount(); row++) tree.expandRow(row);
        if (select != null) selectPath(rootNode, select);
    }

    private boolean selectPath(DefaultMutableTreeNode node, Path path) {
        Entry entry = (Entry) node.getUserObject();
        if (path.equals(entry.path)) {
            tree.setSelectionPath(new TreePath(node.getPath()));
            tree.scrollPathToVisible(new TreePath(node.getPath()));
            return true;
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            if (selectPath((DefaultMutableTreeNode) node.getChildAt(index), path)) return true;
        }
        return false;
    }

    private Path selectedFolder() {
        Entry entry = selectedEntry();
        if (entry == null) return null;
        return entry.folder ? entry.path : entry.path.getParent();
    }

    private Entry selectedEntry() {
        Object selected = tree.getLastSelectedPathComponent();
        return selected instanceof DefaultMutableTreeNode node ? (Entry) node.getUserObject() : null;
    }

    private String askName(String title, String message) {
        return askName(title, message, "");
    }

    private String askName(String title, String message, String initial) {
        String value = (String) JOptionPane.showInputDialog(this, message, title,
                JOptionPane.PLAIN_MESSAGE, null, null, initial);
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static Path resolveStorageRoot() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".unreal-editor-2-assistant")
                : Path.of(localAppData, "UnrealEditor2Assistant");
        return base.resolve("MapNotes");
    }

    private void initializeStorage() {
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException exception) {
            showError("Could not initialize map notes.", exception);
        }
    }

    private static Path uniquePath(Path parent, String base, String suffix) {
        Path candidate = parent.resolve(base + suffix);
        int number = 2;
        while (Files.exists(candidate)) candidate = parent.resolve(base + " " + number++ + suffix);
        return candidate;
    }

    private static String safeName(String name) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return safe.isEmpty() ? "Untitled" : safe;
    }

    private static String stripExtension(String name) {
        return name.substring(0, name.length() - ".html".length());
    }

    private void showError(String message, Exception exception) {
        JOptionPane.showMessageDialog(this, message + "\n" + exception.getMessage(),
                "Map notes", JOptionPane.ERROR_MESSAGE);
    }

    private record Entry(String name, Path path, boolean folder) {
        @Override public String toString() { return name; }
    }

    private static final class DeleteFailure extends RuntimeException {
        DeleteFailure(IOException cause) { super(cause); }
    }
}
