/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.actions;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * This Action allows users to replace a tag applied to blackboard
 * artifacts, with another tag
 */
public final class ReplaceBlackboardArtifactTagAction extends ReplaceTagAction<BlackboardArtifactTag> {

    private static final Logger logger = Logger.getLogger(ReplaceBlackboardArtifactTagAction.class.getName());
    private static final long serialVersionUID = 1L;

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static ReplaceBlackboardArtifactTagAction instance;

    public static synchronized ReplaceBlackboardArtifactTagAction getInstance() {
        if (null == instance) {
            instance = new ReplaceBlackboardArtifactTagAction();
        }
        return instance;
    }

    private ReplaceBlackboardArtifactTagAction() {
        super(MENU_TEXT);
    }

    /**
     * Replaces the specified tag on the given artifact with the new one
     * 
     * @param oldArtifactTag tag to be replaced
     * @param newTagName name of the tag to replace with
     * @param newComment the newComment for the tag use an empty string for no newComment
     */
    @NbBundle.Messages({
        "# {0} - old tag name",
        "# {1} - artifactID",
        "ReplaceBlackboardArtifactTagAction.replaceTag.alert=Unable to replace tag {0} for artifact {1}."})
    @Override
    protected void replaceTag( BlackboardArtifactTag oldArtifactTag, TagName newTagName, String newComment) {
        new SwingWorker<Void, Void>() {
            
            @Override
            protected Void doInBackground() throws Exception {
                TagsManager tagsManager;
                try {
                    tagsManager = Case.getCurrentCaseThrows().getServices().getTagsManager();
                } catch (NoCurrentCaseException ex) {
                    logger.log(Level.SEVERE, "Error replacing artifact tag. No open case found.", ex); //NON-NLS
                    Platform.runLater(()
                            -> new Alert(Alert.AlertType.ERROR, Bundle.ReplaceBlackboardArtifactTagAction_replaceTag_alert(oldArtifactTag.getName().getDisplayName(), oldArtifactTag.getArtifact().getArtifactID())).show()
                    );
                    return null;
                }

                try {
                    logger.log(Level.INFO, "Replacing tag {0}  with tag {1} for artifact {2}", new Object[]{oldArtifactTag.getName().getDisplayName(), newTagName.getDisplayName(), oldArtifactTag.getContent().getName()}); //NON-NLS

                    tagsManager.deleteBlackboardArtifactTag(oldArtifactTag);
                    tagsManager.addBlackboardArtifactTag(oldArtifactTag.getArtifact(), newTagName, newComment);

                } catch (TskCoreException tskCoreException) {
                    logger.log(Level.SEVERE, "Error replacing artifact tag", tskCoreException); //NON-NLS
                    Platform.runLater(()
                            -> new Alert(Alert.AlertType.ERROR, Bundle.ReplaceBlackboardArtifactTagAction_replaceTag_alert(oldArtifactTag.getName().getDisplayName(), oldArtifactTag.getArtifact().getArtifactID())).show()
                    );
                }
                return null;
            }

            @Override
            protected void done() {
                super.done();
                try {
                    get();
                } catch (InterruptedException | ExecutionException ex) {
                    logger.log(Level.SEVERE, "Unexpected exception while replacing artifact tag", ex); //NON-NLS
                }
            }
        }.execute();
    }

   /**
     * Returns list of tags selected by user to replace
     *
     * @return a list of tags
     */
    @Override
    Collection<? extends BlackboardArtifactTag> getTagsToReplace() {
        return Utilities.actionsGlobalContext().lookupAll(BlackboardArtifactTag.class);
    }

}
