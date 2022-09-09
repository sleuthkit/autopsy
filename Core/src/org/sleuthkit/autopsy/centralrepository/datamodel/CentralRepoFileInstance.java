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

import java.util.Objects;
import org.sleuthkit.datamodel.TskData;

/**
 * Global file hash instance
 */
public class CentralRepoFileInstance {

    private int instanceID;
    private int globalSetID;
    private String MD5Hash;
    private TskData.FileKnown knownStatus;
    private String comment;

    public CentralRepoFileInstance(
            int globalSetID,
            String MD5Hash,
            TskData.FileKnown knownStatus,
            String comment) throws CentralRepoException, CorrelationAttributeNormalizationException {
        this(-1, globalSetID, MD5Hash, knownStatus, comment);
    }

    public CentralRepoFileInstance(
            int instanceID,
            int globalSetID,
            String MD5Hash,
            TskData.FileKnown knownStatus,
            String comment) throws CentralRepoException, CorrelationAttributeNormalizationException {

        if(knownStatus == null){
            throw new CentralRepoException("null known status");
        }
        this.instanceID = instanceID;
        this.globalSetID = globalSetID;
        this.MD5Hash = CorrelationAttributeNormalizer.normalize(CorrelationAttributeInstance.FILES_TYPE_ID, MD5Hash);
        this.knownStatus = knownStatus;
        this.comment = comment;
    }

    @Override
    public boolean equals(Object otherInstance) {
        if (this == otherInstance) {
            return true;
        } else if (!(otherInstance instanceof CentralRepoFileInstance)) {
            return false;
        } else {
            return (this.hashCode() == otherInstance.hashCode());
        }
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 59 * hash + this.globalSetID;
        hash = 59 * hash + Objects.hashCode(this.MD5Hash);
        hash = 59 * hash + this.knownStatus.hashCode();
        return hash;
    }
    /**
     * @return the instanceID
     */
    public int getInstanceID() {
        return instanceID;
    }

    /**
     * @param instanceID the instanceID to set
     */
    public void setInstanceID(int instanceID) {
        this.instanceID = instanceID;
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
     * @return the MD5Hash
     */
    public String getMD5Hash() {
        return MD5Hash;
    }

    /**
     * @param MD5Hash the MD5Hash to set
     */
    public void setMD5Hash(String MD5Hash) throws CorrelationAttributeNormalizationException, CentralRepoException {
        this.MD5Hash = CorrelationAttributeNormalizer.normalize(CorrelationAttributeInstance.FILES_TYPE_ID, MD5Hash);
    }

    /**
     * @return the knownStatus
     */
    public TskData.FileKnown getKnownStatus() {
        return knownStatus;
    }

    /**
     * @param knownStatus the knownStatus to set
     */
    public void setKnownStatus(TskData.FileKnown knownStatus) {
        this.knownStatus = knownStatus;
    }

    /**
     * @return the comment
     */
    public String getComment() {
        return null == comment ? "" : comment;
    }

    /**
     * @param comment the comment to set
     */
    public void setComment(String comment) {
        this.comment = comment;
    }
}
