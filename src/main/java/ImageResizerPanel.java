import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
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
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Embedded image resizing and color-adjustment utility. */
public final class ImageResizerPanel extends JPanel {
    private static final Integer[] SIZES = { 2048, 1024, 512, 256, 128, 64, 32, 16 };
    private static final Preferences PREFS = Preferences.userNodeForPackage(ImageResizerPanel.class);
    private static final String LAST_OPEN_DIRECTORY = "lastOpenDirectory";
    private static final String LAST_EXPORT_DIRECTORY = "lastExportDirectory";
    private final Preview preview = new Preview();
    private final JLabel details = new JLabel("No image loaded", SwingConstants.CENTER);
    private final JComboBox<Integer> size = new JComboBox<>(SIZES);
    private final JSlider brightness = slider();
    private final JSlider contrast = slider();
    private final JSlider saturation = slider();
    private final JSlider hue = slider();
    private final JSlider sharpness = slider();
    private final Timer adjustmentTimer = new Timer(120, event -> refresh());
    private BufferedImage loadedSource;
    private BufferedImage original;
    private BufferedImage processed;
    private boolean cropPreviewDirty;
    private double cropWidthScale = 1.0;
    private double cropHeightScale = 1.0;
    private double cropCenterX = 0.5;
    private double cropCenterY = 0.5;

    public ImageResizerPanel() {
        super(new BorderLayout(16, 16));
        setBackground(AssistantTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(18, 22, 20, 22));
        adjustmentTimer.setRepeats(false);

        JLabel heading = new JLabel("Image Resizer");
        AssistantTheme.stylePageTitle(heading);
        JPanel title = new JPanel(new BorderLayout());
        title.setOpaque(false);
        title.add(heading, BorderLayout.WEST);
        title.add(new JLabel("Prepare square PNG textures", SwingConstants.RIGHT), BorderLayout.EAST);
        add(title, BorderLayout.NORTH);

        JPanel previewCard = AssistantTheme.card(new BorderLayout(8, 8));
        preview.setPreferredSize(new Dimension(650, 560));
        preview.setTransferHandler(new ImageDropHandler());
        previewCard.add(preview, BorderLayout.CENTER);
        previewCard.add(details, BorderLayout.SOUTH);
        add(previewCard, BorderLayout.CENTER);
        add(createControls(), BorderLayout.EAST);
        installPasteShortcut();
    }

