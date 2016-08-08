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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import java.util.logging.Level;
import org.sleuthkit.autopsy.*;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.ingest.IngestJob;
import org.joda.time.DateTime;

/**
 * An automated ingest job completed by, or to be completed by, the automated
 * ingest manager.
 */
class AutoIngestJob implements Comparable<AutoIngestJob> {

    // ELTODO: move JobIngestStatus back into AIM
    /**
     * Represents the state of an auto ingest job at any given moment during its
     * lifecycle as it moves from waiting to be processed, through the various
     * stages of processing, to its final completed state.
     */
    static final class JobIngestStatus {

        private enum IngestStatus {

            PENDING("Pending"),
            STARTING("Starting"),
            UPDATING_SHARED_CONFIG("Updating shared configuration"),
            CHECKING_SERVICES("Checking services"),
            OPENING_CASE("Opening case"),
            IDENTIFYING_IMAGES("Identifying images"),
            ADDING_IMAGES("Adding images"),
            ANALYZING_IMAGES("Analyzing images"),
            ANALYZING_FILES("Analyzing files"),
            EXPORTING_FILES("Exporting files"),
            CANCELLING_MODULE("Cancelling module"),
            CANCELLING("Cancelling"),
            COMPLETED("Completed");

            private final String displayText;

            private IngestStatus(String displayText) {
                this.displayText = displayText;
            }

            String getDisplayText() {
                return displayText;
            }

        }

        private IngestStatus ingestStatus;
        private String statusDisplayName;
        private Date startDate;
        private IngestJob ingestJob;
        private boolean cancelled;
        private Date dateCompleted;

        private JobIngestStatus(Date dateCompleted) {
            ingestStatus = IngestStatus.PENDING;
            statusDisplayName = ingestStatus.getDisplayText();
            startDate = DateTime.now().toDate();
            this.dateCompleted = dateCompleted;
        }

        /**
         * Updates displayed status and start fileTime of auto ingest job. Used
         * primarily to display status of remote running jobs.
         *
         * @param newDisplayName Displayed status of the auto ingest job.
         * @param startTime      Start fileTime of the current activity.
         */
        synchronized private void setStatus(String newDisplayName, Date startTime) {
            statusDisplayName = newDisplayName;
            startDate = startTime;
        }

        /**
         * Updates status of auto ingest job. Sets current fileTime as activity
         * start fileTime. Used to update status of local running job.
         *
         * @param newStatus Status of the auto ingest job.
         */
        synchronized private void setStatus(IngestStatus newStatus) {
            if (ingestStatus == IngestStatus.CANCELLING && newStatus != IngestStatus.COMPLETED) {
                /**
                 * Do not overwrite canceling status with anything other than
                 * completed status.
                 */
                return;
            }
            ingestStatus = newStatus;
            statusDisplayName = ingestStatus.getDisplayText();
            startDate = Date.from(Instant.now());
            if (ingestStatus == IngestStatus.COMPLETED) {
                /**
                 * Release the reference for garbage collection since this
                 * object may live for a long time within a completed job.
                 */
                ingestJob = null;
            }
            if (ingestStatus == IngestStatus.COMPLETED) {
                dateCompleted = startDate;
            }
        }

        synchronized private void setIngestJob(IngestJob ingestJob) {
            /**
             * Once this field is set, the ingest job should be used to
             * determine the current activity up until the the job is completed.
             */
            this.ingestJob = ingestJob;
        }

        synchronized AutoIngestJob.Status getStatus() {
            if (null != ingestJob && ingestStatus != IngestStatus.CANCELLING && ingestStatus != IngestStatus.EXPORTING_FILES) {
                String activityDisplayText;
                IngestJob.ProgressSnapshot progress = ingestJob.getSnapshot();
                IngestJob.DataSourceIngestModuleHandle ingestModuleHandle = progress.runningDataSourceIngestModule();
                if (null != ingestModuleHandle) {
                    /**
                     * A first or second stage data source level ingest module
                     * is running. Reporting this takes precedence over
                     * reporting generic file analysis.
                     */
                    startDate = ingestModuleHandle.startTime();
                    if (!ingestModuleHandle.isCancelled()) {
                        activityDisplayText = ingestModuleHandle.displayName();
                    } else {
                        activityDisplayText = String.format(IngestStatus.CANCELLING_MODULE.getDisplayText(), ingestModuleHandle.displayName());
                    }
                } else {
                    /**
                     * If no data source level ingest module is running, then
                     * either it is still the first stage of analysis and file
                     * level ingest modules are running or another ingest job is
                     * still running. Note that there can be multiple ingest
                     * jobs running in parallel. For example, there is an ingest
                     * job created to ingest each extracted virtual machine.
                     */
                    activityDisplayText = IngestStatus.ANALYZING_FILES.getDisplayText();
                    startDate = progress.fileIngestStartTime();
                }
                return new AutoIngestJob.Status(activityDisplayText, startDate);
            } else {
                return new AutoIngestJob.Status(statusDisplayName, startDate);
            }
        }

