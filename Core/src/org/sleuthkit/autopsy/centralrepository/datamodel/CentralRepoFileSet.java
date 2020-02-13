/*
 * Central Repository
 *
 * Copyright 2015-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.datamodel;

import java.time.LocalDate;
import org.sleuthkit.datamodel.TskData;

/**
 * A global set in the Central Repository database
 */
public class CentralRepoFileSet {

    private int globalSetID;
    private int orgID;
    private String setName;
    private String version;
    private TskData.FileKnown fileKnownStatus;
    private boolean isReadOnly;
    private CorrelationAttributeInstance.Type type;
    private LocalDate importDate;

    public CentralRepoFileSet(
            int globalSetID,
            int orgID,
            String setName,
            String version,
            TskData.FileKnown knownStatus,
            boolean isReadOnly,
            CorrelationAttributeInstance.Type type,
            LocalDate importDate) {
        this.globalSetID = globalSetID;
        this.orgID = orgID;
        this.setName = setName;
        this.version = version;
        this.fileKnownStatus = knownStatus;
        this.isReadOnly = isReadOnly;
        this.type = type;
        this.importDate = importDate;
    }

    public CentralRepoFileSet(
            int orgID,
            String setName,
            String version,
            TskData.FileKnown knownStatus,
            boolean isReadOnly,
            CorrelationAttributeInstance.Type type,
            LocalDate importDate) {
        this(-1, orgID, setName, version, knownStatus, isReadOnly, type, importDate);
    }
    
    /**
     * Create a new EamGlobalSet object.
     * This is intended to be used when creating a new global set as the 
     * globalSetID will be unknown to start.
     * importDate will be automatically set to the current time.
     * @param orgID
     * @param setName
     * @param version
     * @param knownStatus
     * @param isReadOnly 
     * @param type
     */
    public CentralRepoFileSet(
            int orgID,
            String setName,
            String version,
            TskData.FileKnown knownStatus,
            boolean isReadOnly,
            CorrelationAttributeInstance.Type type) {
        this(-1, orgID, setName, version, knownStatus, isReadOnly, type, LocalDate.now());
    }

    /**
     * @return the globalSetID
     */
    public int getGlobalSetID() {
        return globalSetID;
    }

    /**
     * @param globalSetID the globalSetID to set
     */
    public void setGlobalSetID(int globalSetID) {
        this.globalSetID = globalSetID;
    }

    /**
     * @return the orgID
     */
    public int getOrgID() {
        return orgID;
    }

    /**
     * @param orgID the orgID to set
     */
    public void setOrgID(int orgID) {
        this.orgID = orgID;
    }

    /**
     * @return the setName
     */
    public String getSetName() {
        return setName;
    }

    /**
     * @param setName the setName to set
     */
    public void setSetName(String setName) {
        this.setName = setName;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(String version) {
        this.version = version;
    }
    
    /**
     * @return whether it is read only
     */
    public boolean isReadOnly() {
        return isReadOnly;
    }

    /**
     * @param isReadOnly
     */
    public void setReadOnly(boolean isReadOnly) {
        this.isReadOnly = isReadOnly;
    }
    
    /**
     * @return the known status
     */
    public TskData.FileKnown getFileKnownStatus() {
        return fileKnownStatus;
    }

    /**
     * @param fileKnownStatus the known status to set
     */
    public void setFileKnownStatus(TskData.FileKnown fileKnownStatus) {
        this.fileKnownStatus = fileKnownStatus;
    }
    
    /**
     * Get the type of reference set
     * 
     * @return the type (files, phone numbers, etc)
     */
    public CorrelationAttributeInstance.Type getType() {
        return type;
    }
    
    /**
     * Sets the type of reference set
     * 
     * @param type 
     */
    void setType(CorrelationAttributeInstance.Type type) {
        this.type = type;
    }

    /**
     * @return the importDate
     */
    public LocalDate getImportDate() {
        return importDate;
    }

    /**
     * @param importDate the importDate to set
     */
    public void setImportDate(LocalDate importDate) {
        this.importDate = importDate;
    }
}
