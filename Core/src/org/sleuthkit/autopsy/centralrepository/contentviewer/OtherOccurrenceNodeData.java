/*
 * Central Repository
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
package org.sleuthkit.autopsy.centralrepository.contentviewer;

import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CorrelationAttributeInstance;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbException;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;
import org.sleuthkit.datamodel.TskDataException;

class OtherOccurrenceNodeData {
    
    private String caseName;
    private String deviceID;
    private String dataSourceName;
    private final String filePath;
    private final String type;
    private final String value;
    private TskData.FileKnown known;
    private final String comment;
    
    OtherOccurrenceNodeData(CorrelationAttributeInstance instance, String type, String value) {
        caseName = instance.getCorrelationCase().getDisplayName();
        deviceID = instance.getCorrelationDataSource().getDeviceID();
        dataSourceName = instance.getCorrelationDataSource().getName();
        filePath = instance.getFilePath();
        this.type = type;
        this.value = value;
        known = instance.getKnownStatus();
        comment = instance.getComment();
    }
    
    OtherOccurrenceNodeData(AbstractFile newFile, Case autopsyCase) throws EamDbException {
        caseName = autopsyCase.getDisplayName();
        try {
            DataSource dataSource = autopsyCase.getSleuthkitCase().getDataSource(newFile.getDataSource().getId());
            deviceID = dataSource.getDeviceId();
            dataSourceName = dataSource.getName();
        } catch (TskDataException | TskCoreException ex) {
            throw new EamDbException("Error loading data source for abstract file ID " + newFile.getId());
        }
        
        filePath = newFile.getParentPath() + newFile.getName();
        type = "Files";
        value = newFile.getMd5Hash();
        known = newFile.getKnown();
        comment = "";
    }
    
    void updateKnown(TskData.FileKnown newKnownStatus) {
        known = newKnownStatus;
    }
    
    String getCaseName() {
        return caseName;
    }
    
    String getDeviceID() {
        return deviceID;
    }
    
    String getDataSourceName() {
        return dataSourceName;
    }
    
    String getFilePath() {
        return filePath;
    }
    
    String getType() {
        return type;
    }
    
    String getValue() {
        return value;
    }
    
    TskData.FileKnown getKnown() {
        return known;
    }
    
    String getComment() {
        return comment;
    }
}
