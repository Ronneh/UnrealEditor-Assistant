import java.util.prefs.Preferences;
import javax.swing.JSplitPane;

/** Persists user-adjusted split-pane divider positions between application runs. */
public final class SplitPaneState {
    private SplitPaneState() { }

    public static void install(JSplitPane splitPane, Class<?> owner, String key) {
        Preferences preferences = Preferences.userNodeForPackage(owner);
        int savedLocation = preferences.getInt("divider." + key, -1);
        if (savedLocation >= 0) splitPane.setDividerLocation(savedLocation);
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            int location = splitPane.getDividerLocation();
            if (location >= 0) preferences.putInt("divider." + key, location);
        });
    }
}
