/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import javafx.scene.Node;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 *
 */
public interface DetailViewNode<S extends DetailViewNode<S>> {

    void setDescriptionVisibility(DescriptionVisibility get);

    List<S> getSubBundleNodes();

    List<Node> getSubNodes();

    void setSpanWidths(List<Double> spanWidths);

    void setDescriptionWidth(double max);

    EventBundle getEventBundle();

    /**
     * apply the 'effect' to visually indicate highlighted nodes
     *
     * @param applied true to apply the highlight 'effect', false to remove it
     */
    void applyHighlightEffect(boolean applied);

    void applySelectionEffect(boolean applied);

    default String getDescription() {
        return getEventBundle().getDescription();
    }

    default EventType getEventType() {
        return getEventBundle().getEventType();
    }

    default Set<Long> getEventIDs() {
        return getEventBundle().getEventIDs();
    }

    default long getStartMillis() {
        return getEventBundle().getStartMillis();
    }

    default long getEndMillis() {
        return getEventBundle().getEndMillis();
    }

    static class StartTimeComparator implements Comparator<DetailViewNode<?>> {

        @Override
        public int compare(DetailViewNode<?> o1, DetailViewNode<?> o2) {
            return Long.compare(o1.getStartMillis(), o2.getStartMillis());
        }
    }
}
