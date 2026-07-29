import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;

/** Image conversion and high-quality resizing shared by the image tools. */
public final class ImageToolSupport {
    private ImageToolSupport() { }

    public static BufferedImage toBuffered(java.awt.Image source) {
        if (source instanceof BufferedImage buffered) return buffered;
        ImageIcon loaded = new ImageIcon(source);
        int width = loaded.getIconWidth();
        int height = loaded.getIconHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("The clipboard image could not be decoded.");
        }
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        loaded.paintIcon(null, g, 0, 0);
        g.dispose();
        return result;
    }

    public static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(source, 0, 0, width, height, null);
        g.dispose();
        return result;
    }
}
