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
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.Objects;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.NetworkUtils;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJobData.ProcessingStage;
import org.sleuthkit.autopsy.ingest.IngestJob;

/**
 * An automated ingest job for a manifest. The manifest specifies a co-located
 * data source and a case to which the data source is to be added.
 */
@ThreadSafe
public final class AutoIngestJob implements Comparable<AutoIngestJob>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final String LOCAL_HOST_NAME = NetworkUtils.getLocalHostName();
    private final AutoIngestJobData nodeData;
    @GuardedBy("this")
    transient private DataSourceProcessor dataSourceProcessor;
    @GuardedBy("this")
    transient private IngestJob ingestJob;
    @GuardedBy("this")
    transient private boolean canceled;
    @GuardedBy("this")
    transient private boolean completed;

    /**
     * Constructs an automated ingest job for a manifest. The manifest specifies
     * a co-located data source and a case to which the data source is to be
     * added.
     *
     * Note: Manifest objects will be phased out and no longer be part of the
     * AutoIngestJob class.
     *
     * @param nodeData          The node data.
     */
    AutoIngestJob(AutoIngestJobData nodeData) {
        this.nodeData = nodeData;
    }

    /**
     * Gets the auto ingest job node data.
     *
     * @return The node data.
     */
    AutoIngestJobData getNodeData() {
        return this.nodeData;
    }

    synchronized void setStage(ProcessingStage newStage) {
        setStage(newStage, Date.from(Instant.now()));
    }

    synchronized void setStage(ProcessingStage newStage, Date stateStartedDate) {
        if (ProcessingStage.CANCELING == this.nodeData.getProcessingStage() && ProcessingStage.COMPLETED != newStage) {
            return;
        }
        this.nodeData.setProcessingStage(newStage);
        this.nodeData.setProcessingStageStartDate(stateStartedDate);
    }

    synchronized StageDetails getStageDetails() {
        String description;
        Date startDate;
        ProcessingStage stage = nodeData.getProcessingStage();
        if (ProcessingStage.CANCELING != stage && null != this.ingestJob) {
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
                    description = String.format(ProcessingStage.CANCELING_MODULE.getDisplayText(), ingestModuleHandle.displayName());
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
                description = ProcessingStage.ANALYZING_FILES.getDisplayText();
                startDate = progress.fileIngestStartTime();
            }
        } else {
            description = stage.getDisplayText();
            startDate = this.nodeData.getProcessingStageStartDate();
        }
        return new StageDetails(description, startDate);
    }

    synchronized void setDataSourceProcessor(DataSourceProcessor dataSourceProcessor) {
        this.dataSourceProcessor = dataSourceProcessor;
    }

    synchronized void setIngestJob(IngestJob ingestJob) {
        this.ingestJob = ingestJob;
    }

    synchronized IngestJob getIngestJob() {
        return this.ingestJob;
    }

    synchronized void cancel() {
        setStage(ProcessingStage.CANCELING);
        canceled = true;
        nodeData.setErrorsOccurred(true);
        if (null != dataSourceProcessor) {
            dataSourceProcessor.cancel();
        }
        if (null != ingestJob) {
            ingestJob.cancel(IngestJob.CancellationReason.USER_CANCELLED);
        }
    }

    synchronized boolean isCanceled() {
        return canceled;
    }

    synchronized void setCompleted() {
        setStage(ProcessingStage.COMPLETED);
        completed = true;
    }

    synchronized boolean isCompleted() {
        return completed;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AutoIngestJob)) {
            return false;
        }
        if (obj == this) {
            return true;
        }

        Path manifestPath1 = this.getNodeData().getManifestFilePath();
        Path manifestPath2 = ((AutoIngestJob) obj).getNodeData().getManifestFilePath();

        return manifestPath1.equals(manifestPath2);
    }

    @Override
    public int hashCode() {
        int hash = 71 * (Objects.hashCode(this.nodeData.getCaseDirectoryPath()));
        return hash;
    }

    @Override
    public int compareTo(AutoIngestJob o) {
        Date date1 = this.getNodeData().getManifestFileDate();
        Date date2 = o.getNodeData().getManifestFileDate();

        return -date1.compareTo(date2);
    }

    @Override
    public String toString() {
        return nodeData.getCaseDirectoryPath().toString();
    }
    
    /**
     * Custom comparator that allows us to sort List<AutoIngestJob> on reverse
     * chronological date modified (descending)
     */
    static class ReverseDateCompletedComparator implements Comparator<AutoIngestJob> {

        @Override
        public int compare(AutoIngestJob o1, AutoIngestJob o2) {
            return -o1.getNodeData().getProcessingStageStartDate().compareTo(o2.getNodeData().getProcessingStageStartDate());
        }
    }

    /**
     * Comparator that sorts auto ingest jobs by priority in descending order.
     */
    public static class PriorityComparator implements Comparator<AutoIngestJob> {

        @Override
        public int compare(AutoIngestJob job, AutoIngestJob anotherJob) {
            Integer priority1 = job.getNodeData().getPriority();
            Integer priority2 = anotherJob.getNodeData().getPriority();

            return -priority1.compareTo(priority2);
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
            if (o1.getNodeData().getProcessingHost().equalsIgnoreCase(LOCAL_HOST_NAME)) {
                return -1; // o1 is for current case, float to top
            } else if (o2.getNodeData().getProcessingHost().equalsIgnoreCase(LOCAL_HOST_NAME)) {
                return 1; // o2 is for current case, float to top
            } else {
                String caseName1 = o1.getNodeData().getCaseName();
                String caseName2 = o2.getNodeData().getCaseName();

                return caseName1.compareToIgnoreCase(caseName2);
            }
        }
    }

    @Immutable
    static final class StageDetails {

        private final String description;
        private final Date startDate;

        private StageDetails(String description, Date startDate) {
            this.description = description;
            this.startDate = startDate;
        }

        String getDescription() {
            return this.description;
        }

        Date getStartDate() {
            return this.startDate;
        }

    }

}
