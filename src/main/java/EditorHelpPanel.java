import com.fasterxml.jackson.databind.JsonNode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTree;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.JFileChooser;
import javax.swing.KeyStroke;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.ImageView;
import javax.swing.text.html.StyleSheet;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/** English-only browser and Lucene search UI for the external Editor Help pack. */
public final class EditorHelpPanel extends JPanel {
    private final JTextField searchField = new JTextField();
    private final DefaultListModel<EditorHelpSearch.SearchResult> resultModel = new DefaultListModel<>();
    private final JList<EditorHelpSearch.SearchResult> resultList = new JList<>(resultModel);
    private final JTree categoryTree = new JTree(new DefaultMutableTreeNode("Loading Editor Guide…"));
    private final JTabbedPane browseTabs = new JTabbedPane();
    private final JEditorPane article = new JEditorPane();
    private final HTMLEditorKit htmlKit = new HelpHtmlEditorKit();
    private final JScrollPane articleScroll = new JScrollPane(article);
    private final JLabel status = new JLabel("Loading Guide content…");
    private final JLabel source = new JLabel(" ");
    private final JButton back = new JButton("Back");
    private final JButton forward = new JButton("Forward");
    private final JButton home = new JButton("Home");
    private final JButton importTutorial = new JButton("Import Tutorial...");
    private final ArrayDeque<String> backHistory = new ArrayDeque<>();
    private final ArrayDeque<String> forwardHistory = new ArrayDeque<>();
    private final Timer searchTimer;
    private final Map<String, EditorHelpContentPack.HelpDocument> documentsById = new HashMap<>();
    private EditorHelpContentPack pack;
    private EditorHelpSearch search;
    private String currentDocumentId;

    public EditorHelpPanel() {
        this(null, null);
        loadEnvironment();
    }

    EditorHelpPanel(EditorHelpContentPack pack, EditorHelpSearch search) {
        super(new BorderLayout(0, 8));
        setBackground(AssistantTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        searchTimer = new Timer(250, event -> runSearch());
        searchTimer.setRepeats(false);
        createUi();
        if (pack != null && search != null) installContent(pack, search);
    }

    private void createUi() {
        JPanel searchBar = new JPanel(new BorderLayout(8, 0));
        searchBar.setOpaque(false);
        JLabel title = new JLabel("Search:");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        searchField.setToolTipText("Search titles, headings, categories and tutorial text");
        searchField.getDocument().addDocumentListener(new SimpleDocumentListener(searchTimer::restart));
        searchField.addActionListener(event -> {
            if (!resultModel.isEmpty()) {
                resultList.setSelectedIndex(0);
                resultList.requestFocusInWindow();
            }
        });
        searchBar.add(title, BorderLayout.WEST);
        searchBar.add(searchField, BorderLayout.CENTER);
        importTutorial.setToolTipText("Import an HTML or PDF tutorial into New Tutorials");
        importTutorial.addActionListener(event -> chooseTutorial());
        searchBar.add(importTutorial, BorderLayout.EAST);
        add(searchBar, BorderLayout.NORTH);

        TransferHandler tutorialDrop = new TutorialDropHandler();
        setTransferHandler(tutorialDrop);
        article.setTransferHandler(tutorialDrop);
        categoryTree.setTransferHandler(tutorialDrop);
        resultList.setTransferHandler(tutorialDrop);
        javax.swing.Action pasteImportAction = new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { pasteTutorial(); }
        };
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "import-tutorial-paste");
        getActionMap().put("import-tutorial-paste", pasteImportAction);
        bindTutorialPaste(article, pasteImportAction);
        bindTutorialPaste(categoryTree, pasteImportAction);
        bindTutorialPaste(resultList, pasteImportAction);

        categoryTree.setRootVisible(false);
        categoryTree.setShowsRootHandles(true);
        categoryTree.setBackground(AssistantTheme.PANEL);
        categoryTree.setForeground(AssistantTheme.TEXT);
        categoryTree.setCellRenderer(new HelpTreeRenderer());
        categoryTree.addTreeSelectionListener(event -> {
            Object selected = categoryTree.getLastSelectedPathComponent();
            if (selected instanceof DefaultMutableTreeNode node
                    && node.getUserObject() instanceof TreeEntry entry && entry.documentId != null) {
                openDocument(entry.documentId, true);
            }
        });

