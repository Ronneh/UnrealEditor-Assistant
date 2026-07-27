import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/** Creates grid-aligned cylinder brushes as Unreal T3D code. */
public final class BrushGeneratorPanel extends JPanel {
    private static final double TEXTURE_SIZE = 1024.0;
    private final JTextField brushName = new JTextField("GeneratedBrush", 14);
    private final JComboBox<String> operation =
            new JComboBox<>(new String[] { "CSG_Add", "CSG_Subtract" });
    private final JComboBox<Integer> grid =
            new JComboBox<>(new Integer[] { 1, 2, 4, 8, 16, 32, 64, 128, 256 });

    private final JSpinner cylinderHeight = spinner(256, 16, 8192, 16);
    private final JSpinner outerRadius = spinner(256, 16, 8192, 16);
    private final JSpinner innerRadius = spinner(0, 0, 8176, 16);
    private final JSpinner cylinderSides = spinner(8, 3, 64, 1);
    private final JCheckBox alignToSide = new JCheckBox("True", true);

    private final JTextArea output = new JTextArea();
    private final JLabel status = new JLabel(" ");

    public BrushGeneratorPanel() {
        super(new BorderLayout(12, 12));
        setBackground(AssistantTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        grid.setSelectedItem(32);
        alignToSide.setOpaque(false);
        add(createHeader(), BorderLayout.NORTH);
        add(createProperties(), BorderLayout.WEST);
        add(createOutput(), BorderLayout.CENTER);
        status.setForeground(AssistantTheme.MUTED);
        add(status, BorderLayout.SOUTH);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Cylinder Brush Generator");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 23f));
        header.add(title, BorderLayout.WEST);
        JLabel description = new JLabel("Generate an on-grid cylinder brush for Unreal Editor.");
        description.setForeground(AssistantTheme.MUTED);
        header.add(description, BorderLayout.EAST);
        return header;
    }

    private JPanel createProperties() {
        JPanel card = AssistantTheme.card(new BorderLayout(0, 12));
        card.setPreferredSize(new Dimension(310, 0));
        JLabel title = new JLabel("Brush properties");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        card.add(title, BorderLayout.NORTH);

        JPanel allProperties = new JPanel(new BorderLayout(0, 9));
        allProperties.setOpaque(false);
        JPanel common = form();
        int row = 0;
        addRow(common, row++, "Name:", brushName);
        addRow(common, row++, "Operation:", operation);
        addRow(common, row, "Grid:", grid);
        allProperties.add(common, BorderLayout.NORTH);
        allProperties.add(cylinderProperties(), BorderLayout.CENTER);
        card.add(allProperties, BorderLayout.NORTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        actions.setOpaque(false);
        JButton generate = new JButton("Generate");
        generate.addActionListener(event -> generate());
        JButton copy = new JButton("Copy code");
        copy.addActionListener(event -> copy());
        actions.add(generate);
        actions.add(copy);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    private JPanel cylinderProperties() {
        JPanel panel = form();
        addRow(panel, 0, "Height:", cylinderHeight);
        addRow(panel, 1, "Outer Radius:", outerRadius);
        addRow(panel, 2, "Inner Radius:", innerRadius);
        addRow(panel, 3, "Sides:", cylinderSides);
        addRow(panel, 4, "Align to Side:", alignToSide);
        return panel;
    }

    private static JPanel form() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        return panel;
    }

    private JScrollPane createOutput() {
        output.setEditable(false);
        output.setLineWrap(false);
        output.setFont(new Font("Verdana", Font.PLAIN, 12));
        output.setBackground(new Color(17, 21, 27));
        output.setForeground(AssistantTheme.TEXT);
        JScrollPane scroll = new JScrollPane(output);
        scroll.setBorder(AssistantTheme.titled("Generated Brush"));
        return scroll;
    }

    private static void addRow(JPanel panel, int row, String label, java.awt.Component field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 7, 9);
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 7, 0);
        panel.add(field, c);
    }

    private static JSpinner spinner(int value, int min, int max, int step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setPreferredSize(new Dimension(112, 27));
        return spinner;
    }

