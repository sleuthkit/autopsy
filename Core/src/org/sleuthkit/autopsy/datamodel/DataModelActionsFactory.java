/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import javax.swing.Action;

import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.actions.ReplaceBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.ReplaceContentTagAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.datamodel.OsAccounts.OsAccountNode;
import org.sleuthkit.autopsy.datamodel.Reports.ReportNode;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.Report;
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

    public static List<Action> getActions(File file, boolean isArtifactSource) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), file));
        final FileNode fileNode = new FileNode(file);
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, fileNode));
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, fileNode));
        } else {
            actionsList.add(ExternalViewerShortcutAction.getInstance());
        }
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
        }
        if (selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        if (isArtifactSource) {
            final Collection<BlackboardArtifact> selectedArtifactsList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (selectedArtifactsList.size() == 1) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }
        }
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    public static List<Action> getActions(SlackFile slackFile, boolean isArtifactSource) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), slackFile));
        final SlackFileNode slackFileNode = new SlackFileNode(slackFile);
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, slackFileNode));
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
        }
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        if (isArtifactSource) {
            final Collection<BlackboardArtifact> selectedArtifactsList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (selectedArtifactsList.size() == 1) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }
        }
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    public static List<Action> getActions(LayoutFile file, boolean isArtifactSource) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), file));
        LayoutFileNode layoutFileNode = new LayoutFileNode(file);
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, layoutFileNode));
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, layoutFileNode));
        } else {
            actionsList.add(ExternalViewerShortcutAction.getInstance());
        }
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());//
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
        }
        if (selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        if (isArtifactSource) {
            final Collection<BlackboardArtifact> selectedArtifactsList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (selectedArtifactsList.size() == 1) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }
        }
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    public static List<Action> getActions(Directory directory, boolean isArtifactSource) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), directory));
        DirectoryNode directoryNode = new DirectoryNode(directory);
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, directoryNode));
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, directoryNode));
        } else {
            actionsList.add(ExternalViewerShortcutAction.getInstance());
        }
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
        }
        if (selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        if (isArtifactSource) {
            final Collection<BlackboardArtifact> selectedArtifactsList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (selectedArtifactsList.size() == 1) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }
        }
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    public static List<Action> getActions(VirtualDirectory directory, boolean isArtifactSource) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), directory));
        VirtualDirectoryNode directoryNode = new VirtualDirectoryNode(directory);
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, directoryNode));
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, directoryNode));
        } else {
            actionsList.add(ExternalViewerShortcutAction.getInstance());
        }
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
        }
        if (selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        if (isArtifactSource) {
            final Collection<BlackboardArtifact> selectedArtifactsList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (selectedArtifactsList.size() == 1) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }
        }
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    public static List<Action> getActions(LocalDirectory directory, boolean isArtifactSource) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), directory));
        LocalDirectoryNode directoryNode = new LocalDirectoryNode(directory);
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, directoryNode));
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, directoryNode));
        } else {
            actionsList.add(ExternalViewerShortcutAction.getInstance());
        }
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
        }
        if (selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        if (isArtifactSource) {
            final Collection<BlackboardArtifact> selectedArtifactsList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (selectedArtifactsList.size() == 1) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }
        }
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    public static List<Action> getActions(LocalFile file, boolean isArtifactSource) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), file));
        final LocalFileNode localFileNode = new LocalFileNode(file);
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, localFileNode));
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, localFileNode));
        } else {
            actionsList.add(ExternalViewerShortcutAction.getInstance());
        }
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
        }
        if (selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        if (isArtifactSource) {
            final Collection<BlackboardArtifact> selectedArtifactsList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (selectedArtifactsList.size() == 1) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }
        }
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    public static List<Action> getActions(DerivedFile file, boolean isArtifactSource) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), file));
        final LocalFileNode localFileNode = new LocalFileNode(file);
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, localFileNode));
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, localFileNode));
        } else {
            actionsList.add(ExternalViewerShortcutAction.getInstance());
        }
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
        }
        if (selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        if (isArtifactSource) {
            final Collection<BlackboardArtifact> selectedArtifactsList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (selectedArtifactsList.size() == 1) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }
        }
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    public static List<Action> getActions(Report report, boolean isArtifactSource) {
        List<Action> actionsList = new ArrayList<>();
        final ReportNode reportNode = new ReportNode(report);
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, reportNode));
        actionsList.add(null); // creates a menu separator
        if (isArtifactSource) {
            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
        }
        if (isArtifactSource) {
            final Collection<BlackboardArtifact> selectedArtifactsList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (selectedArtifactsList.size() == 1) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }
        }
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }

    public static List<Action> getActions(ContentTag contentTag, boolean isArtifactSource) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), contentTag.getContent()));
        final ContentTagNode tagNode = new ContentTagNode(contentTag);
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, tagNode));
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, tagNode));
        } else {
            actionsList.add(ExternalViewerShortcutAction.getInstance());
        }
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
        }
        if (selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        if (isArtifactSource) {
            final Collection<BlackboardArtifact> selectedArtifactsList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (selectedArtifactsList.size() == 1) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }
        }
        actionsList.add(DeleteContentTagAction.getInstance());
        actionsList.add(ReplaceContentTagAction.getInstance());
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }
    
    public static List<Action> getActions(BlackboardArtifactTag artifactTag, boolean isArtifactSource) {
        List<Action> actionsList = new ArrayList<>();
        actionsList.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), artifactTag.getContent()));        
        final BlackboardArtifactTagNode tagNode = new BlackboardArtifactTagNode(artifactTag);
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, tagNode));
        final Collection<AbstractFile> selectedFilesList
                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
        if (selectedFilesList.size() == 1) {
            actionsList.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, tagNode));
        } else {
            actionsList.add(ExternalViewerShortcutAction.getInstance());
        }
        actionsList.add(null); // creates a menu separator
        actionsList.add(ExtractAction.getInstance());
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.add(null); // creates a menu separator
        actionsList.add(AddContentTagAction.getInstance());
        if (isArtifactSource) {
            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
        }
        if (selectedFilesList.size() == 1) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }
        if (isArtifactSource) {
            final Collection<BlackboardArtifact> selectedArtifactsList
                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
            if (selectedArtifactsList.size() == 1) {
                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
            }
        }
        actionsList.add(DeleteBlackboardArtifactTagAction.getInstance());
        actionsList.add(ReplaceBlackboardArtifactTagAction.getInstance());
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
    }
    
    public static List<Action> getActions(OsAccount osAccount) {
        List<Action> actionsList = new ArrayList<>();
        
        OsAccountNode node = new OsAccountNode(osAccount);
        actionsList.add(null); // creates a menu separator
        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, node));
        actionsList.add(null);
        actionsList.add(ExportCSVAction.getInstance());
        actionsList.addAll(ContextMenuExtensionPoint.getActions());
        return actionsList;
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
        } else if (content instanceof Report) {
            return getActions((Report) content, isArtifactSource);
        } else if (content instanceof OsAccount) {
            return getActions((OsAccount) content, isArtifactSource);
        }else {
            return new ArrayList<>();
        }
    }
}
