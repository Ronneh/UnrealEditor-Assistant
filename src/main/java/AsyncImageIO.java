import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.SwingWorker;

/** Performs image file I/O away from Swing's event-dispatch thread. */
public final class AsyncImageIO {
    private AsyncImageIO() { }

    public static void load(File file, Consumer<BufferedImage> onSuccess, Consumer<Exception> onError) {
        new SwingWorker<BufferedImage, Void>() {
            @Override protected BufferedImage doInBackground() throws Exception {
                BufferedImage image = ImageIO.read(file);
                if (image == null) throw new IllegalArgumentException("Unsupported image format.");
                return image;
            }

            @Override protected void done() {
                try {
                    onSuccess.accept(get());
                } catch (Exception exception) {
                    onError.accept(cause(exception));
                }
            }
        }.execute();
    }

    public static void savePng(BufferedImage image, File file, Runnable onSuccess,
                               Consumer<Exception> onError) {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                if (!ImageIO.write(image, "png", file)) {
                    throw new IllegalStateException("No PNG writer is available.");
                }
                return null;
            }

            @Override protected void done() {
                try {
                    get();
                    onSuccess.run();
                } catch (Exception exception) {
                    onError.accept(cause(exception));
                }
            }
        }.execute();
    }

    private static Exception cause(Exception exception) {
        if (exception instanceof ExecutionException execution
                && execution.getCause() instanceof Exception cause) {
            return cause;
        }
        return exception;
    }
}
