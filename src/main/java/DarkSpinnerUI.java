import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicSpinnerUI;

/** Spinner UI with dark, theme-consistent increment and decrement buttons. */
public final class DarkSpinnerUI extends BasicSpinnerUI {
    public static ComponentUI createUI(JComponent component) {
        return new DarkSpinnerUI();
    }

    @Override protected Component createNextButton() {
        return arrowButton(true);
    }

    @Override protected Component createPreviousButton() {
        return arrowButton(false);
    }

    private JButton arrowButton(boolean up) {
        JButton button = new JButton(new ArrowIcon(up));
        button.setBackground(AssistantTheme.PANEL_ALT);
        button.setBorder(BorderFactory.createLineBorder(AssistantTheme.BORDER));
        button.setFocusable(false);
        installNextButtonListenersIfNeeded(button, up);
        return button;
    }

    private void installNextButtonListenersIfNeeded(JButton button, boolean up) {
        if (up) installNextButtonListeners(button);
        else installPreviousButtonListeners(button);
    }

    private static final class ArrowIcon implements Icon {
        private final boolean up;

        ArrowIcon(boolean up) { this.up = up; }

        @Override public int getIconWidth() { return 8; }
        @Override public int getIconHeight() { return 5; }

        @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(component.isEnabled() ? AssistantTheme.TEXT : AssistantTheme.MUTED);
            int[] xs = { x, x + 8, x + 4 };
            int[] ys = up ? new int[] { y + 5, y + 5, y } : new int[] { y, y, y + 5 };
            g.fillPolygon(xs, ys, 3);
            g.dispose();
        }
    }
}
