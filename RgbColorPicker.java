import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

/**
 * Compact RGB picker: saturation/brightness square, hue refinement strip and
 * exact RGB inputs. It intentionally has no tabbed models or preview panel.
 */
public final class RgbColorPicker extends JDialog {
    private final ColorSquare square = new ColorSquare();
    private final HueStrip hueStrip = new HueStrip();
    private final JSpinner red = channelSpinner();
    private final JSpinner green = channelSpinner();
    private final JSpinner blue = channelSpinner();
    private float hue;
    private float saturation;
    private float brightness;
    private boolean updating;
    private Color result;

    private RgbColorPicker(Window owner, String title, Color initial) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(createContent());
        setColor(initial == null ? Color.WHITE : initial);
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    public static Color show(Component parent, String title, Color initial) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        RgbColorPicker picker = new RgbColorPicker(owner, title, initial);
        picker.setVisible(true);
        return picker.result;
    }

    private JPanel createContent() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(AssistantTheme.PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 10, 12));

        JPanel selector = new JPanel(new BorderLayout(8, 0));
        selector.setOpaque(false);
        selector.add(square, BorderLayout.CENTER);
        selector.add(hueStrip, BorderLayout.EAST);
        root.add(selector, BorderLayout.CENTER);

        JPanel exact = new JPanel(new GridBagLayout());
        exact.setOpaque(false);
        addChannel(exact, 0, "R:", red);
        addChannel(exact, 1, "G:", green);
        addChannel(exact, 2, "B:", blue);
        ChangeListener channelChange = event -> updateFromChannels();
        red.addChangeListener(channelChange);
        green.addChangeListener(channelChange);
        blue.addChangeListener(channelChange);
        root.add(exact, BorderLayout.EAST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        actions.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JButton ok = new JButton("OK");
        ok.addActionListener(event -> {
            result = currentColor();
            dispose();
        });
        actions.add(cancel);
        actions.add(ok);
        root.add(actions, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(ok);
        return root;
    }

    private static void addChannel(JPanel panel, int row, String name, JSpinner spinner) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(0, 0, 6, 5);
        c.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel(name), c);
        c.gridx = 1;
        c.insets = new Insets(0, 0, 6, 0);
        panel.add(spinner, c);
    }

    private static JSpinner channelSpinner() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(255, 0, 255, 1));
        spinner.setPreferredSize(new Dimension(62, 27));
        if (spinner.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setBackground(AssistantTheme.PANEL_ALT);
            editor.getTextField().setForeground(AssistantTheme.TEXT);
            editor.getTextField().setCaretColor(AssistantTheme.TEXT);
        }
        return spinner;
    }

    private void setColor(Color color) {
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        updateChannels();
        square.invalidateGradient();
        repaint();
    }

    private Color currentColor() {
        return Color.getHSBColor(hue, saturation, brightness);
    }

    private void updateChannels() {
        updating = true;
        Color color = currentColor();
        red.setValue(color.getRed());
        green.setValue(color.getGreen());
        blue.setValue(color.getBlue());
        updating = false;
    }

    private void updateFromChannels() {
        if (updating) return;
        Color color = new Color((Integer) red.getValue(), (Integer) green.getValue(), (Integer) blue.getValue());
        float[] hsb = Color.RGBtoHSB(color.getRed(), color.getGreen(), color.getBlue(), null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
        square.invalidateGradient();
        repaint();
    }

    private final class ColorSquare extends JPanel {
        private BufferedImage gradient;

        ColorSquare() {
            setPreferredSize(new Dimension(280, 250));
            setBorder(BorderFactory.createLineBorder(AssistantTheme.BORDER));
            MouseAdapter selection = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) { select(event.getPoint()); }
                @Override public void mouseDragged(MouseEvent event) { select(event.getPoint()); }
            };
            addMouseListener(selection);
            addMouseMotionListener(selection);
        }

        void invalidateGradient() {
            gradient = null;
        }

        private void select(Point point) {
            saturation = clamp(point.x / (float) Math.max(1, getWidth() - 1));
            brightness = 1f - clamp(point.y / (float) Math.max(1, getHeight() - 1));
            updateChannels();
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            int width = getWidth(), height = getHeight();
            if (width <= 0 || height <= 0) return;
            if (gradient == null || gradient.getWidth() != width || gradient.getHeight() != height) {
                gradient = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < height; y++) {
                    float value = 1f - y / (float) Math.max(1, height - 1);
                    for (int x = 0; x < width; x++) {
                        float sat = x / (float) Math.max(1, width - 1);
                        gradient.setRGB(x, y, Color.HSBtoRGB(hue, sat, value));
                    }
                }
            }
            graphics.drawImage(gradient, 0, 0, null);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = Math.round(saturation * (width - 1));
            int y = Math.round((1f - brightness) * (height - 1));
            g.setColor(Color.BLACK);
            g.drawOval(x - 5, y - 5, 10, 10);
            g.setColor(Color.WHITE);
            g.drawOval(x - 4, y - 4, 8, 8);
            g.dispose();
        }
    }

    private final class HueStrip extends JPanel {
        HueStrip() {
            setPreferredSize(new Dimension(24, 250));
            setBorder(BorderFactory.createLineBorder(AssistantTheme.BORDER));
            MouseAdapter selection = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) { select(event.getY()); }
                @Override public void mouseDragged(MouseEvent event) { select(event.getY()); }
            };
            addMouseListener(selection);
            addMouseMotionListener(selection);
        }

        private void select(int y) {
            hue = clamp(y / (float) Math.max(1, getHeight() - 1));
            square.invalidateGradient();
            updateChannels();
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            for (int y = 0; y < getHeight(); y++) {
                graphics.setColor(Color.getHSBColor(y / (float) Math.max(1, getHeight() - 1), 1f, 1f));
                graphics.drawLine(0, y, getWidth(), y);
            }
            int marker = Math.round(hue * (getHeight() - 1));
            graphics.setColor(Color.WHITE);
            graphics.drawRect(0, marker - 2, getWidth() - 1, 4);
        }
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
