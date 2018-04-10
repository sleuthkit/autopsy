/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;

/**
 * Abstract actions which are for the modification of AutoIngestJob or Case
 * priority.
 */
abstract class PrioritizationAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
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
     * @param panel   - the AutoIngestJobsPanel which will need to be updated
     *                after the priority is modified
     *
     * @throws
     * org.sleuthkit.autopsy.experimental.autoingest.AutoIngestMonitor.AutoIngestMonitorException
     */
    protected abstract void modifyPriority(AutoIngestMonitor monitor, AutoIngestJobsPanel panel) throws AutoIngestMonitor.AutoIngestMonitorException;

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
                tc.getPendingJobsPanel().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                EventQueue.invokeLater(() -> {
                    try {
                        AutoIngestMonitor monitor = tc.getAutoIngestMonitor();
                        AutoIngestJobsPanel pendingPanel = tc.getPendingJobsPanel();
                        if (monitor != null && pendingPanel != null) {
                            modifyPriority(monitor, pendingPanel);
                        }
                    } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
                        String errorMessage = getErrorMessage();
                        //     LOGGER.log(Level.SEVERE, errorMessage, ex);
                        MessageNotifyUtil.Message.error(errorMessage);
                    } finally {
                        tc.getPendingJobsPanel().setCursor(Cursor.getDefaultCursor());
                    }
                });
            }
        }
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone(); //To change body of generated methods, choose Tools | Templates.
    }

    /**
     * Action to prioritize the specified AutoIngestJob
     */
    static final class PrioritizeJobAction extends PrioritizationAction {

        private static final long serialVersionUID = 1L;

        /**
         * Construct a new PrioritizeJobAction
         *
         * @param selectedJob - the AutoIngestJob to be prioritized
         */
        PrioritizeJobAction(AutoIngestJob selectedJob) {
            super(selectedJob, "Prioritize Job");
        }

        @Override
        protected void modifyPriority(AutoIngestMonitor monitor, AutoIngestJobsPanel panel) throws AutoIngestMonitor.AutoIngestMonitorException {
            monitor.prioritizeJob(getJob());
            panel.refresh(monitor.getJobsSnapshot());
        }

        @Override
        protected String getErrorMessage() {
            return String.format(Bundle.AutoIngestDashboard_errorMessage_jobPrioritization(), getJob().getManifest().getFilePath());
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }

    /**
     * Action to deprioritize the specified AutoIngestJob
     */
    static final class DeprioritizeJobAction extends PrioritizationAction {

        private static final long serialVersionUID = 1L;

        /**
         * Construct a new DeprioritizeJobAction
         *
         * @param selectedJob - the AutoIngestJob to be deprioritized
         */
        DeprioritizeJobAction(AutoIngestJob selectedJob) {
            super(selectedJob, "Deprioritize Job");
        }

        @Override
        protected void modifyPriority(AutoIngestMonitor monitor, AutoIngestJobsPanel panel) throws AutoIngestMonitor.AutoIngestMonitorException {
            monitor.deprioritizeJob(getJob());
            panel.refresh(monitor.getJobsSnapshot());
        }

        @Override
        protected String getErrorMessage() {
            return String.format(Bundle.AutoIngestDashboard_errorMessage_jobDeprioritization(), getJob().getManifest().getFilePath());
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }

    /**
     * Action to prioritize all jobs for the case which the specified
     * AutoIngestJob is a part of.
     */
    static final class PrioritizeCaseAction extends PrioritizationAction {

        private static final long serialVersionUID = 1L;

        /**
         * Construct a new PrioritizeCaseAction
         *
         * @param selectedJob - the AutoIngestJob which should have it's case
         *                    prioritized
         */
        PrioritizeCaseAction(AutoIngestJob selectedJob) {
            super(selectedJob, "Prioritize Case");
        }

        @Override
        protected void modifyPriority(AutoIngestMonitor monitor, AutoIngestJobsPanel panel) throws AutoIngestMonitor.AutoIngestMonitorException {
            monitor.prioritizeCase(getJob().getManifest().getCaseName());
            panel.refresh(monitor.getJobsSnapshot());
        }

        @Override
        protected String getErrorMessage() {
            return String.format(Bundle.AutoIngestDashboard_errorMessage_casePrioritization(), getJob().getManifest().getCaseName());
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }

    /**
     * Action to deprioritize all jobs for the case which the specified
     * AutoIngestJob is a part of.
     */
    static final class DeprioritizeCaseAction extends PrioritizationAction {

        private static final long serialVersionUID = 1L;

        /**
         * Construct a new DeprioritizeCaseAction
         *
         * @param selectedJob - the AutoIngestJob which should have it's case
         *                    deprioritized
         */
        DeprioritizeCaseAction(AutoIngestJob selectedJob) {
            super(selectedJob, "Deprioritize Case");
        }

        @Override
        protected void modifyPriority(AutoIngestMonitor monitor, AutoIngestJobsPanel panel) throws AutoIngestMonitor.AutoIngestMonitorException {
            monitor.deprioritizeCase(getJob().getManifest().getCaseName());
            panel.refresh(monitor.getJobsSnapshot());
        }

        @Override
        protected String getErrorMessage() {
            return String.format(Bundle.AutoIngestDashboard_errorMessage_caseDeprioritization(), getJob().getManifest().getCaseName());
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
