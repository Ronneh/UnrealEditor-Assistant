import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Converts extracted Microsoft HTML Help into a standalone content pack. */
public final class EditorHelpImporter {
    private static final Pattern URI_SCHEME =
            Pattern.compile("(?i)^[a-z][a-z0-9+.-]*:");
    private static final Pattern NAV_TEXT =
            Pattern.compile("(?i)^\\s*(?:home|back|next|previous|prev|top|contents?|index|up)\\s*$");

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: EditorHelpImporter <extracted-chm-directory> <content-pack-directory>");
            System.exit(2);
        }
        ImportResult result = new EditorHelpImporter().importHelp(Path.of(args[0]), Path.of(args[1]));
        System.out.printf(Locale.ROOT,
                "Imported %d tutorials from %d TOC entries (%d skipped, %d missing files, "
                        + "%d broken links, %d missing images, %d duplicates).%n",
                result.report.importedTutorials, result.report.tocEntries,
                result.report.skippedPages.size(), result.report.missingTocFiles.size(),
                result.report.brokenLinks.size(), result.report.missingImages.size(),
                result.report.duplicates.size());
    }

    public ImportResult importHelp(Path source, Path output) throws Exception {
        source = source.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            throw new IOException("CHM extraction directory does not exist: " + source);
        }
        List<Path> hhcFiles;
        try (var stream = Files.walk(source)) {
            hhcFiles = stream.filter(p -> Files.isRegularFile(p)
                    && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".hhc")).toList();
        }
        if (hhcFiles.size() != 1) {
            throw new IOException("Expected exactly one .hhc file, found " + hhcFiles.size());
        }
        Files.createDirectories(output.resolve("pages"));
        Files.createDirectories(output.resolve("assets"));

        Report report = new Report();
        TocNode root = new TocNode("root", null, List.of(), new ArrayList<>());
        Document hhc = Jsoup.parse(hhcFiles.get(0).toFile(), null, "");
        Element topList = hhc.selectFirst("body > ul");
        if (topList == null) throw new IOException("No root UL found in " + hhcFiles.get(0));
        parseTocList(topList, root.children, List.of(), report);

        List<Tutorial> tutorials = new ArrayList<>();
        Map<String, String> firstIdBySource = new LinkedHashMap<>();
        Map<String, String> firstIdByContentHash = new HashMap<>();
        java.util.Set<String> tocReferencedSources = new java.util.HashSet<>();
        Map<String, Path> sourceIndex = buildCaseInsensitiveIndex(source);
        for (TocNode node : flatten(root.children)) {
            if (node.source == null || node.source.isBlank()) continue;
            Resolved resolved = resolve(source, source, node.source, sourceIndex);
            if (resolved.external) {
                report.skippedPages.add(new Issue(node.source, "external TOC target", node.title));
                continue;
            }
            if (resolved.path == null || !Files.isRegularFile(resolved.path)) {
                report.missingTocFiles.add(new Issue(node.source, "file not found", node.title));
                continue;
            }
            String sourceKey = relativeUnix(source, resolved.path).toLowerCase(Locale.ROOT);
            tocReferencedSources.add(sourceKey);
            String existing = firstIdBySource.get(sourceKey);
            if (existing != null) {
                node.tutorialId = existing;
                report.duplicates.add(new Duplicate(node.source, existing, "same normalized source"));
                continue;
            }
            Tutorial tutorial = extractTutorial(source, resolved.path, node, sourceIndex, output, report);
            if (tutorial.text.isBlank()) {
                report.skippedPages.add(new Issue(node.source, "no tutorial text after cleanup", node.title));
                continue;
            }
            String hash = sha256(tutorial.text.replaceAll("\\s+", " ").strip());
            String contentDuplicate = firstIdByContentHash.get(hash);
            if (contentDuplicate != null) {
                node.tutorialId = contentDuplicate;
                firstIdBySource.put(sourceKey, contentDuplicate);
                report.duplicates.add(new Duplicate(node.source, contentDuplicate, "same cleaned text"));
                continue;
            }
            firstIdBySource.put(sourceKey, tutorial.id);
            firstIdByContentHash.put(hash, tutorial.id);
            node.tutorialId = tutorial.id;
            tutorials.add(tutorial);
        }
        rewriteInternalLinks(output, tutorials, firstIdBySource);
        for (Map.Entry<String, Path> entry : sourceIndex.entrySet()) {
            if (isHtml(entry.getValue()) && !tocReferencedSources.contains(entry.getKey())) {
                report.skippedPages.add(new Issue(
                        relativeUnix(source, entry.getValue()), "HTML file not referenced by TOC", null));
            }
        }
        report.importedTutorials = tutorials.size();
        String generatedAt = Instant.now().toString();
        Catalog catalog = new Catalog("unreal-editor-help", 2, "en", "Unreal Editor 2",
                generatedAt, root.children, tutorials);
        Path catalogFile = output.resolve("catalog.json");
        Path reportFile = output.resolve("import-report.json");
        writeJson(catalogFile, catalog);
        writeJson(reportFile, report);
        PackManifest manifest = new PackManifest(
                "unreal-editor-help", "1.0.0", 2, "en", "Unreal Editor 2", generatedAt,
                new PackFile("catalog.json", sha256File(catalogFile), tutorials.size()),
                new PackFile("import-report.json", sha256File(reportFile), null));
        writeJson(output.resolve("manifest.json"), manifest);
        Files.writeString(output.resolve("README.md"), """
                # Unreal Editor Help Content Pack

                This directory is generated independently of the application JAR.

                - `catalog.json`: hierarchical TOC plus normalized tutorial records
                - `manifest.json`: version, language, compatibility and SHA-256 checksums
                - `pages/`: cleaned, script/frame/ad-free HTML
                - `assets/`: referenced images, preserving source-relative paths
                - `import-report.json`: skipped pages, missing targets/assets, broken links and duplicates
                """);
        return new ImportResult(catalog, report);
    }

    private static void parseTocList(
            Element ul, List<TocNode> target, List<String> parents, Report report) {
        for (Element li : ul.children()) {
            if (!li.normalName().equals("li")) continue;
            Element object = firstDirectChild(li, "object");
            if (object == null) continue;
            String title = param(object, "Name");
            String local = param(object, "Local");
            if (title.isBlank()) title = local.isBlank() ? "(untitled)" : local;
            List<String> path = new ArrayList<>(parents);
            path.add(title);
            TocNode node = new TocNode(title, emptyToNull(local), List.copyOf(path), new ArrayList<>());
            target.add(node);
            report.tocEntries++;
            Element childList = firstDirectChild(li, "ul");
            if (childList != null) parseTocList(childList, node.children, path, report);
        }
    }

    private static Tutorial extractTutorial(
            Path root, Path file, TocNode node, Map<String, Path> index, Path output, Report report)
            throws Exception {
        Document doc = Jsoup.parse(file.toFile(), null, file.toUri().toString());
        doc.select("script, noscript, iframe, frame, frameset, object, embed, applet, form, nav, "
                + "[id*=advert], [class*=advert], [id*=banner], [class*=banner]").remove();
        doc.select("a").stream().filter(a -> NAV_TEXT.matcher(a.text()).matches()
                && a.children().isEmpty()).forEach(Element::remove);
        doc.getAllElements().forEach(e -> {
            e.attributes().asList().stream()
                    .filter(a -> a.getKey().toLowerCase(Locale.ROOT).startsWith("on"))
                    .map(a -> a.getKey()).toList().forEach(e::removeAttr);
        });

        String source = relativeUnix(root, file);
        String id = stableId(source);
        List<ResourceRef> images = new ArrayList<>();
        for (Element image : doc.select("img[src], input[type=image][src]")) {
            String raw = image.attr("src");
            Resolved resolved = resolve(root, file.getParent(), raw, index);
            if (resolved.external || resolved.fragmentOnly) {
                images.add(new ResourceRef(raw, raw, resolved.external, true));
            } else if (resolved.path == null || !Files.isRegularFile(resolved.path)) {
                report.missingImages.add(new LinkIssue(source, raw, "image not found"));
                images.add(new ResourceRef(raw, null, false, false));
                image.remove();
            } else {
                String relative = relativeUnix(root, resolved.path);
                Path assetTarget = output.resolve("assets").resolve(relative).normalize();
                ensureInside(output.resolve("assets"), assetTarget);
                Files.createDirectories(assetTarget.getParent());
                Files.copy(resolved.path, assetTarget, StandardCopyOption.REPLACE_EXISTING);
                image.attr("src", "../assets/" + encodeUriPath(relative));
                images.add(new ResourceRef(raw, "assets/" + relative, false, true));
            }
        }

        List<LinkRef> links = new ArrayList<>();
        for (Element anchor : doc.select("a[href]")) {
            String raw = anchor.attr("href");
            if (raw.strip().toLowerCase(Locale.ROOT).startsWith("javascript:")) {
                links.add(new LinkRef(raw, null, false, false));
                anchor.removeAttr("href");
                continue;
            }
            Resolved resolved = resolve(root, file.getParent(), raw, index);
            if (resolved.external || resolved.fragmentOnly) {
                links.add(new LinkRef(raw, raw, resolved.external, true));
                continue;
            }
            boolean exists = resolved.path != null && Files.isRegularFile(resolved.path);
            String normalized = exists ? relativeUnix(root, resolved.path) : null;
            links.add(new LinkRef(raw, normalized, false, exists));
            if (!exists) {
                report.brokenLinks.add(new LinkIssue(source, raw, "target not found"));
                anchor.removeAttr("href");
            } else if (isHtml(resolved.path)) {
                anchor.attr("data-help-source", normalized);
                anchor.removeAttr("href");
            }
        }
        doc.select("base, link[rel=stylesheet], meta[http-equiv=refresh], style").remove();
        List<String> headings = doc.select("h1, h2, h3, h4, h5, h6").eachText().stream()
                .map(String::strip).filter(s -> !s.isBlank()).toList();
        String htmlTitle = doc.title().strip();
        String title = !htmlTitle.isBlank() ? htmlTitle : !headings.isEmpty() ? headings.get(0) : node.title;
        Element body = doc.body();
        String text = body == null ? "" : body.wholeText().replace('\u00a0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" *\\n+ *", "\n").strip();
        doc.outputSettings().charset(StandardCharsets.UTF_8).prettyPrint(true);
        Files.writeString(output.resolve("pages").resolve(id + ".html"), doc.outerHtml());
        return new Tutorial(id, title, source, "pages/" + id + ".html", node.path,
                headings, text, images, links, "UTF-8");
    }

    private static Map<String, Path> buildCaseInsensitiveIndex(Path root) throws IOException {
        Map<String, Path> result = new HashMap<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(p ->
                    result.putIfAbsent(relativeUnix(root, p).toLowerCase(Locale.ROOT), p));
        }
        return result;
    }

    private static void rewriteInternalLinks(
            Path output, List<Tutorial> tutorials, Map<String, String> tutorialIdBySource) throws IOException {
        for (Tutorial tutorial : tutorials) {
            Path page = output.resolve(tutorial.contentFile()).normalize();
            Document document = Jsoup.parse(page.toFile(), StandardCharsets.UTF_8.name(), "");
            for (Element anchor : document.select("a[data-help-source]")) {
                String target = anchor.attr("data-help-source").toLowerCase(Locale.ROOT);
                String targetId = tutorialIdBySource.get(target);
                if (targetId != null) {
                    anchor.attr("href", "https://editor-help.local/tutorial/" + targetId);
                }
                anchor.removeAttr("data-help-source");
            }
            document.outputSettings().charset(StandardCharsets.UTF_8).prettyPrint(true);
            Files.writeString(page, document.outerHtml(), StandardCharsets.UTF_8);
        }
    }

    static String normalizeReference(String raw) {
        if (raw == null) return "";
        String value = raw.strip().replace('\\', '/');
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int fragment = value.indexOf('#');
        if (fragment >= 0) value = value.substring(0, fragment);
        try {
            value = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // Keep malformed legacy URL escapes for missing-target reporting.
        }
        while (value.startsWith("./")) value = value.substring(2);
        return value;
    }

    private static Resolved resolve(Path root, Path base, String raw, Map<String, Path> index) {
        if (raw == null || raw.isBlank()) return new Resolved(null, false, true);
        String trimmed = raw.strip();
        if (trimmed.startsWith("#")) return new Resolved(null, false, true);
        if (URI_SCHEME.matcher(trimmed).find() || trimmed.startsWith("//")) {
            return new Resolved(null, true, false);
        }
        String normalized = normalizeReference(trimmed);
        if (normalized.isBlank()) return new Resolved(null, false, true);
        Path candidate = base.resolve(normalized).normalize();
        if (!candidate.startsWith(root)) return new Resolved(null, false, false);
        if (Files.isRegularFile(candidate)) return new Resolved(candidate, false, false);
        return new Resolved(index.get(relativeUnix(root, candidate).toLowerCase(Locale.ROOT)), false, false);
    }

    private static List<TocNode> flatten(List<TocNode> nodes) {
        List<TocNode> result = new ArrayList<>();
        for (TocNode node : nodes) {
            result.add(node);
            result.addAll(flatten(node.children));
        }
        return result;
    }

    private static Element firstDirectChild(Element parent, String tag) {
        for (Element child : parent.children()) if (child.normalName().equals(tag)) return child;
        return null;
    }

    private static String param(Element object, String name) {
        for (Element p : object.select("param")) {
            if (p.attr("name").equalsIgnoreCase(name)) return p.attr("value").strip();
        }
        return "";
    }

    private static boolean isHtml(Path path) {
        String n = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".htm") || n.endsWith(".html");
    }

    private static String stableId(String source) throws Exception {
        String stem = source.replace('\\', '/').replaceAll("(?i)\\.html?$", "")
                .replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "").toLowerCase(Locale.ROOT);
        if (stem.length() > 64) stem = stem.substring(0, 64).replaceAll("-$", "");
        return stem + "-" + sha256(source.toLowerCase(Locale.ROOT)).substring(0, 10);
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private static String sha256File(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private static String relativeUnix(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static String encodeUriPath(String path) {
        StringBuilder result = new StringBuilder();
        for (String part : path.split("/", -1)) {
            if (!result.isEmpty()) result.append('/');
            for (byte b : part.getBytes(StandardCharsets.UTF_8)) {
                int c = b & 0xff;
                if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                        || "-._~".indexOf(c) >= 0) result.append((char) c);
                else result.append('%').append(String.format(Locale.ROOT, "%02X", c));
            }
        }
        return result.toString();
    }

    private static void ensureInside(Path root, Path target) throws IOException {
        if (!target.startsWith(root.toAbsolutePath().normalize())) {
            throw new IOException("Refusing to write outside content pack: " + target);
        }
    }

    private static String emptyToNull(String value) { return value.isBlank() ? null : value; }

    private static void writeJson(Path path, Object value) throws IOException {
        new ObjectMapper().findAndRegisterModules()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT).writeValue(path.toFile(), value);
    }

    public record ImportResult(Catalog catalog, Report report) {}
    public record Catalog(String packId, int schemaVersion, String language, String engine,
                          String generatedAt,
                          List<TocNode> tableOfContents, List<Tutorial> tutorials) {}
    public record PackManifest(String packId, String packVersion, int schemaVersion, String language,
                               String engine, String generatedAt, PackFile catalog, PackFile importReport) {}
    public record PackFile(String path, String sha256, Integer documentCount) {}
    public static final class TocNode {
        public final String title;
        public final String source;
        public final List<String> path;
        public final List<TocNode> children;
        public String tutorialId;
        TocNode(String title, String source, List<String> path, List<TocNode> children) {
            this.title = title; this.source = source; this.path = path; this.children = children;
        }
    }
    public record Tutorial(String id, String title, String source, String contentFile,
                           List<String> categoryPath, List<String> headings, String text,
                           List<ResourceRef> images, List<LinkRef> links, String encoding) {}
    public record ResourceRef(String original, String normalized, boolean external, boolean exists) {}
    public record LinkRef(String original, String normalized, boolean external, boolean exists) {}
    public record Issue(String source, String reason, String title) {}
    public record LinkIssue(String source, String target, String reason) {}
    public record Duplicate(String source, String canonicalTutorialId, String reason) {}
    public static final class Report {
        public int tocEntries;
        public int importedTutorials;
        public final List<Issue> skippedPages = new ArrayList<>();
        public final List<Issue> missingTocFiles = new ArrayList<>();
        public final List<LinkIssue> brokenLinks = new ArrayList<>();
        public final List<LinkIssue> missingImages = new ArrayList<>();
        public final List<Duplicate> duplicates = new ArrayList<>();
    }
    private record Resolved(Path path, boolean external, boolean fragmentOnly) {}
}
