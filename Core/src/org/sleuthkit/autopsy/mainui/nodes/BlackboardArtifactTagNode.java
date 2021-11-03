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

import java.util.List;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.AnalysisResultItem;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactItem;
import org.sleuthkit.autopsy.datamodel.DataArtifactItem;
import org.sleuthkit.autopsy.mainui.datamodel.BlackboardArtifactTagsRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;

/**
 * A node representing a BlackboardArtifactTag.
 */
public final class BlackboardArtifactTagNode extends AbstractNode {

    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/green-tag-icon-16.png"; //NON-NLS
    private final BlackboardArtifactTagsRowDTO rowData;
    private final List<ColumnKey> columns;

    public BlackboardArtifactTagNode(SearchResultsDTO results, BlackboardArtifactTagsRowDTO rowData) {
        super(Children.LEAF, createLookup(rowData.getTag()));
        this.rowData = rowData;
        this.columns = results.getColumns();
        setDisplayName(results.getDisplayName());
        setName(results.getDisplayName());
        setIconBaseWithExtension(ICON_PATH);
    }

    @Override
    protected Sheet createSheet() {
        return ContentNodeUtil.setSheet(super.createSheet(), columns, rowData.getCellValues());
    }

    /**
     * Creates the lookup for a BlackboardArtifactTag.
     *
     * Note: This method comes from dataModel.BlackboardArtifactTag.
     *
     * @param tag The tag to create a lookup for
     *
     * @return The lookup.
     */
    private static Lookup createLookup(BlackboardArtifactTag tag) {
        /*
         * Make an Autopsy Data Model wrapper for the artifact.
         *
         * NOTE: The creation of an Autopsy Data Model independent of the
         * NetBeans nodes is a work in progress. At the time this comment is
         * being written, this object is only being used to indicate the item
         * represented by this BlackboardArtifactTagNode.
         */
        Content sourceContent = tag.getContent();
        BlackboardArtifact artifact = tag.getArtifact();
        BlackboardArtifactItem<?> artifactItem;
        if (artifact instanceof AnalysisResult) {
            artifactItem = new AnalysisResultItem((AnalysisResult) artifact, sourceContent);
        } else {
            artifactItem = new DataArtifactItem((DataArtifact) artifact, sourceContent);
        }
        return Lookups.fixed(tag, artifactItem, artifact, sourceContent);
    }

    // Actions are not a part of the first story, however I am deleting the original
    // node which will make finding this info a little more difficult.
//   public Action[] getActions(boolean context) {
//        List<Action> actions = new ArrayList<>();
//        BlackboardArtifact artifact = getLookup().lookup(BlackboardArtifact.class);
//        //if this artifact has a time stamp add the action to view it in the timeline
//        try {
//            if (ViewArtifactInTimelineAction.hasSupportedTimeStamp(artifact)) {
//                actions.add(new ViewArtifactInTimelineAction(artifact));
//            }
//        } catch (TskCoreException ex) {
//            LOGGER.log(Level.SEVERE, MessageFormat.format("Error getting arttribute(s) from blackboard artifact{0}.", artifact.getArtifactID()), ex); //NON-NLS
//        }
//        
//        actions.add(new ViewTaggedArtifactAction(Bundle.BlackboardArtifactTagNode_viewSourceArtifact_text(), artifact));
//        actions.add(null);
//        // if the artifact links to another file, add an action to go to that file
//        try {
//            AbstractFile c = findLinked(artifact);
//            if (c != null) {
//                actions.add(ViewFileInTimelineAction.createViewFileAction(c));
//            }
//        } catch (TskCoreException ex) {
//            LOGGER.log(Level.SEVERE, MessageFormat.format("Error getting linked file from blackboard artifact{0}.", artifact.getArtifactID()), ex); //NON-NLS
//        }
//        //if this artifact has associated content, add the action to view the content in the timeline
//        AbstractFile file = getLookup().lookup(AbstractFile.class);
//        if (null != file) {
//            actions.add(ViewFileInTimelineAction.createViewSourceFileAction(file));
//        }
//        actions.addAll(DataModelActionsFactory.getActions(tag, true));
//        actions.add(null);
//        actions.addAll(Arrays.asList(super.getActions(context)));
//        return actions.toArray(new Action[0]);
//    }
//    
    // From DataModelActionsFactory
//    public static List<Action> getActions(BlackboardArtifactTag artifactTag, boolean isArtifactSource) {
//        List<Action> actionsList = new ArrayList<>();
//        actionsList.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), artifactTag.getContent()));        
//        final BlackboardArtifactTagNode tagNode = new BlackboardArtifactTagNode(artifactTag);
//        actionsList.add(null); // creates a menu separator
//        actionsList.add(new NewWindowViewAction(VIEW_IN_NEW_WINDOW, tagNode));
//        final Collection<AbstractFile> selectedFilesList
//                = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(AbstractFile.class));
//        if (selectedFilesList.size() == 1) {
//            actionsList.add(new ExternalViewerAction(OPEN_IN_EXTERNAL_VIEWER, tagNode));
//        } else {
//            actionsList.add(ExternalViewerShortcutAction.getInstance());
//        }
//        actionsList.add(null); // creates a menu separator
//        actionsList.add(ExtractAction.getInstance());
//        actionsList.add(ExportCSVAction.getInstance());
//        actionsList.add(null); // creates a menu separator
//        actionsList.add(AddContentTagAction.getInstance());
//        if (isArtifactSource) {
//            actionsList.add(AddBlackboardArtifactTagAction.getInstance());
//        }
//        if (selectedFilesList.size() == 1) {
//            actionsList.add(DeleteFileContentTagAction.getInstance());
//        }
//        if (isArtifactSource) {
//            final Collection<BlackboardArtifact> selectedArtifactsList
//                    = new HashSet<>(Utilities.actionsGlobalContext().lookupAll(BlackboardArtifact.class));
//            if (selectedArtifactsList.size() == 1) {
//                actionsList.add(DeleteFileBlackboardArtifactTagAction.getInstance());
//            }
//        }
//        actionsList.add(DeleteBlackboardArtifactTagAction.getInstance());
//        actionsList.add(ReplaceBlackboardArtifactTagAction.getInstance());
//        actionsList.addAll(ContextMenuExtensionPoint.getActions());
//        return actionsList;
//    }
}
