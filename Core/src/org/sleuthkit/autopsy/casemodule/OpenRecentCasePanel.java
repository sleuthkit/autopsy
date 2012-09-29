/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;

/**
 * Panel show from the splash dialog that shows recent cases and allows them
 * to be opened.
 */
class OpenRecentCasePanel extends javax.swing.JPanel {

    static String[] caseName;
    static String[] casePaths;
    private static OpenRecentCasePanel instance;
    
    private OpenRecentCasePanel() {
        initComponents();
    }
    
    /**
     * Retrieves all the recent cases and adds them to the table.
     */
    private void generateRecentCases() {

        caseName = RecentCases.getInstance().getRecentCaseNames();
        casePaths = RecentCases.getInstance().getRecentCasePaths();
        int totalRecentCases = RecentCases.getInstance().getTotalRecentCases();

        // create the headers and add all the rows
        String[] headers = {"Case Name", "Path", "Open"};
        String[][] rows = new String[totalRecentCases][];
        final int lastColumn = headers.length - 1;

        for(int i = 0; i < totalRecentCases; i++){
            String path = casePaths[i];
            String shortenPath = path;
            if(path.length() > 50){
                shortenPath = shortenPath.substring(0, 10 + shortenPath.substring(10).indexOf(File.separator) + 1) + "..." +
                        shortenPath.substring((shortenPath.length() - 20) + shortenPath.substring(shortenPath.length() - 20).indexOf(File.separator));
            }
            String[] thisRow = {caseName[i], shortenPath, path};
            rows[i] = thisRow;
            //model.insertRow(i, row);
        }

        // create the table inside with the imgPaths information
        DefaultTableModel model = new DefaultTableModel(rows, headers)
        {
            @Override
            // make the cells in the FileContentTable "read only"
            public boolean isCellEditable(int row, int column){
                return column == lastColumn; // make the last column (Remove button), only the editable
            }
        };
        imagesTable.setModel(model);

        // set the size of the remove column
        TableColumn removeCol = imagesTable.getColumnModel().getColumn(lastColumn);
        removeCol.setPreferredWidth(75);
        removeCol.setMaxWidth(75);
        removeCol.setMinWidth(75);
        removeCol.setResizable(false);

        // create the delete action to remove the image from the current case
        Action open = new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                // get the image path
                JTable table = (JTable)e.getSource();
                int modelRow = Integer.valueOf(e.getActionCommand());
                String removeColumn = table.getValueAt(modelRow, lastColumn).toString();

                // try to close Startup and openRecentCase window if they exist
                try{
                    StartupWindow.getInstance().close();
                    CueBannerPanel.closeOpenRecentCasesWindow();
                }
                catch(Exception ex){
                    Logger.getLogger(OpenRecentCasePanel.class.getName()).log(Level.WARNING, "Error: couldn't open case.", ex);
                }

                // Open the recent cases
                try {
                    Case.open(removeColumn); // open the case
                } catch (Exception ex) {
                    Logger.getLogger(OpenRecentCasePanel.class.getName()).log(Level.WARNING, "Error: couldn't open case.", ex);
                }
            }
        };

        ButtonColumn buttonColumn = new ButtonColumn(imagesTable, open, lastColumn, "Open");
    }
    
    static OpenRecentCasePanel getInstance() {
        if (instance == null) {
            instance = new OpenRecentCasePanel();
        }
        instance.generateRecentCases(); // refresh the case list
        return instance;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jLabel1 = new javax.swing.JLabel();
        cancelButton = new javax.swing.JButton();
        imagesTableScrollPane = new javax.swing.JScrollPane();
        imagesTable = new javax.swing.JTable();

        jLabel1.setText(org.openide.util.NbBundle.getMessage(OpenRecentCasePanel.class, "OpenRecentCasePanel.jLabel1.text")); // NOI18N

        cancelButton.setText(org.openide.util.NbBundle.getMessage(OpenRecentCasePanel.class, "OpenRecentCasePanel.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        imagesTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "Case Name", "Path", "Open"
            }
        ) {
            boolean[] canEdit = new boolean [] {
                false, false, true
            };

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        imagesTable.setShowHorizontalLines(false);
        imagesTable.setShowVerticalLines(false);
        imagesTable.getTableHeader().setReorderingAllowed(false);
        imagesTable.setUpdateSelectionOnSort(false);
        imagesTableScrollPane.setViewportView(imagesTable);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                            .addComponent(jLabel1)
                            .addGap(292, 292, 292))
                        .addGroup(layout.createSequentialGroup()
                            .addComponent(imagesTableScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 470, Short.MAX_VALUE)
                            .addContainerGap()))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(cancelButton)
                        .addContainerGap())))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(imagesTableScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 170, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cancelButton)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
    }//GEN-LAST:event_cancelButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JTable imagesTable;
    private javax.swing.JScrollPane imagesTableScrollPane;
    private javax.swing.JLabel jLabel1;
    // End of variables declaration//GEN-END:variables

    /**
     * Sets the Close button action listener.
     *
     * @param e  the action listener
     */
    public void setCloseButtonActionListener(ActionListener e){
        this.cancelButton.addActionListener(e);
    }
}
