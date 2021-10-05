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
package org.sleuthkit.autopsy.commonpropertiessearch;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
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
    private final NODE_TYPE nodeType;
    private CorrelationAttributeInstance currentAttribute;
    private final CorrelationAttributeInstance.Type correlationType;

    CentralRepoCommonAttributeInstance(Integer attrInstId, CorrelationAttributeInstance.Type correlationType, NODE_TYPE nodeType) {
        super();
        this.crFileId = attrInstId;
        this.correlationType = correlationType;
        this.nodeType = nodeType;
    }

    @Override
    public CorrelationAttributeInstance.Type getCorrelationAttributeInstanceType() {
        return this.correlationType;
    }

    void setCurrentAttributeInst(CorrelationAttributeInstance attribute) {
        this.currentAttribute = attribute;
    }

    @Override
    AbstractFile getAbstractFile() {
        if (this.abstractFile != null) {
            return this.abstractFile;
        }

        Case currentCase;
        if (this.currentAttribute != null) {

            final CorrelationAttributeInstance currentAttributeInstance = this.currentAttribute;

            try {
                String currentFullPath = currentAttributeInstance.getFilePath();
                currentCase = Case.getCurrentCaseThrows();

                // Only attempt to make the abstract file if the attribute is from the current case
                if (currentCase.getName().equals(currentAttributeInstance.getCorrelationCase().getCaseUUID())) {
                    SleuthkitCase tskDb = currentCase.getSleuthkitCase();

                    // Find the correct data source
                    Optional<DataSource> dataSource = tskDb.getDataSources().stream()
                            .filter(p -> p.getId() == currentAttribute.getCorrelationDataSource().getDataSourceObjectID())
                            .findFirst();
                    if (!dataSource.isPresent()) {
                        LOGGER.log(Level.WARNING, String.format("Unable to find data source with device ID %s in the current case", currentAttribute.getCorrelationDataSource().getDeviceID()));
                        return null;
                    }

                    // First try to find the file in the current case using the file object id
                    // we get from the CR (if available).
                    Long fileId = currentAttribute.getFileObjectId();
                    if (fileId != null && fileId != 0) {
                        AbstractFile file = tskDb.getAbstractFileById(fileId);
                        if (file == null) {
                            LOGGER.log(Level.WARNING, String.format("Failed to find file with id %s in current case. Will attempt to find file based on path.", fileId));
                        } else {
                            this.abstractFile = file;
                        }
                    }

                    if (this.abstractFile == null) {
                        
                        if (currentFullPath == null || currentFullPath.isEmpty()) {
                            return null;
                        }
                        
                        // We failed to find the file using the file id so now we
                        // will try using the file name, parent path and data source id.
                        File fileFromPath = new File(currentFullPath);
                        String fileName = fileFromPath.getName();
                        fileName = SleuthkitCase.escapeSingleQuotes(fileName);

                        // Create the parent path. Make sure not to add a separator if there is already one there.
                        String parentPath = fileFromPath.getParent();
                        if (parentPath == null) {
                            return null;
                        }
                        if (!parentPath.endsWith(File.separator)) {
                            parentPath += File.separator;
                        }
                        parentPath = parentPath.replace("\\", "/");
                        parentPath = SleuthkitCase.escapeSingleQuotes(parentPath);
                        final String whereClause = String.format("lower(name) = '%s' AND lower(parent_path) = '%s' AND data_source_obj_id = %s", fileName, parentPath, dataSource.get().getId());
                        List<AbstractFile> potentialAbstractFiles = tskDb.findAllFilesWhere(whereClause);

                        if (potentialAbstractFiles.isEmpty()) {
                            LOGGER.log(Level.SEVERE, String.format("Unable to find AbstractFile for record with filePath: %s.", new Object[]{currentAttributeInstance.getFilePath()}));
                        } else if (potentialAbstractFiles.size() > 1) {
                            LOGGER.log(Level.WARNING, String.format("Unable to find an exact match for AbstractFile for record with filePath: %s.  May have returned the wrong file.", new Object[]{currentFullPath}));
                            this.abstractFile = potentialAbstractFiles.get(0);
                        } else {
                            this.abstractFile = potentialAbstractFiles.get(0);
                        }
                    }
                }
            } catch (TskCoreException | NoCurrentCaseException ex) {
                LOGGER.log(Level.SEVERE, String.format("Unable to find AbstractFile for record with filePath: %s.  Node not created.", new Object[]{currentAttributeInstance.getFilePath()}), ex);
            }
        }

        return this.abstractFile;
    }

    @Override
    public DisplayableItemNode[] generateNodes() {
        List<DisplayableItemNode> attrInstNodeList = new ArrayList<>(0);
        String currCaseDbName = Case.getCurrentCase().getDisplayName();
        try {
            DisplayableItemNode generatedInstNode = AbstractCommonAttributeInstance.createNode(currentAttribute, this.getAbstractFile(), currCaseDbName, nodeType);
            attrInstNodeList.add(generatedInstNode);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, String.format("Unable to get DataSource for record with md5: %s.  Node not created.", new Object[]{currentAttribute.getCorrelationValue()}), ex);
        }

        return attrInstNodeList.toArray(new DisplayableItemNode[attrInstNodeList.size()]);
    }
}
