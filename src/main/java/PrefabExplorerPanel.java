import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.DropMode;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.undo.UndoManager;

/** Folder-based library for editable T3D/text prefab snippets with a live brush preview. */
public final class PrefabExplorerPanel extends JPanel {
    static final Color PREFAB_COLOR = new Color(34, 211, 238);
    private static final String LAST_FILE_DIRECTORY = "prefabLastFileDirectory";
    private static final java.util.prefs.Preferences PREFERENCES =
            java.util.prefs.Preferences.userNodeForPackage(PrefabExplorerPanel.class);
    private final Path storageRoot;
    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();
    private final DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    private final JTree tree = new JTree(treeModel);
    private final JTextArea code = new JTextArea();
    private final BrushPreviewPanel preview = new BrushPreviewPanel();
    private final JLabel title = new JLabel("Select or create a prefab file");
    private final JLabel status = new JLabel("Ready");
    private final JTextField filterField = new JTextField();
    private final JComboBox<String> brushSelector = new JComboBox<>();
    private final UndoManager undoManager = new UndoManager();
    private final Timer autoSaveTimer = new Timer(350, event -> autoSave());
    private final Timer previewTimer = new Timer(220, event -> analyze());
    private Path selectedNote;
    private String baselineCode = "";
    private boolean loading;
    private boolean updatingBrushes;
    private boolean reloadingTree;

    public PrefabExplorerPanel() { this(resolveStorageRoot()); }

    PrefabExplorerPanel(Path storageRoot) {
        super(new BorderLayout(8, 8));
        this.storageRoot = storageRoot;
        autoSaveTimer.setRepeats(false);
        previewTimer.setRepeats(false);
        setBackground(AssistantTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        initializeStorage();
        rootNode.setUserObject(new Entry("All Prefabs", storageRoot, true));
        add(createHeader(), BorderLayout.NORTH);
        add(createWorkspace(), BorderLayout.CENTER);
        installInputSupport();
        reloadTree(null);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel heading = new JLabel("Prefab Explorer");
        AssistantTheme.stylePageTitle(heading);
        heading.setPreferredSize(new Dimension(338, 36));
        header.add(heading, BorderLayout.WEST);
        JPanel actions = horizontalActions(FlowLayout.LEFT);
        actions.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        actions.add(button("+ New Folder", event -> createFolder()));
        actions.add(button("+ New File", event -> createNote()));
        actions.add(button("Rename", event -> renameSelection()));
        actions.add(button("Open", event -> open()));
        actions.add(button("Save as...", event -> exportPrefab()));
        actions.add(button("Delete", event -> deleteSelection()));
        header.add(actions, BorderLayout.CENTER);
        JPanel editActions = horizontalActions(FlowLayout.RIGHT);
        editActions.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        editActions.add(button("Undo", event -> undo()));
        editActions.add(button("Redo", event -> redo()));
        editActions.add(button("Reset", event -> resetEdits()));
        header.add(editActions, BorderLayout.EAST);
        return header;
    }

    private JPanel createWorkspace() {
        configureTree();
        JScrollPane treeScroll = new JScrollPane(tree);
        treeScroll.setPreferredSize(new Dimension(330, 300));
        treeScroll.setBorder(AssistantTheme.titled("Prefabs"));
        filterField.setToolTipText("Filter folders and prefab files");
        filterField.setBorder(AssistantTheme.titled("Search"));
        JPanel explorer = new JPanel(new BorderLayout(0, 5));
        explorer.setOpaque(false);
        explorer.add(filterField, BorderLayout.NORTH);
        explorer.add(treeScroll, BorderLayout.CENTER);

        code.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        code.setLineWrap(false);
        code.setTabSize(4);
        code.setBackground(AssistantTheme.CODE_BACKGROUND);
        code.setForeground(AssistantTheme.TEXT);
        code.setCaretColor(AssistantTheme.TEXT);
        code.setEnabled(false);
        TextSearchSupport.install(code, this, "Prefab Code");

        JPanel codePanel = new JPanel(new BorderLayout());
        codePanel.setOpaque(false);
        title.setForeground(PREFAB_COLOR);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setBorder(BorderFactory.createEmptyBorder(9, 0, 10, 0));
        codePanel.add(title, BorderLayout.NORTH);
        JScrollPane codeScroll = new JScrollPane(code);
        codeScroll.setBorder(BorderFactory.createLineBorder(AssistantTheme.BORDER));
        codePanel.add(codeScroll, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        JPanel codeActions = horizontalActions(FlowLayout.LEFT);
        brushSelector.setPreferredSize(new Dimension(100, 26));
        brushSelector.setToolTipText("Select which brush to preview");
        brushSelector.setBackground(AssistantTheme.PANEL_ALT);
        brushSelector.setForeground(AssistantTheme.TEXT);
        brushSelector.setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list,
                    Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setBackground(selected ? AssistantTheme.ACCENT_DARK : AssistantTheme.PANEL_ALT);
                setForeground(brushSelector.isEnabled() ? AssistantTheme.TEXT : AssistantTheme.MUTED);
                return this;
            }
        });
        brushSelector.addActionListener(event -> showSelectedBrush());
        refreshBrushChoices("");
        codeActions.add(button("Paste", event -> paste()));
        codeActions.add(button("Copy", event -> copy()));
        JPanel brushChoice = horizontalActions(FlowLayout.LEFT);
        brushChoice.setPreferredSize(new Dimension(338, 28));
        brushChoice.add(brushSelector);
        toolbar.add(brushChoice, BorderLayout.WEST);
        toolbar.add(codeActions, BorderLayout.CENTER);
        status.setForeground(AssistantTheme.MUTED);
        status.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        status.setBorder(BorderFactory.createEmptyBorder());
        toolbar.add(status, BorderLayout.EAST);

