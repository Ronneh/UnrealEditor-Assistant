import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

/** Extracts reflowable text and meaningful embedded images in approximate reading order. */
final class PdfTutorialContentExtractor {
    List<PageContent> extract(PDDocument document) throws IOException {
        List<PageContent> pages = new ArrayList<>();
        for (int index = 0; index < document.getNumberOfPages(); index++) {
            PositionedTextStripper text = new PositionedTextStripper(index + 1);
            text.getText(document);
            ImageCollector images = new ImageCollector(document.getPage(index));
            images.processPage(document.getPage(index));
            List<ContentBlock> blocks = new ArrayList<>(text.blocks);
            blocks.addAll(images.blocks);
            blocks.sort(Comparator.comparingDouble(ContentBlock::y)
                    .thenComparingInt(block -> block instanceof TextBlock ? 0 : 1));
            pages.add(new PageContent(index + 1, List.copyOf(blocks)));
        }
        return List.copyOf(pages);
    }

    sealed interface ContentBlock permits TextBlock, ImageBlock { float y(); }
    record TextBlock(float y, String text) implements ContentBlock { }
    record ImageBlock(float y, BufferedImage image) implements ContentBlock { }
    record PageContent(int pageNumber, List<ContentBlock> blocks) { }

    private static final class PositionedTextStripper extends PDFTextStripper {
        private final List<ContentBlock> blocks = new ArrayList<>();

        PositionedTextStripper(int page) throws IOException {
            setStartPage(page);
            setEndPage(page);
            setSortByPosition(true);
            setSuppressDuplicateOverlappingText(true);
        }

        @Override protected void writeString(String value, List<TextPosition> positions) {
            String cleaned = value.replaceAll("\\s+", " ").strip();
            if (cleaned.isBlank() || positions.isEmpty()) return;
            float y = positions.stream().map(TextPosition::getYDirAdj).min(Float::compare).orElse(0f);
            if (!blocks.isEmpty() && blocks.get(blocks.size() - 1) instanceof TextBlock previous
                    && Math.abs(previous.y() - y) < 2.5f) {
                blocks.set(blocks.size() - 1, new TextBlock(previous.y(), previous.text() + " " + cleaned));
            } else {
                blocks.add(new TextBlock(y, cleaned));
            }
        }
    }

    private static final class ImageCollector extends PDFGraphicsStreamEngine {
        private final List<ContentBlock> blocks = new ArrayList<>();
        private Point2D currentPoint;

        ImageCollector(PDPage page) { super(page); }

        @Override public void drawImage(PDImage image) throws IOException {
            Matrix matrix = getGraphicsState().getCurrentTransformationMatrix();
            float width = Math.abs(matrix.getScalingFactorX());
            float height = Math.abs(matrix.getScalingFactorY());
            if (image.isStencil() || image.getWidth() < 80 || image.getHeight() < 50
                    || width < 40 || height < 25) return;
            BufferedImage rendered = image.getImage();
            if (rendered == null) return;
            float y = getPage().getCropBox().getHeight() - matrix.getTranslateY() - height;
            blocks.add(new ImageBlock(Math.max(0, y), rendered));
        }

        @Override public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) { }
        @Override public void clip(int windingRule) { }
        @Override public void moveTo(float x, float y) { currentPoint = new Point2D.Float(x, y); }
        @Override public void lineTo(float x, float y) { currentPoint = new Point2D.Float(x, y); }
        @Override public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            currentPoint = new Point2D.Float(x3, y3);
        }
        @Override public Point2D getCurrentPoint() { return currentPoint; }
        @Override public void closePath() { }
        @Override public void endPath() { }
        @Override public void strokePath() { }
        @Override public void fillPath(int windingRule) { }
        @Override public void fillAndStrokePath(int windingRule) { }
        @Override public void shadingFill(COSName shadingName) { }
    }
}
