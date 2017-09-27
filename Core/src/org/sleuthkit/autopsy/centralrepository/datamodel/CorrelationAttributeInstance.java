/*
 * Central Repository
 *
 * Copyright 2015-2017 Basis Technology Corp.
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
 * Used to store details about a specific instance of a
 * CorrelationAttribute. Includes its data source, path, etc.
 *
 */
@Messages({"EamArtifactInstances.globalStatus.local=Local",
    "EamArtifactInstances.globalStatus.global=Global",
    "EamArtifactInstances.knownStatus.bad=Bad",
    "EamArtifactInstances.knownStatus.known=Known",
    "EamArtifactInstances.knownStatus.unknown=Unknown"})
public class CorrelationAttributeInstance implements Serializable {

    public enum GlobalStatus {
        LOCAL(Bundle.EamArtifactInstances_globalStatus_local()),
        GLOBAL(Bundle.EamArtifactInstances_globalStatus_global());

        private final String globalStatus;

        private GlobalStatus(String globalStatus) {
            this.globalStatus = globalStatus;
        }

        @Override
        public String toString() {
            return globalStatus;
        }
    }

    private static final long serialVersionUID = 1L;

    private int ID;
    private CorrelationCase correlationCase;
    private CorrelationDataSource correlationDataSource;
    private String filePath;
    private String comment;
    private TskData.FileKnown knownStatus;
    private GlobalStatus globalStatus;

    public CorrelationAttributeInstance(
            CorrelationCase eamCase,
            CorrelationDataSource eamDataSource
    ) {
        this(-1, eamCase, eamDataSource, "", null, TskData.FileKnown.UNKNOWN, GlobalStatus.LOCAL);
    }

    public CorrelationAttributeInstance(
            CorrelationCase eamCase,
            CorrelationDataSource eamDataSource,
            String filePath
    ) {
        this(-1, eamCase, eamDataSource, filePath, null, TskData.FileKnown.UNKNOWN, GlobalStatus.LOCAL);
    }

    public CorrelationAttributeInstance(
            CorrelationCase eamCase,
            CorrelationDataSource eamDataSource,
            String filePath,
            String comment
    ) {
        this(-1, eamCase, eamDataSource, filePath, comment, TskData.FileKnown.UNKNOWN, GlobalStatus.LOCAL);
    }

    public CorrelationAttributeInstance(
            CorrelationCase eamCase,
            CorrelationDataSource eamDataSource,
            String filePath,
            String comment,
            TskData.FileKnown knownStatus,
            GlobalStatus globalStatus
    ) {
        this(-1, eamCase, eamDataSource, filePath, comment, knownStatus, globalStatus);
    }

    public CorrelationAttributeInstance(
            int ID,
            CorrelationCase eamCase,
            CorrelationDataSource eamDataSource,
            String filePath,
            String comment,
            TskData.FileKnown knownStatus,
            GlobalStatus globalStatus
    ) {
        this.ID = ID;
        this.correlationCase = eamCase;
        this.correlationDataSource = eamDataSource;
        // Lower case paths to normalize paths and improve correlation results, if this causes significant issues on case-sensitive file systems, remove
        this.filePath = filePath.toLowerCase();
        this.comment = comment;
        this.knownStatus = knownStatus;
        this.globalStatus = globalStatus;
    }

    public Boolean equals(CorrelationAttributeInstance otherInstance) {
        return ((this.getID() == otherInstance.getID())
                && (this.getCorrelationCase().equals(otherInstance.getCorrelationCase()))
                && (this.getCorrelationDataSource().equals(otherInstance.getCorrelationDataSource()))
                && (this.getFilePath().equals(otherInstance.getFilePath()))
                && (this.getGlobalStatus().equals(otherInstance.getGlobalStatus()))
                && (this.getKnownStatus().equals(otherInstance.getKnownStatus()))
                && (this.getComment().equals(otherInstance.getComment())));
    }

    @Override
    public String toString() {
        return this.getID()
                + this.getCorrelationCase().getCaseUUID()
                + this.getCorrelationDataSource().getName()
                + this.getFilePath()
                + this.getGlobalStatus()
                + this.getKnownStatus()
                + this.getComment();
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
     * @return the globalStatus
     */
    public GlobalStatus getGlobalStatus() {
        return globalStatus;
    }

    /**
     * @param globalStatus the globalStatus to set
     */
    public void setGlobalStatus(GlobalStatus globalStatus) {
        this.globalStatus = globalStatus;
    }

}
