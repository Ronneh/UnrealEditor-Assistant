import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
import javax.swing.DropMode;
import javax.swing.JEditorPane;
import javax.swing.Icon;
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
    private static final int ACTION_GAP = 4;
    private final Path storageRoot;
    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree tree = new JTree(treeModel);
    private final JEditorPane editor = htmlPane(true);
    private final JLabel editorTitle = new JLabel("Select or create a note");
    private JButton addFolderButton;
    private JButton addNoteButton;
    private Path selectedNote;
    private boolean loading;

    public NotesPanel() {
        super(new BorderLayout(6, 6));
        setOpaque(false);
        setPreferredSize(new Dimension(445, 220));
        storageRoot = resolveStorageRoot();
        initializeStorage();
        rootNode.setUserObject(new Entry("All notes", storageRoot, true));
        JPanel actions = createActions();
        add(createHeader(), BorderLayout.NORTH);
        add(createWorkspace(), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
        installShortcuts();
        reloadTree(null);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("To-Do List");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        header.add(title, BorderLayout.WEST);
        return header;
    }

    private JPanel createActions() {
        JPanel actions = new JPanel(new EdgeAlignedFlowLayout(FlowLayout.LEFT, ACTION_GAP, 0));
        actions.setOpaque(false);
        addFolderButton = button("+ Folder", event -> createFolder());
        addNoteButton = button("+ Note", event -> createNote());
        actions.add(addFolderButton);
        actions.add(addNoteButton);
        actions.add(button("Rename", event -> renameSelection()));
        actions.add(button("Delete", event -> deleteSelection()));
        actions.add(button("Save", event -> saveCurrent()));
        return actions;
    }

    private javax.swing.JComponent createWorkspace() {
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setBackground(AssistantTheme.PANEL_ALT);
        tree.setForeground(AssistantTheme.TEXT);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            {
                setBackgroundNonSelectionColor(AssistantTheme.PANEL_ALT);
                setBackgroundSelectionColor(AssistantTheme.ACCENT_DARK);
                setTextNonSelectionColor(AssistantTheme.TEXT);
                setTextSelectionColor(AssistantTheme.TEXT);
                setBorderSelectionColor(AssistantTheme.ACCENT);
            }

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
        tree.setDropMode(DropMode.ON_OR_INSERT);
        if (!java.awt.GraphicsEnvironment.isHeadless()) tree.setDragEnabled(true);
        tree.setTransferHandler(new FileTreeReorderHandler(tree, storageRoot,
                value -> ((Entry) value).path, this::reloadAfterMove,
                exception -> showError("Could not move the item.", exception), null));

        JPanel editorPanel = new JPanel(new BorderLayout(0, 3));
        editorPanel.setOpaque(false);
        editorTitle.setForeground(AssistantTheme.MUTED);
        JPanel editorHeader = new JPanel(new BorderLayout(0, 3));
        editorHeader.setOpaque(false);
        editorHeader.add(editorTitle, BorderLayout.NORTH);
        editorHeader.add(createFormattingToolbar(), BorderLayout.SOUTH);
        editorPanel.add(editorHeader, BorderLayout.NORTH);
        JScrollPane editorScroll = new JScrollPane(editor);
        editorScroll.setBorder(BorderFactory.createLineBorder(AssistantTheme.BORDER));
        editorPanel.add(editorScroll, BorderLayout.CENTER);
        editor.setEnabled(false);

        JScrollPane treeScroll = new JScrollPane(tree);
        int dividerLocation = addFolderButton.getPreferredSize().width
                + ACTION_GAP + addNoteButton.getPreferredSize().width + ACTION_GAP / 2;
        treeScroll.setPreferredSize(new Dimension(dividerLocation, 180));
        JSplitPane all = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treeScroll, editorPanel);
        all.setResizeWeight(0.42);
        AssistantTheme.styleSplitPane(all);
        all.setDividerLocation(dividerLocation);
        SplitPaneState.install(all, NotesPanel.class, "notes-editor");
        return all;
    }

    private JPanel createFormattingToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setOpaque(false);
        toolbar.setLayout(new EdgeAlignedFlowLayout(FlowLayout.LEFT, 3, 0));
        JComboBox<Integer> sizes = new JComboBox<>(new Integer[] { 10, 12, 14, 16, 18, 24, 32 });
        sizes.setSelectedItem(14);
        sizes.setPreferredSize(new Dimension(48, 23));
        sizes.addActionListener(event -> applyFontSize((Integer) sizes.getSelectedItem()));
        toolbar.add(sizes);
        toolbar.add(button("B", event -> toggleStyle("bold")));
        toolbar.add(button("I", event -> toggleStyle("italic")));
        toolbar.add(button("U", event -> toggleStyle("underline")));
        toolbar.add(button("\u2022", event -> insertBullet()));
        JButton colorButton = button("", event -> chooseTextColor());
        colorButton.setIcon(new ColorCircleIcon());
        colorButton.setToolTipText("Text color");
        toolbar.add(colorButton);
        return toolbar;
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
            editor.getDocument().insertString(editor.getCaretPosition(), "\u2022 ", null);
            editor.requestFocusInWindow();
        } catch (javax.swing.text.BadLocationException exception) {
            throw new IllegalStateException("Could not insert bullet.", exception);
        }
    }

    private void insertBulletLineBreak(javax.swing.Action insertBreak, ActionEvent event) {
        if (editor.getSelectionStart() != editor.getSelectionEnd()) {
            insertBreak.actionPerformed(event);
            return;
        }
        javax.swing.text.Document document = editor.getDocument();
        int caret = editor.getCaretPosition();
        try {
            String text = document.getText(0, document.getLength());
            int start = text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
            int end = text.indexOf('\n', caret);
            if (end < 0) end = text.length();
            String line = text.substring(start, end);
            String bulletPrefix = "\u2022 ";
            if (!line.startsWith(bulletPrefix)) {
                insertBreak.actionPerformed(event);
                return;
            }
            if (line.substring(bulletPrefix.length()).trim().isEmpty()) {
                document.remove(start, bulletPrefix.length());
                editor.setCaretPosition(start);
                insertBreak.actionPerformed(event);
                return;
            }
            insertBreak.actionPerformed(event);
            document.insertString(editor.getCaretPosition(), bulletPrefix, null);
        } catch (javax.swing.text.BadLocationException exception) {
            throw new IllegalStateException("Could not continue the bullet list.", exception);
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
        javax.swing.Action insertBreak = editor.getActionMap().get(DefaultEditorKit.insertBreakAction);
        editor.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "bulletLineBreak");
        editor.getActionMap().put("bulletLineBreak", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                insertBulletLineBreak(insertBreak, event);
            }
        });
    }

    private void createFolder() {
        Path parent = selectedFolder();
        if (parent == null) parent = storageRoot;
        String name = askName("New folder", "Folder name:");
        if (name == null) return;
        try {
            Path folder = uniquePath(parent, safeName(name), "");
            Files.createDirectories(folder);
            reloadTree(folder);
        } catch (IOException exception) {
            showError("Could not create the folder.", exception);
        }
    }

    private void createNote() {
        Path folder = selectedFolder();
        if (folder == null) {
            DarkDialogs.message(this, "Select a folder first.",
                    "No folder selected", JOptionPane.INFORMATION_MESSAGE);
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
        if (!saveCurrent()) return;
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

    private boolean saveCurrent() {
        if (selectedNote == null || loading || !editor.isEnabled()) return true;
        try {
            Files.writeString(selectedNote, editor.getText(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            showError("Could not save the note.", exception);
            return false;
        }
    }

    private void renameSelection() {
        Entry entry = selectedEntry();
        if (entry == null || entry.path == null || entry.path.equals(storageRoot)) return;
        String name = askName("Rename", "New name:", entry.name);
        if (name == null) return;
        if (!saveCurrent()) return;
        String suffix = entry.folder ? "" : ".html";
        Path target = entry.path.resolveSibling(safeName(name) + suffix);
        if (Files.exists(target)) {
            DarkDialogs.message(this, "An item with this name already exists.",
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
        if (entry == null || entry.path == null || entry.path.equals(storageRoot)) return;
        if (!DeleteConfirmationSupport.confirm(this, entry.name)) return;
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
        loadChildren(rootNode, storageRoot);
        treeModel.reload();
        for (int row = 0; row < tree.getRowCount(); row++) tree.expandRow(row);
        if (select != null) selectPath(rootNode, select);
    }

    private void reloadAfterMove(Path target) {
        if (selectedNote != null && !Files.exists(selectedNote) && Files.isRegularFile(target))
            selectedNote = target;
        reloadTree(target);
    }

    private void loadChildren(DefaultMutableTreeNode parentNode, Path folder) {
        try (var children = Files.list(folder)) {
            FileTreeOrder.sort(folder, children.toList()).forEach(path -> {
                if (Files.isDirectory(path)) {
                    DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(
                            new Entry(path.getFileName().toString(), path, true));
                    loadChildren(folderNode, path);
                    parentNode.add(folderNode);
                } else if (path.getFileName().toString().endsWith(".html")) {
                    parentNode.add(new DefaultMutableTreeNode(new Entry(
                            stripExtension(path.getFileName().toString()), path, false)));
                }
            });
        } catch (IOException exception) {
            showError("Could not load notes.", exception);
        }
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
        String value = (String) DarkDialogs.input(this, message, title,
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
            Path mapsFolder = storageRoot.resolve("Maps");
            if (!Files.exists(mapsFolder)) {
                Files.createDirectories(mapsFolder);
                try (var existing = Files.list(storageRoot)) {
                    for (Path path : existing.toList()) {
                        if (Files.isDirectory(path) && !path.equals(mapsFolder)) {
                            Files.move(path, mapsFolder.resolve(path.getFileName()));
                        }
                    }
                }
            }
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

    static String safeName(String name) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return safe.isEmpty() ? "Untitled" : safe;
    }

    private static String stripExtension(String name) {
        return name.substring(0, name.length() - ".html".length());
    }

    private void showError(String message, Exception exception) {
        DarkDialogs.message(this, message + "\n" + exception.getMessage(),
                "Map notes", JOptionPane.ERROR_MESSAGE);
    }

    private record Entry(String name, Path path, boolean folder) {
        @Override public String toString() { return name; }
    }

    private static final class DeleteFailure extends RuntimeException {
        DeleteFailure(IOException cause) { super(cause); }
    }

    private static final class ColorCircleIcon implements Icon {
        @Override public int getIconWidth() { return 14; }
        @Override public int getIconHeight() { return 14; }

        @Override public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 90, 90));
            g.fillArc(x + 1, y + 1, 12, 12, 0, 120);
            g.setColor(new Color(90, 205, 130));
            g.fillArc(x + 1, y + 1, 12, 12, 120, 120);
            g.setColor(new Color(66, 145, 235));
            g.fillArc(x + 1, y + 1, 12, 12, 240, 120);
            g.setColor(AssistantTheme.TEXT);
            g.drawOval(x + 1, y + 1, 12, 12);
            g.dispose();
        }
    }
}
