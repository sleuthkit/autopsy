/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule;

import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.openide.windows.WindowManager;
import java.awt.Cursor;

/**
 * Panel used by the the open recent case option of the start window.
 */
class OpenRecentCasePanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(OpenRecentCasePanel.class.getName());
    private static OpenRecentCasePanel instance;
    private static String[] caseNames;
    private static String[] casePaths;
    private RecentCasesTableModel model;

    private OpenRecentCasePanel() {
        initComponents();
    }

    static OpenRecentCasePanel getInstance() {
        if (instance == null) {
            instance = new OpenRecentCasePanel();
        }
        instance.generateRecentCases(); // refresh the case list
        return instance;
    }

    /**
     * Sets the Close button action listener.
     *
     * @param e the action listener
     */
    public void setCloseButtonActionListener(ActionListener e) {
        this.cancelButton.addActionListener(e);
    }
    
    /**
     * Retrieves all the recent cases and adds them to the table.
     */
    private void generateRecentCases() {
        caseNames = RecentCases.getInstance().getRecentCaseNames();
        casePaths = RecentCases.getInstance().getRecentCasePaths();
        model = new RecentCasesTableModel();
        imagesTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        imagesTable.setModel(model);

        int width = tableScrollPane.getPreferredSize().width;
        imagesTable.getColumnModel().getColumn(0).setPreferredWidth((int) (.30 * width));
        imagesTable.getColumnModel().getColumn(1).setPreferredWidth((int) (.70 * width));
        // If there are any images, let's select the first one
        if (imagesTable.getRowCount() > 0) {
            imagesTable.setRowSelectionInterval(0, 0);
            openButton.setEnabled(true);
        } else {
            openButton.setEnabled(false);
        }
    }
    
    // Open the selected case
    private void openCase() {
        if (casePaths.length < 1) {
            return;
        }
        final String casePath = casePaths[imagesTable.getSelectedRow()];
        final String caseName = caseNames[imagesTable.getSelectedRow()];
        if (!casePath.equals("")) {
            try {
                StartupWindowProvider.getInstance().close();
                CueBannerPanel.closeOpenRecentCasesWindow();
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Error closing start up window", ex); //NON-NLS
            }

            /*
             * Verify the case name and metadata file path.
             */
            if (caseName.equals("") || casePath.equals("") || (!new File(casePath).exists())) {
                JOptionPane.showMessageDialog(WindowManager.getDefault().getMainWindow(),
                        NbBundle.getMessage(this.getClass(), "RecentItems.openRecentCase.msgDlg.text", caseName),
                        NbBundle.getMessage(this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.title"),
                        JOptionPane.ERROR_MESSAGE);
                RecentCases.getInstance().removeRecentCase(caseName, casePath); // remove the recent case if it doesn't exist anymore

                /*
                 * If a case was not already open, pop up the start window.
                 */
                if (Case.isCaseOpen() == false) {
                    StartupWindowProvider.getInstance().open();
                }

            } else {
                SwingUtilities.invokeLater(() -> {
                    WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                });
                new Thread(() -> {
                    try {
                        Case.open(casePath);
                    } catch (CaseActionException ex) {
                        SwingUtilities.invokeLater(() -> {
                            logger.log(Level.SEVERE, String.format("Error opening case with metadata file path %s", casePath), ex); //NON-NLS                            
                            WindowManager.getDefault().getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                            JOptionPane.showMessageDialog(
                                    WindowManager.getDefault().getMainWindow(),
                                    ex.getMessage(),
                                    NbBundle.getMessage(this.getClass(), "CaseOpenAction.msgDlg.cantOpenCase.title"), JOptionPane.ERROR_MESSAGE); //NON-NLS
                            /*
                             * If a case was not already open, pop up the start
                             * window.
                             */
                            if (!Case.isCaseOpen()) {
                                StartupWindowProvider.getInstance().open();
                            }
                        });
                    }
                }).start();
            }
        }
    }

    /**
     * Table model to keep track of recent cases.
     */
    private class RecentCasesTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        @Override
        public int getRowCount() {
            int count = 0;
            for (String s : caseNames) {
                if (!s.equals("")) {
                    count++;
                }
            }
            return count;
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            String colName = null;
            switch (column) {
                case 0:
                    colName = NbBundle.getMessage(OpenRecentCasePanel.class, "OpenRecentCasePanel.colName.caseName");
                    break;
                case 1:
                    colName = NbBundle.getMessage(OpenRecentCasePanel.class, "OpenRecentCasePanel.colName.path");
                    break;
                default:
                    break;
            }
            return colName;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Object ret = null;
            switch (columnIndex) {
                case 0:
                    ret = caseNames[rowIndex];
                    break;
                case 1:
                    ret = shortenPath(casePaths[rowIndex]);
                    break;
                default:
                    logger.log(Level.SEVERE, "Invalid table column index: {0}", columnIndex); //NON-NLS
                    break;
            }
            return ret;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        }

        private String shortenPath(String path) {
            String shortenedPath = path;
            if (shortenedPath.length() > 50) {
                shortenedPath = path.substring(0, 10 + path.substring(10).indexOf(File.separator) + 1) + "..."
                        + path.substring((path.length() - 20) + path.substring(path.length() - 20).indexOf(File.separator));
            }
            return shortenedPath;
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

        jLabel1 = new javax.swing.JLabel();
        cancelButton = new javax.swing.JButton();
        openButton = new javax.swing.JButton();
        tableScrollPane = new javax.swing.JScrollPane();
        imagesTable = new javax.swing.JTable();

        jLabel1.setText(org.openide.util.NbBundle.getMessage(OpenRecentCasePanel.class, "OpenRecentCasePanel.jLabel1.text")); // NOI18N

        cancelButton.setText(org.openide.util.NbBundle.getMessage(OpenRecentCasePanel.class, "OpenRecentCasePanel.cancelButton.text")); // NOI18N

        openButton.setText(org.openide.util.NbBundle.getMessage(OpenRecentCasePanel.class, "OpenRecentCasePanel.openButton.text")); // NOI18N
        openButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openButtonActionPerformed(evt);
            }
        });

        imagesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        imagesTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        imagesTable.setShowHorizontalLines(false);
        imagesTable.setShowVerticalLines(false);
        imagesTable.getTableHeader().setReorderingAllowed(false);
        imagesTable.setUpdateSelectionOnSort(false);
        imagesTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                imagesTableMouseClicked(evt);
            }
        });
        imagesTable.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                imagesTableKeyPressed(evt);
            }
        });
        tableScrollPane.setViewportView(imagesTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addGap(292, 414, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(tableScrollPane)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(openButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(cancelButton)))
                        .addContainerGap())))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 168, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(openButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void openButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openButtonActionPerformed
        openCase();
    }//GEN-LAST:event_openButtonActionPerformed

    private void imagesTableMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_imagesTableMouseClicked
        // If it's a doubleclick
        if (evt.getClickCount() == 2) {
            openCase();
        }
    }//GEN-LAST:event_imagesTableMouseClicked

    private void imagesTableKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_imagesTableKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
            openCase();
        }
    }//GEN-LAST:event_imagesTableKeyPressed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JTable imagesTable;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JButton openButton;
    private javax.swing.JScrollPane tableScrollPane;
    // End of variables declaration//GEN-END:variables

}
