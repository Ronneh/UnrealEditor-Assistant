import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves the shared application data directory and migrates the legacy name. */
public final class AppStorage {
    private static final String CURRENT_WINDOWS_DIRECTORY = "MappingAssistant";
    private static final String LEGACY_WINDOWS_DIRECTORY = "UnrealEditor2Assistant";
    private static final String CURRENT_HOME_DIRECTORY = ".mapping-assistant";
    private static final String LEGACY_HOME_DIRECTORY = ".unreal-editor-2-assistant";

    private AppStorage() { }

    public static Path root() {
        String localAppData = System.getenv("LOCALAPPDATA");
        Path parent;
        Path current;
        Path legacy;
        if (localAppData == null || localAppData.isBlank()) {
            parent = Path.of(System.getProperty("user.home"));
            current = parent.resolve(CURRENT_HOME_DIRECTORY);
            legacy = parent.resolve(LEGACY_HOME_DIRECTORY);
        } else {
            parent = Path.of(localAppData);
            current = parent.resolve(CURRENT_WINDOWS_DIRECTORY);
            legacy = parent.resolve(LEGACY_WINDOWS_DIRECTORY);
        }
        return migrateLegacyDirectory(current, legacy);
    }

    static Path migrateLegacyDirectory(Path current, Path legacy) {
        if (Files.notExists(current) && Files.isDirectory(legacy)) {
            try {
                return Files.move(legacy, current);
            } catch (IOException | SecurityException ignored) {
                return legacy;
            }
        }
        return current;
    }
}
