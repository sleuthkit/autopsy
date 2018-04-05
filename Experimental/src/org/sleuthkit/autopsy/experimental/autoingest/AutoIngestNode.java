/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.guiutils.StatusIconCellRenderer;

final class AutoIngestNode extends AbstractNode {

    @Messages({
        "AutoIngestNode.caseName.text=Case Name",
        "AutoIngestNode.dataSource.text=Data Source",
        "AutoIngestNode.hostName.text=Host Name",
        "AutoIngestNode.stage.text=Stage",
        "AutoIngestNode.stageTime.text=Time in Stage",
        "AutoIngestNode.jobCreated.text=Job Created",
        "AutoIngestNode.jobCompleted.text=Job Completed",
        "AutoIngestNode.priority.text=Priority",
        "AutoIngestNode.status.text=Status"            
    })

    AutoIngestNode(List<AutoIngestJob> jobs, AutoIngestJobType type) {
        super(Children.create(new AutoIngestNodeChildren(jobs, type), true));
    }

    static class AutoIngestNodeChildren extends ChildFactory<AutoIngestJob> {

        private final AutoIngestJobType autoIngestJobType;
        private final List<AutoIngestJob> jobs;

        AutoIngestNodeChildren(List<AutoIngestJob> jobList, AutoIngestJobType type) {
            this.jobs = jobList;
            autoIngestJobType = type;
        }

        @Override
        protected boolean createKeys(List<AutoIngestJob> list) {
            if (jobs != null && jobs.size() > 0) {
                list.addAll(jobs);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(AutoIngestJob key) {
            return new JobNode(key, autoIngestJobType);
        }

    }

    /**
     * A node which represents a single multi user case.
     */
    static final class JobNode extends AbstractNode {

        private final AutoIngestJob autoIngestJob;
        private final AutoIngestJobType jobType;

        JobNode(AutoIngestJob job, AutoIngestJobType type) {
            super(Children.LEAF);
            jobType = type;
            autoIngestJob = job;
            super.setName(autoIngestJob.getManifest().getCaseName());
            setName(autoIngestJob.getManifest().getCaseName());
            setDisplayName(autoIngestJob.getManifest().getCaseName());
        }
        
        AutoIngestJob getAutoIngestJob(){
            return autoIngestJob;
        }

        @Override
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }
            ss.put(new NodeProperty<>(Bundle.AutoIngestNode_caseName_text(), Bundle.AutoIngestNode_caseName_text(), Bundle.AutoIngestNode_caseName_text(), autoIngestJob.getManifest().getCaseName()));
            ss.put(new NodeProperty<>(Bundle.AutoIngestNode_dataSource_text(), Bundle.AutoIngestNode_dataSource_text(), Bundle.AutoIngestNode_dataSource_text(), autoIngestJob.getManifest().getDataSourcePath().getFileName().toString()));
            switch (jobType) {
                case PENDING_JOB: 
                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_jobCreated_text(), Bundle.AutoIngestNode_jobCreated_text(), Bundle.AutoIngestNode_jobCreated_text(),  autoIngestJob.getManifest().getDateFileCreated()));
                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_priority_text(), Bundle.AutoIngestNode_priority_text(), Bundle.AutoIngestNode_priority_text(), autoIngestJob.getPriority()));
                    break;
                case RUNNING_JOB:
                    AutoIngestJob.StageDetails status = autoIngestJob.getProcessingStageDetails();
                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_hostName_text(), Bundle.AutoIngestNode_hostName_text(),  Bundle.AutoIngestNode_hostName_text(),autoIngestJob.getProcessingHostName()));
                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_stage_text(), Bundle.AutoIngestNode_stage_text(), Bundle.AutoIngestNode_stage_text(), status.getDescription()));
                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_stageTime_text(), Bundle.AutoIngestNode_stageTime_text(), Bundle.AutoIngestNode_stageTime_text(), (Date.from(Instant.now()).getTime()) - (status.getStartDate().getTime())));
                    break;
                case COMPLETED_JOB:
                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_jobCreated_text(), Bundle.AutoIngestNode_jobCreated_text(), Bundle.AutoIngestNode_jobCreated_text(),  autoIngestJob.getManifest().getDateFileCreated()));
                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_jobCompleted_text(), Bundle.AutoIngestNode_jobCompleted_text(), Bundle.AutoIngestNode_jobCompleted_text(), autoIngestJob.getCompletedDate()));
                    ss.put(new NodeProperty<>(Bundle.AutoIngestNode_status_text(), Bundle.AutoIngestNode_status_text(), Bundle.AutoIngestNode_status_text(), autoIngestJob.getErrorsOccurred() ? StatusIconCellRenderer.Status.WARNING : StatusIconCellRenderer.Status.OK));
                    break;
                default:
            }
            return s;
        }
    }

    enum AutoIngestJobType {
        PENDING_JOB, //NON-NLS
        RUNNING_JOB, //NON-NLS
        COMPLETED_JOB //NON-NLS
    }
}
