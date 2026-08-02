import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Clipboard;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Finds brush vertices that are off a chosen Unreal grid and, after explicit
 * confirmation, snaps only those coordinates to their nearest grid point.
 */
public final class BrushOptimizer {

    private static final double GRID_EPSILON = 1.0e-9;
    /** Unreal commonly serializes exact grid values with a tiny floating-point error. */
    private static final double GRID_NOISE_TOLERANCE = 0.01;
    private static final double MIDPOINT_TOLERANCE = 0.25;
    private static final int MAX_CURVE_GRID_MOVES = 4;
    private static final Pattern BEGIN_BRUSH_ACTOR = Pattern.compile(
            "^\\s*Begin\\s+Actor\\s+Class=Brush\\s+Name=([^\\s]+).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern END_ACTOR = Pattern.compile("^\\s*End\\s+Actor\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern VERTEX_LINE = Pattern.compile("^\\s*Vertex\\s+", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile(
            "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");

    private final JTextArea inputArea = createCodeArea();
    private final JTextArea outputArea = createCodeArea();
    private final JComboBox<Integer> gridStepBox =
            new JComboBox<>(new Integer[] { 2, 4, 8, 16, 32, 64, 128, 256 });
    private final JComboBox<Integer> minMoveBox =
            new JComboBox<>(new Integer[] { 0, 2, 4, 8, 16, 32, 64, 128, 256 });
    private final JComboBox<Integer> maxMoveBox =
            new JComboBox<>(new Integer[] { 2, 4, 8, 16, 32, 64, 128, 256 });
    private final JComboBox<Integer> fontSizeBox =
            new JComboBox<>(new Integer[] { 8, 9, 10, 11, 12, 13, 14, 16, 18, 20, 24, 28, 32, 36, 40 });
    private final JCheckBox preserveCurveLines = new JCheckBox("Curved Brush", false);
    private final JLabel statusLabel = new JLabel(" ");
    private final JTextPane logPane = new JTextPane();
    private final BrushPreviewPanel preview = new BrushPreviewPanel();

    private String analyzedMap = "";
    private List<BrushIssue> issues = List.of();
    private boolean optimizationRunning;
    private int optimizationGeneration;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AssistantTheme.install();
            new BrushOptimizer().show();
        });
    }

    private void show() {
        JFrame frame = new JFrame("UT99 Brush Optimizer by VRN|Ron");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(createContent());
        frame.setMinimumSize(new Dimension(1000, 700));
        frame.pack();
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
    }

    /**
     * Creates the optimizer view so it can be hosted by the UE2 Assistant.
     * The optimizer deliberately owns no window when used through this method.
     */
    public JPanel createContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBackground(AssistantTheme.BACKGROUND);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.setBackground(AssistantTheme.BACKGROUND);
        preserveCurveLines.setBackground(AssistantTheme.BACKGROUND);
        controls.add(preserveCurveLines);
        controls.add(new JLabel("Grid step:"));
        gridStepBox.setSelectedItem(2);
        controls.add(gridStepBox);
        controls.add(new JLabel("Min. move:"));
        minMoveBox.setSelectedItem(32);
        minMoveBox.setToolTipText("Minimum move for genuinely off-grid coordinates; tiny Unreal rounding errors are only corrected.");
        minMoveBox.addActionListener(event -> {
            if ((Integer) maxMoveBox.getSelectedItem() < (Integer) minMoveBox.getSelectedItem()) {
                maxMoveBox.setSelectedItem(minMoveBox.getSelectedItem());
            }
        });
        controls.add(minMoveBox);
        controls.add(new JLabel("Max move:"));
        maxMoveBox.setSelectedItem(256);
        maxMoveBox.addActionListener(event -> {
            if ((Integer) minMoveBox.getSelectedItem() > (Integer) maxMoveBox.getSelectedItem()) {
                minMoveBox.setSelectedItem(maxMoveBox.getSelectedItem());
            }
        });
        controls.add(maxMoveBox);
        preserveCurveLines.addActionListener(event -> updateCurveMode());
        updateCurveMode();
        controls.add(new JLabel("Font size:"));
        fontSizeBox.setSelectedItem(12);
        fontSizeBox.addActionListener(event -> setCodeFontSize((Integer) fontSizeBox.getSelectedItem()));
        controls.add(fontSizeBox);
        statusLabel.setForeground(new Color(0, 128, 0));
        controls.add(statusLabel);
        JPanel header = new JPanel(new BorderLayout(0, 5));
        header.setBackground(AssistantTheme.BACKGROUND);
        JLabel heading = new JLabel("Brush Optimizer");
        AssistantTheme.stylePageTitle(heading);
        header.add(heading, BorderLayout.NORTH);
        header.add(controls, BorderLayout.SOUTH);
        root.add(header, BorderLayout.NORTH);

        inputArea.setToolTipText("Paste your map code here.");
        outputArea.setEditable(false);
        TextSearchSupport.install(inputArea, root, "Input Code");
        TextSearchSupport.install(outputArea, root, "Optimized Result");

        JSplitPane codeSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titledScroll("Input Code:", inputArea), titledScroll("Optimized result:", outputArea));
        codeSplit.setResizeWeight(0.5);
        AssistantTheme.styleSplitPane(codeSplit);
        SplitPaneState.install(codeSplit, BrushOptimizer.class, "code");

        logPane.setEditable(false);
        logPane.setFont(new Font("Verdana", Font.PLAIN, 12));
        logPane.setBackground(AssistantTheme.CODE_BACKGROUND);
        JScrollPane logScroll = new JScrollPane(logPane);
        logScroll.setBorder(AssistantTheme.titled("Log"));
        logScroll.setPreferredSize(new Dimension(950, 150));

        JPanel lowerWorkspace = new JPanel(new BorderLayout(7, 0));
        lowerWorkspace.setOpaque(false);
        lowerWorkspace.add(logScroll, BorderLayout.CENTER);
        lowerWorkspace.add(preview, BorderLayout.WEST);

        JPanel lowerSection = new JPanel(new BorderLayout(0, 5));
        lowerSection.setBackground(AssistantTheme.BACKGROUND);
        JPanel actions = new JPanel(new EdgeAlignedFlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setBackground(AssistantTheme.BACKGROUND);
        actions.setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 0));
        actions.add(outlinedButton("Analyze", new Color(224, 132, 40), this::analyzeOnly));
        actions.add(outlinedButton("Optimize", new Color(45, 170, 85), this::optimizeAllBrushes));
        actions.add(button("Paste", this::pasteInput));
        actions.add(button("Copy result", this::copyOutput));
        actions.add(button("Reset", this::reset));
        lowerSection.add(actions, BorderLayout.NORTH);
        lowerSection.add(lowerWorkspace, BorderLayout.CENTER);

        JSplitPane workspaceSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codeSplit, lowerSection);
        workspaceSplit.setResizeWeight(0.78);
        AssistantTheme.styleSplitPane(workspaceSplit);
        SplitPaneState.install(workspaceSplit, BrushOptimizer.class, "workspace");
        root.add(workspaceSplit, BorderLayout.CENTER);
        return root;
    }

    private JButton button(String label, java.util.function.Consumer<ActionEvent> action) {
        JButton button = new JButton(label);
        button.addActionListener(action::accept);
        return button;
    }

    private JButton outlinedButton(String label, Color color,
                                   java.util.function.Consumer<ActionEvent> action) {
        JButton button = button(label, action);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 2),
                BorderFactory.createEmptyBorder(3, 9, 3, 9)));
        return button;
    }

    private JScrollPane titledScroll(String title, JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(AssistantTheme.titled(title));
        return scroll;
    }

    private void setCodeFontSize(int size) {
        inputArea.setFont(inputArea.getFont().deriveFont((float) size));
        outputArea.setFont(outputArea.getFont().deriveFont((float) size));
    }

    private JTextArea createCodeArea() {
        JTextArea area = new JTextArea();
        area.setFont(new Font("Verdana", Font.PLAIN, 12));
        area.setLineWrap(false);
        area.setBackground(AssistantTheme.CODE_BACKGROUND);
        return area;
    }

    private void updateCurveMode() {
        if (preserveCurveLines.isSelected()) {
            gridStepBox.setSelectedItem(2);
            minMoveBox.setSelectedItem(0);
        } else {
            gridStepBox.setSelectedItem(2);
            minMoveBox.setSelectedItem(32);
        }
        gridStepBox.setEnabled(!preserveCurveLines.isSelected());
        minMoveBox.setEnabled(!preserveCurveLines.isSelected());
    }

    private void analyzeOnly(ActionEvent ignored) { runOptimization(false); }

    private void optimizeAllBrushes(ActionEvent ignored) {
        runOptimization(true);
    }

    private void runOptimization(boolean writeOutput) {
        if (optimizationRunning) return;
        String input = inputArea.getText();
        int gridStep = (Integer) gridStepBox.getSelectedItem();
        int minMove = (Integer) minMoveBox.getSelectedItem();
        int maxMove = (Integer) maxMoveBox.getSelectedItem();
        boolean preserveCurves = preserveCurveLines.isSelected();
        int generation = ++optimizationGeneration;
        optimizationRunning = true;
        statusLabel.setForeground(AssistantTheme.MUTED);
        statusLabel.setText(writeOutput ? "Optimizing..." : "Analyzing...");

        new SwingWorker<OptimizationRun, Void>() {
            @Override protected OptimizationRun doInBackground() {
                List<BrushIssue> found = findOffGridBrushes(input, gridStep, minMove);
                boolean[] optimizeActor = new boolean[found.size()];
                java.util.Arrays.fill(optimizeActor, true);
                String optimized = optimizeMap(
                        input, found, optimizeActor, gridStep, minMove, maxMove, preserveCurves);
                return new OptimizationRun(input, found, optimized);
            }

            @Override protected void done() {
                optimizationRunning = false;
                if (generation != optimizationGeneration) return;
                try {
                    OptimizationRun run = get();
                    analyzedMap = run.input();
                    issues = run.issues();
                    int changedLines = writeOptimizationLog(analyzedMap, run.optimized(), !writeOutput);
                    appendPlanarityWarnings(run.optimized());
                    if (writeOutput) outputArea.setText(run.optimized());
                    boolean faulty = issues.stream().anyMatch(issue -> issue.offGridCoordinates > 0);
                    preview.showBrush(writeOutput ? run.optimized() : analyzedMap,
                            writeOutput || !faulty ? new Color(94, 205, 130) : new Color(225, 75, 75),
                            writeOutput ? "Optimized result" : faulty ? "Input: off-grid" : "Input: on-grid");
                    statusLabel.setForeground(new Color(0, 128, 0));
                    statusLabel.setText(changedLines == 0
                            ? "OK: No changes required"
                            : (writeOutput ? "OK: Updated " : "OK: Analysis found ")
                                    + changedLines + " Vertex line(s)");
                } catch (Exception exception) {
                    statusLabel.setForeground(new Color(180, 40, 40));
                    statusLabel.setText("Optimization failed: " + exception.getMessage());
                }
            }
        }.execute();
    }

    private void copyOutput(ActionEvent ignored) {
        String output = outputArea.getText();
        if (output.isEmpty()) {
            statusLabel.setForeground(new Color(180, 40, 40));
            statusLabel.setText("Nothing to copy");
            return;
        }
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(output), null);
        statusLabel.setForeground(new Color(0, 128, 0));
        statusLabel.setText("OK: Copied");
    }

    private void pasteInput(ActionEvent ignored) {
        try {
            inputArea.setText(ClipboardTextSupport.readText());
            inputArea.setCaretPosition(0);
            statusLabel.setForeground(new Color(0, 128, 0));
            statusLabel.setText("OK: Pasted input code");
        } catch (Exception exception) {
            statusLabel.setForeground(new Color(180, 40, 40));
            statusLabel.setText("Clipboard does not contain text");
        }
    }

    private void reset(ActionEvent ignored) {
        optimizationGeneration++;
        inputArea.setText("");
        outputArea.setText("");
        logPane.setText("");
        issues = List.of();
        analyzedMap = "";
        preview.showBrush("", AssistantTheme.MUTED, "Press Analyze");
        statusLabel.setText(" ");
    }

    private int writeOptimizationLog(String original, String optimized, boolean analysisOnly) {
        logPane.setText("");
        String[] originalLines = original.split("\\R", -1);
        String[] optimizedLines = optimized.split("\\R", -1);
        int changeCount = 0;

        if (issues.isEmpty()) {
            appendLog("No Brush actors were found in the input.\n", new Color(180, 40, 40));
            return 0;
        }

        List<BrushIssue> unchangedBrushes = new ArrayList<>();
        boolean analysisHeadingWritten = false;
        for (BrushIssue issue : issues) {
            List<Integer> changedLines = new ArrayList<>();
            for (int line = issue.startLine; line <= issue.endLine; line++) {
                if (VERTEX_LINE.matcher(originalLines[line]).find() && !originalLines[line].equals(optimizedLines[line])) {
                    changedLines.add(line);
                }
            }
            changeCount += changedLines.size();
            if (changedLines.isEmpty()) {
                unchangedBrushes.add(issue);
            } else {
                Color changeColor = analysisOnly ? new Color(180, 40, 40) : new Color(30, 70, 160);
                if (analysisOnly && !analysisHeadingWritten) {
                    appendLog("Brushes that can be optimized:\n", changeColor);
                    analysisHeadingWritten = true;
                }
                appendLog(issue.name + ": " + (analysisOnly ? "would update " : "updated ")
                        + changedLines.size() + " Vertex line(s).\n", changeColor);
                for (int line : changedLines) {
                    appendLog("  " + originalLines[line].trim() + "  ->  " + optimizedLines[line].trim() + "\n", changeColor);
                }
            }
        }
        if (!unchangedBrushes.isEmpty()) {
            appendLog("\nAlready on-grid brushes; no changes required:\n", new Color(0, 128, 0));
            for (BrushIssue issue : unchangedBrushes) {
                appendLog("  OK: " + issue.name + "\n", new Color(0, 128, 0));
            }
        }
        return changeCount;
    }

    private void appendLog(String text, Color color) {
        StyledDocument document = logPane.getStyledDocument();
        javax.swing.text.SimpleAttributeSet style = new javax.swing.text.SimpleAttributeSet();
        StyleConstants.setForeground(style, color);
        try {
            document.insertString(document.getLength(), text, style);
        } catch (BadLocationException exception) {
            throw new IllegalStateException("Unable to append to the optimization log.", exception);
        }
    }

    private void appendPlanarityWarnings(String code) {
        String[] lines = code.split("\\R", -1); List<Point3> polygon = new ArrayList<>(); int warnings = 0;
        for (String line : lines) {
            Point3 point = readVertex(line); if (point != null) polygon.add(point);
            if (line.trim().equalsIgnoreCase("End Polygon")) {
                if (polygon.size() == 4 && planeDistance(polygon) > 0.1) warnings++;
                polygon.clear();
            }
        }
        if (warnings > 0) appendLog("\nPlanarity warnings: " + warnings + " non-planar quad(s).\n", new Color(180, 100, 0));
    }

    private static double planeDistance(List<Point3> p) {
        Point3 a=p.get(0), b=p.get(1), c=p.get(2), d=p.get(3); double[] u={b.x-a.x,b.y-a.y,b.z-a.z}, v={c.x-a.x,c.y-a.y,c.z-a.z};
        double nx=u[1]*v[2]-u[2]*v[1], ny=u[2]*v[0]-u[0]*v[2], nz=u[0]*v[1]-u[1]*v[0];
        double length=Math.sqrt(nx*nx+ny*ny+nz*nz); return length == 0 ? 0 : Math.abs(nx*(d.x-a.x)+ny*(d.y-a.y)+nz*(d.z-a.z))/length;
    }

    private static List<BrushIssue> findOffGridBrushes(String map, int gridStep, int minMove) {
        String[] lines = map.split("\\R", -1);
        List<BrushIssue> found = new ArrayList<>();

        for (int index = 0; index < lines.length; index++) {
            Matcher begin = BEGIN_BRUSH_ACTOR.matcher(lines[index]);
            if (!begin.matches()) continue;

            int end = findActorEnd(lines, index + 1);
            if (end < 0) break;
            int offGridCoordinates = 0;
            double largestAdjustment = 0.0;
            for (int line = index; line <= end; line++) {
                GridCheck check = checkVertexLine(lines[line], gridStep, minMove);
                offGridCoordinates += check.offGridCoordinates;
                largestAdjustment = Math.max(largestAdjustment, check.largestAdjustment);
            }
            found.add(new BrushIssue(begin.group(1), index, end, offGridCoordinates, largestAdjustment));
            index = end;
        }
        return found;
    }

    private static int findActorEnd(String[] lines, int start) {
        for (int index = start; index < lines.length; index++) {
            if (END_ACTOR.matcher(lines[index]).matches()) return index;
        }
        return -1;
    }

    private static GridCheck checkVertexLine(String line, int gridStep, int minMove) {
        if (!VERTEX_LINE.matcher(line).find()) return GridCheck.CLEAN;
        Matcher matcher = NUMBER.matcher(line);
        int offGrid = 0;
        double largestAdjustment = 0.0;
        for (int coordinate = 0; coordinate < 3 && matcher.find(); coordinate++) {
            double value = Double.parseDouble(matcher.group());
            double snapped = snapTarget(value, gridStep, minMove);
            double adjustment = Math.abs(snapped - value);
            if (adjustment > GRID_EPSILON) {
                offGrid++;
                largestAdjustment = Math.max(largestAdjustment, adjustment);
            }
        }
        return new GridCheck(offGrid, largestAdjustment);
    }

    private static String optimizeMap(String map, List<BrushIssue> issues, boolean[] optimizeActor,
                                      int gridStep, int minMove, int maxMove, boolean preserveStraightChains) {
        String separator = lineSeparator(map);
        String[] lines = map.split("\\R", -1);
        StringBuilder output = new StringBuilder();
        int issueIndex = 0;

        for (int line = 0; line < lines.length; line++) {
            while (issueIndex < issues.size() && line > issues.get(issueIndex).endLine) issueIndex++;
            boolean optimize = issueIndex < issues.size() && optimizeActor[issueIndex]
                    && line >= issues.get(issueIndex).startLine && line <= issues.get(issueIndex).endLine;
            output.append(optimize && VERTEX_LINE.matcher(lines[line]).find()
                    ? snapVertexLine(lines[line], gridStep, minMove, maxMove) : lines[line]);
            if (line < lines.length - 1) output.append(separator);
        }
        String optimized = output.toString();
        return preserveStraightChains
                ? preserveCollinearMidpoints(map, optimized, issues, optimizeActor, gridStep)
                : optimized;
    }

    /**
     * Grid snapping can break a curve chain such as A--M--B when M was the
     * midpoint of A and B in the original brush. Restore that exact midpoint
     * relationship while keeping all three points on the selected grid.
     */
    private static String preserveCollinearMidpoints(String originalMap, String snappedMap,
                                                      List<BrushIssue> issues, boolean[] optimizeActor, int gridStep) {
        String separator = lineSeparator(snappedMap);
        String[] originalLines = originalMap.split("\\R", -1);
        String[] snappedLines = snappedMap.split("\\R", -1);

        for (int issueIndex = 0; issueIndex < issues.size(); issueIndex++) {
            if (!optimizeActor[issueIndex]) continue;
            BrushIssue issue = issues.get(issueIndex);
            Map<Point3, List<Integer>> occurrences = new HashMap<>();
            for (int line = issue.startLine; line <= issue.endLine; line++) {
                Point3 point = readVertex(originalLines[line]);
                if (point != null) occurrences.computeIfAbsent(point, ignored -> new ArrayList<>()).add(line);
            }

            List<Point3> points = new ArrayList<>(occurrences.keySet());
            List<MidpointRelation> relations = new ArrayList<>();
            for (int middleIndex = 0; middleIndex < points.size(); middleIndex++) {
                Point3 middle = points.get(middleIndex);
                for (int firstIndex = 0; firstIndex < points.size(); firstIndex++) {
                    if (firstIndex == middleIndex) continue;
                    for (int lastIndex = firstIndex + 1; lastIndex < points.size(); lastIndex++) {
                        if (lastIndex == middleIndex) continue;
                        Point3 first = points.get(firstIndex);
                        Point3 last = points.get(lastIndex);
                        if (middle.isMidpointOf(first, last, Math.max(MIDPOINT_TOLERANCE, gridStep))) {
                            relations.add(new MidpointRelation(first, middle, last));
                        }
                    }
                }
            }

            // A single accidental midpoint is common in regular geometry. Two
            // or more linked midpoint chains identify the curved-brush pattern.
            if (relations.size() < 2) continue;

            Map<Point3, Point3> corrected = new HashMap<>();
            for (Point3 original : points) corrected.put(original, readVertex(snappedLines[occurrences.get(original).get(0)]));
            for (MidpointRelation relation : relations) {
                Point3 first = corrected.get(relation.first);
                Point3 middle = corrected.get(relation.middle);
                Point3 last = corrected.get(relation.last);
                Point3[] aligned = alignMidpoint(first, middle, last, relation.first, relation.last, gridStep);
                corrected.put(relation.first, aligned[0]);
                corrected.put(relation.middle, aligned[1]);
                corrected.put(relation.last, aligned[2]);
            }
            for (Map.Entry<Point3, List<Integer>> entry : occurrences.entrySet()) {
                Point3 replacement = corrected.get(entry.getKey());
                for (int line : entry.getValue()) snappedLines[line] = replaceVertex(snappedLines[line], replacement);
            }
        }
        return String.join(separator, snappedLines);
    }

    private static String lineSeparator(String text) {
        if (text.contains("\r\n")) return "\r\n";
        if (text.indexOf('\r') >= 0) return "\r";
        return "\n";
    }

    private static Point3[] alignMidpoint(Point3 first, Point3 middle, Point3 last,
                                          Point3 originalFirst, Point3 originalLast, int gridStep) {
        double[] adjustedFirst = first.toArray();
        double[] adjustedMiddle = middle.toArray();
        double[] adjustedLast = last.toArray();
        double[] originalA = originalFirst.toArray();
        double[] originalB = originalLast.toArray();

        for (int axis = 0; axis < 3; axis++) {
            AxisAlignment alignment = chooseAlignedAxis(adjustedFirst[axis], adjustedMiddle[axis], adjustedLast[axis],
                    originalA[axis], originalB[axis], gridStep);
            adjustedFirst[axis] = alignment.first;
            adjustedMiddle[axis] = alignment.middle;
            adjustedLast[axis] = alignment.last;
        }
        return new Point3[] { Point3.of(adjustedFirst), Point3.of(adjustedMiddle), Point3.of(adjustedLast) };
    }

    /** Finds a nearby grid-aligned A--M--B chain, preferring coarse shared grid nodes. */
    private static AxisAlignment chooseAlignedAxis(double first, double middle, double last,
                                                    double originalFirst, double originalLast, int gridStep) {
        if (Math.abs(middle - (first + last) / 2.0) <= GRID_EPSILON) {
            return new AxisAlignment(first, middle, last);
        }

        AxisAlignment best = null;
        int bestGridQuality = Integer.MIN_VALUE;
        double bestMovement = Double.MAX_VALUE;
        for (int firstMove = -MAX_CURVE_GRID_MOVES; firstMove <= MAX_CURVE_GRID_MOVES; firstMove++) {
            double candidateFirst = first + firstMove * gridStep;
            for (int lastMove = -MAX_CURVE_GRID_MOVES; lastMove <= MAX_CURVE_GRID_MOVES; lastMove++) {
                double candidateLast = last + lastMove * gridStep;
                double candidateMiddle = (candidateFirst + candidateLast) / 2.0;
                if (Math.abs(candidateMiddle / gridStep - Math.rint(candidateMiddle / gridStep)) > GRID_EPSILON) continue;

                int quality = gridQuality(candidateFirst, gridStep) + gridQuality(candidateMiddle, gridStep)
                        + gridQuality(candidateLast, gridStep);
                double movement = squared(candidateFirst - originalFirst) + squared(candidateMiddle - middle)
                        + squared(candidateLast - originalLast);
                if (quality > bestGridQuality || (quality == bestGridQuality && movement < bestMovement)) {
                    best = new AxisAlignment(candidateFirst, candidateMiddle, candidateLast);
                    bestGridQuality = quality;
                    bestMovement = movement;
                }
            }
        }
        return best == null ? new AxisAlignment(first, middle, last) : best;
    }

    private static int gridQuality(double value, int baseStep) {
        if (Math.abs(value) < GRID_EPSILON) return 0;
        long units = Math.abs(Math.round(value / baseStep));
        int quality = 0;
        while (units > 0 && units % 2 == 0) {
            quality++;
            units /= 2;
        }
        return quality;
    }

    private static double squared(double value) { return value * value; }

    private static String snapVertexLine(String line, int gridStep, int minMove, int maxMove) {
        if (!VERTEX_LINE.matcher(line).find()) return line;
        Matcher matcher = NUMBER.matcher(line);
        StringBuffer output = new StringBuffer();
        int coordinate = 0;
        while (coordinate < 3 && matcher.find()) {
            String token = matcher.group();
            double value = Double.parseDouble(token);
            double snapped = snapTarget(value, gridStep, minMove);
            double adjustment = Math.abs(snapped - value);
            String replacement = adjustment > GRID_EPSILON && adjustment <= maxMove ? format(snapped, token.startsWith("-")) : token;
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
            coordinate++;
        }
        matcher.appendTail(output);
        return output.toString();
    }

    /**
     * Returns an aligned coordinate. Tiny serialization errors go to the
     * nearest grid point; genuinely off-grid values continue in that snapping
     * direction until the configured minimum movement is reached.
     */
    private static double snapTarget(double value, int gridStep, int minMove) {
        double nearest = Math.rint(value / gridStep) * gridStep;
        double nearestMove = Math.abs(nearest - value);
        if (nearestMove <= GRID_EPSILON || nearestMove <= GRID_NOISE_TOLERANCE || minMove <= nearestMove) {
            return nearest;
        }
        double direction = Math.signum(nearest - value);
        if (direction == 0.0) return nearest;
        double target = nearest;
        while (Math.abs(target - value) + GRID_EPSILON < minMove) {
            target += direction * gridStep;
        }
        return target;
    }

    private static Point3 readVertex(String line) {
        if (!VERTEX_LINE.matcher(line).find()) return null;
        Matcher matcher = NUMBER.matcher(line);
        double[] values = new double[3];
        for (int coordinate = 0; coordinate < values.length; coordinate++) {
            if (!matcher.find()) return null;
            values[coordinate] = Double.parseDouble(matcher.group());
        }
        return Point3.of(values);
    }

    private static String replaceVertex(String line, Point3 replacement) {
        return replacePoint(line, replacement, VERTEX_LINE);
    }

    private static String replacePoint(String line, Point3 replacement, Pattern linePattern) {
        if (!linePattern.matcher(line).find()) return line;
        Matcher matcher = NUMBER.matcher(line);
        if (!matcher.find()) return line;
        int numberStart = matcher.start();
        int numberEnd = matcher.end();
        for (int coordinate = 1; coordinate < 3; coordinate++) {
            if (!matcher.find()) return line;
            numberEnd = matcher.end();
        }
        return line.substring(0, numberStart)
                + format(replacement.x, replacement.x < 0.0) + ","
                + format(replacement.y, replacement.y < 0.0) + ","
                + format(replacement.z, replacement.z < 0.0)
                + line.substring(numberEnd);
    }

    private static String format(double value, boolean negativeInput) {
        if (value == 0.0 && negativeInput) return "-00000.000000";
        return String.format(Locale.US, "%+013.6f", value);
    }

    private record GridCheck(int offGridCoordinates, double largestAdjustment) {
        static final GridCheck CLEAN = new GridCheck(0, 0.0);
    }

    private record BrushIssue(String name, int startLine, int endLine, int offGridCoordinates,
                              double largestAdjustment) {
        @Override
        public String toString() {
            if (offGridCoordinates == 0) return name + " — on-grid, checked for curve alignment";
            return name + " — " + offGridCoordinates + " off-grid coordinate(s), largest move: "
                    + String.format(Locale.US, "%.6f", largestAdjustment);
        }
    }

    private record MidpointRelation(Point3 first, Point3 middle, Point3 last) { }

    private record AxisAlignment(double first, double middle, double last) { }

    private record OptimizationRun(String input, List<BrushIssue> issues, String optimized) { }

    private record Point3(double x, double y, double z) {
        static Point3 of(double[] values) { return new Point3(values[0], values[1], values[2]); }
        double[] toArray() { return new double[] { x, y, z }; }
        boolean isMidpointOf(Point3 first, Point3 last, double tolerance) {
            return Math.abs(x - (first.x + last.x) / 2.0) <= tolerance
                    && Math.abs(y - (first.y + last.y) / 2.0) <= tolerance
                    && Math.abs(z - (first.z + last.z) / 2.0) <= tolerance;
        }
    }
}
