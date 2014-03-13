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

/*
 * KeywordSearchPanel
 *
 */
package org.sleuthkit.autopsy.keywordsearch;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.logging.Level;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.sleuthkit.autopsy.casemodule.Case;

/**
 * Keyword search toolbar (in upper right, by default) which allows to search for single terms or phrases
 * 
 * The toolbar uses a different font from the rest of the application, Monospaced 14, 
 * due to the necessity to find a font that displays both Arabic and Asian fonts at an acceptable size. 
 * The default, Tahoma 14, could not perform this task at the desired size, and neither could numerous other fonts. 
 */
class KeywordSearchPanel extends AbstractKeywordSearchPerformer {

    private static final Logger logger = Logger.getLogger(KeywordSearchPanel.class.getName());
    private KeywordPropertyChangeListener listener;
    private boolean active = false;
    private boolean entered = false;
    private static KeywordSearchPanel instance;

    /** Creates new form KeywordSearchPanel */
    private KeywordSearchPanel() {
        initComponents();
        customizeComponents();
    }

    /**
     * @return the default instance KeywordSearchPanel
     */
    public static KeywordSearchPanel getDefault() {
        if (instance == null) {
            instance = new KeywordSearchPanel();
        }
        return instance;
    }

    @Override
    protected void postFilesIndexedChange() {
        //nothing to update
    }

    
    
    private void customizeComponents() {

        listener = new KeywordPropertyChangeListener();

        KeywordSearch.getServer().addServerActionListener(listener);

        Case.addPropertyChangeListener(listener);

        searchBox.addFocusListener(new FocusListener() {

            @Override
            public void focusGained(FocusEvent e) {
                if (searchBox.getText()
                             .equals(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class,
                                                                          "KeywordSearchPanel.searchBox.text"))) {
                    searchBox.setText("");
                    searchBox.setForeground(Color.BLACK);
                    entered = true;
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (searchBox.getText().equals("")) {
                    resetSearchBox();
                }
            }
        });
        KeywordSearchListsViewerPanel listsPanel = KeywordSearchListsViewerPanel.getDefault();
        listsPanel.addSearchButtonActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                listsMenu.setVisible(false);
            }
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

