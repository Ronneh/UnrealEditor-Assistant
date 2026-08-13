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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
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
import javax.swing.SwingUtilities;
import javax.swing.JToggleButton;
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
    private static final int FORMAT_CONTROL_HEIGHT = 27;
    private static final int FORMAT_BUTTON_WIDTH = 29;
    private final Path storageRoot;
    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree tree = new JTree(treeModel);
    private final JEditorPane editor = htmlPane(true);
    private final JLabel editorTitle = new JLabel("Select or create a note");
    private JButton addFolderButton;
    private JButton addNoteButton;
    private JToggleButton boldButton;
    private JToggleButton italicButton;
    private JToggleButton underlineButton;
    private JToggleButton bulletButton;
    private JToggleButton alignLeftButton;
    private JToggleButton alignCenterButton;
    private JToggleButton alignRightButton;
    private Path selectedNote;
    private boolean loading;

    public NotesPanel() {
        super(new BorderLayout(6, 3));
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
        editor.addCaretListener(event -> updateFormattingState());
        reloadTree(null);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("My Notes");
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

        JPanel editorPanel = new JPanel(new BorderLayout());
        editorPanel.setOpaque(false);
        editorTitle.setForeground(AssistantTheme.MUTED);
        editorTitle.setBorder(BorderFactory.createEmptyBorder(0, 0, 3, 0));
        JPanel editorHeader = new JPanel(new BorderLayout(0, 4));
        editorHeader.setBackground(AssistantTheme.PANEL_ALT);
        editorHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AssistantTheme.BORDER),
                BorderFactory.createEmptyBorder(5, 7, 6, 7)));
        editorHeader.add(editorTitle, BorderLayout.NORTH);
        editorHeader.add(createFormattingToolbar(), BorderLayout.SOUTH);
        editorPanel.add(editorHeader, BorderLayout.NORTH);
        JScrollPane editorScroll = new JScrollPane(editor);
        editorScroll.setBorder(BorderFactory.createMatteBorder(
                0, 1, 1, 1, AssistantTheme.BORDER));
        editorPanel.add(editorScroll, BorderLayout.CENTER);
        editor.setEnabled(false);

        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setBorder(BorderFactory.createLineBorder(AssistantTheme.BORDER));
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
        Dimension sizeControlSize = new Dimension(52, FORMAT_CONTROL_HEIGHT);
        sizes.setPreferredSize(sizeControlSize);
        sizes.setMinimumSize(sizeControlSize);
        sizes.setMaximumSize(sizeControlSize);
        sizes.setBorder(BorderFactory.createLineBorder(AssistantTheme.CODE_BACKGROUND));
        sizes.setToolTipText("Font size");
        sizes.addActionListener(event -> applyFontSize((Integer) sizes.getSelectedItem()));
        toolbar.add(sizes);
        boldButton = formattingToggleButton("B", "Bold",
                event -> applyStyle("bold", boldButton.isSelected()));
        boldButton.setFont(boldButton.getFont().deriveFont(Font.BOLD));
        toolbar.add(boldButton);
        italicButton = formattingToggleButton("I", "Italic",
                event -> applyStyle("italic", italicButton.isSelected()));
        italicButton.setFont(italicButton.getFont().deriveFont(Font.ITALIC));
        toolbar.add(italicButton);
        underlineButton = formattingToggleButton("<html><u>U</u></html>", "Underline",
                event -> applyStyle("underline", underlineButton.isSelected()));
        toolbar.add(underlineButton);
        bulletButton = formattingToggleButton("\u2022", "Toggle bullet",
                event -> toggleBullet());
        toolbar.add(bulletButton);
        JButton colorButton = formattingButton("", "Text color", event -> chooseTextColor());
        colorButton.setIcon(new ColorCircleIcon());
        toolbar.add(colorButton);
        alignLeftButton = formattingToggleButton(new TextAlignmentIcon(StyleConstants.ALIGN_LEFT),
                "Align left", event -> applyParagraphAlignment(StyleConstants.ALIGN_LEFT));
        alignCenterButton = formattingToggleButton(new TextAlignmentIcon(StyleConstants.ALIGN_CENTER),
                "Center", event -> applyParagraphAlignment(StyleConstants.ALIGN_CENTER));
        alignRightButton = formattingToggleButton(new TextAlignmentIcon(StyleConstants.ALIGN_RIGHT),
                "Align right", event -> applyParagraphAlignment(StyleConstants.ALIGN_RIGHT));
        ButtonGroup alignments = new ButtonGroup();
        alignments.add(alignLeftButton);
        alignments.add(alignCenterButton);
        alignments.add(alignRightButton);
        alignLeftButton.setSelected(true);
        toolbar.add(alignLeftButton);
        toolbar.add(alignCenterButton);
        toolbar.add(alignRightButton);
        return toolbar;
    }

    private JButton formattingButton(String text, String tooltip,
            java.util.function.Consumer<ActionEvent> action) {
        JButton button = button(text, action);
        styleFormattingControl(button, tooltip);
        return button;
    }

    private JToggleButton formattingToggleButton(String text, String tooltip,
            java.util.function.Consumer<ActionEvent> action) {
        JToggleButton button = new JToggleButton(text);
        button.setMargin(new java.awt.Insets(2, 6, 2, 6));
        button.addActionListener(action::accept);
        styleFormattingControl(button, tooltip);
        return button;
    }

    private void styleFormattingControl(AbstractButton button, String tooltip) {
        Dimension size = new Dimension(FORMAT_BUTTON_WIDTH, FORMAT_CONTROL_HEIGHT);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setBorder(BorderFactory.createLineBorder(AssistantTheme.CODE_BACKGROUND));
        button.setFocusPainted(false);
        button.setToolTipText(tooltip);
    }

    private JButton formattingButton(Icon icon, String tooltip,
            java.util.function.Consumer<ActionEvent> action) {
        JButton button = formattingButton("", tooltip, action);
        button.setIcon(icon);
        return button;
    }

    private JToggleButton formattingToggleButton(Icon icon, String tooltip,
            java.util.function.Consumer<ActionEvent> action) {
        JToggleButton button = formattingToggleButton("", tooltip, action);
        button.setIcon(icon);
        return button;
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

    private void applyStyle(String style, boolean enabled) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        if ("bold".equals(style)) StyleConstants.setBold(attributes, enabled);
        else if ("italic".equals(style)) StyleConstants.setItalic(attributes, enabled);
        else StyleConstants.setUnderline(attributes, enabled);
        applyCharacterAttributes(attributes);
        SwingUtilities.invokeLater(this::updateFormattingState);
    }

    private void applyParagraphAlignment(int alignment) {
        if (!editor.isEnabled()
                || !(editor.getDocument() instanceof javax.swing.text.StyledDocument document)) return;
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setAlignment(attributes, alignment);
        int start = editor.getSelectionStart();
        int length = editor.getSelectionEnd() - start;
        document.setParagraphAttributes(start, length, attributes, false);
        editor.requestFocusInWindow();
        SwingUtilities.invokeLater(this::updateFormattingState);
    }

    private void updateFormattingState() {
        if (boldButton == null || !(editor.getEditorKit() instanceof StyledEditorKit kit)) return;
        if (editor.getSelectionStart() < editor.getSelectionEnd()
                && editor.getDocument() instanceof javax.swing.text.StyledDocument document) {
            int start = editor.getSelectionStart();
            int end = editor.getSelectionEnd();
            boldButton.setSelected(selectionContainsStyle(document, start, end, "bold"));
            italicButton.setSelected(selectionContainsStyle(document, start, end, "italic"));
            underlineButton.setSelected(selectionContainsStyle(document, start, end, "underline"));
        } else {
            javax.swing.text.AttributeSet character = kit.getInputAttributes();
            boldButton.setSelected(StyleConstants.isBold(character));
            italicButton.setSelected(StyleConstants.isItalic(character));
            underlineButton.setSelected(StyleConstants.isUnderline(character));
        }
        bulletButton.setSelected(currentParagraphHasBullet());

        int alignment = StyleConstants.ALIGN_LEFT;
        if (editor.getDocument() instanceof javax.swing.text.StyledDocument document) {
            int position = Math.min(editor.getCaretPosition(), document.getLength());
            alignment = StyleConstants.getAlignment(
                    document.getParagraphElement(position).getAttributes());
        }
        alignLeftButton.setSelected(alignment == StyleConstants.ALIGN_LEFT);
        alignCenterButton.setSelected(alignment == StyleConstants.ALIGN_CENTER);
        alignRightButton.setSelected(alignment == StyleConstants.ALIGN_RIGHT);
    }

    private static boolean selectionContainsStyle(javax.swing.text.StyledDocument document,
            int start, int end, String style) {
        int position = start;
        while (position < end) {
            javax.swing.text.Element character = document.getCharacterElement(position);
            javax.swing.text.AttributeSet attributes = character.getAttributes();
            boolean present = "bold".equals(style) ? StyleConstants.isBold(attributes)
                    : "italic".equals(style) ? StyleConstants.isItalic(attributes)
                    : StyleConstants.isUnderline(attributes);
            if (present) return true;
            int next = Math.min(end, character.getEndOffset());
            position = next > position ? next : position + 1;
        }
        return false;
    }

    private void toggleBullet() {
        if (!editor.isEnabled()
                || !(editor.getDocument() instanceof javax.swing.text.StyledDocument document)) return;
        int selectionStart = editor.getSelectionStart();
        int selectionEnd = editor.getSelectionEnd();
        int effectiveEnd = selectionEnd > selectionStart ? selectionEnd - 1 : selectionEnd;
        List<Integer> paragraphStarts = new ArrayList<>();
        int position = selectionStart;
        while (position <= effectiveEnd) {
            javax.swing.text.Element paragraph = document.getParagraphElement(position);
            int paragraphStart = paragraph.getStartOffset();
            if (paragraphStarts.isEmpty()
                    || paragraphStarts.get(paragraphStarts.size() - 1) != paragraphStart)
                paragraphStarts.add(paragraphStart);
            int next = paragraph.getEndOffset();
            if (next <= position || next > effectiveEnd) break;
            position = next;
        }

        boolean removeBullets = paragraphStarts.stream()
                .allMatch(start -> paragraphHasBullet(document, start));
        try {
            for (int index = paragraphStarts.size() - 1; index >= 0; index--) {
                int start = paragraphStarts.get(index);
                if (removeBullets) document.remove(start, 2);
                else if (!paragraphHasBullet(document, start))
                    document.insertString(start, "\u2022 ", null);
            }
            editor.requestFocusInWindow();
            SwingUtilities.invokeLater(this::updateFormattingState);
        } catch (javax.swing.text.BadLocationException exception) {
            throw new IllegalStateException("Could not toggle the bullet list.", exception);
        }
    }

    private boolean currentParagraphHasBullet() {
        if (!(editor.getDocument() instanceof javax.swing.text.StyledDocument document)) return false;
        int position = Math.min(editor.getCaretPosition(), document.getLength());
        return paragraphHasBullet(document, document.getParagraphElement(position).getStartOffset());
    }

    private static boolean paragraphHasBullet(javax.swing.text.Document document, int start) {
        if (start < 0 || start + 2 > document.getLength()) return false;
        try {
            return "\u2022 ".equals(document.getText(start, 2));
        } catch (javax.swing.text.BadLocationException exception) {
            return false;
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

    private static final class TextAlignmentIcon implements Icon {
        private final int alignment;

        TextAlignmentIcon(int alignment) {
            this.alignment = alignment;
        }

        @Override public int getIconWidth() { return 15; }
        @Override public int getIconHeight() { return 14; }

        @Override public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(component.isEnabled() ? AssistantTheme.TEXT : AssistantTheme.MUTED);
            int[] widths = { 15, 10, 13, 8 };
            for (int line = 0; line < widths.length; line++) {
                int width = widths[line];
                int offset = alignment == StyleConstants.ALIGN_CENTER ? (15 - width) / 2
                        : alignment == StyleConstants.ALIGN_RIGHT ? 15 - width : 0;
                int lineY = y + 1 + line * 4;
                g.drawLine(x + offset, lineY, x + offset + width - 1, lineY);
            }
            g.dispose();
        }
    }
}
