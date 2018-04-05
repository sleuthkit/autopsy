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

import java.awt.Dimension;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;
import javax.swing.SwingWorker;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.datamodel.EmptyNode;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestNode.AutoIngestJobType;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestNode.JobNode;

/**
 *
 * @author wschaefer
 */
final class AutoIngestJobsPanel extends javax.swing.JPanel implements ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private final org.openide.explorer.view.OutlineView outlineView;
    private final Outline outline;
    private ExplorerManager explorerManager;
    private JobListWorker jobListWorker;
    private final AutoIngestJobType type;

    /**
     * Creates new form PendingJobsPanel
     */
    AutoIngestJobsPanel(AutoIngestJobType jobType) {
        initComponents();
        type = jobType;
        outlineView = new org.openide.explorer.view.OutlineView();
        outline = outlineView.getOutline();
        customize();
    }

    void customize() {

        switch (type) {
            case PENDING_JOB:
                outlineView.setPropertyColumns(Bundle.AutoIngestNode_dataSource_text(), Bundle.AutoIngestNode_dataSource_text(),
                        Bundle.AutoIngestNode_jobCreated_text(), Bundle.AutoIngestNode_jobCreated_text(),
                        Bundle.AutoIngestNode_priority_text(), Bundle.AutoIngestNode_priority_text());
                break;
            case RUNNING_JOB:
                outlineView.setPropertyColumns(Bundle.AutoIngestNode_dataSource_text(), Bundle.AutoIngestNode_dataSource_text(),
                        Bundle.AutoIngestNode_hostName_text(), Bundle.AutoIngestNode_hostName_text(),
                        Bundle.AutoIngestNode_stage_text(), Bundle.AutoIngestNode_stage_text(),
                        Bundle.AutoIngestNode_stageTime_text(), Bundle.AutoIngestNode_stageTime_text());
                break;
            case COMPLETED_JOB:
                outlineView.setPropertyColumns(Bundle.AutoIngestNode_dataSource_text(), Bundle.AutoIngestNode_dataSource_text(),
                        Bundle.AutoIngestNode_jobCreated_text(), Bundle.AutoIngestNode_jobCreated_text(),
                        Bundle.AutoIngestNode_jobCompleted_text(), Bundle.AutoIngestNode_jobCompleted_text(),
                        Bundle.AutoIngestNode_status_text(), Bundle.AutoIngestNode_status_text());
                break;
            default:
        }
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.AutoIngestNode_caseName_text());
        outline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        outline.setRootVisible(false);
        outline.setColumnSorted(0, false, 1);
        if (null == explorerManager) {
            explorerManager = new ExplorerManager();

        }
        outline.setRowSelectionAllowed(false);
        add(outlineView, java.awt.BorderLayout.CENTER);

    }

    @Override
    public void setSize(Dimension d) {
        super.setSize(d);
        outlineView.setMaximumSize(new Dimension(400, 100));
        outline.setPreferredScrollableViewportSize(new Dimension(400, 100));
    }

    void addListSelectionListener(ListSelectionListener listener) {
        outline.getSelectionModel().addListSelectionListener(listener);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    void refresh(AutoIngestMonitor.JobsSnapshot jobsSnapshot) {
        if (jobListWorker == null || jobListWorker.isDone()) {
            outline.setRowSelectionAllowed(false);
//            EmptyNode emptyNode = new EmptyNode("Refreshing...");
//            explorerManager.setRootContext(emptyNode);
            jobListWorker = new JobListWorker(jobsSnapshot, type);
            jobListWorker.execute();
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents

    AutoIngestJob getSelectedAutoIngestJob() {
        Node[] selectedRows = explorerManager.getSelectedNodes();
        if (selectedRows.length == 1) {
            return ((JobNode) selectedRows[0]).getAutoIngestJob();
        }
        return null;
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
    /**
     * Swingworker to fetch the updated List of cases in a background thread
     */
    private class JobListWorker extends SwingWorker<List<AutoIngestJob>, Void> {

        private final AutoIngestMonitor.JobsSnapshot jobsSnapshot;
        private final AutoIngestJobType jobType;

        JobListWorker(AutoIngestMonitor.JobsSnapshot snapshot, AutoIngestJobType type) {
            jobsSnapshot = snapshot;
            jobType = type;
        }

        @Override
        protected List<AutoIngestJob> doInBackground() throws Exception {
            List<AutoIngestJob> jobs;
            switch (jobType) {
                case PENDING_JOB:
                    jobs = jobsSnapshot.getPendingJobs();
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
            jobs.sort(new AutoIngestJob.PriorityComparator());
            return jobs;
        }

        @Override
        protected void done() {
            try {
                List<AutoIngestJob> jobs = get();
                EventQueue.invokeLater(() -> {
                    AutoIngestNode autoIngestNode = new AutoIngestNode(jobs, jobType);
                    explorerManager.setRootContext(autoIngestNode);
                    outline.setRowSelectionAllowed(true);
                });
            } catch (InterruptedException | ExecutionException ex) {
                Exceptions.printStackTrace(ex);
            }

        }
    }

}
