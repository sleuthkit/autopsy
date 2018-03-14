/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EnumSet;
import java.util.logging.Level;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.coreutils.Logger;

/**
 * A panel that provides a toolbar button for the dropdown keyword list search
 * panel and dropdown single keyword search panel. Displayed in the upper right
 * hand corner of the application by default.
 */
class DropdownToolbar extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DropdownToolbar.class.getName());
    private static DropdownToolbar instance;
    private SearchSettingsChangeListener searchSettingsChangeListener;
    private boolean active = false;
    private DropdownSingleTermSearchPanel dropPanel = null;

    /**
     * Gets the singleton panel that provides a toolbar button for the dropdown
     * keyword list search panel and dropdown single keyword search panel.
     * Displayed in the upper right hand corner of the application by default.
     *
     * @return The panel.
     */
    public synchronized static DropdownToolbar getDefault() {
        if (instance == null) {
            instance = new DropdownToolbar();
        }
        return instance;
    }

    /**
     * Constructs a panel that provides a toolbar button for the dropdown
     * keyword list search panel and dropdown single keyword search panel.
     * Displayed in the upper right hand corner of the application by default.
     */
    private DropdownToolbar() {
        initComponents();
        customizeComponents();
    }

    /**
     * Does additional initialization of the GUI components created by the
     * initComponents method.
     */
    private void customizeComponents() {
        searchSettingsChangeListener = new SearchSettingsChangeListener();
        KeywordSearch.getServer().addServerActionListener(searchSettingsChangeListener);
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), searchSettingsChangeListener);

        DropdownListSearchPanel listsPanel = DropdownListSearchPanel.getDefault();
        listsPanel.addSearchButtonActionListener((ActionEvent e) -> {
            listsMenu.setVisible(false);
        });

        // Adding border of six to account for menu border
        listsMenu.setSize(listsPanel.getPreferredSize().width + 6, listsPanel.getPreferredSize().height + 6);
        listsMenu.add(listsPanel);
        listsMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                listsButton.setSelected(true);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                listsButton.setSelected(false);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                listsButton.setSelected(false);
            }
        });

        dropPanel = DropdownSingleTermSearchPanel.getDefault();
        dropPanel.addSearchButtonActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                searchMenu.setVisible(false);
            }
        });
        searchMenu.setSize(dropPanel.getPreferredSize().width + 6, dropPanel.getPreferredSize().height + 6);
        searchMenu.add(dropPanel);
        searchMenu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                searchDropButton.setSelected(true);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                searchDropButton.setSelected(false);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
                searchDropButton.setSelected(false);
            }
        });

    }

    private void maybeShowListsPopup(MouseEvent evt) {
        if (!active || !listsButton.isEnabled()) {
            return;
        }
        if (evt != null && !SwingUtilities.isLeftMouseButton(evt)) {
            return;
        }
        listsMenu.show(listsButton, listsButton.getWidth() - listsMenu.getWidth(), listsButton.getHeight() - 1);
    }

    private void maybeShowSearchPopup(MouseEvent evt) {
        if (!active || !searchDropButton.isEnabled()) {
            return;
        }
        if (evt != null && !SwingUtilities.isLeftMouseButton(evt)) {
            return;
        }
        searchMenu.show(searchDropButton, searchDropButton.getWidth() - searchMenu.getWidth(), searchDropButton.getHeight() - 1);
    }

    private class SearchSettingsChangeListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (RuntimeProperties.runningWithGUI()) {
                String changed = evt.getPropertyName();
                if (changed.equals(Case.Events.CURRENT_CASE.toString())) {
                    if (null != evt.getNewValue()) {
                        boolean disableSearch = false;
                        /*
                         * A case has been opened.
                         */
                        try {
                            Server server = KeywordSearch.getServer();
                            if (server.coreIsOpen() == false) {
                                disableSearch = true;
                            }
                            else {
                                Index indexInfo = server.getIndexInfo();
                                if (IndexFinder.getCurrentSolrVersion().equals(indexInfo.getSolrVersion())) {
                                    /*
                                     * Solr version is current, so check the Solr
                                     * schema version and selectively enable the ad
                                     * hoc search UI components.
                                     */
                                    boolean schemaIsCurrent = IndexFinder.getCurrentSchemaVersion().equals(indexInfo.getSchemaVersion());
                                    listsButton.setEnabled(schemaIsCurrent);
                                    searchDropButton.setEnabled(true);
                                    dropPanel.setRegexSearchEnabled(schemaIsCurrent);
                                    active = true;
                                } else {
                                    /*
                                     * Unsupported Solr version, disable the ad hoc
                                     * search UI components.
                                     */
                                    disableSearch = true;
                                }
                            }
                        } catch (NoOpenCoreException ex) {
                            /*
                             * Error, disable the ad hoc search UI components.
                             */
                            logger.log(Level.SEVERE, "Error getting text index info", ex); //NON-NLS
                            disableSearch = true;
                        }
                        
                        if (disableSearch) {
                            searchDropButton.setEnabled(false);
                            listsButton.setEnabled(false);
                            active = false;
                        }
                        
                    } else {
                        /*
                         * A case has been closed.
                         */
                        dropPanel.clearSearchBox();
                        searchDropButton.setEnabled(false);
                        listsButton.setEnabled(false);
                        active = false;
                    }
                } else if (changed.equals(Server.CORE_EVT)) {
                    final Server.CORE_EVT_STATES state = (Server.CORE_EVT_STATES) evt.getNewValue();
                    switch (state) {
                        case STARTED:
                            try {
                                final int numIndexedFiles = KeywordSearch.getServer().queryNumIndexedFiles();
                                KeywordSearch.fireNumIndexedFilesChange(null, numIndexedFiles);
                            } catch (NoOpenCoreException | KeywordSearchModuleException ex) {
                                logger.log(Level.SEVERE, "Error executing Solr query", ex); //NON-NLS
                            }
                            break;
                        case STOPPED:
                            break;
                        default:
                    }
                }
            }
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

        listsMenu = new javax.swing.JPopupMenu();
        searchMenu = new javax.swing.JPopupMenu();
        listsButton = new javax.swing.JButton();
        searchDropButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JSeparator();

        setOpaque(false);

        listsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/watchbutton-icon.png"))); // NOI18N
        listsButton.setText(org.openide.util.NbBundle.getMessage(DropdownToolbar.class, "ListBundleName")); // NOI18N
        listsButton.setBorderPainted(false);
        listsButton.setContentAreaFilled(false);
        listsButton.setEnabled(false);
        listsButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/watchbutton-icon-rollover.png"))); // NOI18N
        listsButton.setRolloverSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/watchbutton-icon-pressed.png"))); // NOI18N
        listsButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                listsButtonMousePressed(evt);
            }
        });
        listsButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                listsButtonActionPerformed(evt);
            }
        });

        searchDropButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/searchbutton-icon.png"))); // NOI18N
        searchDropButton.setText(org.openide.util.NbBundle.getMessage(DropdownToolbar.class, "KeywordSearchPanel.searchDropButton.text")); // NOI18N
        searchDropButton.setBorderPainted(false);
        searchDropButton.setContentAreaFilled(false);
        searchDropButton.setEnabled(false);
        searchDropButton.setMaximumSize(new java.awt.Dimension(146, 27));
        searchDropButton.setMinimumSize(new java.awt.Dimension(146, 27));
        searchDropButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/searchbutton-icon-rollover.png"))); // NOI18N
        searchDropButton.setRolloverSelectedIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/searchbutton-icon-pressed.png"))); // NOI18N
        searchDropButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                searchDropButtonMousePressed(evt);
            }
        });

        jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(listsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 7, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(1, 1, 1)
                .addComponent(searchDropButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, Short.MAX_VALUE)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(listsButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(searchDropButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jSeparator1)))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void listsButtonMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listsButtonMousePressed
        maybeShowListsPopup(evt);
    }//GEN-LAST:event_listsButtonMousePressed

    private void listsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_listsButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_listsButtonActionPerformed

    private void searchDropButtonMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_searchDropButtonMousePressed
        maybeShowSearchPopup(evt);
    }//GEN-LAST:event_searchDropButtonMousePressed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JButton listsButton;
    private javax.swing.JPopupMenu listsMenu;
    private javax.swing.JButton searchDropButton;
    private javax.swing.JPopupMenu searchMenu;
    // End of variables declaration//GEN-END:variables

}
