/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2013 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.actions;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.scene.control.Menu;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.FileIDSelectionModel;
import org.sleuthkit.autopsy.imagegallery.FileUpdateEvent;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.TagName;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Instances of this Action allow users to apply tags to content.
 *
 * //TODO: since we are not using actionsGlobalContext anymore and this has
 * diverged from autopsy action, make this extend from controlsfx Action
 */
public class AddDrawableTagAction extends AddTagAction {
    
    private static final Logger LOGGER = Logger.getLogger(AddDrawableTagAction.class.getName());

    // This class is a singleton to support multi-selection of nodes, since
    // org.openide.nodes.NodeOp.findActions(Node[] nodes) will only pick up an Action if every 
    // node in the array returns a reference to the same action object from Node.getActions(boolean).    
    private static AddDrawableTagAction instance;
    
    public static synchronized AddDrawableTagAction getInstance() {
        if (null == instance) {
            instance = new AddDrawableTagAction();
        }
        return instance;
    }
    
    private AddDrawableTagAction() {
    }
    
    public Menu getPopupMenu() {
        return new TagMenu();
    }
    
    @Override
    protected String getActionDisplayName() {
        return Utilities.actionsGlobalContext().lookupAll(AbstractFile.class).size() > 1 ? "Tag Files" : "Tag File";
    }
    
    @Override
    public void addTag(TagName tagName, String comment) {
        Set<Long> selectedFiles = new HashSet<>(FileIDSelectionModel.getInstance().getSelected());
        
        new SwingWorker<Void, Void>() {
            
            @Override
            protected Void doInBackground() throws Exception {
                for (Long fileID : selectedFiles) {
                    try {
                        DrawableFile<?> file = ImageGalleryController.getDefault().getFileFromId(fileID);
                        LOGGER.log(Level.INFO, "tagging {0} with {1} and comment {2}", new Object[]{file.getName(), tagName.getDisplayName(), comment});
                        Case.getCurrentCase().getServices().getTagsManager().addContentTag(file, tagName, comment);
                    } catch (IllegalStateException ex) {
                        LOGGER.log(Level.SEVERE, "Case was closed out from underneath Updatefile task", ex);
                    } catch (TskCoreException ex) {
                        LOGGER.log(Level.SEVERE, "Error tagging result", ex);
                        JOptionPane.showMessageDialog(null, "Unable to tag " + fileID + ".", "Tagging Error", JOptionPane.ERROR_MESSAGE);
                    }

                    //make sure rest of ui  hears category change.
                    ImageGalleryController.getDefault().getGroupManager().handleFileUpdate(FileUpdateEvent.newUpdateEvent(Collections.singleton(fileID), DrawableAttribute.TAGS));
                  
                }
                
                refreshDirectoryTree();
                return null;
            }
            
            @Override
            protected void done() {
                super.done();
                try {
                    get();
                } catch (InterruptedException | ExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "unexpected exception while tagging files", ex);
                }
            }
            
        }.execute();
    }
}
