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

import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * Abstract actions which are for the modification of AutoIngestJob or Case
 * priority.
 */
abstract class PrioritizationAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(PrioritizationAction.class.getName());
    private final AutoIngestJob job;

    
    /**
     * Construct a new Prioritization action for the selected job
     *
     * @param selectedJob The job which will be used to determine what has it's
     *                    priority modified
     * @param title       - the string to represent the action in menus
     */
    PrioritizationAction(AutoIngestJob selectedJob, String title) {
        super(title);
        job = selectedJob;
    }

    /**
     * The implementation specific method which modifies job or case priority
     *
     * @param monitor - the AutoIngestMonitor which can be accessed to change
     *                the job or case priority
     *
     * @throws
     * org.sleuthkit.autopsy.experimental.autoingest.AutoIngestMonitor.AutoIngestMonitorException
     */
    protected abstract void modifyPriority(AutoIngestMonitor monitor) throws AutoIngestMonitor.AutoIngestMonitorException;

    /**
     * Get the implementation specific error message for if modifyPriority fails
     *
     * @return the error message for the current implementation
     */
    protected abstract String getErrorMessage();

    /**
     * Gets the job this action is constructed for
     *
     * @return job - the AutoIngestJob
     */
    protected AutoIngestJob getJob() {
        return job;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (job != null) {
            final AutoIngestDashboardTopComponent tc = (AutoIngestDashboardTopComponent) WindowManager.getDefault().findTopComponent(AutoIngestDashboardTopComponent.PREFERRED_ID);
            if (tc != null) {
                AutoIngestDashboard dashboard = tc.getAutoIngestDashboard();
                if (dashboard != null) {
                    dashboard.getPendingJobsPanel().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    EventQueue.invokeLater(() -> {
                        try {
                            modifyPriority(dashboard.getMonitor());
                            dashboard.getPendingJobsPanel().refresh(getRefreshEvent(dashboard.getMonitor()));
                        } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
                            String errorMessage = getErrorMessage();
                            logger.log(Level.SEVERE, errorMessage, ex);
                            MessageNotifyUtil.Message.error(errorMessage);
                        } finally {
                            dashboard.getPendingJobsPanel().setCursor(Cursor.getDefaultCursor());
                        }
                    });
                }
            }
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone(); //To change body of generated methods, choose Tools | Templates.
    }

    abstract AutoIngestNodeRefreshEvents.AutoIngestRefreshEvent getRefreshEvent(AutoIngestMonitor monitor);

    /**
     * Action to prioritize the specified AutoIngestJob
     */
    @Messages({"PrioritizationAction.prioritizeJobAction.title=Prioritize Job",
        "PrioritizationAction.prioritizeJobAction.error=Failed to prioritize job \"%s\"."})
    static final class PrioritizeJobAction extends PrioritizationAction {

        private static final long serialVersionUID = 1L;

        /**
         * Construct a new PrioritizeJobAction
         *
         * @param selectedJob - the AutoIngestJob to be prioritized
         */
        PrioritizeJobAction(AutoIngestJob selectedJob) {
            super(selectedJob, Bundle.PrioritizationAction_prioritizeJobAction_title());
        }

        @Override
        protected void modifyPriority(AutoIngestMonitor monitor) throws AutoIngestMonitor.AutoIngestMonitorException {
            monitor.prioritizeJob(getJob());
        }

        @Override
        protected String getErrorMessage() {
            return String.format(Bundle.PrioritizationAction_prioritizeJobAction_error(), getJob().getManifest().getFilePath());
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        AutoIngestNodeRefreshEvents.AutoIngestRefreshEvent getRefreshEvent(AutoIngestMonitor monitor) {
            return new AutoIngestNodeRefreshEvents.RefreshJobEvent(monitor, getJob());
        }
    }

    /**
     * Action to deprioritize the specified AutoIngestJob
     */
    @Messages({"PrioritizationAction.deprioritizeJobAction.title=Deprioritize Job",
        "PrioritizationAction.deprioritizeJobAction.error=Failed to deprioritize job \"%s\"."})
    static final class DeprioritizeJobAction extends PrioritizationAction {

        private static final long serialVersionUID = 1L;

        /**
         * Construct a new DeprioritizeJobAction
         *
         * @param selectedJob - the AutoIngestJob to be deprioritized
         */
        DeprioritizeJobAction(AutoIngestJob selectedJob) {
            super(selectedJob, Bundle.PrioritizationAction_deprioritizeJobAction_title());
        }

        @Override
        protected void modifyPriority(AutoIngestMonitor monitor) throws AutoIngestMonitor.AutoIngestMonitorException {
            monitor.deprioritizeJob(getJob());
        }

        @Override
        protected String getErrorMessage() {
            return String.format(Bundle.PrioritizationAction_deprioritizeJobAction_error(), getJob().getManifest().getFilePath());
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        AutoIngestNodeRefreshEvents.AutoIngestRefreshEvent getRefreshEvent(AutoIngestMonitor monitor) {
            return new AutoIngestNodeRefreshEvents.RefreshJobEvent(monitor, getJob());
        }
    }

    /**
     * Action to prioritize all jobs for the case which the specified
     * AutoIngestJob is a part of.
     */
    @Messages({"PrioritizationAction.prioritizeCaseAction.title=Prioritize Case",
        "PrioritizationAction.prioritizeCaseAction.error=Failed to prioritize case \"%s\"."})
    static final class PrioritizeCaseAction extends PrioritizationAction {

        private static final long serialVersionUID = 1L;

        /**
         * Construct a new PrioritizeCaseAction
         *
         * @param selectedJob - the AutoIngestJob which should have it's case
         *                    prioritized
         */
        PrioritizeCaseAction(AutoIngestJob selectedJob) {
            super(selectedJob, Bundle.PrioritizationAction_prioritizeCaseAction_title());
        }

        @Override
        protected void modifyPriority(AutoIngestMonitor monitor) throws AutoIngestMonitor.AutoIngestMonitorException {
            monitor.prioritizeCase(getJob().getManifest().getCaseName());
        }

        @Override
        protected String getErrorMessage() {
            return String.format(Bundle.PrioritizationAction_prioritizeCaseAction_error(), getJob().getManifest().getCaseName());
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        AutoIngestNodeRefreshEvents.AutoIngestRefreshEvent getRefreshEvent(AutoIngestMonitor monitor) {
            return new AutoIngestNodeRefreshEvents.RefreshCaseEvent(monitor, getJob().getManifest().getCaseName());
        }
    }

    /**
     * Action to deprioritize all jobs for the case which the specified
     * AutoIngestJob is a part of.
     */
    @Messages({"PrioritizationAction.deprioritizeCaseAction.title=Deprioritize Case",
        "PrioritizationAction.deprioritizeCaseAction.error=Failed to deprioritize case \"%s\"."})
    static final class DeprioritizeCaseAction extends PrioritizationAction {

        private static final long serialVersionUID = 1L;

        /**
         * Construct a new DeprioritizeCaseAction
         *
         * @param selectedJob - the AutoIngestJob which should have it's case
         *                    deprioritized
         */
        DeprioritizeCaseAction(AutoIngestJob selectedJob) {
            super(selectedJob, Bundle.PrioritizationAction_deprioritizeCaseAction_title());
        }

        @Override
        protected void modifyPriority(AutoIngestMonitor monitor) throws AutoIngestMonitor.AutoIngestMonitorException {
            monitor.deprioritizeCase(getJob().getManifest().getCaseName());
        }

        @Override
        protected String getErrorMessage() {
            return String.format(Bundle.PrioritizationAction_deprioritizeCaseAction_error(), getJob().getManifest().getCaseName());
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        AutoIngestNodeRefreshEvents.AutoIngestRefreshEvent getRefreshEvent(AutoIngestMonitor monitor) {
            return new AutoIngestNodeRefreshEvents.RefreshCaseEvent(monitor, getJob().getManifest().getCaseName());
        }
    }
}
