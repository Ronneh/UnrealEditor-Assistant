import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persists the user-defined order of files and folders without renaming them. */
public final class FileTreeOrder {
    private static final String ORDER_FILE = ".ue2-order";
    private FileTreeOrder() { }

    public static List<Path> sort(Path folder, List<Path> paths) {
        Map<String, Integer> positions = new HashMap<>();
        Path orderFile = folder.resolve(ORDER_FILE);
        if (Files.isRegularFile(orderFile)) {
            try {
                int index = 0;
                for (String name : Files.readAllLines(orderFile, StandardCharsets.UTF_8))
                    if (!name.isBlank() && !positions.containsKey(name)) positions.put(name, index++);
            } catch (IOException ignored) { }
        }
        Comparator<Path> fallback = (first, second) -> {
            boolean firstFolder = Files.isDirectory(first), secondFolder = Files.isDirectory(second);
            if (firstFolder != secondFolder) return firstFolder ? -1 : 1;
            return first.getFileName().toString().compareToIgnoreCase(second.getFileName().toString());
        };
        return paths.stream().sorted((first, second) -> {
            Integer a = positions.get(first.getFileName().toString());
            Integer b = positions.get(second.getFileName().toString());
            if (a != null && b != null) return Integer.compare(a, b);
            if (a != null) return -1;
            if (b != null) return 1;
            return fallback.compare(first, second);
        }).toList();
    }

    public static void place(Path folder, Path item, int index) throws IOException {
        List<Path> children;
        try (var stream = Files.list(folder)) {
            children = new ArrayList<>(sort(folder, stream
                    .filter(path -> !ORDER_FILE.equals(path.getFileName().toString())).toList()));
        }
        children.removeIf(path -> path.getFileName().equals(item.getFileName()));
        children.add(Math.max(0, Math.min(index, children.size())), item);
        Files.write(folder.resolve(ORDER_FILE),
                children.stream().map(path -> path.getFileName().toString()).toList(), StandardCharsets.UTF_8);
    }
}
