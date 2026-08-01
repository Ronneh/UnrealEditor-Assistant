import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.font.GlyphVector;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Four-shot level screenshot composer with interactive square cropping,
 * rearrangeable 2x2 output, and draggable text labels.
 */
public final class ScreenshotMakerPanel extends JPanel {
    private static final int OUTPUT_SIZE = 1024;
    private static final int CELL_SIZE = OUTPUT_SIZE / 2;
    private static final Preferences PREFS = Preferences.userNodeForPackage(ScreenshotMakerPanel.class);
    private static final String LAST_OPEN_DIRECTORY = "lastOpenDirectory";
    private static final String LAST_EXPORT_DIRECTORY = "lastExportDirectory";
    private static final String[] VALID_RESOLUTIONS = {
            "1280x720", "1280x800", "1600x900", "1920x1080",
            "1920x1200", "2560x1440", "2560x1600", "3840x2160"
    };

    private final java.awt.CardLayout steps = new java.awt.CardLayout();
    private final JPanel stepCards = new JPanel(steps);
    private final Shot[] shots = { new Shot(), new Shot(), new Shot(), new Shot() };
    private final JButton[] shotButtons = new JButton[4];
    private final CropCanvas cropCanvas = new CropCanvas();
    private final CompositionCanvas composition = new CompositionCanvas();
    private final JLabel cropStatus = new JLabel("Load four screenshots to begin.");
    private final JComboBox<String> fontBox =
            new JComboBox<>(usableFontFamilies());
    private final JSpinner fontSize = new JSpinner(new SpinnerNumberModel(120, 12, 400, 2));
    private final JTextField labelText = new JTextField("BT-");
    private final JCheckBox gradientOverlay = new JCheckBox("Gradient Overlay");
    private final JCheckBox outerGlow = new JCheckBox("Outer Glow");
    private final JCheckBox innerGlow = new JCheckBox("Inner Glow");
    private final JCheckBox satin = new JCheckBox("Satin");
    private final JCheckBox stroke = new JCheckBox("Stroke");
    private final JComboBox<String> blendMode =
            new JComboBox<>(new String[] { "Normal", "Multiply", "Screen", "Overlay", "Linear Dodge (Add)" });
    private final JSlider textOpacity = new JSlider(0, 100, 100);
    private final JButton gradientTopColor = colorButton(Color.WHITE);
    private final JButton gradientBottomColor = colorButton(Color.WHITE);
    private final JButton outerGlowColor = colorButton(Color.WHITE);
    private final JButton innerGlowColor = colorButton(Color.WHITE);
    private final JButton satinColor = colorButton(Color.WHITE);
    private final JButton strokeColor = colorButton(Color.WHITE);
    private final JCheckBox centerDividers = new JCheckBox("Center dividers");
    private final JCheckBox dividersOnTop = new JCheckBox("Always on top");
    private final JComboBox<String> dividerStyle =
            new JComboBox<>(new String[] { "Solid", "Dashed", "Dotted", "Wave" });
    private final JSpinner dividerWidth = new JSpinner(new SpinnerNumberModel(2, 1, 40, 1));
    private final JButton dividerColor = colorButton(Color.WHITE);
    private final JRadioButton[] exportSizeButtons = {
            new JRadioButton("2048x2048"), new JRadioButton("1024x1024"),
            new JRadioButton("512x512"),
            new JRadioButton("256x256")
    };
    private final int[] exportSizes = { 2048, 1024, 512, 256 };
    private int activeShot;
    private int nextPasteShot;

    private static String[] usableFontFamilies() {
        String sample="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        List<String> usable=new ArrayList<>();
        for (String family:GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            String lower=family.toLowerCase(java.util.Locale.ROOT);
            boolean iconFont=lower.matches(".*(symbol|wingdings|webdings|marlett|emoji|icons?|assets|mdl2).*");
            Font font=new Font(family,Font.PLAIN,16);
            if (!iconFont && font.canDisplayUpTo(sample)==-1) usable.add(family);
        }
        return usable.toArray(String[]::new);
    }

