import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Locates the external content pack and keeps its derived Lucene index current. */
public final class EditorHelpEnvironment {
    public static final String CONTENT_PROPERTY = "unreal.help.content";
    private static final String INDEX_DIRECTORY = "search-index";
    private static final String INDEX_VERSION_FILE = ".catalog-sha256";

    public Session open() throws IOException {
        Path root = locateContentPack();
        EditorHelpContentPack pack = new EditorHelpContentPackLoader().load(root);
        Path index = root.resolve(INDEX_DIRECTORY);
        String catalogHash = readCatalogHash(root.resolve("manifest.json"));
        Path marker = index.resolve(INDEX_VERSION_FILE);
        String indexedHash = Files.isRegularFile(marker) ? Files.readString(marker).strip() : "";
        if (!catalogHash.equalsIgnoreCase(indexedHash) || !hasLuceneIndex(index)) {
            EditorHelpSearch.buildIndex(pack, index);
            markIndexCurrent(pack, index);
        }
        return new Session(pack, new EditorHelpSearch(index));
    }

    public Path locateContentPack() throws IOException {
        List<Path> candidates = contentPackCandidates();
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("manifest.json"))) return candidate;
        }
        throw new IOException("Editor Help content pack was not found. Checked: "
                + candidates.stream().map(Path::toString).toList());
    }

    List<Path> contentPackCandidates() {
        List<Path> candidates = new ArrayList<>();
        String configured = System.getProperty(CONTENT_PROPERTY);
        if (configured != null && !configured.isBlank()) candidates.add(Path.of(configured));

        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            Path application = Path.of(appPath).toAbsolutePath().normalize();
            if (application.getParent() != null) candidates.add(application.getParent().resolve("help-content"));
        }
        try {
            Path code = Path.of(EditorHelpEnvironment.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            Path base = Files.isDirectory(code) ? code : code.getParent();
            if (base != null) {
                candidates.add(base.resolve("help-content"));
                if (base.getParent() != null) candidates.add(base.getParent().resolve("help-content"));
            }
        } catch (URISyntaxException | NullPointerException ignored) {
            // Development working-directory candidates below remain available.
        }
        Path working = Path.of("").toAbsolutePath().normalize();
        candidates.add(working.resolve("help-content"));
        candidates.add(working.resolve("help-content-pack"));
        return candidates.stream().map(p -> p.toAbsolutePath().normalize()).distinct().toList();
    }

    private static boolean hasLuceneIndex(Path index) {
        if (!Files.isDirectory(index)) return false;
        try (var files = Files.list(index)) {
            return files.anyMatch(p -> p.getFileName().toString().startsWith("segments_"));
        } catch (IOException e) {
            return false;
        }
    }

    private static String readCatalogHash(Path manifest) throws IOException {
        JsonNode root = new ObjectMapper().readTree(manifest.toFile());
        String hash = root.path("catalog").path("sha256").asText();
        if (hash.isBlank()) throw new IOException("Manifest catalog SHA-256 is missing");
        return hash;
    }

    static void markIndexCurrent(EditorHelpContentPack pack, Path index) throws IOException {
        Files.createDirectories(index);
        String hash = readCatalogHash(pack.root().resolve("manifest.json"));
        Files.writeString(index.resolve(INDEX_VERSION_FILE), hash + System.lineSeparator(),
                StandardCharsets.US_ASCII);
    }

    public record Session(EditorHelpContentPack pack, EditorHelpSearch search) implements AutoCloseable {
        @Override public void close() throws IOException {
            search.close();
        }
    }
}