        synchronized private IngestJob setStatusCancelled() {
            cancelled = true;
            setStatus(JobIngestStatus.IngestStatus.CANCELLING);
            return ingestJob;
        }

        synchronized private IngestJob cancelModule() {
            setStatus(JobIngestStatus.IngestStatus.CANCELLING_MODULE);
            return ingestJob;
        }

        synchronized private boolean isCancelled() {
            return cancelled;
        }

        synchronized Date getDateCompleted() {
            return dateCompleted;
        }

        synchronized Date getDateStarted() {
            return startDate;
        }
    }

    private static final Logger logger = Logger.getLogger(AutoIngestJob.class.getName());
    private final String caseName;
    private final Path imageFolderPath;
    private final Date imageFolderCreateDate;
    private Path caseFolderName;
    private final String jobDisplayName;
    private String nodeName;
//ELTODO    private final AutoIngestManager.JobIngestStatus ingestStatus;
    private final JobIngestStatus ingestStatus;
    private static final String localHostName = NetworkUtils.getLocalHostName();

    /**
     * This variable is being accessed by AID as well as JMS thread
     */
    private volatile boolean isLocalJob;
    private Date readyFileTimeStamp;
    private Date prioritizedFileTimeStamp;

    /**
     * Constructs an automated ingest job completed by, or to be completed by,
     * the automated ingest manager.
     *
     * @param imageFolderPath The fully qualified path to the case input folder
     *                        for the job.
     * @param caseName        The case to which this job belongs. Note that this
     *                        is the original case name (and not the timestamped
     *                        case name).
     * @param caseFolderName  The fully qualified path to the case output folder
     *                        for the job, if known.
     * @param ingestStatus    Ingest status details provided by the automated
     *                        ingest manager.
     * @param nodeName        Name of the node that is processing the job
     */
    AutoIngestJob(Path imageFolderPath, String caseName, Path caseFolderName, /* //ELTODO AutoIngestManager.*/JobIngestStatus ingestStatus, String nodeName) {
        this.caseName = caseName;
        this.imageFolderPath = imageFolderPath;
        this.caseFolderName = caseFolderName;
        this.ingestStatus = ingestStatus;
        this.jobDisplayName = resolveJobDisplayName(imageFolderPath);
        this.isLocalJob = true; // jobs are set to "local" by default
        this.nodeName = nodeName;

        /**
         * Either initialize to the folder creation date or the current date.
         * Note that the way this is coded allows the folder creation date field
         * to be final.
         */
        BasicFileAttributes attrs = null;
        try {
            attrs = Files.readAttributes(imageFolderPath, BasicFileAttributes.class);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, String.format("Failed to read attributes of input folder %s", imageFolderPath), ex);
        }
        this.imageFolderCreateDate = attrs == null ? new Date() : new Date(attrs.creationTime().toMillis());

        try {
            attrs = Files.readAttributes(Paths.get(imageFolderPath.toString(), StateFile.Type.READY.fileName()), BasicFileAttributes.class);
            this.readyFileTimeStamp = new Date(attrs.creationTime().toMillis());
        } catch (IOException ex) {
            // Auto ingest job may be created for a remotely running job so no need to log exception if we don't find READY file
            this.readyFileTimeStamp = new Date();
        }

