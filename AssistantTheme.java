import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.Border;

/** Shared colors and Swing defaults for the assistant's dark theme. */
public final class AssistantTheme {
    /** Locale detected before Swing is switched to its English UI resources. */
    public static final Locale USER_LOCALE = Locale.getDefault();
    public static final Color BACKGROUND = new Color(20, 24, 31);
    public static final Color HEADER = new Color(25, 31, 41);
    public static final Color PANEL = new Color(30, 36, 46);
    public static final Color PANEL_ALT = new Color(36, 43, 55);
    public static final Color BORDER = new Color(58, 67, 82);
    public static final Color TEXT = new Color(232, 237, 244);
    public static final Color MUTED = new Color(156, 167, 184);
    public static final Color ACCENT = new Color(66, 145, 235);
    public static final Color ACCENT_DARK = new Color(39, 87, 139);

    private AssistantTheme() { }

    public static void install() {
        Locale.setDefault(Locale.ENGLISH);
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }
        UIManager.put("Panel.background", PANEL);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.background", new Color(0, 0, 0, 0));
        UIManager.put("Button.background", PANEL_ALT);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.select", ACCENT_DARK);
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
        UIManager.put("TextArea.background", new Color(17, 21, 27));
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", TEXT);
        UIManager.put("TextField.background", new Color(17, 21, 27));
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("TextPane.background", new Color(17, 21, 27));
        UIManager.put("TextPane.foreground", TEXT);
        UIManager.put("ComboBox.background", PANEL_ALT);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("CheckBox.background", PANEL);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("Viewport.background", new Color(17, 21, 27));
        UIManager.put("SplitPane.background", BORDER);
        UIManager.put("SplitPane.dividerSize", 7);
        UIManager.put("SplitPaneDivider.draggingColor", ACCENT_DARK);
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollBar.background", PANEL);
        UIManager.put("ScrollBar.foreground", MUTED);
        UIManager.put("ScrollBar.track", new Color(18, 22, 29));
        UIManager.put("ScrollBar.trackHighlight", PANEL_ALT);
        UIManager.put("ScrollBar.thumb", BORDER);
        UIManager.put("ScrollBar.thumbDarkShadow", new Color(38, 45, 57));
        UIManager.put("ScrollBar.thumbHighlight", new Color(77, 88, 105));
        UIManager.put("ScrollBar.thumbShadow", new Color(45, 53, 66));
        UIManager.put("ScrollBar.width", 13);
        UIManager.put("TabbedPane.background", PANEL);
        UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("Slider.background", PANEL);
        UIManager.put("Slider.foreground", TEXT);
        UIManager.put("TitledBorder.titleColor", MUTED);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("FileChooser.background", PANEL);
        UIManager.put("FileChooser.foreground", TEXT);
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    }

    public static JPanel card(BorderLayout layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        return panel;
    }

    public static Border titled(String title) {
        return BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER), title);
    }
}
