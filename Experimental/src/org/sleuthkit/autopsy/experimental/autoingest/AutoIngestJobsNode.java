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

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import javax.swing.Action;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datamodel.NodeProperty;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestMonitor.JobsSnapshot;
import org.sleuthkit.autopsy.guiutils.DurationCellRenderer;
import org.sleuthkit.autopsy.guiutils.StatusIconCellRenderer;

/**
 * A node which represents all AutoIngestJobs of a given AutoIngestJobStatus.
 * Each job with the specified status will have a child node representing it.
 */
final class AutoIngestJobsNode extends AbstractNode {
    
    //Event bus is non static so that each instance of this will only listen to events sent to that instance
    private final EventBus refreshChildrenEventBus;

    @Messages({
        "AutoIngestJobsNode.caseName.text=Case Name",
        "AutoIngestJobsNode.dataSource.text=Data Source",
        "AutoIngestJobsNode.hostName.text=Host Name",
        "AutoIngestJobsNode.stage.text=Stage",
        "AutoIngestJobsNode.stageTime.text=Time in Stage",
        "AutoIngestJobsNode.jobCreated.text=Job Created",
        "AutoIngestJobsNode.jobCompleted.text=Job Completed",
        "AutoIngestJobsNode.priority.text=Prioritized",
        "AutoIngestJobsNode.status.text=Status"
    })

    /**
     * Construct a new AutoIngestJobsNode.
     */
    AutoIngestJobsNode(AutoIngestJobStatus status, EventBus eventBus) {
        super(Children.create(new AutoIngestNodeChildren(status, eventBus), false));
        refreshChildrenEventBus = eventBus;
    }

    /**
     * Refresh the contents of the AutoIngestJobsNode and all of its children.
     */
    void refresh(AutoIngestNodeRefreshEvents.AutoIngestRefreshEvent refreshEvent) {
        refreshChildrenEventBus.post(refreshEvent);
    }

    /**
     * A ChildFactory for generating JobNodes.
     */
    static final class AutoIngestNodeChildren extends ChildFactory<AutoIngestJob> {

        private final AutoIngestJobStatus autoIngestJobStatus;
        private JobsSnapshot jobsSnapshot;
        private final RefreshChildrenSubscriber refreshChildrenSubscriber = new RefreshChildrenSubscriber();
        private final EventBus refreshEventBus;

        /**
         * Create children nodes for the AutoIngestJobsNode which will each
         * represent a single AutoIngestJob
         *
         * @param snapshot the snapshot which contains the AutoIngestJobs
         * @param status   the status of the jobs being displayed
         */
        AutoIngestNodeChildren(AutoIngestJobStatus status, EventBus eventBus) {
            jobsSnapshot = new JobsSnapshot();
            autoIngestJobStatus = status;
            refreshEventBus = eventBus;
            refreshChildrenSubscriber.register(refreshEventBus);
        }

        @Override
        protected boolean createKeys(List<AutoIngestJob> list) {
            List<AutoIngestJob> jobs;
            switch (autoIngestJobStatus) {
                case PENDING_JOB:
                    jobs = jobsSnapshot.getPendingJobs();
                    Collections.sort(jobs);
                    break;
                case RUNNING_JOB:
                    jobs = jobsSnapshot.getRunningJobs();
                    break;
                case COMPLETED_JOB:
                    jobs = jobsSnapshot.getCompletedJobs();
                    break;
                default:
                    jobs = new ArrayList<>();
            }
            if (jobs != null && jobs.size() > 0) {
                list.addAll(jobs);
            }
            return true;
        }

        @Override
        protected Node createNodeForKey(AutoIngestJob key) {
            return new JobNode(key, autoIngestJobStatus, refreshEventBus);
        }

        /**
         * Class which registers with EventBus and causes child nodes which
         * exist to be refreshed.
         */
        private class RefreshChildrenSubscriber {

            /**
             * Construct a RefreshChildrenSubscriber
             */
            private RefreshChildrenSubscriber() {
            }

            /**
             * Registers this subscriber with the specified EventBus to receive
             * events posted to it.
             *
             * @param eventBus - the EventBus to register this subscriber to
             */
            private void register(EventBus eventBus) {
                eventBus.register(this);
            }

            /**
             * Receive events which implement the AutoIngestRefreshEvent
             * interface from the EventBus which this class is registered to,
             * and refresh the children created by this factory.
             *
             *
             * @param refreshEvent the AutoIngestRefreshEvent which was received
             */
            @Subscribe
            private void subscribeToRefresh(AutoIngestNodeRefreshEvents.AutoIngestRefreshEvent refreshEvent) {
                //Ignore netbeans suggesting this isn't being used, it is used behind the scenes by the EventBus
                //RefreshChildrenEvents can change which children are present however
                //RefreshJobEvents and RefreshCaseEvents can still change the order we want to display them in
                jobsSnapshot = refreshEvent.getJobsSnapshot();
                refresh(true);
            }

        }

    }

