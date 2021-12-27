/*
 * Autopsy Forensic Browser
 *
 * Copyright 2012-2021 Basis Technology Corp.
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
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.utils.IconsUtil;
import org.sleuthkit.autopsy.datamodel.DataArtifactItem;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactRowDTO;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactTableSearchResultsDTO;
import org.sleuthkit.datamodel.BlackboardArtifactTag;
import org.sleuthkit.autopsy.mainui.datamodel.DataArtifactTableSearchResultsDTO.CommAccoutTableSearchResultsDTO;
import org.sleuthkit.datamodel.DataArtifact;
import org.sleuthkit.datamodel.Tag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * node to display a data artifact.
 */
public class DataArtifactNode extends ArtifactNode<DataArtifact, DataArtifactRowDTO> {

    private static final Logger logger = Logger.getLogger(DataArtifactNode.class.getName());

    private static Lookup createLookup(DataArtifactRowDTO row) {
        DataArtifactItem artifactItem = new DataArtifactItem(row.getDataArtifact(), row.getSrcContent());
        if (row.getSrcContent() == null) {
            return Lookups.fixed(row.getDataArtifact(), artifactItem);
        } else {
            return Lookups.fixed(row.getDataArtifact(), artifactItem, row.getSrcContent());
        }
    }

    public DataArtifactNode(DataArtifactTableSearchResultsDTO tableData, DataArtifactRowDTO artifactRow) {
        this(tableData, artifactRow, getIconFilePath(tableData));
    }

    public DataArtifactNode(DataArtifactTableSearchResultsDTO tableData, DataArtifactRowDTO artifactRow, String iconPath) {
        super(tableData, artifactRow, tableData.getColumns(), createLookup(artifactRow), iconPath);
    }

    @Override
    public Optional<List<Tag>> getAllTagsFromDatabase() {
        try {
            List<BlackboardArtifactTag> artifactTags = ContentNodeUtil.getArtifactTagsFromDatabase(getRowDTO().getArtifact());
            if (!artifactTags.isEmpty()) {
                List<Tag> tags = new ArrayList<>();
                tags.addAll(artifactTags);
                return Optional.of(tags);
            }

        } catch (TskCoreException | NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Failed to get content tags from database for Artifact id=" + getRowDTO().getArtifact().getId(), ex);
        }
        return Optional.empty();
    }
    
    private static String getIconFilePath(DataArtifactTableSearchResultsDTO tableData) {
        if(!(tableData instanceof CommAccoutTableSearchResultsDTO)) {
            return IconsUtil.getIconFilePath(tableData.getArtifactType().getTypeID());
        }
        
        return IconsUtil.getIconFilePath(((CommAccoutTableSearchResultsDTO)tableData).getAccountType());  
    }
}
