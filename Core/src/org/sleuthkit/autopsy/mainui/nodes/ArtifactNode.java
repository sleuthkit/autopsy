/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.mainui.nodes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.Action;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.util.Utilities;
import org.sleuthkit.autopsy.actions.AddBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.AddContentTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileBlackboardArtifactTagAction;
import org.sleuthkit.autopsy.actions.DeleteFileContentTagAction;
import org.sleuthkit.autopsy.actions.ViewArtifactAction;
import org.sleuthkit.autopsy.actions.ViewOsAccountAction;
import org.sleuthkit.autopsy.coreutils.ContextMenuExtensionPoint;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactItem;
import org.sleuthkit.autopsy.datamodel.DataModelActionsFactory;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.sleuthkit.autopsy.datamodel.LayoutFileNode;
import org.sleuthkit.autopsy.datamodel.LocalDirectoryNode;
import org.sleuthkit.autopsy.datamodel.LocalFileNode;
import org.sleuthkit.autopsy.datamodel.SlackFileNode;
import org.sleuthkit.autopsy.datamodel.VirtualDirectoryNode;
import org.sleuthkit.autopsy.directorytree.ExportCSVAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.directorytree.ExtractAction;
import org.sleuthkit.autopsy.directorytree.NewWindowViewAction;
import org.sleuthkit.autopsy.directorytree.ViewContextAction;
import org.sleuthkit.autopsy.mainui.datamodel.ArtifactRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.autopsy.timeline.actions.ViewArtifactInTimelineAction;
import org.sleuthkit.autopsy.timeline.actions.ViewFileInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.DerivedFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.LayoutFile;
import org.sleuthkit.datamodel.LocalDirectory;
import org.sleuthkit.datamodel.LocalFile;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.Report;
import org.sleuthkit.datamodel.SlackFile;
import org.sleuthkit.datamodel.VirtualDirectory;

public abstract class ArtifactNode<T extends BlackboardArtifact, R extends ArtifactRowDTO<T>> extends AbstractNode {

    private final R rowData;
    private final BlackboardArtifact.Type artifactType;
    private final List<ColumnKey> columns;

    ArtifactNode(R rowData, List<ColumnKey> columns, BlackboardArtifact.Type artifactType, Lookup lookup, String iconPath) {
        super(Children.LEAF, lookup);
        this.rowData = rowData;
        this.artifactType = artifactType;
        this.columns = columns;
        setupNodeDisplay(iconPath);
    }

    protected void setupNodeDisplay(String iconPath) {
        // use first cell value for display name
        String displayName = rowData.getCellValues().size() > 0
                ? rowData.getCellValues().get(0).toString()
                : "";

        setDisplayName(displayName);
        setShortDescription(displayName);
        setName(Long.toString(rowData.getId()));
        setIconBaseWithExtension(iconPath != null && iconPath.charAt(0) == '/' ? iconPath.substring(1) : iconPath);
    }

    @Override
    protected Sheet createSheet() {
        return ContentNodeUtil.setSheet(super.createSheet(), columns, rowData.getCellValues());
    }

    /**
     * Returns a list of non null actions from the given possibly null options.
     *
     * @param items The items to purge of null items.
     *
     * @return The list of non-null actions.
     */
    private List<Action> getNonNull(Action... items) {
        return Stream.of(items)
                .filter(i -> i != null)
                .collect(Collectors.toList());
    }

    @Override
    public Action[] getActions(boolean context) {
        // groupings of actions where each group will be separated by a divider
        List<List<Action>> actionsLists = new ArrayList<>();

        T artifact = rowData.getArtifact();
        Content srcContent = rowData.getSrcContent();

        // view artifact in timeline
        actionsLists.add(getNonNull(
                getTimelineArtifactAction(artifact, rowData.isTimelineSupported())
        ));

        // view associated file (TSK_PATH_ID attr) in directory and timeline
        AbstractFile associatedFile = rowData.getLinkedFile() instanceof AbstractFile
                ? (AbstractFile) rowData.getLinkedFile()
                : null;
        actionsLists.add(getAssociatedFileActions(associatedFile, this.artifactType));

        // view source content in directory and timeline
        actionsLists.add(getNonNull(
                getViewSrcContentAction(artifact, srcContent),
                getTimelineSrcContentAction(srcContent)
        ));

        // menu options for artifact with report parent
        if (srcContent instanceof Report) {
            actionsLists.add(DataModelActionsFactory.getActions(srcContent, false));
        }

        Node parentFileNode = getParentFileNode(srcContent);
        int selectedFileCount = Utilities.actionsGlobalContext().lookupAll(AbstractFile.class).size();
        int selectedArtifactCount = Utilities.actionsGlobalContext().lookupAll(BlackboardArtifactItem.class).size();

        // view source content if source content is some sort of file
        actionsLists.add(getSrcContentViewerActions(parentFileNode, selectedFileCount));

        // extract / export if source content is some sort of file
        if (parentFileNode != null) {
            actionsLists.add(Arrays.asList(ExtractAction.getInstance(), ExportCSVAction.getInstance()));
        }

        // file and result tagging
        actionsLists.add(getTagActions(parentFileNode != null, artifact, selectedFileCount, selectedArtifactCount));

        // menu extension items (i.e. add to central repository)
        actionsLists.add(ContextMenuExtensionPoint.getActions());

        // netbeans default items (i.e. properties)
        actionsLists.add(Arrays.asList(super.getActions(context)));

        return actionsLists.stream()
                // remove any empty lists
                .filter((lst) -> lst != null && !lst.isEmpty())
                // add in null between each list group
                .flatMap(lst -> Stream.concat(Stream.of((Action) null), lst.stream()))
                // skip the first null
                .skip(1)
                .toArray(sz -> new Action[sz]);
    }