        resultList.setBackground(AssistantTheme.PANEL);
        resultList.setForeground(AssistantTheme.TEXT);
        resultList.setSelectionBackground(AssistantTheme.ACCENT_DARK);
        resultList.setCellRenderer(new SearchResultRenderer());
        resultList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && resultList.getSelectedValue() != null) {
                openDocument(resultList.getSelectedValue().id(), true);
            }
        });

        JScrollPane contentsScroll = new JScrollPane(categoryTree);
        JScrollPane resultsScroll = new JScrollPane(resultList);
        contentsScroll.setBorder(BorderFactory.createLineBorder(AssistantTheme.BACKGROUND));
        resultsScroll.setBorder(BorderFactory.createLineBorder(AssistantTheme.BACKGROUND));
        contentsScroll.getViewport().setBackground(AssistantTheme.PANEL);
        resultsScroll.getViewport().setBackground(AssistantTheme.PANEL);
        browseTabs.setBackground(AssistantTheme.BACKGROUND);
        browseTabs.setForeground(AssistantTheme.TEXT);
        AssistantTheme.styleTabbedPane(browseTabs);
        browseTabs.setBorder(BorderFactory.createLineBorder(AssistantTheme.BACKGROUND));
        browseTabs.addTab("Contents", contentsScroll);
        browseTabs.addTab("Search Results", resultsScroll);
        browseTabs.setPreferredSize(new Dimension(350, 620));

        article.setEditable(false);
        article.setContentType("text/html");
        article.setBackground(AssistantTheme.CODE_BACKGROUND);
        article.setForeground(AssistantTheme.TEXT);
        article.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 28));
        StyleSheet isolatedStyles = new StyleSheet();
        isolatedStyles.addStyleSheet(htmlKit.getStyleSheet());
        isolatedStyles.addRule("html, body { background: #0c0f14; color: #e8edf4; }");
        isolatedStyles.addRule("body { font-family: Verdana, sans-serif; font-size: 12px; "
                + "margin-top: 22px; margin-left: 0; margin-bottom: 22px; margin-right: 0; }");
        isolatedStyles.addRule("p, div, li, td, th, blockquote { font-family: Verdana, sans-serif; "
                + "font-size: 12px; text-align: left; }");
        isolatedStyles.addRule("p { margin-top: 7px; margin-bottom: 9px; }");
        isolatedStyles.addRule("ul, ol { margin-top: 8px; margin-bottom: 10px; }");
        isolatedStyles.addRule("li { margin-top: 4px; margin-bottom: 5px; }");
        isolatedStyles.addRule("li p { margin-top: 0; }");
        isolatedStyles.addRule("font { font-family: Verdana, sans-serif; font-size: 12px; }");
        isolatedStyles.addRule("h1, h2, h3, h4, h5, h6 { color: #e8edf4; "
                + "font-family: Verdana, sans-serif; text-align: left; margin-top: 16px; margin-bottom: 9px; }");
        isolatedStyles.addRule("h1 { font-size: 23px; }");
        isolatedStyles.addRule("h2 { font-size: 20px; }");
        isolatedStyles.addRule("h3 { font-size: 17px; }");
        isolatedStyles.addRule("h4, h5, h6 { font-size: 15px; }");
        isolatedStyles.addRule(".help-page-title, .help-page-title font { font-family: Verdana, sans-serif; "
                + "font-size: 14px; font-weight: bold; text-align: left; }");
        isolatedStyles.addRule(".help-vocabulary-lists { list-style-type: none; margin-left: 0; padding-left: 0; }");
        isolatedStyles.addRule(".help-vocabulary-root, .help-vocabulary-root font { "
                + "font-size: 13px; font-weight: bold; text-decoration: none; }");
        isolatedStyles.addRule(".help-vocabulary-term, .help-vocabulary-term font { text-decoration: underline; }");
        isolatedStyles.addRule(".help-fog-heading, .help-fog-heading font { font-weight: bold; }");
        isolatedStyles.addRule(".help-fog-tutorial, .help-fog-tutorial font { "
                + "font-size: 14px; font-weight: bold; }");
        isolatedStyles.addRule(".help-fog-warning, .help-fog-warning font { "
                + "color: #ef6666; font-weight: bold; }");
        isolatedStyles.addRule("hr { width: 97%; text-align: left; margin-left: 0; margin-right: 0; }");
        isolatedStyles.addRule("a { color: #66aaf2; }");
        isolatedStyles.addRule("pre, code, tt { background: #11151b; color: #dce7f5; "
                + "font-family: Monospaced; font-size: 12px; text-align: left; white-space: pre-wrap; }");
        isolatedStyles.addRule("table, td, th { color: #e8edf4; border-color: #3a4352; }");
        isolatedStyles.addRule("table { text-align: left; margin-left: 0; }");
        isolatedStyles.addRule("table.help-data-table { border-collapse: collapse; border: 1px solid #3a4352; }");
        isolatedStyles.addRule("table.help-data-table td, table.help-data-table th { "
                + "border: 1px solid #3a4352; padding: 3px 5px; }");
        isolatedStyles.addRule("img { max-width: 100%; }");
        isolatedStyles.addRule(".help-image-only { text-align: center; }");
        isolatedStyles.addRule("p.help-pdf-paragraph { font-size: 15px; line-height: 1.5; "
                + "margin-top: 10px; margin-left: 0; margin-right: 0; margin-bottom: 12px; }");
        isolatedStyles.addRule(".help-pdf-image { margin-top: 16px; margin-bottom: 18px; text-align: center; }");
        isolatedStyles.addRule("table.help-icon-entry { margin-top: 8px; margin-bottom: 10px; }");
        isolatedStyles.addRule("td.help-icon { padding-right: 7px; }");
        isolatedStyles.addRule("td.help-icon-heading { font-weight: bold; vertical-align: middle; }");
        isolatedStyles.addRule("td.help-icon-description { padding-top: 4px; }");
        isolatedStyles.addRule(".help-editor-header-buttons p { margin-bottom: 16px; }");
        isolatedStyles.addRule(".help-toolbar-section, .help-toolbar-section font { "
                + "font-size: 14px; font-weight: bold; }");
        isolatedStyles.addRule("table.help-brush-action { margin-top: 4px; margin-bottom: 8px; }");
        isolatedStyles.addRule("td.help-brush-action-text { vertical-align: middle; }");
        isolatedStyles.addRule("pre.help-keymover-code { background: #1a2029; color: #dce7f5; "
                + "border: 1px solid #3a4352; padding: 10px; }");
        htmlKit.setStyleSheet(isolatedStyles);
        article.setEditorKit(htmlKit);
        articleScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel viewer = new JPanel(new BorderLayout(0, 6));
        viewer.setBackground(AssistantTheme.PANEL);
        JPanel navigation = new JPanel(new BorderLayout(8, 0));
        navigation.setOpaque(false);
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        back.setEnabled(false);
        forward.setEnabled(false);
        back.addActionListener(event -> navigateBack());
        forward.addActionListener(event -> navigateForward());
        home.addActionListener(event -> showHome());
        buttons.add(back);
        buttons.add(forward);
        buttons.add(home);
        navigation.add(buttons, BorderLayout.WEST);
        source.setForeground(AssistantTheme.MUTED);
        navigation.add(source, BorderLayout.CENTER);
        viewer.add(navigation, BorderLayout.NORTH);
        articleScroll.getViewport().setBackground(AssistantTheme.CODE_BACKGROUND);
        articleScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        viewer.add(articleScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, browseTabs, viewer);
        split.setResizeWeight(0.29);
        split.setDividerLocation(350);
        AssistantTheme.styleSplitPane(split);
        SplitPaneState.install(split, EditorHelpPanel.class, "browser-viewer");
        add(split, BorderLayout.CENTER);
        showMessage("Editor Help is loading", "The external English content pack is being validated.");
    }

    private void loadEnvironment() {
        new SwingWorker<EditorHelpEnvironment.Session, Void>() {
            @Override protected EditorHelpEnvironment.Session doInBackground() throws Exception {
                return new EditorHelpEnvironment().open();
            }
            @Override protected void done() {
                try {
                    EditorHelpEnvironment.Session session = get();
                    installContent(session.pack(), session.search());
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    status.setText("Content unavailable");
                    showMessage("Editor Help is unavailable", escapeHtml(cause.getMessage()));
                }
            }
        }.execute();
    }

    private void chooseTutorial() {
        File home = new File(System.getProperty("user.home", "."));
        JFileChooser chooser = new DarkFileChooser(FileSaveSupport.preferredDirectory(null, home));
        chooser.setDialogTitle("Import Tutorial");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Tutorials (*.html, *.htm, *.pdf)", "html", "htm", "pdf"));
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            importTutorial(chooser.getSelectedFile().toPath());
        }
    }

    private void pasteTutorial() {
        try {
            Transferable clipboard = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            Path file = tutorialPath(clipboard);
            if (file == null) throw new IllegalArgumentException(
                    "Copy one HTML or PDF file in Explorer, then press Ctrl+V.");
            importTutorial(file);
        } catch (Exception exception) {
            DarkDialogs.message(this, exception.getMessage(), "Cannot paste tutorial", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void importTutorial(Path file) {
        if (pack == null) {
            DarkDialogs.message(this, "The Editor Guide content pack is not available.",
                    "Cannot import tutorial", JOptionPane.WARNING_MESSAGE);
            return;
        }
        importTutorial.setEnabled(false);
        status.setText("Importing tutorial...");
        new SwingWorker<EditorHelpTutorialImporter.ImportResult, Void>() {
            @Override protected EditorHelpTutorialImporter.ImportResult doInBackground() throws Exception {
                return new EditorHelpTutorialImporter().importTutorial(file, pack.root());
            }
            @Override protected void done() {
                try {
                    EditorHelpTutorialImporter.ImportResult result = get();
                    reloadAfterImport(result);
                } catch (Exception exception) {
                    importTutorial.setEnabled(true);
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    status.setText("Import failed");
                    DarkDialogs.message(EditorHelpPanel.this, cause.getMessage(),
                            "Tutorial import failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void reloadAfterImport(EditorHelpTutorialImporter.ImportResult result) {
        status.setText("Updating tutorial search...");
        new SwingWorker<EditorHelpEnvironment.Session, Void>() {
            @Override protected EditorHelpEnvironment.Session doInBackground() throws Exception {
                return new EditorHelpEnvironment().open();
            }
            @Override protected void done() {
                importTutorial.setEnabled(true);
                try {
                    EditorHelpEnvironment.Session session = get();
                    EditorHelpSearch oldSearch = search;
                    installContent(session.pack(), session.search());
                    if (oldSearch != null && oldSearch != search) oldSearch.close();
                    openDocument(result.id(), false);
                    status.setText((result.updated() ? "Updated " : "Imported ") + result.title()
                            + " (" + result.copiedImages() + " images)");
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    status.setText("Tutorial imported; reload failed");
                    DarkDialogs.message(EditorHelpPanel.this,
                            "The tutorial was imported, but the guide could not be reloaded:\n" + cause.getMessage(),
                            "Reload failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static Path tutorialPath(Transferable value) throws Exception {
        if (value == null) return null;
        if (value.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            @SuppressWarnings("unchecked") List<File> files =
                    (List<File>) value.getTransferData(DataFlavor.javaFileListFlavor);
            return files.size() == 1 ? files.get(0).toPath() : null;
        }
        if (value.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            String text = ((String) value.getTransferData(DataFlavor.stringFlavor)).strip();
            if (text.startsWith("file:")) return Path.of(java.net.URI.create(text));
            if (!text.isBlank() && !text.contains("\n")) return Path.of(text.replaceAll("^\"|\"$", ""));
        }
        return null;
    }

    private static void bindTutorialPaste(javax.swing.JComponent component, javax.swing.Action action) {
        component.getInputMap(WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "import-tutorial-paste");
        component.getActionMap().put("import-tutorial-paste", action);
    }

    private final class TutorialDropHandler extends TransferHandler {
        @Override public int getSourceActions(javax.swing.JComponent component) {
            return component == article && article.getSelectedText() != null ? COPY : NONE;
        }

        @Override protected Transferable createTransferable(javax.swing.JComponent component) {
            if (component != article) return null;
            String selected = article.getSelectedText();
            return selected == null || selected.isEmpty() ? null : new StringSelection(selected);
        }

        @Override public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                    || support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                Path file = tutorialPath(support.getTransferable());
                if (file == null) throw new IllegalArgumentException("Drop exactly one HTML or PDF tutorial file.");
                importTutorial(file);
                return true;
            } catch (Exception exception) {
                DarkDialogs.message(EditorHelpPanel.this, exception.getMessage(),
                        "Cannot import tutorial", JOptionPane.WARNING_MESSAGE);
                return false;
            }
        }
    }

    void installContent(EditorHelpContentPack loadedPack, EditorHelpSearch loadedSearch) {
        pack = loadedPack;
        search = loadedSearch;
        documentsById.clear();
        for (EditorHelpContentPack.HelpDocument document : pack.documents()) {
            documentsById.put(document.id(), document);
        }
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(new TreeEntry("Editor Help", null));
        for (JsonNode node : pack.tableOfContents()) root.add(createTreeNode(node));
        categoryTree.setModel(new DefaultTreeModel(root));
        status.setText(pack.documents().size() + " tutorials");
        searchField.setEnabled(true);
        if (root.getChildCount() > 0) {
            categoryTree.expandPath(new TreePath(root.getPath()));
        }
        showMessage("Unreal Editor 2 Help",
                "Browse the contents on the left or search all " + pack.documents().size()
                        + " tutorials.");
    }

    private DefaultMutableTreeNode createTreeNode(JsonNode json) {
        String title = correctHelpTitle(json.path("title").asText("(untitled)"));
        String tutorialId = json.path("tutorialId").asText(null);
        DefaultMutableTreeNode treeNode =
                new DefaultMutableTreeNode(new TreeEntry(title, tutorialId));
        for (JsonNode child : json.path("children")) treeNode.add(createTreeNode(child));
        return treeNode;
    }

    private void runSearch() {
        if (search == null) return;
        String query = searchField.getText().strip();
        resultModel.clear();
        if (query.isEmpty()) {
            browseTabs.setSelectedIndex(0);
            status.setText(pack.documents().size() + " tutorials");
            return;
        }
        try {
            List<EditorHelpSearch.SearchResult> results = search.search(query, 100);
            results.forEach(resultModel::addElement);
            browseTabs.setSelectedIndex(1);
            status.setText(results.isEmpty() ? "No results found" : results.size() + " results");
        } catch (Exception e) {
            status.setText("Search failed");
            showMessage("Search failed", escapeHtml(e.getMessage()));
        }
    }

    void openDocument(String id, boolean addToHistory) {
        EditorHelpContentPack.HelpDocument document = documentsById.get(id);
        if (document == null || pack == null) return;
        if (addToHistory && currentDocumentId != null && !currentDocumentId.equals(id)) {
            backHistory.push(currentDocumentId);
            forwardHistory.clear();
        }
        try {
            Path page = document.resolveContent(pack.root());
            String preparedHtml = prepareArticleHtml(Files.readString(
                    page, java.nio.charset.StandardCharsets.UTF_8));
            HTMLDocument htmlDocument = (HTMLDocument) htmlKit.createDefaultDocument();
            htmlDocument.setBase(page.toUri().toURL());
            htmlDocument.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
            try (java.io.Reader reader = new java.io.StringReader(preparedHtml)) {
                htmlKit.read(reader, htmlDocument, 0);
            }
            article.setDocument(htmlDocument);
            scrollArticleToTop();
            currentDocumentId = id;
            source.setText(correctHelpTitle(document.title()) + "  ·  " + document.source());
            updateNavigationButtons();
        } catch (Exception e) {
            showMessage("Unable to open tutorial", escapeHtml(e.getMessage()));
        }
    }

    private void navigateBack() {
        if (backHistory.isEmpty()) return;
        if (currentDocumentId != null) forwardHistory.push(currentDocumentId);
        openDocument(backHistory.pop(), false);
        updateNavigationButtons();
    }

    private void navigateForward() {
        if (forwardHistory.isEmpty()) return;
        if (currentDocumentId != null) backHistory.push(currentDocumentId);
        openDocument(forwardHistory.pop(), false);
        updateNavigationButtons();
    }

    private void updateNavigationButtons() {
        back.setEnabled(!backHistory.isEmpty());
        forward.setEnabled(!forwardHistory.isEmpty());
    }

    private void showHome() {
        if (currentDocumentId != null) backHistory.push(currentDocumentId);
        currentDocumentId = null;
        forwardHistory.clear();
        showMessage("Unreal Editor 2 Help",
                "Browse the contents on the left or search all " + pack.documents().size()
                        + " tutorials.");
        updateNavigationButtons();
    }

    private void showMessage(String heading, String message) {
        article.setText("<html><body style='font-family:Verdana;padding:24px'><h1>"
                + escapeHtml(heading) + "</h1><p>" + message + "</p></body></html>");
        scrollArticleToTop();
        source.setText(" ");
    }

    private static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String prepareArticleHtml(String html) {
        Document document = Jsoup.parse(html);
        removeLegacySiteLabels(document);
        removeGlobalHelpArtifacts(document);
        normalizeEzkeelCharacters(document);
        stitchSplitScreenshots(document);
        unwrapLayoutLists(document);
        normalizeEditorHeaderButtons(document);
        normalizeEditorToolbarButtons(document);
        normalizeBrushActionRows(document);
        normalizeDefinitiveUnrealEdGuide(document);
        normalizeKeyMoverTutorial(document);
        normalizeCenteredTutorialImages(document);
        normalizeIconEntries(document);
        normalizeLegacyIconTables(document);
        normalizeDataTables(document);
        wrapLongCodeLines(document);
        normalizeBlackswayPageHeader(document);
        normalizePageHeaders(document);
        normalizeVocabulary(document);
        normalizeGoldabarTips(document);
        normalizeTargetedInterfaceMarkup(document);
        normalizeInterfaceIcons(document);
        normalizeBegFireImage(document);
        normalizeMillenniumFog(document);
        normalizeWolfWaterImages(document);
        normalizeBrushDefinitionImage(document);
        trimLeadingBlockWhitespace(document);
        document.select("[bgcolor], [background], [text], [link], [vlink], [alink]").forEach(element -> {
            element.removeAttr("bgcolor");
            element.removeAttr("background");
            element.removeAttr("text");
            element.removeAttr("link");
            element.removeAttr("vlink");
            element.removeAttr("alink");
        });
        document.select("font[color]").forEach(element -> element.removeAttr("color"));
        document.select("a[href]").forEach(element -> element.removeAttr("href"));
        document.select("font[size], font[face]").forEach(element -> {
            element.removeAttr("size");
            element.removeAttr("face");
        });
        document.select("p, div, li, ul, ol, blockquote, h1, h2, h3, h4, h5, h6, td, th")
                .forEach(element -> {
                    boolean imageOnly = element.text().isBlank() && !element.select("img").isEmpty();
                    if (imageOnly && !element.hasClass("help-icon")) {
                        element.addClass("help-image-only");
                        element.attr("align", "center");
                    } else {
                        element.attr("align", "left");
                    }
                });
        document.select("table").forEach(table -> table.attr("align", "left"));
        document.select("[style]").forEach(element -> {
            String style = element.attr("style")
                    .replaceAll("(?i)(?:background(?:-color)?|color)\\s*:[^;]+;?", "");
            if (style.isBlank()) element.removeAttr("style");
            else element.attr("style", style);
        });
        document.outputSettings().charset(java.nio.charset.StandardCharsets.UTF_8);
        return document.outerHtml();
    }

    static String correctHelpTitle(String title) {
        return title.replace("The Definative UnrealEd 2.0 Introduction and Guide",
                "The Definitive UnrealEd 2.0 Introduction and Guide")
                .replace("Adavnced Brushes", "Advanced Brushes")
                .replace("Creating Assult Levels by Silencer",
                        "Creating Assault Levels by Silencer");
    }

    private static void removeGlobalHelpArtifacts(Document document) {
        for (Element element : new ArrayList<>(document.getAllElements())) {
            if (element.text().replace('\u00a0', ' ').strip().equals("[^TOP]")) {
                if (element.normalName().matches("a|font|span")) element.remove();
            }
        }
    }

    private static void normalizeEzkeelCharacters(Document document) {
        Element author = document.selectFirst("meta[name=Author]");
        boolean ezkeel = author != null
                && author.attr("content").toLowerCase(java.util.Locale.ROOT).contains("ezkeel");
        ezkeel |= document.text().toLowerCase(java.util.Locale.ROOT).contains("by ezkeel");
        if (!ezkeel) return;
        List<TextNode> textNodes = new ArrayList<>();
        collectTextNodes(document, textNodes);
        for (TextNode text : textNodes) {
            text.text(text.getWholeText()
                    .replace("Â’", "'").replace("â€™", "'").replace("", "'")
                    .replace("Â“", "\"").replace("Â”", "\"")
                    .replace("â€œ", "\"").replace("â€", "\""));
        }
    }

    private static void normalizeKeyMoverTutorial(Document document) {
        if (!document.title().equalsIgnoreCase("Movers That Are Triggered By Keys")) return;

        Element firstTable = document.body().selectFirst("> table");
        if (firstTable != null) {
            Node node = firstTable.previousSibling();
            while (node != null) {
                Node previous = node.previousSibling();
                if (node instanceof TextNode text
                        && text.getWholeText().replace('\u00a0', ' ').isBlank()) {
                    node.remove();
                } else if (node instanceof Element element
                        && element.text().replace('\u00a0', ' ').isBlank()) {
                    element.remove();
                }
                node = previous;
            }
        }

        document.select("p").stream()
                .filter(paragraph -> {
                    String text = paragraph.text().replace('\u00a0', ' ').strip();
                    return text.startsWith("Editor used:") && text.contains("Download")
                            && text.contains(".zip");
                })
                .findFirst().ifPresent(Element::remove);

        document.select("p").stream()
                .filter(paragraph -> paragraph.text().contains(
                        "www.planetunreal.com/chimeric"))
                .filter(paragraph -> paragraph.text().toLowerCase(java.util.Locale.ROOT)
                        .contains("found some very interesting stuff"))
                .findFirst().ifPresent(paragraph -> {
                    String text = paragraph.text();
                    int sentence = text.indexOf("But at ");
                    paragraph.text(sentence < 0 ? text : text.substring(0, sentence).stripTrailing());
                });

        Element prompt = document.select("p").stream()
                .filter(paragraph -> paragraph.text().contains(
                        "5. Copy the following lines into the script"))
                .findFirst().orElse(null);
        if (prompt == null) return;
        Element row = prompt.closest("tr");
        if (row == null) return;
        Element codeRow = row.nextElementSibling();
        while (codeRow != null && codeRow.select("font[face*=Courier]").isEmpty()) {
            codeRow = codeRow.nextElementSibling();
        }
        if (codeRow == null) return;
        Element cell = codeRow.selectFirst("td");
        if (cell == null) return;
        List<String> lines = cell.select("p").stream()
                .map(Element::text)
                .toList();
        cell.empty().appendElement("pre").addClass("help-keymover-code")
                .text(String.join("\n", lines));
        codeRow.removeAttr("height");
    }

    private static void normalizeCenteredTutorialImages(Document document) {
        String visibleTitle = document.select(".heading, body > p:first-of-type").text().strip();
        boolean slippery = document.title().equalsIgnoreCase("Slippery Surfaces");
        boolean waterRoom = visibleTitle.toLowerCase(java.util.Locale.ROOT)
                .contains("a room that fills with water");
        if (!slippery && !waterRoom) return;
        for (Element image : new ArrayList<>(document.select("img"))) {
            Element centered = new Element("div").addClass("help-image-only")
                    .addClass("help-centered-tutorial-image")
                    .attr("align", "center")
                    .appendChild(image.clone().removeAttr("align"));
            image.replaceWith(centered);
        }
    }

    private static void removeLegacySiteLabels(Document document) {
        for (Element link : new ArrayList<>(document.select("a"))) {
            String label = link.text().replace('\u00a0', ' ').strip();
            if (!label.equalsIgnoreCase("UED Resource Lab")
                    && !label.equalsIgnoreCase("UT City")) {
                continue;
            }
            Element line = link.closest("p, h1, h2, h3, h4, h5, h6");
            if (line != null && line.text().replace('\u00a0', ' ').strip().equalsIgnoreCase(label)) {
                line.remove();
            } else {
                link.remove();
            }
        }
    }

    private static void normalizeDefinitiveUnrealEdGuide(Document document) {
        if (!document.select("h3").text()
                .contains("THE DEFINITIVE UNREALED v2.0 INTRODUCTION AND GUIDE")) {
            return;
        }
        java.util.Map<String, String> sectionTitles = java.util.Map.ofEntries(
                java.util.Map.entry("INTRODUCTION", "Introduction:"),
                java.util.Map.entry("WHO THIS TUTORIAL IS FOR:", "Who this Tutorial is for:"),
                java.util.Map.entry("FIRE IT UP:", "Fire it up:"),
                java.util.Map.entry("WHAT WE WILL BE DOING:", "What we will be doing:"),
                java.util.Map.entry("YOUR FIRST FIVE MINUTES", "Your first five minutes:"),
                java.util.Map.entry("THE FIRST ROOM EVER", "The first room ever:"),
                java.util.Map.entry("LIGHTING - NORMAL AND COLORED - PART 1",
                        "Lighting - Normal and colored - Part 1:"),
                java.util.Map.entry("REBUILDING AND PLAYING THE LEVEL",
                        "Rebuilding and playing the level:"),
                java.util.Map.entry("THE CONCEPT OF INTERSECTION AND DEINTERSECTION",
                        "The concept of Intersection and Deintersection:"),
                java.util.Map.entry("CONNECTING TWO ROOMS & COLORED LIGHTING",
                        "Connecting two rooms & Colored lighting:"));
        for (Element heading : document.select("h1, h2, h3, h4, h5, h6, p")) {
            String label = heading.text().replace('\u00a0', ' ').strip();
            String replacement = sectionTitles.get(label.toUpperCase(java.util.Locale.ROOT));
            if (replacement != null) {
                heading.tagName("p").addClass("help-guide-section")
                        .empty().appendElement("strong").text(replacement);
            }
        }

        Element playerStartImage = document.selectFirst("img[src$=unrealed12.jpg]");
        if (playerStartImage == null) return;
        Element textParagraph = playerStartImage.closest("p");
        if (textParagraph == null) return;
        Element imageParagraph = new Element("p")
                .addClass("help-image-only")
                .addClass("help-playerstart-image")
                .attr("align", "center")
                .appendChild(playerStartImage.clone().removeAttr("align"));
        textParagraph.before(imageParagraph);
        Element formerWrapper = playerStartImage.parent();
        playerStartImage.remove();
        if (formerWrapper != null && formerWrapper.text().replace('\u00a0', ' ').isBlank()
                && formerWrapper.select("img").isEmpty()) {
            formerWrapper.remove();
        }
        textParagraph.addClass("help-playerstart-text");

        normalizeGuideStep(document, "(a).", "(a). Insert Playerstart:");
        normalizeGuideStep(document, "(b).", "(b). Rebuilding the Level:");
        normalizeGuideStep(document, "(c).", "(c). Saving and playing:");
    }

    private static void normalizeGuideStep(Document document, String marker, String heading) {
        Element paragraph = document.select("p").stream()
                .filter(element -> element.text().replace('\u00a0', ' ').strip().startsWith(marker))
                .findFirst().orElse(null);
        if (paragraph == null) return;
        boolean colonConsumed = false;
        List<TextNode> orderedText = new ArrayList<>();
        collectTextNodes(paragraph, orderedText);
        for (TextNode text : orderedText) {
            String value = text.getWholeText();
            int colon = value.indexOf(':');
            if (colon >= 0) {
                text.text(value.substring(colon + 1).replaceFirst("^[\\s\\u00a0]+", ""));
                colonConsumed = true;
                break;
            } else {
                text.text("");
            }
        }
        if (!colonConsumed) return;
        paragraph.select("font > br:first-child").remove();
        paragraph.prependChild(new Element("br"));
        paragraph.prependChild(new Element("strong").addClass("help-guide-step").text(heading));
    }

    private static void collectTextNodes(Node node, List<TextNode> destination) {
        for (Node child : node.childNodes()) {
            if (child instanceof TextNode text) destination.add(text);
            else collectTextNodes(child, destination);
        }
    }

    private static void normalizeBrushActionRows(Document document) {
        String visibleTitle = document.select(".heading").text().strip();
        if (visibleTitle.equalsIgnoreCase("The Subtracted Brush")
                || visibleTitle.equalsIgnoreCase("The Mover Brush")) {
            Element actionImage = document.selectFirst(
                    "img[src$=button_subtractq.jpg], img[src$=button_moverq.jpg]");
            if (actionImage != null) {
                Element caption = actionImage.nextElementSibling();
                if (caption == null || !caption.normalName().equals("i")) {
                    caption = actionImage.parent().nextElementSibling();
                }
                if (caption != null && caption.normalName().equals("i")) {
                    Element table = new Element("table").addClass("help-brush-action")
                            .attr("border", "0").attr("cellpadding", "0")
                            .attr("cellspacing", "0").attr("align", "left");
                    Element row = table.appendElement("tr").attr("height", "32");
                    row.appendElement("td").addClass("help-brush-action-icon")
                            .attr("valign", "middle")
                            .appendChild(actionImage.clone().removeAttr("align"));
                    row.appendElement("td").addClass("help-brush-action-gap")
                            .attr("width", "4").appendText("\u00a0");
                    Element textCell = row.appendElement("td")
                            .addClass("help-brush-action-text").attr("valign", "middle");
                    Element italic = caption.clone();
                    italic.select("br").remove();
                    textCell.appendChild(italic);
                    actionImage.before(table);
                    actionImage.remove();
                    caption.remove();
                }
            }
        }

        if (visibleTitle.equalsIgnoreCase("The Semi-Solid Brush")
                || visibleTitle.equalsIgnoreCase("The Non-Solid Brush")) {
            for (Element italic : document.select("i")) {
                for (TextNode text : new ArrayList<>(italic.textNodes())) {
                    String value = text.getWholeText();
                    String sentence = "This will open a dialogue window (shown below).";
                    int end = value.indexOf(sentence);
                    if (end < 0) continue;
                    int split = end + sentence.length();
                    text.text(value.substring(0, split));
                    text.after(new TextNode(value.substring(split).replaceFirst(
                            "^[\\s\\u00a0]+", "")));
                    text.after(new Element("br"));
                    return;
                }
            }
        }
    }

    private static void normalizeEditorHeaderButtons(Document document) {
        if (!document.title().equalsIgnoreCase("Editor Tool Buttons")
                || !document.select("h1").text().contains("Editor 2.0 Toolbar Buttons")) {
            return;
        }
        document.body().addClass("help-editor-header-buttons");

        for (Element link : new ArrayList<>(document.select("a"))) {
            String label = link.text().replace('\u00a0', ' ').strip();
            if (!label.equalsIgnoreCase("unrealed.exe")
                    && !label.equalsIgnoreCase("UED Resource Lab")) {
                continue;
            }
            Element line = link.closest("p");
            if (line != null) line.remove();
        }

        document.select("h1, h2, h3, h4, h5, h6, p").stream()
                .filter(element -> element.text().replace('\u00a0', ' ').strip()
                        .matches("(?i)Functions / Modes:|Brushes:|Operations:|Viewing Modes:"))
                .forEach(element -> element.addClass("help-toolbar-section"));

        for (Element paragraph : document.select("p:has(img):has(b), p:has(img):has(strong)")) {
            paragraph.select("font").forEach(Element::unwrap);
            removeLeadingBreaks(paragraph);
        }
    }

    private static void normalizeEditorToolbarButtons(Document document) {
        if (!document.title().equalsIgnoreCase("Editor Tool Buttons")
                || !document.select("h1").text().contains("Editor 2.0 Header Buttons")) {
            return;
        }

        for (Element link : new ArrayList<>(document.select("a"))) {
            if (!link.text().replace('\u00a0', ' ').strip().equalsIgnoreCase("UED Resource Lab")) {
                continue;
            }
            Element line = link.closest("p");
            if (line != null) line.remove();
        }

        document.select("h1, h2, h3, h4, h5, h6, p").stream()
                .filter(element -> element.text().replace('\u00a0', ' ').strip()
                        .equalsIgnoreCase("Functions:"))
                .forEach(element -> element.addClass("help-toolbar-section"));

        for (Element paragraph : new ArrayList<>(
                document.select("p:has(img):has(b), p:has(img):has(strong)"))) {
            paragraph.select("font").forEach(Element::unwrap);
            removeLeadingBreaks(paragraph);
            Element heading = paragraph.selectFirst("b, strong");
            if (heading == null) continue;
            String label = heading.text().strip().replaceFirst("\\.$", "");

            if (label.matches("(?i)Search for Actors|Group Browser|Music Browser")) {
                List<Element> images = new ArrayList<>(paragraph.select("img"));
                Element insertionPoint = paragraph;
                for (int index = 1; index < images.size(); index++) {
                    Element image = images.get(index);
                    Element imageLine = new Element("p")
                            .addClass("help-toolbar-screenshot")
                            .addClass("help-image-only")
                            .attr("align", "center")
                            .appendChild(image.clone().removeAttr("align"));
                    insertionPoint.after(imageLine);
                    insertionPoint = imageLine;
                    image.remove();
                }
            } else if (label.equalsIgnoreCase("Build Options")) {
                Element description = paragraph.nextElementSibling();
                if (description != null && description.normalName().equals("p")) {
                    paragraph.appendText(" ");
                    for (Node node : new ArrayList<>(description.childNodes())) {
                        paragraph.appendChild(node.clone());
                    }
                    description.remove();
                }
            }
        }
    }

    private static void normalizeTargetedInterfaceMarkup(Document document) {
        if (!document.text().contains("This is the default mode and you will only move selected object")) return;
        for (Element heading : new ArrayList<>(document.select("b, strong"))) {
            String label = heading.text().strip().replaceFirst(":$", "");
            if (!label.matches("(?i)Deintersect|Add special brush|Hide selected actors|"
                    + "Split selected brushes|"
                    + "Cube brush|Sheet brush|Cylinder brush|Curved stair brush|"
                    + "Build Geo,? Light,? Path and Options|Built Geo,? Light,? Path and Options")) {
                continue;
            }

            Element parent = heading.parent();
            if (parent != null && parent.normalName().equals("font")) {
                Element image = followingImageAmongSiblings(heading);
                if (image != null) {
                    parent.before(heading.clone());
                    heading.remove();
                    parent.select("br").forEach(Element::remove);
                    removeBreaksUntilNextElement(parent);
                    continue;
                }
            }

            Element image = followingImageAmongSiblings(heading);
            if (image == null) continue;
            Node afterImage = image.nextSibling();
            List<Node> iconNodes = new ArrayList<>();
            Node node = heading.nextSibling();
            while (node != null) {
                Node next = node.nextSibling();
                iconNodes.add(node);
                if (node == image) break;
                node = next;
            }
            if (iconNodes.isEmpty() || iconNodes.get(iconNodes.size() - 1) != image) continue;
            Element iconContainer = new Element("font");
            heading.after(iconContainer);
            iconNodes.forEach(iconContainer::appendChild);
            iconContainer.select("br").forEach(Element::remove);
            while (afterImage instanceof Element breakElement
                    && breakElement.normalName().equals("br")) {
                Node next = afterImage.nextSibling();
                breakElement.remove();
                afterImage = next;
            }
            if (afterImage instanceof Element followingElement) {
                removeLeadingBreaks(followingElement);
            }
        }
    }

    private static void removeLeadingBreaks(Element element) {
        if (element == null) return;
        Node node = element.firstChild();
        while (node != null) {
            Node next = node.nextSibling();
            if (node instanceof TextNode text
                    && text.getWholeText().replace('\u00a0', ' ').isBlank()) {
                node.remove();
            } else if (node instanceof Element breakElement
                    && breakElement.normalName().equals("br")) {
                breakElement.remove();
            } else {
                break;
            }
            node = next;
        }
    }

    private static Element removeBreaksUntilNextElement(Element element) {
        Node node = element.nextSibling();
        while (node != null) {
            Node next = node.nextSibling();
            if (node instanceof TextNode text
                    && text.getWholeText().replace('\u00a0', ' ').isBlank()) {
                node.remove();
            } else if (node instanceof Element breakElement
                    && breakElement.normalName().equals("br")) {
                breakElement.remove();
            } else if (node instanceof Element followingElement) {
                removeLeadingBreaks(followingElement);
                return followingElement;
            } else {
                break;
            }
            node = next;
        }
        return null;
    }

    private static void normalizeGoldabarTips(Document document) {
        if (!document.text().contains("Dean \"Goldabar\" Tate")) return;
        for (TextNode text : document.body().textNodes()) {
            removeLeadingTipHyphen(text);
        }
        for (Element element : document.body().getAllElements()) {
            if (element.normalName().matches("pre|code|tt")) continue;
            for (TextNode text : element.textNodes()) removeLeadingTipHyphen(text);
        }
    }

    private static void removeLeadingTipHyphen(TextNode text) {
        text.text(text.getWholeText().replaceFirst(
                "^[\\s\\u00a0]*[-\\u2013\\u2014][\\s\\u00a0]*(?=\\p{L})", ""));
    }

    private static Element followingImageAmongSiblings(Element heading) {
        Node node = heading.nextSibling();
        int inspected = 0;
        while (node != null && inspected++ < 6) {
            if (node instanceof Element element) {
                if (element.normalName().equals("b") || element.normalName().equals("strong")) return null;
                if (element.normalName().equals("img")) return element;
                Element nested = element.selectFirst("img");
                if (nested != null) return nested;
            }
            node = node.nextSibling();
        }
        return null;
    }

    private static void normalizeVocabulary(Document document) {
        if (!document.text().contains("Unreal Editor Vocabulary")) return;
        document.select("ul, ol").forEach(list -> list.addClass("help-vocabulary-lists"));
        boolean rootAssigned = false;
        for (Element item : document.select("li")) {
            Element paragraph = item.children().stream()
                    .filter(child -> child.normalName().equals("p"))
                    .findFirst().orElse(null);
            if (paragraph == null) continue;
            Element breakElement = paragraph.selectFirst("br");
            if (!rootAssigned && paragraph.text().strip().equalsIgnoreCase("Brushes")) {
                paragraph.addClass("help-vocabulary-root");
                rootAssigned = true;
            } else if (breakElement != null) {
                Element parent = breakElement.parent();
                Element term = new Element("span").addClass("help-vocabulary-term");
                List<Node> preceding = new ArrayList<>();
                for (Node node : parent.childNodes()) {
                    if (node == breakElement) break;
                    preceding.add(node);
                }
                if (!preceding.isEmpty()) {
                    breakElement.before(term);
                    preceding.forEach(term::appendChild);
                }
            }
        }
    }

    private static void normalizeInterfaceIcons(Document document) {
        if (!document.text().contains("This is the default mode and you will only move selected object")) return;
        for (Element heading : new ArrayList<>(document.select("b, strong"))) {
            Element details = heading.nextElementSibling();
            if (details == null || !details.normalName().equals("font")) continue;
            Element image = details.selectFirst("img");
            if (image == null) continue;
            Element table = new Element("table").addClass("help-icon-entry")
                    .attr("border", "0").attr("cellpadding", "0").attr("cellspacing", "0")
                    .attr("align", "left");
            int iconHeight = parsePositiveInt(image.attr("height"), 32);
            Element row = table.appendElement("tr").attr("height", Integer.toString(iconHeight));
            row.appendElement("td")
                    .addClass("help-icon-heading")
                    .attr("height", Integer.toString(iconHeight))
                    .attr("valign", "middle")
                    .appendChild(heading.clone());
            row.appendElement("td")
                    .addClass("help-icon-gap")
                    .attr("width", "4")
                    .attr("height", Integer.toString(iconHeight))
                    .attr("valign", "middle")
                    .appendText("\u00a0");
            row.appendElement("td")
                    .addClass("help-icon")
                    .attr("height", Integer.toString(iconHeight))
                    .attr("valign", "middle")
                    .appendChild(image.clone());
            heading.before(table);
            heading.remove();
            while (image.previousSibling() instanceof Element previous
                    && previous.normalName().equals("br")) previous.remove();
            if (image.nextSibling() instanceof Element next && next.normalName().equals("br")) next.remove();
            image.remove();
            if (details.text().replace('\u00a0', ' ').isBlank() && details.children().isEmpty()) {
                details.remove();
            }
        }
    }

    private static void normalizeBrushDefinitionImage(Document document) {
        Element pageHeading = document.selectFirst(".heading");
        String visibleTitle = pageHeading == null ? "" : pageHeading.text().strip();
        String title = document.title().strip();
        if (!title.matches("(?i)The (Active|Additive|Subtracted|Mover|Semi-Solid|Non-Solid) Brush")
                && !visibleTitle.matches(
                        "(?i)The (Active|Additive|Subtracted|Mover|Semi-Solid|Non-Solid) Brush")) {
            return;
        }
        Element paragraph = document.selectFirst("p:has(img)");
        if (paragraph == null) return;
        Element image = paragraph.selectFirst("img");
        if (image == null) return;
        Element imageParagraph = new Element("p").addClass("help-image-only").attr("align", "center");
        Element rotated = image.clone().addClass("help-rotate-90");
        rotated.removeAttr("align");
        imageParagraph.appendChild(rotated);
        paragraph.before(imageParagraph);
        image.remove();
        paragraph.attr("align", "left");
    }

    private static void normalizeBegFireImage(Document document) {
        if (!document.title().equalsIgnoreCase("Fire")
                || !document.text().matches("(?s).*\\bBy:\\s*BEG\\b.*")) {
            return;
        }
        Element assumptions = document.select("h1, h2, h3, h4, h5, h6").stream()
                .filter(heading -> heading.text().strip().equalsIgnoreCase("ASSUMPTIONS:"))
                .findFirst()
                .orElse(null);
        Element image = document.select("img").last();
        if (assumptions == null || image == null) return;

        Element imageParagraph = new Element("p")
                .addClass("help-image-only")
                .attr("align", "center");
        imageParagraph.appendChild(image.clone().removeAttr("align"));
        assumptions.before(imageParagraph);
        image.remove();
    }

    private static void normalizeMillenniumFog(Document document) {
        if (!document.title().equalsIgnoreCase("Fog")
                || !document.text().toLowerCase(java.util.Locale.ROOT).contains("millennium")) {
            return;
        }
        for (Element image : document.select("img[src$=fog1.jpg], img[src$=fog2.jpg]")) {
            Node next = image.nextSibling();
            if (!(next instanceof Element breakElement
                    && breakElement.normalName().equals("br")
                    && breakElement.hasAttr("clear"))) {
                image.after(new Element("br").attr("clear", "all"));
            }
        }
        for (Element heading : document.select("h1, h2, h3, h4, h5, h6")) {
            String label = heading.text().strip();
            if (label.matches("(?i)Forward:|Abstract:|Assumptions:|Introduction:|"
                    + "Setting the scene:|The light settings:")) {
                heading.addClass("help-fog-heading");
            } else if (label.equalsIgnoreCase("Tutorial:")) {
                heading.addClass("help-fog-tutorial");
            } else if (label.equalsIgnoreCase("A Little Warning:")) {
                heading.addClass("help-fog-warning");
            }
        }
    }

    private static void normalizeWolfWaterImages(Document document) {
        if (!document.title().equalsIgnoreCase("Wolf's Tutorial Water")) return;
        Element overhangImage = document.selectFirst("img[src$=image1.jpg]");
        if (overhangImage == null) return;

        for (Element image : new ArrayList<>(document.select("img"))) {
            if (image == overhangImage) break;
            Element paragraph = image.closest("p");
            if (paragraph != null) {
                paragraph.addClass("help-image-only")
                        .addClass("help-wolf-prior-image")
                        .attr("align", "center");
                image.removeAttr("align");
                continue;
            }
            Element container = image.parent();
            if (container == null) continue;
            Element imageParagraph = new Element("p")
                    .addClass("help-image-only")
                    .addClass("help-wolf-prior-image")
                    .attr("align", "center");
            imageParagraph.appendChild(image.clone().removeAttr("align"));
            container.before(imageParagraph);
            image.remove();
            if (container.text().replace('\u00a0', ' ').isBlank()
                    && container.select("img").isEmpty()) {
                container.remove();
            }
        }

        Element sourceParagraph = overhangImage.closest("p");
        if (sourceParagraph == null) return;
        Element imageParagraph = new Element("p")
                .addClass("help-image-only")
                .addClass("help-wolf-overhang-image")
                .attr("align", "center");
        imageParagraph.appendChild(overhangImage.clone().removeAttr("align"));
        sourceParagraph.after(imageParagraph);
        overhangImage.remove();
    }

    private static void normalizeBlackswayPageHeader(Document document) {
        if (!document.text().toLowerCase(java.util.Locale.ROOT).contains("by: blacksway")) return;
        Element headingCell = document.selectFirst("td.heading:has(hr)");
        if (headingCell == null) return;
        Element row = headingCell.parent();
        if (row == null || !row.normalName().equals("tr")) return;
        List<Element> cells = row.children().stream()
                .filter(child -> child.normalName().matches("td|th"))
                .toList();
        if (cells.size() < 2) return;
        for (Element cell : new ArrayList<>(cells)) {
            if (cell == headingCell) continue;
            cell.remove();
        }
        headingCell.attr("colspan", Integer.toString(cells.size()))
                .removeAttr("width")
                .attr("align", "left");
        Element outerTable = row.closest("table");
        if (outerTable != null) {
            outerTable.removeAttr("cellpadding")
                    .removeAttr("width")
                    .attr("width", "100%")
                    .attr("align", "left");
        }
    }

    private static void normalizePageHeaders(Document document) {
        for (Element rule : document.select("hr")) {
            rule.removeAttr("size")
                    .removeAttr("noshade")
                    .attr("align", "left")
                    .attr("width", "97%");
            Element title = rule.previousElementSibling();
            while (title != null && title.text().replace('\u00a0', ' ').isBlank()
                    && title.select("img").isEmpty()) {
                Element previous = title.previousElementSibling();
                title.remove();
                title = previous;
            }
            if (title != null) {
                title.addClass("help-page-title").attr("align", "left");
            }
        }
    }

    private static void trimLeadingBlockWhitespace(Document document) {
        for (Element block : document.select(
                "p, div, li, td, th, blockquote, h1, h2, h3, h4, h5, h6")) {
            TextNode firstText = firstTextNode(block);
            if (firstText == null) continue;
            firstText.text(firstText.getWholeText().replaceFirst("^[\\s\\u00a0]+", ""));
        }
    }

    private static TextNode firstTextNode(Element element) {
        for (Node node : element.childNodes()) {
            if (node instanceof TextNode text) return text;
            if (node instanceof Element child) {
                TextNode nested = firstTextNode(child);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static void wrapLongCodeLines(Document document) {
        for (Element pre : document.select("pre")) {
            for (Element element : pre.getAllElements()) {
                for (TextNode text : element.textNodes()) {
                    text.text(wrapCodeText(text.getWholeText(), 80));
                }
            }
        }
    }

    private static String wrapCodeText(String text, int maximumColumns) {
        StringBuilder wrapped = new StringBuilder(text.length() + 32);
        String[] lines = text.split("\\R", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = shortenCodeRuler(lines[lineIndex], 64);
            while (line.length() > maximumColumns) {
                int breakAt = line.lastIndexOf(' ', maximumColumns);
                if (breakAt < Math.max(16, leadingWhitespace(line) + 8)) breakAt = maximumColumns;
                wrapped.append(line, 0, breakAt).append('\n');
                String indentation = line.substring(0, leadingWhitespace(line));
                line = indentation + line.substring(breakAt).stripLeading();
            }
            wrapped.append(line);
            if (lineIndex + 1 < lines.length) wrapped.append('\n');
        }
        return wrapped.toString();
    }

    private static String shortenCodeRuler(String line, int maximumMarks) {
        int prefixLength = leadingWhitespace(line);
        if (line.startsWith("//", prefixLength)) prefixLength += 2;
        else if (line.startsWith("#", prefixLength) || line.startsWith(";", prefixLength)) prefixLength++;
        while (prefixLength < line.length() && Character.isWhitespace(line.charAt(prefixLength))) {
            prefixLength++;
        }
        String marks = line.substring(prefixLength).stripTrailing();
        if (marks.length() <= maximumMarks || marks.isEmpty()) return line;
        char mark = marks.charAt(0);
        if (mark != '=' && mark != '-' && mark != '_' && mark != '*') return line;
        if (marks.chars().anyMatch(character -> character != mark)) return line;
        return line.substring(0, prefixLength) + String.valueOf(mark).repeat(maximumMarks);
    }

    private static int leadingWhitespace(String value) {
        int length = 0;
        while (length < value.length() && Character.isWhitespace(value.charAt(length))) length++;
        return length;
    }

    private static void unwrapLayoutLists(Document document) {
        boolean changed;
        do {
            changed = false;
            for (Element list : new ArrayList<>(document.select("ul, ol"))) {
                if (list.children().stream().noneMatch(child -> child.normalName().equals("li"))) {
                    list.unwrap();
                    changed = true;
                }
            }
        } while (changed);
    }

    private static void normalizeIconEntries(Document document) {
        for (Element container : new ArrayList<>(document.select("p, div"))) {
            List<Node> nodes = new ArrayList<>(container.childNodes());
            int imageIndex = nextMeaningfulNode(nodes, 0);
            if (imageIndex < 0 || !(nodes.get(imageIndex) instanceof Element image)
                    || !image.normalName().equals("img")) {
                continue;
            }
            int headingIndex = nextMeaningfulNode(nodes, imageIndex + 1);
            if (headingIndex < 0 || !(nodes.get(headingIndex) instanceof Element heading)
                    || !(heading.normalName().equals("b") || heading.normalName().equals("strong"))) {
                continue;
            }
            int descriptionIndex = nextMeaningfulNode(nodes, headingIndex + 1);
            if (descriptionIndex < 0) continue;

            Element table = new Element("table")
                    .addClass("help-icon-entry")
                    .attr("border", "0")
                    .attr("cellpadding", "0")
                    .attr("cellspacing", "0")
                    .attr("align", "left");
            Element headingRow = table.appendElement("tr");
            int iconWidth = parsePositiveInt(image.attr("width"), 36);
            Element normalizedHeading = heading.clone();
            removeTrailingPeriod(normalizedHeading);
            headingRow.appendElement("td")
                    .addClass("help-icon")
                    .attr("width", Integer.toString(iconWidth))
                    .attr("valign", "middle")
                    .appendChild(image.clone());
            headingRow.appendElement("td")
                    .addClass("help-icon-gap")
                    .attr("width", "4")
                    .appendText("\u00a0");
            headingRow.appendElement("td")
                    .addClass("help-icon-heading")
                    .attr("valign", "middle")
                    .appendChild(normalizedHeading);

            Element wrapper = new Element("div").addClass("help-icon-block");
            wrapper.appendChild(table);
            Element descriptionCell = wrapper.appendElement("p")
                    .addClass("help-icon-description");
            for (int index = headingIndex + 1; index < nodes.size(); index++) {
                Node node = nodes.get(index);
                if (node instanceof Element element && element.normalName().equals("br")
                        && element.hasAttr("clear")) {
                    continue;
                }
                descriptionCell.appendChild(node.clone());
            }
            container.replaceWith(wrapper);
        }
    }

    private static void removeTrailingPeriod(Element heading) {
        List<TextNode> textNodes = new ArrayList<>();
        for (Element element : heading.getAllElements()) textNodes.addAll(element.textNodes());
        for (int index = textNodes.size() - 1; index >= 0; index--) {
            TextNode text = textNodes.get(index);
            String value = text.getWholeText();
            if (value.isBlank()) continue;
            text.text(value.replaceFirst("\\.\\s*$", ""));
            return;
        }
    }

    private static void normalizeLegacyIconTables(Document document) {
        boolean unrealedButtons = document.title().equalsIgnoreCase("The Buttons of Unrealed 2");
        for (Element row : document.select("tr")) {
            if (row.closest("table.help-icon-entry") != null) continue;
            List<Element> cells = row.children().stream()
                    .filter(child -> child.normalName().equals("td") || child.normalName().equals("th"))
                    .toList();
            if (cells.size() < 2) continue;
            Element iconCell = cells.get(0);
            Element image = iconCell.selectFirst("img");
            if (image == null || !iconCell.text().replace('\u00a0', ' ').isBlank()) continue;

            Element contentCell = cells.get(1);
            Element heading = contentCell.selectFirst("b, strong");
            if (heading == null) continue;
            Element compactHeading;
            if (unrealedButtons) {
                int iconHeight = parsePositiveInt(image.attr("height"), 24);
                compactHeading = new Element("table").addClass("help-icon-entry")
                        .attr("cellpadding", "0").attr("cellspacing", "0")
                        .attr("border", "0").attr("align", "left");
                Element compactRow = compactHeading.appendElement("tbody").appendElement("tr")
                        .attr("height", Integer.toString(iconHeight));
                compactRow.appendElement("td").addClass("help-icon-heading")
                        .attr("valign", "middle").appendChild(heading.clone());
                compactRow.appendElement("td").attr("width", "4");
                compactRow.appendElement("td").addClass("help-icon")
                        .attr("valign", "middle")
                        .appendChild(image.clone().removeAttr("align"));
            } else {
                compactHeading = new Element("p").addClass("help-icon-heading");
                compactHeading.appendChild(image.clone().attr("align", "middle"));
                compactHeading.appendText("\u00a0");
                compactHeading.appendChild(heading.clone());
            }
            Element firstContent = contentCell.children().first();
            if (firstContent == null) contentCell.appendChild(compactHeading);
            else firstContent.before(compactHeading);
            heading.remove();
            iconCell.remove();
            contentCell.removeAttr("width").attr("align", "left").attr("colspan",
                    Integer.toString(cells.size()));
            row.parent().closest("table").removeAttr("width").attr("width", "100%").attr("align", "left");
        }
    }

    private static void normalizeDataTables(Document document) {
        for (Element table : document.select("table")) {
            if (table.hasClass("help-icon-entry")) continue;
            List<Element> directCells = table.select("> tbody > tr > td, > tbody > tr > th, "
                    + "> tr > td, > tr > th");
            if (directCells.size() < 4) continue;
            long borderedCells = directCells.stream()
                    .filter(cell -> cell.attr("style").toLowerCase(java.util.Locale.ROOT)
                            .contains("border"))
                    .count();
            if (borderedCells * 2 < directCells.size()) continue;

            table.addClass("help-data-table")
                    .attr("border", "1")
                    .attr("cellspacing", "0")
                    .attr("cellpadding", "4");
            directCells.forEach(cell -> cell.addClass("help-data-cell")
                    .attr("style", "border: 1px solid #3a4352;"));
        }
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int nextMeaningfulNode(List<Node> nodes, int start) {
        for (int index = start; index < nodes.size(); index++) {
            Node node = nodes.get(index);
            if (node instanceof TextNode text
                    && text.getWholeText().replace('\u00a0', ' ').isBlank()) {
                continue;
            }
            return index;
        }
        return -1;
    }

    private static void stitchSplitScreenshots(Document document) {
        for (org.jsoup.nodes.Element row : document.select("tr")) {
            List<org.jsoup.nodes.Element> images = row.select("img").stream().toList();
            for (org.jsoup.nodes.Element left : images) {
                String source = left.attr("src");
                if (!source.toLowerCase(java.util.Locale.ROOT).matches(".*-left\\.(?:jpg|jpeg|gif|png)$")) {
                    continue;
                }
                String expectedRight = source.replaceFirst(
                        "(?i)-left(\\.(?:jpg|jpeg|gif|png))$", "-right$1");
                org.jsoup.nodes.Element right = images.stream()
                        .filter(image -> image.attr("src").equalsIgnoreCase(expectedRight))
                        .findFirst().orElse(null);
                if (right == null) continue;
                org.jsoup.nodes.Element imageRow = new org.jsoup.nodes.Element("tr");
                org.jsoup.nodes.Element imageCell = new org.jsoup.nodes.Element("td")
                        .attr("colspan", Integer.toString(Math.max(1, row.childrenSize())))
                        .attr("align", "center");
                org.jsoup.nodes.Element leftCopy = left.clone();
                org.jsoup.nodes.Element rightCopy = right.clone();
                leftCopy.removeAttr("align").removeAttr("hspace");
                rightCopy.removeAttr("align").removeAttr("hspace");
                imageCell.appendChild(leftCopy);
                imageCell.appendChild(rightCopy);
                imageRow.appendChild(imageCell);
                row.before(imageRow);
                left.remove();
                right.remove();
                break;
            }
        }
    }

    private static final class HelpHtmlEditorKit extends HTMLEditorKit {
        private final ViewFactory factory = new HTMLFactory() {
            @Override public View create(javax.swing.text.Element element) {
                Object name = element.getAttributes().getAttribute(
                        javax.swing.text.StyleConstants.NameAttribute);
                Object cssClass = element.getAttributes().getAttribute(HTML.Attribute.CLASS);
                if (name == HTML.Tag.IMG && cssClass != null
                        && cssClass.toString().contains("help-rotate-90")) {
                    return new RotatedImageView(element);
                }
                if (name == HTML.Tag.IMG && cssClass != null
                        && cssClass.toString().contains("help-inline-icon")) {
                    return new VerticallyCenteredImageView(element);
                }
                if (name == HTML.Tag.IMG && cssClass != null
                        && cssClass.toString().contains("help-pdf-import-image")) {
                    return new PdfResponsiveImageView(element);
                }
                if (name == HTML.Tag.IMG) return new ResponsiveImageView(element);
                return super.create(element);
            }
        };

        @Override public ViewFactory getViewFactory() {
            return factory;
        }
    }

    private static final class RotatedImageView extends ImageView {
        RotatedImageView(javax.swing.text.Element element) {
            super(element);
        }

        @Override public float getPreferredSpan(int axis) {
            return super.getPreferredSpan(axis == View.X_AXIS ? View.Y_AXIS : View.X_AXIS);
        }

        @Override public void paint(Graphics graphics, Shape allocation) {
            Rectangle bounds = allocation.getBounds();
            Graphics2D rotated = (Graphics2D) graphics.create();
            rotated.translate(bounds.getCenterX(), bounds.getCenterY());
            rotated.rotate(Math.PI / 2);
            super.paint(rotated, new Rectangle(
                    -bounds.height / 2, -bounds.width / 2, bounds.height, bounds.width));
            rotated.dispose();
        }
    }

    private static class ResponsiveImageView extends ImageView {
        ResponsiveImageView(javax.swing.text.Element element) {
            super(element);
        }

        protected int horizontalReserve() {
            // Keeps large images below the article/table width so they cannot widen their parent view.
            return 150;
        }

        private float scale() {
            float naturalWidth = super.getPreferredSpan(View.X_AXIS);
            Component container = getContainer();
            if (naturalWidth <= 0 || container == null || container.getWidth() <= 0) return 1f;
            int availableWidth = Math.max(1, container.getWidth() - horizontalReserve());
            return Math.min(1f, availableWidth / naturalWidth);
        }

        @Override public float getPreferredSpan(int axis) {
            return super.getPreferredSpan(axis) * scale();
        }

        @Override public void paint(Graphics graphics, Shape allocation) {
            float scale = scale();
            Image image = getImage();
            if (scale >= 0.999f || image == null) {
                super.paint(graphics, allocation);
                return;
            }
            Rectangle bounds = allocation.getBounds();
            graphics.drawImage(image, bounds.x, bounds.y, bounds.width, bounds.height, getContainer());
        }
    }

    private static final class PdfResponsiveImageView extends ResponsiveImageView {
        PdfResponsiveImageView(javax.swing.text.Element element) { super(element); }

        @Override protected int horizontalReserve() {
            // Body/table/paragraph margins, borders and the vertical scrollbar.
            return 170;
        }
    }

    private static final class VerticallyCenteredImageView extends ResponsiveImageView {
        VerticallyCenteredImageView(javax.swing.text.Element element) {
            super(element);
        }

        @Override public float getAlignment(int axis) {
            return axis == View.Y_AXIS ? 0.5f : super.getAlignment(axis);
        }
    }

    private void scrollArticleToTop() {
        article.setCaretPosition(0);
        articleScroll.getVerticalScrollBar().setValue(0);
        SwingUtilities.invokeLater(() -> {
            article.setCaretPosition(0);
            article.scrollRectToVisible(new Rectangle(0, 0, 1, 1));
            articleScroll.getVerticalScrollBar().setValue(0);
        });
    }

    int resultCountForTest() { return resultModel.size(); }
    String currentDocumentIdForTest() { return currentDocumentId; }
    JTextField searchFieldForTest() { return searchField; }
    JTree categoryTreeForTest() { return categoryTree; }
    JTabbedPane browseTabsForTest() { return browseTabs; }
    JEditorPane articleForTest() { return article; }

    private record TreeEntry(String title, String documentId) {
        @Override public String toString() { return title; }
    }

    private static final class HelpTreeRenderer extends DefaultTreeCellRenderer {
        @Override public Component getTreeCellRendererComponent(
                JTree tree, Object value, boolean selected, boolean expanded,
                boolean leaf, int row, boolean hasFocus) {
            JLabel label = (JLabel) super.getTreeCellRendererComponent(
                    tree, value, selected, expanded, leaf, row, hasFocus);
            label.setBackground(selected ? AssistantTheme.ACCENT_DARK : AssistantTheme.PANEL);
            label.setForeground(AssistantTheme.TEXT);
            label.setOpaque(true);
            return label;
        }
    }

    private static final class SearchResultRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean hasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, hasFocus);
            if (value instanceof EditorHelpSearch.SearchResult result) {
                label.setText("<html><b>" + escapeHtml(correctHelpTitle(result.title())) + "</b><br>"
                        + "<span style='color:#9ca7b8'>" + escapeHtml(result.categoryPath())
                        + "</span><br>" + escapeHtml(result.excerpt()) + "</html>");
                label.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
            }
            label.setBackground(selected ? AssistantTheme.ACCENT_DARK : AssistantTheme.PANEL);
            label.setForeground(AssistantTheme.TEXT);
            label.setOpaque(true);
            return label;
        }
    }

    @FunctionalInterface
    private interface ChangeAction { void run(); }

    private record SimpleDocumentListener(ChangeAction action)
            implements javax.swing.event.DocumentListener {
        @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
        @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { action.run(); }
    }
}
