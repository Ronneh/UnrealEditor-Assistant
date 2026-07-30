import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorHelpImporterTest {
    @TempDir Path temp;

    @Test
    void importsNestedTocAndCleansLegacyHtml() throws Exception {
        Path source = Files.createDirectories(temp.resolve("source"));
        Path ued = Files.createDirectories(source.resolve("UED"));
        Files.writeString(source.resolve("help.hhc"), """
                <html><body><ul>
                  <li><object><param name="Name" value="Level Editing"></object><ul>
                    <li><object><param name="Name" value="Résumé"><param name="Local" value="UED/My%20Page.htm"></object></li>
                  </ul></li>
                </ul></body></html>
                """);
        Charset windows1252 = Charset.forName("windows-1252");
        Files.write(ued.resolve("My Page.htm"), """
                <html><head><meta charset="windows-1252"><title>Café</title>
                <script>bad()</script></head><body onload="bad()"><h1>Résumé</h1>
                <div class="advert-banner">Buy now</div><p>Crème brûlée tutorial.</p>
                <a href="Other.htm">Broken</a><a href="https://example.org">External</a>
                <a href="unreal://example.unr">Unreal protocol</a>
                <a href="javascript:bad()">Script link</a>
                <img src="pics/My%20Image.gif"><iframe src="nav.htm"></iframe></body></html>
                """.getBytes(windows1252));
        Path pics = Files.createDirectories(ued.resolve("pics"));
        Files.write(pics.resolve("My Image.gif"), new byte[] {'G', 'I', 'F'});

        EditorHelpImporter.ImportResult result =
                new EditorHelpImporter().importHelp(source, temp.resolve("pack"));

        assertEquals(2, result.report().tocEntries);
        assertEquals(1, result.report().importedTutorials);
        assertEquals(1, result.report().brokenLinks.size());
        assertEquals(0, result.report().missingImages.size());
        var tutorial = result.catalog().tutorials().get(0);
        assertEquals("Café", tutorial.title());
        assertTrue(tutorial.text().contains("Crème brûlée"));
        assertEquals(java.util.List.of("Level Editing", "Résumé"), tutorial.categoryPath());
        String clean = Files.readString(temp.resolve("pack").resolve(tutorial.contentFile()));
        assertFalse(clean.contains("<script"));
        assertFalse(clean.contains("<iframe"));
        assertFalse(clean.contains("Buy now"));
        assertFalse(clean.contains("onload"));
        assertFalse(clean.contains("javascript:bad"));
        assertTrue(Files.exists(temp.resolve("pack/assets/UED/pics/My Image.gif")));
    }

    @Test
    void reportsMissingTargetsImagesAndDuplicateSources() throws Exception {
        Path source = Files.createDirectories(temp.resolve("source2"));
        Files.writeString(source.resolve("help.hhc"), """
                <html><body><ul>
                  <li><object><param name="Name" value="One"><param name="Local" value="page.htm"></object></li>
                  <li><object><param name="Name" value="Again"><param name="Local" value="./PAGE.htm"></object></li>
                  <li><object><param name="Name" value="Gone"><param name="Local" value="missing.htm"></object></li>
                </ul></body></html>
                """);
        Files.writeString(source.resolve("page.htm"),
                "<html><body><h1>Text</h1><p>Enough tutorial content.</p><img src='../escape.gif'></body></html>");
        Files.writeString(source.resolve("orphan.htm"), "<html><body>Orphan</body></html>");

        EditorHelpImporter.ImportResult result =
                new EditorHelpImporter().importHelp(source, temp.resolve("pack2"));

        assertEquals(3, result.report().tocEntries);
        assertEquals(1, result.report().importedTutorials);
        assertEquals(1, result.report().duplicates.size());
        assertEquals(1, result.report().missingTocFiles.size());
        assertEquals(1, result.report().missingImages.size());
        assertEquals(1, result.report().skippedPages.size());
        assertEquals(2, new ObjectMapper().readTree(temp.resolve("pack2/catalog.json").toFile())
                .path("schemaVersion").asInt());
        assertEquals("en", new ObjectMapper().readTree(temp.resolve("pack2/manifest.json").toFile())
                .path("language").asText());
    }

    @Test
    void normalizesEncodedPathsWithoutTreatingPlusAsSpace() {
        assertEquals("folder/My Page+A.htm",
                EditorHelpImporter.normalizeReference("./folder/My%20Page+A.htm?x=1#part"));
        assertEquals("../shared/page.htm", EditorHelpImporter.normalizeReference("../shared/page.htm"));
    }
}