        try {
            attrs = Files.readAttributes(Paths.get(imageFolderPath.toString(), StateFile.Type.PRIORITIZED.fileName()), BasicFileAttributes.class);
            this.prioritizedFileTimeStamp = new Date(attrs.creationTime().toMillis());
        } catch (IOException ex) {
            this.prioritizedFileTimeStamp = null;
        }
    }

    static final class Status {

        private final String activity;
        private final Date activityStartDate;

        Status(String activity, Date activityStartTime) {
            this.activity = activity;
            this.activityStartDate = activityStartTime;
        }

        String getActivity() {
            return this.activity;
        }

        Date getActivityStartDate() {
            return this.activityStartDate;
        }
    }

    Status getStatus() {
        return ingestStatus.getStatus();
    }

    // ELTODO AutoIngestManager.JobIngestStatus getIngestStatus() {
    JobIngestStatus getIngestStatus() {
        return ingestStatus;
    }

    /**
     * Determine auto ingest job's display name. Display name is a relative path
     * from case folder down to auto ingest job's folder.
     *
     * @param jobFolderPath Full path to auto ingest job's directory
     *
     * @return Auto ingest job's display name
     */
    private String resolveJobDisplayName(Path jobFolderPath) {
        Path pathRelative;
        try {
            Path rootInputFolderPath = Paths.get(UserPreferences.getAutoModeImageFolder());
            Path casePath = PathUtils.caseImagesPathFromImageFolderPath(rootInputFolderPath, jobFolderPath);
            pathRelative = casePath.relativize(jobFolderPath);
        } catch (Exception ignore) {
            // job folder is not a subpath of case folder, return entire job folder path
            return jobFolderPath.toString();
        }
        return pathRelative.toString();
    }

    /**
     * Returns the fully qualified path to the input folder.
     */
    Path getImageFolderPath() {
        return imageFolderPath;
    }

    /**
     * Returns the name of the case to which the ingest job belongs. Note that
     * this is the original case name (not the timestamped Autopsy case name).
     */
    String getCaseName() {
        return this.caseName;
    }

    /**
     * Returns the display name for current auto ingest job.
     */
    String getJobDisplayName() {
        return this.jobDisplayName;
    }

    /**
     * Returns the fully qualified path to the case results folder.
     */
    Path getCaseFolderPath() {
        return this.caseFolderName;
    }

    /**
     * Set the fully qualifies path to the case results folder.
     *
     * @param resultsPath
     */
    void setCaseFolderPath(Path resultsPath) {
        this.caseFolderName = resultsPath;
    }

    /**
     * Get the date processing completed on the job.
     */
    Date getDateCompleted() {
        return ingestStatus.getDateCompleted();
    }

    /**
     * Get the ready file created date
     */
    Date getReadyFileTimeStamp() {
        return this.readyFileTimeStamp;
    }

    /**
     * Get the prioritized file created date.
     */
    Date getPrioritizedFileTimeStamp() {
        return this.prioritizedFileTimeStamp;
    }

    /**
     * Sets the created date of the prirotized state file for this job.
     */
    void setPrioritizedFileTimeStamp(Date timeStamp) {
        /*
         * RJC: This method is a bit of a hack to support a quick and dirty way
         * of giving user feedback when an input folder or a case is
         * prioritized. It can be removed when a better solution is found, or
         * replaced with a method that looks up the state file time stamp.
         */
        this.prioritizedFileTimeStamp = timeStamp;
    }

    /**
     * Gets case status based on the state files that exist in the job folder.
     *
     * @return See CaseStatus enum definition.
     */
    CaseStatus getCaseStatus() {
        try {
            if (StateFile.exists(imageFolderPath, StateFile.Type.CANCELLED)) {
                return CaseStatus.CANCELLATIONS;
            } else if (StateFile.exists(imageFolderPath, StateFile.Type.ERROR)) {
                return CaseStatus.ERRORS;
            } else if (StateFile.exists(imageFolderPath, StateFile.Type.INTERRUPTED)) {
                return CaseStatus.INTERRUPTS;
            } else {
                return CaseStatus.OK;
            }
        } catch (IOException | SecurityException ex) {
            logger.log(Level.SEVERE, String.format("Failed to determine status of case at %s", imageFolderPath), ex);
            return CaseStatus.ERRORS;
        }
    }

    /**
     * Returns the date the input folder was created.
     *
     * @return
     */
    Date getDateCreated() {
        return this.imageFolderCreateDate;
    }

    /**
     * Updates flag whether the auto ingest job is running on local AIM node or
     * remote one.
     *
     * @param isLocal true if job is local, false otherwise.
     */
    void setIsLocalJob(boolean isLocal) {
        this.isLocalJob = isLocal;
    }

    /**
     * Gets flag whether the auto ingest job is running on local AIM node or
     * remote one.
     *
     * @return True if job is local, false otherwise.
     */
    boolean getIsLocalJob() {
        return this.isLocalJob;
    }

    /**
     * Gets name of AIN that is processing the job.
     *
     * @return Name of the node that is processing the job.
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Sets name of AIN that is processing the job.
     *
     * @param nodeName Name of the node that is processing the job.
     */
    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AutoIngestJob)) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        AutoIngestJob rhs = (AutoIngestJob) obj;

        return this.imageFolderPath.toString().equals(rhs.imageFolderPath.toString());
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + Objects.hashCode(this.imageFolderPath);
        hash = 71 * hash + Objects.hashCode(this.imageFolderCreateDate);
        hash = 71 * hash + Objects.hashCode(this.caseName);
        return hash;
    }

    /**
     * Default sorting is by ready file creation date, descending
     */
    @Override
    public int compareTo(AutoIngestJob o) {
        return -this.imageFolderCreateDate.compareTo(o.getDateCreated());
    }

    /**
     * Determine if this job object is a higher priority than the otherJob.
     *
     * @param otherJob The job to compare against.
     *
     * @return true if this job is a higher priority, otherwise false.
     */
    boolean isHigherPriorityThan(AutoIngestJob otherJob) {
        if (this.prioritizedFileTimeStamp == null) {
            return false;
        }
        if (otherJob.prioritizedFileTimeStamp == null) {
            return true;
        }
        return (this.prioritizedFileTimeStamp.compareTo(otherJob.prioritizedFileTimeStamp) > 0);
    }

    /**
     * Custom comparator that allows us to sort List<AutoIngestJob> on reverse
     * chronological date modified (descending)
     */
    static class ReverseDateCompletedComparator implements Comparator<AutoIngestJob> {

        @Override
        public int compare(AutoIngestJob o1, AutoIngestJob o2) {
            return -o1.getDateCompleted().compareTo(o2.getDateCompleted());
        }
    }

    /**
     * Custom comparator that allows us to sort List<AutoIngestJob> on reverse
     * chronological date created (descending)
     */
    static class ReverseDateCreatedComparator implements Comparator<AutoIngestJob> {

        @Override
        public int compare(AutoIngestJob o1, AutoIngestJob o2) {
            return -o1.getDateCreated().compareTo(o2.getDateCreated());
        }
    }

    /**
     * Custom comparator that allows us to sort List<AutoIngestJob> on reverse
     * chronological date started (descending)
     */
    static class ReverseDateStartedComparator implements Comparator<AutoIngestJob> {

        @Override
        public int compare(AutoIngestJob o1, AutoIngestJob o2) {
            return -o1.getStatus().getActivityStartDate().compareTo(o2.getStatus().getActivityStartDate());
        }
    }

    /**
     * Custom comparator that sorts the pending list with prioritized cases
     * first, then nonprioritized cases. Prioritized cases are last in, first
     * out. Nonprioritized cases are first in, first out. Prioritized times are
     * from the creation time of the viking.prioritized file. Non prioritized
     * are from the folder creation time.
     */
    public static class PrioritizedPendingListComparator implements Comparator<AutoIngestJob> {

        @Override
        public int compare(AutoIngestJob o1, AutoIngestJob o2) {
            Date dateCreated1 = o1.getDateCreated();
            Date dateCreated2 = o2.getDateCreated();
            Date datePrioritized1 = o1.getPrioritizedFileTimeStamp();
            Date datePrioritized2 = o2.getPrioritizedFileTimeStamp();

            if (datePrioritized1 != null && datePrioritized2 != null) {
                // both are prioritized, sort on prioritized file date, last in first out   
                return datePrioritized2.compareTo(datePrioritized1);
            } else if (datePrioritized1 == null && datePrioritized2 == null) {
                // both are not prioritized, sort on folder creation date, first in first out
                return dateCreated1.compareTo(dateCreated2);
            } else if (datePrioritized1 != null) {
                // left hand side is prioritized
                return -1;
            } else {
                // datePrioritized2 != null, so right hand side is prioritized
                return 1;
            }
        }
    }

    /**
     * Custom comparator that allows us to sort List<AutoIngestJob> on case name
     * alphabetically except for jobs for the current host, which are placed at
     * the top of the list.
     */
    static class AlphabeticalComparator implements Comparator<AutoIngestJob> {

        @Override
        public int compare(AutoIngestJob o1, AutoIngestJob o2) {
            if (o1.getNodeName().equalsIgnoreCase(localHostName)) {
                return -1; // o1 is for current case, float to top
            } else if (o2.getNodeName().equalsIgnoreCase(localHostName)) {
                return 1; // o2 is for current case, float to top
            } else {
                return o1.getCaseName().compareToIgnoreCase(o2.getCaseName());
            }
        }
    }
}
