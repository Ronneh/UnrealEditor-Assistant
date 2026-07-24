import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

/** Single-window launcher and workspace for Unreal Editor 2 utilities. */
public final class UnrealEditor2Assistant {
    private static final String HOME = "home";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu");

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JPanel navigation = new JPanel();
    private final Map<String, JButton> navigationButtons = new LinkedHashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            AssistantTheme.install();
            new UnrealEditor2Assistant().show();
        });
    }

    private void show() {
        JFrame frame = new JFrame("Unreal Editor 2 Assistant");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(createContent());
        frame.setMinimumSize(new Dimension(1080, 720));
        frame.setSize(1280, 820);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel createContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AssistantTheme.BACKGROUND);
        navigation.setLayout(new BoxLayout(navigation, BoxLayout.X_AXIS));
        navigation.setBackground(AssistantTheme.HEADER);
        navigation.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));

        registerApp(HOME, "\u2302", "Home", createHomePanel());
        registerApp("optimizer", "\u25a6", "Brush", new BrushOptimizer().createContent());
        registerApp("double", "\u21c9", "Double", new MapDoublerPanel());
        registerApp("resizer", "\u2922", "Resize", new ImageResizerPanel());
        registerApp("screenshots", "\u25a3", "Screens", new ScreenshotMakerPanel());
        addPlaceholder("+");
        addPlaceholder("+");
        addPlaceholder("+");
        navigation.add(Box.createHorizontalGlue());

        root.add(navigation, BorderLayout.NORTH);
        root.add(cards, BorderLayout.CENTER);
        showApp(HOME);
        removeButtonFocusPainting(root);
        return root;
    }

    private void removeButtonFocusPainting(Component component) {
        if (component instanceof AbstractButton button) button.setFocusPainted(false);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) removeButtonFocusPainting(child);
        }
    }

    private void registerApp(String id, String icon, String label, JPanel content) {
        cards.add(content, id);
        JButton button = navButton(icon, label);
        button.setToolTipText(label);
        button.addActionListener(event -> showApp(id));
        navigationButtons.put(id, button);
        navigation.add(button);
        navigation.add(Box.createHorizontalStrut(6));
    }

    private void addPlaceholder(String icon) {
        JButton button = navButton(icon, "Future tool");
        button.setEnabled(false);
        button.setToolTipText("Reserved for a future tool");
        navigation.add(button);
        navigation.add(Box.createHorizontalStrut(6));
    }

    private JButton navButton(String icon, String label) {
        JButton button = new JButton("<html><div style='text-align:center;font-size:16px'>"
                + icon + "</div><div style='font-size:9px'>" + label + "</div></html>");
        button.setPreferredSize(new Dimension(76, 50));
        button.setMaximumSize(new Dimension(76, 50));
        button.setMinimumSize(new Dimension(76, 50));
        button.setFocusable(false);
        button.setForeground(AssistantTheme.TEXT);
        button.setBackground(AssistantTheme.HEADER);
        button.setBorder(BorderFactory.createLineBorder(AssistantTheme.BORDER));
        return button;
    }

    private void showApp(String id) {
        cardLayout.show(cards, id);
        navigationButtons.forEach((appId, button) ->
                button.setBackground(appId.equals(id) ? AssistantTheme.ACCENT_DARK : AssistantTheme.HEADER));
    }

    private JPanel createHomePanel() {
        JPanel home = new JPanel(new BorderLayout(18, 18));
        home.setBackground(AssistantTheme.BACKGROUND);
        home.setBorder(BorderFactory.createEmptyBorder(24, 30, 24, 30));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JPanel welcome = new JPanel(new BorderLayout());
        welcome.setOpaque(false);
        JLabel heading = new JLabel("Unreal Editor 2 Assistant");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 28f));
        JLabel subtitle = new JLabel("A focused workspace for mapping and texture tools.");
        subtitle.setForeground(AssistantTheme.MUTED);
        subtitle.setBorder(BorderFactory.createEmptyBorder(5, 1, 0, 0));
        welcome.add(heading, BorderLayout.NORTH);
        welcome.add(subtitle, BorderLayout.CENTER);
        top.add(welcome, BorderLayout.WEST);
        top.add(createCompactClock(), BorderLayout.EAST);
        home.add(top, BorderLayout.NORTH);

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.setLayout(new BoxLayout(tools, BoxLayout.Y_AXIS));
        tools.add(toolCard("Brush Optimizer", "Make your off-grid brush on-grid!", "Open", "optimizer"));
        tools.add(Box.createVerticalStrut(10));
        tools.add(toolCard("Double", "Double your map.", "Open", "double"));
        tools.add(Box.createVerticalStrut(10));
        tools.add(toolCard("Screenshot Maker", "Create a screenshot for your map.", "Open", "screenshots"));
        tools.add(Box.createVerticalStrut(10));
        tools.add(toolCard("Image Resizer", "Resize and tune textures to use for mapping.", "Open", "resizer"));
        tools.add(Box.createVerticalGlue());

        JPanel toolsCard = AssistantTheme.card(new BorderLayout(0, 14));
        JLabel title = new JLabel("Available tools");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 19f));
        toolsCard.add(title, BorderLayout.NORTH);
        toolsCard.add(tools, BorderLayout.CENTER);
        home.add(toolsCard, BorderLayout.CENTER);
        return home;
    }

    private JPanel toolCard(String title, String description, String action, String id) {
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setBackground(AssistantTheme.PANEL_ALT);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AssistantTheme.BORDER),
                BorderFactory.createEmptyBorder(16, 18, 16, 14)));
        JLabel text = new JLabel("<html><b>" + title + "</b><br><span style='color:#9ca7b8'>"
                + description + "</span></html>");
        text.setFont(text.getFont().deriveFont(15f));
        row.add(text, BorderLayout.CENTER);
        JButton open = new JButton(action);
        open.setPreferredSize(new Dimension(100, 36));
        open.addActionListener(event -> showApp(id));
        row.add(open, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
        return row;
    }

    private JPanel createCompactClock() {
        JPanel clockPanel = AssistantTheme.card(new BorderLayout(10, 0));
        clockPanel.setPreferredSize(new Dimension(315, 126));
        AnalogClock clock = new AnalogClock();
        clockPanel.add(clock, BorderLayout.WEST);
        JLabel digital = new JLabel("", SwingConstants.CENTER);
        digital.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
        clockPanel.add(digital, BorderLayout.CENTER);
        Timer timer = new Timer(250, event -> {
            LocalDateTime now = LocalDateTime.now();
            clock.time = now;
            clock.repaint();
            digital.setText("<html><div style='text-align:center'>" + now.format(TIME)
                    + "</div><div style='font-family:sans-serif;font-size:9px;color:#9ca7b8'>"
                    + now.format(DATE) + "</div></html>");
        });
        timer.setInitialDelay(0);
        timer.start();
        return clockPanel;
    }

    private static final class AnalogClock extends JPanel {
        private LocalDateTime time = LocalDateTime.now();
        AnalogClock() {
            setOpaque(false);
            setPreferredSize(new Dimension(92, 92));
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int r = Math.min(getWidth(), getHeight()) / 2 - 5;
            int cx = getWidth() / 2, cy = getHeight() / 2;
            g.setColor(AssistantTheme.PANEL_ALT);
            g.fillOval(cx-r, cy-r, r*2, r*2);
            g.setColor(AssistantTheme.TEXT);
            g.drawOval(cx-r, cy-r, r*2, r*2);
            for (int i=0; i<12; i++) {
                double a=Math.toRadians(i*30-90);
                g.drawLine(cx+(int)(Math.cos(a)*(r-5)), cy+(int)(Math.sin(a)*(r-5)),
                        cx+(int)(Math.cos(a)*(r-10)), cy+(int)(Math.sin(a)*(r-10)));
            }
            hand(g,cx,cy,r*.48,(time.getHour()%12+time.getMinute()/60.0)*30,4,AssistantTheme.TEXT);
            hand(g,cx,cy,r*.68,(time.getMinute()+time.getSecond()/60.0)*6,3,AssistantTheme.ACCENT);
            hand(g,cx,cy,r*.72,time.getSecond()*6,1,new Color(230,86,86));
            g.dispose();
        }
        private void hand(Graphics2D g,int x,int y,double length,double degrees,int width,Color color) {
            double a=Math.toRadians(degrees-90);
            g.setStroke(new java.awt.BasicStroke(width,1,1));
            g.setColor(color);
            g.drawLine(x,y,x+(int)(Math.cos(a)*length),y+(int)(Math.sin(a)*length));
        }
    }
}
