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

abstract class PrioritizationAction extends AbstractAction {

    private static final long serialVersionUID = 1L;
    private final AutoIngestJob job;

    PrioritizationAction(AutoIngestJob selectedJob, String title) {
        super(title);
        job = selectedJob;
    }

    protected abstract void modifyPriority(AutoIngestMonitor monitor, AutoIngestJobsPanel panel) throws AutoIngestMonitor.AutoIngestMonitorException;

    protected abstract String getErrorMessage();

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

    static final class PrioritizeJobAction extends PrioritizationAction {

        private static final long serialVersionUID = 1L;

        PrioritizeJobAction(AutoIngestJob selectedJob) {
            super(selectedJob, "Prioritize Job");
        }

        @Override
        protected void modifyPriority(AutoIngestMonitor monitor, AutoIngestJobsPanel panel) throws AutoIngestMonitor.AutoIngestMonitorException {
            monitor.prioritizeJob(getJob());
            panel.refresh(monitor);
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

    static final class DeprioritizeJobAction extends PrioritizationAction {

        private static final long serialVersionUID = 1L;

        DeprioritizeJobAction(AutoIngestJob selectedJob) {
            super(selectedJob, "Deprioritize Job");
        }

        @Override
        protected void modifyPriority(AutoIngestMonitor monitor, AutoIngestJobsPanel panel) throws AutoIngestMonitor.AutoIngestMonitorException {
            monitor.deprioritizeJob(getJob());
            panel.refresh(monitor);
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

    static final class PrioritizeCaseAction extends PrioritizationAction {

        private static final long serialVersionUID = 1L;

        PrioritizeCaseAction(AutoIngestJob selectedJob) {
            super(selectedJob, "Prioritize Case");
        }

        @Override
        protected void modifyPriority(AutoIngestMonitor monitor, AutoIngestJobsPanel panel) throws AutoIngestMonitor.AutoIngestMonitorException {
            monitor.prioritizeCase(getJob().getManifest().getCaseName());
            panel.refresh(monitor);
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

    static final class DeprioritizeCaseAction extends PrioritizationAction {

        private static final long serialVersionUID = 1L;

        DeprioritizeCaseAction(AutoIngestJob selectedJob) {
            super(selectedJob, "Deprioritize Case");
        }

        @Override
        protected void modifyPriority(AutoIngestMonitor monitor, AutoIngestJobsPanel panel) throws AutoIngestMonitor.AutoIngestMonitorException {
            monitor.deprioritizeCase(getJob().getManifest().getCaseName());
            panel.refresh(monitor);
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
