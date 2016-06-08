/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.datamodel;

import com.google.common.collect.Iterables;
import java.awt.event.ActionEvent;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import org.openide.util.Utilities;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.ArtifactEventType;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 *
 */
public class ViewArtifactInTimelineAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static ViewArtifactInTimelineAction instance;

    public static synchronized ViewArtifactInTimelineAction getInstance() {
        if (null == instance) {
            instance = new ViewArtifactInTimelineAction();
        }
        return instance;
    }

    private ViewArtifactInTimelineAction() {
        super("View result in Timeline");
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        final Set<Integer> artifactEventTypeIDs = ArtifactEventType.getAllArtifactEventTypes().stream()
                .map(ArtifactEventType::getArtifactTypeID)
                .collect(Collectors.toSet());

        //for each artifact, get all datetime attributes for that artifact type
        Set<BlackboardArtifact> artifacts = Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class).stream()
                .filter(artifact -> artifactEventTypeIDs.contains(artifact.getArtifactTypeID()))
                .collect(Collectors.toSet());

        if (artifacts.size() > 1) {
            return;
        } else {
            SystemAction.get(OpenTimelineAction.class).showArtifactInTimeline(Iterables.getOnlyElement(artifacts, null));
        }
    }
}