        searchBox.setComponentPopupMenu(rightClickMenu);
        ActionListener actList = new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem jmi = (JMenuItem) e.getSource();
                if (jmi.equals(cutMenuItem)) {
                    searchBox.cut();
                } else if (jmi.equals(copyMenuItem)) {
                    searchBox.copy();
                } else if (jmi.equals(pasteMenuItem)) {
                    if (searchBox.getText()
                                 .equals(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class,
                                                                              "KeywordSearchPanel.searchBox.text"))) {
                        searchBox.setText("");
                        searchBox.setForeground(Color.BLACK);
                        entered = true;
                    }
                    searchBox.paste();
                } else if (jmi.equals(selectAllMenuItem)) {
                    searchBox.selectAll();
                }
            }
        };
        cutMenuItem.addActionListener(actList);
        copyMenuItem.addActionListener(actList);
        pasteMenuItem.addActionListener(actList);
        selectAllMenuItem.addActionListener(actList);

    }

    private void resetSearchBox() {
        searchBox.setEditable(true);
        searchBox.setText(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class,
                                                               "KeywordSearchPanel.searchBox.text"));
        searchBox.setForeground(Color.LIGHT_GRAY);
        regExCheckboxMenuItem.setEnabled(true);
        entered = false;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        settingsMenu = new javax.swing.JPopupMenu();
        regExCheckboxMenuItem = new javax.swing.JCheckBoxMenuItem();
        listsMenu = new javax.swing.JPopupMenu();
        rightClickMenu = new javax.swing.JPopupMenu();
        cutMenuItem = new javax.swing.JMenuItem();
        copyMenuItem = new javax.swing.JMenuItem();
        pasteMenuItem = new javax.swing.JMenuItem();
        selectAllMenuItem = new javax.swing.JMenuItem();
        searchBoxPanel = new javax.swing.JPanel();
        searchBox = new javax.swing.JTextField();
        settingsLabel = new javax.swing.JLabel();
        searchButton = new javax.swing.JLabel();
        listsButton = new javax.swing.JButton();

        regExCheckboxMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class, "KeywordSearchPanel.regExCheckboxMenuItem.text")); // NOI18N
        settingsMenu.add(regExCheckboxMenuItem);

        cutMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class, "KeywordSearchPanel.cutMenuItem.text")); // NOI18N
        rightClickMenu.add(cutMenuItem);

        copyMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class, "KeywordSearchPanel.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        pasteMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class, "KeywordSearchPanel.pasteMenuItem.text")); // NOI18N
        rightClickMenu.add(pasteMenuItem);

        selectAllMenuItem.setText(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class, "KeywordSearchPanel.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        setOpaque(false);

        searchBoxPanel.setBorder(new javax.swing.border.LineBorder(java.awt.Color.lightGray, 1, true));
        searchBoxPanel.setPreferredSize(new java.awt.Dimension(255, 18));

        searchBox.setFont(new java.awt.Font("Monospaced", 0, 14)); // NOI18N
        searchBox.setForeground(java.awt.Color.lightGray);
        searchBox.setText(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class, "KeywordSearchPanel.searchBox.text")); // NOI18N
        searchBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 3, 4, 1));
        searchBox.setEnabled(false);
        searchBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchBoxActionPerformed(evt);
            }
        });

        settingsLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/dropdown-icon.png"))); // NOI18N
        settingsLabel.setText(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class, "KeywordSearchPanel.settingsLabel.text")); // NOI18N
        settingsLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 2, 1, 2));
        settingsLabel.setEnabled(false);
        settingsLabel.setMaximumSize(new java.awt.Dimension(23, 20));
        settingsLabel.setMinimumSize(new java.awt.Dimension(23, 20));
        settingsLabel.setPreferredSize(new java.awt.Dimension(23, 20));
        settingsLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                settingsLabelMouseEntered(evt);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                settingsLabelMouseExited(evt);
            }
            public void mousePressed(java.awt.event.MouseEvent evt) {
                settingsLabelMousePressed(evt);
            }
        });

        searchButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/search-icon.png"))); // NOI18N
        searchButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class, "KeywordSearchPanel.searchButton.text")); // NOI18N
        searchButton.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 2, 1, 2));
        searchButton.setEnabled(false);
        searchButton.setMaximumSize(new java.awt.Dimension(23, 20));
        searchButton.setMinimumSize(new java.awt.Dimension(23, 20));
        searchButton.setPreferredSize(new java.awt.Dimension(23, 20));
        searchButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                searchButtonMousePressed(evt);
            }
        });

        javax.swing.GroupLayout searchBoxPanelLayout = new javax.swing.GroupLayout(searchBoxPanel);
        searchBoxPanel.setLayout(searchBoxPanelLayout);
        searchBoxPanelLayout.setHorizontalGroup(
            searchBoxPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(searchBoxPanelLayout.createSequentialGroup()
                .addComponent(settingsLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 9, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(searchBox, javax.swing.GroupLayout.DEFAULT_SIZE, 202, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(searchButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        searchBoxPanelLayout.setVerticalGroup(
            searchBoxPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(searchBox)
            .addComponent(settingsLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(searchButton, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        listsButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/watchbutton-icon.png"))); // NOI18N
        listsButton.setText(org.openide.util.NbBundle.getMessage(KeywordSearchPanel.class, "ListBundleName")); // NOI18N
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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(listsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 18, Short.MAX_VALUE)
                .addComponent(searchBoxPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 244, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(listsButton, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(searchBoxPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 27, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void searchBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchBoxActionPerformed
        if (!entered) {
            return;
        }
        getRootPane().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            search();
        } finally {
            getRootPane().setCursor(null);
        }
    }//GEN-LAST:event_searchBoxActionPerformed

    private void settingsLabelMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_settingsLabelMousePressed
        maybeShowSettingsPopup(evt);
    }//GEN-LAST:event_settingsLabelMousePressed

    private void listsButtonMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_listsButtonMousePressed
        maybeShowListsPopup(evt);
    }//GEN-LAST:event_listsButtonMousePressed

    private void listsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_listsButtonActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_listsButtonActionPerformed

    private void searchButtonMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_searchButtonMousePressed
        searchBoxActionPerformed(null);
    }//GEN-LAST:event_searchButtonMousePressed

    private void settingsLabelMouseEntered(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_settingsLabelMouseEntered
        settingsLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/dropdown-icon-rollover.png")));
    }//GEN-LAST:event_settingsLabelMouseEntered

    private void settingsLabelMouseExited(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_settingsLabelMouseExited
        settingsLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/dropdown-icon.png")));
    }//GEN-LAST:event_settingsLabelMouseExited
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JMenuItem cutMenuItem;
    private javax.swing.JButton listsButton;
    private javax.swing.JPopupMenu listsMenu;
    private javax.swing.JMenuItem pasteMenuItem;
    private javax.swing.JCheckBoxMenuItem regExCheckboxMenuItem;
    private javax.swing.JPopupMenu rightClickMenu;
    private javax.swing.JTextField searchBox;
    private javax.swing.JPanel searchBoxPanel;
    private javax.swing.JLabel searchButton;
    private javax.swing.JMenuItem selectAllMenuItem;
    private javax.swing.JLabel settingsLabel;
    private javax.swing.JPopupMenu settingsMenu;
    // End of variables declaration//GEN-END:variables

    @Override
    public String getQueryText() {
        return searchBox.getText();
    }

    @Override
    public boolean isLuceneQuerySelected() {
        return !regExCheckboxMenuItem.isSelected();
    }

    @Override
    public boolean isMultiwordQuery() {
        return false;
    }

    @Override
    public List<Keyword> getQueryList() {
        throw new UnsupportedOperationException(
                NbBundle.getMessage(this.getClass(), "KeywordSearchPanel.getQueryList.exception.msg"));
    }

    private class KeywordPropertyChangeListener implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            String changed = evt.getPropertyName();
            Object oldValue = evt.getOldValue();
            Object newValue = evt.getNewValue();

            if (changed.equals(Case.Events.CURRENT_CASE.toString())) {
                resetSearchBox();
                if (newValue == null) {
                    setFields(false);
                } else {
                    setFields(true);
                }
            } else if (changed.equals(Server.CORE_EVT)) {
                final Server.CORE_EVT_STATES state = (Server.CORE_EVT_STATES) newValue;
                switch (state) {
                    case STARTED:
                        try {
                            final int numIndexedFiles = KeywordSearch.getServer().queryNumIndexedFiles();
                            KeywordSearch.fireNumIndexedFilesChange(null, new Integer(numIndexedFiles));
                            //setFilesIndexed(numIndexedFiles);
                        } 
                        catch (NoOpenCoreException ex) {
                            logger.log(Level.SEVERE, "Error executing Solr query, " + ex);
                        }
                        catch (KeywordSearchModuleException se) {
                            logger.log(Level.SEVERE, "Error executing Solr query, " + se.getMessage());
                        }
                        break;
                    case STOPPED:
                        break;
                    default:
                }
            }
        }

        private void setFields(boolean enabled) {
            searchBox.setEnabled(enabled);
            regExCheckboxMenuItem.setEnabled(enabled);
            settingsLabel.setEnabled(enabled);
            listsButton.setEnabled(enabled);
            searchButton.setEnabled(enabled);
            active = enabled;
        }
    }

    private void maybeShowSettingsPopup(MouseEvent evt) {
        if (!active) {
            return;
        }
        if (evt != null && !SwingUtilities.isLeftMouseButton(evt)) {
            return;
        }

        settingsMenu.show(searchBoxPanel, 0, searchBoxPanel.getHeight());
    }

    private void maybeShowListsPopup(MouseEvent evt) {
        if (!active) {
            return;
        }
        if (evt != null && !SwingUtilities.isLeftMouseButton(evt)) {
            return;
        }
        listsMenu.show(listsButton, listsButton.getWidth() - listsMenu.getWidth(), listsButton.getHeight() - 1);
    }
}
