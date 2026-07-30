import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** CLI for validating a content pack, rebuilding its Lucene index and optionally testing a query. */
public final class EditorHelpIndexTool {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: EditorHelpIndexTool <content-pack-directory> <index-directory> [query]");
            System.exit(2);
        }
        EditorHelpContentPack pack = new EditorHelpContentPackLoader().load(Path.of(args[0]));
        Path index = Path.of(args[1]);
        EditorHelpSearch.buildIndex(pack, index);
        EditorHelpEnvironment.markIndexCurrent(pack, index);
        System.out.printf(Locale.ROOT, "Indexed %d English tutorials in %s%n",
                pack.documents().size(), index.toAbsolutePath().normalize());
        if (args.length == 3) {
            try (EditorHelpSearch search = new EditorHelpSearch(index)) {
                List<EditorHelpSearch.SearchResult> results = search.search(args[2], 10);
                for (EditorHelpSearch.SearchResult result : results) {
                    System.out.printf(Locale.ROOT, "%.3f  %s  [%s]%n",
                            result.score(), result.title(), result.source());
                }
            }
        }
    }
}
