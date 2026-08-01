import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;

/** Adds MOVE drag-and-drop and an insertion line to a filesystem-backed JTree. */
public final class FileTreeReorderHandler extends TransferHandler {
    private static final DataFlavor PATH_FLAVOR = new DataFlavor(Path.class, "Application Path");
    private final JTree tree;
    private final Path root;
    private final Function<Object, Path> pathOf;
    private final Consumer<Path> reload;
    private final Consumer<Exception> error;
    private final TransferHandler external;
    private final Predicate<Path> movable;

    public FileTreeReorderHandler(JTree tree, Path root, Function<Object, Path> pathOf,
            Consumer<Path> reload, Consumer<Exception> error, TransferHandler external) {
        this(tree, root, pathOf, reload, error, external, path -> true);
    }

    public FileTreeReorderHandler(JTree tree, Path root, Function<Object, Path> pathOf,
            Consumer<Path> reload, Consumer<Exception> error, TransferHandler external,
            Predicate<Path> movable) {
        this.tree = tree; this.root = root; this.pathOf = pathOf; this.reload = reload;
        this.error = error; this.external = external; this.movable = movable;
    }

    @Override protected Transferable createTransferable(JComponent component) {
        Path path = selectedPath();
        if (path == null || path.equals(root) || !movable.test(path)) return null;
        return new Transferable() {
            @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] { PATH_FLAVOR }; }
            @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return PATH_FLAVOR.equals(flavor); }
            @Override public Object getTransferData(DataFlavor flavor) { return path; }
        };
    }

    @Override public int getSourceActions(JComponent component) { return MOVE; }

    @Override public boolean canImport(TransferSupport support) {
        if (support.isDataFlavorSupported(PATH_FLAVOR)) {
            if (support.isDrop()) support.setDropAction(MOVE);
            return support.getDropLocation() instanceof JTree.DropLocation location
                    && location.getPath() != null;
        }
        return external != null && external.canImport(support);
    }

    @Override public boolean importData(TransferSupport support) {
        if (!support.isDataFlavorSupported(PATH_FLAVOR)) {
            if (support.isDrop() && support.getDropLocation() instanceof JTree.DropLocation location
                    && location.getPath() != null) tree.setSelectionPath(location.getPath());
            return external != null && external.importData(support);
        }
        try {
            Path source = (Path) support.getTransferable().getTransferData(PATH_FLAVOR);
            if (!movable.test(source)) return false;
            JTree.DropLocation location = (JTree.DropLocation) support.getDropLocation();
            DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) location.getPath().getLastPathComponent();
            Path folder = pathOf.apply(parentNode.getUserObject());
            if (!Files.isDirectory(folder) || folder.startsWith(source)) return false;
            Path target = folder.resolve(source.getFileName());
            if (!source.getParent().equals(folder)) {
                if (Files.exists(target)) throw new IOException("An item with this name already exists.");
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            } else target = source;
            FileTreeOrder.place(folder, target, location.getChildIndex());
            reload.accept(target);
            return true;
        } catch (Exception exception) { error.accept(exception); return false; }
    }

    private Path selectedPath() {
        Object selected = tree.getLastSelectedPathComponent();
        return selected instanceof DefaultMutableTreeNode node ? pathOf.apply(node.getUserObject()) : null;
    }
}