    private void generate() {
        try {
            String name = brushName.getText().trim();
            validateName(name);
            int selectedGrid = (Integer) grid.getSelectedItem();
            String code = generateCylinder(name, (String) operation.getSelectedItem(),
                    (Integer) cylinderSides.getValue(), (Integer) outerRadius.getValue(),
                    (Integer) innerRadius.getValue(), (Integer) cylinderHeight.getValue(),
                    alignToSide.isSelected(), selectedGrid, 0, 0, 0);
            status.setText("Generated an on-grid cylinder.");
            output.setText(code);
            output.setCaretPosition(0);
            status.setForeground(new Color(94, 205, 130));
        } catch (IllegalArgumentException exception) {
            status.setForeground(new Color(225, 105, 105));
            status.setText(exception.getMessage());
            JOptionPane.showMessageDialog(this, exception.getMessage(),
                    "Could not generate brush", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void validateName(String name) {
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(
                    "The name must start with a letter or underscore and contain no spaces.");
        }
    }

    private void copy() {
        if (output.getText().isBlank()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(output.getText()), null);
        status.setForeground(new Color(94, 205, 130));
        status.setText("Brush code copied.");
    }

    static String generateCylinder(String name, String operation, int sides, int outerRadius,
                                   int innerRadius, int height, boolean alignToSide, int grid,
                                   int centerX, int centerY, int centerZ) {
        if (innerRadius < 0 || innerRadius >= outerRadius) {
            throw new IllegalArgumentException("Inner Radius must be smaller than Outer Radius.");
        }
        int bottom = snap(centerZ - height / 2.0, grid);
        int top = snap(centerZ + height / 2.0, grid);
        if (bottom == top) throw new IllegalArgumentException("Height is too small for the selected grid.");
        double offset = alignToSide ? Math.PI / sides : 0;
        List<GridPoint> outer = ring(sides, outerRadius, grid, centerX, centerY, offset, "Outer Radius");
        List<GridPoint> inner = innerRadius == 0 ? List.of()
                : ring(sides, innerRadius, grid, centerX, centerY, offset, "Inner Radius");
        StringBuilder code = startBrush(name, operation);

        for (int i = 0; i < sides; i++) {
            GridPoint a = outer.get(i), b = outer.get((i + 1) % sides);
            appendFace(code, List.of(point(a, bottom), point(a, top), point(b, top), point(b, bottom)), false);
        }
        if (inner.isEmpty()) {
            List<Point3> topFace = new ArrayList<>();
            List<Point3> bottomFace = new ArrayList<>();
            for (int i = sides - 1; i >= 0; i--) topFace.add(point(outer.get(i), top));
            for (GridPoint point : outer) bottomFace.add(point(point, bottom));
            appendFace(code, topFace, false, true);
            appendFace(code, bottomFace, false, true);
        } else {
            // Keep the T3D structure predictable: all walls first, all caps last.
            for (int i = 0; i < sides; i++) {
                GridPoint ia = inner.get(i), ib = inner.get((i + 1) % sides);
                appendFace(code, List.of(point(ia, bottom), point(ib, bottom),
                        point(ib, top), point(ia, top)), false);
            }
            for (int i = 0; i < sides; i++) {
                GridPoint oa = outer.get(i), ob = outer.get((i + 1) % sides);
                GridPoint ia = inner.get(i), ib = inner.get((i + 1) % sides);
                appendFace(code, List.of(point(oa, top), point(ia, top),
                        point(ib, top), point(ob, top)), false, true);
                appendFace(code, List.of(point(ob, bottom), point(ib, bottom),
                        point(ia, bottom), point(oa, bottom)), false, true);
            }
        }
        return finishBrush(code, name);
    }

    /** Compatibility entry point for the original solid prism generator. */
    static String generatePrism(String name, String operation, int sides, int radius,
                                int height, int grid, int centerX, int centerY, int centerZ) {
        return generateCylinder(name, operation, sides, radius, 0, height,
                false, grid, centerX, centerY, centerZ);
    }

    private static List<GridPoint> ring(int sides, int radius, int grid, int cx, int cy,
                                        double offset, String propertyName) {
        List<GridPoint> points = new ArrayList<>();
        Set<GridPoint> unique = new HashSet<>();
        for (int i = 0; i < sides; i++) {
            double angle = Math.PI / 2.0 + offset - i * Math.PI * 2.0 / sides;
            GridPoint point = new GridPoint(
                    snap(cx + Math.cos(angle) * radius, grid),
                    snap(cy + Math.sin(angle) * radius, grid));
            if (!unique.add(point)) {
                throw new IllegalArgumentException(propertyName
                        + " is too small for this side count and grid.");
            }
            points.add(point);
        }
        return points;
    }

    private static StringBuilder startBrush(String name, String operation) {
        return new StringBuilder()
                .append("Begin Map\n")
                .append("Begin Actor Class=Brush Name=").append(name).append('\n')
                .append("    CsgOper=").append(operation).append('\n')
                .append("    MainScale=(SheerAxis=SHEER_ZX)\n")
                .append("    PostScale=(SheerAxis=SHEER_ZX)\n")
                .append("    Level=LevelInfo'MyLevel.LevelInfo0'\n")
                .append("    Tag=\"Brush\"\n")
                .append("    Region=(Zone=LevelInfo'MyLevel.LevelInfo0',iLeaf=-1)\n")
                .append("    bSelected=True\n")
                .append("    Begin Brush Name=").append(name).append("Model\n")
                .append("        Begin PolyList\n");
    }

    private static String finishBrush(StringBuilder code, String name) {
        return code.append("        End PolyList\n")
                .append("    End Brush\n")
                .append("    Brush=Model'MyLevel.").append(name).append("Model'\n")
                .append("    Name=\"").append(name).append("\"\n")
                .append("End Actor\n")
                .append("Begin Surface\n")
                .append("End Surface\n")
                .append("End Map\n")
                .toString();
    }

    private static void appendFace(StringBuilder code, List<Point3> vertices, boolean tessellate) {
        appendFace(code, vertices, tessellate, false);
    }

    private static void appendFace(StringBuilder code, List<Point3> vertices,
                                   boolean tessellate, boolean cap) {
        if (tessellate && vertices.size() > 3) {
            for (int index = 1; index < vertices.size() - 1; index++) {
                appendPolygon(code, List.of(vertices.get(0), vertices.get(index),
                        vertices.get(index + 1)), cap && index == vertices.size() - 2);
            }
        } else {
            appendPolygon(code, vertices, cap);
        }
    }

    private static void appendPolygon(StringBuilder code, List<Point3> vertices, boolean cap) {
        if (vertices.size() < 3) return;
        TextureBasis basis = textureBasis(vertices);
        code.append("            Begin Polygon Item=").append(cap ? "Cap" : "Wall").append('\n')
                .append("                Origin   ").append(vector(
                        vertices.get(0).x, vertices.get(0).y, vertices.get(0).z)).append('\n')
                .append("                Normal   ").append(vector(
                        basis.normalX, basis.normalY, basis.normalZ)).append('\n')
                .append("                TextureU ").append(vector(
                        basis.uX, basis.uY, basis.uZ)).append('\n')
                .append("                TextureV ").append(vector(
                        basis.vX, basis.vY, basis.vZ)).append('\n');
        for (Point3 vertex : vertices) {
            code.append("                Vertex   ")
                    .append(vector(vertex.x, vertex.y, vertex.z)).append('\n');
        }
        code.append("            End Polygon\n");
    }

    private static TextureBasis textureBasis(List<Point3> vertices) {
        Point3 a = vertices.get(0), b = vertices.get(1), c = vertices.get(2);
        double abX = b.x - a.x, abY = b.y - a.y, abZ = b.z - a.z;
        double acX = c.x - a.x, acY = c.y - a.y, acZ = c.z - a.z;
        double nx = abY * acZ - abZ * acY;
        double ny = abZ * acX - abX * acZ;
        double nz = abX * acY - abY * acX;
        double length = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length == 0) throw new IllegalArgumentException("A generated polygon has no area.");
        nx /= length;
        ny /= length;
        nz /= length;

        int widthX = extent(vertices, true, false);
        int widthY = extent(vertices, false, true);
        int heightZ = extent(vertices, false, false);
        double ux = 0, uy = 0, uz = 0, vx = 0, vy = 0, vz = 0;
        if (Math.abs(nz) > 0.5) {
            ux = TEXTURE_SIZE / widthX;
            vy = Math.copySign(TEXTURE_SIZE / widthY, nz);
        } else {
            // Follow the actual horizontal edge. This is identical to the
            // axis-aligned UT texture bases and remains coplanar on cylinders.
            double edgeX = c.x - a.x;
            double edgeY = c.y - a.y;
            double edgeLengthSquared = edgeX * edgeX + edgeY * edgeY;
            ux = edgeX * TEXTURE_SIZE / edgeLengthSquared;
            uy = edgeY * TEXTURE_SIZE / edgeLengthSquared;
            vz = -TEXTURE_SIZE / heightZ;
        }
        return new TextureBasis(nx, ny, nz, ux, uy, uz, vx, vy, vz);
    }

    private static int extent(List<Point3> vertices, boolean xAxis, boolean yAxis) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (Point3 vertex : vertices) {
            int value = xAxis ? vertex.x : yAxis ? vertex.y : vertex.z;
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        return maximum - minimum;
    }

    private static Point3 point(GridPoint point, int z) {
        return new Point3(point.x, point.y, z);
    }

    private static int snap(double value, int grid) {
        return (int) Math.round(value / grid) * grid;
    }

    private static String vector(int x, int y, int z) {
        return format(x) + "," + format(y) + "," + format(z);
    }

    private static String vector(double x, double y, double z) {
        return format(x) + "," + format(y) + "," + format(z);
    }

    private static String format(double value) {
        if (Math.abs(value) < 0.0000005) value = 0;
        return String.format(Locale.US, "%+013.6f", value);
    }

    private record GridPoint(int x, int y) { }
    private record Point3(int x, int y, int z) { }
    private record TextureBasis(double normalX, double normalY, double normalZ,
                                double uX, double uY, double uZ,
                                double vX, double vY, double vZ) { }
}
