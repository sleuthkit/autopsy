/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.DataSource;

/**
 * A dropdown panel that provides GUI components that allow a user to do three
 * types of ad hoc single keyword searches. The first option is a standard
 * Lucene query for one or more terms, with or without wildcards and explicit
 * Boolean operators, or a phrase. The second option is a Lucene query for a
 * substring of a single term. The third option is a regex query using first the
 * terms component, followed by standard Lucene queries for any terms found.
 *
 * The toolbar uses a different font from the rest of the application,
 * Monospaced 14, due to the necessity to find a font that displays both Arabic
 * and Asian fonts at an acceptable size. The default, Tahoma 14, could not
 * perform this task at the desired size, and neither could numerous other
 * fonts.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class DropdownSingleTermSearchPanel extends AdHocSearchPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DropdownSingleTermSearchPanel.class.getName());
    private static DropdownSingleTermSearchPanel defaultInstance = null;
    private Map<Long, String> dataSourceMap = new HashMap<>();
    private List<String> toolTipList = new ArrayList<>();

    /**
     * Gets the default instance of a dropdown panel that provides GUI
     * components that allow a user to do three types of ad hoc single keyword
     * searches.
     *
     * @return the default instance of DropdownSingleKeywordSearchPanel
     */
    public static synchronized DropdownSingleTermSearchPanel getDefault() {
        if (null == defaultInstance) {
            defaultInstance = new DropdownSingleTermSearchPanel();
        }
        return defaultInstance;
    }

    /**
     * Constructs a dropdown panel that provides GUI components that allow a
     * user to do three types of ad hoc single keyword searches.
     */
    private DropdownSingleTermSearchPanel() {
        initComponents();
        customizeComponents();
        this.dataSourceList.addListSelectionListener((ListSelectionEvent e) -> {
            firePropertyChange("Ad Hoc Search test1", null, null);
        });
        this.dataSourceList.addMouseMotionListener(new MouseMotionListener() {

            @Override
            public void mouseDragged(MouseEvent e) {
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                JList<String> DsList = (JList<String>) e.getSource();
                int index = DsList.locationToIndex(e.getPoint());
                if (index > -1) {
                    DsList.setToolTipText(toolTipList.get(index));
                }
            }
        });
    }

    /**
     * Does additional initialization of the GUI components created by the
     * initComponents method.
     */
    private void customizeComponents() {
        keywordTextField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (keywordTextField.getText().isEmpty()) {
                    clearSearchBox();
                }
            }
        });

        keywordTextField.setComponentPopupMenu(rightClickMenu);
        ActionListener actList = (ActionEvent e) -> {
            JMenuItem jmi = (JMenuItem) e.getSource();
            if (jmi.equals(cutMenuItem)) {
                keywordTextField.cut();
            } else if (jmi.equals(copyMenuItem)) {
                keywordTextField.copy();
            } else if (jmi.equals(pasteMenuItem)) {
                keywordTextField.paste();
            } else if (jmi.equals(selectAllMenuItem)) {
                keywordTextField.selectAll();
            }
        };
        JPanel p = new JPanel();
        p.setToolTipText("new panel");
        p.add(new JLabel("P panel"));
        cutMenuItem.addActionListener(actList);
        copyMenuItem.addActionListener(actList);
        pasteMenuItem.addActionListener(actList);
        selectAllMenuItem.addActionListener(actList);
    }

    /**
     * Add an action listener to the Search buttom component of the panel.
     *
     * @param actionListener The actin listener.
     */
    void addSearchButtonActionListener(ActionListener actionListener) {
        searchButton.addActionListener(actionListener);
    }

    /**
     * Clears the text in the query text field, i.e., sets it to the emtpy
     * string.
     */
    void clearSearchBox() {
        keywordTextField.setText("");
    }

    void setRegexSearchEnabled(boolean enabled) {
        exactRadioButton.setSelected(true);
        regexRadioButton.setEnabled(enabled);
    }

    /**
     * Gets a single keyword list consisting of a single keyword encapsulating
     * the input term(s)/phrase/substring/regex.
     *
     * @return The keyword list.
     */
    @NbBundle.Messages({"DropdownSingleTermSearchPanel.warning.title=Warning",
        "DropdownSingleTermSearchPanel.warning.text=Boundary characters ^ and $ do not match word boundaries. Consider\nreplacing with an explicit list of boundary characters, such as [ \\.,]"})
    @Override
    List<KeywordList> getKeywordLists() {

        if (regexRadioButton.isSelected()) {
            if ((keywordTextField.getText() != null)
                    && (keywordTextField.getText().startsWith("^")
                    || (keywordTextField.getText().endsWith("$") && !keywordTextField.getText().endsWith("\\$")))) {

                KeywordSearchUtil.displayDialog(NbBundle.getMessage(this.getClass(), "DropdownSingleTermSearchPanel.warning.title"),
                        NbBundle.getMessage(this.getClass(), "DropdownSingleTermSearchPanel.warning.text"),
                        KeywordSearchUtil.DIALOG_MESSAGE_TYPE.WARN);
            }
        }

        List<Keyword> keywords = new ArrayList<>();
        keywords.add(new Keyword(keywordTextField.getText(), !regexRadioButton.isSelected(), exactRadioButton.isSelected()));
        List<KeywordList> keywordLists = new ArrayList<>();
        keywordLists.add(new KeywordList(keywords));
        return keywordLists;
    }

    /**
     * Not implemented.
     */
    @Override
    protected void postFilesIndexedChange() {
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        queryTypeButtonGroup = new javax.swing.ButtonGroup();
        rightClickMenu = new javax.swing.JPopupMenu();
        cutMenuItem = new javax.swing.JMenuItem();
        copyMenuItem = new javax.swing.JMenuItem();
        pasteMenuItem = new javax.swing.JMenuItem();
        selectAllMenuItem = new javax.swing.JMenuItem();
        jPanel1 = new javax.swing.JPanel();
        keywordTextField = new javax.swing.JTextField();
        searchButton = new javax.swing.JButton();
        exactRadioButton = new javax.swing.JRadioButton();
        substringRadioButton = new javax.swing.JRadioButton();
        regexRadioButton = new javax.swing.JRadioButton();
        dataSourcePanel = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        dataSourceCheckBox = new javax.swing.JCheckBox();
        jScrollPane1 = new javax.swing.JScrollPane();
        dataSourceList = new javax.swing.JList<>();

        org.openide.awt.Mnemonics.setLocalizedText(cutMenuItem, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.cutMenuItem.text")); // NOI18N
        rightClickMenu.add(cutMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(copyMenuItem, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(pasteMenuItem, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.pasteMenuItem.text")); // NOI18N
        rightClickMenu.add(pasteMenuItem);

        org.openide.awt.Mnemonics.setLocalizedText(selectAllMenuItem, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 100, Short.MAX_VALUE)
        );

        keywordTextField.setFont(new java.awt.Font("Monospaced", 0, 14)); // NOI18N
        keywordTextField.setText(org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.keywordTextField.text")); // NOI18N
        keywordTextField.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(192, 192, 192), 1, true));
        keywordTextField.setMinimumSize(new java.awt.Dimension(2, 25));
        keywordTextField.setPreferredSize(new java.awt.Dimension(2, 25));
        keywordTextField.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                keywordTextFieldMouseClicked(evt);
            }
        });
        keywordTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                keywordTextFieldActionPerformed(evt);
            }
        });

        searchButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/keywordsearch/search-icon.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(searchButton, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.searchButton.text")); // NOI18N
        searchButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                searchButtonActionPerformed(evt);
            }
        });

        queryTypeButtonGroup.add(exactRadioButton);
        exactRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(exactRadioButton, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.exactRadioButton.text")); // NOI18N

        queryTypeButtonGroup.add(substringRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(substringRadioButton, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.substringRadioButton.text")); // NOI18N

        queryTypeButtonGroup.add(regexRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(regexRadioButton, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSearchPanel.regexRadioButton.text")); // NOI18N

        dataSourcePanel.setPreferredSize(new java.awt.Dimension(30, 100));

        javax.swing.GroupLayout dataSourcePanelLayout = new javax.swing.GroupLayout(dataSourcePanel);
        dataSourcePanel.setLayout(dataSourcePanelLayout);
        dataSourcePanelLayout.setHorizontalGroup(
            dataSourcePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 0, Short.MAX_VALUE)
        );
        dataSourcePanelLayout.setVerticalGroup(
            dataSourcePanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 85, Short.MAX_VALUE)
        );

        jLabel1.setFont(new java.awt.Font("Tahoma", 0, 10)); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSingleTermSearchPanel.jLabel1.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(dataSourceCheckBox, org.openide.util.NbBundle.getMessage(DropdownSingleTermSearchPanel.class, "DropdownSingleTermSearchPanel.dataSourceCheckBox.text")); // NOI18N
        dataSourceCheckBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataSourceCheckBoxActionPerformed(evt);
            }
        });

        dataSourceList.setMinimumSize(new java.awt.Dimension(0, 200));
        jScrollPane1.setViewportView(dataSourceList);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(keywordTextField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(searchButton))
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(exactRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(substringRadioButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(regexRadioButton)
                        .addGap(0, 5, Short.MAX_VALUE))
                    .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(dataSourceCheckBox)
                            .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 297, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(5, 5, 5)
                        .addComponent(dataSourcePanel, javax.swing.GroupLayout.DEFAULT_SIZE, 10, Short.MAX_VALUE)))
                .addContainerGap())
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(keywordTextField, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(searchButton, javax.swing.GroupLayout.DEFAULT_SIZE, 26, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(exactRadioButton)
                    .addComponent(substringRadioButton)
                    .addComponent(regexRadioButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(dataSourcePanel, javax.swing.GroupLayout.PREFERRED_SIZE, 85, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(dataSourceCheckBox)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Action performed by the action listener for the search button.
     *
     * @param evt The action event.
     */
    private void searchButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_searchButtonActionPerformed
        keywordTextFieldActionPerformed(evt);
    }//GEN-LAST:event_searchButtonActionPerformed

    /**
     * Action performed by the action listener for the keyword text field.
     *
     * @param evt The action event.
     */
    private void keywordTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_keywordTextFieldActionPerformed
        try {
            search();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error performing ad hoc single keyword search", e); //NON-NLS
        }
    }//GEN-LAST:event_keywordTextFieldActionPerformed

    /**
     * Mouse event handler for the keyword text field.
     *
     * @param evt The mouse event.
     */
    private void keywordTextFieldMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_keywordTextFieldMouseClicked
        if (evt.isPopupTrigger()) {
            rightClickMenu.show(evt.getComponent(), evt.getX(), evt.getY());
        }
    }//GEN-LAST:event_keywordTextFieldMouseClicked

    private void dataSourceCheckBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataSourceCheckBoxActionPerformed
        dataSourceList.setModel(new javax.swing.AbstractListModel<String>() {
            List<String> strings = getDataSourceArray();

            public int getSize() {
                return strings.size();
            }

            public String getElementAt(int idx) {
                return strings.get(idx);
            }
        });

        setComponentsEnabled();
        firePropertyChange("Ad Hoc Search Test", null, null);
        
    }//GEN-LAST:event_dataSourceCheckBoxActionPerformed

    void setComponentsEnabled() {
        boolean enabled = this.dataSourceCheckBox.isSelected();
        this.dataSourceList.setVisible(enabled);
        this.dataSourceList.setEnabled(enabled);
        this.jLabel1.setEnabled(enabled);
        if (enabled) {
            this.dataSourceList.setSelectionInterval(0, this.dataSourceList.getModel().getSize()-1);
        } else {
            this.dataSourceList.setSelectedIndices(new int[0]);
        }
    }

    private List<String> getDataSourceArray() {
        List<String> dsList = new ArrayList<>();
        try {
            Case currentCase = Case.getCurrentCaseThrows();
            SleuthkitCase tskDb = currentCase.getSleuthkitCase();
            List<DataSource> dataSources = tskDb.getDataSources();
            Collections.sort(dataSources, (DataSource ds1, DataSource ds2) -> ds1.getName().compareTo(ds2.getName()));
            for (DataSource ds : dataSources) {
                String dsName = ds.getName();
                File dataSourceFullName = new File(dsName);
                String displayName = dataSourceFullName.getName();
                dataSourceMap.put(ds.getId(), displayName);  
                toolTipList.add(dsName);
                dsList.add(displayName);
            }
        } catch (NoCurrentCaseException ex) {
            LOGGER.log(Level.SEVERE, "Unable to get current open case.", ex);
        } catch (TskCoreException ex) {
            LOGGER.log(Level.SEVERE, "Failed to get data source info from database.", ex);
        }
        return dsList;
    }

    /**
     * Get a set of data source object ids that are selected.
     * @return A set of selected object ids. 
     */
    Set<Long> getDataSourcesSelected() {
        Set<Long> dataSourceObjIdSet = new HashSet<>();
        for (Long key : dataSourceMap.keySet()) {
            String value = dataSourceMap.get(key);
            for (String dataSource : this.dataSourceList.getSelectedValuesList()) {
                if (value.equals(dataSource)) {
                    dataSourceObjIdSet.add(key);
                }
            }
        }
        return dataSourceObjIdSet;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JMenuItem cutMenuItem;
    private javax.swing.JCheckBox dataSourceCheckBox;
    private javax.swing.JList<String> dataSourceList;
    private javax.swing.JPanel dataSourcePanel;
    private javax.swing.JRadioButton exactRadioButton;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextField keywordTextField;
    private javax.swing.JMenuItem pasteMenuItem;
    private javax.swing.ButtonGroup queryTypeButtonGroup;
    private javax.swing.JRadioButton regexRadioButton;
    private javax.swing.JPopupMenu rightClickMenu;
    private javax.swing.JButton searchButton;
    private javax.swing.JMenuItem selectAllMenuItem;
    private javax.swing.JRadioButton substringRadioButton;
    // End of variables declaration//GEN-END:variables
}
