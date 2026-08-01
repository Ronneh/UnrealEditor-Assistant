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
import java.util.List;
import java.util.prefs.Preferences;
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
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Creates a square tileable texture by mirroring a centered source crop. */
public final class SeamlessTexturePanel extends JPanel {
    private static final Integer[] OUTPUT_SIZES = { 128, 256, 512, 1024 };
    private static final Preferences PREFS = Preferences.userNodeForPackage(SeamlessTexturePanel.class);
    private static final String LAST_OPEN_DIRECTORY = "lastOpenDirectory";
    private final ImageCanvas inputCanvas = new ImageCanvas("Load or Paste an image.");
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
        inputCanvas.setTransferHandler(new ImageDropHandler());
        installPasteShortcut();
    }

    private JPanel createControls() {
        JPanel controls = new JPanel(new BorderLayout(12, 0));
        controls.setOpaque(false);
        JPanel sourceActions = new JPanel(new EdgeAlignedFlowLayout(java.awt.FlowLayout.LEFT, 7, 0));
        sourceActions.setOpaque(false);
        sourceActions.add(button("Paste", event -> pasteImage()));
        sourceActions.add(button("Load image...", event -> loadImage()));
        sourceActions.add(new JLabel("Output size:"));
        outputSize.setSelectedItem(512);
        outputSize.setPreferredSize(new Dimension(90, 26));
        outputSize.addActionListener(event -> {
            if (source != null) generate();
        });
        sourceActions.add(outputSize);
        sourceActions.add(new JLabel("px"));
        sourceActions.add(button("Generate", event -> generate()));
        controls.add(sourceActions, BorderLayout.WEST);
        JPanel resultActions = new JPanel(new EdgeAlignedFlowLayout(java.awt.FlowLayout.RIGHT, 7, 0));
        resultActions.setOpaque(false);
        resultActions.add(button("Save PNG...", event -> saveResult()));
        resultActions.add(button("Copy result", event -> copyResult()));
        controls.add(resultActions, BorderLayout.EAST);
        status.setForeground(AssistantTheme.MUTED);
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setBackground(AssistantTheme.BACKGROUND);
        JLabel heading = new JLabel("Seamless Texture");
        AssistantTheme.stylePageTitle(heading);
        header.add(heading, BorderLayout.NORTH);
        header.add(controls, BorderLayout.CENTER);
        header.add(status, BorderLayout.SOUTH);
        return header;
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
        JFileChooser chooser = new DarkFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Images (PNG, JPG, BMP)", "png", "jpg", "jpeg", "bmp"));
        chooser.setCurrentDirectory(FileSaveSupport.preferredDirectory(
                PREFS.get(LAST_OPEN_DIRECTORY, null),
                new File(System.getProperty("user.home"))));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        rememberOpenDirectory(file);
        status.setForeground(AssistantTheme.MUTED);
        status.setText("Loading " + file.getName() + "...");
        AsyncImageIO.load(file,
                image -> setSource(image, file.getName()),
                exception -> showInputError("The selected file could not be loaded."));
    }

    private void rememberOpenDirectory(File file) {
        File directory = file.getAbsoluteFile().getParentFile();
        if (directory != null) PREFS.put(LAST_OPEN_DIRECTORY, directory.getAbsolutePath());
    }

    private void pasteImage() {
        ClipboardImageSupport.paste(
                image -> setSource(image, "clipboard"),
                exception -> showInputError("The clipboard does not contain a supported image."));
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
        JFileChooser chooser = new DarkFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG image", "png"));
        chooser.setSelectedFile(new File("seamless-texture.png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".png"))
            file = new File(file.getParentFile(), file.getName() + ".png");
        if (!FileSaveSupport.confirmOverwrite(this, file)) return;
        File targetFile = file;
        BufferedImage image = result;
        status.setForeground(AssistantTheme.MUTED);
        status.setText("Saving " + targetFile.getName() + "...");
        AsyncImageIO.savePng(image, targetFile, () -> {
            status.setForeground(new Color(94, 205, 130));
            status.setText("Saved " + targetFile.getName() + ".");
        }, exception -> DarkDialogs.message(this, "The PNG could not be saved.",
                "Save texture", JOptionPane.ERROR_MESSAGE));
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

    private final class ImageDropHandler extends TransferHandler {
        @Override public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.imageFlavor)
                    || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                if (support.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    Object value = support.getTransferable().getTransferData(DataFlavor.imageFlavor);
                    if (value instanceof Image image) {
                        setSource(ImageToolSupport.toBuffered(image), "drag and drop");
                        return true;
                    }
                }
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) support.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);
                if (files.isEmpty()) return false;
                File file = files.get(0);
                rememberOpenDirectory(file);
                status.setForeground(AssistantTheme.MUTED);
                status.setText("Loading " + file.getName() + "...");
                AsyncImageIO.load(file,
                        image -> setSource(image, file.getName()),
                        exception -> showInputError("The dropped file could not be loaded."));
                return true;
            } catch (Exception exception) {
                showInputError("The dropped content is not a supported image.");
                return false;
            }
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
