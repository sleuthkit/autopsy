/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.autoingest;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.ingest.IngestJob;

/**
 * An automated ingest job auto ingest jobs associated with a manifest file. A
 * manifest file specifies a co-located data source and a case to which the data
 * source is to be added.
 */
@ThreadSafe
public final class AutoIngestJob implements Comparable<AutoIngestJob>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final String LOCAL_HOST_NAME = NetworkUtils.getLocalHostName();
    private final Manifest manifest;
    private final String nodeName;
    @GuardedBy("this")
    private String caseDirectoryPath;
    @GuardedBy("this")
    private Integer priority;
    @GuardedBy("this")
    private Stage stage;
    @GuardedBy("this")
    private Date stageStartDate;
    @GuardedBy("this")
    transient private IngestJob ingestJob;

    /**
     * RJCTODO
     *
     * @param manifest
     */
    AutoIngestJob(Manifest manifest, Path caseDirectoryPath, int priority, String nodeName) {
        this.manifest = manifest;
        if (null != caseDirectoryPath) {
            this.caseDirectoryPath = caseDirectoryPath.toString();
        } else {
            this.caseDirectoryPath = "";
        }
        this.priority = priority;
        this.nodeName = nodeName;
        this.stage = Stage.PENDING;
        this.stageStartDate = manifest.getDateFileCreated();
    }

    /**
     * RJCTODO
     *
     * @return
     */
    Manifest getManifest() {
        return this.manifest;
    }

    /**
     * Queries whether or not a case directory path has been set for this auto
     * ingest job.
     *
     * @return True or false
     */
    synchronized boolean hasCaseDirectoryPath() {
        return (false == this.caseDirectoryPath.isEmpty());
    }

    /**
     * Sets the path to the case directory of the case associated with this job.
     *
     * @param caseDirectoryPath The path to the case directory.
     */
    synchronized void setCaseDirectoryPath(Path caseDirectoryPath) {
        this.caseDirectoryPath = caseDirectoryPath.toString();
    }

    /**
     * Gets the path to the case directory of the case associated with this job,
     * may be null.
     *
     * @return The case directory path or null if the case directory has not
     *         been created yet.
     */
    synchronized Path getCaseDirectoryPath() {
        return Paths.get(caseDirectoryPath); // RJCTODO: This may not be such a good idea, perhaps a null is better if the path is empty string
    }

    synchronized void setPriority(Integer priority) {
        this.priority = priority;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    synchronized Integer getPriority() {
        return this.priority;
    }

    /**
     * RJCTODO
     *
     * @param newState
     */
    synchronized void setStage(Stage newState) {
        setStage(newState, Date.from(Instant.now()));
    }

    /**
     * RJCTODO
     *
     * @param state
     * @param stateStartedDate
     */
    synchronized void setStage(Stage newState, Date stateStartedDate) {
        if (Stage.CANCELLED == this.stage && Stage.COMPLETED != newState) {
            /**
             * Do not overwrite canceling status with anything other than
             * completed status.
             */
            return;
        }
        this.stage = newState;
        this.stageStartDate = stateStartedDate;
    }

    /**
     * RJCTODO:
     *
     * @return
     */
    synchronized Stage getStage() {
        return this.stage;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    synchronized Date getStageStartDate() {
        return this.stageStartDate;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    synchronized StageDetails getStageDetails() {
        String description;
        Date startDate;
        if (null != this.ingestJob) {
            IngestJob.ProgressSnapshot progress = this.ingestJob.getSnapshot();
            IngestJob.DataSourceIngestModuleHandle ingestModuleHandle = progress.runningDataSourceIngestModule();
            if (null != ingestModuleHandle) {
                /**
                 * A first or second stage data source level ingest module is
                 * running. Reporting this takes precedence over reporting
                 * generic file analysis.
                 */
                startDate = ingestModuleHandle.startTime();
                if (!ingestModuleHandle.isCancelled()) {
                    description = ingestModuleHandle.displayName();
                } else {
                    description = String.format(Stage.CANCELLING_MODULE.getDisplayText(), ingestModuleHandle.displayName()); // RJCTODO: FIx this
                }
            } else {
                /**
                 * If no data source level ingest module is running, then either
                 * it is still the first stage of analysis and file level ingest
                 * modules are running or another ingest job is still running.
                 * Note that there can be multiple ingest jobs running in
                 * parallel. For example, there is an ingest job created to
                 * ingest each extracted virtual machine.
                 */
                description = Stage.ANALYZING_FILES.getDisplayText();
                startDate = progress.fileIngestStartTime();
            }
        } else {
            description = this.stage.getDisplayText();
            startDate = this.stageStartDate;
        }
        return new StageDetails(description, startDate);
    }

    /**
     * RJCTODO
     *
     * @param ingestStatus
     */
    // RJCTODO: Consider moving this class into AIM and making this private
    synchronized void setIngestJob(IngestJob ingestJob) {
        this.ingestJob = ingestJob;
    }

    /**
     * RJCTODO
     *
     * @return
     */
    // RJCTODO: Consider moving this class into AIM and making this private. 
    // Or move the AID into a separate package. Or do not worry about it.
    synchronized IngestJob getIngestJob() {
        return this.ingestJob;
    }

    /**
     * RJCTODO Gets name of the node associated with the job, possibly a remote
     * hose if the job is in progress.
     *
     * @return The node name.
     */
    String getNodeName() {
        return nodeName;
    }

    /**
     * RJCTODO
     *
     * @param obj
     *
     * @return
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AutoIngestJob)) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        return this.getManifest().getFilePath().equals(((AutoIngestJob) obj).getManifest().getFilePath());
    }

    /**
     * RJCTODO
     *
     * @return
     */
    @Override
    public int hashCode() {
        // RJCTODO: Update this
        int hash = 7;
//        hash = 71 * hash + Objects.hashCode(this.dateCreated);
        return hash;
    }

    /**
     * RJCTODO Default sorting is by ready file creation date, descending
     *
     * @param o
     *
     * @return
     */
    @Override
    public int compareTo(AutoIngestJob o) {
        return -this.getManifest().getDateFileCreated().compareTo(o.getManifest().getDateFileCreated());
    }

    /**
     * Custom comparator that allows us to sort List<AutoIngestJob> on reverse
     * chronological date modified (descending)
     */
    static class ReverseDateCompletedComparator implements Comparator<AutoIngestJob> {

        /**
         * RJCTODO
         *
         * @param o1
         * @param o2
         *
         * @return
         */
        @Override
        public int compare(AutoIngestJob o1, AutoIngestJob o2) {
            return -o1.getStageStartDate().compareTo(o2.getStageStartDate());
        }
    }

    /**
     * Custom comparator that sorts the pending list with prioritized cases
     * first, then nonprioritized cases. Prioritized cases are last in, first
     * out. Nonprioritized cases are first in, first out. Prioritized times are
     * from the creation time of the "prioritized" state file. Non prioritized
     * are from the folder creation time.
     */
    public static class PriorityComparator implements Comparator<AutoIngestJob> {

        /**
         * RJCTODO
         *
         * @param o1
         * @param o2
         *
         * @return
         */
        @Override
        public int compare(AutoIngestJob o1, AutoIngestJob o2) {
            Integer result = o1.getPriority().compareTo(o2.getPriority());
            if (0 != result) {
                return result;
            } else {
                return o1.getManifest().getDateFileCreated().compareTo(o2.getManifest().getDateFileCreated());
            }
        }

    }

    /**
     * Custom comparator that allows us to sort List<AutoIngestJob> on case name
     * alphabetically except for jobs for the current host, which are placed at
     * the top of the list.
     */
    static class AlphabeticalComparator implements Comparator<AutoIngestJob> {

        /**
         * RJCTODO
         *
         * @param o1
         * @param o2
         *
         * @return
         */
        @Override
        public int compare(AutoIngestJob o1, AutoIngestJob o2) {
            if (o1.getNodeName().equalsIgnoreCase(LOCAL_HOST_NAME)) {
                return -1; // o1 is for current case, float to top
            } else if (o2.getNodeName().equalsIgnoreCase(LOCAL_HOST_NAME)) {
                return 1; // o2 is for current case, float to top
            } else {
                return o1.getManifest().getCaseName().compareToIgnoreCase(o2.getManifest().getCaseName());
            }
        }
    }

    enum Stage {

        PENDING("Pending"),
        STARTING("Starting"),
        UPDATING_SHARED_CONFIG("Updating shared configuration"),
        CHECKING_SERVICES("Checking services"),
        OPENING_CASE("Opening case"),
        IDENTIFYING_DATA_SOURCE("Identifying data source type"),
        ADDING_DATA_SOURCE("Adding data source"),
        ANALYZING_DATA_SOURCE("Analyzing data source"),
        ANALYZING_FILES("Analyzing files"),
        EXPORTING_FILES("Exporting files"),
        CANCELLING_MODULE("Cancelling module"),
        CANCELLED("Cancelling"),
        COMPLETED("Completed");

        private final String displayText;

        private Stage(String displayText) {
            this.displayText = displayText;
        }

        String getDisplayText() {
            return displayText;
        }

    }

    /**
     * RJCTODO
     */
    @Immutable
    static final class StageDetails {

        private final String description;
        private final Date startDate;

        /**
         * RJCTODO
         *
         * @param description
         * @param startDate
         */
        private StageDetails(String description, Date startDate) {
            this.description = description;
            this.startDate = startDate;
        }

        /**
         * RJCTODO
         *
         * @return
         */
        String getDescription() {
            return this.description;
        }

        /**
         * RJCTODO
         *
         * @return
         */
        Date getStartDate() {
            return this.startDate;
        }

    }

}
