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
import javax.swing.JOptionPane;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestMonitor.AutoIngestNodeState;
import org.sleuthkit.autopsy.ingest.IngestProgressSnapshotDialog;

final class AutoIngestAdminActions {

    static abstract class AutoIngestNodeControlAction extends AbstractAction {

        private final AutoIngestNodeState nodeState;
        private final Logger logger = Logger.getLogger(AutoIngestNodeControlAction.class.getName());

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

        ProgressDialogAction() {
            super(Bundle.AutoIngestAdminActions_progressDialogAction_title());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            //TODO JIRA-3734
            final AutoIngestDashboardTopComponent tc = (AutoIngestDashboardTopComponent) WindowManager.getDefault().findTopComponent(AutoIngestDashboardTopComponent.PREFERRED_ID);
            if (tc != null) {
                AutoIngestDashboard dashboard = tc.getAutoIngestDashboard();
                if (dashboard != null) {
                    new IngestProgressSnapshotDialog(dashboard.getTopLevelAncestor(), true);
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

        CancelJobAction() {
            super(Bundle.AutoIngestAdminActions_cancelJobAction_title());
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

    @NbBundle.Messages({"AutoIngestAdminActions.reprocessJobAction.title=Reprocess Job"})
    static final class ReprocessJobAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        ReprocessJobAction() {
            super(Bundle.AutoIngestAdminActions_reprocessJobAction_title());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            //TODO JIRA-3739
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }

    @NbBundle.Messages({"AutoIngestAdminActions.deleteCaseAction.title=Delete Case"})
    static final class DeleteCaseAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        DeleteCaseAction() {
            super(Bundle.AutoIngestAdminActions_deleteCaseAction_title());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            //TODO JIRA-3740
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }

    @NbBundle.Messages({"AutoIngestAdminActions.showCaseLogAction.title=Show Case Log"})
    static final class ShowCaseLogAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        ShowCaseLogAction() {
            super(Bundle.AutoIngestAdminActions_showCaseLogAction_title());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            //TODO JIRA-
        }

        @Override
        public Object clone() throws CloneNotSupportedException {
            return super.clone(); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
