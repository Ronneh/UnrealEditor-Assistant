import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
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
    private static final Pattern RED_TOKEN =
            Pattern.compile("red", Pattern.CASE_INSENSITIVE);

    private final JTextArea inputArea = codeArea();
    private final JTextArea outputArea = codeArea();
    private final JTextPane logArea = new JTextPane();
    private final JLabel status = new JLabel("Paste your map code here, then press Analyze and Double.");

    public MapDoublerPanel() {
        super(new BorderLayout(8, 8));
        setBackground(AssistantTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(createControls(), BorderLayout.NORTH);
        add(createWorkspace(), BorderLayout.CENTER);
    }

    private JPanel createControls() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        controls.add(button("Analyze", event -> analyze(false)));
        controls.add(button("Double", event -> analyze(true)));
        controls.add(button("Copy result", this::copyResult));
        controls.add(button("Reset", this::reset));
        JLabel note = new JLabel("All Events and Tags in the map must contain the word 'red'.");
        note.setForeground(new Color(235, 184, 80));
        controls.add(note);
        status.setForeground(AssistantTheme.MUTED);
        controls.add(status);
        return controls;
    }

    private JPanel createWorkspace() {
        outputArea.setEditable(true);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JSplitPane codeSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titledScroll("Input Code:", inputArea),
                titledScroll("Doubled result:", outputArea));
        codeSplit.setResizeWeight(0.5);

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(AssistantTheme.titled("Log"));
        logScroll.setPreferredSize(new Dimension(900, 175));

        JSplitPane workspaceSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codeSplit, logScroll);
        workspaceSplit.setResizeWeight(0.76);
        workspaceSplit.setContinuousLayout(true);
        workspaceSplit.setBorder(BorderFactory.createEmptyBorder());

        JPanel workspace = new JPanel(new BorderLayout());
        workspace.setOpaque(false);
        workspace.add(workspaceSplit, BorderLayout.CENTER);
        return workspace;
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
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setLineWrap(false);
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
            output.addAll(actor);
            index = end + 1;
        }
        for (int line = 0; line < output.size(); line++) {
            String before = output.get(line);
            String after = RED_TOKEN.matcher(before).replaceAll("blue");
            if (!before.equals(after)) {
                output.set(line, after);
                changes.add(new Change("Events & Tags", "red changed to blue", before, after));
            }
        }
        return new TransformResult(String.join(separator, output), List.copyOf(changes));
    }

    private static int findActorEnd(String[] lines, int start) {
        for (int index = start; index < lines.length; index++) {
            if (END_ACTOR.matcher(lines[index]).matches()) return index;
        }
        return -1;
    }

    private static void transformActor(List<String> actor, List<Change> changes) {
        Matcher classMatcher = ACTOR_CLASS.matcher(actor.get(0));
        if (!classMatcher.find()) return;
        String actorClass = classMatcher.group(1);
        String actorName = readActorName(actor.get(0));

        if (actorClass.equalsIgnoreCase("PlayerStart")) {
            setOrInsert(actor, TEAM_NUMBER, "TeamNumber", "1", actorName, changes);
        } else if (actorClass.equalsIgnoreCase("FlagBase")) {
            invertFlagTeam(actor, actorName, changes);
        }
    }

    private static void setOrInsert(List<String> actor, Pattern propertyPattern,
                                    String key, String value, String actorName,
                                    List<Change> changes) {
        for (int line = 1; line < actor.size() - 1; line++) {
            if (!propertyPattern.matcher(actor.get(line)).find()) continue;
            String before = actor.get(line);
            String after = replaceAssignment(before, key, value);
            if (!before.equals(after)) {
                actor.set(line, after);
                changes.add(new Change(actorName, key + " set to " + value, before, after));
            }
            return;
        }
        String inserted = propertyIndent(actor) + key + "=" + value;
        actor.add(1, inserted);
        changes.add(new Change(actorName, key + " inserted as " + value, "<missing>", inserted));
    }

    private static void invertFlagTeam(List<String> actor, String actorName, List<Change> changes) {
        for (int line = 1; line < actor.size() - 1; line++) {
            String before = actor.get(line);
            if (!TEAM.matcher(before).find()) continue;
            if (TEAM_ONE.matcher(before).matches()) {
                actor.set(line, "");
                changes.add(new Change(actorName, "Team=1 removed for the doubled FlagBase", before, "<empty line>"));
            } else {
                String after = replaceAssignment(before, "Team", "1");
                actor.set(line, after);
                changes.add(new Change(actorName, "Team changed to 1", before, after));
            }
            return;
        }
        String inserted = propertyIndent(actor) + "Team=1";
        actor.add(1, inserted);
        changes.add(new Change(actorName, "Team=1 inserted for the doubled FlagBase", "<missing>", inserted));
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

    private void writeLog(List<Change> changes, boolean applied) {
        logArea.setText("");
        StyledDocument document = logArea.getStyledDocument();
        SimpleAttributeSet normal = new SimpleAttributeSet();
        StyleConstants.setForeground(normal, AssistantTheme.TEXT);
        StyleConstants.setFontFamily(normal, Font.MONOSPACED);
        StyleConstants.setFontSize(normal, 12);
        SimpleAttributeSet heading = new SimpleAttributeSet(normal);
        StyleConstants.setForeground(heading, applied
                ? new Color(94, 205, 130) : new Color(235, 166, 65));
        StyleConstants.setBold(heading, true);
        StyleConstants.setFontSize(heading, 15);
        if (changes.isEmpty()) {
            appendLog(document, "No PlayerStart, FlagBase, or red-to-blue changes were required.\n", normal);
        } else {
            appendLog(document, applied ? "Applied changes:\n" : "Changes that will be made:\n", heading);
            Map<String, List<Change>> byActor = new LinkedHashMap<>();
            for (Change change : changes) {
                byActor.computeIfAbsent(change.actor(), ignored -> new ArrayList<>()).add(change);
            }
            for (Map.Entry<String, List<Change>> actor : byActor.entrySet()) {
                appendLog(document, "\n" + actor.getKey() + ":\n", normal);
                for (Change change : actor.getValue()) {
                    appendLog(document, "  " + change.before().strip()
                            + " -> " + change.after().strip() + "\n", normal);
                }
            }
        }
        logArea.setCaretPosition(0);
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

    record Change(String actor, String description, String before, String after) { }
    record TransformResult(String output, List<Change> changes) { }
}
