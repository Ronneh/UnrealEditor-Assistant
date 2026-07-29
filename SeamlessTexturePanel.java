import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Creates a square tileable texture by mirroring a centered source crop. */
public final class SeamlessTexturePanel extends JPanel {
    private static final Integer[] OUTPUT_SIZES = { 128, 256, 512, 1024 };
    private final ImageCanvas inputCanvas = new ImageCanvas("Load or paste a source image.");
    private final ImageCanvas outputCanvas = new ImageCanvas("The seamless result will appear here.");
    private final JComboBox<Integer> outputSize = new JComboBox<>(OUTPUT_SIZES);
    private final JLabel status = new JLabel("Load an image or press Ctrl+V.");
    private BufferedImage source;
    private BufferedImage result;

    public SeamlessTexturePanel() {
        super(new BorderLayout(8, 8));
        setBackground(AssistantTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 14, 14));
        add(createControls(), BorderLayout.NORTH);
        add(createWorkspace(), BorderLayout.CENTER);
        installPasteShortcut();
    }

    private JPanel createControls() {
        JPanel controls = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 7, 0));
        controls.setOpaque(false);
        controls.add(button("Load image...", event -> loadImage()));
        controls.add(button("Paste", event -> pasteImage()));
        controls.add(new JLabel("Output size:"));
        outputSize.setSelectedItem(512);
        outputSize.setPreferredSize(new Dimension(90, 26));
        outputSize.addActionListener(event -> {
            if (source != null) generate();
        });
        controls.add(outputSize);
        controls.add(new JLabel("px"));
        controls.add(button("Generate", event -> generate()));
        controls.add(button("Copy result", event -> copyResult()));
        controls.add(button("Save PNG...", event -> saveResult()));
        status.setForeground(AssistantTheme.MUTED);
        controls.add(status);
        return controls;
    }

    private JComponent createWorkspace() {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                titledCanvas("Source", inputCanvas), titledCanvas("Seamless result", outputCanvas));
        split.setResizeWeight(0.5);
        AssistantTheme.styleSplitPane(split);
        return split;
    }

    private JScrollPane titledCanvas(String title, ImageCanvas canvas) {
        JScrollPane scroll = new JScrollPane(canvas);
        scroll.setBorder(AssistantTheme.titled(title));
        return scroll;
    }

    private JButton button(String text, java.util.function.Consumer<ActionEvent> action) {
        JButton button = new JButton(text);
        button.addActionListener(action::accept);
        return button;
    }

    private void installPasteShortcut() {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control V"), "pasteTexture");
        getActionMap().put("pasteTexture", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) { pasteImage(); }
        });
    }

    private void loadImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Images (PNG, JPG, BMP)", "png", "jpg", "jpeg", "bmp"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            BufferedImage image = ImageIO.read(chooser.getSelectedFile());
            if (image == null) throw new IllegalArgumentException("Unsupported image format.");
            setSource(image, chooser.getSelectedFile().getName());
        } catch (Exception exception) {
            showInputError("The selected file could not be loaded.");
        }
    }

    private void pasteImage() {
        try {
            Object value = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.imageFlavor);
            if (!(value instanceof Image image)) {
                showInputError("The clipboard does not contain a supported image.");
                return;
            }
            setSource(ImageToolSupport.toBuffered(image), "clipboard");
        } catch (Exception exception) {
            showInputError("The clipboard does not contain a supported image.");
        }
    }

    private void setSource(BufferedImage image, String origin) {
        source = ensureArgb(image);
        inputCanvas.image = source;
        inputCanvas.repaint();
        status.setForeground(AssistantTheme.MUTED);
        status.setText("Loaded " + source.getWidth() + "×" + source.getHeight() + " from " + origin + ".");
        generate();
    }

    private void generate() {
        if (source == null) {
            showInputError("Load or paste an image first.");
            return;
        }
        try {
            result = makeSeamless(source, (Integer) outputSize.getSelectedItem());
        } catch (IllegalArgumentException exception) {
            result = null;
            outputCanvas.image = null;
            outputCanvas.repaint();
            showInputError(exception.getMessage());
            return;
        }
        outputCanvas.image = result;
        outputCanvas.repaint();
        status.setForeground(new Color(94, 205, 130));
        status.setText("Seamless texture generated at " + result.getWidth() + "×" + result.getHeight() + ".");
    }

    /**
     * Crops a centered square without distorting the source, scales it to one
     * quarter of the requested output, and mirrors it across both center axes.
     */
    static BufferedImage makeSeamless(BufferedImage input, int outputSize) {
        int cropSize = Math.min(input.getWidth(), input.getHeight());
        int cropX = (input.getWidth() - cropSize) / 2;
        int cropY = (input.getHeight() - cropSize) / 2;
        BufferedImage squareCrop = input.getSubimage(cropX, cropY, cropSize, cropSize);
        BufferedImage quarter = ImageToolSupport.resize(
                squareCrop,
                outputSize / 2,
                outputSize / 2
        );
        return SeamlessTexture.createMirroredTexture(quarter);
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    private static BufferedImage ensureArgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) return image;
        return copy(image);
    }

    private void copyResult() {
        if (result == null) {
            showInputError("Generate a seamless texture first.");
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                new ImageTransferable(result), null);
        status.setForeground(new Color(94, 205, 130));
        status.setText("Seamless texture copied to the clipboard.");
    }

    private void saveResult() {
        if (result == null) {
            showInputError("Generate a seamless texture first.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG image", "png"));
        chooser.setSelectedFile(new File("seamless-texture.png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".png"))
            file = new File(file.getParentFile(), file.getName() + ".png");
        try {
            ImageIO.write(result, "png", file);
            status.setForeground(new Color(94, 205, 130));
            status.setText("Saved " + file.getName() + ".");
        } catch (Exception exception) {
            JOptionPane.showMessageDialog(this, "The PNG could not be saved.",
                    "Save texture", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showInputError(String message) {
        status.setForeground(new Color(225, 105, 105));
        status.setText(message);
    }

    private static final class ImageCanvas extends JPanel {
        private final String emptyText;
        private BufferedImage image;

        ImageCanvas(String emptyText) {
            this.emptyText = emptyText;
            setBackground(AssistantTheme.CODE_BACKGROUND);
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (image == null) {
                graphics.setColor(AssistantTheme.MUTED);
                graphics.drawString(emptyText, 20, 30);
                return;
            }
            int margin = 18;
            int availableWidth = Math.max(1, getWidth() - margin * 2);
            int availableHeight = Math.max(1, getHeight() - margin * 2);
            double scale = Math.min(availableWidth / (double) image.getWidth(),
                    availableHeight / (double) image.getHeight());
            int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
            int x = (getWidth() - width) / 2;
            int y = (getHeight() - height) / 2;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, x, y, width, height, null);
            g.setColor(AssistantTheme.BORDER);
            g.drawRect(x, y, width - 1, height - 1);
            g.dispose();
        }
    }

    private record ImageTransferable(Image image) implements Transferable {
        @Override public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { DataFlavor.imageFlavor };
        }

        @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return image;
        }
    }
}
