import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.Directory;

/**
 * Regeneratable local full-text index for English Editor Help content.
 * The index is derived data and never needs to be stored in the application JAR.
 */
public final class EditorHelpSearch implements AutoCloseable {
    private static final String[] SEARCH_FIELDS = {"title", "headings", "categories", "text"};
    private static final Map<String, Float> BOOSTS =
            Map.of("title", 5.0f, "headings", 3.0f, "categories", 2.0f, "text", 1.0f);
    private final Analyzer analyzer;
    private final Directory directory;
    private final DirectoryReader reader;
    private final IndexSearcher searcher;

    public static void buildIndex(EditorHelpContentPack pack, Path indexDirectory) throws IOException {
        Files.createDirectories(indexDirectory);
        try (Analyzer analyzer = new EnglishAnalyzer();
             NIOFSDirectory directory = new NIOFSDirectory(indexDirectory);
             IndexWriter writer = new IndexWriter(directory,
                     new IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE))) {
            for (EditorHelpContentPack.HelpDocument help : pack.documents()) {
                Document document = new Document();
                document.add(new StringField("id", help.id(), Field.Store.YES));
                document.add(new StringField("source", help.source(), Field.Store.YES));
                document.add(new StringField("contentFile", help.contentFile(), Field.Store.YES));
                document.add(new TextField("title", help.title(), Field.Store.YES));
                document.add(new TextField("headings", String.join("\n", help.headings()), Field.Store.NO));
                document.add(new TextField("categories", String.join(" / ", help.categoryPath()), Field.Store.YES));
                document.add(new TextField("text", help.text(), Field.Store.YES));
                writer.addDocument(document);
            }
            writer.commit();
        }
    }

    public EditorHelpSearch(Path indexDirectory) throws IOException {
        analyzer = new EnglishAnalyzer();
        directory = new NIOFSDirectory(indexDirectory);
        reader = DirectoryReader.open(directory);
        searcher = new IndexSearcher(reader);
    }

    public List<SearchResult> search(String queryText, int limit) throws Exception {
        if (queryText == null || queryText.isBlank() || limit < 1) return List.of();
        MultiFieldQueryParser parser = new MultiFieldQueryParser(SEARCH_FIELDS, analyzer, BOOSTS);
        var query = parser.parse(QueryParser.escape(queryText.strip()));
        ScoreDoc[] hits = searcher.search(query, limit).scoreDocs;
        List<SearchResult> result = new ArrayList<>(hits.length);
        for (ScoreDoc hit : hits) {
            Document document = searcher.storedFields().document(hit.doc);
            result.add(new SearchResult(
                    document.get("id"), document.get("title"), document.get("source"),
                    document.get("contentFile"), document.get("categories"), hit.score,
                    excerpt(document.get("text"), queryText)));
        }
        return List.copyOf(result);
    }

    public int documentCount() {
        return reader.numDocs();
    }

    private static String excerpt(String text, String query) {
        if (text == null || text.isBlank()) return "";
        String lower = text.toLowerCase(Locale.ENGLISH);
        int match = -1;
        for (String token : query.toLowerCase(Locale.ENGLISH).split("[^a-z0-9]+")) {
            if (token.length() > 2) {
                int candidate = lower.indexOf(token);
                if (candidate >= 0 && (match < 0 || candidate < match)) match = candidate;
            }
        }
        int start = match < 0 ? 0 : Math.max(0, match - 90);
        int end = Math.min(text.length(), start + 280);
        String value = text.substring(start, end).replaceAll("\\s+", " ").strip();
        return (start > 0 ? "… " : "") + value + (end < text.length() ? " …" : "");
    }

    @Override
    public void close() throws IOException {
        try {
            reader.close();
        } finally {
            try {
                directory.close();
            } finally {
                analyzer.close();
            }
        }
    }

    public record SearchResult(
            String id, String title, String source, String contentFile,
            String categoryPath, float score, String excerpt) {}
}
