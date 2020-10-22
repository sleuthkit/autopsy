/*
 * Autopsy
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.ui;

import javax.swing.DefaultListModel;
import javax.swing.event.ListSelectionListener;

/**
 * A JPanel to display domain summaries.
 */
public class DomainSummaryViewer extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<DomainWrapper> domainListModel = new DefaultListModel<>();

    /**
     * Clear the list of documents being displayed.
     */
    void clearViewer() {
        synchronized (this) {
            domainListModel.removeAllElements();
            domainScrollPane.getVerticalScrollBar().setValue(0);
        }
    }

    /**
     * Creates new form DomainSummaryPanel
     */
    public DomainSummaryViewer() {
        initComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        domainScrollPane = new javax.swing.JScrollPane();
        domainList = new javax.swing.JList<>();

        setLayout(new java.awt.BorderLayout());

        domainList.setModel(domainListModel);
        domainList.setCellRenderer(new DomainSummaryPanel());
        domainScrollPane.setViewportView(domainList);

        add(domainScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList<DomainWrapper> domainList;
    private javax.swing.JScrollPane domainScrollPane;
    // End of variables declaration//GEN-END:variables

    /**
     * Add the summary for a domain to the panel.
     *
     * @param domainWrapper The object which contains the domain summary which
     *                      will be displayed.
     */
    void addDomain(DomainWrapper domainWrapper) {
        synchronized (this) {
            domainListModel.addElement(domainWrapper);
        }
    }

    /**
     * Get the list of AbstractFiles which are represented by the selected
     * document preview.
     *
     * @return The list of AbstractFiles which are represented by the selected
     *         document preview.
     */
    String getDomainForSelected() {
        synchronized (this) {
            if (domainList.getSelectedIndex() == -1) {
                return "";
            } else {
                return domainListModel.getElementAt(domainList.getSelectedIndex()).getResultDomain().getDomain();
            }
        }
    }

    /**
     * Add a selection listener to the list of document previews being
     * displayed.
     *
     * @param listener The ListSelectionListener to add to the selection model.
     */
    void addListSelectionListener(ListSelectionListener listener) {
        domainList.getSelectionModel().addListSelectionListener(listener);
    }
}
