import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/** Reads images and copied image files from the system clipboard. */
public final class ClipboardImageSupport {
    private ClipboardImageSupport() { }

    public static void paste(Consumer<BufferedImage> onSuccess, Consumer<Exception> onError) {
        try {
            Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (contents == null) throw unsupported();

            if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Object value = contents.getTransferData(DataFlavor.imageFlavor);
                if (value instanceof Image image) {
                    onSuccess.accept(ImageToolSupport.toBuffered(image));
                    return;
                }
            }

            if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                Object value = contents.getTransferData(DataFlavor.javaFileListFlavor);
                if (value instanceof List<?> files && !files.isEmpty() && files.get(0) instanceof File file) {
                    AsyncImageIO.load(file, onSuccess, onError);
                    return;
                }
            }

            for (DataFlavor flavor : contents.getTransferDataFlavors()) {
                if (!flavor.isMimeTypeEqual("image/png")
                        && !flavor.isMimeTypeEqual("image/jpeg")
                        && !flavor.isMimeTypeEqual("image/gif")) {
                    continue;
                }
                Object value = contents.getTransferData(flavor);
                BufferedImage image = readEncodedImage(value);
                if (image != null) {
                    onSuccess.accept(image);
                    return;
                }
            }

            if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Object value = contents.getTransferData(DataFlavor.stringFlavor);
                if (value instanceof String text) {
                    File file = copiedPath(text);
                    if (file != null) {
                        AsyncImageIO.load(file, onSuccess, onError);
                        return;
                    }
                }
            }
            throw unsupported();
        } catch (Exception exception) {
            onError.accept(exception);
        }
    }

    private static BufferedImage readEncodedImage(Object value) throws Exception {
        if (value instanceof InputStream stream) {
            try (stream) {
                return ImageIO.read(stream);
            }
        }
        if (value instanceof byte[] bytes) {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        }
        return null;
    }

    static File copiedPath(String clipboardText) {
        String value = clipboardText.lines().filter(line -> !line.isBlank()).findFirst().orElse("").trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            Path path = value.startsWith("file:") ? Path.of(URI.create(value)) : Path.of(value);
            File file = path.toFile();
            return file.isFile() ? file : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static IllegalArgumentException unsupported() {
        return new IllegalArgumentException("The clipboard does not contain a supported image.");
    }
}
