/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui;

import javafx.scene.chart.Axis;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;
import org.sleuthkit.autopsy.timeline.TimeLineController;

public interface ContextMenuProvider<X> {

    public Axis<X> getXAxis();

    public TimeLineController getController();

    ContextMenu getContextMenu();

    ContextMenu getChartContextMenu(MouseEvent m);

    IntervalSelector<? extends X> getIntervalSelector();

    void setIntervalSelector(IntervalSelector<? extends X> newIntervalSelector);

    /**
     * derived classes should implement this so as to supply an appropriate
     * subclass of {@link IntervalSelector}
     *
     * @return a new interval selector
     */
    IntervalSelector<X> newIntervalSelector();

    /**
     * clear any references to previous interval selectors , including removing
     * the interval selector from the ui / scene-graph
     */
    void clearIntervalSelector();
}
