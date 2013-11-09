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
package org.sleuthkit.autopsy.datamodel;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Action;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.HashSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * This class provides methods for creating sets of actions for data model objects. 
 */
class DataModelActionsFactory  {    
    static List<Action> getActions(File file) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction("View File in Directory", file));                    
        final FileNode fileNode = new FileNode(file);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction("View in New Window", fileNode));
        actions.add(new ExternalViewerAction("Open in External Viewer", fileNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(new HashSearchAction("Search for files with the same MD5 hash", fileNode));
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        actions.add(AddBlackboardArtifactTagAction.getInstance());
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }        

    static List<Action> getActions(LayoutFile file) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction("View File in Directory", file));                    
        LayoutFileNode layoutFileNode = new LayoutFileNode(file);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction("View in New Window", layoutFileNode));
        actions.add(new ExternalViewerAction("Open in External Viewer", layoutFileNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());//
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        actions.add(AddBlackboardArtifactTagAction.getInstance());
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }        
    
    static List<Action> getActions(Directory directory) {
        List<Action> actions = new ArrayList<>();        
        actions.add(new ViewContextAction("View File in Directory", directory));                    
        DirectoryNode directoryNode = new DirectoryNode(directory);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction("View in New Window", directoryNode));
        actions.add(new ExternalViewerAction("Open in External Viewer", directoryNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        actions.add(AddBlackboardArtifactTagAction.getInstance());
        actions.addAll(ContextMenuExtensionPoint.getActions()); // RJCTODO: Separator should not be added by provider
        return actions;
    }        
    
    static List<Action> getActions(VirtualDirectory directory) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction("View File in Directory", directory));                    
        VirtualDirectoryNode directoryNode = new VirtualDirectoryNode(directory);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction("View in New Window", directoryNode));
        actions.add(new ExternalViewerAction("Open in External Viewer", directoryNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        actions.add(AddBlackboardArtifactTagAction.getInstance());
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }        
        
    static List<Action> getActions(LocalFile file) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction("View File in Directory", file));                    
        final LocalFileNode localFileNode = new LocalFileNode(file);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction("View in New Window", localFileNode));
        actions.add(new ExternalViewerAction("Open in External Viewer", localFileNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        actions.add(AddBlackboardArtifactTagAction.getInstance());
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }        
        
    static List<Action> getActions(DerivedFile file) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction("View File in Directory", file));                    
        final LocalFileNode localFileNode = new LocalFileNode(file);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction("View in New Window", localFileNode));
        actions.add(new ExternalViewerAction("Open in External Viewer", localFileNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        actions.add(AddBlackboardArtifactTagAction.getInstance());
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }        
        
    static List<Action> getActions(AbstractFile file) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction("View File in Directory", file));                    
        // RJCTODO
        return actions;
    }                
}