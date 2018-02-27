/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2017-2018 Basis Technology Corp.
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
import java.util.HashSet;
import java.util.List;
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
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

/**
 * Instances of this Action allow users to delete tags applied to content.
 */
@NbBundle.Messages({
    "DeleteFileContentTagAction.deleteTag=Remove File Tag"
})
public class DeleteFileContentTagAction extends AbstractAction implements Presenter.Popup {
    
    private static final Logger logger = Logger.getLogger(DeleteFileContentTagAction.class.getName());

    private static final long serialVersionUID = 1L;
    private static final String MENU_TEXT = NbBundle.getMessage(DeleteFileContentTagAction.class,
            "DeleteFileContentTagAction.deleteTag");

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static DeleteFileContentTagAction instance;

    public static synchronized DeleteFileContentTagAction getInstance() {
        if (null == instance) {
            instance = new DeleteFileContentTagAction();
        }
        return instance;
    }

    private DeleteFileContentTagAction() {
        super(MENU_TEXT);
    }

    @Override
    public JMenuItem getPopupPresenter() {
        return new TagMenu();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
    }

    protected String getActionDisplayName() {
        return MENU_TEXT;
    }

    @NbBundle.Messages({
        "# {0} - fileID",
        "DeleteFileContentTagAction.deleteTag.alert=Unable to untag file {0}."})
    protected void deleteTag(TagName tagName, ContentTag contentTag, long fileId) {
        new SwingWorker<Void, Void>() {

            @Override
            protected Void doInBackground() throws Exception {
                TagsManager tagsManager;
                try {
                    tagsManager = Case.getOpenCase().getServices().getTagsManager();
                } catch (NoCurrentCaseException ex) {
                    logger.log(Level.SEVERE, "Error untagging file. No open case found.", ex); //NON-NLS
                    Platform.runLater(() ->
                            new Alert(Alert.AlertType.ERROR, Bundle.DeleteFileContentTagAction_deleteTag_alert(fileId)).show()
                    );
                    return null;
                }
                
                try {
                    logger.log(Level.INFO, "Removing tag {0} from {1}", new Object[]{tagName.getDisplayName(), contentTag.getContent().getName()}); //NON-NLS
                    tagsManager.deleteContentTag(contentTag);
                } catch (TskCoreException tskCoreException) {
                    logger.log(Level.SEVERE, "Error untagging file", tskCoreException); //NON-NLS
                    Platform.runLater(() ->
                            new Alert(Alert.AlertType.ERROR, Bundle.DeleteFileContentTagAction_deleteTag_alert(fileId)).show()
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
                    logger.log(Level.SEVERE, "Unexpected exception while untagging file", ex); //NON-NLS
                }
            }
        }.execute();
    }

    /**
     * Instances of this class implement a context menu user interface for
     * creating or selecting a tag name for a tag and specifying an optional tag
     * comment.
     */
    private class TagMenu extends JMenu {

        private static final long serialVersionUID = 1L;

        TagMenu() {
            super(getActionDisplayName());
            
            final Collection<AbstractFile> selectedAbstractFilesList =
                    new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
            
            if(!selectedAbstractFilesList.isEmpty()) {
                AbstractFile file = selectedAbstractFilesList.iterator().next();
                
                Map<String, TagName> tagNamesMap = null;
                try {
                    // Get the current set of tag names.
                    TagsManager tagsManager = Case.getOpenCase().getServices().getTagsManager();

                    tagNamesMap = new TreeMap<>(tagsManager.getDisplayNamesToTagNamesMap());
                } catch (TskCoreException | NoCurrentCaseException ex) {
                    Logger.getLogger(TagsManager.class.getName()).log(Level.SEVERE, "Failed to get tag names", ex); //NON-NLS
                }

                // Each tag name in the current set of tags gets its own menu item in
                // the "Quick Tags" sub-menu. Selecting one of these menu items adds
                // a tag with the associated tag name.
                if (null != tagNamesMap && !tagNamesMap.isEmpty()) {
                    try {
                        List<ContentTag> existingTagsList =
                                Case.getOpenCase().getServices().getTagsManager()
                                        .getContentTagsByContent(file);

                        for (Map.Entry<String, TagName> entry : tagNamesMap.entrySet()) {
                            String tagDisplayName = entry.getKey();

                            TagName tagName = entry.getValue();
                            for(ContentTag contentTag : existingTagsList) {
                                if(tagDisplayName.equals(contentTag.getName().getDisplayName())) {
                                    String notableString = tagName.getKnownStatus() == TskData.FileKnown.BAD ? TagsManager.getNotableTagLabel() : "";
                                    JMenuItem tagNameItem = new JMenuItem(tagDisplayName + notableString);
                                    tagNameItem.addActionListener((ActionEvent e) -> {
                                        deleteTag(tagName, contentTag, file.getId());
                                    });
                                    add(tagNameItem);
                                }
                            }
                        }
                    } catch (TskCoreException | NoCurrentCaseException ex) {
                        Logger.getLogger(TagMenu.class.getName())
                                .log(Level.SEVERE, "Error retrieving tags for TagMenu", ex); //NON-NLS
                    }
                }

                if(getItemCount() == 0) {
                    setEnabled(false);
                }
            }
        }
    }

}
