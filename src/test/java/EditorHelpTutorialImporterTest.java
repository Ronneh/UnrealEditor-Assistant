import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorHelpTutorialImporterTest {
    @TempDir Path temp;

    @Test
    void wrapsLongPdfLinesAndLongIndividualTokens() {
        String formatted = EditorHelpTutorialImporter.formatPdfText(
                "This is a deliberately long PDF sentence containing several words that must wrap "
                        + "before it can force the Swing HTML container beyond the viewport edge. "
                        + "https://example.test/" + "a".repeat(100));
        assertTrue(formatted.contains("<br>"));
        for (String line : formatted.split("<br>")) assertTrue(line.length() <= 78, line);
    }

    @Test
    void importsHtmlImagesAndNewTutorialsCategoryThenUpdatesExistingSource() throws Exception {
        Path pack = createPack();
        Path source = Files.createDirectories(temp.resolve("tutorial"));
        Files.createDirectories(source.resolve("images"));
        ImageIO.write(new BufferedImage(12, 8, BufferedImage.TYPE_INT_RGB), "png",
                source.resolve("images/step.png").toFile());
        Path html = source.resolve("door.html");
        Files.writeString(html, """
                <html><head><title>Mover Door</title></head><body>
                <h1>Create a Door</h1><p>First version</p><img src="images/step.png">
                </body></html>
                """);

        EditorHelpTutorialImporter importer = new EditorHelpTutorialImporter();
        var first = importer.importTutorial(html, pack);
        assertEquals("Mover Door", first.title());
        assertEquals(1, first.copiedImages());
        assertTrue(!first.updated());

        EditorHelpContentPack loaded = new EditorHelpContentPackLoader().load(pack);
        assertEquals(2, loaded.documents().size());
        var imported = loaded.documents().stream().filter(d -> d.id().equals(first.id())).findFirst().orElseThrow();
        assertEquals(java.util.List.of("New Tutorials", "Mover Door"), imported.categoryPath());
        assertTrue(Files.isRegularFile(imported.resolveContent(pack).getParent().resolve("images/step.png")));
        JsonNode catalog = new ObjectMapper().readTree(pack.resolve("catalog.json").toFile());
        assertEquals("New Tutorials", catalog.path("tableOfContents").get(0).path("title").asText());

        Files.writeString(html, "<html><head><title>Mover Door Updated</title></head><body><h1>New</h1></body></html>");
        var second = importer.importTutorial(html, pack);
        assertTrue(second.updated());
        assertEquals(first.id(), second.id());
        assertEquals(2, new EditorHelpContentPackLoader().load(pack).documents().size());
    }

    @Test
    void importsPdfAsSearchablePageImages() throws Exception {
        Path pack = createPack();
        Path pdfFile = temp.resolve("Mover Reference.pdf");
        try (PDDocument pdf = new PDDocument()) {
            pdf.getDocumentInformation().setTitle("Mover PDF Guide");
            PDPage page = new PDPage();
            pdf.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 16);
                content.newLineAtOffset(72, 700);
                content.showText("Create a searchable mover door");
                content.endText();
                BufferedImage screenshot = new BufferedImage(1200, 600, BufferedImage.TYPE_INT_RGB);
                var screenshotGraphics = screenshot.createGraphics();
                screenshotGraphics.fillRect(0, 0, 1200, 600);
                screenshotGraphics.dispose();
                content.drawImage(LosslessFactory.createFromImage(pdf, screenshot), 72, 500, 480, 240);
            }
            pdf.save(pdfFile.toFile());
        }

        var result = new EditorHelpTutorialImporter().importTutorial(pdfFile, pack);
        assertEquals("Mover PDF Guide", result.title());
        assertEquals(1, result.copiedImages());
        EditorHelpContentPack loaded = new EditorHelpContentPackLoader().load(pack);
        var tutorial = loaded.documents().stream().filter(d -> d.id().equals(result.id())).findFirst().orElseThrow();
        assertTrue(tutorial.text().contains("searchable mover door"));
        Path importedHtml = tutorial.resolveContent(pack);
        String importedPage = Files.readString(importedHtml);
        assertTrue(importedPage.contains("images/image-0001.png"));
        assertTrue(importedPage.contains("class=\"help-pdf-import-image\""));
        assertTrue(importedPage.contains("<p class=\"help-pdf-paragraph\">"));
        assertTrue(importedPage.contains("Create a searchable mover door"));
        assertTrue(!importedPage.contains("Original page"));
        assertTrue(!importedPage.contains("Page 1"));
        assertTrue(!importedPage.contains("help-pdf-content"));
        Path importedImage = importedHtml.getParent().resolve("images/image-0001.png");
        assertTrue(Files.size(importedImage) > 0);
        assertEquals(760, ImageIO.read(importedImage.toFile()).getWidth());
    }

    private Path createPack() throws Exception {
        Path source = Files.createDirectories(temp.resolve("legacy"));
        Files.writeString(source.resolve("help.hhc"), """
                <html><body><ul><li><object><param name="Name" value="Basics"></object><ul>
                <li><object><param name="Name" value="Brushes"><param name="Local" value="brush.htm"></object></li>
                </ul></li></ul></body></html>
                """);
        Files.writeString(source.resolve("brush.htm"),
                "<html><head><title>Brushes</title></head><body><h1>Brushes</h1></body></html>");
        Path pack = temp.resolve("pack");
        new EditorHelpImporter().importHelp(source, pack);
        return pack;
    }
}
