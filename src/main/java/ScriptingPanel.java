import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.DropMode;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.event.HyperlinkEvent;

/** UnrealScript editor, offline advisor, and UCC compiler front end. */
public final class ScriptingPanel extends JPanel {
    private static final Pattern CLASS = Pattern.compile(
            "(?im)^\\s*class\\s+([A-Za-z_]\\w*)\\s+(?:extends|expands)\\s+([A-Za-z_]\\w*)\\s*;");
    private static final Preferences PREFS = Preferences.userNodeForPackage(ScriptingPanel.class);
    private final JTextArea editor = new JTextArea();
    private final JTextPane advice = new JTextPane();
    private final JTextPane learning = new JTextPane();
    private final JTextPane reference = new JTextPane();
    private final JTextPane editorGuideContext = new JTextPane();
    private final JTextArea compilerOutput = new JTextArea();
    private final JTabbedPane assistantTabs = new JTabbedPane();
    private final JScrollPane sourceScroll = new JScrollPane(editor);
    private final JLabel status = new JLabel("Ready.");
    private final JButton compileButton = new JButton("Compile with UCC");
    private final Path scriptLibrary = resolveScriptLibrary();
    private final Path tutorialsFolder = scriptLibrary.resolve("Tutorials");
    private final DefaultMutableTreeNode scriptRoot = new DefaultMutableTreeNode();
    private final DefaultTreeModel scriptTreeModel = new DefaultTreeModel(scriptRoot);
    private final JTree scriptTree = new JTree(scriptTreeModel);
    private Path currentFile;
    private Path uccExecutable;
    private boolean dirty;
    private EditorHelpContentPack editorGuidePack;
    private String relatedEditorGuideHtml = "";

    public ScriptingPanel() {
        super(new BorderLayout(8, 8));
        setBackground(AssistantTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
        initializeScriptLibrary();
        configureComponents();
        add(createWorkspace(), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        installShortcuts();
        restoreUccPath();
        setTemplate("Basic Actor");
        dirty = false;
        analyze();
        loadEditorGuideKnowledge();
    }

    private void configureComponents() {
        editor.setFont(new Font("Verdana", Font.PLAIN, 14));
        editor.setTabSize(4);
        editor.setLineWrap(false);
        editor.setBackground(AssistantTheme.CODE_BACKGROUND);
        editor.setForeground(AssistantTheme.TEXT);
        editor.setCaretColor(AssistantTheme.TEXT);
        editor.setSelectionColor(AssistantTheme.ACCENT_DARK);
        editor.getDocument().addDocumentListener((DocumentChange) () -> {
            dirty = true;
            updateFileLabel();
        });
        advice.setContentType("text/html");
        advice.setEditable(false);
        advice.setBackground(AssistantTheme.CODE_BACKGROUND);
        configureHtmlPane(learning);
        configureHtmlPane(reference);
        configureHtmlPane(editorGuideContext);
        editorGuideContext.addHyperlinkListener(event -> {
            if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            if (event.getDescription().startsWith("help:"))
                showEditorGuideDocument(event.getDescription().substring("help:".length()));
            else if (event.getDescription().equals("context:back")) showRelatedEditorGuideResults();
        });
        compilerOutput.setEditable(false);
        compilerOutput.setFont(new Font("Verdana", Font.PLAIN, 12));
        compilerOutput.setBackground(AssistantTheme.CODE_BACKGROUND);
        compilerOutput.setForeground(AssistantTheme.TEXT);
    }

    private JPanel createToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(AssistantTheme.BACKGROUND);
        bar.setBorder(BorderFactory.createEmptyBorder(5, 0, 3, 0));
        bar.setPreferredSize(new Dimension(0, 36));
        JPanel files = row();
        files.add(button("New", event -> newFile()));
        files.add(button("Open...", event -> open()));
        files.add(button("Save", event -> save()));
        files.add(button("Save as...", event -> saveAs()));
        JComboBox<String> templates = new JComboBox<>(UnrealScriptLearning.TEMPLATES);
        files.add(new JLabel("Template:"));
        files.add(templates);
        files.add(button("Insert", event -> insertTemplate((String) templates.getSelectedItem())));
        JComboBox<Integer> fonts = new JComboBox<>(new Integer[] { 12, 13, 14, 16, 18, 20 });
        fonts.setSelectedItem(14);
        fonts.setPreferredSize(new Dimension(55, 26));
        fonts.addActionListener(event -> editor.setFont(editor.getFont()
                .deriveFont(((Integer) fonts.getSelectedItem()).floatValue())));
        files.add(new JLabel("Font:"));
        files.add(fonts);

        bar.add(files, BorderLayout.WEST);
        return bar;
    }

    private JPanel createUccActions() {
        JPanel actions = new JPanel(new BorderLayout());
        actions.setBackground(AssistantTheme.BACKGROUND);
        actions.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        JPanel analysis = new JPanel(new EdgeAlignedFlowLayout(FlowLayout.LEFT, 6, 0));
        analysis.setOpaque(false);
        analysis.add(button("Analyze", event -> analyze()));
        actions.add(analysis, BorderLayout.WEST);
        JPanel compiler = new JPanel(new EdgeAlignedFlowLayout(FlowLayout.RIGHT, 6, 0));
        compiler.setOpaque(false);
        compiler.add(button("Set UCC...", event -> chooseUcc()));
        compileButton.addActionListener(event -> compile());
        compiler.add(compileButton);
        actions.add(compiler, BorderLayout.EAST);
        return actions;
    }

    private JPanel row() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        return panel;
    }

