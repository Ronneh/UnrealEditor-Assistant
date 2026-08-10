import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

/** Adds a user-authored HTML page and its local images to an Editor Help content pack. */
public final class EditorHelpTutorialImporter {
    private static final String CATEGORY = "New Tutorials";
    private static final int PDF_IMAGE_MAX_WIDTH = 760;
    private final ObjectMapper mapper = new ObjectMapper();

    public ImportResult importTutorial(Path htmlFile, Path packRoot) throws IOException {
        Path source = htmlFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) throw new IOException("Tutorial file not found: " + source);
        if (isPdf(source)) return importPdf(source, packRoot);
        if (!isHtml(source)) throw new IOException("Select an HTML or PDF tutorial file.");
        return importHtml(source, packRoot, CATEGORY + "/" + source.getFileName(), null);
    }

    private ImportResult importPdf(Path pdfFile, Path packRoot) throws IOException {
        Path temporary = Files.createTempDirectory("editor-guide-pdf-");
        try (PDDocument pdf = Loader.loadPDF(pdfFile.toFile())) {
            if (pdf.isEncrypted() && !pdf.getCurrentAccessPermission().canExtractContent()) {
                throw new IOException("This PDF does not permit content extraction.");
            }
            if (pdf.getNumberOfPages() == 0) throw new IOException("The PDF contains no pages.");
            String title = pdf.getDocumentInformation().getTitle();
            if (title == null || title.isBlank()) title = fileStem(pdfFile.getFileName().toString());
            Path images = Files.createDirectories(temporary.resolve("images"));
            List<PdfTutorialContentExtractor.PageContent> pages =
                    new PdfTutorialContentExtractor().extract(pdf);
            StringBuilder searchableText = new StringBuilder();
            StringBuilder html = new StringBuilder("<html><head><meta charset=\"UTF-8\"><title>")
                    .append(escapeHtml(title)).append("</title></head><body><h1>")
                    .append(escapeHtml(title)).append("</h1>");
            int imageNumber = 0;
            for (PdfTutorialContentExtractor.PageContent page : pages) {
                for (PdfTutorialContentExtractor.ContentBlock block : page.blocks()) {
                    if (block instanceof PdfTutorialContentExtractor.TextBlock textBlock) {
                        searchableText.append(textBlock.text()).append(' ');
                        html.append("<p class=\"help-pdf-paragraph\">")
                                .append(formatPdfText(textBlock.text())).append("</p>");
                    } else if (block instanceof PdfTutorialContentExtractor.ImageBlock imageBlock) {
                        String imageName = String.format(Locale.ROOT, "image-%04d.png", ++imageNumber);
                        ImageIO.write(scalePdfImage(imageBlock.image()), "png",
                                images.resolve(imageName).toFile());
                        html.append("<p class=\"help-image-only help-pdf-image\"><img "
                                + "class=\"help-pdf-import-image\" src=\"images/")
                                .append(imageName).append("\" alt=\"Image from page ")
                                .append(page.pageNumber()).append("\"></p>");
                    }
                }
            }
            html.append("</body></html>");
            Path generatedHtml = temporary.resolve("tutorial.html");
            Files.writeString(generatedHtml, html, StandardCharsets.UTF_8);
            return importHtml(generatedHtml, packRoot, CATEGORY + "/" + pdfFile.getFileName(),
                    searchableText.toString().strip());
        } finally {
            deleteTemporaryTree(temporary);
        }
    }

    private ImportResult importHtml(Path source, Path packRoot, String sourceName, String searchText)
            throws IOException {
        Path sourceDirectory = source.getParent();
        String html = Files.readString(source, StandardCharsets.UTF_8);
        Document document = Jsoup.parse(html, source.toUri().toString());
        String title = document.title().strip();
        if (title.isBlank() && document.selectFirst("h1") != null) title = document.selectFirst("h1").text().strip();
        if (title.isBlank()) title = fileStem(source.getFileName().toString());

        Path root = packRoot.toAbsolutePath().normalize();
        Path catalogFile = root.resolve("catalog.json");
        Path manifestFile = root.resolve("manifest.json");
        ObjectNode catalog = (ObjectNode) mapper.readTree(catalogFile.toFile());
        ObjectNode manifest = (ObjectNode) mapper.readTree(manifestFile.toFile());
        ArrayNode tutorials = requireArray(catalog, "tutorials");
        ArrayNode toc = requireArray(catalog, "tableOfContents");

        ObjectNode previous = null;
        for (JsonNode entry : tutorials) {
            if (sourceName.equals(entry.path("source").asText())) {
                previous = (ObjectNode) entry;
                break;
            }
        }
        boolean updated = previous != null;
        String id = updated ? previous.path("id").asText() : uniqueId(tutorials, slug(title));
        String contentFile = "pages/new-tutorials/" + id + "/tutorial.html";
        Path destinationDirectory = EditorHelpContentPack.safeResolve(root, "pages/new-tutorials/" + id);
        Files.createDirectories(destinationDirectory);

        List<String> copiedImages = copyLocalImages(document, sourceDirectory, destinationDirectory);
        Files.writeString(destinationDirectory.resolve("tutorial.html"), html, StandardCharsets.UTF_8);

        ObjectNode entry = mapper.createObjectNode();
        entry.put("id", id);
        entry.put("title", title);
        entry.put("source", sourceName);
        entry.put("contentFile", contentFile);
        entry.putArray("categoryPath").add(CATEGORY).add(title);
        ArrayNode headings = entry.putArray("headings");
        document.select("h1, h2, h3, h4, h5, h6").forEach(heading -> headings.add(heading.text()));
        entry.put("text", searchText == null || searchText.isBlank() ? document.text() : searchText);
        ArrayNode images = entry.putArray("images");
        copiedImages.forEach(images::add);
        entry.putArray("links");
        entry.put("encoding", "UTF-8");

        if (updated) {
            for (int i = 0; i < tutorials.size(); i++) {
                if (id.equals(tutorials.get(i).path("id").asText())) tutorials.set(i, entry);
            }
            removeTutorialNode(toc, id);
        } else {
            tutorials.add(entry);
        }
        ObjectNode category = findOrCreateCategory(toc);
        ObjectNode node = mapper.createObjectNode();
        node.put("title", title);
        node.putArray("path").add(CATEGORY).add(title);
        node.putArray("children");
        node.put("source", sourceName);
        node.put("tutorialId", id);
        ((ArrayNode) category.get("children")).add(node);

        mapper.writerWithDefaultPrettyPrinter().writeValue(catalogFile.toFile(), catalog);
        manifest.put("generatedAt", Instant.now().toString());
        ObjectNode descriptor = (ObjectNode) manifest.path("catalog");
        descriptor.put("sha256", sha256(catalogFile));
        descriptor.put("documentCount", tutorials.size());
        mapper.writerWithDefaultPrettyPrinter().writeValue(manifestFile.toFile(), manifest);
        return new ImportResult(id, title, copiedImages.size(), updated);
    }

    private List<String> copyLocalImages(Document document, Path sourceDirectory, Path destination) throws IOException {
        List<String> copied = new ArrayList<>();
        for (Element image : document.select("img[src]")) {
            String reference = image.attr("src").strip();
            if (reference.isBlank() || reference.startsWith("data:") || reference.startsWith("http://")
                    || reference.startsWith("https://")) continue;
            String pathPart = reference.split("[?#]", 2)[0];
            Path relative;
            try {
                URI uri = URI.create(pathPart.replace("\\", "/"));
                if (uri.isAbsolute()) throw new IOException("Images must use relative paths: " + reference);
                relative = Path.of(uri.getPath()).normalize();
            } catch (IllegalArgumentException exception) {
                throw new IOException("Invalid image path: " + reference, exception);
            }
            Path imageSource = sourceDirectory.resolve(relative).normalize();
            if (!imageSource.startsWith(sourceDirectory) || !Files.isRegularFile(imageSource)) {
                throw new IOException("Referenced image was not found beside the tutorial: " + reference);
            }
            Path target = destination.resolve(relative).normalize();
            if (!target.startsWith(destination)) throw new IOException("Image path escapes the tutorial folder: " + reference);
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            Files.copy(imageSource, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            copied.add(relative.toString().replace('\\', '/'));
        }
        return List.copyOf(copied);
    }

    private ObjectNode findOrCreateCategory(ArrayNode toc) {
        for (JsonNode node : toc) if (CATEGORY.equals(node.path("title").asText())) return (ObjectNode) node;
        ObjectNode category = mapper.createObjectNode();
        category.put("title", CATEGORY);
        category.putArray("path").add(CATEGORY);
        category.putArray("children");
        toc.insert(0, category);
        return category;
    }

    private static void removeTutorialNode(ArrayNode nodes, String id) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            JsonNode node = nodes.get(i);
            if (id.equals(node.path("tutorialId").asText())) nodes.remove(i);
            else if (node.path("children").isArray()) removeTutorialNode((ArrayNode) node.path("children"), id);
        }
    }

    private static ArrayNode requireArray(ObjectNode object, String field) throws IOException {
        if (!(object.get(field) instanceof ArrayNode array)) throw new IOException("Catalog field is not an array: " + field);
        return array;
    }

    private static boolean isHtml(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm");
    }

    private static boolean isPdf(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    private static String fileStem(String name) { return name.replaceFirst("(?i)\\.(html?|pdf)$", ""); }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static String formatPdfText(String value) {
        final int lineLength = 78;
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String originalWord : value.strip().split("\\s+")) {
            String word = originalWord;
            while (word.length() > lineLength) {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, lineLength));
                word = word.substring(lineLength);
            }
            if (word.isEmpty()) continue;
            if (!line.isEmpty() && line.length() + 1 + word.length() > lineLength) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines.stream().map(EditorHelpTutorialImporter::escapeHtml)
                .collect(java.util.stream.Collectors.joining("<br>"));
    }

    private static java.awt.image.BufferedImage scalePdfImage(java.awt.image.BufferedImage source) {
        if (source.getWidth() <= PDF_IMAGE_MAX_WIDTH) return source;
        int height = Math.max(1, Math.round(source.getHeight()
                * (PDF_IMAGE_MAX_WIDTH / (float) source.getWidth())));
        java.awt.image.BufferedImage scaled = new java.awt.image.BufferedImage(
                PDF_IMAGE_MAX_WIDTH, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, PDF_IMAGE_MAX_WIDTH, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static void deleteTemporaryTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    private static String slug(String value) {
        String slug = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "tutorial" : slug;
    }

    private static String uniqueId(ArrayNode tutorials, String slug) {
        String base = "new-" + slug;
        String candidate = base;
        int suffix = 2;
        while (containsId(tutorials, candidate)) candidate = base + "-" + suffix++;
        return candidate;
    }

    private static boolean containsId(ArrayNode tutorials, String id) {
        for (JsonNode tutorial : tutorials) if (id.equals(tutorial.path("id").asText())) return true;
        return false;
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }

    public record ImportResult(String id, String title, int copiedImages, boolean updated) { }
}
