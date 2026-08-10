import java.awt.Component;
import java.nio.file.Path;
import javax.swing.JEditorPane;
import javax.swing.JPanel;

/** Optional SPI implemented only by internal tutorial-authoring builds. */
public interface EditorHelpAuthoringExtension {
    void install(Host host);
    default void documentChanged(Path articlePath) {}

    interface Host {
        Component parent();
        JEditorPane article();
        JPanel toolbar();
        Path currentArticlePath();
        Path authoringSavePath();
        void reloadCurrentArticle();
        void setStatus(String text);
        void setNavigationEnabled(boolean enabled);
    }
}