    /**
     * Returns the name of the artifact based on the artifact type to be used
     * with the associated file string in a right click menu.
     *
     * @param artifactType The artifact type.
     *
     * @return The artifact type name.
     */
    @NbBundle.Messages({
        "ArtifactNode_getAssociatedTypeStr_webCache=Cached File",
        "ArtifactNode_getAssociatedTypeStr_webDownload=Downloaded File",
        "ArtifactNode_getAssociatedTypeStr_associated=Associated File",})
    private String getAssociatedTypeStr(BlackboardArtifact.Type artifactType) {
        if (BlackboardArtifact.Type.TSK_WEB_CACHE.equals(artifactType)) {
            return Bundle.ArtifactNode_getAssociatedTypeStr_webCache();
        } else if (BlackboardArtifact.Type.TSK_WEB_DOWNLOAD.equals(artifactType)) {
            return Bundle.ArtifactNode_getAssociatedTypeStr_webDownload();
        } else {
            return Bundle.ArtifactNode_getAssociatedTypeStr_associated();
        }
    }

    /**
     * Returns the name to represent the type of the content (file, data
     * artifact, os account, item).
     *
     * @param content The content.
     *
     * @return The name of the type of content.
     */
    @Messages({
        "ArtifactNode_getViewSrcContentAction_type_File=File",
        "ArtifactNode_getViewSrcContentAction_type_DataArtifact=Data Artifact",
        "ArtifactNode_getViewSrcContentAction_type_OSAccount=OS Account",
        "ArtifactNode_getViewSrcContentAction_type_unknown=Item"
    })
    private String getContentTypeStr(Content content) {
        if (content instanceof AbstractFile) {
            return Bundle.ArtifactNode_getViewSrcContentAction_type_File();
        } else if (content instanceof DataArtifact) {
            return Bundle.ArtifactNode_getViewSrcContentAction_type_DataArtifact();
        } else if (content instanceof OsAccount) {
            return Bundle.ArtifactNode_getViewSrcContentAction_type_OSAccount();
        } else {
            return Bundle.ArtifactNode_getViewSrcContentAction_type_unknown();
        }
    }

    @Messages({
        "# {0} - type",
        "ArtifactNode_getAssociatedFileActions_viewAssociatedFileAction=View {0} in Directory",
        "# {0} - type",
        "ArtifactNode_getAssociatedFileActions_viewAssociatedFileInTimelineAction=View {0} in Timeline..."
    })
    private List<Action> getAssociatedFileActions(AbstractFile associatedFile, BlackboardArtifact.Type artifactType) {
        if (associatedFile != null) {
            return Arrays.asList(
                    new ViewContextAction(
                            Bundle.ArtifactNode_getAssociatedFileActions_viewAssociatedFileAction(
                                    getAssociatedTypeStr(artifactType)),
                            associatedFile),
                    new ViewFileInTimelineAction(associatedFile,
                            Bundle.ArtifactNode_getAssociatedFileActions_viewAssociatedFileInTimelineAction(
                                    getAssociatedTypeStr(artifactType)))
            );
        } else {
            return Collections.emptyList();
        }

    }

    /**
     * Creates an action to navigate to src content in tree hierarchy.
     *
     * @param artifact The artifact.
     * @param content  The content.
     *
     * @return The action or null if no action derived.
     */
    @NbBundle.Messages({
        "# {0} - contentType",
        "ArtifactNode_getSrcContentAction_actionDisplayName=View Source {0} in Directory"
    })
    private Action getViewSrcContentAction(BlackboardArtifact artifact, Content content) {
        if (content instanceof DataArtifact) {
            return new ViewArtifactAction(
                    (BlackboardArtifact) content,
                    Bundle.ArtifactNode_getSrcContentAction_actionDisplayName(
                            getContentTypeStr(content)));
        } else if (content instanceof OsAccount) {
            return new ViewOsAccountAction(
                    (OsAccount) content,
                    Bundle.ArtifactNode_getSrcContentAction_actionDisplayName(
                            getContentTypeStr(content)));
        } else if (content instanceof AbstractFile || artifact instanceof DataArtifact) {
            return new ViewContextAction(
                    Bundle.ArtifactNode_getSrcContentAction_actionDisplayName(
                            getContentTypeStr(content)),
                    content);
        } else {
            return null;
        }
    }

