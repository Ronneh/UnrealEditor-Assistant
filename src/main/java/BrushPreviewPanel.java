import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Animated, orthographic wireframe preview for the first brush in T3D code. */
public final class BrushPreviewPanel extends JPanel {
    private static final Pattern NUMBER = Pattern.compile(
            "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?");
    private double angle;
    private double tilt = Math.toRadians(58);
    private double zoom = 1.0;
    private int dragY;
    private final Timer animation = new Timer(16, event -> {
        angle += 0.009;
        repaint();
    });
    private List<List<Point3>> polygons = List.of();
    private Color wireColor = AssistantTheme.MUTED;
    private String caption = "Press Analyze";

    public BrushPreviewPanel() {
        setBackground(Color.BLACK);
        setOpaque(true);
        setPreferredSize(new Dimension(285, 170));
        setMinimumSize(new Dimension(220, 120));
        setToolTipText("Drag vertically to tilt; use the mouse wheel to zoom");
        MouseAdapter tiltControl = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (event.getButton() != MouseEvent.BUTTON1) return;
                dragY = event.getY();
                setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            }

            @Override public void mouseDragged(MouseEvent event) {
                if ((event.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == 0) return;
                int delta = event.getY() - dragY;
                tilt -= delta * 0.012;
                dragY = event.getY();
                repaint();
            }

            @Override public void mouseReleased(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) setCursor(Cursor.getDefaultCursor());
            }
        };
        addMouseListener(tiltControl);
        addMouseMotionListener(tiltControl);
        addMouseWheelListener(event -> {
            zoom = Math.max(0.35, Math.min(4.0,
                    zoom * Math.pow(1.12, -event.getPreciseWheelRotation())));
            event.consume();
            repaint();
        });
        updateBorderTitle();
    }

    public void showBrush(String code, Color color, String source) {
        showBrush(code, color, source, 0);
    }

    public void showBrush(String code, Color color, String source, int brushIndex) {
        List<List<List<Point3>>> brushes = parseBrushes(code);
        int index = Math.max(0, Math.min(brushIndex, brushes.size() - 1));
        polygons = brushes.isEmpty() ? List.of() : brushes.get(index);
        wireColor = color;
        caption = polygons.isEmpty() ? ("Press Analyze".equals(source) ? source : "No brush vertices found") : source
                + (brushes.size() > 1 ? " (" + (index + 1) + "/" + brushes.size() + ")" : "");
        angle = 0;
        updateBorderTitle();
        if (!polygons.isEmpty() && !animation.isRunning()) animation.start();
        revalidate();
        repaint();
    }

    private void updateBorderTitle() {
        setBorder(AssistantTheme.titled("Brush preview: " + caption));
    }

    @Override public void removeNotify() {
        animation.stop();
        super.removeNotify();
    }

    @Override public void addNotify() {
        super.addNotify();
        if (!polygons.isEmpty() && !animation.isRunning()) animation.start();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Insets insets = getInsets();
        g.setColor(AssistantTheme.BACKGROUND);
        g.fillRect(1, 1, Math.max(0, getWidth() - 2), Math.max(0, insets.top - 1));
        if (polygons.isEmpty()) {
            g.dispose();
            return;
        }

        Bounds bounds = bounds(polygons);
        double availableWidth = Math.max(1, getWidth() - insets.left - insets.right - 24);
        double availableHeight = Math.max(1, getHeight() - insets.top - insets.bottom - 20);
        double scale = Math.min(availableWidth, availableHeight)
                / Math.max(1, bounds.radius * 2.0) * 0.78 * zoom;
        double centerX = insets.left + (getWidth() - insets.left - insets.right) / 2.0;
        double centerY = insets.top + (getHeight() - insets.top - insets.bottom) / 2.0 + 3;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double tiltCos = Math.cos(tilt), tiltSin = Math.sin(tilt);

        g.setColor(wireColor);
        g.setStroke(new BasicStroke(1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (List<Point3> polygon : polygons) {
            if (polygon.size() < 2) continue;
            for (int index = 0; index < polygon.size(); index++) {
                Point3 first = polygon.get(index);
                Point3 second = polygon.get((index + 1) % polygon.size());
                ScreenPoint a = project(first, bounds, cos, sin, tiltCos, tiltSin, scale, centerX, centerY);
                ScreenPoint b = project(second, bounds, cos, sin, tiltCos, tiltSin, scale, centerX, centerY);
                g.draw(new Line2D.Double(a.x, a.y, b.x, b.y));
            }
        }
        g.dispose();
    }

    private static ScreenPoint project(Point3 point, Bounds bounds, double cos, double sin,
                                       double tiltCos, double tiltSin, double scale,
                                       double centerX, double centerY) {
        double x = point.x - bounds.centerX;
        double y = point.y - bounds.centerY;
        double z = point.z - bounds.centerZ;
        double rotatedX = x * cos - y * sin;
        double rotatedY = x * sin + y * cos;
        double projectedY = rotatedY * tiltCos - z * tiltSin;
        return new ScreenPoint(centerX + rotatedX * scale, centerY + projectedY * scale);
    }

    static int polygonCount(String code) { return parseBrushes(code).stream().mapToInt(List::size).sum(); }
    static int brushCount(String code) { return parseBrushes(code).size(); }

    private static List<List<List<Point3>>> parseBrushes(String code) {
        List<List<List<Point3>>> brushes = new ArrayList<>();
        List<List<Point3>> result = null;
        List<Point3> polygon = null;
        boolean insideBrush = false;
        boolean insidePolyList = false;
        for (String line : code.split("\\R")) {
            String trimmed = line.trim();
            if (startsWithIgnoreCase(trimmed, "Begin Brush")) {
                if (insideBrush && result != null && !result.isEmpty()) brushes.add(List.copyOf(result));
                insideBrush = true;
                result = new ArrayList<>();
            } else if (insideBrush && trimmed.equalsIgnoreCase("End Brush")) {
                if (result != null && !result.isEmpty()) brushes.add(List.copyOf(result));
                result = null; polygon = null; insideBrush = false; insidePolyList = false;
            } else if (startsWithIgnoreCase(trimmed, "Begin PolyList")) {
                if (!insideBrush) result = new ArrayList<>();
                insidePolyList = true;
            } else if (trimmed.equalsIgnoreCase("End PolyList")) {
                if (!insideBrush && result != null && !result.isEmpty()) brushes.add(List.copyOf(result));
                if (!insideBrush) result = null;
                insidePolyList = false;
            } else if ((insideBrush || insidePolyList) && startsWithIgnoreCase(trimmed, "Begin Polygon")) {
                polygon = new ArrayList<>();
            } else if ((insideBrush || insidePolyList) && trimmed.equalsIgnoreCase("End Polygon")) {
                if (result != null && polygon != null && polygon.size() >= 2) result.add(List.copyOf(polygon));
                polygon = null;
            } else if ((insideBrush || insidePolyList) && polygon != null
                    && startsWithIgnoreCase(trimmed, "Vertex")) {
                Matcher matcher = NUMBER.matcher(trimmed);
                double[] values = new double[3];
                boolean complete = true;
                for (int coordinate = 0; coordinate < 3; coordinate++) {
                    if (!matcher.find()) {
                        complete = false;
                        break;
                    }
                    values[coordinate] = Double.parseDouble(matcher.group());
                }
                if (complete) polygon.add(new Point3(values[0], values[1], values[2]));
            }
        }
        if (result != null && !result.isEmpty()) brushes.add(List.copyOf(result));
        return List.copyOf(brushes);
    }

    private static boolean startsWithIgnoreCase(String text, String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static Bounds bounds(List<List<Point3>> polygons) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (List<Point3> polygon : polygons) {
            for (Point3 point : polygon) {
                minX = Math.min(minX, point.x);
                minY = Math.min(minY, point.y);
                minZ = Math.min(minZ, point.z);
                maxX = Math.max(maxX, point.x);
                maxY = Math.max(maxY, point.y);
                maxZ = Math.max(maxZ, point.z);
            }
        }
        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double halfX = (maxX - minX) / 2.0;
        double halfY = (maxY - minY) / 2.0;
        double halfZ = (maxZ - minZ) / 2.0;
        double radius = Math.sqrt(halfX * halfX + halfY * halfY + halfZ * halfZ);
        return new Bounds(centerX, centerY, centerZ, radius);
    }

    private record Point3(double x, double y, double z) { }
    private record ScreenPoint(double x, double y) { }
    private record Bounds(double centerX, double centerY, double centerZ, double radius) { }
}
