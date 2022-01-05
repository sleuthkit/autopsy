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
import java.util.Optional;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.autopsy.mainui.datamodel.ContentTagsRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ContentTag;

/**
 * A node representing a ContentTag.
 */
public final class ContentTagNode extends BaseNode<SearchResultsDTO, ContentTagsRowDTO> {

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
        super(Children.LEAF, createLookup(rowData.getTag()), results, rowData);
        this.rowData = rowData;
        this.columns = results.getColumns();
        setDisplayName(rowData.getDisplayName());
        setName(rowData.getDisplayName());
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

    @Override
    public Optional<Long> getFileForViewInTimelineAction() {
        Content tagContent = rowData.getTag().getContent();
        if (tagContent instanceof AbstractFile) {
            return Optional.of(tagContent.getId());
        }

        return Optional.empty();
    }

    @Override
    public boolean supportsViewInTimeline() {
        return true;
    }

    @Override
    public boolean supportsAssociatedFileActions() {
        return true;
    }

    @Override
    public Optional<AbstractFile> getLinkedFile() {
        Content content = rowData.getTag().getContent();
        if (content instanceof AbstractFile) {
            return Optional.of((AbstractFile) content);
        }

        return Optional.empty();
    }

    @Override
    public boolean supportsSourceContentViewerActions() {
        return true;
    }

    @Override
    public Optional<Node> getNewWindowActionNode() {
        return Optional.of(this);
    }

    @Override
    public Optional<Node> getExternalViewerActionNode() {
        return Optional.of(this);
    }

    @Override
    public boolean supportsTableExtractActions() {
        return true;
    }

    @Override
    public boolean supportsContentTagAction() {
        return true;
    }

    @Override
    public boolean supportsReplaceTagAction() {
        return true;
    }
}
