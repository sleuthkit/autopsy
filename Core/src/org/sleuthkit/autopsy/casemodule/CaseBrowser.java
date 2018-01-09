/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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

import java.awt.Component;
import java.awt.Cursor;
import java.lang.reflect.InvocationTargetException;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.datamodel.NodeProperty;

/**
 * A Swing JPanel with a JTabbedPane child component. The tabbed pane contains
 * result viewers.
 *
 * The "main" DataResultPanel for the desktop application has a table viewer
 * (DataResultViewerTable) and a thumbnail viewer (DataResultViewerThumbnail),
 * plus zero to many additional DataResultViewers, since the DataResultViewer
 * interface is an extension point.
 *
 * The "main" DataResultPanel resides in the "main" results view
 * (DataResultTopComponent) that is normally docked into the upper right hand
 * side of the main window of the desktop application.
 *
 * The result viewers in the "main panel" are used to view the child nodes of a
 * node selected in the tree view (DirectoryTreeTopComponent) that is normally
 * docked into the left hand side of the main window of the desktop application.
 *
 * Nodes selected in the child results viewers of a DataResultPanel are
 * displayed in a content view (implementation of the DataContent interface)
 * supplied the panel. The default content view is (DataContentTopComponent) is
 * normally docked into the lower right hand side of the main window, underneath
 * the results view. A custom content view may be specified instead.
 */
class CaseBrowser extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private Outline outline;
    private ExplorerManager em;
    private org.openide.explorer.view.OutlineView outlineView;

    /**
     * Creates new form CaseBrowser
     */
    CaseBrowser() {
        outlineView = new org.openide.explorer.view.OutlineView();
        initComponents();

        outline = outlineView.getOutline();
        outlineView.setPropertyColumns(
                Bundle.CaseNode_column_createdTime(), Bundle.CaseNode_column_createdTime(),
                Bundle.CaseNode_column_status(), Bundle.CaseNode_column_status(),
                Bundle.CaseNode_column_metadataFilePath(), Bundle.CaseNode_column_metadataFilePath()
        );
        outline.setRootVisible(false);
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Bundle.CaseNode_column_name());
        outline.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        outline.setColumnSorted(1, false, 1); //it would be nice if the column index wasn't hardcoded

    }

    /**
     * Initializes this panel. Intended to be called by a parent top component
     * when the top component is opened.
     */
    void open() {
        if (null == em) {
            /*
             * Get an explorer manager to pass to the child result viewers. If
             * the application components are put together as expected, this
             * will be an explorer manager owned by a parent top component, and
             * placed by the top component in the look up that is proxied as the
             * action global context when the top component has focus. The
             * sharing of this explorer manager enables the same child node
             * selections to be made in all of the result viewers.
             */
            em = ExplorerManager.find(this);
        }
        jScrollPane1.setViewportView(outlineView);
        setColumnWidths();
        this.setVisible(true);
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public void setNode(Node selectedNode) {

        /*
         * The quick filter must be reset because when determining column width,
         * ETable.getRowCount is called, and the documentation states that quick
         * filters must be unset for the method to work "If the quick-filter is
         * applied the number of rows do not match the number of rows in the
         * model."
         */
        outline.unsetQuickFilter();
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            boolean hasChildren = false;
            if (selectedNode != null) {
                // @@@ This just did a DB round trip to get the count and the results were not saved...
                hasChildren = selectedNode.getChildren().getNodesCount() > 0;
            }

            if (hasChildren) {
                em.setRootContext(selectedNode);;
                outline = outlineView.getOutline();
            } else {
                Node emptyNode = new AbstractNode(Children.LEAF);
                em.setRootContext(emptyNode); // make empty node
                outlineView.setPropertyColumns(); // set the empty property header
            }
        } finally {
            this.setCursor(null);
        }
    }

    public void addListSelectionListener(ListSelectionListener listener) {
        outline.getSelectionModel().addListSelectionListener(listener);
    }

    String getCasePath() {
        int[] selectedRows = outline.getSelectedRows();
        System.out.println("Explored Context: " + em.getExploredContext());
        System.out.println("EM ROOT NODe: " + em.getRootContext().getDisplayName());
        if (selectedRows.length == 1) {
            System.out.println("Selected Row: " + selectedRows[0]);
            for (int colIndex = 0; colIndex < outline.getColumnCount(); colIndex++) {
                TableColumn col = outline.getColumnModel().getColumn(colIndex);
                System.out.println("COL: " + col.getHeaderValue().toString());
                if (col.getHeaderValue().toString().equals(Bundle.CaseNode_column_metadataFilePath())) {
                    try {
                        return ((NodeProperty)outline.getValueAt(selectedRows[0], colIndex)).getValue().toString();
                    } catch (IllegalAccessException ex) {
                        
                    } catch (InvocationTargetException ex) {
                        
                    }        
                }
            }

        }
        return null;
    }
                                                                                                                                                                                                                                                    
    boolean isRowSelected() {
        System.out.println("SELECTED ROWS: " + outline.getSelectedRows().length);
        return outline.getSelectedRows().length > 0;
    }

    private void setColumnWidths() {
        int margin = 4;
        int padding = 8;

        final int rows = Math.min(100, outline.getRowCount());

        for (int column = 0; column < outline.getModel().getColumnCount(); column++) {
            int columnWidthLimit = 500;
            int columnWidth = 0;

            // find the maximum width needed to fit the values for the first 100 rows, at most
            for (int row = 0; row < rows; row++) {
                TableCellRenderer renderer = outline.getCellRenderer(row, column);
                Component comp = outline.prepareRenderer(renderer, row, column);
                columnWidth = Math.max(comp.getPreferredSize().width, columnWidth);
            }

            columnWidth += 2 * margin + padding; // add margin and regular padding
            columnWidth = Math.min(columnWidth, columnWidthLimit);

            outline.getColumnModel().getColumn(column).setPreferredWidth(columnWidth);
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

        jScrollPane1 = new javax.swing.JScrollPane();

        setMinimumSize(new java.awt.Dimension(0, 5));
        setPreferredSize(new java.awt.Dimension(5, 5));
        setLayout(new java.awt.BorderLayout());

        jScrollPane1.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane1.setMinimumSize(new java.awt.Dimension(0, 5));
        jScrollPane1.setOpaque(false);
        jScrollPane1.setPreferredSize(new java.awt.Dimension(5, 5));
        add(jScrollPane1, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JScrollPane jScrollPane1;
    // End of variables declaration//GEN-END:variables

}
