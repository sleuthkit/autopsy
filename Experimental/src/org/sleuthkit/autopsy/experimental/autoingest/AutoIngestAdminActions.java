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
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Level;
import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestMonitor.AutoIngestNodeState;
import org.sleuthkit.autopsy.ingest.IngestProgressSnapshotDialog;

final class AutoIngestAdminActions {

    private static final Logger logger = Logger.getLogger(AutoIngestAdminActions.class.getName());

    static abstract class AutoIngestNodeControlAction extends AbstractAction {

        private final AutoIngestNodeState nodeState;

        AutoIngestNodeControlAction(AutoIngestNodeState nodeState, String title) {
            super(title);
            this.nodeState = nodeState;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (nodeState != null) {
                final AinStatusDashboardTopComponent tc = (AinStatusDashboardTopComponent) WindowManager.getDefault().findTopComponent(AinStatusDashboardTopComponent.PREFERRED_ID);
                if (tc != null) {
                    AinStatusDashboard dashboard = tc.getAinStatusDashboard();
                    if (dashboard != null) {
                        dashboard.getNodesStatusPanel().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        EventQueue.invokeLater(() -> {
                            try {
                                controlAutoIngestNode(dashboard);
                            } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
                                logger.log(Level.WARNING, "Error sending control event to node", ex);
                            } finally {
                                dashboard.getNodesStatusPanel().setCursor(Cursor.getDefaultCursor());
                            }
                        });
                    }
                }
            }
        }

        protected abstract void controlAutoIngestNode(AinStatusDashboard dashboard) throws AutoIngestMonitor.AutoIngestMonitorException;

        AutoIngestNodeState getNodeState() {
            return nodeState;
        }

        @NbBundle.Messages({"AutoIngestAdminActions.pause.title=Pause Node",
            "AutoIngestAdminActions.resume.title=Resume Node"})
        static final class PauseResumeAction extends AutoIngestNodeControlAction {

            private static final long serialVersionUID = 1L;

            PauseResumeAction(AutoIngestNodeState nodeState) {
                super(nodeState, nodeState.getState() == AutoIngestNodeState.State.RUNNING
                        ? Bundle.AutoIngestAdminActions_pause_title() : Bundle.AutoIngestAdminActions_resume_title());
            }

            @Override
            public Object clone() throws CloneNotSupportedException {
                return super.clone(); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            protected void controlAutoIngestNode(AinStatusDashboard dashboard) throws AutoIngestMonitor.AutoIngestMonitorException {
                if (getNodeState().getState() == AutoIngestNodeState.State.RUNNING) {
                    dashboard.getMonitor().pauseAutoIngestNode(getNodeState().getName());
                } else {
                    dashboard.getMonitor().resumeAutoIngestNode(getNodeState().getName());
                }
            }
        }

        @NbBundle.Messages({"AutoIngestAdminActions.shutdown.title=Shutdown Node",
            "AutoIngestAdminActions.shutdown.OK=OK", "AutoIngestAdminActions.shutdown.Cancel=Cancel",
            "AutoIngestAdminActions.shutdown.consequences=This will cancel any currently running job on this host. Exiting while a job is running potentially leaves the case in an inconsistent or corrupted state."})
        static final class ShutdownAction extends AutoIngestNodeControlAction {

            private static final long serialVersionUID = 1L;

            ShutdownAction(AutoIngestNodeState nodeState) {
                super(nodeState, Bundle.AutoIngestAdminActions_shutdown_title());
            }

            @Override
            public Object clone() throws CloneNotSupportedException {
                return super.clone(); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            protected void controlAutoIngestNode(AinStatusDashboard dashboard) throws AutoIngestMonitor.AutoIngestMonitorException {
                Object[] options = {Bundle.AutoIngestAdminActions_shutdown_OK(),
                    Bundle.AutoIngestAdminActions_shutdown_Cancel()
                };

                int reply = JOptionPane.showOptionDialog(dashboard.getNodesStatusPanel(),
                        Bundle.AutoIngestAdminActions_shutdown_consequences(),
                        NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.ConfirmExitHeader"),
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[JOptionPane.NO_OPTION]);

                if (reply == JOptionPane.OK_OPTION) {
                    dashboard.getMonitor().shutdownAutoIngestNode(getNodeState().getName());
                }
            }
        }
    }

    @NbBundle.Messages({"AutoIngestAdminActions.progressDialogAction.title=Ingest Progress"})
    static final class ProgressDialogAction extends AbstractAction {

        private static final long serialVersionUID = 1L;
        private final AutoIngestJob job;

