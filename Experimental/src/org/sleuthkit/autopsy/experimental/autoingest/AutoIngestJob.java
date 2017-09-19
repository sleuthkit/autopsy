/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.ingest.IngestJob;

/**
 * An automated ingest job, which is an ingest job performed by the automated
 * ingest service.
 */
@ThreadSafe
public final class AutoIngestJob implements Comparable<AutoIngestJob>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final int CURRENT_VERSION = 1;
    private static final int DEFAULT_PRIORITY = 0;
    private static final String LOCAL_HOST_NAME = NetworkUtils.getLocalHostName();

    /*
     * Version 0 fields.
     */
    private final Manifest manifest;
    @GuardedBy("this")
    private String nodeName;
    @GuardedBy("this")
    private String caseDirectoryPath;
    @GuardedBy("this")
    private Integer priority;
    @GuardedBy("this")
    private Stage stage;
    @GuardedBy("this")
    private Date stageStartDate;
    @GuardedBy("this")
    transient private DataSourceProcessor dataSourceProcessor;
    @GuardedBy("this")
    transient private IngestJob ingestJob;
    @GuardedBy("this")
    transient private boolean cancelled;
    @GuardedBy("this")
    transient private boolean completed;
    @GuardedBy("this")
    private Date completedDate;
    @GuardedBy("this")
    private boolean errorsOccurred;

    /*
     * Version 1 fields.
     */
    private final int version; // For possible future use.
    @GuardedBy("this")
    private ProcessingStatus processingStatus;
    @GuardedBy("this")
    private int numberOfCrashes;
    @GuardedBy("this")
    private StageDetails stageDetails;

    /**
     * Constructs a new automated ingest job. All job state not specified in the
     * job manifest is set to the default state for a new job.
     *
     * @param manifest The manifest for an automated ingest job.
     */
    AutoIngestJob(Manifest manifest) {
        /*
         * Version 0 fields.
         */
        this.manifest = manifest;
        this.nodeName = "";
        this.caseDirectoryPath = "";
        this.priority = DEFAULT_PRIORITY;
        this.stage = Stage.PENDING;
        this.stageStartDate = manifest.getDateFileCreated();
        this.dataSourceProcessor = null;
        this.ingestJob = null;
        this.cancelled = false;
        this.completed = false;
        this.completedDate = new Date(0);
        this.errorsOccurred = false;

        /*
         * Version 1 fields.
         */
        this.version = CURRENT_VERSION;
        this.processingStatus = ProcessingStatus.PENDING;
        this.numberOfCrashes = 0;
        this.stageDetails = this.getProcessingStageDetails();
    }

    /**
     * Constructs an automated ingest job using the coordination service node
     * data for the job.
     *
     * @param nodeData The coordination service node data for an automated
     *                 ingest job.
     */
    AutoIngestJob(AutoIngestJobNodeData nodeData) {
        /*
         * Version 0 fields.
         */
        this.manifest = new Manifest(nodeData.getManifestFilePath(), nodeData.getManifestFileDate(), nodeData.getCaseName(), nodeData.getDeviceId(), nodeData.getDataSourcePath(), Collections.emptyMap());
        this.nodeName = nodeData.getProcessingHostName();
        this.caseDirectoryPath = nodeData.getCaseDirectoryPath().toString();
        this.priority = nodeData.getPriority();
        this.stage = nodeData.getProcessingStage();
        this.stageStartDate = nodeData.getProcessingStageStartDate();
        this.dataSourceProcessor = null; // Transient data not in node data.
        this.ingestJob = null; // Transient data not in node data.
        this.cancelled = false; // Transient data not in node data.
        this.completed = false; // Transient data not in node data.
        this.completedDate = nodeData.getCompletedDate();
        this.errorsOccurred = nodeData.getErrorsOccurred();

        /*
         * Version 1 fields.
         */
        this.version = CURRENT_VERSION;
        this.processingStatus = nodeData.getProcessingStatus();
        this.numberOfCrashes = nodeData.getNumberOfCrashes();
        this.stageDetails = this.getProcessingStageDetails();
    }

    /**
     * Gets the job manifest.
     *
     * @return The job manifest.
     */
    Manifest getManifest() {
        return this.manifest;
    }

    /**
     * Sets the path to the case directory for the job.
     *
     * @param caseDirectoryPath The path to the case directory. Can be the empty
     *                          path if the case directory has not been created
     *                          yet.
     */
    synchronized void setCaseDirectoryPath(Path caseDirectoryPath) {
        if (null != caseDirectoryPath) {
            this.caseDirectoryPath = caseDirectoryPath.toString();
        } else {
            this.caseDirectoryPath = "";
        }
    }

    /**
     * Gets the path to the case directory for job, may be the empty path if the
     * case directory has not been created yet.
     *
     * @return The case directory path. Will be the empty path if the case
     *         directory has not been created yet.
     */
    synchronized Path getCaseDirectoryPath() {
        return Paths.get(caseDirectoryPath);
    }

    /**
     * Sets the priority of the job. A higher number indicates a higher
     * priority.
     *
     * @param priority The priority.
     */
    synchronized void setPriority(Integer priority) {
        this.priority = priority;
    }

    /**
     * Gets the priority of the job. A higher number indicates a higher
     * priority.
     *
     * @return The priority.
     */
    synchronized Integer getPriority() {
        return this.priority;
    }

    /**
     * Sets the processing stage of the job. The start date/time for the stage
     * is set when the stage is set.
     *
     * @param newStage The processing stage.
     */
    synchronized void setProcessingStage(Stage newStage) {
        if (Stage.CANCELLING == this.stage && Stage.COMPLETED != newStage) {
            return;
        }
        this.stage = newStage;
        this.stageStartDate = Date.from(Instant.now());
    }

    /**
     * Gets the processing stage of the job.
     *
     * @return The processing stage.
     */
    synchronized Stage getProcessingStage() {
        return this.stage;
    }

    /**
     * Gets the date/time the current processing stage of the job started.
     *
     * @return The current processing stage start date/time.
     */
    synchronized Date getProcessingStageStartDate() {
        return new Date(this.stageStartDate.getTime());
    }

    /**
     * Gets any available details associated with the current processing stage
     * of the job, e.g., the currently running data source level ingest module,
     * an ingest module in the process of being cancelled, etc. If no additional
     * details are available, the stage details will be the same as the
     * processing stage.
     *
     * @return A stage details object consisting of a descrition and associated
     *         date/time.
     */
    synchronized StageDetails getProcessingStageDetails() {
        String description;
        Date startDate;
        if (Stage.CANCELLING != this.stage && null != this.ingestJob) {
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
                    description = String.format(Stage.CANCELLING_MODULE.getDisplayText(), ingestModuleHandle.displayName());
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
        this.stageDetails = new StageDetails(description, startDate);
        return this.stageDetails;
    }

    /**
     * Sets the data source processor for the job. Used for job cancellation.
     *
     * @param dataSourceProcessor A data source processor for the job.
     */
    synchronized void setDataSourceProcessor(DataSourceProcessor dataSourceProcessor) {
        this.dataSourceProcessor = dataSourceProcessor;
    }

    /**
     * Sets the ingest job for the auto ingest job. Used for obtaining
     * processing stage details, cancelling the currently running data source
     * ingest module, and cancelling the job.
     *
     * @param ingestJob The ingest job for the auto ingest job.
     */
    synchronized void setIngestJob(IngestJob ingestJob) {
        this.ingestJob = ingestJob;
    }

    /**
     * Gets the ingest job for the auto ingest job.
     *
     * @return The ingest job, may be null.
     *
     * TODO (JIRA-3059): Provide an AutoIngestJob ingest module cancellation API
     * instead.
     */
    synchronized IngestJob getIngestJob() {
        return this.ingestJob;
    }

    /**
     * Cancels the job.
     */
    synchronized void cancel() {
        setProcessingStage(Stage.CANCELLING);
        cancelled = true;
        errorsOccurred = true;
        if (null != dataSourceProcessor) {
            dataSourceProcessor.cancel();
        }
        if (null != ingestJob) {
            ingestJob.cancel(IngestJob.CancellationReason.USER_CANCELLED);
        }
    }

    /**
     * Indicates whether or not the job has been cancelled. This is transient
     * state used by the auto ingest manager that is not saved as coordination
     * service node data for the job.
     *
     * @return True or false.
     */
    synchronized boolean isCanceled() {
        return cancelled;
    }

    /**
     * Marks the job as completed. This is transient state used by the auto
     * ingest manager that is not saved as coordination service node data for
     * the job.
     */
    synchronized void setCompleted() {
        setProcessingStage(Stage.COMPLETED);
        completed = true;
    }

    /**
     * Indicates whether or not the job has been completed. This is transient
     * state that is not saved as coordination service node data for the job.
     *
     * @return True or false.
     */
    synchronized boolean isCompleted() {
        return completed;
    }

    /**
     * Sets the date the job was completed, with or without cancellation or
     * errors.
     *
     * @param completedDate The completion date.
     */
    synchronized void setCompletedDate(Date completedDate) {
        this.completedDate = new Date(completedDate.getTime());
    }

    /**
     * Gets the date the job was completed, with or without cancellation or
     * errors.
     *
     * @return True or false.
     */
    synchronized Date getCompletedDate() {
        return new Date(completedDate.getTime());
    }

    /**
     * Sets whether or not errors occurred during the processing of the job.
     *
     * @param errorsOccurred True or false;
     */
    synchronized void setErrorsOccurred(boolean errorsOccurred) {
        this.errorsOccurred = errorsOccurred;
    }

    /**
     * Queries whether or not errors occurred during the processing of the job.
     *
     * @return True or false.
     */
    synchronized boolean getErrorsOccurred() {
        return this.errorsOccurred;
    }

    /**
     * Gets the processing host name for this job.
     *
     * @return The processing host name.
     */
    synchronized String getProcessingHostName() {
        return nodeName;
    }

    /**
     * Sets the processing host name for this job.
     *
     * @param processingHostName The processing host name.
     */
    synchronized void setProcessingHostName(String processingHostName) {
        this.nodeName = processingHostName;
    }

    /**
     * Gets the processing status of the job.
     *
     * @return The processing status.
     */
    synchronized ProcessingStatus getProcessingStatus() {
        return this.processingStatus;
    }

    /**
     * Sets the processing status of the job.
     *
     * @param processingStatus The processing status.
     */
    synchronized void setProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
    }

    /**
     * Gets the number of time this job has "crashed" during processing.
     *
     * @return The number of crashes.
     */
    synchronized int getNumberOfCrashes() {
        return this.numberOfCrashes;
    }

    /**
     * Sets the number of time this job has "crashed" during processing.
     *
     * @param numberOfCrashes The number of crashes.
     */
    synchronized void setNumberOfCrashes(int numberOfCrashes) {
        this.numberOfCrashes = numberOfCrashes;
    }

    /**
     * Indicates whether some other job is "equal to" this job. Two jobs are
     * equal if they have the same manifest file path.
     *
     * @param otherJob The job to which this job is to be compared.
     *
     * @return True or false.
     */
    @Override
    public boolean equals(Object otherJob) {
        if (!(otherJob instanceof AutoIngestJob)) {
            return false;
        }
        if (otherJob == this) {
            return true;
        }
        return this.getManifest().getFilePath().equals(((AutoIngestJob) otherJob).getManifest().getFilePath());
    }

    /**
     * Returns a hash code value for the job. The hash code is derived from the
     * manifest file path.
     *
     * @return The hash code.
     */
    @Override
    public int hashCode() {
        int hash = 71 * (Objects.hashCode(this.getManifest().getFilePath()));
        return hash;
    }

    /**
     * Compares one job to another in a way that orders jobs by manifest
     * creation date.
     *
     * @param otherJob The job to which this job is to be compared.
     *
     * @return A negative integer, zero, or a positive integer as this job is
     *         less than, equal to, or greater than the specified job.
     */
    @Override
    public int compareTo(AutoIngestJob otherJob) {
        return -this.getManifest().getDateFileCreated().compareTo(otherJob.getManifest().getDateFileCreated());
    }

    /**
     * Comparator that supports doing a descending sort of jobs based on job
     * completion date.
     */
    static class ReverseCompletedDateComparator implements Comparator<AutoIngestJob> {

        @Override
        public int compare(AutoIngestJob o1, AutoIngestJob o2) {
            return -o1.getCompletedDate().compareTo(o2.getCompletedDate());
        }

    }

    /**
     * Comparator that supports doing a descending sort of jobs based on job
     * priority.
     */
    public static class PriorityComparator implements Comparator<AutoIngestJob> {

        @Override
        public int compare(AutoIngestJob job, AutoIngestJob anotherJob) {
            return -(job.getPriority().compareTo(anotherJob.getPriority()));
        }

    }

    /**
     * Comparator that supports doing an alphabetical sort of jobs based on a
     * combination of case name and processing host.
     */
    static class CaseNameAndProcessingHostComparator implements Comparator<AutoIngestJob> {

        @Override
        public int compare(AutoIngestJob aJob, AutoIngestJob anotherJob) {
            if (aJob.getProcessingHostName().equalsIgnoreCase(LOCAL_HOST_NAME)) {
                return -1; // aJob is for this, float to top
            } else if (anotherJob.getProcessingHostName().equalsIgnoreCase(LOCAL_HOST_NAME)) {
                return 1; // anotherJob is for this, float to top
            } else {
                return aJob.getManifest().getCaseName().compareToIgnoreCase(anotherJob.getManifest().getCaseName());
            }
        }

    }

    /**
     * Processing statuses for an auto ingest job.
     */
    enum ProcessingStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        DELETED
    }

    /**
     * Processing stages for an auto ingest job.
     */
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
        CANCELLING("Cancelling"),
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
     * Processing stage details for an auto ingest job.
     */
    @Immutable
    static final class StageDetails implements Serializable {

        private static final long serialVersionUID = 1L;
        private final String description;
        private final Date startDate;

        StageDetails(String description, Date startDate) {
            this.description = description;
            this.startDate = startDate;
        }

        String getDescription() {
            return this.description;
        }

        Date getStartDate() {
            return new Date(this.startDate.getTime());
        }

    }

}
