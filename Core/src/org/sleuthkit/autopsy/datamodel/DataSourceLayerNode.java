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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DataSourcesLayerChildren.SubtreeEnum;
import org.sleuthkit.autopsy.datamodel.accounts.Accounts;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.LocalFilesDataSource;


/**
 *  Datasource layer node - an optional grouping node in the data tree view
 * 
 */
public class DataSourceLayerNode extends DisplayableItemNode {

    private static final Logger logger = Logger.getLogger(DataSourceLayerNode.class.getName());

    /**
     * Creates the Datasource node for the given data source,  
     * and initializes the children nodes under it based on the subtree specified 
     * 
     * @param dataSourceLayerInfo specifies the 
     */
    DataSourceLayerNode(DataSourcesLayerChildren.DataSourceLayerInfo dataSourceLayerInfo) {

        super (Optional.ofNullable(createDSLayerNodeChildren(dataSourceLayerInfo))
                        .orElse(new RootContentChildren(Arrays.asList(Collections.EMPTY_LIST))));

        DataSource dataSource = dataSourceLayerInfo.getDataSource();
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
    
    private static RootContentChildren createDSLayerNodeChildren(DataSourcesLayerChildren.DataSourceLayerInfo dataSourceLayerInfo) {

        SubtreeEnum subtree = dataSourceLayerInfo.getSubtree();
        long dsObjId = dataSourceLayerInfo.getDataSource().getId();

        try {
            switch (subtree) {
                case VIEWS:
                    return new RootContentChildren(Arrays.asList(
                                new FileTypes(Case.getOpenCase().getSleuthkitCase(), dsObjId),
                                new DeletedContent(Case.getOpenCase().getSleuthkitCase(), dsObjId), 
                                new FileSize(Case.getOpenCase().getSleuthkitCase(), dsObjId))
                                );   
                    
                case RESULTS:   //  TBD:
                    return new  RootContentChildren(Arrays.asList(
                                    new ExtractedContent(Case.getOpenCase().getSleuthkitCase()),
                                    new KeywordHits(Case.getOpenCase().getSleuthkitCase()),
                                    new HashsetHits(Case.getOpenCase().getSleuthkitCase()),
                                    new EmailExtracted(Case.getOpenCase().getSleuthkitCase()),
                                    new InterestingHits(Case.getOpenCase().getSleuthkitCase()),
                                    new Accounts(Case.getOpenCase().getSleuthkitCase()) 
                    ));
                case TAGS:      //  TBD:
                case REPORTS:   //  TBD:
                    
                default: 
                {
                    logger.log(Level.SEVERE, "Unknown subtree type " + subtree.name()); //NON-NLS
                    return null;    
                }
            }
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
