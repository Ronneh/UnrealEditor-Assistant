import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.junit.jupiter.api.Test;

class GifMakerPanelTest {
    @Test
    void writesEveryFrameAsAnAnimatedGif() throws Exception {
        BufferedImage first = image(Color.CYAN);
        BufferedImage second = image(Color.MAGENTA);
        File target = Files.createTempFile("mapping-assistant-", ".gif").toFile();

        GifMakerPanel.writeGif(target, List.of(first, second), List.of(120, 340), 0);

        assertTrue(target.length() > 0);
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        try (ImageInputStream input = ImageIO.createImageInputStream(target)) {
            reader.setInput(input);
            assertEquals(2, reader.getNumImages(true));
            assertEquals(32, reader.getWidth(0));
            assertEquals(18, reader.getHeight(0));
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage image(Color color) {
        BufferedImage image = new BufferedImage(32, 18, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return image;
    }
}
