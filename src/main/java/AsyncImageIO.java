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

    public static void save(BufferedImage image, File file, Runnable onSuccess,
                            Consumer<Exception> onError) {
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                String format = FileSaveSupport.imageFormat(file);
                BufferedImage output = format.equals("bmp") ? withoutAlpha(image) : image;
                if (!ImageIO.write(output, format, file)) {
                    throw new IllegalStateException("No " + format.toUpperCase() + " writer is available.");
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

    private static BufferedImage withoutAlpha(BufferedImage image) {
        BufferedImage output = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) output.setRGB(x, y, image.getRGB(x, y));
        }
        return output;
    }

    private static Exception cause(Exception exception) {
        if (exception instanceof ExecutionException execution
                && execution.getCause() instanceof Exception cause) {
            return cause;
        }
        return exception;
    }
}
