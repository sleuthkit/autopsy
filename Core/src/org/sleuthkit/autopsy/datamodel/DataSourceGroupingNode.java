/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2018 Basis Technology Corp.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Level;
import org.openide.util.lookup.Lookups;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalFilesDataSource;

/**
 * Data source grouping node - an optional grouping node in the data tree view
 *
 */
class DataSourceGroupingNode extends DisplayableItemNode {

    private static final Logger logger = Logger.getLogger(DataSourceGroupingNode.class.getName());

    /**
     * Creates a data source grouping node for the given data source.
     *
     * @param dataSource specifies the data source
     */
    DataSourceGroupingNode(DataSource dataSource) {

        super(Optional.ofNullable(createDSGroupingNodeChildren(dataSource))
                .orElse(new RootContentChildren(Arrays.asList(Collections.EMPTY_LIST))),
                Lookups.singleton(dataSource));

        if (dataSource instanceof Image) {
            Image image = (Image) dataSource;

            super.setName(image.getName());
            super.setDisplayName(image.getName());
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/image.png");
        } else if (dataSource instanceof LocalFilesDataSource) {
            LocalFilesDataSource localFilesDataSource = (LocalFilesDataSource) dataSource;

            super.setName(localFilesDataSource.getName());
            super.setDisplayName(localFilesDataSource.getName());
            this.setIconBaseWithExtension("org/sleuthkit/autopsy/images/fileset-icon-16.png");
        }

    }

    @Override
    public boolean isLeafTypeNode() {
        return false;
    }

    private static RootContentChildren createDSGroupingNodeChildren(DataSource dataSource) {

        long dsObjId = dataSource.getId();
        try {
            return new RootContentChildren(Arrays.asList(
                    new DataSources(dsObjId),
                    new Views(Case.getCurrentCaseThrows().getSleuthkitCase(), dsObjId),
                    new DataArtifacts(dsObjId),
                    new AnalysisResults(dsObjId),
                    new OsAccounts(Case.getCurrentCaseThrows().getSleuthkitCase(), dsObjId),
                    new Tags(dsObjId)
            ));

        } catch (NoCurrentCaseException ex) {
            logger.log(Level.SEVERE, "Error getting open case.", ex); //NON-NLS
            return null;
        }
    }

    @Override
    public <T> T accept(DisplayableItemNodeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public String getItemType() {
        return getClass().getName();
    }
}