        ProgressDialogAction(AutoIngestJob job) {
            super(Bundle.AutoIngestAdminActions_progressDialogAction_title());
            this.job = job;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final AutoIngestDashboardTopComponent tc = (AutoIngestDashboardTopComponent) WindowManager.getDefault().findTopComponent(AutoIngestDashboardTopComponent.PREFERRED_ID);
            if (tc != null) {
                AutoIngestDashboard dashboard = tc.getAutoIngestDashboard();
                if (dashboard != null) {
                    new IngestProgressSnapshotDialog(dashboard.getTopLevelAncestor(), true, job);
                }
            }
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }

    @NbBundle.Messages({"AutoIngestAdminActions.cancelJobAction.title=Cancel Job"})
    static final class CancelJobAction extends AbstractAction {

        private static final long serialVersionUID = 1L;
        private final AutoIngestJob job;

        CancelJobAction(AutoIngestJob job) {
            super(Bundle.AutoIngestAdminActions_cancelJobAction_title());
            this.job = job;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (job == null) {
                return;
            }

            final AutoIngestDashboardTopComponent tc = (AutoIngestDashboardTopComponent) WindowManager.getDefault().findTopComponent(AutoIngestDashboardTopComponent.PREFERRED_ID);
            if (tc == null) {
                return;
            }

            AutoIngestDashboard dashboard = tc.getAutoIngestDashboard();
            if (dashboard != null) {
                Object[] options = {
                    org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.CancelJob"),
                    org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.DoNotCancelJob")};
                int reply = JOptionPane.showOptionDialog(dashboard.getRunningJobsPanel(),
                        NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.CancelJobAreYouSure"),
                        NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.ConfirmCancellationHeader"),
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[1]);
                if (reply == 0) {
                    /*
                     * Call setCursor on this to ensure it appears (if there is
                     * time to see it).
                     */
                    dashboard.getRunningJobsPanel().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    EventQueue.invokeLater(() -> {
                        dashboard.getMonitor().cancelJob(job);
                        dashboard.getRunningJobsPanel().setCursor(Cursor.getDefaultCursor());
                    });
                }
            }
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }

    @NbBundle.Messages({"AutoIngestAdminActions.cancelModuleAction.title=Cancel Module"})
    static final class CancelModuleAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        CancelModuleAction() {
            super(Bundle.AutoIngestAdminActions_cancelModuleAction_title());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            //TODO JIRA-3738
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }

    @NbBundle.Messages({"AutoIngestAdminActions.reprocessJobAction.title=Reprocess Job",
        "AutoIngestAdminActions.reprocessJobAction.error=Failed to reprocess job"})
    static final class ReprocessJobAction extends AbstractAction {

        private static final long serialVersionUID = 1L;
        private final AutoIngestJob job;

        ReprocessJobAction(AutoIngestJob job) {
            super(Bundle.AutoIngestAdminActions_reprocessJobAction_title());
            this.job = job;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (job == null) {
                return;
            }

            final AutoIngestDashboardTopComponent tc = (AutoIngestDashboardTopComponent) WindowManager.getDefault().findTopComponent(AutoIngestDashboardTopComponent.PREFERRED_ID);
            if (tc == null) {
                return;
            }

            AutoIngestDashboard dashboard = tc.getAutoIngestDashboard();
            if (dashboard != null) {
                /*
                 * Call setCursor on this to ensure it appears (if there is time
                 * to see it).
                 */
                dashboard.getCompletedJobsPanel().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                EventQueue.invokeLater(() -> {
                    try {
                        dashboard.getMonitor().reprocessJob(job);
                        dashboard.refreshTables();
                        dashboard.getCompletedJobsPanel().setCursor(Cursor.getDefaultCursor());
                    } catch (AutoIngestMonitor.AutoIngestMonitorException ex) {
                        logger.log(Level.SEVERE, Bundle.AutoIngestAdminActions_reprocessJobAction_error(), ex);
                        MessageNotifyUtil.Message.error(Bundle.AutoIngestAdminActions_reprocessJobAction_error());
                    } finally {
                        dashboard.getCompletedJobsPanel().setCursor(Cursor.getDefaultCursor());
                    }
                });
            }
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }

    @NbBundle.Messages({"AutoIngestAdminActions.deleteCaseAction.title=Delete Case",
        "AutoIngestAdminActions.deleteCaseAction.error=Failed to delete case."})
    static final class DeleteCaseAction extends AbstractAction {

        private static final long serialVersionUID = 1L;
        private final AutoIngestJob job;

        DeleteCaseAction(AutoIngestJob selectedJob) {
            super(Bundle.AutoIngestAdminActions_deleteCaseAction_title());
            this.job = selectedJob;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (job == null) {
                return;
            }

            final AutoIngestDashboardTopComponent tc = (AutoIngestDashboardTopComponent) WindowManager.getDefault().findTopComponent(AutoIngestDashboardTopComponent.PREFERRED_ID);
            if (tc == null) {
                return;
            }

            AutoIngestDashboard dashboard = tc.getAutoIngestDashboard();
            if (dashboard != null) {
                String caseName = job.getManifest().getCaseName();

                Object[] options = {
                    org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.Delete"),
                    org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.DoNotDelete")
                };
                Object[] msgContent = {org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.DeleteAreYouSure") + "\"" + caseName + "\"?"};
                int reply = JOptionPane.showOptionDialog(dashboard,
                        msgContent,
                        org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "ConfirmationDialog.ConfirmDeletionHeader"),
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        null,
                        options,
                        options[JOptionPane.NO_OPTION]);
                if (reply == JOptionPane.YES_OPTION) {
                    EventQueue.invokeLater(() -> {
                        dashboard.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                        AutoIngestManager.CaseDeletionResult result = dashboard.getMonitor().deleteCase(job);

                        dashboard.getCompletedJobsPanel().refresh(new AutoIngestNodeRefreshEvents.RefreshChildrenEvent(dashboard.getMonitor().getJobsSnapshot()));
                        dashboard.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                        if (AutoIngestManager.CaseDeletionResult.FAILED == result) {
                            JOptionPane.showMessageDialog(dashboard,
                                    String.format("Could not delete case %s. It may be in use.", caseName),
                                    org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.DeletionFailed"),
                                    JOptionPane.INFORMATION_MESSAGE);
                        } else if (AutoIngestManager.CaseDeletionResult.PARTIALLY_DELETED == result) {
                            JOptionPane.showMessageDialog(dashboard,
                                    String.format("Could not fully delete case %s. See log for details.", caseName),
                                    org.openide.util.NbBundle.getMessage(AutoIngestControlPanel.class, "AutoIngestControlPanel.DeletionFailed"),
                                    JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                }
            }
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }

    @NbBundle.Messages({"AutoIngestAdminActions.showCaseLogAction.title=Show Case Log",
        "AutoIngestAdminActions.showCaseLogActionFailed.title=Unable to display case log",
        "AutoIngestAdminActions.showCaseLogActionFailed.message=Case log file does not exist",
        "AutoIngestAdminActions.showCaseLogActionDialog.ok=Okay",
        "AutoIngestAdminActions.showCaseLogActionDialog.cannotFindLog=Unable to find the selected case log file",
        "AutoIngestAdminActions.showCaseLogActionDialog.unableToShowLogFile=Unable to show log file"})
    static final class ShowCaseLogAction extends AbstractAction {

        private static final long serialVersionUID = 1L;
        private final AutoIngestJob job;

        ShowCaseLogAction(AutoIngestJob job) {
            super(Bundle.AutoIngestAdminActions_showCaseLogAction_title());
            this.job = job;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (job == null) {
                return;
            }

            final AutoIngestDashboardTopComponent tc = (AutoIngestDashboardTopComponent) WindowManager.getDefault().findTopComponent(AutoIngestDashboardTopComponent.PREFERRED_ID);
            if (tc == null) {
                return;
            }

            AutoIngestDashboard dashboard = tc.getAutoIngestDashboard();
            if (dashboard != null) {
                try {
                    Path caseDirectoryPath = job.getCaseDirectoryPath();
                    if (null != caseDirectoryPath) {
                        Path pathToLog = AutoIngestJobLogger.getLogPath(caseDirectoryPath);
                        if (pathToLog.toFile().exists()) {
                            Desktop.getDesktop().edit(pathToLog.toFile());
                        } else {
                            JOptionPane.showMessageDialog(dashboard, Bundle.AutoIngestAdminActions_showCaseLogActionFailed_message(),
                                    Bundle.AutoIngestAdminActions_showCaseLogAction_title(), JOptionPane.ERROR_MESSAGE);
                        }
                    } else {
                        MessageNotifyUtil.Message.warn("The case directory for this job has been deleted.");
                    }
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Dashboard error attempting to display case auto ingest log", ex);
                    Object[] options = {Bundle.AutoIngestAdminActions_showCaseLogActionDialog_ok()};
                    JOptionPane.showOptionDialog(dashboard,
                            Bundle.AutoIngestAdminActions_showCaseLogActionDialog_cannotFindLog(),
                            Bundle.AutoIngestAdminActions_showCaseLogActionDialog_unableToShowLogFile(),
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.PLAIN_MESSAGE,
                            null,
                            options,
                            options[0]);
                }
            }
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
