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
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.autopsy.mainui.datamodel.ContentTagsRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.datamodel.ContentTag;

/**
 * A node representing a ContentTag.
 */
public final class ContentTagNode extends AbstractNode {

    private static final String CONTENT_ICON_PATH = "org/sleuthkit/autopsy/images/blue-tag-icon-16.png"; //NON-NLS

    private final ContentTagsRowDTO rowData;
    private final List<ColumnKey> columns;

    /**
     * Construct a new node.
     *
     * @param results Search results.
     * @param rowData Row data.
     */
    public ContentTagNode(SearchResultsDTO results, ContentTagsRowDTO rowData) {
        super(Children.LEAF, createLookup(rowData.getTag()));
        this.rowData = rowData;
        this.columns = results.getColumns();
        setDisplayName(results.getDisplayName());
        setName(results.getDisplayName());
        setIconBaseWithExtension(CONTENT_ICON_PATH);
    }

    @Override
    protected Sheet createSheet() {
        return ContentNodeUtil.setSheet(super.createSheet(), columns, rowData.getCellValues());
    }

    /**
     * Create the Lookup based on the tag type.
     *
     * @param tag The node tag.
     *
     * @return The lookup for the tag.
     */
    private static Lookup createLookup(ContentTag tag) {
        return Lookups.fixed(tag, tag.getContent());
    }

// Not adding support for actions at this time, but am deleting the original node
// classes in dataModel.  This is the action code from the original ContentTagNode    
//    public Action[] getActions(boolean context) {
//        List<Action> actions = new ArrayList<>();
//        
//
//        AbstractFile file = getLookup().lookup(AbstractFile.class);
//        if (file != null) {
//            actions.add(ViewFileInTimelineAction.createViewFileAction(file));
//        }
//
//        actions.addAll(DataModelActionsFactory.getActions(tag, false));
//        actions.add(null);
//        actions.addAll(Arrays.asList(super.getActions(context))); 
//        return actions.toArray(new Action[actions.size()]);
//    }
//    From DataModelActionsFactory
//        public static List<Action> getActions(ContentTag contentTag, boolean isArtifactSource) {
//        List<Action> actionsList = new ArrayList<>();
//        actionsList.add(new ViewContextAction((isArtifactSource ? VIEW_SOURCE_FILE_IN_DIR : VIEW_FILE_IN_DIR), contentTag.getContent()));
//        final ContentTagNode tagNode = new ContentTagNode(contentTag);
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
//        actionsList.add(DeleteContentTagAction.getInstance());
//        actionsList.add(ReplaceContentTagAction.getInstance());
//        actionsList.addAll(ContextMenuExtensionPoint.getActions());
//        return actionsList;
//    }
}
