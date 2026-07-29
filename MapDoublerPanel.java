import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.BoxLayout;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Applies the team-specific property changes required after duplicating a map
 * section in Unreal Editor 2. Every edit is scoped to its owning Actor block.
 */
public final class MapDoublerPanel extends JPanel {
    private static final Pattern BEGIN_ACTOR =
            Pattern.compile("^\\s*Begin\\s+Actor\\b.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACTOR_CLASS =
            Pattern.compile("\\bClass=([^\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern END_ACTOR =
            Pattern.compile("^\\s*End\\s+Actor\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEAM_NUMBER =
            Pattern.compile("^\\s*TeamNumber\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEAM =
            Pattern.compile("^\\s*Team\\s*=", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEAM_ONE =
            Pattern.compile("^\\s*Team\\s*=\\s*1(?:\\s*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EVENT_OR_TAG =
            Pattern.compile("^(\\s*)(Event|Tag)(\\s*=\\s*)(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RED_TOKEN =
            Pattern.compile("red", Pattern.CASE_INSENSITIVE);

    private final JTextArea inputArea = codeArea();
    private final JTextArea outputArea = codeArea();
    private final JTextPane logArea = new JTextPane();
    private final JLabel status = new JLabel("Paste your map code here, then press Analyze and Double map.");
    private String inputSearchText = "";
    private JDialog inputSearchDialog;
    private JTextField inputSearchField;

    public MapDoublerPanel() {
        super(new BorderLayout(8, 8));
        setBackground(AssistantTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        installInputSearch();
        add(createControls(), BorderLayout.NORTH);
        add(createWorkspace(), BorderLayout.CENTER);
    }

    private void installInputSearch() {
        inputArea.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("control F"), "findInputCode");
        inputArea.getActionMap().put("findInputCode", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                showInputSearchDialog();
            }
        });

        inputArea.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("F3"), "findNextInputCode");
        inputArea.getActionMap().put("findNextInputCode", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                if (inputSearchText.isEmpty()) {
                    inputArea.getActionMap().get("findInputCode").actionPerformed(event);
                } else {
                    findInputMatch(true);
                }
            }
        });

        inputArea.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("shift F3"), "findPreviousInputCode");
        inputArea.getActionMap().put("findPreviousInputCode", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                if (inputSearchText.isEmpty()) {
                    inputArea.getActionMap().get("findInputCode").actionPerformed(event);
                } else {
                    findInputMatch(false);
                }
            }
        });
    }

    private void showInputSearchDialog() {
        if (inputSearchDialog == null) createInputSearchDialog();

        String selectedText = inputArea.getSelectedText();
        if (selectedText != null && !selectedText.isBlank()) {
            inputSearchField.setText(selectedText);
        } else if (inputSearchField.getText().isEmpty()) {
            inputSearchField.setText(inputSearchText);
        }
        inputSearchDialog.setLocationRelativeTo(this);
        inputSearchDialog.setVisible(true);
        inputSearchDialog.toFront();
        inputSearchField.requestFocusInWindow();
        inputSearchField.selectAll();
    }

    private void createInputSearchDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        inputSearchDialog = new JDialog(owner, "Find in Input Code", Dialog.ModalityType.MODELESS);
        inputSearchDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);

        inputSearchField = new JTextField(28);
        inputSearchField.addActionListener(event -> searchFromDialog(true));

        JButton previous = new JButton("Previous");
        previous.addActionListener(event -> searchFromDialog(false));
        JButton next = new JButton("Next");
        next.addActionListener(event -> searchFromDialog(true));
        JButton close = new JButton("Close");
        close.addActionListener(event -> inputSearchDialog.setVisible(false));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(inputSearchField, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(previous);
        buttons.add(next);
        buttons.add(close);
        content.add(buttons, BorderLayout.SOUTH);
        inputSearchDialog.setContentPane(content);
        inputSearchDialog.pack();
    }

    private void searchFromDialog(boolean forward) {
        inputSearchText = inputSearchField.getText();
        if (inputSearchText.isEmpty()) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        findInputMatch(forward);
        inputSearchField.requestFocusInWindow();
        SwingUtilities.invokeLater(() -> inputArea.getCaret().setSelectionVisible(true));
    }

    private void findInputMatch(boolean forward) {
        String content = inputArea.getText();
        String haystack = content.toLowerCase(Locale.ROOT);
        String needle = inputSearchText.toLowerCase(Locale.ROOT);
        int match;
        boolean wrapped = false;

        if (forward) {
            int start = Math.max(inputArea.getSelectionEnd(), inputArea.getCaretPosition());
            match = haystack.indexOf(needle, start);
            if (match < 0 && start > 0) {
                match = haystack.indexOf(needle);
                wrapped = match >= 0;
            }
        } else {
            int start = Math.min(inputArea.getSelectionStart(), inputArea.getCaretPosition()) - 1;
            match = start >= 0 ? haystack.lastIndexOf(needle, start) : -1;
            if (match < 0 && start < haystack.length() - 1) {
                match = haystack.lastIndexOf(needle);
                wrapped = match >= 0;
            }
        }

        if (match < 0) {
            status.setForeground(new Color(225, 105, 105));
            status.setText("No match found for \"" + inputSearchText + "\".");
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        inputArea.requestFocusInWindow();
        inputArea.select(match, match + inputSearchText.length());
        status.setForeground(AssistantTheme.MUTED);
        status.setText((wrapped ? "Search wrapped. " : "")
                + "Found \"" + content.substring(match, match + inputSearchText.length()) + "\".");
    }

    private JPanel createControls() {
        JPanel controls = new JPanel(new BorderLayout(12, 0));
        controls.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(button("Analyze", event -> analyze(false)));
        actions.add(button("Double map!", event -> analyze(true)));
        actions.add(button("Copy result", this::copyResult));
        actions.add(button("Reset", this::reset));
        controls.add(actions, BorderLayout.WEST);

        JPanel messages = new JPanel();
        messages.setOpaque(false);
        messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
        JLabel note = new JLabel("All Events and Tags in the map must contain the word 'red'.");
        note.setForeground(new Color(235, 184, 80));
        messages.add(note);
        status.setForeground(AssistantTheme.MUTED);
        messages.add(status);
        controls.add(messages, BorderLayout.CENTER);
        return controls;
    }

    private JPanel createWorkspace() {
        outputArea.setEditable(true);
        TextSearchSupport.install(outputArea, this, "Doubled Result");
        logArea.setEditable(false);
        logArea.setBackground(AssistantTheme.CODE_BACKGROUND);
        JSplitPane codeSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titledScroll("Input Code:", inputArea),
                titledScroll("Doubled result:", outputArea));
        codeSplit.setResizeWeight(0.5);
        styleSplitPane(codeSplit);

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(AssistantTheme.titled("Log"));
        logScroll.setPreferredSize(new Dimension(900, 175));

        JSplitPane workspaceSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codeSplit, logScroll);
        workspaceSplit.setResizeWeight(0.76);
        workspaceSplit.setContinuousLayout(true);
        styleSplitPane(workspaceSplit);

        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setOpaque(false);
        workspace.add(workspaceSplit, BorderLayout.CENTER);
        return workspace;
    }

    private static void styleSplitPane(JSplitPane splitPane) {
        AssistantTheme.styleSplitPane(splitPane);
    }

    private JButton button(String text, java.util.function.Consumer<ActionEvent> action) {
        JButton button = new JButton(text);
        button.addActionListener(action::accept);
        return button;
    }

    private JScrollPane titledScroll(String title, JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(AssistantTheme.titled(title));
        return scroll;
    }

    private static JTextArea codeArea() {
        JTextArea area = new JTextArea();
        area.setFont(new Font("Verdana", Font.PLAIN, 12));
        area.setLineWrap(false);
        area.setBackground(AssistantTheme.CODE_BACKGROUND);
        return area;
    }

    private void analyze(boolean writeOutput) {
        String input = inputArea.getText();
        if (input.isBlank()) {
            status.setForeground(new Color(225, 105, 105));
            status.setText("Paste map code first.");
            return;
        }

        TransformResult result = transform(input);
        writeLog(result.changes(), writeOutput);
        if (writeOutput) outputArea.setText(result.output());
        status.setForeground(result.changes().isEmpty()
                ? AssistantTheme.MUTED : new Color(94, 205, 130));
        int count = result.changes().size();
        String changeWord = count == 1 ? " change." : " changes.";
        status.setText(result.changes().isEmpty()
                ? "No applicable changes found."
                : (writeOutput ? "Applied " : "Analysis found ") + count + changeWord);
    }

    static TransformResult transform(String input) {
        String separator = input.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = input.split("\\R", -1);
        List<String> output = new ArrayList<>();
        List<Change> changes = new ArrayList<>();

        for (int index = 0; index < lines.length;) {
            if (!BEGIN_ACTOR.matcher(lines[index]).matches()) {
                output.add(lines[index++]);
                continue;
            }

            int end = findActorEnd(lines, index + 1);
            if (end < 0) {
                while (index < lines.length) output.add(lines[index++]);
                break;
            }
            List<String> actor = new ArrayList<>();
            for (int line = index; line <= end; line++) actor.add(lines[line]);
            transformActor(actor, changes);
            transformEventsAndTags(actor, changes);
            output.addAll(actor);
            index = end + 1;
        }
        return new TransformResult(String.join(separator, output), List.copyOf(changes));
    }

    private static void transformEventsAndTags(List<String> actor, List<Change> changes) {
        String actorName = readActorName(actor.get(0));
        String actorClass = readActorClass(actor.get(0));
        for (int line = 1; line < actor.size() - 1; line++) {
            String before = actor.get(line);
            Matcher property = EVENT_OR_TAG.matcher(before);
            if (!property.matches()) continue;

            String value = property.group(4);
            String transformedValue = RED_TOKEN.matcher(value).replaceAll("blue");
            if (value.equals(transformedValue)) continue;

            String after = property.group(1) + property.group(2) + property.group(3) + transformedValue;
            actor.set(line, after);
            changes.add(new Change(actorClass, actorName,
                    property.group(2) + ": red changed to blue", before, after));
        }
    }

    private static int findActorEnd(String[] lines, int start) {
        for (int index = start; index < lines.length; index++) {
            if (END_ACTOR.matcher(lines[index]).matches()) return index;
        }
        return -1;
    }

    private static void transformActor(List<String> actor, List<Change> changes) {
        String actorClass = readActorClass(actor.get(0));
        if (actorClass.isEmpty()) return;
        String actorName = readActorName(actor.get(0));

        if (actorClass.equalsIgnoreCase("PlayerStart")) {
            setOrInsert(actor, TEAM_NUMBER, "TeamNumber", "1", actorClass, actorName, changes);
        } else if (actorClass.equalsIgnoreCase("FlagBase")) {
            invertFlagTeam(actor, actorClass, actorName, changes);
        }
    }

    private static void setOrInsert(List<String> actor, Pattern propertyPattern,
                                    String key, String value, String actorClass, String actorName,
                                    List<Change> changes) {
        for (int line = 1; line < actor.size() - 1; line++) {
            if (!propertyPattern.matcher(actor.get(line)).find()) continue;
            String before = actor.get(line);
            String after = replaceAssignment(before, key, value);
            if (!before.equals(after)) {
                actor.set(line, after);
                changes.add(new Change(actorClass, actorName, key + " set to " + value, before, after));
            }
            return;
        }
        String inserted = propertyIndent(actor) + key + "=" + value;
        actor.add(1, inserted);
        changes.add(new Change(actorClass, actorName,
                key + " inserted as " + value, "<missing>", inserted));
    }

    private static void invertFlagTeam(List<String> actor, String actorClass,
                                       String actorName, List<Change> changes) {
        for (int line = 1; line < actor.size() - 1; line++) {
            String before = actor.get(line);
            if (!TEAM.matcher(before).find()) continue;
            if (TEAM_ONE.matcher(before).matches()) {
                actor.set(line, "");
                changes.add(new Change(actorClass, actorName,
                        "Team=1 removed for the doubled FlagBase", before, "<empty line>"));
            } else {
                String after = replaceAssignment(before, "Team", "1");
                actor.set(line, after);
                changes.add(new Change(actorClass, actorName, "Team changed to 1", before, after));
            }
            return;
        }
        String inserted = propertyIndent(actor) + "Team=1";
        actor.add(1, inserted);
        changes.add(new Change(actorClass, actorName,
                "Team=1 inserted for the doubled FlagBase", "<missing>", inserted));
    }

    private static String replaceAssignment(String line, String key, String value) {
        Pattern assignment = Pattern.compile("(?i)(" + Pattern.quote(key) + "\\s*=\\s*)[^\\s,\\)]+");
        Matcher matcher = assignment.matcher(line);
        if (!matcher.find()) return line;
        return line.substring(0, matcher.start()) + matcher.group(1) + value
                + line.substring(matcher.end());
    }

    private static String propertyIndent(List<String> actor) {
        for (int line = 1; line < actor.size() - 1; line++) {
            Matcher matcher = Pattern.compile("^(\\s+)\\S").matcher(actor.get(line));
            if (matcher.find()) return matcher.group(1);
        }
        return "     ";
    }

    private static String readActorName(String beginLine) {
        Matcher matcher = Pattern.compile("\\bName=([^\\s]+)", Pattern.CASE_INSENSITIVE).matcher(beginLine);
        return matcher.find() ? matcher.group(1) : "Unnamed actor";
    }

    private static String readActorClass(String beginLine) {
        Matcher matcher = ACTOR_CLASS.matcher(beginLine);
        return matcher.find() ? matcher.group(1) : "";
    }

    private void writeLog(List<Change> changes, boolean applied) {
        logArea.setText("");
        StyledDocument document = logArea.getStyledDocument();
        SimpleAttributeSet normal = new SimpleAttributeSet();
        StyleConstants.setForeground(normal, AssistantTheme.TEXT);
        StyleConstants.setFontFamily(normal, "Verdana");
        StyleConstants.setFontSize(normal, 12);
        SimpleAttributeSet heading = new SimpleAttributeSet(normal);
        StyleConstants.setForeground(heading, applied
                ? new Color(94, 205, 130) : new Color(235, 166, 65));
        StyleConstants.setBold(heading, true);
        StyleConstants.setFontSize(heading, 15);
        SimpleAttributeSet categoryHeading = new SimpleAttributeSet(normal);
        StyleConstants.setForeground(categoryHeading, AssistantTheme.MUTED);
        StyleConstants.setBold(categoryHeading, true);
        StyleConstants.setFontSize(categoryHeading, 13);
        if (changes.isEmpty()) {
            appendLog(document, "No PlayerStart, FlagBase, or red-to-blue changes were required.\n", normal);
        } else {
            appendLog(document, applied ? "Applied changes:\n" : "Changes that will be made:\n", heading);
            List<Change> sortedChanges = new ArrayList<>(changes);
            sortedChanges.sort(Comparator
                    .comparingInt((Change change) -> logCategoryPriority(change.actorClass()))
                    .thenComparing(Change::actorClass, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Change::actor, String.CASE_INSENSITIVE_ORDER));
            Map<ActorKey, List<Change>> byActor = new LinkedHashMap<>();
            for (Change change : sortedChanges) {
                ActorKey key = new ActorKey(change.actorClass(), change.actor());
                byActor.computeIfAbsent(key, ignored -> new ArrayList<>()).add(change);
            }
            String currentCategory = "";
            for (Map.Entry<ActorKey, List<Change>> actor : byActor.entrySet()) {
                String category = logCategory(actor.getKey().actorClass());
                if (!category.equals(currentCategory)) {
                    appendLog(document, "\n" + category + "\n", categoryHeading);
                    currentCategory = category;
                }
                appendLog(document, "\n" + actor.getKey().name() + ":\n", normal);
                for (Change change : actor.getValue()) {
                    appendLog(document, "  " + change.before().strip()
                            + " -> " + change.after().strip() + "\n", normal);
                }
            }
        }
        logArea.setCaretPosition(0);
    }

    private static int logCategoryPriority(String actorClass) {
        if (actorClass.equalsIgnoreCase("FlagBase")
                || actorClass.equalsIgnoreCase("PlayerStart")) return 0;
        if (actorClass.equalsIgnoreCase("Mover")) return 1;
        if (actorClass.equalsIgnoreCase("Trigger")) return 2;
        if (actorClass.equalsIgnoreCase("SpecialEvent")) return 3;
        return 4;
    }

    private static String logCategory(String actorClass) {
        if (actorClass.equalsIgnoreCase("FlagBase")
                || actorClass.equalsIgnoreCase("PlayerStart")) return "Flags & PlayerStarts:";
        if (actorClass.equalsIgnoreCase("Mover")) return "Movers:";
        if (actorClass.equalsIgnoreCase("Trigger")) return "Triggers:";
        if (actorClass.equalsIgnoreCase("SpecialEvent")) return "SpecialEvents:";
        return actorClass.isBlank() ? "Other Actors:" : actorClass + ":";
    }

    private static void appendLog(StyledDocument document, String text, SimpleAttributeSet style) {
        try {
            document.insertString(document.getLength(), text, style);
        } catch (javax.swing.text.BadLocationException exception) {
            throw new IllegalStateException("Could not update log.", exception);
        }
    }

    private void copyResult(ActionEvent ignored) {
        if (outputArea.getText().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Press Double before copying the result.",
                    "No result", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(outputArea.getText()), null);
        status.setForeground(new Color(94, 205, 130));
        status.setText("Result copied to clipboard.");
    }

    private void reset(ActionEvent ignored) {
        inputArea.setText("");
        outputArea.setText("");
        logArea.setText("");
        status.setForeground(AssistantTheme.MUTED);
        status.setText("Paste the your map code here, then press Analyze or Double.");
    }

    record Change(String actorClass, String actor, String description, String before, String after) { }
    private record ActorKey(String actorClass, String name) { }
    record TransformResult(String output, List<Change> changes) { }
}
