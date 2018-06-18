/*
 * Central Repository
 *
 * Copyright 2015-2018 Basis Technology Corp.
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

import java.io.Serializable;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.datamodel.TskData;

/**
 *
 * Used to store details about a specific instance of a CorrelationAttribute.
 * Includes its data source, path, etc.
 *
 */
@Messages({
    "EamArtifactInstances.knownStatus.bad=Bad",
    "EamArtifactInstances.knownStatus.known=Known",
    "EamArtifactInstances.knownStatus.unknown=Unknown"})
public class CorrelationAttributeInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    private int ID;
    private CorrelationCase correlationCase;
    private CorrelationDataSource correlationDataSource;
    private String filePath;
    private String comment;
    private TskData.FileKnown knownStatus;

    public CorrelationAttributeInstance(
            CorrelationCase eamCase,
            CorrelationDataSource eamDataSource,
            String filePath
    ) throws EamDbException {
        this(-1, eamCase, eamDataSource, filePath, null, TskData.FileKnown.UNKNOWN);
    }


    public CorrelationAttributeInstance(
            CorrelationCase eamCase,
            CorrelationDataSource eamDataSource,
            String filePath,
            String comment,
            TskData.FileKnown knownStatus
    ) throws EamDbException {
        this(-1, eamCase, eamDataSource, filePath, comment, knownStatus);
    }

    CorrelationAttributeInstance(
            int ID,
            CorrelationCase eamCase,
            CorrelationDataSource eamDataSource,
            String filePath,
            String comment,
            TskData.FileKnown knownStatus
    ) throws EamDbException {
        if (filePath == null) {
            throw new EamDbException("file path is null");
        }

        this.ID = ID;
        this.correlationCase = eamCase;
        this.correlationDataSource = eamDataSource;
        // Lower case paths to normalize paths and improve correlation results, if this causes significant issues on case-sensitive file systems, remove
        this.filePath = filePath.toLowerCase();
        this.comment = comment;
        this.knownStatus = knownStatus;
    }

    public Boolean equals(CorrelationAttributeInstance otherInstance) {
        return ((this.getID() == otherInstance.getID())
                && (this.getCorrelationCase().equals(otherInstance.getCorrelationCase()))
                && (this.getCorrelationDataSource().equals(otherInstance.getCorrelationDataSource()))
                && (this.getFilePath().equals(otherInstance.getFilePath()))
                && (this.getKnownStatus().equals(otherInstance.getKnownStatus()))
                && (this.getComment().equals(otherInstance.getComment())));
    }

    @Override
    public String toString() {
        return this.getID()
                + this.getCorrelationCase().getCaseUUID()
                + this.getCorrelationDataSource().getDeviceID()
                + this.getFilePath()
                + this.getKnownStatus()
                + this.getComment();
    }

    /**
     * Is this a database instance?
     *
     * @return True if the instance ID is greater or equal to zero; otherwise
     *         false.
     */
    public boolean isDatabaseInstance() {
        return (ID >= 0);
    }

    /**
     * @return the database ID
     */
    int getID() {
        return ID;
    }

    /**
     * @return the eamCase
     */
    public CorrelationCase getCorrelationCase() {
        return correlationCase;
    }

    /**
     * @return the eamDataSource
     */
    public CorrelationDataSource getCorrelationDataSource() {
        return correlationDataSource;
    }

    /**
     * @return the filePath
     */
    public String getFilePath() {
        return filePath;
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

    /**
     * Get this knownStatus. This only indicates whether an item has been tagged
     * as notable and should never return KNOWN.
     *
     * @return BAD if the item has been tagged as notable, UNKNOWN otherwise
     */
    public TskData.FileKnown getKnownStatus() {
        return knownStatus;
    }

    /**
     * Set the knownStatus. This only indicates whether an item has been tagged
     * as notable and should never be set to KNOWN.
     *
     * @param knownStatus Should be BAD if the item is tagged as notable,
     *                    UNKNOWN otherwise
     */
    public void setKnownStatus(TskData.FileKnown knownStatus) {
        this.knownStatus = knownStatus;
    }
}
