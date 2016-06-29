/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.timeline.actions;

import java.awt.event.ActionEvent;
import java.util.Set;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.ArtifactEventType;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An action that shows the given artifact in the Timeline List View.
 */
public final class ViewArtifactInTimelineAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    private static final Set<ArtifactEventType> ARTIFACT_EVENT_TYPES =
            EventType.allTypes.stream()
            .filter((EventType t) -> t instanceof ArtifactEventType)
            .map(ArtifactEventType.class::cast)
            .collect(Collectors.toSet());

    private final BlackboardArtifact artifact;

    @NbBundle.Messages({"ViewArtifactInTimelineAction.displayName=View Result in Timeline... "})
    public ViewArtifactInTimelineAction(BlackboardArtifact artifact) {
        super(Bundle.ViewArtifactInTimelineAction_displayName());
        this.artifact = artifact;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        SystemAction.get(OpenTimelineAction.class).showArtifactInTimeline(artifact);
    }

    /**
     * Does the given artifact have a type that Timeline supports, and does it
     * have a positive timestamp in the supported attribute?
     *
     * @param artifact The artifact to test for a supported timestamp
     *
     * @return True if this artifact has a timestamp supported by Timeline.
     */
    public static boolean hasSupportedTimeStamp(BlackboardArtifact artifact) throws TskCoreException {
        //see if the given artifact is a supported type ...
        for (ArtifactEventType artEventType : ARTIFACT_EVENT_TYPES) {
            if (artEventType.getArtifactTypeID() == artifact.getArtifactTypeID()) {
                //... and has a non-bogus timestamp in the supported attribute
                BlackboardAttribute attribute = artifact.getAttribute(artEventType.getDateTimeAttributeType());
                if (null != attribute && attribute.getValueLong() > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
