import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AppStorageTest {
    @Test
    void legacyDirectoryIsMigratedToCurrentName() throws Exception {
        Path parent = Files.createTempDirectory("mapping-assistant-storage");
        Path legacy = Files.createDirectory(parent.resolve("legacy"));
        Files.writeString(legacy.resolve("notes.txt"), "preserved");
        Path current = parent.resolve("current");

        Path resolved = AppStorage.migrateLegacyDirectory(current, legacy);

        assertEquals(current, resolved);
        assertTrue(Files.exists(current.resolve("notes.txt")));
        assertFalse(Files.exists(legacy));
    }

    @Test
    void currentDirectoryWinsWhenBothNamesExist() throws Exception {
        Path parent = Files.createTempDirectory("mapping-assistant-storage");
        Path legacy = Files.createDirectory(parent.resolve("legacy"));
        Path current = Files.createDirectory(parent.resolve("current"));

        Path resolved = AppStorage.migrateLegacyDirectory(current, legacy);

        assertEquals(current, resolved);
        assertTrue(Files.isDirectory(legacy));
    }
}
