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

import java.text.MessageFormat;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.datamodel.AnalysisResultItem;
import org.sleuthkit.autopsy.datamodel.BlackboardArtifactItem;
import org.sleuthkit.autopsy.datamodel.DataArtifactItem;
import org.sleuthkit.autopsy.mainui.datamodel.BlackboardArtifactTagsRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.ColumnKey;
import org.sleuthkit.autopsy.mainui.datamodel.SearchResultsDTO;
import org.sleuthkit.autopsy.timeline.actions.ViewArtifactInTimelineAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.AnalysisResult;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A node representing a BlackboardArtifactTag.
 */
public final class BlackboardArtifactTagNode extends BaseNode<SearchResultsDTO, BlackboardArtifactTagsRowDTO> {

    private static final String ICON_PATH = "org/sleuthkit/autopsy/images/green-tag-icon-16.png"; //NON-NLS
    private final BlackboardArtifactTagsRowDTO rowData;
    private final List<ColumnKey> columns;

    private static final Logger logger = Logger.getLogger(BlackboardArtifactTagNode.class.getName());

    public BlackboardArtifactTagNode(SearchResultsDTO results, BlackboardArtifactTagsRowDTO rowData) {
        super(Children.LEAF, createLookup(rowData.getTag()), results, rowData);
        this.rowData = rowData;
        this.columns = results.getColumns();
        setDisplayName(rowData.getDisplayName());
        setShortDescription(rowData.getDisplayName());
        setName(Long.toString(rowData.getId()));
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

    @Override
    public Optional<Content> getSourceContent() {
        return Optional.ofNullable(rowData.getTag().getContent());
    }

    @Override
    public Optional<BlackboardArtifact> getArtifact() {
        return Optional.ofNullable(rowData.getTag().getArtifact());
    }

    @Override
    public boolean supportsViewInTimeline() {
        BlackboardArtifact artifact = rowData.getTag().getArtifact();
        if (artifact != null) {
            try {
                return ViewArtifactInTimelineAction.hasSupportedTimeStamp(artifact);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, MessageFormat.format("Error getting arttribute(s) from blackboard artifact{0}.", artifact.getArtifactID()), ex); //NON-NLS
            }
        }

        return false;
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
    public boolean supportsArtifactTagAction() {
        return true;
    }

    @Override
    public boolean supportsReplaceTagAction() {
        return true;
    }

    @Override
    public boolean supportsContentTagAction() {
        return rowData.getTag().getContent() instanceof AbstractFile;
    }
    
    @Override
    public boolean supportsResultArtifactAction() {
        return true;
    }
}
