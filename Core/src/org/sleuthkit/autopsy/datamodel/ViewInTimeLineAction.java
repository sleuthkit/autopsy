/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.LongSummaryStatistics;
import java.util.stream.LongStream;
import javax.swing.AbstractAction;
import org.joda.time.Interval;
import org.openide.util.Utilities;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;

/**
 *
 */
class ViewInTimeLineAction extends AbstractAction {

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static ViewInTimeLineAction instance;

    public static synchronized ViewInTimeLineAction getInstance() {
        if (null == instance) {
            instance = new ViewInTimeLineAction();
        }
        return instance;
    }

    private ViewInTimeLineAction() {
        super("View in Timeline");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Collection<? extends AbstractFile> selectedFiles = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class);

        LongSummaryStatistics summaryStatistics = selectedFiles.stream()
                .flatMapToLong(file -> LongStream.of(file.getAtime(), file.getCrtime(), file.getCtime(), file.getMtime()))
                .summaryStatistics();

        Interval interval = new Interval(summaryStatistics.getMin() * 1000, 1 + summaryStatistics.getMax() * 1000);

        SystemAction.get(OpenTimelineAction.class).showTimeline(interval);
    }
}
