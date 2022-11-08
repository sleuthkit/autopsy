/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2021 Basis Technology Corp.
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
import java.awt.Dimension;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.datamodel.EmptyNode;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJobsNode.AutoIngestJobStatus;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestJobsNode.JobNode;
import org.sleuthkit.autopsy.experimental.autoingest.AutoIngestNodeRefreshEvents.AutoIngestRefreshEvent;
import org.sleuthkit.autopsy.guiutils.DurationCellRenderer;
import org.sleuthkit.autopsy.guiutils.StatusIconCellRenderer;

/**
 * A panel which displays an outline view with all jobs for a specified status.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class AutoIngestJobsPanel extends javax.swing.JPanel implements ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private static final int INITIAL_CASENAME_WIDTH = 170;
    private static final int INITIAL_DATASOURCE_WIDTH = 270;
    private static final int INITIAL_PRIORITIZED_WIDTH = 20;
    private static final int INITIAL_OCR_WIDTH = 20;
    private static final int INITIAL_STATUS_WIDTH = 20;
    private static final int INVALID_INDEX = -1;
    private final org.openide.explorer.view.OutlineView outlineView;
    private final Outline outline;
    private ExplorerManager explorerManager;
    private final AutoIngestJobStatus status;

    /**
     * Creates a new AutoIngestJobsPanel of the specified jobStatus
     *
     * @param jobStatus the status of the jbos to be displayed on this panel
     */
    AutoIngestJobsPanel(AutoIngestJobStatus jobStatus) {
        initComponents();
        status = jobStatus;
        outlineView = new org.openide.explorer.view.OutlineView();
        outline = outlineView.getOutline();
        customize();
    }

    

    /**
     * Set up the AutoIngestJobsPanel's so that its outlineView is displaying
     * the correct columns for the specified AutoIngestJobStatus
     */
    @Messages({"AutoIngestJobsPanel.waitNode.text=Please Wait..."})
    void customize() {
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.AutoIngestJobsNode_caseName_text());
        outline.setRowSelectionAllowed(false); //rows will be made selectable after table has been populated
        outline.setFocusable(false);  //table will be made focusable after table has been populated
        if (null == explorerManager) {
            explorerManager = new ExplorerManager();
        }
        explorerManager.setRootContext(new EmptyNode(Bundle.AutoIngestJobsPanel_waitNode_text()));
        int indexOfColumn;
        switch (status) {
            case PENDING_JOB:
                outlineView.setPropertyColumns(Bundle.AutoIngestJobsNode_dataSource_text(), Bundle.AutoIngestJobsNode_dataSource_text(),
                        Bundle.AutoIngestJobsNode_jobCreated_text(), Bundle.AutoIngestJobsNode_jobCreated_text(),
                        Bundle.AutoIngestJobsNode_priority_text(), Bundle.AutoIngestJobsNode_priority_text(),
                        Bundle.AutoIngestJobsNode_ocr_text(), Bundle.AutoIngestJobsNode_ocr_text());
                indexOfColumn = getColumnIndexByName(Bundle.AutoIngestJobsNode_priority_text());
                if (indexOfColumn != INVALID_INDEX) {
                    outline.getColumnModel().getColumn(indexOfColumn).setPreferredWidth(INITIAL_PRIORITIZED_WIDTH);
                    outline.getColumnModel().getColumn(indexOfColumn).setCellRenderer(new PrioritizedIconCellRenderer());
                }
                indexOfColumn = getColumnIndexByName(Bundle.AutoIngestJobsNode_ocr_text());
                if (indexOfColumn != INVALID_INDEX) {
                    outline.getColumnModel().getColumn(indexOfColumn).setPreferredWidth(INITIAL_OCR_WIDTH);
                    outline.getColumnModel().getColumn(indexOfColumn).setCellRenderer(new OcrIconCellRenderer());
                }
                break;
            case RUNNING_JOB:
                outlineView.setPropertyColumns(Bundle.AutoIngestJobsNode_dataSource_text(), Bundle.AutoIngestJobsNode_dataSource_text(),
                        Bundle.AutoIngestJobsNode_hostName_text(), Bundle.AutoIngestJobsNode_hostName_text(),
                        Bundle.AutoIngestJobsNode_stage_text(), Bundle.AutoIngestJobsNode_stage_text(),
                        Bundle.AutoIngestJobsNode_stageTime_text(DurationCellRenderer.getUnitSeperator()),
                        Bundle.AutoIngestJobsNode_stageTime_text(DurationCellRenderer.getUnitSeperator()));
                indexOfColumn = getColumnIndexByName(Bundle.AutoIngestJobsNode_caseName_text());
                if (indexOfColumn != INVALID_INDEX) {
                    outline.setColumnSorted(indexOfColumn, true, 1);
                }
                break;
            case COMPLETED_JOB:
                outlineView.setPropertyColumns(Bundle.AutoIngestJobsNode_dataSource_text(), Bundle.AutoIngestJobsNode_dataSource_text(),
                        Bundle.AutoIngestJobsNode_jobCreated_text(), Bundle.AutoIngestJobsNode_jobCreated_text(),
                        Bundle.AutoIngestJobsNode_jobCompleted_text(), Bundle.AutoIngestJobsNode_jobCompleted_text(),
                        Bundle.AutoIngestJobsNode_status_text(), Bundle.AutoIngestJobsNode_status_text(),
                        Bundle.AutoIngestJobsNode_ocr_text(), Bundle.AutoIngestJobsNode_ocr_text());
                indexOfColumn = getColumnIndexByName(Bundle.AutoIngestJobsNode_jobCompleted_text());
                if (indexOfColumn != INVALID_INDEX) {
                    outline.setColumnSorted(indexOfColumn, false, 1);
                }
                indexOfColumn = getColumnIndexByName(Bundle.AutoIngestJobsNode_status_text());
                if (indexOfColumn != INVALID_INDEX) {
                    outline.getColumnModel().getColumn(indexOfColumn).setPreferredWidth(INITIAL_STATUS_WIDTH);
                    outline.getColumnModel().getColumn(indexOfColumn).setCellRenderer(new StatusIconCellRenderer());
                }
                indexOfColumn = getColumnIndexByName(Bundle.AutoIngestJobsNode_ocr_text());
                if (indexOfColumn != INVALID_INDEX) {
                    outline.getColumnModel().getColumn(indexOfColumn).setPreferredWidth(INITIAL_OCR_WIDTH);
                    outline.getColumnModel().getColumn(indexOfColumn).setCellRenderer(new OcrIconCellRenderer());
                }
                break;
            default:
        }
        outline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        outline.setRootVisible(false);

        indexOfColumn = getColumnIndexByName(Bundle.AutoIngestJobsNode_caseName_text());
        if (indexOfColumn != INVALID_INDEX) {
            outline.getColumnModel().getColumn(indexOfColumn).setPreferredWidth(INITIAL_CASENAME_WIDTH);
        }
        indexOfColumn = getColumnIndexByName(Bundle.AutoIngestJobsNode_dataSource_text());
        if (indexOfColumn != INVALID_INDEX) {
            outline.getColumnModel().getColumn(indexOfColumn).setPreferredWidth(INITIAL_DATASOURCE_WIDTH);
        }
        add(outlineView, java.awt.BorderLayout.CENTER);
    }

    private int getColumnIndexByName(String columnName) {
        for (int index = 0; index < outline.getColumnModel().getColumnCount(); index++) {
            if (outline.getColumnModel().getColumn(index).getHeaderValue().toString().equals(columnName)) {
                return index;
            }
        }
        return INVALID_INDEX;
    }

    @Override
    public void setSize(Dimension d) {
        super.setSize(d);
        outlineView.setMaximumSize(new Dimension(400, 100));
        outline.setPreferredScrollableViewportSize(new Dimension(400, 100));
    }

    /**
     * Add a list selection listener to the selection model of the outline being
     * used in this panel.
     *
     * @param listener the ListSelectionListener to add
     */
    void addListSelectionListener(ListSelectionListener listener) {
        outline.getSelectionModel().addListSelectionListener(listener);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    /**
     * Update the contents of this AutoIngestJobsPanel while retaining currently
     * selected node.
     *
     * @param refreshEvent - the AutoIngestRefreshEvent which will provide the
     *                     new contents
     */
    void refresh(AutoIngestRefreshEvent refreshEvent) {
        synchronized (this) {
            outline.setRowSelectionAllowed(false);
            if (explorerManager.getRootContext() instanceof AutoIngestJobsNode) {
                ((AutoIngestJobsNode) explorerManager.getRootContext()).refresh(refreshEvent);
            } else {
                //Make a new AutoIngestJobsNode with it's own EventBus and set it as the root context
                explorerManager.setRootContext(new AutoIngestJobsNode(refreshEvent.getMonitor(), status, new EventBus("AutoIngestJobsNodeEventBus")));
            }
            outline.setRowSelectionAllowed(true);
            outline.setFocusable(true);
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

    /**
     * Get the AutoIngestJob for the currently selected node of this panel.
     *
     * @return AutoIngestJob which is currently selected in this panel
     */
    AutoIngestJob getSelectedAutoIngestJob() {
        Node[] selectedRows = explorerManager.getSelectedNodes();
        if (selectedRows.length == 1) {
            return ((JobNode) selectedRows[0]).getAutoIngestJob();
        }
        return null;
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

}
