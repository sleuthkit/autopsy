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

import java.util.Arrays;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Encapsulates data required to instantiate a <code>FileInstanceNode</code> for
 * an instance in the CaseDB
 */
final public class CaseDBCommonAttributeInstance extends AbstractCommonAttributeInstance {

    private static final Logger LOGGER = Logger.getLogger(CaseDBCommonAttributeInstance.class.getName());
    private final String value;

    /**
     * Create meta data required to find an abstract file and build a
     * FileInstanceNode.
     *
     * @param objectId       id of abstract file to find
     * @param dataSourceName name of datasource where the object is found
     * @param value          the correlation value which was found
     */
    CaseDBCommonAttributeInstance(Long abstractFileReference, String dataSource, String caseName, String value) {
        super(abstractFileReference, dataSource, caseName);
        this.value = value;
    }

    @Override
    public DisplayableItemNode[] generateNodes() {
        final CaseDBCommonAttributeInstanceNode intraCaseCommonAttributeInstanceNode = new CaseDBCommonAttributeInstanceNode(this.getAbstractFile(), this.getCaseName(), this.getDataSource(), this.value, NODE_TYPE.COUNT_NODE);
        return Arrays.asList(intraCaseCommonAttributeInstanceNode).toArray(new DisplayableItemNode[1]);
    }

    @Override
    AbstractFile getAbstractFile() {

        Case currentCase;
        try {
            currentCase = Case.getCurrentCaseThrows();

            SleuthkitCase tskDb = currentCase.getSleuthkitCase();

            return this.abstractFile = tskDb.findAllFilesWhere(String.format("obj_id in (%s)", this.getAbstractFileObjectId())).get(0);

        } catch (TskCoreException | NoCurrentCaseException ex) {
            LOGGER.log(Level.SEVERE, String.format("Unable to find AbstractFile for record with obj_id: %s.  Node not created.", new Object[]{this.getAbstractFileObjectId()}), ex);
            return null;
        }
    }

    @Override
    public CorrelationAttributeInstance.Type getCorrelationAttributeInstanceType() {
        //may be required at a later date
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
