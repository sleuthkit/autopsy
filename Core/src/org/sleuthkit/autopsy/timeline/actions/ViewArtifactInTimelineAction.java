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
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.openide.util.actions.SystemAction;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.timeline.OpenTimelineAction;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * An action that shows the given artifact in the Timeline List View.
 */
public final class ViewArtifactInTimelineAction extends AbstractAction {

    private static final Logger logger = Logger.getLogger(ViewFileInTimelineAction.class.getName());

    private final BlackboardArtifact artifact;

    /**
     * Constructor.
     * @param artifact The artifact to navigate to in the timeline.
     */
    @NbBundle.Messages({"ViewArtifactInTimelineAction.displayName=View Result in Timeline... "})
    public ViewArtifactInTimelineAction(BlackboardArtifact artifact) {
        this(artifact, Bundle.ViewArtifactInTimelineAction_displayName());
    }

        /**
     * Constructor.
     * @param artifact The artifact to navigate to in the timeline.
     * @param displayName The display name for the action.
     */
    public ViewArtifactInTimelineAction(BlackboardArtifact artifact, String displayName) {
        super(displayName);
        this.artifact = artifact;
        // If timeline functionality is not available this action is disabled.
        if ("false".equals(ModuleSettings.getConfigSetting("timeline", "enable_timeline"))) {
            setEnabled(false);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            SystemAction.get(OpenTimelineAction.class).showArtifactInTimeline(artifact);
        } catch (TskCoreException ex) {
            MessageNotifyUtil.Message.error("Error opening Timeline");
            logger.log(Level.SEVERE, "Error showing timeline.", ex);
        }
    }

    /**
     * Does the given artifact have a datetime attribute?
     *
     * @param artifact The artifact to test for a supported timestamp
     *
     * @return True if this artifact has a timestamp supported by Timeline.
     */
    public static boolean hasSupportedTimeStamp(BlackboardArtifact artifact) throws TskCoreException {

        for (BlackboardAttribute attr : artifact.getAttributes()) {
            if (attr.getValueType() == BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME) {
                return true;
            }
        }
        return false;
    }
}
