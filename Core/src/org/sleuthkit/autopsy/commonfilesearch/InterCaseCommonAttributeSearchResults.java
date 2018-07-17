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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttribute;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Generates a DisplayableItmeNode using a CentralRepositoryFile.
 */
final public class InterCaseCommonAttributeSearchResults extends CommonAttributeInstanceNodeGenerator {
    
    private static final Logger LOGGER = Logger.getLogger(InterCaseCommonAttributeSearchResults.class.getName());
    private final Integer crFileId;
    private CorrelationAttributeInstance currentAttributeInst;
    private String currentFullPath;
    
    InterCaseCommonAttributeSearchResults(Integer attrInstId, Map<Long, AbstractFile> cachedFiles) {
        super(cachedFiles);
        this.crFileId = attrInstId;
    }
    
    
    @Override
    protected AbstractFile loadFileFromSleuthkitCase() {

        Case currentCase;
        this.currentFullPath = this.currentAttributeInst.getFilePath();
            
        try {
            currentCase = Case.getCurrentCaseThrows();

            SleuthkitCase tskDb = currentCase.getSleuthkitCase();
            String[] splitPath = this.currentFullPath.split("/");
            String fileName = splitPath[splitPath.length -1];
            AbstractFile abstractFile = tskDb.findAllFilesWhere(String.format("lower(name) = '%s'", fileName)).get(0); // TODO workaround where we don't need AbstractFile?

            return abstractFile;

        } catch (TskCoreException | NoCurrentCaseException ex) {
            LOGGER.log(Level.SEVERE, String.format("Unable to find AbstractFile for record with filePath: %s.  Node not created.", new Object[]{this.currentFullPath}), ex);
            return null;
        }
    }
    
    @Override
    public DisplayableItemNode[] generateNodes() {
        InterCaseSearchResultsProcessor eamDbAttrInst = new InterCaseSearchResultsProcessor();
        CorrelationAttribute corrAttr = eamDbAttrInst.findSingleCorrelationAttribute(crFileId);
        List<DisplayableItemNode> attrInstNodeList = new ArrayList<>(0);
        
        for (CorrelationAttributeInstance attrInst : corrAttr.getInstances()) {
            currentAttributeInst = attrInst;
            
            DisplayableItemNode generatedInstNode = new InterCaseCommonAttributeInstanceNode(currentAttributeInst, this.lookupOrCreateAbstractFile());
            if (generatedInstNode != null) {
                attrInstNodeList.add(generatedInstNode);
            }
        }
        return attrInstNodeList.toArray(new DisplayableItemNode[attrInstNodeList.size()]);
    }
}