        preview.setPreferredSize(new Dimension(330, 330));
        preview.setMinimumSize(new Dimension(330, 330));
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(330, 630));
        left.setMinimumSize(new Dimension(330, 0));
        left.setMaximumSize(new Dimension(330, Integer.MAX_VALUE));
        left.add(explorer, BorderLayout.CENTER);
        left.add(preview, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(8, 0));
        center.setOpaque(false);
        center.add(left, BorderLayout.WEST);
        center.add(codePanel, BorderLayout.CENTER);
        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setOpaque(false);
        workspace.add(center, BorderLayout.CENTER);
        workspace.add(toolbar, BorderLayout.SOUTH);
        return workspace;
    }

    private void configureTree() {
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setBackground(AssistantTheme.PANEL_ALT);
        tree.setForeground(AssistantTheme.TEXT);
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            { setBackgroundNonSelectionColor(AssistantTheme.PANEL_ALT); setBackgroundSelectionColor(AssistantTheme.ACCENT_DARK);
              setTextNonSelectionColor(AssistantTheme.TEXT); setTextSelectionColor(AssistantTheme.TEXT); }
            @Override public java.awt.Component getTreeCellRendererComponent(JTree tree, Object value,
                    boolean selected, boolean expanded, boolean leaf, int row, boolean focus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focus);
                if (value instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof Entry entry && entry.folder)
                    setIcon(UIManager.getIcon(expanded ? "Tree.openIcon" : "Tree.closedIcon"));
                return this;
            }
        });
        tree.addTreeSelectionListener(event -> { if (!reloadingTree) selectEntry(); });
    }

    private void installInputSupport() {
        code.getInputMap().put(KeyStroke.getKeyStroke("control S"), "savePrefab");
        code.getActionMap().put("savePrefab", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { exportPrefab(); }
        });
        code.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
            private void changed() {
                if (loading) return;
                previewTimer.restart();
                if (selectedNote != null) autoSaveTimer.restart();
            }
        });
        code.getDocument().addUndoableEditListener(event -> undoManager.addEdit(event.getEdit()));
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { filterChanged(); }
            @Override public void removeUpdate(DocumentEvent event) { filterChanged(); }
            @Override public void changedUpdate(DocumentEvent event) { filterChanged(); }
            private void filterChanged() { reloadTree(selectedNote); }
        });
        code.getInputMap().put(KeyStroke.getKeyStroke("control Z"), "undoPrefab");
        code.getActionMap().put("undoPrefab", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { undo(); }
        });
        code.getInputMap().put(KeyStroke.getKeyStroke("control Y"), "redoPrefab");
        code.getActionMap().put("redoPrefab", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { redo(); }
        });
        code.getInputMap().put(KeyStroke.getKeyStroke("control shift Z"), "redoPrefab");
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("control V"), "pastePrefab");
        getActionMap().put("pastePrefab", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { paste(); }
        });
        code.getInputMap().put(KeyStroke.getKeyStroke("control V"), "pastePrefab");
        code.getActionMap().put("pastePrefab", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { paste(); }
        });
        tree.getInputMap().put(KeyStroke.getKeyStroke("DELETE"), "deletePrefab");
        tree.getActionMap().put("deletePrefab", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { deleteSelection(); }
        });
        TransferHandler drops = new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                boolean accepted = support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || support.isDataFlavorSupported(DataFlavor.stringFlavor);
                if (accepted && support.isDrop()) support.setDropAction(COPY);
                return accepted;
            }
            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    Transferable value = support.getTransferable();
                    if (value.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @SuppressWarnings("unchecked") List<File> files =
                                (List<File>) value.getTransferData(DataFlavor.javaFileListFlavor);
                        return importFiles(files);
                    }
                    importText((String) value.getTransferData(DataFlavor.stringFlavor));
                    return true;
                } catch (Exception exception) {
                    showError("Could not import the dropped prefab.", exception);
                    return false;
                }
            }
        };
        setTransferHandler(drops);
        code.setTransferHandler(drops);
        tree.setDropMode(DropMode.INSERT);
        if (!java.awt.GraphicsEnvironment.isHeadless()) tree.setDragEnabled(true);
        tree.setTransferHandler(new FileTreeReorderHandler(tree, storageRoot,
                value -> ((Entry) value).path, this::reloadTree,
                exception -> showError("Could not move the item.", exception), drops));
    }

    private void analyze() {
        String text = code.getText();
        refreshBrushChoices(text);
        showSelectedBrush();
        int polygons = BrushPreviewPanel.polygonCount(text);
        int brushes = BrushPreviewPanel.brushCount(text);
        status.setText(polygons == 0 ? "No brush polygons found"
                : brushes + (brushes == 1 ? " brush, " : " brushes, ") + polygons + " polygons");
    }

    private void refreshBrushChoices(String text) {
        int selected = Math.max(0, brushSelector.getSelectedIndex());
        int count = BrushPreviewPanel.brushCount(text);
        updatingBrushes = true;
        brushSelector.removeAllItems();
        if (count <= 1) brushSelector.addItem("None");
        else for (int index = 1; index <= count; index++) brushSelector.addItem("Brush " + index);
        brushSelector.setEnabled(count > 1);
        brushSelector.setSelectedIndex(count <= 1 ? 0 : Math.min(selected, count - 1));
        updatingBrushes = false;
    }

    private void showSelectedBrush() {
        if (updatingBrushes) return;
        preview.showBrush(code.getText(), PREFAB_COLOR, displayName(),
                Math.max(0, brushSelector.getSelectedIndex()));
    }

    private void undo() {
        if (undoManager.canUndo()) { undoManager.undo(); status.setText("Undo"); }
    }

    private void redo() {
        if (undoManager.canRedo()) { undoManager.redo(); status.setText("Redo"); }
    }

    private void copy() {
        if (code.getText().isEmpty()) { status.setText("Nothing to copy"); return; }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code.getText()), null);
        status.setText("Copied");
    }

    private void paste() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (clipboard.isDataFlavorAvailable(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked") List<File> files =
                        (List<File>) clipboard.getData(DataFlavor.javaFileListFlavor);
                importFiles(files);
                return;
            }
            String text = ClipboardTextSupport.readText();
            Path clipboardFile = pathFromClipboardText(text);
            if (clipboardFile != null) importFile(clipboardFile);
            else importText(withTrailingLineBreak(text));
        } catch (Exception exception) { showError("Could not paste the prefab.", exception); }
    }

    private void importText(String text) throws IOException {
        if (text == null) return;
        Entry selection = selectedEntry();
        if (selection != null && selection.folder) {
            Path note = uniquePath(selection.path, "Pasted Prefab", ".t3d");
            AtomicTextFile.write(note, text);
            reloadTree(note);
        } else {
            if (!code.isEnabled()) {
                loading = true;
                code.setEnabled(true);
                code.setText(text);
                code.setCaretPosition(0);
                loading = false;
                undoManager.discardAllEdits();
            } else {
                code.replaceSelection(text);
            }
            analyze();
        }
    }

    private void open() {
        JFileChooser chooser = new DarkFileChooser();
        chooser.setDialogTitle("Open Prefab");
        chooser.setFileFilter(new FileNameExtensionFilter("Prefab files (*.t3d, *.txt)", "t3d", "txt"));
        configureChooserDirectory(chooser);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            rememberChooserDirectory(chooser);
            try { importFile(chooser.getSelectedFile().toPath()); }
            catch (IOException exception) { showError("Could not open the prefab.", exception); }
        }
    }

    private boolean importFiles(List<File> files) throws IOException {
        boolean imported = false;
        for (File file : files) if (isPrefab(file.toPath())) { importFile(file.toPath()); imported = true; }
        if (!imported) {
            status.setText("Only .t3d and .txt files are supported");
            DarkDialogs.message(this, "Only .t3d and .txt files are supported.",
                    "Unsupported prefab file", JOptionPane.WARNING_MESSAGE);
        }
        return imported;
    }

    private void importFile(Path file) throws IOException {
        if (!isPrefab(file)) throw new IOException("Only .t3d and .txt files are supported.");
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Entry selection = selectedEntry();
        if (selection != null && selection.folder) {
            String extension = extension(file);
            Path note = uniquePath(selection.path, baseName(file), extension);
            AtomicTextFile.write(note, text);
            reloadTree(note);
        } else {
            selectedNote = null; baselineCode = text; loading = true; code.setEnabled(true); code.setText(text);
            loading = false; code.setCaretPosition(0); undoManager.discardAllEdits();
            title.setText(file.getFileName().toString() + " (not saved in library)"); analyze();
        }
    }

    private void createFolder() {
        Path parent = selectedFolder(); if (parent == null) parent = storageRoot;
        String name = askName("New folder", "Folder name:", ""); if (name == null) return;
        try { Path folder = uniquePath(parent, safeName(name), ""); Files.createDirectories(folder); reloadTree(folder); }
        catch (IOException exception) { showError("Could not create the folder.", exception); }
    }

    private void createNote() {
        Path folder = selectedFolder();
        if (folder == null) { DarkDialogs.message(this, "Select a folder first."); return; }
        String name = askName("New prefab file", "File name:", ""); if (name == null) return;
        try { Path note = uniquePath(folder, safeName(name), ".t3d"); AtomicTextFile.write(note, ""); reloadTree(note); }
        catch (IOException exception) { showError("Could not create the file.", exception); }
    }

    private void selectEntry() {
        if (!saveCurrent()) return;
        Entry entry = selectedEntry();
        if (entry == null || entry.folder) {
            selectedNote = null; baselineCode = ""; loading = true; code.setText(""); loading = false;
            undoManager.discardAllEdits(); code.setEnabled(false);
            title.setText(entry == null ? "Select or create a prefab file" : entry.name);
            refreshBrushChoices("");
            preview.showBrush("", PREFAB_COLOR, "Select a prefab file"); status.setText("Ready"); return;
        }
        try {
            selectedNote = entry.path; loading = true; baselineCode = Files.readString(entry.path, StandardCharsets.UTF_8);
            code.setText(baselineCode);
            code.setCaretPosition(0); loading = false; undoManager.discardAllEdits();
            code.setEnabled(true); title.setText(entry.name); analyze();
        } catch (IOException exception) { loading = false; showError("Could not open the prefab file.", exception); }
    }

    private boolean saveCurrent() {
        if (selectedNote == null || loading || !code.isEnabled()) return true;
        try { AtomicTextFile.write(selectedNote, code.getText()); status.setText("Saved"); return true; }
        catch (IOException exception) { showError("Could not save the prefab file.", exception); return false; }
    }

    private void autoSave() {
        if (saveCurrent()) status.setText("Auto-saved");
    }

    private void resetEdits() {
        if (!code.isEnabled()) { status.setText("Nothing to reset"); return; }
        autoSaveTimer.stop();
        loading = true;
        code.setText(baselineCode);
        code.setCaretPosition(0);
        loading = false;
        if (selectedNote != null) {
            try { AtomicTextFile.write(selectedNote, baselineCode); }
            catch (IOException exception) { showError("Could not reset the prefab file.", exception); return; }
        }
        undoManager.discardAllEdits();
        analyze();
        status.setText("Changes reset");
    }

    private void exportPrefab() {
        if (!code.isEnabled() || code.getText().isEmpty()) {
            status.setText("Nothing to export");
            return;
        }
        JFileChooser chooser = new DarkFileChooser();
        chooser.setDialogTitle("Export Prefab");
        configureChooserDirectory(chooser);
        FileNameExtensionFilter t3d = new FileNameExtensionFilter("T3D prefab (*.t3d)", "t3d");
        FileNameExtensionFilter txt = new FileNameExtensionFilter("Text file (*.txt)", "txt");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.addChoosableFileFilter(t3d);
        chooser.addChoosableFileFilter(txt);
        String currentExtension = selectedNote == null ? ".t3d" : extension(selectedNote);
        chooser.setFileFilter(".txt".equals(currentExtension) ? txt : t3d);
        chooser.setSelectedFile(new File(selectedNote == null ? "Prefab" + currentExtension
                : selectedNote.getFileName().toString()));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        rememberChooserDirectory(chooser);
        String suffix = chooser.getFileFilter() == txt ? ".txt" : ".t3d";
        Path target = chooser.getSelectedFile().toPath();
        String lower = target.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (!lower.endsWith(".t3d") && !lower.endsWith(".txt"))
            target = target.resolveSibling(target.getFileName() + suffix);
        if (Files.exists(target) && DarkDialogs.confirm(this,
                "Overwrite \"" + target.getFileName() + "\"?", "Export Prefab",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        try {
            AtomicTextFile.write(target, code.getText());
            status.setText("Exported " + target.getFileName());
        } catch (IOException exception) { showError("Could not export the prefab.", exception); }
    }

    private void renameSelection() {
        Entry entry = selectedEntry(); if (entry == null || entry.path.equals(storageRoot)) return;
        String name = askName("Rename", "New name:", baseName(entry.path)); if (name == null || !saveCurrent()) return;
        String suffix = entry.folder ? "" : extension(entry.path); Path target = entry.path.resolveSibling(safeName(name) + suffix);
        if (Files.exists(target)) { DarkDialogs.message(this, "An item with this name already exists."); return; }
        try { Files.move(entry.path, target, StandardCopyOption.ATOMIC_MOVE); selectedNote = null; reloadTree(target); }
        catch (IOException exception) { showError("Could not rename the item.", exception); }
    }

    private void deleteSelection() {
        Entry entry = selectedEntry(); if (entry == null || entry.path.equals(storageRoot)) return;
        if (!DeleteConfirmationSupport.confirm(this, entry.name)) return;
        try {
            if (entry.folder) try (var paths = Files.walk(entry.path)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            } else Files.deleteIfExists(entry.path);
            selectedNote = null; reloadTree(null);
        } catch (IOException exception) { showError("Could not delete the item.", exception); }
    }

    private void reloadTree(Path select) {
        reloadingTree = true;
        rootNode.removeAllChildren();
        loadChildren(rootNode, storageRoot, filterField.getText().trim().toLowerCase(java.util.Locale.ROOT), false);
        treeModel.reload();
        for (int row = 0; row < tree.getRowCount(); row++) tree.expandRow(row);
        boolean selected = select != null && selectPath(rootNode, select);
        reloadingTree = false;
        if (selected || select == null) selectEntry();
    }

    private boolean loadChildren(DefaultMutableTreeNode parent, Path folder, String query, boolean parentMatches) {
        boolean found = false;
        try (var children = Files.list(folder)) {
            for (Path path : FileTreeOrder.sort(folder, children.toList())) {
                String name = path.getFileName().toString();
                boolean matches = parentMatches || query.isEmpty()
                        || name.toLowerCase(java.util.Locale.ROOT).contains(query);
                if (Files.isDirectory(path)) {
                    DefaultMutableTreeNode node = new DefaultMutableTreeNode(new Entry(name, path, true));
                    boolean childFound = loadChildren(node, path, query, matches);
                    if (matches || childFound) { parent.add(node); found = true; }
                } else if (isPrefab(path) && matches) {
                    parent.add(new DefaultMutableTreeNode(new Entry(name, path, false)));
                    found = true;
                }
            }
        } catch (IOException exception) { showError("Could not load prefabs.", exception); }
        return found;
    }

    private boolean selectPath(DefaultMutableTreeNode node, Path path) {
        if (((Entry) node.getUserObject()).path.equals(path)) { TreePath selection = new TreePath(node.getPath()); tree.setSelectionPath(selection); tree.scrollPathToVisible(selection); return true; }
        for (int i = 0; i < node.getChildCount(); i++) if (selectPath((DefaultMutableTreeNode) node.getChildAt(i), path)) return true;
        return false;
    }

    private Entry selectedEntry() { Object value = tree.getLastSelectedPathComponent(); return value instanceof DefaultMutableTreeNode node ? (Entry) node.getUserObject() : null; }
    private Path selectedFolder() { Entry entry = selectedEntry(); return entry == null ? null : entry.folder ? entry.path : entry.path.getParent(); }
    private String displayName() { return selectedNote == null ? "Imported prefab" : selectedNote.getFileName().toString(); }
    private JPanel transparentFlow(int alignment) { JPanel panel = new JPanel(new FlowLayout(alignment, 5, 0)); panel.setOpaque(false); return panel; }
    private void configureChooserDirectory(JFileChooser chooser) {
        File directory = FileSaveSupport.preferredDirectory(
                PREFERENCES.get(LAST_FILE_DIRECTORY, null), new File(System.getProperty("user.home")));
        chooser.setCurrentDirectory(directory);
    }

    private void rememberChooserDirectory(JFileChooser chooser) {
        File directory = chooser.getCurrentDirectory();
        if (directory != null && directory.isDirectory())
            PREFERENCES.put(LAST_FILE_DIRECTORY, directory.getAbsolutePath());
    }
    private JPanel horizontalActions(int alignment) {
        JPanel panel = new JPanel(new EdgeAlignedFlowLayout(alignment, 5, 0));
        panel.setOpaque(false);
        return panel;
    }
    private JButton button(String text, java.util.function.Consumer<ActionEvent> action) { JButton button = new JButton(text); button.addActionListener(action::accept); return button; }
    private String askName(String windowTitle, String message, String initial) { String value = (String) DarkDialogs.input(this, message, windowTitle, JOptionPane.PLAIN_MESSAGE, null, null, initial); return value == null || value.trim().isEmpty() ? null : value.trim(); }
    private void initializeStorage() { try { Files.createDirectories(storageRoot); } catch (IOException exception) { showError("Could not initialize prefab storage.", exception); } }
    private void showError(String message, Exception exception) { DarkDialogs.message(this, message + "\n" + exception.getMessage(), "Prefab Explorer", JOptionPane.ERROR_MESSAGE); }
    private static Path resolveStorageRoot() { String data = System.getenv("LOCALAPPDATA"); Path base = data == null || data.isBlank() ? Path.of(System.getProperty("user.home"), ".unreal-editor-2-assistant") : Path.of(data, "UnrealEditor2Assistant"); return base.resolve("Prefabs"); }
    static boolean isPrefab(Path path) { String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT); return Files.isRegularFile(path) && (name.endsWith(".t3d") || name.endsWith(".txt")); }
    private static Path pathFromClipboardText(String text) { if (text == null || text.contains("\n") || text.contains("\r")) return null; try { Path path = Path.of(text.trim().replaceAll("^\"|\"$", "")); return isPrefab(path) ? path : null; } catch (RuntimeException ignored) { return null; } }
    static String withTrailingLineBreak(String text) {
        if (text == null || text.isEmpty() || text.endsWith("\n") || text.endsWith("\r")) return text;
        return text + System.lineSeparator();
    }
    private static String extension(Path path) { String name = path.getFileName().toString(); int dot = name.lastIndexOf('.'); return dot < 0 ? ".t3d" : name.substring(dot).toLowerCase(java.util.Locale.ROOT); }
    private static String baseName(Path path) { String name = path.getFileName().toString(); int dot = name.lastIndexOf('.'); return dot < 0 ? name : name.substring(0, dot); }
    private static String safeName(String name) { String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim(); return safe.isEmpty() ? "Untitled" : safe; }
    private static Path uniquePath(Path parent, String base, String suffix) { Path candidate = parent.resolve(base + suffix); int number = 2; while (Files.exists(candidate)) candidate = parent.resolve(base + " " + number++ + suffix); return candidate; }
    private record Entry(String name, Path path, boolean folder) { @Override public String toString() { return name; } }

    private static final class EdgeAlignedFlowLayout extends FlowLayout {
        EdgeAlignedFlowLayout(int alignment, int horizontalGap, int verticalGap) {
            super(alignment, horizontalGap, verticalGap);
        }

        @Override public void layoutContainer(java.awt.Container target) {
            super.layoutContainer(target);
            int shift = getAlignment() == FlowLayout.RIGHT ? getHgap() : -getHgap();
            for (java.awt.Component component : target.getComponents())
                component.setLocation(component.getX() + shift, component.getY());
        }
    }
}
