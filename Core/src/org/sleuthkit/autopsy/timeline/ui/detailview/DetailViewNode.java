/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.List;
import java.util.Set;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 *
 */
public interface DetailViewNode<S extends DetailViewNode<S>> {

    public long getStartMillis();

    public long getEndMillis();

    public void setDescriptionVisibility(DescriptionVisibility get);

    public List<S> getSubNodes();

    public void setSpanWidths(List<Double> spanWidths);

    public void setDescriptionWidth(double max);

    public EventType getEventType();

    public Set<Long> getEventIDs();

    public String getDescription();

    public EventBundle getEventBundle();

    /**
     * apply the 'effect' to visually indicate highlighted nodes
     *
     * @param applied true to apply the highlight 'effect', false to remove it
     */
    void applyHighlightEffect(boolean applied);

    public void applySelectionEffect(boolean applied);
}
