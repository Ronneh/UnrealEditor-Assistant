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

/** Validated, immutable view of an installed English Editor Help content pack. */
public record EditorHelpContentPack(
        Path root,
        String packId,
        String packVersion,
        int schemaVersion,
        String language,
        String engine,
        JsonNode tableOfContents,
        List<HelpDocument> documents) {

    public record HelpDocument(
            String id,
            String title,
            String source,
            String contentFile,
            List<String> categoryPath,
            List<String> headings,
            String text) {
        public Path resolveContent(Path packRoot) throws IOException {
            return safeResolve(packRoot, contentFile);
        }
    }

    static Path safeResolve(Path root, String relative) throws IOException {
        if (relative == null || relative.isBlank()) throw new IOException("Empty pack-relative path");
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relative.replace('/', java.io.File.separatorChar)).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IOException("Path escapes content pack: " + relative);
        }
        return resolved;
    }
}
