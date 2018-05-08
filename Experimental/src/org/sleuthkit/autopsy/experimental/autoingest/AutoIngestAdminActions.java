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

import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import org.openide.util.NbBundle;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.ingest.IngestProgressSnapshotDialog;

final class AutoIngestAdminActions {

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

    @NbBundle.Messages({"AutoIngestAdminActions.pause.title=Pause Node"})
    static final class PauseAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        PauseAction() {
            super(Bundle.AutoIngestAdminActions_pause_title());
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

    @NbBundle.Messages({"AutoIngestAdminActions.resume.title=Resume Node"})
    static final class ResumeAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        ResumeAction() {
            super(Bundle.AutoIngestAdminActions_resume_title());
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

    @NbBundle.Messages({"AutoIngestAdminActions.shutdown.title=Shutdown Node"})
    static final class ShutdownAction extends AbstractAction {

        private static final long serialVersionUID = 1L;

        ShutdownAction() {
            super(Bundle.AutoIngestAdminActions_shutdown_title());
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
