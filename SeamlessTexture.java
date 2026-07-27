import java.awt.image.BufferedImage;

/** Creates a seamless texture by mirroring one source quarter in both directions. */
public final class SeamlessTexture {

    private SeamlessTexture() {
    }

    /**
     * Uses the complete source image as the upper-left quarter of the new texture.
     */
    public static BufferedImage createMirroredTexture(BufferedImage source) {
        if (source == null) {
            throw new IllegalArgumentException("The source image must not be null.");
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        int outputWidth = sourceWidth * 2;
        int outputHeight = sourceHeight * 2;

        BufferedImage output = new BufferedImage(
                outputWidth,
                outputHeight,
                BufferedImage.TYPE_INT_ARGB
        );

        for (int y = 0; y < outputHeight; y++) {
            int sourceY = y < sourceHeight
                    ? y
                    : outputHeight - 1 - y;

            for (int x = 0; x < outputWidth; x++) {
                int sourceX = x < sourceWidth
                        ? x
                        : outputWidth - 1 - x;

                output.setRGB(x, y, source.getRGB(sourceX, sourceY));
            }
        }

        return output;
    }
}
