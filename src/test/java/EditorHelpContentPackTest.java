import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorHelpContentPackTest {
    @TempDir Path temp;

    @Test
    void validatesPackAndBuildsEnglishSearchIndex() throws Exception {
        Path packDirectory = createPack();
        EditorHelpContentPack pack = new EditorHelpContentPackLoader().load(packDirectory);

        assertEquals("en", pack.language());
        assertEquals(2, pack.schemaVersion());
        assertEquals(2, pack.documents().size());
        assertTrue(Files.isRegularFile(pack.documents().get(0).resolveContent(pack.root())));
        String brushPage = Files.readString(packDirectory.resolve(pack.documents().get(0).contentFile()));
        assertTrue(brushPage.contains("https://editor-help.local/tutorial/"));

        Path index = temp.resolve("index");
        EditorHelpSearch.buildIndex(pack, index);
        try (EditorHelpSearch search = new EditorHelpSearch(index)) {
            assertEquals(2, search.documentCount());
            var brushResults = search.search("brush geometry", 5);
            assertFalse(brushResults.isEmpty());
            assertEquals("Brush Basics", brushResults.get(0).title());
            assertTrue(brushResults.get(0).excerpt().toLowerCase().contains("brush"));

            var moverResults = search.search("moving doors", 5);
            assertFalse(moverResults.isEmpty());
            assertEquals("Mover Setup", moverResults.get(0).title());
        }
    }

    @Test
    void rejectsCatalogTamperingAndNonEnglishManifest() throws Exception {
        Path tamperedPack = createPack();
        Files.writeString(tamperedPack.resolve("catalog.json"), "\n", java.nio.file.StandardOpenOption.APPEND);
        IOException checksum = assertThrows(IOException.class,
                () -> new EditorHelpContentPackLoader().load(tamperedPack));
        assertTrue(checksum.getMessage().contains("SHA-256 mismatch"));

        Path pack = createPack();
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode manifest = (ObjectNode) mapper.readTree(pack.resolve("manifest.json").toFile());
        manifest.put("language", "de");
        mapper.writerWithDefaultPrettyPrinter().writeValue(pack.resolve("manifest.json").toFile(), manifest);
        Path nonEnglishPack = pack;
        IOException language = assertThrows(IOException.class,
                () -> new EditorHelpContentPackLoader().load(nonEnglishPack));
        assertTrue(language.getMessage().contains("Only English"));
    }

    private Path createPack() throws Exception {
        Path source = Files.createDirectories(temp.resolve("source-" + System.nanoTime()));
        Files.writeString(source.resolve("help.hhc"), """
                <html><body><ul>
                  <li><object><param name="Name" value="Geometry"></object><ul>
                    <li><object><param name="Name" value="Brush Basics"><param name="Local" value="brush.htm"></object></li>
                  </ul></li>
                  <li><object><param name="Name" value="Actors"></object><ul>
                    <li><object><param name="Name" value="Mover Setup"><param name="Local" value="mover.htm"></object></li>
                  </ul></li>
                </ul></body></html>
                """);
        Files.writeString(source.resolve("brush.htm"), """
                <html><head><title>Brush Basics</title></head><body><h1>Brush Geometry</h1>
                <p>Additive and subtractive brushes create BSP geometry.</p>
                <a href="mover.htm">Continue with movers</a></body></html>
                """);
        Files.writeString(source.resolve("mover.htm"), """
                <html><head><title>Mover Setup</title></head><body><h1>Moving Doors</h1>
                <p>A mover can create moving doors and elevators.</p></body></html>
                """);
        Path pack = temp.resolve("pack-" + System.nanoTime());
        new EditorHelpImporter().importHelp(source, pack);
        return pack;
    }
}
