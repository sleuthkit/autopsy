/*
 * 
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
package org.sleuthkit.autopsy.commonfilesearch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Represents that a row in the CR was found in multiple cases.
 *
 * Generates a DisplayableItmeNode using a CentralRepositoryFile.
 */
final public class CentralRepoCommonAttributeInstance extends AbstractCommonAttributeInstance {

    private static final Logger LOGGER = Logger.getLogger(CentralRepoCommonAttributeInstance.class.getName());
    private final Integer crFileId;
    private CorrelationAttribute currentAttribute;

    CentralRepoCommonAttributeInstance(Integer attrInstId) {
        super();
        this.crFileId = attrInstId;
    }

    void setCurrentAttributeInst(CorrelationAttribute attribute) {
        this.currentAttribute = attribute;
    }

    @Override
    AbstractFile getAbstractFile() {

        Case currentCase;
        if (this.currentAttribute != null) {
            String currentFullPath = this.currentAttribute.getInstances().get(0).getFilePath();
            try {
                currentCase = Case.getCurrentCaseThrows();

                SleuthkitCase tskDb = currentCase.getSleuthkitCase();

                File fileFromPath = new File(currentFullPath);
                String fileName = fileFromPath.getName();
                String parentPath = fileFromPath.getParent() + File.separator;
                List<AbstractFile> potentialAbstractFiles = tskDb.findAllFilesWhere(String.format("lower(name) = '%s' AND md5 = '%s'", fileName, currentAttribute.getCorrelationValue()));
                AbstractFile finalAbstractFile = null;
                for (AbstractFile aFile : potentialAbstractFiles) {
                    // If a direct match exists, return that and we'll create a CaseDb instance node from it
                    if (aFile.getParentPath().equalsIgnoreCase(parentPath)) {
                        finalAbstractFile = aFile;
                        break;
                    }
                }
                // If not direct match exists, return first md5 match, only used as a backing file for CR instance node.
                if (finalAbstractFile == null) {
                    if(potentialAbstractFiles.isEmpty()){
                        return null;
                    } else {
                        finalAbstractFile = potentialAbstractFiles.get(0);
                    }
                }

                return finalAbstractFile;

            } catch (TskCoreException | NoCurrentCaseException ex) {
                LOGGER.log(Level.SEVERE, String.format("Unable to find AbstractFile for record with filePath: %s.  Node not created.", new Object[]{currentFullPath}), ex);
                return null;
            }
        }
        return null;
    }

    @Override
    public DisplayableItemNode[] generateNodes() {

        // @@@ We should be doing more of this work in teh generateKeys method. We want to do as little as possible in generateNodes
        InterCaseSearchResultsProcessor eamDbAttrInst = new InterCaseSearchResultsProcessor();
        CorrelationAttribute corrAttr = eamDbAttrInst.findSingleCorrelationAttribute(crFileId);
        List<DisplayableItemNode> attrInstNodeList = new ArrayList<>(0);
        String currCaseDbName = Case.getCurrentCase().getDisplayName();

        try {
            this.setCurrentAttributeInst(corrAttr);

            AbstractFile abstractFileForAttributeInstance = this.getAbstractFile();
            DisplayableItemNode generatedInstNode = AbstractCommonAttributeInstance.createInstance(corrAttr, abstractFileForAttributeInstance, currCaseDbName);
            attrInstNodeList.add(generatedInstNode);

        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, String.format("Unable to get DataSource for record with md5: %s.  Node not created.", new Object[]{corrAttr.getCorrelationValue()}), ex);
        }

        return attrInstNodeList.toArray(new DisplayableItemNode[attrInstNodeList.size()]);
    }
}
