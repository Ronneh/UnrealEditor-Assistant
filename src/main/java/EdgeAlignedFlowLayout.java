import java.awt.Container;
import java.awt.FlowLayout;

/** Flow layout whose first/last component is flush with the panel edge. */
final class EdgeAlignedFlowLayout extends FlowLayout {
    EdgeAlignedFlowLayout(int alignment, int horizontalGap, int verticalGap) {
        super(alignment, horizontalGap, verticalGap);
    }

    @Override public void layoutContainer(Container target) {
        super.layoutContainer(target);
        int shift = getAlignment() == FlowLayout.RIGHT ? getHgap() : -getHgap();
        for (java.awt.Component component : target.getComponents())
            component.setLocation(component.getX() + shift, component.getY());
    }
}
