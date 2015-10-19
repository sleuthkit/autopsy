/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import java.awt.event.ActionEvent;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import org.joda.time.Interval;
import org.openide.util.Exceptions;
import org.openide.util.Utilities;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.ArtifactEventType;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class ViewInTimeLineAction extends AbstractAction {

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
        TreeSet<Long> timestamps = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class).stream()
                .flatMap(file -> Stream.of(file.getAtime(), file.getCrtime(), file.getCtime(), file.getMtime()))
                .collect(Collectors.toCollection(TreeSet::new));

        //for each artifact, get all datetime attributes for that artifact type
        for (BlackboardArtifact bbart : Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class)) {
            Set<BlackboardAttribute.ATTRIBUTE_TYPE> attributeTypes = ArtifactEventType.getAllArtifactEventTypes().stream()
                    .filter(artEventType -> bbart.getArtifactTypeID() == artEventType.getArtifactType().getTypeID())
                    .map(ArtifactEventType::getDateTimeAttrubuteType)
                    .collect(Collectors.toSet());

            for (BlackboardAttribute.ATTRIBUTE_TYPE type : attributeTypes) {
                try {
                    Set<Long> collect1 = bbart.getAttributes(type).stream().map(BlackboardAttribute::getValueLong).collect(Collectors.toSet());
                    timestamps.addAll(collect1);
                } catch (TskCoreException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }

        Interval interval = new Interval(timestamps.first() * 1000, 1 + timestamps.last() * 1000);

        SystemAction.get(OpenTimelineAction.class).showTimeline(interval);
    }
}
