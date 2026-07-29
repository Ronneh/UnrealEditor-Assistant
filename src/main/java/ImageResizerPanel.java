import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.IOException;
import java.util.List;
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
    private static final Integer[] SIZES = { 1024, 512, 256, 128, 64, 32, 16 };
    private final Preview preview = new Preview();
    private final JLabel details = new JLabel("No image loaded", SwingConstants.CENTER);
    private final JComboBox<Integer> size = new JComboBox<>(SIZES);
    private final JSlider brightness = slider();
    private final JSlider contrast = slider();
    private final JSlider saturation = slider();
    private final JSlider hue = slider();
    private final JSlider sharpness = slider();
    private final Timer adjustmentTimer = new Timer(120, event -> refresh());
    private BufferedImage original;
    private BufferedImage processed;

    public ImageResizerPanel() {
        super(new BorderLayout(16, 16));
        setBackground(AssistantTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(18, 22, 20, 22));
        adjustmentTimer.setRepeats(false);

        JLabel heading = new JLabel("Image Resizer");
        heading.setFont(heading.getFont().deriveFont(java.awt.Font.BOLD, 23f));
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
        JButton open = new JButton("Open image...");
        open.addActionListener(this::openImage);
        JButton paste = new JButton("Paste image");
        paste.addActionListener(this::pasteImage);
        JPanel importButtons = new JPanel(new GridLayout(1, 2, 7, 0));
        importButtons.setOpaque(false);
        importButtons.setPreferredSize(new Dimension(260, 32));
        importButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        importButtons.add(open);
        importButtons.add(paste);
        rows.add(importButtons);
        rows.add(javax.swing.Box.createVerticalStrut(15));

        JPanel sizeRow = new JPanel(new BorderLayout(8, 0));
        sizeRow.setOpaque(false);
        sizeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        sizeRow.setPreferredSize(new Dimension(260, 30));
        sizeRow.add(new JLabel("Square size"), BorderLayout.WEST);
        size.setPreferredSize(new Dimension(110, 28));
        size.setMaximumSize(new Dimension(110, 28));
        sizeRow.add(size, BorderLayout.EAST);
        rows.add(sizeRow);
        rows.add(javax.swing.Box.createVerticalStrut(12));
        rows.add(control("Brightness", brightness));
        rows.add(control("Contrast", contrast));
        rows.add(control("Saturation", saturation));
        rows.add(control("Hue", hue));
        rows.add(control("Sharpness", sharpness));
        rows.add(javax.swing.Box.createVerticalStrut(8));
        JButton reset = new JButton("Reset adjustments");
        reset.addActionListener(event -> resetAdjustments());
        rows.add(reset);
        rows.add(javax.swing.Box.createVerticalGlue());
        controls.add(rows, BorderLayout.CENTER);

        JButton save = new JButton("Export PNG...");
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
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPG, BMP, GIF)", "png", "jpg", "jpeg", "bmp", "gif"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        details.setText("Loading image...");
        AsyncImageIO.load(chooser.getSelectedFile(), this::setImage,
                exception -> showError("Could not open image", exception));
    }

    private void pasteImage(ActionEvent ignored) {
        ClipboardImageSupport.paste(
                this::setImage,
                exception -> showError("Could not paste image", exception));
    }

    private void setImage(BufferedImage image) {
        original = image;
        updateAvailableSizes();
        resetAdjustments();
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

    private void resetAdjustments() {
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
        details.setText(original.getWidth() + " × " + original.getHeight() + "  to  "
                + outputSize + " × " + outputSize + " PNG");
        preview.repaint();
    }

    private BufferedImage cropSquare(BufferedImage input) {
        int side = Math.min(input.getWidth(), input.getHeight());
        return input.getSubimage((input.getWidth()-side)/2, (input.getHeight()-side)/2, side, side);
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
            JOptionPane.showMessageDialog(this, "Open or paste an image first.", "No image", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG image", "png"));
        chooser.setSelectedFile(new File("texture.png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file=chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".png")) file=new File(file.getParentFile(),file.getName()+".png");
        if (!FileSaveSupport.confirmOverwrite(this, file)) return;
        File targetFile = file;
        BufferedImage image = processed;
        details.setText("Saving " + targetFile.getName() + "...");
        AsyncImageIO.savePng(image, targetFile,
                () -> details.setText("Saved " + targetFile.getAbsolutePath()),
                exception -> showError("Could not save PNG", exception));
    }

    private void copyImage(ActionEvent ignored) {
        if (processed == null) {
            JOptionPane.showMessageDialog(this, "Open or paste an image first.",
                    "No image", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(processed), null);
        details.setText("Result copied to clipboard");
    }

    private void showError(String title, Exception exception) {
        JOptionPane.showMessageDialog(this, exception.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    private final class Preview extends JPanel {
        Preview() {
            setBackground(AssistantTheme.CODE_BACKGROUND);
            setBorder(BorderFactory.createLineBorder(AssistantTheme.BORDER));
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (processed==null) {
                graphics.setColor(AssistantTheme.MUTED);
                graphics.drawString("Open or paste an image to begin",24,35);
                return;
            }
            int maxW=getWidth()-30,maxH=getHeight()-30;
            double scale=Math.min((double)maxW/processed.getWidth(),(double)maxH/processed.getHeight());
            int w=(int)(processed.getWidth()*scale),h=(int)(processed.getHeight()*scale);
            Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(processed,(getWidth()-w)/2,(getHeight()-h)/2,w,h,null);
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
