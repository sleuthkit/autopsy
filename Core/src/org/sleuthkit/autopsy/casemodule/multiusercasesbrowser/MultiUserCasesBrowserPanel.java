/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.casemodule.multiusercasesbrowser;

import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;
import org.netbeans.swing.outline.DefaultOutlineModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import org.openide.util.NbBundle;
import org.openide.explorer.view.OutlineView;
import org.sleuthkit.autopsy.casemodule.multiusercasesbrowser.MultiUserCaseBrowserCustomizer.Column;
import org.sleuthkit.autopsy.casemodule.multiusercasesbrowser.MultiUserCaseBrowserCustomizer.SortColumn;

/**
 * A JPanel that contains a NetBeans OutlineView that is used to provide a
 * tabular view of the multi-user cases known to the coordination service. The
 * outline view set up, including the property sheets and actions of the
 * MultiUserCaseNodes it displays, are defined using a
 * MultiUserCaseBrowserCustomizer. Each MultiUserCaseNode has a CaseNodeData
 * object in its Lookup.
 */
@SuppressWarnings("PMD.SingularField") // Matisse-generated UI widgets cause lots of false positives for this in PMD
public final class MultiUserCasesBrowserPanel extends javax.swing.JPanel implements ExplorerManager.Provider {

    private static final long serialVersionUID = 1L;
    private static final int NAME_COLUMN_INDEX = 0;
    private static final int NAME_COLUMN_WIDTH = 150;
    private final ExplorerManager explorerManager;
    private final MultiUserCaseBrowserCustomizer customizer;
    private final OutlineView outlineView;
    private final Outline outline;

    /**
     * Constructs a JPanel that contains a NetBeans OutlineView that is used to
     * provide a tabular view of the multi-user cases known to the coordination
     * service. The outline view set up, including the property sheets and
     * actions of the MultiUserCaseNodes it displays, are defined using a
     * MultiUserCaseBrowserCustomizer. Each MultiUserCaseNode has a CaseNodeData
     * object in its Lookup.
     *
     * @param explorerManager The ExplorerManager for the browser's OutlineView.
     * @param customizer      A customizer for the browser.
     */
    public MultiUserCasesBrowserPanel(ExplorerManager explorerManager, MultiUserCaseBrowserCustomizer customizer) {
        this.explorerManager = explorerManager;
        this.customizer = customizer;
        initComponents();
        outlineView = new org.openide.explorer.view.OutlineView();
        outline = this.outlineView.getOutline();
        configureOutlineView();
        add(outlineView, BorderLayout.CENTER);
        this.setVisible(true);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return explorerManager;
    }

    /**
     * Configures the child scroll pane component's child OutlineView component.
     */
    private void configureOutlineView() {
        /*
         * Set up the outline view columns and sorting.
         */
        Map<Column, SortColumn> sortColumns = new HashMap<>();
        for (SortColumn sortColumn : customizer.getSortColumns()) {
            sortColumns.put(sortColumn.column(), sortColumn);
        }
        ((DefaultOutlineModel) outline.getOutlineModel()).setNodesColumnLabel(Column.DISPLAY_NAME.getDisplayName());
        if (sortColumns.containsKey(Column.DISPLAY_NAME)) {
            SortColumn sortColumn = sortColumns.get(Column.DISPLAY_NAME);
            outline.setColumnSorted(0, sortColumn.sortAscending(), sortColumn.sortRank());
        }
        List<Column> sheetProperties = customizer.getColumns();
        for (int index = 0; index < sheetProperties.size(); ++index) {
            Column property = sheetProperties.get(index);
            String propertyName = property.getDisplayName();
            outlineView.addPropertyColumn(propertyName, propertyName, propertyName);
            if (sortColumns.containsKey(property)) {
                SortColumn sortColumn = sortColumns.get(property);
                outline.setColumnSorted(index + 1, sortColumn.sortAscending(), sortColumn.sortRank());
            }
        }

        /*
         * Give the case name column a greater width.
         */
        outline.getColumnModel().getColumn(NAME_COLUMN_INDEX).setPreferredWidth(NAME_COLUMN_WIDTH);
        
        /*
         * Hide the root node and configure the node selection mode.
         */
        outline.setRootVisible(false);
        outline.setSelectionMode(customizer.allowMultiSelect() ? ListSelectionModel.MULTIPLE_INTERVAL_SELECTION : ListSelectionModel.SINGLE_SELECTION);
    }

    /**
     * Adds a listener to changes in case selection in this browser.
     *
     * @param listener the ListSelectionListener to add
     */
    public void addListSelectionListener(ListSelectionListener listener) {
        outline.getSelectionModel().addListSelectionListener(listener);
    }

    /**
     * Refreshes the display of the list of multi-user cases known to the
     * coordination service.
     */
    @NbBundle.Messages({
        "MultiUserCasesBrowserPanel.waitNode.message=Please Wait..."
    })
    public void displayCases() {
        explorerManager.setRootContext(new MultiUserCasesRootNode(customizer));
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setMinimumSize(new java.awt.Dimension(0, 5));
        setPreferredSize(new java.awt.Dimension(5, 5));
        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

}
