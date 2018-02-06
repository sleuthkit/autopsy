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
package org.sleuthkit.autopsy.contentviewers;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingWorker;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import org.netbeans.swing.etable.ETableColumn;
import org.netbeans.swing.etable.ETableColumnModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;

class SQLiteTableView extends JPanel implements ExplorerManager.Provider {

    private final org.openide.explorer.view.OutlineView outlineView;
    private final Outline outline;
    private final ExplorerManager explorerManager;

    /**
     * Creates new form SQLiteTableView
     *
     */
    SQLiteTableView() {

        initComponents();
        outlineView = new org.openide.explorer.view.OutlineView();
        add(outlineView, BorderLayout.CENTER);
        outlineView.setPropertyColumns();   // column headers will be set later
        outlineView.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        outlineView.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

        outline = outlineView.getOutline();

        outline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        outline.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        outline.setRowSelectionAllowed(false);
        outline.setRootVisible(false);

        explorerManager = new ExplorerManager();
    }

    /**
     * Sets up the columns in the display table
     *
     * @param tableRows
     */
    void setupTable(List<Map<String, Object>> tableRows) {

     
        if (Objects.isNull(tableRows) || tableRows.isEmpty()) {
            outlineView.setPropertyColumns();
        } else {

            // Set up the column names
            Map<String, Object> row = tableRows.get(0);
            String[] propStrings = new String[row.size() * 2];
            int i = 0;
            for (Map.Entry<String, Object> col : row.entrySet()) {
                String colName = col.getKey();
                propStrings[2 * i] = colName;
                propStrings[2 * i + 1] = colName;
                i++;
            }

            outlineView.setPropertyColumns(propStrings);
        }
        
        // Hide the 'Nodes' column
        TableColumnModel columnModel = outline.getColumnModel();
        ETableColumn column = (ETableColumn) columnModel.getColumn(0);
        ((ETableColumnModel) columnModel).setColumnHidden(column, true);

        // Set the Nodes for the ExplorerManager.
        // The Swingworker ensures that setColumnWidths() is called after all nodes have been created.
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {

                explorerManager.setRootContext(new AbstractNode(Children.create(new SQLiteTableRowFactory(tableRows), true)));
                return false;
            }

            @Override
            protected void done() {
                super.done();
                
                setColumnWidths();
            }
        }.execute();
        
    }

    private void setColumnWidths() {
        int margin = 4;
        int padding = 8;

         // find the maximum width needed to fit the values for the first N rows, at most
        final int rows = Math.min(20, outline.getRowCount());
        for (int col = 1; col < outline.getColumnCount(); col++) {
            int columnWidthLimit = 500;
            int columnWidth = 50;

            for (int row = 0; row < rows; row++) {
                TableCellRenderer renderer = outline.getCellRenderer(row, col);
                Component comp = outline.prepareRenderer(renderer, row, col);
          
                columnWidth = Math.max(comp.getPreferredSize().width, columnWidth);
            }

            columnWidth += 2 * margin + padding; // add margin and regular padding
            columnWidth = Math.min(columnWidth, columnWidthLimit);
            outline.getColumnModel().getColumn(col).setPreferredWidth(columnWidth);
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

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
