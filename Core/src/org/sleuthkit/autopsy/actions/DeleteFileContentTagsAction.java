/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013-2015 Basis Technology Corp.
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
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this Action allow users to delete tags applied to content.
 */
public class DeleteFileContentTagsAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final String MENU_TEXT = NbBundle.getMessage(DeleteFileContentTagsAction.class,
            "DeleteFileContentTagsAction.deleteTags");

    // This class is a singleton to support multi-selection of nodes, since 
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static DeleteFileContentTagsAction instance;

    public static synchronized DeleteFileContentTagsAction getInstance() {
        if (null == instance) {
            instance = new DeleteFileContentTagsAction();
        }
        return instance;
    }

    private DeleteFileContentTagsAction() {
        super(MENU_TEXT);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // TODO: This should be 'selectedFilesList'.
        //final Collection<? extends ContentTag> selectedTagsList = Utilities.actionsGlobalContext().lookupAll(ContentTag.class);
        final Collection<AbstractFile> selectedFilesList = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        new Thread(() -> {
            for(AbstractFile selectedFile : selectedFilesList) {
                try {
                    List<ContentTag> existingTagsList = Case.getCurrentCase().getServices().getTagsManager().getAllContentTags();
                    for(ContentTag existingTag : existingTagsList) {
                        if(existingTag.getContent().getId() == selectedFile.getId()) {
                            Case.getCurrentCase().getServices().getTagsManager().deleteContentTag(existingTag);
                        }
                    }
                } catch (TskCoreException ex) {
                    Logger.getLogger(AddContentTagAction.class.getName())
                            .log(Level.SEVERE, "Error deleting tag", ex); //NON-NLS
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null,
                                NbBundle.getMessage(this.getClass(),
                                        "DeleteFileContentTagsAction.unableToDelTag.msg",
                                        selectedFile),
                                NbBundle.getMessage(this.getClass(), "DeleteFileContentTagsAction.tagDelErr"),
                                JOptionPane.ERROR_MESSAGE);
                    });
                    break;
                }
            }
        }).start();
    }

    /**
     * Deprecated, use actionPerformed() instead.
     *
     * @param event The event associated with the action.
     *
     * @deprecated
     */
    @Deprecated
    protected void doAction(ActionEvent event) {
        actionPerformed(event);
    }

    /**
     * Deprecated, does nothing. The TagManager methods to create, update or
     * delete tags now notify the case that there is a tag change. The case then
     * publishes an event that triggers a refresh of the tags sub-tree in the
     * tree view.
     *
     * @deprecated
     */
    @Deprecated
    protected void refreshDirectoryTree() {
    }

}
