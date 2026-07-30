import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads and validates Editor Help packs before application or search code can use them. */
public final class EditorHelpContentPackLoader {
    public static final int SUPPORTED_SCHEMA_VERSION = 2;
    private final ObjectMapper mapper = new ObjectMapper();

    public EditorHelpContentPack load(Path packDirectory) throws IOException {
        Path root = packDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IOException("Content pack directory not found: " + root);
        JsonNode manifest = readRequired(root.resolve("manifest.json"), "manifest");
        requireText(manifest, "packId");
        requireText(manifest, "packVersion");
        requireText(manifest, "engine");
        if (manifest.path("schemaVersion").asInt(-1) != SUPPORTED_SCHEMA_VERSION) {
            throw new IOException("Unsupported content pack schema version: "
                    + manifest.path("schemaVersion").asText());
        }
        String language = requireText(manifest, "language");
        if (!language.equals("en")) {
            throw new IOException("Only English content packs are supported, found: " + language);
        }

        JsonNode catalogDescriptor = manifest.path("catalog");
        Path catalogFile = verifiedFile(root, catalogDescriptor, "catalog");
        JsonNode catalog = readRequired(catalogFile, "catalog");
        if (!catalog.path("packId").asText().equals(manifest.path("packId").asText())) {
            throw new IOException("Catalog packId does not match manifest");
        }
        if (catalog.path("schemaVersion").asInt(-1) != SUPPORTED_SCHEMA_VERSION
                || !catalog.path("language").asText().equals("en")) {
            throw new IOException("Catalog compatibility metadata does not match manifest");
        }
        verifiedFile(root, manifest.path("importReport"), "import report");

        JsonNode tutorials = catalog.path("tutorials");
        if (!tutorials.isArray()) throw new IOException("Catalog tutorials must be an array");
        int expectedCount = catalogDescriptor.path("documentCount").asInt(-1);
        if (expectedCount != tutorials.size()) {
            throw new IOException("Manifest document count " + expectedCount
                    + " does not match catalog count " + tutorials.size());
        }
        Set<String> ids = new HashSet<>();
        List<EditorHelpContentPack.HelpDocument> documents = new ArrayList<>();
        for (JsonNode tutorial : tutorials) {
            String id = requireText(tutorial, "id");
            if (!ids.add(id)) throw new IOException("Duplicate tutorial id: " + id);
            String contentFile = requireText(tutorial, "contentFile");
            Path content = EditorHelpContentPack.safeResolve(root, contentFile);
            if (!Files.isRegularFile(content)) throw new IOException("Tutorial page not found: " + contentFile);
            documents.add(new EditorHelpContentPack.HelpDocument(
                    id, requireText(tutorial, "title"), requireText(tutorial, "source"), contentFile,
                    textArray(tutorial.path("categoryPath"), "categoryPath"),
                    textArray(tutorial.path("headings"), "headings"),
                    requireText(tutorial, "text")));
        }
        JsonNode toc = catalog.path("tableOfContents");
        if (!toc.isArray()) throw new IOException("Catalog tableOfContents must be an array");
        return new EditorHelpContentPack(root, manifest.path("packId").asText(),
                manifest.path("packVersion").asText(), SUPPORTED_SCHEMA_VERSION, language,
                manifest.path("engine").asText(), toc.deepCopy(), List.copyOf(documents));
    }

    private Path verifiedFile(Path root, JsonNode descriptor, String label) throws IOException {
        if (!descriptor.isObject()) throw new IOException("Missing " + label + " descriptor");
        String relative = requireText(descriptor, "path");
        String expectedHash = requireText(descriptor, "sha256");
        Path file = EditorHelpContentPack.safeResolve(root, relative);
        if (!Files.isRegularFile(file)) throw new IOException("Missing " + label + " file: " + relative);
        String actualHash = sha256(file);
        if (!actualHash.equalsIgnoreCase(expectedHash)) {
            throw new IOException("SHA-256 mismatch for " + label + ": " + relative);
        }
        return file;
    }

    private JsonNode readRequired(Path file, String label) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("Missing " + label + ": " + file);
        return mapper.readTree(file.toFile());
    }

    private static String requireText(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IOException("Missing or empty field: " + field);
        }
        return value.asText();
    }

    private static List<String> textArray(JsonNode node, String field) throws IOException {
        if (!node.isArray()) throw new IOException(field + " must be an array");
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) throw new IOException(field + " must contain only strings");
            values.add(value.asText());
        }
        return List.copyOf(values);
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0; ) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IOException(impossible);
        }
    }
}
