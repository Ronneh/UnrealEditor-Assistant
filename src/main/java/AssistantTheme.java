import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JSplitPane;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/** Shared colors and Swing defaults for the dark theme. */
public final class AssistantTheme {
    /** Locale detected before Swing is switched to its English UI resources. */
    public static final Locale USER_LOCALE = Locale.getDefault();
    public static final Color BACKGROUND = new Color(20, 24, 31);
    public static final Color HEADER = new Color(25, 31, 41);
    public static final Color PANEL = new Color(30, 36, 46);
    public static final Color PANEL_ALT = new Color(36, 43, 55);
    public static final Color CODE_BACKGROUND = new Color(12, 15, 20);
    public static final Color SCROLL_THUMB = new Color(40, 47, 59);
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
        Object[] defaultKeys = UIManager.getDefaults().keySet().toArray();
        for (Object key : defaultKeys) {
            Object value = UIManager.get(key);
            if (value instanceof Font font) {
                UIManager.put(key, new FontUIResource("Verdana", font.getStyle(), font.getSize()));
            }
        }
        UIManager.put("Panel.background", PANEL);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.background", new Color(0, 0, 0, 0));
        UIManager.put("Button.background", PANEL_ALT);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.select", ACCENT_DARK);
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
        UIManager.put("TextArea.background", CODE_BACKGROUND);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("TextArea.caretForeground", TEXT);
        UIManager.put("TextArea.border", BorderFactory.createEmptyBorder());
        UIManager.put("TextField.background", new Color(17, 21, 27));
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("TextField.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("FormattedTextField.background", new Color(17, 21, 27));
        UIManager.put("FormattedTextField.foreground", TEXT);
        UIManager.put("FormattedTextField.caretForeground", TEXT);
        UIManager.put("FormattedTextField.selectionBackground", ACCENT_DARK);
        UIManager.put("FormattedTextField.selectionForeground", TEXT);
        UIManager.put("FormattedTextField.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("TextPane.background", CODE_BACKGROUND);
        UIManager.put("TextPane.foreground", TEXT);
        UIManager.put("TextPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("EditorPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("ComboBoxUI", DarkComboBoxUI.class.getName());
        UIManager.put("ComboBox.background", PANEL_ALT);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", ACCENT_DARK);
        UIManager.put("ComboBox.selectionForeground", TEXT);
        UIManager.put("ComboBox.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("SpinnerUI", DarkSpinnerUI.class.getName());
        UIManager.put("Spinner.background", PANEL_ALT);
        UIManager.put("Spinner.foreground", TEXT);
        UIManager.put("Spinner.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("Spinner.arrowButtonBackground", PANEL_ALT);
        UIManager.put("Spinner.arrowButtonForeground", TEXT);
        UIManager.put("Spinner.arrowButtonBorder", BorderFactory.createLineBorder(BORDER));
        UIManager.put("Spinner.editorBorderPainted", false);
        UIManager.put("CheckBox.background", PANEL);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("RadioButton.background", PANEL);
        UIManager.put("RadioButton.foreground", TEXT);
        UIManager.put("RadioButton.disabledText", MUTED);
        UIManager.put("RadioButton.select", ACCENT_DARK);
        UIManager.put("ScrollPane.background", PANEL);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollPane.viewportBorder", BorderFactory.createEmptyBorder());
        UIManager.put("Viewport.background", CODE_BACKGROUND);
        UIManager.put("SplitPaneUI", "javax.swing.plaf.basic.BasicSplitPaneUI");
        UIManager.put("SplitPane.background", BORDER);
        UIManager.put("SplitPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("SplitPane.dividerSize", 7);
        UIManager.put("SplitPaneDivider.draggingColor", ACCENT_DARK);
        UIManager.put("SplitPaneDivider.border", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollBarUI", "javax.swing.plaf.basic.BasicScrollBarUI");
        UIManager.put("ScrollBar.background", PANEL);
        UIManager.put("ScrollBar.foreground", MUTED);
        UIManager.put("ScrollBar.track", new Color(18, 22, 29));
        UIManager.put("ScrollBar.trackHighlight", PANEL_ALT);
        UIManager.put("ScrollBar.thumb", SCROLL_THUMB);
        UIManager.put("ScrollBar.thumbDarkShadow", SCROLL_THUMB);
        UIManager.put("ScrollBar.thumbHighlight", SCROLL_THUMB);
        UIManager.put("ScrollBar.thumbShadow", SCROLL_THUMB);
        UIManager.put("ScrollBar.buttonBackground", PANEL_ALT);
        UIManager.put("ScrollBar.buttonDarkShadow", BORDER);
        UIManager.put("ScrollBar.buttonHighlight", PANEL_ALT);
        UIManager.put("ScrollBar.buttonShadow", BORDER);
        UIManager.put("ScrollBar.width", 13);
        UIManager.put("TabbedPane.background", PANEL);
        UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("TabbedPane.selected", CODE_BACKGROUND);
        UIManager.put("TabbedPane.selectedBackground", CODE_BACKGROUND);
        UIManager.put("TabbedPane.contentAreaColor", CODE_BACKGROUND);
        UIManager.put("TabbedPane.focus", CODE_BACKGROUND);
        UIManager.put("TabbedPane.selectHighlight", BORDER);
        UIManager.put("TabbedPane.light", BORDER);
        UIManager.put("TabbedPane.highlight", BORDER);
        UIManager.put("TabbedPane.shadow", BACKGROUND);
        UIManager.put("TabbedPane.darkShadow", BACKGROUND);
        UIManager.put("Slider.background", PANEL);
        UIManager.put("Slider.foreground", TEXT);
        UIManager.put("TitledBorder.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("TitledBorder.titleColor", MUTED);
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("FileChooser.background", PANEL);
        UIManager.put("FileChooser.foreground", TEXT);
        UIManager.put("defaultFont", new FontUIResource("Verdana", Font.PLAIN, 12));
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

    public static void styleSplitPane(JSplitPane splitPane) {
        splitPane.setUI(new BasicSplitPaneUI());
        splitPane.setContinuousLayout(true);
        splitPane.setDividerSize(7);
        splitPane.setBackground(BORDER);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        if (splitPane.getUI() instanceof BasicSplitPaneUI ui) {
            ui.getDivider().setBackground(PANEL_ALT);
            ui.getDivider().setBorder(BorderFactory.createLineBorder(BORDER));
        }
    }
}
