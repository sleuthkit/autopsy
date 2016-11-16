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

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.HashSearchAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.VirtualDirectory;

/**
 * This class provides methods for creating sets of actions for data model
 * objects.
 */
// TODO: All of the methods below that deal with classes derived from AbstractFile are the same except for the creation of wrapper nodes to pass to actions.
//   1. Do the types of the wrapper nodes really need to vary? If not, it would mean a single 
//   static List<Action> getActions(AbstrctFile file, boolean isArtifactSource)
//   method could be implemented. If the different nodes are necessary, is it merely because of some misuse of the Visitor pattern somewhere?
//   2. All of this would be much improved by not constructing nodes with actions, but this might be necessary with pushing of nodes rather than use of lookups to 
//   handle selections.
public class DataModelActionsFactory {

    public static final String VIEW_SOURCE_FILE_IN_DIR = NbBundle
            .getMessage(DataModelActionsFactory.class, "DataModelActionsFactory.srcFileInDir.text");
    public static final String VIEW_FILE_IN_DIR = NbBundle
            .getMessage(DataModelActionsFactory.class, "DataModelActionsFactory.fileInDir.text");
    public static final String VIEW_IN_NEW_WINDOW = NbBundle
            .getMessage(DataModelActionsFactory.class, "DataModelActionsFactory.viewNewWin.text");
    public static final String OPEN_IN_EXTERNAL_VIEWER = NbBundle
            .getMessage(DataModelActionsFactory.class, "DataModelActionsFactory.openExtViewer.text");
    public static final String SEARCH_FOR_FILES_SAME_MD5 = NbBundle
            .getMessage(DataModelActionsFactory.class, "DataModelActionsFactory.srfFileSameMD5.text");

    public static List<Action> getActions(File file, boolean isArtifactSource) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), file));
        final FileNode fileNode = new FileNode(file);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, fileNode));
        actions.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, fileNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(new HashSearchAction(SEARCH_FOR_FILES_SAME_MD5, fileNode));
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actions.add(AddBlackboardArtifactTagAction.getInstance());
        }
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }
    
    public static List<Action> getActions(SlackFile slackFile, boolean isArtifactSource) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), slackFile));
        final SlackFileNode slackFileNode = new SlackFileNode(slackFile);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, slackFileNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actions.add(AddBlackboardArtifactTagAction.getInstance());
        }
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }

    public static List<Action> getActions(LayoutFile file, boolean isArtifactSource) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), file));
        LayoutFileNode layoutFileNode = new LayoutFileNode(file);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, layoutFileNode));
        actions.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, layoutFileNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());//
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actions.add(AddBlackboardArtifactTagAction.getInstance());
        }
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }

    public static List<Action> getActions(Directory directory, boolean isArtifactSource) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), directory));
        DirectoryNode directoryNode = new DirectoryNode(directory);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, directoryNode));
        actions.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, directoryNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actions.add(AddBlackboardArtifactTagAction.getInstance());
        }
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }

    public static List<Action> getActions(VirtualDirectory directory, boolean isArtifactSource) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), directory));
        VirtualDirectoryNode directoryNode = new VirtualDirectoryNode(directory);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, directoryNode));
        actions.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, directoryNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actions.add(AddBlackboardArtifactTagAction.getInstance());
        }
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }

    public static List<Action> getActions(LocalFile file, boolean isArtifactSource) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), file));
        final LocalFileNode localFileNode = new LocalFileNode(file);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, localFileNode));
        actions.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, localFileNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actions.add(AddBlackboardArtifactTagAction.getInstance());
        }
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }

    public static List<Action> getActions(DerivedFile file, boolean isArtifactSource) {
        List<Action> actions = new ArrayList<>();
        actions.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), file));
        final LocalFileNode localFileNode = new LocalFileNode(file);
        actions.add(null); // creates a menu separator
        actions.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, localFileNode));
        actions.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, localFileNode));
        actions.add(null); // creates a menu separator
        actions.add(ExtractAction.getInstance());
        actions.add(null); // creates a menu separator
        actions.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actions.add(AddBlackboardArtifactTagAction.getInstance());
        }
        actions.addAll(ContextMenuExtensionPoint.getActions());
        return actions;
    }

    public static List<Action> getActions(Content content, boolean isArtifactSource) {
        if (content instanceof File) {
            return getActions((File) content, isArtifactSource);
        } else if (content instanceof LayoutFile) {
            return getActions((LayoutFile) content, isArtifactSource);
        } else if (content instanceof Directory) {
            return getActions((Directory) content, isArtifactSource);
        } else if (content instanceof VirtualDirectory) {
            return getActions((VirtualDirectory) content, isArtifactSource);
        } else if (content instanceof LocalFile) {
            return getActions((LocalFile) content, isArtifactSource);
        } else if (content instanceof DerivedFile) {
            return getActions((DerivedFile) content, isArtifactSource);
        } else if (content instanceof SlackFile) {
            return getActions((SlackFile) content, isArtifactSource);
        } else {
            return new ArrayList<>();
        }
    }
}
