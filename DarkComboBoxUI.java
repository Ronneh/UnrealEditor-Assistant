import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicComboBoxUI;

/** Combo-box UI with a theme-colored, clearly visible drop-down arrow. */
public final class DarkComboBoxUI extends BasicComboBoxUI {
    public static ComponentUI createUI(JComponent component) {
        return new DarkComboBoxUI();
    }

    @Override protected JButton createArrowButton() {
        JButton button = new JButton(new ArrowIcon());
        button.setName("ComboBox.arrowButton");
        button.setBackground(AssistantTheme.PANEL_ALT);
        button.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, AssistantTheme.BORDER));
        button.setFocusable(false);
        return button;
    }

    private static final class ArrowIcon implements Icon {
        @Override public int getIconWidth() { return 10; }
        @Override public int getIconHeight() { return 7; }

        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(component.isEnabled() ? AssistantTheme.TEXT : AssistantTheme.MUTED);
            g.fillPolygon(new int[] { x, x + 10, x + 5 }, new int[] { y, y, y + 6 }, 3);
            g.dispose();
        }
    }
}
