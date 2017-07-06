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
 * Used to store info about a specific Artifact Instance.
 *
 */
@Messages({"EamArtifactInstances.globalStatus.local=Local",
    "EamArtifactInstances.globalStatus.global=Global",
    "EamArtifactInstances.knownStatus.bad=Bad",
    "EamArtifactInstances.knownStatus.known=Known",
    "EamArtifactInstances.knownStatus.unknown=Unknown"})
public class EamArtifactInstance implements Serializable {

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

    private String ID;
    private EamCase eamCase;
    private EamDataSource eamDataSource;
    private String filePath;
    private String comment;
    private TskData.FileKnown knownStatus;
    private GlobalStatus globalStatus;

    public EamArtifactInstance(
            EamCase eamCase,
            EamDataSource eamDataSource
    ) {
        this("", eamCase, eamDataSource, "", null, TskData.FileKnown.UNKNOWN, GlobalStatus.LOCAL);
    }

    public EamArtifactInstance(
            EamCase eamCase,
            EamDataSource eamDataSource,
            String filePath
    ) {
        this("", eamCase, eamDataSource, filePath, null, TskData.FileKnown.UNKNOWN, GlobalStatus.LOCAL);
    }

    public EamArtifactInstance(
            EamCase eamCase,
            EamDataSource eamDataSource,
            String filePath,
            String comment
    ) {
        this("", eamCase, eamDataSource, filePath, comment, TskData.FileKnown.UNKNOWN, GlobalStatus.LOCAL);
    }

    public EamArtifactInstance(
            EamCase eamCase,
            EamDataSource eamDataSource,
            String filePath,
            String comment,
            TskData.FileKnown knownStatus,
            GlobalStatus globalStatus
    ) {
        this("", eamCase, eamDataSource, filePath, comment, knownStatus, globalStatus);
    }

    public EamArtifactInstance(
            String ID,
            EamCase eamCase,
            EamDataSource eamDataSource,
            String filePath,
            String comment,
            TskData.FileKnown knownStatus,
            GlobalStatus globalStatus
    ) {
        this.ID = ID;
        this.eamCase = eamCase;
        this.eamDataSource = eamDataSource;
        // Lower case paths to normalize paths and improve correlation results, if this causes significant issues on case-sensitive file systems, remove
        this.filePath = filePath.toLowerCase();
        this.comment = comment;
        this.knownStatus = knownStatus;
        this.globalStatus = globalStatus;
    }

    public Boolean equals(EamArtifactInstance otherInstance) {
        return ((this.getID().equals(otherInstance.getID()))
                && (this.getEamCase().equals(otherInstance.getEamCase()))
                && (this.getEamDataSource().equals(otherInstance.getEamDataSource()))
                && (this.getFilePath().equals(otherInstance.getFilePath()))
                && (this.getGlobalStatus().equals(otherInstance.getGlobalStatus()))
                && (this.getKnownStatus().equals(otherInstance.getKnownStatus()))
                && (this.getComment().equals(otherInstance.getComment())));
    }

    @Override
    public String toString() {
        return this.getID()
                + this.getEamCase().getCaseUUID()
                + this.getEamDataSource().getName()
                + this.getFilePath()
                + this.getGlobalStatus()
                + this.getKnownStatus()
                + this.getComment();
    }

    /**
     * @return the ID
     */
    public String getID() {
        return ID;
    }

    /**
     * @param ID the ID to set
     */
    public void setID(String ID) {
        this.ID = ID;
    }

    /**
     * @return the eamCase
     */
    public EamCase getEamCase() {
        return eamCase;
    }

    /**
     * @param eamCase the eamCase to set
     */
    public void setEamCase(EamCase eamCase) {
        this.eamCase = eamCase;
    }

    /**
     * @return the eamDataSource
     */
    public EamDataSource getEamDataSource() {
        return eamDataSource;
    }

    /**
     * @param eamDataSource the eamDataSource to set
     */
    public void setEamDataSource(EamDataSource eamDataSource) {
        this.eamDataSource = eamDataSource;
    }

    /**
     * @return the filePath
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * @param filePath the filePath to set
     */
    public void setFilePath(String filePath) {
        // Lower case paths to normalize paths and improve correlation results, if this causes significant issues on case-sensitive file systems, remove
        this.filePath = filePath.toLowerCase();
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