    private JPanel createControls() {
        JPanel controls = AssistantTheme.card(new BorderLayout(0, 15));
        controls.setPreferredSize(new Dimension(300, 0));
        JLabel title = new JLabel("Output & adjustments");
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 17f));
        controls.add(title, BorderLayout.NORTH);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new javax.swing.BoxLayout(rows, javax.swing.BoxLayout.Y_AXIS));
        JButton open = new JButton("Open");
        open.addActionListener(this::openImage);
        JButton paste = new JButton("Paste");
        paste.addActionListener(this::pasteImage);
        JButton reset = new JButton("Reset");
        reset.addActionListener(event -> reset());
        JPanel importButtons = new JPanel(new GridLayout(1, 3, 7, 0));
        importButtons.setOpaque(false);
        importButtons.setPreferredSize(new Dimension(260, 32));
        importButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        importButtons.add(open);
        importButtons.add(paste);
        importButtons.add(reset);
        rows.add(importButtons);
        rows.add(javax.swing.Box.createVerticalStrut(8));

        JPanel transformButtons = new JPanel(new GridLayout(1, 3, 7, 0));
        transformButtons.setOpaque(false);
        transformButtons.setPreferredSize(new Dimension(260, 32));
        transformButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JButton rotate = new JButton("90°");
        rotate.addActionListener(event -> rotateSource());
        transformButtons.add(rotate);
        JButton mirror = new JButton("Mirror");
        mirror.setToolTipText("Mirror horizontally");
        mirror.addActionListener(event -> mirrorSource());
        transformButtons.add(mirror);
        JPanel emptySlot = new JPanel();
        emptySlot.setOpaque(false);
        transformButtons.add(emptySlot);
        rows.add(transformButtons);
        rows.add(javax.swing.Box.createVerticalStrut(8));

        JPanel sizeRow = new JPanel(new EdgeAlignedFlowLayout(FlowLayout.LEFT, 7, 0));
        sizeRow.setOpaque(false);
        sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        sizeRow.setPreferredSize(new Dimension(260, 30));
        sizeRow.add(new JLabel("Size:"));
        size.setPreferredSize(new Dimension(80, 28));
        sizeRow.add(size);
        rows.add(sizeRow);
        rows.add(javax.swing.Box.createVerticalStrut(12));
        rows.add(control("Brightness", brightness));
        rows.add(control("Contrast", contrast));
        rows.add(control("Saturation", saturation));
        rows.add(control("Hue", hue));
        rows.add(control("Sharpness", sharpness));
        rows.add(javax.swing.Box.createVerticalGlue());
        controls.add(rows, BorderLayout.CENTER);

        JButton save = new JButton("Export");
        save.addActionListener(this::saveImage);
        JButton copy = new JButton("Copy to clipboard");
        copy.addActionListener(this::copyImage);
        JPanel outputButtons = new JPanel(new GridLayout(1, 2, 7, 0));
        outputButtons.setOpaque(false);
        outputButtons.add(save);
        outputButtons.add(copy);
        controls.add(outputButtons, BorderLayout.SOUTH);
        size.addActionListener(event -> scheduleRefresh());
        javax.swing.event.ChangeListener listener = event -> scheduleRefresh();
        brightness.addChangeListener(listener);
        contrast.addChangeListener(listener);
        saturation.addChangeListener(listener);
        hue.addChangeListener(listener);
        sharpness.addChangeListener(listener);
        return controls;
    }

    private JPanel control(String label, JSlider slider) {
        JPanel panel = new JPanel(new BorderLayout(0, 2));
        panel.setOpaque(false);
        panel.add(new JLabel(label), BorderLayout.NORTH);
        panel.add(slider, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        return panel;
    }

    private static JSlider slider() {
        JSlider slider = new JSlider(-100, 100, 0);
        slider.setMajorTickSpacing(100);
        slider.setPaintTicks(true);
        return slider;
    }

    private void openImage(ActionEvent ignored) {
        JFileChooser chooser = new DarkFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPG, BMP, GIF)", "png", "jpg", "jpeg", "bmp", "gif"));
        chooser.setCurrentDirectory(FileSaveSupport.preferredDirectory(
                PREFS.get(LAST_OPEN_DIRECTORY, null),
                new File(System.getProperty("user.home"))));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        File directory = file.getAbsoluteFile().getParentFile();
        if (directory != null) PREFS.put(LAST_OPEN_DIRECTORY, directory.getAbsolutePath());
        details.setText("Loading image...");
        AsyncImageIO.load(file, this::setImage,
                exception -> showError("Could not open image", exception));
    }

    private void pasteImage(ActionEvent ignored) {
        ClipboardImageSupport.paste(
                this::setImage,
                exception -> showError("Could not paste image", exception));
    }

    private void setImage(BufferedImage image) {
        loadedSource = image;
        original = image;
        resetView();
        updateAvailableSizes();
        resetAdjustmentSliders();
    }

    private void rotateSource() {
        if (original == null) {
            DarkDialogs.message(this, "Open or paste an image first.",
                    "No image", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        original = ImageToolSupport.rotateClockwise(original);
        refresh();
    }

    private void mirrorSource() {
        if (original == null) {
            DarkDialogs.message(this, "Open or paste an image first.",
                    "No image", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        original = ImageToolSupport.mirrorHorizontal(original);
        refresh();
    }

    private void updateAvailableSizes() {
        int maximum = Math.min(original.getWidth(), original.getHeight());
        size.removeAllItems();
        for (Integer candidate : SIZES) {
            if (candidate <= maximum) size.addItem(candidate);
        }
        if (size.getItemCount() == 0) size.addItem(Math.max(1, maximum));
        size.setSelectedIndex(0);
    }

    private void installPasteShortcut() {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control V"), "pasteImage");
        getActionMap().put("pasteImage", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent event) {
                pasteImage(event);
            }
        });
    }

    private void reset() {
        if (loadedSource != null) {
            original = loadedSource;
            resetView();
            updateAvailableSizes();
        }
        resetAdjustmentSliders();
    }

    private void resetView() {
        cropWidthScale = 1.0;
        cropHeightScale = 1.0;
        cropCenterX = 0.5;
        cropCenterY = 0.5;
    }

    private void resetAdjustmentSliders() {
        brightness.setValue(0);
        contrast.setValue(0);
        saturation.setValue(0);
        hue.setValue(0);
        sharpness.setValue(0);
        adjustmentTimer.stop();
        refresh();
    }

    private void scheduleRefresh() {
        adjustmentTimer.restart();
    }

    private void refresh() {
        if (original == null) {
            processed = null;
            preview.repaint();
            return;
        }
        Integer selectedSize = (Integer) size.getSelectedItem();
        if (selectedSize == null) return;
        int outputSize = selectedSize;
        BufferedImage square = cropSquare(original);
        BufferedImage resized = ImageToolSupport.resize(square, outputSize, outputSize);
        processed = adjust(resized);
        cropPreviewDirty = false;
        details.setText(original.getWidth() + " × " + original.getHeight() + "  to  "
                + outputSize + " × " + outputSize + " PNG   |   Crop "
                + Math.round(cropWidthScale * 100) + "% × "
                + Math.round(cropHeightScale * 100) + "%");
        preview.repaint();
    }

    private BufferedImage cropSquare(BufferedImage input) {
        Rectangle crop = cropBounds(input);
        return input.getSubimage(crop.x, crop.y, crop.width, crop.height);
    }

    private Rectangle cropBounds(BufferedImage input) {
        int base = Math.min(input.getWidth(), input.getHeight());
        int width = Math.max(1, Math.min(input.getWidth(),
                (int) Math.round(base * cropWidthScale)));
        int height = Math.max(1, Math.min(input.getHeight(),
                (int) Math.round(base * cropHeightScale)));
        int x = (int) Math.round(cropCenterX * input.getWidth() - width / 2.0);
        int y = (int) Math.round(cropCenterY * input.getHeight() - height / 2.0);
        x = Math.max(0, Math.min(input.getWidth() - width, x));
        y = Math.max(0, Math.min(input.getHeight() - height, y));
        return new Rectangle(x, y, width, height);
    }

    private BufferedImage adjust(BufferedImage input) {
        BufferedImage output = new BufferedImage(input.getWidth(), input.getHeight(), BufferedImage.TYPE_INT_ARGB);
        float bright = brightness.getValue() * 1.5f;
        float contrastFactor = Math.max(0f, 1f + contrast.getValue() / 100f);
        float saturationFactor = Math.max(0f, 1f + saturation.getValue() / 100f);
        float hueOffset = hue.getValue() / 100f;
        for (int y=0; y<input.getHeight(); y++) {
            for (int x=0; x<input.getWidth(); x++) {
                int argb=input.getRGB(x,y), a=(argb>>>24)&255;
                int r=(argb>>>16)&255, g=(argb>>>8)&255, b=argb&255;
                r=clamp((r-128)*contrastFactor+128+bright);
                g=clamp((g-128)*contrastFactor+128+bright);
                b=clamp((b-128)*contrastFactor+128+bright);
                float[] hsb=Color.RGBtoHSB(r,g,b,null);
                hsb[0]=(hsb[0]+hueOffset+1f)%1f;
                hsb[1]=Math.max(0f,Math.min(1f,hsb[1]*saturationFactor));
                int rgb=Color.HSBtoRGB(hsb[0],hsb[1],hsb[2]);
                output.setRGB(x,y,(a<<24)|(rgb&0xffffff));
            }
        }
        int amount=sharpness.getValue();
        if (amount != 0) {
            float strength=amount/100f;
            float center=amount>0 ? 1f+4f*strength : 1f+strength;
            float neighbor=amount>0 ? -strength : -strength/4f;
            Kernel kernel=new Kernel(3,3,new float[]{0,neighbor,0,neighbor,center,neighbor,0,neighbor,0});
            output=new ConvolveOp(kernel,ConvolveOp.EDGE_NO_OP,null).filter(output,null);
        }
        return output;
    }

    private int clamp(float value) { return Math.max(0,Math.min(255,Math.round(value))); }

    private void saveImage(ActionEvent ignored) {
        if (processed == null) {
            DarkDialogs.message(this, "Open or paste an image first.", "No image", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new DarkFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images (PNG, BMP)", "png", "bmp"));
        chooser.setCurrentDirectory(FileSaveSupport.preferredDirectory(
                PREFS.get(LAST_EXPORT_DIRECTORY, null),
                new File(System.getProperty("user.home"))));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), "texture.png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = FileSaveSupport.ensureImageExtension(chooser.getSelectedFile());
        if (!FileSaveSupport.confirmOverwrite(this, file)) return;
        File exportDirectory = file.getAbsoluteFile().getParentFile();
        if (exportDirectory != null) PREFS.put(LAST_EXPORT_DIRECTORY, exportDirectory.getAbsolutePath());
        File targetFile = file;
        BufferedImage image = processed;
        details.setText("Saving " + targetFile.getName() + "...");
        AsyncImageIO.save(image, targetFile,
                () -> details.setText("Saved " + targetFile.getAbsolutePath()),
                exception -> showError("Could not export image", exception));
    }

    private void copyImage(ActionEvent ignored) {
        if (processed == null) {
            DarkDialogs.message(this, "Open or paste an image first.",
                    "No image", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(processed), null);
        details.setText("Result copied to clipboard");
    }

    private void showError(String title, Exception exception) {
        DarkDialogs.message(this, exception.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    private final class Preview extends JPanel {
        private static final int NONE = -1;
        private static final int MOVE = 0;
        private static final int LEFT = 1;
        private static final int RIGHT = 2;
        private static final int TOP = 4;
        private static final int BOTTOM = 8;
        private static final int HANDLE = 8;
        private final Rectangle displayedImage = new Rectangle();
        private final Rectangle displayedCrop = new Rectangle();
        private Point dragOffset;
        private Point dragStartPoint;
        private Rectangle dragStartCrop;
        private int dragMode = NONE;

        Preview() {
            setBackground(AssistantTheme.CODE_BACKGROUND);
            setBorder(BorderFactory.createLineBorder(AssistantTheme.BORDER));
            setToolTipText("Drag the crop box to move it. Scroll to resize it.");
            MouseAdapter navigation = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    dragMode = hitTest(event.getPoint());
                    if (dragMode == MOVE) {
                        dragOffset = new Point(event.getX() - displayedCrop.x,
                                event.getY() - displayedCrop.y);
                    } else if (dragMode != NONE) {
                        dragStartPoint = event.getPoint();
                        dragStartCrop = new Rectangle(displayedCrop);
                    }
                }

                @Override public void mouseDragged(MouseEvent event) {
                    if (original == null || dragMode == NONE || displayedImage.width <= 0) return;
                    cropPreviewDirty = true;
                    if (dragMode != MOVE) {
                        resizeCrop(event.getPoint());
                        repaint();
                        return;
                    }
                    int x = Math.max(displayedImage.x, Math.min(
                            event.getX() - dragOffset.x,
                            displayedImage.x + displayedImage.width - displayedCrop.width));
                    int y = Math.max(displayedImage.y, Math.min(
                            event.getY() - dragOffset.y,
                            displayedImage.y + displayedImage.height - displayedCrop.height));
                    cropCenterX = (x - displayedImage.x + displayedCrop.width / 2.0)
                            / displayedImage.width;
                    cropCenterY = (y - displayedImage.y + displayedCrop.height / 2.0)
                            / displayedImage.height;
                    repaint();
                }

                @Override public void mouseReleased(MouseEvent event) {
                    if (dragMode == NONE) return;
                    dragOffset = null;
                    dragStartPoint = null;
                    dragStartCrop = null;
                    dragMode = NONE;
                    refresh();
                }

                @Override public void mouseWheelMoved(MouseWheelEvent event) {
                    if (original == null) return;
                    double factor = 1.0 + event.getPreciseWheelRotation() * 0.05;
                    double base = Math.min(original.getWidth(), original.getHeight());
                    double maximumWidth = original.getWidth() / base;
                    double maximumHeight = original.getHeight() / base;
                    double maximumFactor = Math.min(
                            maximumWidth / cropWidthScale,
                            maximumHeight / cropHeightScale);
                    factor = Math.max(0.1 / Math.min(cropWidthScale, cropHeightScale),
                            Math.min(maximumFactor, factor));
                    cropWidthScale *= factor;
                    cropHeightScale *= factor;
                    cropPreviewDirty = true;
                    repaint();
                    scheduleRefresh();
                }
            };
            addMouseListener(navigation);
            addMouseMotionListener(navigation);
            addMouseWheelListener(navigation);
        }

        private int hitTest(Point point) {
            Rectangle hitArea = new Rectangle(displayedCrop);
            hitArea.grow(HANDLE, HANDLE);
            if (!hitArea.contains(point)) return NONE;
            int mode = 0;
            if (Math.abs(point.x - displayedCrop.x) <= HANDLE) mode |= LEFT;
            if (Math.abs(point.x - (displayedCrop.x + displayedCrop.width)) <= HANDLE) mode |= RIGHT;
            if (Math.abs(point.y - displayedCrop.y) <= HANDLE) mode |= TOP;
            if (Math.abs(point.y - (displayedCrop.y + displayedCrop.height)) <= HANDLE) mode |= BOTTOM;
            return mode == 0 && displayedCrop.contains(point) ? MOVE : mode;
        }

        private void resizeCrop(Point point) {
            if (dragStartCrop == null || dragStartPoint == null) return;
            int left = dragStartCrop.x;
            int top = dragStartCrop.y;
            int right = dragStartCrop.x + dragStartCrop.width;
            int bottom = dragStartCrop.y + dragStartCrop.height;
            boolean horizontal = (dragMode & (LEFT | RIGHT)) != 0;
            boolean vertical = (dragMode & (TOP | BOTTOM)) != 0;
            int deltaX = point.x - dragStartPoint.x;
            int deltaY = point.y - dragStartPoint.y;
            int requestedWidth = dragStartCrop.width
                    + ((dragMode & LEFT) != 0 ? -deltaX : deltaX);
            int requestedHeight = dragStartCrop.height
                    + ((dragMode & TOP) != 0 ? -deltaY : deltaY);
            int newWidth = horizontal ? Math.max(12, requestedWidth) : dragStartCrop.width;
            int newHeight = vertical ? Math.max(12, requestedHeight) : dragStartCrop.height;
            if (horizontal && vertical) {
                double widthFactor = newWidth / (double) dragStartCrop.width;
                double heightFactor = newHeight / (double) dragStartCrop.height;
                double factor = (widthFactor + heightFactor) / 2.0;
                newWidth = Math.max(12, (int) Math.round(dragStartCrop.width * factor));
                newHeight = Math.max(12, (int) Math.round(dragStartCrop.height * factor));
            }
            newWidth = Math.min(displayedImage.width, newWidth);
            newHeight = Math.min(displayedImage.height, newHeight);
            int newX = (dragMode & LEFT) != 0 ? right - newWidth : left;
            int newY = (dragMode & TOP) != 0 ? bottom - newHeight : top;
            newX = Math.max(displayedImage.x,
                    Math.min(displayedImage.x + displayedImage.width - newWidth, newX));
            newY = Math.max(displayedImage.y,
                    Math.min(displayedImage.y + displayedImage.height - newHeight, newY));
            int displayedBase = Math.min(displayedImage.width, displayedImage.height);
            cropWidthScale = newWidth / (double) displayedBase;
            cropHeightScale = newHeight / (double) displayedBase;
            cropCenterX = (newX - displayedImage.x + newWidth / 2.0) / displayedImage.width;
            cropCenterY = (newY - displayedImage.y + newHeight / 2.0) / displayedImage.height;
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (original==null) {
                graphics.setColor(AssistantTheme.MUTED);
                graphics.drawString("Open or paste an image to begin",24,35);
                return;
            }
            int maxW=getWidth()-30,maxH=getHeight()-30;
            double scale=Math.min((double)maxW/original.getWidth(),(double)maxH/original.getHeight());
            int w=(int)(original.getWidth()*scale),h=(int)(original.getHeight()*scale);
            displayedImage.setBounds((getWidth()-w)/2,(getHeight()-h)/2,w,h);
            Rectangle crop = cropBounds(original);
            displayedCrop.setBounds(
                    displayedImage.x + (int) Math.round(crop.x * scale),
                    displayedImage.y + (int) Math.round(crop.y * scale),
                    Math.max(1, (int) Math.round(crop.width * scale)),
                    Math.max(1, (int) Math.round(crop.height * scale)));
            Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(original,displayedImage.x,displayedImage.y,w,h,null);
            if (processed != null && !cropPreviewDirty) {
                g.drawImage(processed,
                        displayedCrop.x, displayedCrop.y,
                        displayedCrop.width, displayedCrop.height, null);
            }
            g.setColor(new Color(0, 0, 0, 145));
            g.fillRect(displayedImage.x, displayedImage.y, displayedImage.width,
                    displayedCrop.y - displayedImage.y);
            g.fillRect(displayedImage.x, displayedCrop.y,
                    displayedCrop.x - displayedImage.x, displayedCrop.height);
            g.fillRect(displayedCrop.x + displayedCrop.width, displayedCrop.y,
                    displayedImage.x + displayedImage.width - displayedCrop.x - displayedCrop.width,
                    displayedCrop.height);
            g.fillRect(displayedImage.x, displayedCrop.y + displayedCrop.height,
                    displayedImage.width,
                    displayedImage.y + displayedImage.height - displayedCrop.y - displayedCrop.height);
            g.setColor(new Color(255, 255, 255, 220));
            g.drawRect(displayedCrop.x, displayedCrop.y,
                    displayedCrop.width - 1, displayedCrop.height - 1);
            int half = 3;
            int centerX = displayedCrop.x + displayedCrop.width / 2;
            int centerY = displayedCrop.y + displayedCrop.height / 2;
            int right = displayedCrop.x + displayedCrop.width;
            int bottom = displayedCrop.y + displayedCrop.height;
            int[] handleX = { displayedCrop.x, centerX, right };
            int[] handleY = { displayedCrop.y, centerY, bottom };
            for (int x : handleX) {
                for (int y : handleY) {
                    if (x == centerX && y == centerY) continue;
                    g.fillRect(x - half, y - half, half * 2 + 1, half * 2 + 1);
                }
            }
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
                        setImage(ImageToolSupport.toBuffered(image));
                        return true;
                    }
                }
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) support.getTransferable()
                        .getTransferData(DataFlavor.javaFileListFlavor);
                if (files.isEmpty()) return false;
                details.setText("Loading image...");
                AsyncImageIO.load(files.get(0), ImageResizerPanel.this::setImage,
                        exception -> showError("Could not import dropped image", exception));
                return true;
            } catch (Exception exception) {
                showError("Could not import dropped image", exception);
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