    /**
     * Returns a Node representing the file content if the content is indeed
     * some sort of file. Otherwise, return null.
     *
     * @param content The content.
     *
     * @return The file node or null if not a file.
     */
    private Node getParentFileNode(Content content) {
        if (content instanceof File) {
            return new org.sleuthkit.autopsy.datamodel.FileNode((AbstractFile) content);
        } else if (content instanceof Directory) {
            return new DirectoryNode((Directory) content);
        } else if (content instanceof VirtualDirectory) {
            return new VirtualDirectoryNode((VirtualDirectory) content);
        } else if (content instanceof LocalDirectory) {
            return new LocalDirectoryNode((LocalDirectory) content);
        } else if (content instanceof LayoutFile) {
            return new LayoutFileNode((LayoutFile) content);
        } else if (content instanceof LocalFile || content instanceof DerivedFile) {
            return new LocalFileNode((AbstractFile) content);
        } else if (content instanceof SlackFile) {
            return new SlackFileNode((AbstractFile) content);
        } else {
            return null;
        }
    }

    /**
     * Returns tag actions.
     *
     * @param hasSrcFile            Whether or not the artifact has a source
     *                              file.
     * @param artifact              This artifact.
     * @param selectedFileCount     The count of selected files.
     * @param selectedArtifactCount The count of selected artifacts.
     *
     * @return The tag actions.
     */
    private List<Action> getTagActions(boolean hasSrcFile, BlackboardArtifact artifact, int selectedFileCount, int selectedArtifactCount) {
        List<Action> actionsList = new ArrayList<>();

        // don't show AddContentTagAction for data artifacts.
        if (hasSrcFile && !(artifact instanceof DataArtifact)) {
            actionsList.add(AddContentTagAction.getInstance());
        }

        actionsList.add(AddBlackboardArtifactTagAction.getInstance());

        // don't show DeleteFileContentTagAction for data artifacts.
        if (hasSrcFile && (!(artifact instanceof DataArtifact)) && (selectedFileCount == 1)) {
            actionsList.add(DeleteFileContentTagAction.getInstance());
        }

        if (selectedArtifactCount == 1) {
            actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
        }

        return actionsList;
    }

    /**
     * Returns actions to view src content in a different viewer or window.
     *
     * @param srcFileNode       The source file node or null if no source file.
     * @param selectedFileCount The number of selected files.
     *
     * @return The list of actions or an empty list.
     */
    @NbBundle.Messages({
        "ArtifactNode_getSrcContentViewerActions_viewInNewWin=View Item in New Window",
        "ArtifactNode_getSrcContentViewerActions_openInExtViewer=Open in External Viewer  Ctrl+E"
    })
    private List<Action> getSrcContentViewerActions(Node srcFileNode, int selectedFileCount) {
        List<Action> actionsList = new ArrayList<>();
        if (srcFileNode != null) {
            actionsList.add(new NewWindowViewAction(Bundle.ArtifactNode_getSrcContentViewerActions_viewInNewWin(), srcFileNode));
            if (selectedFileCount == 1) {
                actionsList.add(new ExternalViewerAction(Bundle.ArtifactNode_getSrcContentViewerActions_openInExtViewer(), srcFileNode));
            } else {
                actionsList.add(ExternalViewerShortcutAction.getInstance());
            }
        }
        return actionsList;
    }

    /**
     * If the source content of the artifact represented by this node is a file,
     * returns an action to view the file in the data source tree.
     *
     * @param srcContent The src content to navigate to in the timeline action.
     *
     * @return The src content navigation action or null.
     */
    @NbBundle.Messages({
        "# {0} - contentType",
        "ArtifactNode_getTimelineSrcContentAction_actionDisplayName=View Source {0} in Timeline... "
    })
    private Action getTimelineSrcContentAction(Content srcContent) {
        if (srcContent instanceof AbstractFile) {
            return new ViewFileInTimelineAction((AbstractFile) srcContent,
                    Bundle.ArtifactNode_getTimelineSrcContentAction_actionDisplayName(
                            getContentTypeStr(srcContent)));
        }
        return null;
    }

    /**
     * If the artifact represented by this node has a timestamp, an action to
     * view it in the timeline.
     *
     * @param art                   The artifact for timeline navigation action.
     * @param hasSupportedTimeStamp This artifact has a supported time stamp.
     *
     * @return The action or null if no action should exist.
     */
    @NbBundle.Messages({
        "ArtifactNode_getTimelineArtifactAction_displayName=View Selected Item in Timeline... "
    })
    private Action getTimelineArtifactAction(BlackboardArtifact art, boolean hasSupportedTimeStamp) {
        if (hasSupportedTimeStamp) {
            return new ViewArtifactInTimelineAction(art, Bundle.ArtifactNode_getTimelineArtifactAction_displayName());
        } else {
            return null;
        }
    }
}
