/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.List;
import java.util.Set;
import javafx.scene.layout.Pane;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;

/**
 *
 */
public interface DetailViewNode {

    long getStartMillis();

    long getEndMillis();

    public void setDescriptionVisibility(DescriptionVisibility get);

    public Pane getSubNodePane();

    public void setSpanWidths(List<Double> spanWidths);

    public void setDescriptionWidth(double max);

    public EventType getType();

    public Set<Long> getEventIDs();

    public void applySelectionEffect(boolean applied);

    public String getDescription();

    public EventBundle getBundleDescriptor();

}