    private JSplitPane createWorkspace() {
        JPanel source = new JPanel(new BorderLayout(0, 4));
        source.setOpaque(false);
        JPanel editorHeader = new JPanel(new BorderLayout(0, 2));
        editorHeader.setBackground(AssistantTheme.BACKGROUND);
        editorHeader.add(createToolbar(), BorderLayout.NORTH);
        source.add(editorHeader, BorderLayout.NORTH);
        updateFileLabel();
        source.add(sourceScroll, BorderLayout.CENTER);
        assistantTabs.addTab("Learn", createLearningPanel());
        assistantTabs.addTab("Recommendations", new JScrollPane(advice));
        assistantTabs.addTab("Reference", new JScrollPane(reference));
        assistantTabs.addTab("Editor Guide", new JScrollPane(editorGuideContext));
        assistantTabs.addTab("Compiler output", new JScrollPane(compilerOutput));
        assistantTabs.setBackground(AssistantTheme.PANEL);
        assistantTabs.setForeground(AssistantTheme.TEXT);
        assistantTabs.setBackgroundAt(assistantTabs.getSelectedIndex(), AssistantTheme.CODE_BACKGROUND);
        assistantTabs.addChangeListener(event -> {
            for (int index = 0; index < assistantTabs.getTabCount(); index++) {
                assistantTabs.setBackgroundAt(index, index == assistantTabs.getSelectedIndex()
                        ? AssistantTheme.CODE_BACKGROUND : AssistantTheme.PANEL);
            }
        });
        JSplitPane upperWorkspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createScriptBrowser(), source);
        upperWorkspace.setResizeWeight(0.25);
        upperWorkspace.setDividerLocation(330);
        AssistantTheme.styleSplitPane(upperWorkspace);
        JPanel upperSection = new JPanel(new BorderLayout(0, 4));
        upperSection.setBackground(AssistantTheme.BACKGROUND);
        upperSection.add(upperWorkspace, BorderLayout.CENTER);
        JSplitPane actionSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createScriptActions(), createUccActions());
        actionSplit.setResizeWeight(0.25);
        actionSplit.setDividerLocation(330);
        AssistantTheme.styleSplitPane(actionSplit);
        upperWorkspace.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
                event -> actionSplit.setDividerLocation(upperWorkspace.getDividerLocation()));
        actionSplit.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,
                event -> upperWorkspace.setDividerLocation(actionSplit.getDividerLocation()));
        upperSection.add(actionSplit, BorderLayout.SOUTH);
        JSplitPane workspace = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                upperSection, assistantTabs);
        workspace.setResizeWeight(0.62);
        workspace.setDividerLocation(430);
        AssistantTheme.styleSplitPane(workspace);
        return workspace;
    }

    private JPanel createScriptBrowser() {
        scriptRoot.setUserObject(new ScriptEntry("Scripts", scriptLibrary, true, false));
        scriptTree.setRootVisible(true);
        scriptTree.setShowsRootHandles(true);
        scriptTree.setBackground(AssistantTheme.PANEL_ALT);
        scriptTree.setForeground(AssistantTheme.TEXT);
        scriptTree.setCellRenderer(new DefaultTreeCellRenderer() {
            {
                setBackgroundNonSelectionColor(AssistantTheme.PANEL_ALT);
                setBackgroundSelectionColor(AssistantTheme.ACCENT_DARK);
                setTextNonSelectionColor(AssistantTheme.TEXT);
                setTextSelectionColor(AssistantTheme.TEXT);
                setBorderSelectionColor(AssistantTheme.ACCENT);
            }
            @Override public java.awt.Component getTreeCellRendererComponent(
                    JTree tree, Object value, boolean selected, boolean expanded,
                    boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                if (value instanceof DefaultMutableTreeNode node
                        && node.getUserObject() instanceof ScriptEntry entry && entry.folder) {
                    setIcon(UIManager.getIcon(expanded ? "Tree.openIcon" : "Tree.closedIcon"));
                }
                return this;
            }
        });
        scriptTree.addTreeSelectionListener(event -> openTreeSelection());
        scriptTree.setDropMode(DropMode.ON_OR_INSERT);
        if (!java.awt.GraphicsEnvironment.isHeadless()) scriptTree.setDragEnabled(true);
        scriptTree.setTransferHandler(new FileTreeReorderHandler(scriptTree, scriptLibrary,
                value -> ((ScriptEntry) value).path, this::reloadScriptTreeAfterMove,
                exception -> showError("The item could not be moved.", exception), null,
                path -> !isTutorialPath(path)));
        reloadScriptTree(currentFile);
        JPanel browser = new JPanel(new BorderLayout(0, 4));
        browser.setOpaque(false);
        JLabel heading = new JLabel("UScript Guide");
        AssistantTheme.stylePageTitle(heading);
        browser.add(heading, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(scriptTree);
        scroll.setBorder(AssistantTheme.titled("Scripts"));
        browser.add(scroll, BorderLayout.CENTER);
        browser.setPreferredSize(new Dimension(330, 0));
        return browser;
    }

    private JPanel createScriptActions() {
        JPanel actions = new JPanel(new EdgeAlignedFlowLayout(FlowLayout.LEFT, 5, 0));
        actions.setBackground(AssistantTheme.BACKGROUND);
        actions.setPreferredSize(new Dimension(330, 36));
        actions.add(button("+ Folder", event -> createScriptFolder()));
        actions.add(button("+ File", event -> createScriptFile()));
        return actions;
    }

    private void initializeScriptLibrary() {
        try {
            Files.createDirectories(scriptLibrary);
            Files.createDirectories(tutorialsFolder);
            for (int index = 0; index < UnrealScriptLearning.LESSONS.length; index++) {
                String lesson = UnrealScriptLearning.LESSONS[index];
                Path file = tutorialPath(lesson);
                String source = UnrealScriptLearning.exampleForLesson(lesson);
                Files.writeString(file, source, StandardCharsets.ISO_8859_1);
            }
        } catch (IOException exception) {
            showError("The script library could not be initialized.", exception);
        }
    }

    private void reloadScriptTree(Path selection) {
        scriptRoot.removeAllChildren();
        loadScriptChildren(scriptRoot, scriptLibrary);
        scriptTreeModel.reload();
        scriptTree.expandRow(0);
        if (selection != null) selectScriptPath(scriptRoot, selection.toAbsolutePath().normalize());
    }

    private void reloadScriptTreeAfterMove(Path target) {
        if (currentFile != null && !Files.exists(currentFile) && Files.isRegularFile(target))
            currentFile = target;
        reloadScriptTree(target);
        updateFileLabel();
    }

    private void loadScriptChildren(DefaultMutableTreeNode parent, Path folder) {
        try (var paths = Files.list(folder)) {
            List<Path> children = FileTreeOrder.sort(folder, paths
                    .filter(path -> Files.isDirectory(path)
                            || path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".uc"))
                    .toList());
            for (Path path : children) {
                boolean folderEntry = Files.isDirectory(path);
                boolean tutorial = isTutorialPath(path);
                String displayName = tutorial && !folderEntry ? tutorialDisplayName(path)
                        : path.getFileName().toString();
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(new ScriptEntry(
                        displayName, path, folderEntry, tutorial));
                parent.add(node);
                if (folderEntry) loadScriptChildren(node, path);
            }
        } catch (IOException exception) {
            showError("The script folders could not be read.", exception);
        }
    }

    private void openTreeSelection() {
        ScriptEntry entry = selectedScriptEntry();
        if (entry == null || entry.folder || entry.path.equals(currentFile)) return;
        if (!confirmDiscard()) return;
        try {
            currentFile = entry.path;
            editor.setText(Files.readString(currentFile, StandardCharsets.ISO_8859_1));
            editor.setCaretPosition(0);
            dirty = false;
            updateFileLabel();
            analyze();
            status.setText("Opened " + currentFile.getFileName() + ".");
        } catch (IOException exception) {
            showError("The script could not be opened.", exception);
        }
    }

    private void createScriptFolder() {
        Path parent = selectedScriptFolder();
        String name = askScriptName("New folder", "Folder name:", "New Folder");
        if (name == null) return;
        try {
            Path folder = uniqueScriptPath(parent, safeScriptName(name), "");
            Files.createDirectories(folder);
            reloadScriptTree(folder);
        } catch (IOException exception) {
            showError("The folder could not be created.", exception);
        }
    }

    private void createScriptFile() {
        Path parent = selectedScriptFolder();
        String name = askScriptName("New UnrealScript file", "Class name:", "MyClass");
        if (name == null) return;
        String className = safeScriptName(name).replaceAll("[^A-Za-z0-9_]", "_");
        try {
            Path file = uniqueScriptPath(parent, className, ".uc");
            Files.writeString(file, "class " + baseName(file) + " expands Actor;\n\ndefaultproperties\n{\n}\n",
                    StandardCharsets.ISO_8859_1);
            reloadScriptTree(file);
        } catch (IOException exception) {
            showError("The script file could not be created.", exception);
        }
    }

    private void renameScriptEntry() {
        ScriptEntry entry = selectedScriptEntry();
        if (entry == null || entry.path.equals(scriptLibrary)) return;
        if (entry.protectedEntry) {
            DarkDialogs.message(this, "Tutorials cannot be renamed.",
                    "Protected tutorial", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String initial = entry.folder ? entry.name : baseName(entry.path);
        String name = askScriptName("Rename", "New name:", initial);
        if (name == null) return;
        String suffix = entry.folder ? "" : ".uc";
        Path target = entry.path.resolveSibling(safeScriptName(name) + suffix);
        if (Files.exists(target)) {
            DarkDialogs.message(this, "An item with this name already exists.",
                    "Rename", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Files.move(entry.path, target);
            if (entry.path.equals(currentFile)) currentFile = target;
            reloadScriptTree(target);
            updateFileLabel();
        } catch (IOException exception) {
            showError("The item could not be renamed.", exception);
        }
    }

    private void deleteScriptEntry() {
        ScriptEntry entry = selectedScriptEntry();
        if (entry == null || entry.path.equals(scriptLibrary)) return;
        if (entry.protectedEntry) {
            DarkDialogs.message(this, "Tutorials cannot be deleted.",
                    "Protected tutorial", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!DeleteConfirmationSupport.confirm(this, entry.name)) return;
        try {
            if (entry.folder) {
                try (var paths = Files.walk(entry.path)) {
                    for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
                }
            } else Files.delete(entry.path);
            if (entry.path.equals(currentFile)) newFile();
            reloadScriptTree(null);
        } catch (IOException exception) {
            showError("The item could not be deleted.", exception);
        }
    }

    private ScriptEntry selectedScriptEntry() {
        Object selected = scriptTree.getLastSelectedPathComponent();
        return selected instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof ScriptEntry entry ? entry : null;
    }

    private Path selectedScriptFolder() {
        ScriptEntry selected = selectedScriptEntry();
        if (selected == null) return scriptLibrary;
        if (selected.protectedEntry) return scriptLibrary;
        return selected.folder ? selected.path : selected.path.getParent();
    }

    private boolean selectScriptPath(DefaultMutableTreeNode node, Path target) {
        if (node.getUserObject() instanceof ScriptEntry entry
                && entry.path.toAbsolutePath().normalize().equals(target)) {
            scriptTree.setSelectionPath(new TreePath(node.getPath()));
            scriptTree.scrollPathToVisible(new TreePath(node.getPath()));
            return true;
        }
        for (int index = 0; index < node.getChildCount(); index++)
            if (selectScriptPath((DefaultMutableTreeNode) node.getChildAt(index), target)) return true;
        return false;
    }

    private String askScriptName(String title, String message, String initial) {
        Object value = DarkDialogs.input(this, message, title, JOptionPane.PLAIN_MESSAGE,
                null, null, initial);
        return value == null || value.toString().trim().isEmpty() ? null : value.toString().trim();
    }

    private static Path uniqueScriptPath(Path parent, String base, String suffix) {
        Path result = parent.resolve(base + suffix);
        int number = 2;
        while (Files.exists(result)) result = parent.resolve(base + " " + number++ + suffix);
        return result;
    }

    private static String safeScriptName(String name) {
        String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return safe.isEmpty() ? "Untitled" : safe;
    }

    private boolean isTutorialPath(Path path) {
        return path.toAbsolutePath().normalize().startsWith(tutorialsFolder.toAbsolutePath().normalize());
    }

    private Path tutorialPath(String lesson) {
        int index = java.util.Arrays.asList(UnrealScriptLearning.LESSONS).indexOf(lesson);
        String name = safeScriptName(lesson).replaceAll("[^A-Za-z0-9._ -]", "_");
        return tutorialsFolder.resolve(String.format(Locale.ROOT, "%02d_%s.uc", index + 1, name));
    }

    private String tutorialDisplayName(Path path) {
        for (String lesson : UnrealScriptLearning.LESSONS)
            if (tutorialPath(lesson).equals(path)) return lesson;
        return baseName(path);
    }

    private static Path resolveScriptLibrary() {
        String data = System.getenv("LOCALAPPDATA");
        Path base = data == null || data.isBlank()
                ? Path.of(System.getProperty("user.home"), ".unreal-editor-2-assistant")
                : Path.of(data, "UnrealEditor2Assistant");
        return base.resolve("Scripts");
    }

    private void loadEditorGuideKnowledge() {
        editorGuideContext.setText("<html><body style='font-family:sans-serif;background:#11151b;"
                + "color:#e8edf4;padding:10px'>Loading related Editor Guide content...</body></html>");
        new SwingWorker<EditorHelpContentPack, Void>() {
            @Override protected EditorHelpContentPack doInBackground() throws Exception {
                Path root = new EditorHelpEnvironment().locateContentPack();
                return new EditorHelpContentPackLoader().load(root);
            }
            @Override protected void done() {
                try {
                    editorGuidePack = get();
                    updateEditorGuideContext(editor.getText());
                } catch (Exception exception) {
                    editorGuideContext.setText("<html><body style='font-family:sans-serif;background:#11151b;"
                            + "color:#9ca7b8;padding:10px'>Editor Guide content is unavailable.</body></html>");
                }
            }
        }.execute();
    }

    private void updateEditorGuideContext(String sourceCode) {
        if (editorGuidePack == null) return;
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String lower = sourceCode.toLowerCase(Locale.ROOT);
        String[] knownTerms = { "unrealscript", "actor", "trigger", "touch", "event", "tag",
                "mover", "timer", "network", "replication", "mutator", "weapon", "zone",
                "kicker", "teleporter", "collision", "package", "compile" };
        for (String term : knownTerms) if (lower.contains(term)) terms.add(term);
        Matcher declaration = CLASS.matcher(sourceCode);
        if (declaration.find()) terms.add(declaration.group(2).toLowerCase(Locale.ROOT));
        if (terms.isEmpty()) terms.add("unrealscript");

        List<RankedHelp> related = editorGuidePack.documents().stream()
                .map(document -> new RankedHelp(document, helpScore(document, terms)))
                .filter(item -> item.score > 0)
                .sorted(Comparator.comparingInt(RankedHelp::score).reversed()
                        .thenComparing(item -> item.document.title()))
                .limit(6)
                .toList();
        StringBuilder html = new StringBuilder("<html><body style='font-family:sans-serif;"
                + "background:#11151b;color:#e8edf4;padding:10px'>"
                + "<h2 style='margin-top:0'>Related Editor Guide articles</h2>"
                + "<p style='color:#9ca7b8'>Matched from the current code: ")
                .append(html(String.join(", ", terms))).append("</p>");
        if (related.isEmpty()) html.append("<p>No related articles found.</p>");
        for (RankedHelp item : related) {
            EditorHelpContentPack.HelpDocument document = item.document;
            html.append("<div style='margin:8px 0;padding:9px;background:#1e242e;"
                            + "border-left:4px solid #66aaf2'><a href='help:")
                    .append(html(document.id())).append("'><b>").append(html(document.title()))
                    .append("</b></a><br><span style='color:#9ca7b8'>")
                    .append(html(String.join(" / ", document.categoryPath())))
                    .append("</span><br>").append(html(helpExcerpt(document.text(), terms)))
                    .append("</div>");
        }
        html.append("</body></html>");
        relatedEditorGuideHtml = html.toString();
        showRelatedEditorGuideResults();
    }

    private static int helpScore(EditorHelpContentPack.HelpDocument document, Iterable<String> terms) {
        String title = document.title().toLowerCase(Locale.ROOT);
        String headings = String.join(" ", document.headings()).toLowerCase(Locale.ROOT);
        String categories = String.join(" ", document.categoryPath()).toLowerCase(Locale.ROOT);
        String text = document.text().toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (title.contains(term)) score += 5;
            if (headings.contains(term)) score += 3;
            if (categories.contains(term)) score += 2;
            if (text.contains(term)) score++;
        }
        return score;
    }

    private static String helpExcerpt(String text, Iterable<String> terms) {
        if (text == null || text.isBlank()) return "";
        String compact = text.replaceAll("\\s+", " ").trim();
        String lower = compact.toLowerCase(Locale.ROOT);
        int match = -1;
        for (String term : terms) {
            int found = lower.indexOf(term);
            if (found >= 0 && (match < 0 || found < match)) match = found;
        }
        int start = match < 0 ? 0 : Math.max(0, match - 70);
        int end = Math.min(compact.length(), start + 260);
        return (start > 0 ? "... " : "") + compact.substring(start, end)
                + (end < compact.length() ? " ..." : "");
    }

    private void showEditorGuideDocument(String id) {
        if (editorGuidePack == null) return;
        editorGuidePack.documents().stream().filter(document -> document.id().equals(id)).findFirst()
                .ifPresent(document -> {
                    String body = html(document.text()).replace("\n", "<br>");
                    editorGuideContext.setText("<html><body style='font-family:sans-serif;background:#11151b;"
                            + "color:#e8edf4;padding:10px'><p><a href='context:back'>&#8592; Back to related results</a></p><br>"
                            + "<h1 style='font-size:14px;font-weight:bold;margin-top:0'>"
                            + html(document.title()) + "</h1>"
                            + "<p style='color:#9ca7b8'>" + html(String.join(" / ", document.categoryPath()))
                            + "</p><p>" + body + "</p></body></html>");
                    editorGuideContext.setCaretPosition(0);
                });
    }

    private void showRelatedEditorGuideResults() {
        if (relatedEditorGuideHtml.isBlank()) return;
        editorGuideContext.setText(relatedEditorGuideHtml);
        editorGuideContext.setCaretPosition(0);
    }

    private record RankedHelp(EditorHelpContentPack.HelpDocument document, int score) { }

    private void configureHtmlPane(JTextPane pane) {
        pane.setContentType("text/html");
        pane.setEditable(false);
        pane.setBackground(AssistantTheme.CODE_BACKGROUND);
    }

    private JPanel createLearningPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBackground(AssistantTheme.CODE_BACKGROUND);
        JComboBox<String> lessons = new JComboBox<>(UnrealScriptLearning.LESSONS);
        lessons.addActionListener(event -> showLesson((String) lessons.getSelectedItem()));
        JPanel selector = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        selector.setOpaque(false);
        selector.add(new JLabel("Lesson:"));
        selector.add(lessons);
        selector.add(button("Load lesson example", event -> loadLessonExample(
                (String) lessons.getSelectedItem())));
        panel.add(selector, BorderLayout.NORTH);
        panel.add(new JScrollPane(learning), BorderLayout.CENTER);
        showLesson(UnrealScriptLearning.LESSONS[0]);
        return panel;
    }

    private void showLesson(String name) {
        learning.setText(UnrealScriptLearning.lesson(name));
        learning.setCaretPosition(0);
    }

    private void loadLessonExample(String lesson) {
        if (lesson == null || !confirmDiscard()) return;
        Path tutorial = tutorialPath(lesson);
        try {
            currentFile = tutorial;
            editor.setText(Files.readString(tutorial, StandardCharsets.ISO_8859_1));
            editor.setCaretPosition(0);
            dirty = false;
            updateFileLabel();
            selectScriptPath(scriptRoot, tutorial.toAbsolutePath().normalize());
            analyze();
            status.setText("Loaded tutorial " + lesson + ".");
        } catch (IOException exception) {
            showError("The tutorial could not be loaded.", exception);
        }
    }

    private JButton button(String text, java.util.function.Consumer<ActionEvent> action) {
        JButton button = new JButton(text);
        button.addActionListener(action::accept);
        return button;
    }

    private void installShortcuts() {
        shortcut("control S", "save", this::save);
        shortcut("F6", "analyze", () -> { analyze(); return true; });
        shortcut("F7", "compile", () -> { compile(); return true; });
    }

    private void shortcut(String key, String name, java.util.function.BooleanSupplier action) {
        editor.getInputMap().put(KeyStroke.getKeyStroke(key), name);
        editor.getActionMap().put(name, new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { action.getAsBoolean(); }
        });
    }

    private void newFile() {
        if (!confirmDiscard()) return;
        currentFile = null;
        setTemplate("Basic Actor");
        dirty = false;
        updateFileLabel();
        analyze();
    }

    private void insertTemplate(String name) {
        if (dirty && DarkDialogs.confirm(this,
                "Replace the current source with the selected template?", "Insert template",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        currentFile = null;
        setTemplate(name);
        dirty = true;
        updateFileLabel();
        analyze();
    }

    private void setTemplate(String name) {
        editor.setText(UnrealScriptLearning.template(name));
        editor.setCaretPosition(0);
    }

    private void open() {
        if (!confirmDiscard()) return;
        JFileChooser chooser = scriptChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            currentFile = chooser.getSelectedFile().toPath();
            editor.setText(Files.readString(currentFile, StandardCharsets.ISO_8859_1));
            editor.setCaretPosition(0);
            dirty = false;
            updateFileLabel();
            analyze();
            status.setText("Opened " + currentFile.getFileName() + ".");
        } catch (IOException exception) {
            showError("The script could not be opened.", exception);
        }
    }

    private boolean save() {
        if (currentFile != null && isTutorialPath(currentFile)) return saveAs();
        return currentFile == null ? saveAs() : writeFile(currentFile);
    }

    private boolean saveAs() {
        JFileChooser chooser = scriptChooser();
        String className = className();
        chooser.setSelectedFile(new File((className == null ? "MyClass" : className) + ".uc"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return false;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".uc"))
            file = new File(file.getParentFile(), file.getName() + ".uc");
        if (!FileSaveSupport.confirmOverwrite(this, file)) return false;
        return writeFile(file.toPath());
    }

    private boolean writeFile(Path targetFile) {
        try {
            Files.writeString(targetFile, editor.getText(), StandardCharsets.ISO_8859_1);
            currentFile = targetFile;
            dirty = false;
            updateFileLabel();
            status.setText("Saved " + currentFile.getFileName() + ".");
            return true;
        } catch (IOException exception) {
            showError("The script could not be saved.", exception);
            return false;
        }
    }

    private JFileChooser scriptChooser() {
        JFileChooser chooser = new DarkFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("UnrealScript source (*.uc)", "uc"));
        if (currentFile != null) chooser.setCurrentDirectory(currentFile.toFile().getParentFile());
        return chooser;
    }

    private void analyze() {
        String source = editor.getText();
        List<Finding> results = new ArrayList<>();
        Matcher declaration = CLASS.matcher(source);
        String declaredClass = null;
        String parentClass = null;
        if (!declaration.find()) {
            results.add(error("Add a declaration such as <code>class MyActor expands Actor;</code>."));
        } else {
            declaredClass = declaration.group(1);
            parentClass = declaration.group(2);
            if (currentFile != null && !isTutorialPath(currentFile)
                    && !baseName(currentFile).equalsIgnoreCase(declaredClass))
                results.add(error("The file name must match the class: <code>"
                        + html(declaredClass) + ".uc</code>."));
        }
        checkPairs(source, results);
        if (!Pattern.compile("(?im)^\\s*defaultproperties\\s*\\{").matcher(source).find())
            results.add(warning("Add a <code>defaultproperties</code> block for class defaults."));
        if (source.matches("(?is).*\\bPostBeginPlay\\s*\\(.*")
                && !source.matches("(?is).*\\bSuper\\.PostBeginPlay\\s*\\(\\s*\\).*"))
            results.add(warning("A <code>PostBeginPlay</code> override should normally call "
                    + "<code>Super.PostBeginPlay()</code>."));
        if (source.matches("(?is).*\\bTick\\s*\\(.*"))
            results.add(tip("Keep <code>Tick()</code> small; it runs every frame."));
        if (source.matches("(?is).*\\bCheckReplacement\\s*\\(.*"))
            results.add(tip("Return <code>false</code> to remove the original actor. Guard a replacement "
                    + "against replacing itself, or the mutator can loop forever."));
        if (source.matches("(?is).*\\breplication\\s*\\{.*"))
            results.add(tip("UT networking is server-authoritative. Variable replication travels from "
                    + "the authority to clients; function replication also depends on ownership."));
        if (source.matches("(?is).*\\bsimulated\\s+(?:function|event|state).*"))
            results.add(tip("A simulated function may run on a client proxy, but it does not send its "
                    + "changes back to the server."));
        if (source.matches("(?is).*RemoteRole\\s*=\\s*ROLE_SimulatedProxy.*"))
            results.add(tip("<code>ROLE_SimulatedProxy</code> suits predictable actors such as projectiles."));
        if (source.matches("(?is).*\\bTouch\\s*\\(.*")
                && !source.matches("(?is).*\\b(?:Pawn|PlayerPawn)\\s*\\(\\s*Other\\s*\\).*"))
            results.add(tip("For a player-only BunnyTrack trigger, cast <code>Other</code> to "
                    + "<code>Pawn</code> or <code>PlayerPawn</code> and check the result against "
                    + "<code>None</code>."));
        if (source.matches("(?is).*\\bTouch\\s*\\(.*")
                && !source.matches("(?is).*\\bbCollideActors\\s*=\\s*True.*"))
            results.add(warning("<code>Touch()</code> requires collision. Add "
                    + "<code>bCollideActors=True</code> and suitable collision dimensions."));
        if (source.matches("(?is).*\\bSetTimer\\s*\\(.*")
                && !source.matches("(?is).*\\b(?:function|event)\\s+Timer\\s*\\(\\s*\\).*"))
            results.add(warning("<code>SetTimer()</code> is used, but this class does not implement "
                    + "<code>Timer()</code>. An inherited implementation may run instead."));
        if (source.matches("(?is).*\\bTriggerEvent\\s*\\(\\s*Event.*")
                && !source.matches("(?is).*\\bEvent\\s*=\\s*\\w+.*"))
            results.add(tip("Set the placed actor's <code>Event</code> in UnrealEd. It must match "
                    + "the target Mover or actor <code>Tag</code>."));
        checkDefaultProperties(source, results);
        if (results.stream().noneMatch(f -> f.level != Level.TIP))
            results.add(new Finding(Level.OK, "No structural problems found. UCC remains authoritative."));
        results.add(tip("Package layout: <code>UnrealTournament/&lt;Package&gt;/Classes/"
                + (declaredClass == null ? "&lt;Class&gt;" : html(declaredClass))
                + ".uc</code>. Add the package to <code>EditPackages</code>, then run <code>ucc make</code>."));
        reference.setText(UnrealScriptLearning.referenceFor(parentClass));
        reference.setCaretPosition(0);
        render(results);
        updateEditorGuideContext(source);
        long errors = results.stream().filter(f -> f.level == Level.ERROR).count();
        long warnings = results.stream().filter(f -> f.level == Level.WARNING).count();
        status.setText("Analysis complete: " + errors + " error(s), " + warnings + " warning(s).");
    }

    private void checkDefaultProperties(String source, List<Finding> results) {
        Matcher block = Pattern.compile("(?is)\\bdefaultproperties\\s*\\{(.*)\\}\\s*$").matcher(source);
        if (!block.find()) return;
        String defaults = block.group(1);
        for (String rawLine : defaults.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("//")) continue;
            if (line.endsWith(";")) {
                results.add(warning("Do not put semicolons after assignments in "
                        + "<code>defaultproperties</code>: <code>" + html(line) + "</code>"));
                break;
            }
            if (!line.contains("=") && !line.equals("{") && !line.equals("}")) {
                results.add(warning("<code>defaultproperties</code> contains a line that is not a "
                        + "property assignment: <code>" + html(line) + "</code>"));
                break;
            }
        }
    }

    private void checkPairs(String source, List<Finding> results) {
        int braces = 0, parentheses = 0;
        boolean string = false, lineComment = false, blockComment = false;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i), n = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (lineComment) { if (c == '\n') lineComment = false; continue; }
            if (blockComment) {
                if (c == '*' && n == '/') { blockComment = false; i++; }
                continue;
            }
            if (!string && c == '/' && n == '/') { lineComment = true; i++; continue; }
            if (!string && c == '/' && n == '*') { blockComment = true; i++; continue; }
            if (c == '"' && (i == 0 || source.charAt(i - 1) != '\\')) { string = !string; continue; }
            if (string) continue;
            if (c == '{') braces++; else if (c == '}') braces--;
            else if (c == '(') parentheses++; else if (c == ')') parentheses--;
        }
        if (braces != 0) results.add(error("Opening and closing braces are not balanced."));
        if (parentheses != 0) results.add(error("Opening and closing parentheses are not balanced."));
        if (string) results.add(error("A string literal is not closed."));
        if (blockComment) results.add(error("A block comment is not closed."));
    }

    private void render(List<Finding> results) {
        StringBuilder out = new StringBuilder("""
                <html><body style='font-family:sans-serif;background:#11151b;color:#e8edf4;padding:10px'>
                """);
        for (Finding finding : results) {
            String color = switch (finding.level) {
                case ERROR -> "#ef7777"; case WARNING -> "#e7bd68";
                case TIP -> "#79aef0"; case OK -> "#66cf8e";
            };
            out.append("<div style='margin:8px 0;padding:8px;border-left:4px solid ")
                    .append(color).append(";background:#1e242e'><b style='color:")
                    .append(color).append("'>").append(finding.level.label)
                    .append("</b><br>").append(finding.message).append("</div>");
        }
        out.append("</body></html>");
        advice.setText(out.toString());
        advice.setCaretPosition(0);
    }

    private void chooseUcc() {
        JFileChooser chooser = new DarkFileChooser();
        chooser.setDialogTitle("Select Unreal Tournament compiler");
        chooser.setFileFilter(new FileNameExtensionFilter("UCC executable (ucc.exe)", "exe"));
        if (uccExecutable != null) chooser.setSelectedFile(uccExecutable.toFile());
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path selected = chooser.getSelectedFile().toPath();
        if (!selected.getFileName().toString().equalsIgnoreCase("ucc.exe")) {
            DarkDialogs.message(this, "Select ucc.exe in UnrealTournament\\System.",
                    "Invalid compiler", JOptionPane.WARNING_MESSAGE);
            return;
        }
        uccExecutable = selected;
        PREFS.put("uccPath", selected.toString());
        status.setText("UCC compiler: " + selected);
    }

    private void restoreUccPath() {
        String saved = PREFS.get("uccPath", "");
        if (!saved.isBlank() && Files.isRegularFile(Path.of(saved))) uccExecutable = Path.of(saved);
    }

    private void compile() {
        analyze();
        if (uccExecutable == null || !Files.isRegularFile(uccExecutable)) {
            chooseUcc();
            if (uccExecutable == null) return;
        }
        if (!save()) return;
        String setupIssue = compilerSetupIssue();
        if (setupIssue != null) {
            DarkDialogs.message(this, setupIssue, "Package setup required",
                    JOptionPane.WARNING_MESSAGE);
            status.setText("Compilation stopped: package setup is incomplete.");
            return;
        }
        Path compiler = uccExecutable;
        compileButton.setEnabled(false);
        compilerOutput.setText("Running: \"" + compiler + "\" make\n\n");
        assistantTabs.setSelectedIndex(4);
        status.setText("Compiling UnrealScript packages...");
        new SwingWorker<Integer, String>() {
            @Override protected Integer doInBackground() throws Exception {
                ProcessBuilder builder = new ProcessBuilder(compiler.toString(), "make");
                builder.directory(compiler.getParent().toFile());
                builder.redirectErrorStream(true);
                Process process = builder.start();
                try (BufferedReader input = new BufferedReader(new InputStreamReader(
                        process.getInputStream(), StandardCharsets.ISO_8859_1))) {
                    String line;
                    while ((line = input.readLine()) != null) publish(line);
                }
                return process.waitFor();
            }
            @Override protected void process(List<String> lines) {
                for (String line : lines) compilerOutput.append(line + System.lineSeparator());
                compilerOutput.setCaretPosition(compilerOutput.getDocument().getLength());
            }
            @Override protected void done() {
                compileButton.setEnabled(true);
                try {
                    int code = get();
                    compilerOutput.append("\nUCC exited with code " + code + ".\n");
                    appendCompilerExplanation(compilerOutput.getText());
                    status.setText(code == 0 ? "Compilation completed successfully."
                            : "Compilation failed. Review the compiler output.");
                } catch (Exception exception) {
                    compilerOutput.append("\nCould not run UCC: " + exception.getMessage() + "\n");
                    status.setText("Could not run the compiler.");
                }
            }
        }.execute();
    }

    private void appendCompilerExplanation(String output) {
        String lower = output.toLowerCase(Locale.ROOT);
        List<String> explanations = new ArrayList<>();
        if (lower.contains("bad or missing expression") || lower.contains("parse error"))
            explanations.add("Check the reported line for a missing value, parenthesis, brace, "
                    + "operator, or semicolon. Remember: defaultproperties assignments have no semicolon.");
        if (lower.contains("unrecognized member") || lower.contains("unknown property"))
            explanations.add("The member may not exist in this class hierarchy, or its name is misspelled.");
        if (lower.contains("can't find") || lower.contains("failed to load"))
            explanations.add("Check package names, object references, EditPackages order, and imported assets.");
        if (lower.contains("superclass") || lower.contains("parent class"))
            explanations.add("The parent class must be available from an earlier EditPackages entry.");
        if (lower.contains("already exists") || lower.contains("package") && lower.contains("exists"))
            explanations.add("An old compiled .u package may block rebuilding. Back it up and remove it "
                    + "manually only when you intend to rebuild that package.");
        if (lower.contains("error") && explanations.isEmpty())
            explanations.add("Start with the first compiler error. Later errors are often consequences of it.");
        if (explanations.isEmpty()) return;
        compilerOutput.append("\nAssistant explanation:\n");
        for (String explanation : explanations) compilerOutput.append("- " + explanation + "\n");
    }

    private String compilerSetupIssue() {
        Path systemDirectory = uccExecutable.getParent();
        Path gameDirectory = systemDirectory == null ? null : systemDirectory.getParent();
        if (gameDirectory == null || currentFile == null) return "The compiler path is incomplete.";
        Path relative;
        try {
            relative = gameDirectory.toAbsolutePath().normalize()
                    .relativize(currentFile.toAbsolutePath().normalize());
        } catch (IllegalArgumentException exception) {
            relative = null;
        }
        if (relative == null || relative.getNameCount() != 3
                || !relative.getName(1).toString().equalsIgnoreCase("Classes")) {
            return "Save the class inside the selected Unreal Tournament installation:\n\n"
                    + gameDirectory + "\\<Package>\\Classes\\"
                    + (className() == null ? "<Class>" : className()) + ".uc";
        }
        String packageName = relative.getName(0).toString();
        Path configuration = systemDirectory.resolve("UnrealTournament.ini");
        if (!Files.isRegularFile(configuration)) {
            return "UnrealTournament.ini was not found next to ucc.exe.";
        }
        try {
            boolean configured = Files.readAllLines(configuration, StandardCharsets.ISO_8859_1)
                    .stream()
                    .map(String::trim)
                    .anyMatch(line -> line.equalsIgnoreCase("EditPackages=" + packageName));
            if (!configured) {
                return "Add this line to [Editor.EditorEngine] in UnrealTournament.ini:\n\n"
                        + "EditPackages=" + packageName;
            }
        } catch (IOException exception) {
            return "UnrealTournament.ini could not be read:\n" + exception.getMessage();
        }
        return null;
    }

    private boolean confirmDiscard() {
        return !dirty || DarkDialogs.confirm(this, "Discard the unsaved changes?",
                "Unsaved script", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private String className() {
        Matcher matcher = CLASS.matcher(editor.getText());
        return matcher.find() ? matcher.group(1) : null;
    }

    private void updateFileLabel() {
        String path = currentFile == null ? "Untitled.uc" : currentFile.toString();
        sourceScroll.setBorder(AssistantTheme.titled(
                "UnrealScript Code: " + (dirty ? "* " : "") + path));
        sourceScroll.repaint();
    }

    private void showError(String message, Exception exception) {
        DarkDialogs.message(this, message + "\n" + exception.getMessage(),
                "UScript Guide", JOptionPane.ERROR_MESSAGE);
    }

    private static String baseName(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Finding error(String text) { return new Finding(Level.ERROR, text); }
    private static Finding warning(String text) { return new Finding(Level.WARNING, text); }
    private static Finding tip(String text) { return new Finding(Level.TIP, text); }
    private enum Level {
        ERROR("Error"), WARNING("Warning"), TIP("Tip"), OK("OK");
        private final String label;
        Level(String label) { this.label = label; }
    }
    private record Finding(Level level, String message) { }
    private record ScriptEntry(String name, Path path, boolean folder, boolean protectedEntry) {
        @Override public String toString() { return name; }
    }

    @FunctionalInterface
    private interface DocumentChange extends javax.swing.event.DocumentListener {
        void changed();
        @Override default void insertUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        @Override default void removeUpdate(javax.swing.event.DocumentEvent event) { changed(); }
        @Override default void changedUpdate(javax.swing.event.DocumentEvent event) { changed(); }
    }
}