    public ScreenshotMakerPanel() {
        super(new BorderLayout(0, 14));
        setBackground(AssistantTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 20, 20));
        add(createHeader(), BorderLayout.NORTH);
        stepCards.setOpaque(false);
        stepCards.add(createCropStep(), "crop");
        stepCards.add(createComposeStep(), "compose");
        add(stepCards, BorderLayout.CENTER);
        steps.show(stepCards, "crop");
        installPasteShortcut();
        installFileDrop();
        updateCropSelection();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("Screenshot Maker");
        AssistantTheme.stylePageTitle(title);
        header.add(title, BorderLayout.WEST);
        JLabel info = new JLabel("1. Load & crop   >   2. Arrange & label   >   3. Export PNG");
        info.setForeground(AssistantTheme.MUTED);
        header.add(info, BorderLayout.EAST);
        return header;
    }

    private JPanel createCropStep() {
        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setOpaque(false);
        JPanel strip = AssistantTheme.card(new BorderLayout());
        strip.setPreferredSize(new Dimension(220, 0));
        JPanel shotList = new JPanel(new GridLayout(4, 1, 0, 10));
        shotList.setOpaque(false);
        for (int i=0; i<4; i++) {
            final int index=i;
            JPanel shotEntry = new JPanel(new BorderLayout(5, 0));
            shotEntry.setOpaque(false);
            shotButtons[i]=new JButton("<html><b>Screenshot " + (i+1)
                    + "</b><br><span style='color:#9ca7b8'>Empty</span></html>");
            shotButtons[i].setHorizontalAlignment(SwingConstants.LEFT);
            shotButtons[i].addActionListener(event -> selectShot(index));
            shotButtons[i].setTransferHandler(fileDropHandler(index));
            JButton openFolder = new JButton(new FolderIcon());
            openFolder.setToolTipText("Open an image file for Screenshot " + (i + 1));
            openFolder.setPreferredSize(new Dimension(42, 32));
            openFolder.setFocusable(false);
            openFolder.addActionListener(event -> loadShot(index));
            JPanel folderPosition = new JPanel(new BorderLayout());
            folderPosition.setOpaque(false);
            folderPosition.add(openFolder, BorderLayout.NORTH);
            shotEntry.add(shotButtons[i], BorderLayout.CENTER);
            shotEntry.add(folderPosition, BorderLayout.EAST);
            shotList.add(shotEntry);
        }
        strip.add(shotList, BorderLayout.CENTER);
        root.add(strip, BorderLayout.WEST);

        JPanel canvasCard=AssistantTheme.card(new BorderLayout(0,8));
        canvasCard.add(cropCanvas,BorderLayout.CENTER);
        cropStatus.setForeground(AssistantTheme.MUTED);
        canvasCard.add(cropStatus,BorderLayout.SOUTH);
        root.add(canvasCard,BorderLayout.CENTER);

        JPanel actions=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,8,0));
        actions.setOpaque(false);
        JButton replace=new JButton("Replace current...");
        replace.addActionListener(event -> loadShot(activeShot));
        JButton next=new JButton("Next", new RightArrowIcon());
        next.setHorizontalTextPosition(SwingConstants.LEFT);
        next.setIconTextGap(7);
        next.addActionListener(event -> showComposition());
        actions.add(replace);
        actions.add(next);
        root.add(actions,BorderLayout.SOUTH);
        return root;
    }

    private JPanel createComposeStep() {
        JPanel root=new JPanel(new BorderLayout(14,14));
        root.setOpaque(false);
        JPanel canvasCard=AssistantTheme.card(new BorderLayout());
        canvasCard.add(composition,BorderLayout.CENTER);
        root.add(canvasCard,BorderLayout.CENTER);
        root.add(createLabelControls(),BorderLayout.EAST);

        JPanel actions=new JPanel(new BorderLayout(12,0));
        actions.setOpaque(false);
        JPanel exportOptions=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT,7,0));
        exportOptions.setOpaque(false);
        exportOptions.add(new JLabel("Export size:"));
        ButtonGroup group=new ButtonGroup();
        for (JRadioButton button:exportSizeButtons) {
            button.setOpaque(false);
            group.add(button);
            exportOptions.add(button);
        }
        exportSizeButtons[1].setSelected(true);
        actions.add(exportOptions,BorderLayout.WEST);

        JPanel actionButtons=new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT,8,0));
        actionButtons.setOpaque(false);
        JButton back=new JButton("Adjust crops");
        back.addActionListener(event -> steps.show(stepCards,"crop"));
        JButton export=new JButton("Export PNG...");
        export.addActionListener(event -> exportComposition());
        actionButtons.add(back);
        actionButtons.add(export);
        actions.add(actionButtons,BorderLayout.EAST);
        root.add(actions,BorderLayout.SOUTH);
        return root;
    }

    private JPanel createLabelControls() {
        JPanel controls=AssistantTheme.card(new BorderLayout(0,6));
        controls.setPreferredSize(new Dimension(360,0));
        JLabel title=new JLabel("Labels");
        title.setFont(title.getFont().deriveFont(Font.BOLD,17f));
        controls.add(title,BorderLayout.NORTH);
        JPanel rows=new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows,BoxLayout.Y_AXIS));

        labelText.setBackground(AssistantTheme.PANEL_ALT);
        labelText.setForeground(AssistantTheme.TEXT);
        labelText.setCaretColor(AssistantTheme.TEXT);
        labelText.setBorder(fontBox.getBorder());
        fontBox.setRenderer(new FontPreviewRenderer());
        fontBox.setSelectedItem("Verdana");

        if (fontSize.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setBackground(AssistantTheme.PANEL_ALT);
            editor.getTextField().setForeground(AssistantTheme.TEXT);
            editor.getTextField().setCaretColor(AssistantTheme.TEXT);
        }
        fontSize.setBorder(fontBox.getBorder());
        fontSize.setPreferredSize(new Dimension(76, 28));
        styleSpinnerButtons(fontSize);

        JPanel form = new JPanel(new java.awt.GridBagLayout());
        form.setOpaque(false);
        addFormRow(form, 0, "Text:", labelText);
        addFormRow(form, 1, "Font:", fontBox);
        addFormRow(form, 2, "Size:", fontSize);
        form.setMaximumSize(new Dimension(Integer.MAX_VALUE, 112));
        rows.add(form);
        rows.add(Box.createVerticalStrut(12));

        JButton add=new JButton("Add text");
        add.addActionListener(event -> addLabel());
        JButton remove=new JButton("Remove selected text");
        remove.addActionListener(event -> composition.removeSelectedLabel());
        JPanel textButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        textButtons.setOpaque(false);
        textButtons.add(add);
        textButtons.add(Box.createHorizontalStrut(8));
        textButtons.add(remove);
        rows.add(textButtons);
        rows.add(Box.createVerticalStrut(8));
        rows.add(createTextEffectsPanel());
        rows.add(Box.createVerticalGlue());
        controls.add(rows,BorderLayout.CENTER);
        return controls;
    }

    private void addFormRow(JPanel form, int row, String label, JComponent field) {
        java.awt.GridBagConstraints constraints = new java.awt.GridBagConstraints();
        constraints.gridy = row;
        constraints.insets = new java.awt.Insets(0, 0, 8, 8);
        constraints.anchor = java.awt.GridBagConstraints.WEST;
        form.add(new JLabel(label), constraints);
        constraints.gridx = 1;
        boolean compact = field == fontSize;
        constraints.weightx = compact ? 0 : 1;
        constraints.fill = compact ? java.awt.GridBagConstraints.NONE
                : java.awt.GridBagConstraints.HORIZONTAL;
        constraints.insets = new java.awt.Insets(0, 0, 8, 0);
        if (!compact) field.setPreferredSize(new Dimension(245, 28));
        form.add(field, constraints);
    }

    private JPanel createTextEffectsPanel() {
        JPanel effects=new JPanel();
        effects.setOpaque(false);
        effects.setLayout(new BoxLayout(effects,BoxLayout.Y_AXIS));
        JLabel heading=new JLabel("Effects");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD,17f));
        JPanel headingRow = new JPanel(new BorderLayout());
        headingRow.setOpaque(false);
        headingRow.add(heading, BorderLayout.WEST);
        headingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        headingRow.setAlignmentX(LEFT_ALIGNMENT);
        effects.add(headingRow);
        effects.add(Box.createVerticalStrut(7));

        JPanel checks=new JPanel(new GridLayout(3,2,6,3));
        checks.setOpaque(false);
        checks.add(gradientOverlay);
        checks.add(outerGlow);
        checks.add(innerGlow);
        checks.add(satin);
        checks.add(stroke);
        checks.add(new JLabel(""));
        checks.setMaximumSize(new Dimension(Integer.MAX_VALUE,78));
        effects.add(checks);
        effects.add(Box.createVerticalStrut(8));

        JPanel colors=new JPanel(new GridLayout(3,2,7,5));
        colors.setOpaque(false);
        colors.add(colorControl("Gradient Top",gradientTopColor));
        colors.add(colorControl("Inner Glow",innerGlowColor));
        colors.add(colorControl("Gradient Bottom",gradientBottomColor));
        colors.add(colorControl("Outer Glow",outerGlowColor));
        colors.add(colorControl("Satin",satinColor));
        colors.add(colorControl("Stroke",strokeColor));
        colors.setMaximumSize(new Dimension(Integer.MAX_VALUE,92));
        effects.add(colors);
        effects.add(Box.createVerticalStrut(8));

        JPanel blendRow=new JPanel(new BorderLayout(8,0));
        blendRow.setOpaque(false);
        blendRow.add(new JLabel("Blend Mode:"),BorderLayout.WEST);
        blendRow.add(blendMode,BorderLayout.CENTER);
        blendRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,29));
        effects.add(blendRow);
        effects.add(Box.createVerticalStrut(8));

        JPanel opacityRow=new JPanel(new BorderLayout(8,0));
        opacityRow.setOpaque(false);
        opacityRow.add(new JLabel("Opacity:"),BorderLayout.WEST);
        textOpacity.setOpaque(false);
        JLabel opacityValue=new JLabel("100%");
        opacityValue.setMinimumSize(new Dimension(52,24));
        opacityValue.setPreferredSize(new Dimension(52,24));
        opacityRow.add(textOpacity,BorderLayout.CENTER);
        opacityRow.add(opacityValue,BorderLayout.EAST);
        textOpacity.addChangeListener(event -> opacityValue.setText(textOpacity.getValue()+"%"));
        opacityRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,32));
        effects.add(opacityRow);
        effects.add(Box.createVerticalStrut(8));
        effects.add(createDividerEffectsPanel());

        java.awt.event.ActionListener styleChange=event -> applyEffectsToSelectedText();
        gradientOverlay.addActionListener(styleChange);
        outerGlow.addActionListener(styleChange);
        innerGlow.addActionListener(styleChange);
        satin.addActionListener(styleChange);
        stroke.addActionListener(styleChange);
        blendMode.addActionListener(styleChange);
        textOpacity.addChangeListener(event -> applyEffectsToSelectedText());
        return effects;
    }

    private JPanel createDividerEffectsPanel() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        panel.setOpaque(false);
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.anchor = java.awt.GridBagConstraints.WEST;
        c.fill = java.awt.GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridwidth = 1;
        panel.add(centerDividers, c);
        c.gridx = 1;
        panel.add(dividersOnTop, c);
        c.gridy = 1;
        c.gridx = 0;
        c.gridwidth = 1;
        c.insets = new java.awt.Insets(4, 0, 0, 6);
        panel.add(new JLabel("Style:"), c);
        c.gridx = 1;
        panel.add(dividerStyle, c);
        c.gridx = 0;
        c.gridy = 2;
        panel.add(new JLabel("Width:"), c);
        c.gridx = 1;
        JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        settings.setOpaque(false);
        settings.add(dividerWidth);
        settings.add(dividerColor);
        panel.add(settings, c);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        centerDividers.addActionListener(event -> composition.invalidatePreview());
        dividersOnTop.addActionListener(event -> composition.invalidatePreview());
        dividerStyle.addActionListener(event -> composition.invalidatePreview());
        dividerWidth.addChangeListener(event -> composition.invalidatePreview());
        dividerWidth.setBorder(fontSize.getBorder());
        dividerWidth.setPreferredSize(new Dimension(76, 28));
        if (dividerWidth.getEditor() instanceof JSpinner.DefaultEditor editor) {
            editor.getTextField().setBackground(AssistantTheme.PANEL_ALT);
            editor.getTextField().setForeground(AssistantTheme.TEXT);
            editor.getTextField().setCaretColor(AssistantTheme.TEXT);
        }
        styleSpinnerButtons(dividerWidth);
        dividerColor.setToolTipText("Choose divider color");
        dividerColor.addActionListener(event -> {
            chooseColor("Divider", dividerColor);
            composition.invalidatePreview();
        });
        return panel;
    }

    private JPanel colorControl(String name,JButton swatch) {
        JPanel panel=new JPanel(new BorderLayout(5,0));
        panel.setOpaque(false);
        JLabel label=new JLabel(name+":");
        label.setFont(label.getFont().deriveFont(11f));
        panel.add(label,BorderLayout.CENTER);
        swatch.setToolTipText("Choose "+name+" color");
        swatch.addActionListener(event -> chooseColor(name,swatch));
        panel.add(swatch,BorderLayout.EAST);
        return panel;
    }

    private static JButton colorButton(Color color) {
        JButton button=new JButton();
        button.setBackground(color);
        button.setOpaque(true);
        button.setPreferredSize(new Dimension(34,22));
        button.setBorder(BorderFactory.createLineBorder(AssistantTheme.BORDER));
        button.setFocusPainted(false);
        return button;
    }

    private void chooseColor(String name,JButton swatch) {
        Color selected=RgbColorPicker.show(this,name+" Color",swatch.getBackground());
        if (selected==null) return;
        swatch.setBackground(selected);
        applyEffectsToSelectedText();
    }

    private void applyEffectsToSelectedText() {
        int selected=composition.selectedLabel;
        if (selected<0 || selected>=composition.labels.size()) return;
        TextLabel label=composition.labels.get(selected);
        label.gradient=gradientOverlay.isSelected();
        label.outerGlow=outerGlow.isSelected();
        label.innerGlow=innerGlow.isSelected();
        label.satin=satin.isSelected();
        label.stroke=stroke.isSelected();
        label.blendMode=blendMode.getSelectedIndex();
        label.opacity=textOpacity.getValue();
        label.gradientTop=gradientTopColor.getBackground();
        label.gradientBottom=gradientBottomColor.getBackground();
        label.outerGlowColor=outerGlowColor.getBackground();
        label.innerGlowColor=innerGlowColor.getBackground();
        label.satinColor=satinColor.getBackground();
        label.strokeColor=strokeColor.getBackground();
        composition.invalidatePreview();
    }

    private void styleSpinnerButtons(java.awt.Container container) {
        for (java.awt.Component child:container.getComponents()) {
            if (child instanceof JButton button) {
                button.setBackground(AssistantTheme.PANEL_ALT);
                button.setForeground(AssistantTheme.TEXT);
                button.setBorder(BorderFactory.createLineBorder(AssistantTheme.BORDER));
                button.setFocusPainted(false);
                button.setPreferredSize(new Dimension(18,13));
            }
            if (child instanceof java.awt.Container nested) styleSpinnerButtons(nested);
        }
    }

    private void selectShot(int index) {
        activeShot=index;
        cropStatus.setForeground(AssistantTheme.MUTED);
        cropStatus.setText(shots[index].image == null
                ? "Screenshot " + (index + 1) + " selected. Press Ctrl+V or use the folder button."
                : "Screenshot " + (index + 1) + " selected. Drag the square to adjust its crop.");
        updateCropSelection();
    }

    private void loadShot(int index) {
        JFileChooser chooser=new DarkFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Screenshots (PNG, JPG, BMP)","png","jpg","jpeg","bmp"));
        chooser.setCurrentDirectory(FileSaveSupport.preferredDirectory(
                PREFS.get(LAST_OPEN_DIRECTORY, null),
                new File(System.getProperty("user.home"))));
        if (chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        File directory = file.getAbsoluteFile().getParentFile();
        if (directory != null) PREFS.put(LAST_OPEN_DIRECTORY, directory.getAbsolutePath());
        loadShotFile(index, file, "file");
    }

    private void loadShotFile(int index, File file, String source) {
        cropStatus.setForeground(AssistantTheme.MUTED);
        cropStatus.setText("Loading " + file.getName() + "...");
        AsyncImageIO.load(file, image -> {
            setShotImage(index, image, source);
            String resolution=image.getWidth()+"x"+image.getHeight();
            if (!isValidResolution(resolution)) {
                cropStatus.setText("Loaded "+resolution+" — this is outside the recommended resolution list, but can still be used.");
            }
        }, exception -> DarkDialogs.message(this, exception.getMessage(),
                "Could not load screenshot", JOptionPane.ERROR_MESSAGE));
    }

    private void installFileDrop() {
        TransferHandler handler = fileDropHandler(null);
        setTransferHandler(handler);
        stepCards.setTransferHandler(handler);
        cropCanvas.setTransferHandler(handler);
    }

    private TransferHandler fileDropHandler(Integer fixedIndex) {
        return new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    Object value = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!(value instanceof List<?> files)) return false;
                    int index = fixedIndex == null ? activeShot : fixedIndex;
                    boolean loaded = false;
                    for (Object item : files) {
                        if (index >= shots.length || !(item instanceof File file)) break;
                        loadShotFile(index++, file, "drag and drop");
                        loaded = true;
                    }
                    return loaded;
                } catch (Exception exception) {
                    cropStatus.setForeground(new Color(225, 105, 105));
                    cropStatus.setText("The dropped file is not a supported image.");
                    return false;
                }
            }
        };
    }

    private void installPasteShortcut() {
        KeyStroke paste = KeyStroke.getKeyStroke("control V");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(paste, "pasteScreenshot");
        getActionMap().put("pasteScreenshot", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                pasteShot(nextPasteShot, true);
            }
        });
    }

    private void pasteShot(int index) {
        pasteShot(index, false);
    }

    private void pasteShot(int index, boolean advancePosition) {
        ClipboardImageSupport.paste(
                image -> {
                    setShotImage(index, image, "clipboard");
                    if (advancePosition) nextPasteShot = Math.min(shots.length - 1, index + 1);
                },
                exception -> {
                    cropStatus.setForeground(new Color(225, 105, 105));
                    cropStatus.setText("The clipboard content is not a supported image.");
                });
    }

    private void setShotImage(int index, BufferedImage image, String source) {
        Shot shot = shots[index];
        shot.image = image;
        shot.crop = initialCropFor(image);
        activeShot = index;
        String resolution = image.getWidth() + "x" + image.getHeight();
        shotButtons[index].setText("<html><b>Screenshot " + (index + 1)
                + "</b><br>" + resolution + "</html>");
        cropStatus.setForeground(AssistantTheme.MUTED);
        cropStatus.setText("Screenshot " + (index + 1) + " added from " + source
                + ". Drag the square to choose the crop.");
        updateCropSelection();
    }

    static Rectangle initialCropFor(BufferedImage image) {
        int side = Math.min(image.getWidth(), image.getHeight());
        return new Rectangle((image.getWidth() - side) / 2,
                (image.getHeight() - side) / 2, side, side);
    }

    private boolean isValidResolution(String resolution) {
        for (String valid:VALID_RESOLUTIONS) if (valid.equals(resolution)) return true;
        return false;
    }

    private void updateCropSelection() {
        for (int i=0;i<shotButtons.length;i++) {
            shotButtons[i].setBackground(i==activeShot?AssistantTheme.ACCENT_DARK:AssistantTheme.PANEL_ALT);
        }
        cropCanvas.repaint();
    }

    private void showComposition() {
        for (Shot shot:shots) {
            if (shot.image==null) {
                DarkDialogs.message(this,"Please load all four screenshots first.","Screenshots missing",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }
        composition.refreshCrops();
        updateExportSizes();
        steps.show(stepCards,"compose");
    }

    private void updateExportSizes() {
        boolean selected=false;
        for (int index=0;index<exportSizes.length;index++) {
            int candidate=exportSizes[index];
            boolean available=true;
            for (Shot shot:shots) {
                available &= shot.image!=null
                        && Math.min(shot.image.getWidth(),shot.image.getHeight())>=candidate;
            }
            exportSizeButtons[index].setEnabled(available);
            exportSizeButtons[index].setToolTipText(available ? null
                    : "Every source image must be at least "+candidate+" pixels on its shortest side.");
            if (available && !selected) {
                exportSizeButtons[index].setSelected(true);
                selected=true;
            }
        }
    }

    private void addLabel() {
        String text=labelText.getText().trim();
        if (text.isEmpty()) return;
        composition.labels.add(new TextLabel(text,(String)fontBox.getSelectedItem(),(Integer)fontSize.getValue(),
                OUTPUT_SIZE/2,OUTPUT_SIZE/2,gradientOverlay.isSelected(),outerGlow.isSelected(),
                innerGlow.isSelected(),satin.isSelected(),stroke.isSelected(),
                blendMode.getSelectedIndex(),textOpacity.getValue(),
                gradientTopColor.getBackground(),gradientBottomColor.getBackground(),
                outerGlowColor.getBackground(),innerGlowColor.getBackground(),
                satinColor.getBackground(),strokeColor.getBackground()));
        composition.selectedLabel=composition.labels.size()-1;
        composition.invalidatePreview();
    }

    private void exportComposition() {
        int exportSize=1024;
        for (int index=0;index<exportSizeButtons.length;index++) {
            if (exportSizeButtons[index].isSelected() && exportSizeButtons[index].isEnabled()) {
                exportSize=exportSizes[index];
                break;
            }
        }
        JFileChooser chooser=new DarkFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PNG image","png"));
        chooser.setCurrentDirectory(FileSaveSupport.preferredDirectory(
                PREFS.get(LAST_EXPORT_DIRECTORY, null),
                new File(System.getProperty("user.home"))));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), "level-screenshot.png"));
        if (chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION) return;
        File file=chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".png")) file=new File(file.getParentFile(),file.getName()+".png");
        if (!FileSaveSupport.confirmOverwrite(this, file)) return;
        File exportDirectory = file.getAbsoluteFile().getParentFile();
        if (exportDirectory != null) PREFS.put(LAST_EXPORT_DIRECTORY, exportDirectory.getAbsolutePath());
        File targetFile = file;
        BufferedImage export = composition.renderOutput(exportSize, false);
        AsyncImageIO.savePng(export, targetFile, () -> { },
                exception -> DarkDialogs.message(this, exception.getMessage(),
                        "Could not export PNG", JOptionPane.ERROR_MESSAGE));
    }

    private final class CropCanvas extends JPanel {
        private Rectangle displayedImage=new Rectangle();
        private Rectangle displayedCrop=new Rectangle();
        private Point dragOffset;
        CropCanvas() {
            setBackground(AssistantTheme.CODE_BACKGROUND);
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter mouse=new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (shots[activeShot].image!=null && displayedCrop.contains(e.getPoint()))
                        dragOffset=new Point(e.getX()-displayedCrop.x,e.getY()-displayedCrop.y);
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (dragOffset==null) return;
                    Shot shot=shots[activeShot];
                    double sx=(double)shot.image.getWidth()/displayedImage.width;
                    double sy=(double)shot.image.getHeight()/displayedImage.height;
                    int displayX=Math.max(displayedImage.x,Math.min(e.getX()-dragOffset.x,
                            displayedImage.x+displayedImage.width-displayedCrop.width));
                    int displayY=Math.max(displayedImage.y,Math.min(e.getY()-dragOffset.y,
                            displayedImage.y+displayedImage.height-displayedCrop.height));
                    shot.crop.x=(int)Math.round((displayX-displayedImage.x)*sx);
                    shot.crop.y=(int)Math.round((displayY-displayedImage.y)*sy);
                    shot.crop.x=Math.min(shot.image.getWidth()-shot.crop.width,Math.max(0,shot.crop.x));
                    shot.crop.y=Math.min(shot.image.getHeight()-shot.crop.height,Math.max(0,shot.crop.y));
                    repaint();
                }
                @Override public void mouseReleased(MouseEvent e) { dragOffset=null; }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Shot shot=shots[activeShot];
            if (shot.image==null) {
                graphics.setColor(AssistantTheme.MUTED);
                graphics.drawString("Choose Screenshot 1–4 to load an image.",24,35);
                return;
            }
            int margin=18,maxW=getWidth()-margin*2,maxH=getHeight()-margin*2;
            double scale=Math.min((double)maxW/shot.image.getWidth(),(double)maxH/shot.image.getHeight());
            int w=(int)(shot.image.getWidth()*scale),h=(int)(shot.image.getHeight()*scale);
            displayedImage.setBounds((getWidth()-w)/2,(getHeight()-h)/2,w,h);
            displayedCrop.setBounds(displayedImage.x+(int)Math.round(shot.crop.x*scale),
                    displayedImage.y+(int)Math.round(shot.crop.y*scale),
                    (int)Math.round(shot.crop.width*scale),(int)Math.round(shot.crop.height*scale));
            Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(shot.image,displayedImage.x,displayedImage.y,w,h,null);
            g.setComposite(AlphaComposite.SrcOver.derive(.62f));
            g.setColor(Color.BLACK);
            g.fillRect(displayedImage.x,displayedImage.y,displayedImage.width,displayedCrop.y-displayedImage.y);
            g.fillRect(displayedImage.x,displayedCrop.y,displayedCrop.x-displayedImage.x,displayedCrop.height);
            g.fillRect(displayedCrop.x+displayedCrop.width,displayedCrop.y,
                    displayedImage.x+displayedImage.width-(displayedCrop.x+displayedCrop.width),displayedCrop.height);
            g.fillRect(displayedImage.x,displayedCrop.y+displayedCrop.height,displayedImage.width,
                    displayedImage.y+displayedImage.height-(displayedCrop.y+displayedCrop.height));
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2f));
            g.draw(displayedCrop);
            g.dispose();
        }
    }

    private final class CompositionCanvas extends JPanel {
        private final List<BufferedImage> cells=new ArrayList<>();
        private final List<TextLabel> labels=new ArrayList<>();
        private int selectedLabel=-1,dragCell=-1,selectedCell=-1;
        private Point dragOffset;
        private Point pressPoint;
        private boolean dragged;
        private Rectangle outputBounds=new Rectangle();
        private BufferedImage previewCache;
        private int previewCacheSize = -1;
        CompositionCanvas() {
            setBackground(AssistantTheme.CODE_BACKGROUND);
            setFocusable(true);
            getInputMap(JComponent.WHEN_FOCUSED)
                    .put(KeyStroke.getKeyStroke("DELETE"), "removeSelectedLabel");
            getActionMap().put("removeSelectedLabel", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                    removeSelectedLabel();
                }
            });
            MouseAdapter mouse=new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    if (!outputBounds.contains(e.getPoint())) return;
                    Point output=toOutput(e.getPoint());
                    selectedLabel=findLabel(output);
                    pressPoint=e.getPoint();
                    dragged=false;
                    if (selectedLabel>=0) {
                        TextLabel label=labels.get(selectedLabel);
                        dragOffset=new Point(output.x-label.x,output.y-label.y);
                    } else {
                        dragCell=cellAt(output);
                    }
                    invalidatePreview();
                }
                @Override public void mouseDragged(MouseEvent e) {
                    if (pressPoint != null && pressPoint.distance(e.getPoint()) > 4) dragged=true;
                    if (selectedLabel<0 || dragOffset==null) {
                        repaint();
                        return;
                    }
                    Point output=toOutput(e.getPoint());
                    TextLabel label=labels.get(selectedLabel);
                    label.x=Math.max(0,Math.min(OUTPUT_SIZE,output.x-dragOffset.x));
                    label.y=Math.max(0,Math.min(OUTPUT_SIZE,output.y-dragOffset.y));
                    invalidatePreview();
                }
                @Override public void mouseReleased(MouseEvent e) {
                    if (dragCell>=0) {
                        int target=cellAt(toOutput(e.getPoint()));
                        if (dragged && target>=0 && target!=dragCell) {
                            Collections.swap(cells,dragCell,target);
                            selectedCell=-1;
                        } else if (!dragged && target>=0) {
                            if (selectedCell>=0 && selectedCell!=target) {
                                Collections.swap(cells,selectedCell,target);
                                selectedCell=-1;
                            } else {
                                selectedCell=target;
                            }
                        }
                    }
                    dragCell=-1;
                    dragOffset=null;
                    pressPoint=null;
                    invalidatePreview();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }
        void refreshCrops() {
            cells.clear();
            for (Shot shot:shots) {
                BufferedImage crop=shot.image.getSubimage(shot.crop.x,shot.crop.y,shot.crop.width,shot.crop.height);
                cells.add(crop);
            }
            invalidatePreview();
        }
        void removeSelectedLabel() {
            if (selectedLabel>=0 && selectedLabel<labels.size()) labels.remove(selectedLabel);
            selectedLabel=-1;
            invalidatePreview();
        }
        void invalidatePreview() {
            previewCache = null;
            previewCacheSize = -1;
            repaint();
        }
        private BufferedImage previewOutput(int targetSize) {
            if (previewCache == null || previewCacheSize != targetSize) {
                previewCache = renderOutput(targetSize, true);
                previewCacheSize = targetSize;
            }
            return previewCache;
        }
        private int cellAt(Point p) {
            if (p.x<0||p.y<0||p.x>=OUTPUT_SIZE||p.y>=OUTPUT_SIZE) return -1;
            return (p.y/CELL_SIZE)*2+p.x/CELL_SIZE;
        }
        private int findLabel(Point p) {
            BufferedImage measure=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=measure.createGraphics();
            for (int i=labels.size()-1;i>=0;i--) {
                TextLabel label=labels.get(i);
                g.setFont(new Font(label.font,Font.PLAIN,label.size));
                FontMetrics fm=g.getFontMetrics();
                Rectangle bounds=new Rectangle(label.x,label.y-fm.getAscent(),fm.stringWidth(label.text),fm.getHeight());
                if (bounds.contains(p)) { g.dispose(); return i; }
            }
            g.dispose();
            return -1;
        }
        private Point toOutput(Point point) {
            if (outputBounds.width<=0) return new Point(-1,-1);
            int x=(int)((point.x-outputBounds.x)*(double)OUTPUT_SIZE/outputBounds.width);
            int y=(int)((point.y-outputBounds.y)*(double)OUTPUT_SIZE/outputBounds.height);
            return new Point(Math.max(0,Math.min(OUTPUT_SIZE-1,x)),
                    Math.max(0,Math.min(OUTPUT_SIZE-1,y)));
        }
        BufferedImage renderOutput(int targetSize,boolean selection) {
            BufferedImage image=new BufferedImage(targetSize,targetSize,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(Color.BLACK);
            g.fillRect(0,0,targetSize,targetSize);
            int targetCell=targetSize/2;
            for (int i=0;i<cells.size();i++) {
                g.drawImage(cells.get(i),(i%2)*targetCell,(i/2)*targetCell,targetCell,targetCell,null);
            }
            g.dispose();

            if (centerDividers.isSelected() && !dividersOnTop.isSelected()) drawDividers(image);

            for (int i=0;i<labels.size();i++) {
                TextLabel label=labels.get(i);
                BufferedImage labelLayer=renderLabel(label,selection && i==selectedLabel,targetSize);
                if (label.blendMode != 0) blendComposite(image,labelLayer,label.blendMode);
                else {
                    Graphics2D layerGraphics=image.createGraphics();
                    layerGraphics.drawImage(labelLayer,0,0,null);
                    layerGraphics.dispose();
                }
            }

            if (centerDividers.isSelected() && dividersOnTop.isSelected()) drawDividers(image);

            if (selection) {
                g=image.createGraphics();
                if (!centerDividers.isSelected()) {
                    g.setColor(new Color(255,255,255,120));
                    g.drawLine(targetCell,0,targetCell,targetSize);
                    g.drawLine(0,targetCell,targetSize,targetCell);
                }
                if (selectedCell>=0) {
                    int x=(selectedCell%2)*targetCell;
                    int y=(selectedCell/2)*targetCell;
                    g.setColor(AssistantTheme.ACCENT);
                    g.setStroke(new BasicStroke(6f));
                    g.drawRect(x+3,y+3,targetCell-6,targetCell-6);
                }
                g.dispose();
            }
            return image;
        }

        private void drawDividers(BufferedImage image) {
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(dividerColor.getBackground());
            double scale = image.getWidth() / (double) OUTPUT_SIZE;
            int center = image.getWidth() / 2;
            float width = Math.max(1f, ((Number) dividerWidth.getValue()).floatValue() * (float) scale);
            String style = (String) dividerStyle.getSelectedItem();
            if ("Wave".equals(style)) {
                g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                java.awt.geom.Path2D vertical = new java.awt.geom.Path2D.Double();
                java.awt.geom.Path2D horizontal = new java.awt.geom.Path2D.Double();
                vertical.moveTo(center, 0);
                horizontal.moveTo(0, center);
                for (int position = 0; position <= image.getWidth(); position += Math.max(2, (int) (4 * scale))) {
                    double offset = Math.sin(position / Math.max(1, 18.0 * scale)) * width * 2.5;
                    vertical.lineTo(center + offset, position);
                    horizontal.lineTo(position, center + offset);
                }
                g.draw(vertical);
                g.draw(horizontal);
            } else {
                float[] dash = "Dashed".equals(style) ? new float[] { width * 4, width * 3 }
                        : "Dotted".equals(style) ? new float[] { width, width * 2 } : null;
                g.setStroke(dash == null
                        ? new BasicStroke(width)
                        : new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, dash, 0));
                g.drawLine(center, 0, center, image.getHeight());
                g.drawLine(0, center, image.getWidth(), center);
            }
            g.dispose();
        }

        private BufferedImage renderLabel(TextLabel label,boolean selected,int targetSize) {
            double scale = targetSize / (double) OUTPUT_SIZE;
            BufferedImage layer=new BufferedImage(targetSize,targetSize,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g=layer.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            Font font=new Font(label.font,Font.PLAIN,Math.max(1,(int)Math.round(label.size*scale)));
            g.setFont(font);
            GlyphVector glyphs=font.createGlyphVector(g.getFontRenderContext(),label.text);
            int x=(int)Math.round(label.x*scale), y=(int)Math.round(label.y*scale);
            Shape shape=glyphVectorOutline(glyphs,x,y);
            g.setComposite(AlphaComposite.SrcOver.derive(label.opacity/100f));

            if (label.outerGlow) {
                g.setColor(withAlpha(label.outerGlowColor,115));
                g.setStroke(new BasicStroke(Math.max(1f,13f*(float)scale),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(shape);
            }
            if (label.satin) {
                g.translate(4*scale,5*scale);
                g.setColor(withAlpha(label.satinColor,150));
                g.fill(shape);
                g.translate(-4*scale,-5*scale);
            }
            if (label.stroke) {
                g.setColor(withAlpha(label.strokeColor,220));
                g.setStroke(new BasicStroke(Math.max(1f,5f*(float)scale),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                g.draw(shape);
            }
            FontMetrics fm=g.getFontMetrics();
            if (label.gradient) {
                g.setPaint(new GradientPaint(0,y-fm.getAscent(),label.gradientTop,
                        0,y,label.gradientBottom));
            } else {
                g.setColor(Color.WHITE);
            }
            g.fill(shape);
            if (label.innerGlow) {
                g.setColor(withAlpha(label.innerGlowColor,150));
                g.setStroke(new BasicStroke(Math.max(1f,2.5f*(float)scale)));
                g.draw(shape);
            }
            if (selected) {
                Rectangle bounds=shape.getBounds();
                g.setComposite(AlphaComposite.SrcOver);
                g.setColor(AssistantTheme.ACCENT);
                g.setStroke(new BasicStroke(1f));
                g.drawRect(bounds.x-3,bounds.y-3,bounds.width+6,bounds.height+6);
            }
            g.dispose();
            return layer;
        }

        private Color withAlpha(Color color,int alpha) {
            return new Color(color.getRed(),color.getGreen(),color.getBlue(),alpha);
        }

        private Shape glyphVectorOutline(GlyphVector vector,int x,int y) {
            return vector.getOutline(x,y);
        }

        private void blendComposite(BufferedImage base,BufferedImage overlay,int mode) {
            for (int y=0;y<base.getHeight();y++) {
                for (int x=0;x<base.getWidth();x++) {
                    int top=overlay.getRGB(x,y);
                    int alpha=(top>>>24)&255;
                    if (alpha==0) continue;
                    int bottom=base.getRGB(x,y);
                    int r=blendChannel((bottom>>>16)&255,(top>>>16)&255,alpha,mode);
                    int green=blendChannel((bottom>>>8)&255,(top>>>8)&255,alpha,mode);
                    int blue=blendChannel(bottom&255,top&255,alpha,mode);
                    base.setRGB(x,y,0xff000000|(r<<16)|(green<<8)|blue);
                }
            }
        }

        private int blendChannel(int bottom,int top,int alpha,int mode) {
            int blended = switch (mode) {
                case 1 -> bottom * top / 255;
                case 2 -> 255 - (255 - bottom) * (255 - top) / 255;
                case 3 -> bottom < 128 ? 2 * bottom * top / 255
                        : 255 - 2 * (255 - bottom) * (255 - top) / 255;
                case 4 -> Math.min(255, bottom + top);
                default -> top;
            };
            return (bottom * (255 - alpha) + blended * alpha) / 255;
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            int side=Math.min(getWidth()-24,getHeight()-24);
            if (side <= 0) return;
            outputBounds.setBounds((getWidth()-side)/2,(getHeight()-side)/2,side,side);
            BufferedImage output=previewOutput(side);
            Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(output,outputBounds.x,outputBounds.y,null);
            g.setColor(AssistantTheme.BORDER);
            g.draw(outputBounds);
            g.dispose();
        }
    }

    private static final class Shot {
        BufferedImage image;
        Rectangle crop;
    }
    private static final class TextLabel {
        final String text,font;
        final int size;
        boolean gradient,outerGlow,innerGlow,satin,stroke;
        int blendMode,opacity;
        Color gradientTop,gradientBottom,outerGlowColor,innerGlowColor,satinColor,strokeColor;
        int x,y;
        TextLabel(String text,String font,int size,int x,int y,boolean gradient,
                  boolean outerGlow,boolean innerGlow,boolean satin,boolean stroke,
                  int blendMode,int opacity,Color gradientTop,Color gradientBottom,
                  Color outerGlowColor,Color innerGlowColor,Color satinColor,Color strokeColor) {
            this.text=text; this.font=font; this.size=size; this.x=x; this.y=y;
            this.gradient=gradient; this.outerGlow=outerGlow; this.innerGlow=innerGlow;
            this.satin=satin; this.stroke=stroke; this.blendMode=blendMode; this.opacity=opacity;
            this.gradientTop=gradientTop; this.gradientBottom=gradientBottom;
            this.outerGlowColor=outerGlowColor; this.innerGlowColor=innerGlowColor;
            this.satinColor=satinColor; this.strokeColor=strokeColor;
        }
    }

    /** Displays every installed font name using that font, similar to Word. */
    private static final class FontPreviewRenderer extends DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean selected, boolean hasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, hasFocus);
            String family = value == null ? "Verdana" : value.toString();
            label.setText(family);
            label.setFont(new Font(family, Font.PLAIN, 16));
            label.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
            if (!selected) {
                label.setBackground(AssistantTheme.PANEL_ALT);
                label.setForeground(AssistantTheme.TEXT);
            }
            return label;
        }
    }

    private static final class FolderIcon implements Icon {
        @Override public int getIconWidth() { return 18; }
        @Override public int getIconHeight() { return 14; }

        @Override public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(224, 169, 66));
            g.fillRoundRect(x, y + 3, 18, 11, 2, 2);
            g.fillRoundRect(x + 1, y, 8, 6, 2, 2);
            g.setColor(new Color(255, 205, 105));
            g.fillRoundRect(x + 1, y + 5, 16, 8, 2, 2);
            g.setColor(new Color(137, 97, 35));
            g.drawRoundRect(x, y + 3, 17, 10, 2, 2);
            g.dispose();
        }
    }

    private static final class RightArrowIcon implements Icon {
        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 12; }

        @Override public void paintIcon(java.awt.Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(component.isEnabled() ? AssistantTheme.TEXT : AssistantTheme.MUTED);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int centerY = y + getIconHeight() / 2;
            g.drawLine(x + 1, centerY, x + 13, centerY);
            g.drawLine(x + 9, y + 2, x + 14, centerY);
            g.drawLine(x + 14, centerY, x + 9, y + 10);
            g.dispose();
        }
    }
}