    /**
     * A node which represents a single auto ingest job.
     */
    static final class JobNode extends AbstractNode {

        private final AutoIngestJob autoIngestJob;
        private final AutoIngestJobStatus jobStatus;
        private final RefreshNodeSubscriber refreshNodeSubscriber = new RefreshNodeSubscriber();

        /**
         * Construct a new JobNode to represent an AutoIngestJob and its status.
         *
         * @param job    - the AutoIngestJob being represented by this node
         * @param status - the current status of the AutoIngestJob being
         *               represented
         */
        JobNode(AutoIngestJob job, AutoIngestJobStatus status, EventBus eventBus) {
            super(Children.LEAF);
            jobStatus = status;
            autoIngestJob = job;
            setName(autoIngestJob.toString());  //alows job to be uniquely found by name since it will involve a hash of the AutoIngestJob
            setDisplayName(autoIngestJob.getManifest().getCaseName()); //displays user friendly case name as name
            refreshNodeSubscriber.register(eventBus);
        }

        /**
         * Get the AutoIngestJob which this node represents.
         *
         * @return autoIngestJob
         */
        AutoIngestJob getAutoIngestJob() {
            return autoIngestJob;
        }

        @Override
        @Messages({"AutoIngestJobsNode.prioritized.true=Yes",
            "AutoIngestJobsNode.prioritized.false=No"
        })
        protected Sheet createSheet() {
            Sheet s = super.createSheet();
            Sheet.Set ss = s.get(Sheet.PROPERTIES);
            if (ss == null) {
                ss = Sheet.createPropertiesSet();
                s.put(ss);
            }
            ss.put(new NodeProperty<>(Bundle.AutoIngestJobsNode_caseName_text(), Bundle.AutoIngestJobsNode_caseName_text(), Bundle.AutoIngestJobsNode_caseName_text(),
                    autoIngestJob.getManifest().getCaseName()));
            ss.put(new NodeProperty<>(Bundle.AutoIngestJobsNode_dataSource_text(), Bundle.AutoIngestJobsNode_dataSource_text(), Bundle.AutoIngestJobsNode_dataSource_text(),
                    autoIngestJob.getManifest().getDataSourcePath().getFileName().toString()));
            switch (jobStatus) {
                case PENDING_JOB:
                    ss.put(new NodeProperty<>(Bundle.AutoIngestJobsNode_jobCreated_text(), Bundle.AutoIngestJobsNode_jobCreated_text(), Bundle.AutoIngestJobsNode_jobCreated_text(),
                            autoIngestJob.getManifest().getDateFileCreated()));
                    ss.put(new NodeProperty<>(Bundle.AutoIngestJobsNode_priority_text(), Bundle.AutoIngestJobsNode_priority_text(), Bundle.AutoIngestJobsNode_priority_text(),
                            autoIngestJob.getPriority() > 0 ? Bundle.AutoIngestJobsNode_prioritized_true() : Bundle.AutoIngestJobsNode_prioritized_false()));
                    break;
                case RUNNING_JOB:
                    AutoIngestJob.StageDetails status = autoIngestJob.getProcessingStageDetails();
                    ss.put(new NodeProperty<>(Bundle.AutoIngestJobsNode_hostName_text(), Bundle.AutoIngestJobsNode_hostName_text(), Bundle.AutoIngestJobsNode_hostName_text(),
                            autoIngestJob.getProcessingHostName()));
                    ss.put(new NodeProperty<>(Bundle.AutoIngestJobsNode_stage_text(), Bundle.AutoIngestJobsNode_stage_text(), Bundle.AutoIngestJobsNode_stage_text(),
                            status.getDescription()));
                    ss.put(new NodeProperty<>(Bundle.AutoIngestJobsNode_stageTime_text(), Bundle.AutoIngestJobsNode_stageTime_text(), Bundle.AutoIngestJobsNode_stageTime_text(),
                            DurationCellRenderer.longToDurationString((Date.from(Instant.now()).getTime()) - (status.getStartDate().getTime()))));
                    break;
                case COMPLETED_JOB:
                    ss.put(new NodeProperty<>(Bundle.AutoIngestJobsNode_jobCreated_text(), Bundle.AutoIngestJobsNode_jobCreated_text(), Bundle.AutoIngestJobsNode_jobCreated_text(),
                            autoIngestJob.getManifest().getDateFileCreated()));
                    ss.put(new NodeProperty<>(Bundle.AutoIngestJobsNode_jobCompleted_text(), Bundle.AutoIngestJobsNode_jobCompleted_text(), Bundle.AutoIngestJobsNode_jobCompleted_text(),
                            autoIngestJob.getCompletedDate()));
                    ss.put(new NodeProperty<>(Bundle.AutoIngestJobsNode_status_text(), Bundle.AutoIngestJobsNode_status_text(), Bundle.AutoIngestJobsNode_status_text(),
                            autoIngestJob.getErrorsOccurred() ? StatusIconCellRenderer.Status.WARNING : StatusIconCellRenderer.Status.OK));
                    break;
                default:
            }
            return s;
        }

