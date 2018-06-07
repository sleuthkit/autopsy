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

import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javax.swing.AbstractAction;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.actions.Presenter;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.TagsManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Instances of this Action allow users to replace a tag applied to blackboard
 * artifacts, with another tag
 */
@NbBundle.Messages({
    "ReplaceBlackboardArtifactTagAction.replaceTag=Replace Result Tag"
})
public final class ReplaceBlackboardArtifactTagAction extends AbstractAction implements Presenter.Popup {

    private static final Logger logger = Logger.getLogger(ReplaceBlackboardArtifactTagAction.class.getName());

    private static final long serialVersionUID = 1L;
    private static final String MENU_TEXT = NbBundle.getMessage(ReplaceBlackboardArtifactTagAction.class,
            "ReplaceBlackboardArtifactTagAction.replaceTag");

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

    @Override
    public void actionPerformed(ActionEvent event) {
    }

    protected String getActionDisplayName() {
        return MENU_TEXT;
    }

    @NbBundle.Messages({
        "# {0} - old tag name",
        "# {1} - artifactID",
        "ReplaceBlackboardArtifactTagAction.replaceTag.alert=Unable to replace tag {0} for artifact {1}."})
    protected void replaceTag(TagName oldTagName, TagName newTagName, BlackboardArtifactTag oldArtifactTag) {
        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                TagsManager tagsManager;
                try {
                    tagsManager = Case.getCurrentCaseThrows().getServices().getTagsManager();
                } catch (NoCurrentCaseException ex) {
                    logger.log(Level.SEVERE, "Error replacing artifact tag. No open case found.", ex); //NON-NLS
                    Platform.runLater(()
                            -> new Alert(Alert.AlertType.ERROR, Bundle.ReplaceBlackboardArtifactTagAction_replaceTag_alert(oldTagName.getDisplayName(), oldArtifactTag.getArtifact().getArtifactID())).show()
                    );
                    return null;  
                }

                try {
                    logger.log(Level.INFO, "Replacing tag {0}  with tag {1} for artifact {2}", new Object[]{oldTagName.getDisplayName(), newTagName.getDisplayName(), oldArtifactTag.getContent().getName()}); //NON-NLS
                    
                   System.out.println("RAMAN: Replacing tag " + oldTagName.getDisplayName() + " with tag " + newTagName.getDisplayName() );
                   tagsManager.deleteBlackboardArtifactTag(oldArtifactTag);
                   tagsManager.addBlackboardArtifactTag(oldArtifactTag.getArtifact(), newTagName);
                           
                 
                } catch (TskCoreException tskCoreException) {
                    logger.log(Level.SEVERE, "Error replacing artifact tag", tskCoreException); //NON-NLS
                    Platform.runLater(()
                            -> new Alert(Alert.AlertType.ERROR, Bundle.ReplaceBlackboardArtifactTagAction_replaceTag_alert(oldTagName.getDisplayName(), oldArtifactTag.getArtifact().getArtifactID())).show()
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

    @Override
    public JMenuItem getPopupPresenter() {
         return new ReplaceArtifactTagMenu();
    }

    /**
     * Instances of this class implement a context menu user interface for
     * selecting a tag name to replace the tag with 
     */
    private final class ReplaceArtifactTagMenu extends JMenu {

        private static final long serialVersionUID = 1L;

        ReplaceArtifactTagMenu() {
            super(getActionDisplayName());
            
            final Collection<? extends BlackboardArtifactTag> selectedTags = Utilities.actionsGlobalContext().lookupAll(BlackboardArtifactTag.class);

            // Get the current set of tag names.
            Map<String, TagName> tagNamesMap = null;
            try {
                TagsManager tagsManager = Case.getCurrentCaseThrows().getServices().getTagsManager();
                tagNamesMap = new TreeMap<>(tagsManager.getDisplayNamesToTagNamesMap());
            } catch (TskCoreException | NoCurrentCaseException ex) {
                Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
            }

           
            if (null != tagNamesMap && !tagNamesMap.isEmpty()) {
                for (Map.Entry<String, TagName> entry : tagNamesMap.entrySet()) {
                    String tagDisplayName = entry.getKey();
                    String notableString = entry.getValue().getKnownStatus() == TskData.FileKnown.BAD ? TagsManager.getNotableTagLabel() : "";
                    JMenuItem tagNameItem = new JMenuItem(tagDisplayName + notableString);
                    // for the bookmark tag name only, added shortcut label
                    if (tagDisplayName.equals(NbBundle.getMessage(AddTagAction.class, "AddBookmarkTagAction.bookmark.text"))) {
                        tagNameItem.setAccelerator(AddBookmarkTagAction.BOOKMARK_SHORTCUT);
                    }

                    // Add action to replace the tag
                    tagNameItem.addActionListener((ActionEvent e) -> {
                        selectedTags.forEach((oldtag) -> {
                            replaceTag(oldtag.getName(), entry.getValue(), oldtag);
                        });
                    });
                    
                    add(tagNameItem);
                }
            } else {
                JMenuItem empty = new JMenuItem(NbBundle.getMessage(this.getClass(), "AddTagAction.noTags"));
                empty.setEnabled(false);
                add(empty);
            }

            addSeparator();
           
            JMenuItem newTagMenuItem = new JMenuItem(NbBundle.getMessage(this.getClass(), "AddTagAction.newTag"));
            newTagMenuItem.addActionListener((ActionEvent e) -> {
                TagName newTagName = GetTagNameDialog.doDialog();
                if (null != newTagName) {
                    selectedTags.forEach((oldtag) -> {
                        replaceTag(oldtag.getName(), newTagName, oldtag);
                    });
                }
            });
            add(newTagMenuItem);
            
        }
    }
        
}
