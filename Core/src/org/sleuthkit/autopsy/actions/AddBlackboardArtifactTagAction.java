/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.util.HashSet;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this Action allow users to apply tags to blackboard artifacts.
 */
@NbBundle.Messages({
    "AddBlackboardArtifactTagAction.singularTagResult=Tag Result",
    "AddBlackboardArtifactTagAction.pluralTagResult=Tag Results",
    "# {0} - artifactName",
    "AddBlackboardArtifactTagAction.unableToTag.msg=Unable to tag {0}.",
    "AddBlackboardArtifactTagAction.taggingErr=Tagging Error"
})
public class AddBlackboardArtifactTagAction extends AddTagAction {

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static AddBlackboardArtifactTagAction instance;

    public static synchronized AddBlackboardArtifactTagAction getInstance() {
        if (null == instance) {
            instance = new AddBlackboardArtifactTagAction();
        }
        return instance;
    }

    private AddBlackboardArtifactTagAction() {
        super("");
    }

    @Override
    protected String getActionDisplayName() {
        String singularTagResult = NbBundle.getMessage(this.getClass(),
                "AddBlackboardArtifactTagAction.singularTagResult");
        String pluralTagResult = NbBundle.getMessage(this.getClass(),
                "AddBlackboardArtifactTagAction.pluralTagResult");
        return Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class).size() > 1 ? pluralTagResult : singularTagResult;
    }

    @Override
    protected void addTag(TagName tagName, String comment) {
        /*
         * The documentation for Lookup.lookupAll() explicitly says that the
         * collection it returns may contain duplicates. Within this invocation
         * of addTag(), we don't want to tag the same BlackboardArtifact more
         * than once, so we dedupe the BlackboardArtifacts by stuffing them into
         * a HashSet.
         */
        final Collection<BlackboardArtifact> selectedArtifacts = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));

        new Thread(() -> {
            for (BlackboardArtifact artifact : selectedArtifacts) {
                try {
                    Case.getOpenCase().getServices().getTagsManager().addBlackboardArtifactTag(artifact, tagName, comment);
                } catch (TskCoreException | NoCurrentCaseException ex) {
                    Logger.getLogger(AddBlackboardArtifactTagAction.class.getName()).log(Level.SEVERE, "Error tagging result", ex); //NON-NLS
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                                NbBundle.getMessage(this.getClass(),
                                        "AddBlackboardArtifactTagAction.unableToTag.msg",
                                        artifact.getDisplayName()),
                                NbBundle.getMessage(this.getClass(),
                                        "AddBlackboardArtifactTagAction.taggingErr"),
                                JOptionPane.ERROR_MESSAGE);
                    });
                    break;
                }
            }
        }).start();
    }
}