        @Override
        public Action[] getActions(boolean context) {
            List<Action> actions = new ArrayList<>();
            if (AutoIngestDashboard.isAdminAutoIngestDashboard()) {
                switch (jobStatus) {
                    case PENDING_JOB:
                        actions.add(new PrioritizationAction.PrioritizeJobAction(autoIngestJob));
                        actions.add(new PrioritizationAction.PrioritizeCaseAction(autoIngestJob));
                        PrioritizationAction.DeprioritizeJobAction deprioritizeJobAction = new PrioritizationAction.DeprioritizeJobAction(autoIngestJob);
                        deprioritizeJobAction.setEnabled(autoIngestJob.getPriority() > 0);
                        actions.add(deprioritizeJobAction);
                        PrioritizationAction.DeprioritizeCaseAction deprioritizeCaseAction = new PrioritizationAction.DeprioritizeCaseAction(autoIngestJob);
                        deprioritizeCaseAction.setEnabled(autoIngestJob.getPriority() > 0);
                        actions.add(deprioritizeCaseAction);
                        break;
                    case RUNNING_JOB:
                        actions.add(new AutoIngestAdminActions.ProgressDialogAction(autoIngestJob));
                        actions.add(new AutoIngestAdminActions.CancelJobAction(autoIngestJob));
//                        actions.add(new AutoIngestAdminActions.CancelModuleAction());
                        break;
                    case COMPLETED_JOB:
                        actions.add(new AutoIngestAdminActions.ReprocessJobAction(autoIngestJob));
                        actions.add(new AutoIngestAdminActions.DeleteCaseAction(autoIngestJob));
                        actions.add(new AutoIngestAdminActions.ShowCaseLogAction(autoIngestJob));
                        break;
                    default:
                }
            }
            return actions.toArray(new Action[actions.size()]);
        }

        /**
         * Class which registers with EventBus and causes specific nodes to have
         * their properties to be refreshed.
         */
        private class RefreshNodeSubscriber {

            /**
             * Constructs a RefreshNodeSubscriber
             */
            private RefreshNodeSubscriber() {
            }

            /**
             * Registers this subscriber with the specified EventBus to receive
             * events posted to it.
             *
             * @param eventBus - the EventBus to register this subscriber to
             */
            private void register(EventBus eventBus) {
                eventBus.register(this);
            }

            /**
             * Receive events of type RefreshJobEvent from the EventBus which
             * this class is registered to and refresh the nodes properties if
             * it is the node for the job specified in the event.
             *
             * @param refreshEvent the RefreshJobEvent which was received
             */
            @Subscribe
            private void subscribeToRefreshJob(AutoIngestNodeRefreshEvents.RefreshJobEvent refreshEvent) {
                //Ignore netbeans suggesting this isn't being used, it is used behind the scenes by the EventBus
                if (getAutoIngestJob().equals(refreshEvent.getJobToRefresh())) {
                    setSheet(createSheet());
                }
            }

            /**
             * Receive events of type RefreshCaseEvent from the EventBus which
             * this class is registered to and refresh the nodes which have jobs
             * which are members of case specified in the event.
             *
             * @param refreshEvent the RefreshCaseEvent which was received
             */
            @Subscribe
            private void subscribeToRefreshCase(AutoIngestNodeRefreshEvents.RefreshCaseEvent refreshEvent) {
                //Ignore netbeans suggesting this isn't being used, it is used behind the scenes by the EventBus
                if (getAutoIngestJob().getManifest().getCaseName().equals(refreshEvent.getCaseToRefresh())) {
                    setSheet(createSheet());
                }
            }

            /**
             * Refresh the properties of all running jobs anytime a
             * RefreshChildrenEvent is received so that stages and times stay up
             * to date.
             *
             * @param refreshEvent - the RefreshChildrenEvent which was received
             */
            @Subscribe
            private void subscribeToRefreshChildren(AutoIngestNodeRefreshEvents.RefreshChildrenEvent refreshEvent) {
                //Ignore netbeans suggesting this isn't being used, it is used behind the scenes by the EventBus
                if (jobStatus == AutoIngestJobStatus.RUNNING_JOB) {
                    setSheet(createSheet());
                }
            }

        }
    }

    /**
     * An enumeration used to indicate the current status of an auto ingest job
     * node.
     */
    enum AutoIngestJobStatus {
        PENDING_JOB, //NON-NLS
        RUNNING_JOB, //NON-NLS
        COMPLETED_JOB //NON-NLS
    }
}
