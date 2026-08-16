import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import org.w3c.dom.Node;

/** Animated level-screenshot composer with an unlimited, reorderable timeline. */
public final class GifMakerPanel extends JPanel {
    private static final Preferences PREFS = Preferences.userNodeForPackage(GifMakerPanel.class);
    private static final String OPEN_DIR = "gifOpenDirectory", EXPORT_DIR = "gifExportDirectory";
    private final List<Frame> frames = new ArrayList<>();
    private final FrameModel frameModel = new FrameModel();
    private final JTable timeline = new JTable(frameModel);
    private final Preview preview = new Preview();
    private final JSpinner delay = new JSpinner(new SpinnerNumberModel(250, 20, 10000, 10));
    private final JComboBox<Integer> outputSize = new JComboBox<>(new Integer[]{256,512,1024});
    private final JSpinner loops = new JSpinner(new SpinnerNumberModel(0, 0, 999, 1));
    private final JSlider zoom = new JSlider(25, 300, 100);
    private final JCheckBox pingPong = new JCheckBox("Ping-pong playback");
    private final JComboBox<String> fit = new JComboBox<>(new String[]{"Crop to fill", "Fit with bars", "Stretch"});
    private final JTextField labelText = new JTextField("BT-");
    private final JComboBox<String> font = new JComboBox<>(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
    private final JSpinner fontSize = new JSpinner(new SpinnerNumberModel(64, 8, 300, 2));
    private final JSlider opacity = new JSlider(0, 100, 100);
    private final JCheckBox gradient = new JCheckBox("Gradient Overlay");
    private final JCheckBox outerGlow = new JCheckBox("Outer Glow");
    private final JCheckBox innerGlow = new JCheckBox("Inner Glow");
    private final JCheckBox satin = new JCheckBox("Satin");
    private final JCheckBox stroke = new JCheckBox("Stroke");
    private final JComboBox<String> blend = new JComboBox<>(new String[]{"Normal", "Multiply", "Screen", "Overlay", "Linear Dodge (Add)"});
    private final JSlider glowSize = new JSlider(0, 100, 40), strokeSize = new JSlider(0, 100, 25);
    private final JButton topColor = colorButton(new Color(74,222,255)), bottomColor = colorButton(new Color(86,74,255));
    private final JButton glowColor = colorButton(new Color(32,211,238)), innerColor = colorButton(new Color(255,160,64));
    private final JButton satinColor = colorButton(new Color(120,72,220)), strokeColor = colorButton(new Color(10,14,22));
    private final List<Label> labels = new ArrayList<>();
    private int selectedLabel = -1;
    private int activeFrame = -1;
    private boolean syncingFrameControls;
    private javax.swing.Timer playback;

    public GifMakerPanel() {
        super(new BorderLayout(12,12));
        setBackground(AssistantTheme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(16,20,18,20));
        add(header(), BorderLayout.NORTH);
        add(workspace(), BorderLayout.CENTER);
        installDropAndPaste();
    }

    private JComponent header() {
        JPanel p=new JPanel(new BorderLayout()); p.setOpaque(false);
        JLabel h=new JLabel("GIF Maker"); h.setFont(h.getFont().deriveFont(Font.BOLD,22f));
        p.add(h,BorderLayout.WEST);
        JLabel s=new JLabel("Add Screenshots  >  Arrange & Label  >  Export animation GIF"); s.setForeground(AssistantTheme.MUTED);
        p.add(s,BorderLayout.EAST); return p;
    }

    private JComponent workspace() {
        JPanel root=new JPanel(new BorderLayout(12,12)); root.setOpaque(false);
        preview.setPreferredSize(new Dimension(700,470));
        root.add(preview,BorderLayout.CENTER); root.add(controls(),BorderLayout.EAST); root.add(timeline(),BorderLayout.SOUTH);
        return root;
    }

    private JComponent timeline() {
        JPanel p=AssistantTheme.card(new BorderLayout(8,8));
        timeline.setRowHeight(100); timeline.setRowMargin(0); timeline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        timeline.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); timeline.setTableHeader(null); timeline.setCellSelectionEnabled(true);
        timeline.setFillsViewportHeight(true); timeline.setShowGrid(false); timeline.setBackground(new Color(10,14,20));
        timeline.setDefaultRenderer(Object.class,(table,value,sel,focus,row,col)->thumbnail(frames.get(col),col,sel));
        timeline.getColumnModel().getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting()){int i=timeline.getSelectedColumn();if(i>=0)select(i);}});
        JScrollPane scroll=new JScrollPane(timeline,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setPreferredSize(new Dimension(700,96)); scroll.getViewport().setBackground(AssistantTheme.PANEL_ALT); p.add(scroll,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,5,0)); actions.setOpaque(false);
        actions.add(button("+ Add images",e->chooseImages())); actions.add(button("Paste",e->paste()));
        actions.add(button("Remove",e->removeFrame())); actions.add(button("<",e->move(-1))); actions.add(button(">",e->move(1)));
        actions.add(new JLabel(" Frame delay:")); actions.add(delay); actions.add(new JLabel("ms"));
        delay.addChangeListener(e->{timeline.repaint();if(playback!=null)playback.setDelay((Integer)delay.getValue());});
        p.add(actions,BorderLayout.NORTH); return p;
    }

    private JLabel thumbnail(Frame frame,int index,boolean selected) {
        BufferedImage thumb=new BufferedImage(116,58,BufferedImage.TYPE_INT_RGB); Graphics2D g=thumb.createGraphics();
        drawFit(g,cropped(frame),116,58,frame.fit,frame.zoom); g.dispose(); JLabel l=new JLabel((index+1)+"  "+delay.getValue()+" ms",new ImageIcon(thumb),SwingConstants.LEFT);
        l.setOpaque(true); l.setBackground(selected?AssistantTheme.ACCENT_DARK:AssistantTheme.PANEL_ALT);
        l.setForeground(AssistantTheme.TEXT); l.setVerticalAlignment(SwingConstants.CENTER); l.setIconTextGap(7);
        l.setBorder(BorderFactory.createEmptyBorder(8,5,8,5)); return l;
    }

    private JComponent controls() {
        JPanel outer=AssistantTheme.card(new BorderLayout()); outer.setPreferredSize(new Dimension(360,500));
        JPanel p=new JPanel(); p.setOpaque(false); p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));
        title(p,"Animation"); row(p,"Size",outputSize); row(p,"Fit",fit); row(p,"Image zoom",zoom); row(p,"Loops",loops); aligned(p,pingPong);
        loops.setToolTipText("0 repeats forever; otherwise the GIF repeats this many times.");
        pingPong.setToolTipText("Play forward and then backward instead of jumping from the last frame to the first.");
        JPanel play=new JPanel(new FlowLayout(FlowLayout.LEFT,4,2)); play.setOpaque(false); play.add(button("Preview",e->togglePlayback())); play.add(button("Export GIF",e->exportGif())); aligned(p,play);
        title(p,"Labels"); font.setMaximumRowCount(12); row(p,"Text",labelText); row(p,"Font",font); row(p,"Size",fontSize);
        JPanel lab=new JPanel(new FlowLayout(FlowLayout.LEFT,4,1)); lab.setOpaque(false); lab.add(button("Add",e->addLabel())); lab.add(button("Remove",e->removeLabel())); aligned(p,lab);
        title(p,"Effects"); row(p,"Blend",blend);
        JPanel checks=new JPanel(new GridLayout(3,2,6,2)); checks.setOpaque(false); checks.add(gradient); checks.add(innerGlow); checks.add(satin); checks.add(outerGlow); checks.add(stroke); checks.add(new JLabel()); aligned(p,checks);
        row(p,"Gradient",topColor,bottomColor); row(p,"Glow / inner",glowColor,innerColor); row(p,"Satin / stroke",satinColor,strokeColor);
        row(p,"Glow size",glowSize); row(p,"Stroke size",strokeSize); row(p,"Opacity",opacity);
        ChangeListener change=e->applyLabelControls(); for(JSlider x:new JSlider[]{opacity,glowSize,strokeSize})x.addChangeListener(change);
        for(AbstractButton x:new AbstractButton[]{gradient,outerGlow,innerGlow,satin,stroke})x.addActionListener(e->applyLabelControls());
        for(JButton x:new JButton[]{topColor,bottomColor,glowColor,innerColor,satinColor,strokeColor})x.addActionListener(e->applyLabelControls());
        blend.addActionListener(e->applyLabelControls()); font.addActionListener(e->applyLabelControls()); fontSize.addChangeListener(change);
        fit.addActionListener(e->{Frame f=selectedFrame();if(!syncingFrameControls&&f!=null){f.fit=fit.getSelectedIndex();preview.repaint();timeline.repaint();}});
        zoom.addChangeListener(e->{Frame f=selectedFrame();if(!syncingFrameControls&&f!=null){f.zoom=zoom.getValue();preview.repaint();timeline.repaint();}});
        outputSize.addActionListener(e->preview.repaint());
        labelText.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){applyLabelControls();} public void removeUpdate(DocumentEvent e){applyLabelControls();} public void changedUpdate(DocumentEvent e){applyLabelControls();}});
        JScrollPane scroll=new JScrollPane(p,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); scroll.setBorder(null); scroll.getViewport().setOpaque(false); scroll.setOpaque(false); scroll.getVerticalScrollBar().setUnitIncrement(18); outer.add(scroll); return outer;
    }

    private void title(JPanel p,String text){ JPanel r=new JPanel(new BorderLayout());r.setOpaque(false);JLabel l=new JLabel(text);l.setFont(l.getFont().deriveFont(Font.BOLD,17f));r.add(l,BorderLayout.WEST);r.setBorder(BorderFactory.createEmptyBorder(9,0,5,0));aligned(p,r); }
    private void aligned(JPanel p,JComponent c){c.setAlignmentX(Component.LEFT_ALIGNMENT);c.setPreferredSize(new Dimension(306,c.getPreferredSize().height));c.setMaximumSize(new Dimension(306,c.getPreferredSize().height));p.add(c);}
    private void row(JPanel p,String name,JComponent... cs){ JPanel r=new JPanel(new FlowLayout(FlowLayout.LEFT,5,2)); r.setOpaque(false); JLabel l=new JLabel(name+":"); l.setPreferredSize(new Dimension(70,25)); r.add(l); for(JComponent c:cs){if(cs.length==2)c.setPreferredSize(new Dimension(70,25));else if(c instanceof JComboBox<?>||c instanceof JTextField)c.setPreferredSize(new Dimension(220,25));else if(c instanceof JSlider)c.setPreferredSize(new Dimension(220,25)); r.add(c);}aligned(p,r); }
    private JButton button(String text,ActionListener a){ JButton b=new JButton(text); b.addActionListener(a); return b; }
    private static JButton colorButton(Color c){ JButton b=new JButton(); b.setBackground(c); b.setPreferredSize(new Dimension(28,22)); b.addActionListener(e->{Color n=JColorChooser.showDialog(b,"Choose color",b.getBackground());if(n!=null){b.setBackground(n);}}); return b; }

    private void chooseImages(){ JFileChooser c=chooser(PREFS.get(OPEN_DIR,".")); c.setMultiSelectionEnabled(true); if(c.showOpenDialog(this)==JFileChooser.APPROVE_OPTION){File[] fs=c.getSelectedFiles(); if(fs.length>0)PREFS.put(OPEN_DIR,fs[0].getParent()); for(File f:fs)addFile(f);} }
    private JFileChooser chooser(String dir){JFileChooser c=new JFileChooser(dir);c.setFileFilter(new FileNameExtensionFilter("Images","png","jpg","jpeg","bmp","gif"));return c;}
    private void addFile(File f){try{BufferedImage i=ImageIO.read(f);if(i==null)throw new IOException("Unsupported image");addFrame(i,f.getName());}catch(IOException ex){error("Could not load "+f.getName()+": "+ex.getMessage());}}
    private void addFrame(BufferedImage image,String name){frames.add(new Frame(image,name));frameModel.fireTableStructureChanged();prepareColumns();select(frames.size()-1);}
    private void prepareColumns(){for(int i=0;i<timeline.getColumnCount();i++)timeline.getColumnModel().getColumn(i).setPreferredWidth(155);}
    private void select(int i){if(i>=0&&i<frames.size()){activeFrame=i;if(timeline.getSelectedColumn()!=i)timeline.setColumnSelectionInterval(i,i);syncingFrameControls=true;Frame f=frames.get(i);fit.setSelectedIndex(f.fit);zoom.setValue(f.zoom);syncingFrameControls=false;}else activeFrame=-1;preview.repaint();}
    private int selected(){return activeFrame;} private Frame selectedFrame(){return activeFrame>=0&&activeFrame<frames.size()?frames.get(activeFrame):null;}
    private void removeFrame(){int i=selected();if(i<0)return;frames.remove(i);frameModel.fireTableStructureChanged();prepareColumns();select(Math.min(i,frames.size()-1));}
    private void move(int d){int i=selected(),j=i+d;if(i<0||j<0||j>=frames.size())return;Collections.swap(frames,i,j);frameModel.fireTableStructureChanged();prepareColumns();select(j);}
    private void paste(){ClipboardImageSupport.paste(image->addFrame(image,"Clipboard"),ex->error("Could not paste image: "+ex.getMessage()));}
    private void installDropAndPaste(){setTransferHandler(new TransferHandler(){public boolean canImport(TransferSupport s){return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);} public boolean importData(TransferSupport s){try{for(Object f:(List<?>)s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor))if(f instanceof File file)addFile(file);return true;}catch(Exception e){return false;}}}); getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("control V"),"paste");getActionMap().put("paste",new AbstractAction(){public void actionPerformed(ActionEvent e){paste();}});}

    private void addLabel(){Label l=new Label();l.text=labelText.getText();l.font=(String)font.getSelectedItem();l.size=(Integer)fontSize.getValue();l.x=.5;l.y=.82;labels.add(l);selectedLabel=labels.size()-1;applyLabelControls();}
    private void removeLabel(){if(selectedLabel>=0&&selectedLabel<labels.size())labels.remove(selectedLabel);selectedLabel=-1;preview.repaint();}
    private void applyLabelControls(){if(selectedLabel<0||selectedLabel>=labels.size())return;Label l=labels.get(selectedLabel);l.text=labelText.getText();l.font=(String)font.getSelectedItem();l.size=(Integer)fontSize.getValue();l.opacity=opacity.getValue();l.gradient=gradient.isSelected();l.outer=outerGlow.isSelected();l.inner=innerGlow.isSelected();l.satin=satin.isSelected();l.stroke=stroke.isSelected();l.blend=blend.getSelectedIndex();l.glow=glowSize.getValue();l.strokeWidth=strokeSize.getValue();l.top=topColor.getBackground();l.bottom=bottomColor.getBackground();l.glowColor=glowColor.getBackground();l.innerColor=innerColor.getBackground();l.satinColor=satinColor.getBackground();l.strokeColor=strokeColor.getBackground();preview.repaint();}

    private void togglePlayback(){if(frames.isEmpty())return;if(playback!=null&&playback.isRunning()){playback.stop();return;}final int[] i={Math.max(0,selected())},direction={1};playback=new javax.swing.Timer((Integer)delay.getValue(),e->{select(i[0]);if(pingPong.isSelected()&&frames.size()>1){if(i[0]==frames.size()-1)direction[0]=-1;else if(i[0]==0)direction[0]=1;i[0]+=direction[0];}else i[0]=(i[0]+1)%frames.size();});playback.start();}
    private void exportGif(){if(frames.isEmpty()){error("Add at least one screenshot first.");return;}JFileChooser c=chooser(PREFS.get(EXPORT_DIR,"."));c.setSelectedFile(new File("level-animation.gif"));if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;File file=c.getSelectedFile();if(!file.getName().toLowerCase().endsWith(".gif"))file=new File(file.getParentFile(),file.getName()+".gif");PREFS.put(EXPORT_DIR,file.getParent());List<BufferedImage> images=new ArrayList<>();List<Integer> delays=new ArrayList<>();int frameDelay=(Integer)delay.getValue();for(Frame f:frames){images.add(render(f));delays.add(frameDelay);}if(pingPong.isSelected()&&frames.size()>2)for(int i=frames.size()-2;i>0;i--){images.add(render(frames.get(i)));delays.add(frameDelay);}try{writeGif(file,images,delays,(Integer)loops.getValue());JOptionPane.showMessageDialog(this,"GIF exported successfully.");}catch(IOException ex){error("Could not export GIF: "+ex.getMessage());}}
    private BufferedImage render(Frame f){int w=(Integer)outputSize.getSelectedItem(),h=w;BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=out.createGraphics();drawFit(g,cropped(f),w,h,f.fit,f.zoom);g.dispose();for(Label l:labels)blend(out,labelLayer(l,w,h),l.blend);return out;}
    private BufferedImage cropped(Frame f){int x=(int)Math.round(f.cropX*f.image.getWidth()),y=(int)Math.round(f.cropY*f.image.getHeight());int w=Math.max(1,(int)Math.round(f.cropW*f.image.getWidth())),h=Math.max(1,(int)Math.round(f.cropH*f.image.getHeight()));x=Math.max(0,Math.min(f.image.getWidth()-w,x));y=Math.max(0,Math.min(f.image.getHeight()-h,y));return f.image.getSubimage(x,y,w,h);}
    private void drawFit(Graphics2D g,BufferedImage im,int w,int h,int mode){drawFit(g,im,w,h,mode,100);}
    private void drawFit(Graphics2D g,BufferedImage im,int w,int h,int mode,int zoomPercent){g.setColor(Color.BLACK);g.fillRect(0,0,w,h);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);double z=zoomPercent/100.0;if(mode==2){int dw=(int)(w*z),dh=(int)(h*z);g.drawImage(im,(w-dw)/2,(h-dh)/2,dw,dh,null);return;}double s=(mode==0?Math.max((double)w/im.getWidth(),(double)h/im.getHeight()):Math.min((double)w/im.getWidth(),(double)h/im.getHeight()))*z;int dw=(int)(im.getWidth()*s),dh=(int)(im.getHeight()*s);g.drawImage(im,(w-dw)/2,(h-dh)/2,dw,dh,null);}
    private BufferedImage labelLayer(Label l,int w,int h){BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=out.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);Font f=new Font(l.font,Font.BOLD,l.size);GlyphVector v=f.createGlyphVector(g.getFontRenderContext(),l.text);Shape shape=v.getOutline((float)(l.x*w-v.getVisualBounds().getWidth()/2),(float)(l.y*h));if(l.outer){g.setColor(alpha(l.glowColor,l.opacity/3));g.setStroke(new BasicStroke(2+12*l.glow/100f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(shape);}if(l.stroke){g.setColor(alpha(l.strokeColor,l.opacity));g.setStroke(new BasicStroke(1+8*l.strokeWidth/100f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(shape);}Paint paint=l.gradient?new GradientPaint(0,(float)(l.y*h-l.size),alpha(l.top,l.opacity),0,(float)(l.y*h),alpha(l.bottom,l.opacity)):alpha(l.top,l.opacity);g.setPaint(paint);g.fill(shape);if(l.inner){g.setColor(alpha(l.innerColor,l.opacity/3));g.setStroke(new BasicStroke(Math.max(1,l.size/18f)));g.draw(shape);}if(l.satin){g.setComposite(AlphaComposite.SrcAtop.derive(.28f));g.setPaint(new GradientPaint(0,(float)(l.y*h-l.size),l.satinColor,0,(float)(l.y*h),new Color(0,0,0,0)));g.fill(shape);}g.dispose();return out;}
    private Color alpha(Color c,int pct){return new Color(c.getRed(),c.getGreen(),c.getBlue(),Math.max(0,Math.min(255,pct*255/100)));}
    private void blend(BufferedImage base,BufferedImage top,int mode){for(int y=0;y<base.getHeight();y++)for(int x=0;x<base.getWidth();x++){int t=top.getRGB(x,y),a=t>>>24;if(a==0)continue;int b=base.getRGB(x,y);int r=channel(b>>16&255,t>>16&255,a,mode),g=channel(b>>8&255,t>>8&255,a,mode),bl=channel(b&255,t&255,a,mode);base.setRGB(x,y,0xff000000|r<<16|g<<8|bl);}}
    private int channel(int b,int t,int a,int m){int z=switch(m){case 1->b*t/255;case 2->255-(255-b)*(255-t)/255;case 3->b<128?2*b*t/255:255-2*(255-b)*(255-t)/255;case 4->Math.min(255,b+t);default->t;};return(b*(255-a)+z*a)/255;}
    private void error(String s){JOptionPane.showMessageDialog(this,s,"GIF Maker",JOptionPane.ERROR_MESSAGE);}

    static void writeGif(File file,List<BufferedImage> images,List<Integer> delays,int loops)throws IOException{Iterator<ImageWriter> it=ImageIO.getImageWritersByFormatName("gif");if(!it.hasNext())throw new IOException("No GIF writer is available");ImageWriter writer=it.next();try(ImageOutputStream out=ImageIO.createImageOutputStream(file)){writer.setOutput(out);writer.prepareWriteSequence(null);for(int i=0;i<images.size();i++){ImageTypeSpecifier type=ImageTypeSpecifier.createFromRenderedImage(images.get(i));IIOMetadata md=writer.getDefaultImageMetadata(type,writer.getDefaultWriteParam());String fmt=md.getNativeMetadataFormatName();IIOMetadataNode root=(IIOMetadataNode)md.getAsTree(fmt);IIOMetadataNode gce=node(root,"GraphicControlExtension");gce.setAttribute("disposalMethod","none");gce.setAttribute("userInputFlag","FALSE");gce.setAttribute("transparentColorFlag","FALSE");gce.setAttribute("delayTime",Integer.toString(Math.max(2,delays.get(i)/10)));gce.setAttribute("transparentColorIndex","0");if(i==0){IIOMetadataNode app=node(root,"ApplicationExtensions"),ext=new IIOMetadataNode("ApplicationExtension");ext.setAttribute("applicationID","NETSCAPE");ext.setAttribute("authenticationCode","2.0");ext.setUserObject(new byte[]{1,(byte)(loops&255),(byte)(loops>>8&255)});app.appendChild(ext);}md.setFromTree(fmt,root);writer.writeToSequence(new IIOImage(images.get(i),null,md),writer.getDefaultWriteParam());}writer.endWriteSequence();}finally{writer.dispose();}}
    private static IIOMetadataNode node(IIOMetadataNode root,String name){for(int i=0;i<root.getLength();i++){Node n=root.item(i);if(name.equals(n.getNodeName()))return(IIOMetadataNode)n;}IIOMetadataNode n=new IIOMetadataNode(name);root.appendChild(n);return n;}

    private final class Preview extends JPanel {
        private static final int NONE=0,MOVE=1,LEFT=2,RIGHT=4,TOP=8,BOTTOM=16,HANDLE=9;
        private final Rectangle imageBounds=new Rectangle(),cropBounds=new Rectangle();
        private Point start; private Rectangle startCrop; private int dragMode,draggingLabel=-1;
        Preview(){
            setBackground(Color.BLACK);setToolTipText("Drag the crop or its handles. Scroll to zoom. Ctrl-drag a label to move it.");
            MouseAdapter mouse=new MouseAdapter(){
                @Override public void mousePressed(MouseEvent e){start=e.getPoint();if(e.isControlDown()){draggingLabel=findLabel(e.getPoint());if(draggingLabel>=0){selectedLabel=draggingLabel;return;}}dragMode=hit(e.getPoint());startCrop=new Rectangle(cropBounds);}
                @Override public void mouseDragged(MouseEvent e){Frame f=selectedFrame();if(f==null)return;if(draggingLabel>=0){Label l=labels.get(draggingLabel);l.x=Math.max(0,Math.min(1,l.x+(e.getX()-start.x)/(double)cropBounds.width));l.y=Math.max(0,Math.min(1,l.y+(e.getY()-start.y)/(double)cropBounds.height));start=e.getPoint();repaint();return;}if(dragMode==NONE)return;int dx=e.getX()-start.x,dy=e.getY()-start.y;Rectangle r=new Rectangle(startCrop);if(dragMode==MOVE){r.x+=dx;r.y+=dy;}else{if((dragMode&LEFT)!=0){r.x+=dx;r.width-=dx;}if((dragMode&RIGHT)!=0)r.width+=dx;if((dragMode&TOP)!=0){r.y+=dy;r.height-=dy;}if((dragMode&BOTTOM)!=0)r.height+=dy;}r.width=Math.max(20,Math.min(imageBounds.width,r.width));r.height=Math.max(20,Math.min(imageBounds.height,r.height));r.x=Math.max(imageBounds.x,Math.min(imageBounds.x+imageBounds.width-r.width,r.x));r.y=Math.max(imageBounds.y,Math.min(imageBounds.y+imageBounds.height-r.height,r.y));f.cropX=(r.x-imageBounds.x)/(double)imageBounds.width;f.cropY=(r.y-imageBounds.y)/(double)imageBounds.height;f.cropW=r.width/(double)imageBounds.width;f.cropH=r.height/(double)imageBounds.height;timeline.repaint();repaint();}
                @Override public void mouseReleased(MouseEvent e){dragMode=NONE;draggingLabel=-1;}
                @Override public void mouseWheelMoved(MouseWheelEvent e){Frame f=selectedFrame();if(f!=null)zoom.setValue(Math.max(zoom.getMinimum(),Math.min(zoom.getMaximum(),f.zoom-e.getWheelRotation()*5)));}
            };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);
        }
        private int findLabel(Point p){for(int i=labels.size()-1;i>=0;i--){Label l=labels.get(i);double x=cropBounds.x+l.x*cropBounds.width,y=cropBounds.y+l.y*cropBounds.height;double scale=cropBounds.width/(double)Math.max(1,(Integer)outputSize.getSelectedItem());if(Math.abs(p.x-x)<Math.max(35,l.text.length()*l.size*scale/3)&&Math.abs(p.y-y)<Math.max(12,l.size*scale))return i;}return-1;}
        private int hit(Point p){if(!new Rectangle(cropBounds.x-HANDLE,cropBounds.y-HANDLE,cropBounds.width+2*HANDLE,cropBounds.height+2*HANDLE).contains(p))return NONE;int m=0;if(Math.abs(p.x-cropBounds.x)<=HANDLE)m|=LEFT;if(Math.abs(p.x-(cropBounds.x+cropBounds.width))<=HANDLE)m|=RIGHT;if(Math.abs(p.y-cropBounds.y)<=HANDLE)m|=TOP;if(Math.abs(p.y-(cropBounds.y+cropBounds.height))<=HANDLE)m|=BOTTOM;return m==0&&cropBounds.contains(p)?MOVE:m;}
        @Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);Frame f=selectedFrame();if(f==null){graphics.setColor(AssistantTheme.MUTED);graphics.drawString("Add screenshots or drop them here",30,40);return;}double scale=Math.min((getWidth()-24.0)/f.image.getWidth(),(getHeight()-24.0)/f.image.getHeight());int dw=(int)(f.image.getWidth()*scale),dh=(int)(f.image.getHeight()*scale);imageBounds.setBounds((getWidth()-dw)/2,(getHeight()-dh)/2,dw,dh);cropBounds.setBounds(imageBounds.x+(int)Math.round(f.cropX*dw),imageBounds.y+(int)Math.round(f.cropY*dh),Math.max(1,(int)Math.round(f.cropW*dw)),Math.max(1,(int)Math.round(f.cropH*dh)));Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(f.image,imageBounds.x,imageBounds.y,dw,dh,null);g.setColor(new Color(0,0,0,150));g.fillRect(imageBounds.x,imageBounds.y,imageBounds.width,cropBounds.y-imageBounds.y);g.fillRect(imageBounds.x,cropBounds.y,cropBounds.x-imageBounds.x,cropBounds.height);g.fillRect(cropBounds.x+cropBounds.width,cropBounds.y,imageBounds.x+imageBounds.width-cropBounds.x-cropBounds.width,cropBounds.height);g.fillRect(imageBounds.x,cropBounds.y+cropBounds.height,imageBounds.width,imageBounds.y+imageBounds.height-cropBounds.y-cropBounds.height);g.drawImage(render(f),cropBounds.x,cropBounds.y,cropBounds.width,cropBounds.height,null);g.setColor(AssistantTheme.ACCENT);g.setStroke(new BasicStroke(2));g.draw(cropBounds);int[] xs={cropBounds.x,cropBounds.x+cropBounds.width/2,cropBounds.x+cropBounds.width};int[] ys={cropBounds.y,cropBounds.y+cropBounds.height/2,cropBounds.y+cropBounds.height};for(int x:xs)for(int y:ys){g.setColor(AssistantTheme.TEXT);g.fillRect(x-4,y-4,9,9);g.setColor(AssistantTheme.ACCENT_DARK);g.drawRect(x-4,y-4,9,9);}g.dispose();}
    }
    private final class FrameModel extends AbstractTableModel {public int getRowCount(){return frames.isEmpty()?0:1;}public int getColumnCount(){return frames.size();}public Object getValueAt(int r,int c){return frames.get(c);}}
    private static final class Frame {final BufferedImage image;final String name;int fit=1,zoom=100;double cropX=0,cropY=0,cropW=1,cropH=1;Frame(BufferedImage i,String n){image=i;name=n;}}
    private static final class Label {String text="",font="Verdana";int size=64,opacity=100,blend,glow=40,strokeWidth=25;double x,y;boolean gradient,outer,inner,satin,stroke;Color top,bottom,glowColor,innerColor,satinColor,strokeColor;}
}
