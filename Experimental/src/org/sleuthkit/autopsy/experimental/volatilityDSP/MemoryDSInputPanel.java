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
package org.sleuthkit.autopsy.experimental.volatilityDSP;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.ModuleSettings;
import org.sleuthkit.autopsy.coreutils.PathValidator;

final class MemoryDSInputPanel extends JPanel implements DocumentListener {

    private static final long serialVersionUID = 1L;    //default
    private final String PROP_LASTINPUT_PATH = "LBL_LastInputFile_PATH";
    private final JFileChooser fc = new JFileChooser();
    // Externally supplied name is used to store settings 
    private final String contextName;
    private final String[] pluginList;
    private final PluginListTableModel tableModel = new PluginListTableModel();
    private final List<String> PluginListNames = new ArrayList<>();
    private final Map<String, Boolean> pluginListStates = new HashMap<>(); // is set by listeners when users select and deselect items

    /**
     * Creates new MemoryDSInputPanel panel for user input
     */
    private MemoryDSInputPanel(String context) {
        this.pluginList = new String[]{"amcache", "cmdline", "cmdscan", "consoles", "malfind", "netscan", "notepad", "pslist", "psxview", "shellbags", "shimcache", "shutdown", "userassist", "apihooks", "connscan", "devicetree", "dlllist", "envars", "filescan", "gahti", "getservicesids", "getsids", "handles", "hashdump", "hivelist", "hivescan", "impscan", "ldrmodules", "lsadump", "modules", "mutantscan", "privs", "psscan", "pstree", "sockets", "svcscan", "shimcache", "timeliner", "unloadedmodules", "userhandles", "vadinfo", "verinfo"};
        Arrays.sort(this.pluginList);

        initComponents();

        errorLabel.setVisible(false);

        fc.setDragEnabled(false);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);

        this.contextName = context;
    }

    /**
     * Creates and returns an instance the panel
     */
    static synchronized MemoryDSInputPanel createInstance(String context) {
        MemoryDSInputPanel instance = new MemoryDSInputPanel(context);

        instance.postInit();
        instance.customizePluginListTable();
        instance.createTimeZoneList();
        instance.createVolatilityVersionList();
        instance.createPluginList();

        return instance;
    }

    //post-constructor initialization to properly initialize listener support
    //without leaking references of uninitialized objects
    private void postInit() {
        pathTextField.getDocument().addDocumentListener(this);
    }

    private void customizePluginListTable() {
        PluginList.setModel(tableModel);
        PluginList.setTableHeader(null);
        PluginList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        final int width = listsScrollPane.getPreferredSize().width;
        PluginList.setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        TableColumn column;
        for (int i = 0; i < PluginList.getColumnCount(); i++) {
            column = PluginList.getColumnModel().getColumn(i);
            if (i == 0) {
                column.setPreferredWidth(((int) (width * 0.07)));
            } else {
                column.setPreferredWidth(((int) (width * 0.92)));
            }
        }
    }

    /**
     * Creates the drop down list for the time zones and then makes the local
     * machine time zone to be selected.
     */
    private void createTimeZoneList() {
        // load and add all timezone
        String[] ids = SimpleTimeZone.getAvailableIDs();
        for (String id : ids) {
            TimeZone zone = TimeZone.getTimeZone(id);
            int offset = zone.getRawOffset() / 1000;
            int hour = offset / 3600;
            int minutes = (offset % 3600) / 60;
            String item = String.format("(GMT%+d:%02d) %s", hour, minutes, id);

            timeZoneComboBox.addItem(item);
        }
        // get the current timezone
        TimeZone thisTimeZone = Calendar.getInstance().getTimeZone();
        int thisOffset = thisTimeZone.getRawOffset() / 1000;
        int thisHour = thisOffset / 3600;
        int thisMinutes = (thisOffset % 3600) / 60;
        String formatted = String.format("(GMT%+d:%02d) %s", thisHour, thisMinutes, thisTimeZone.getID());

        // set the selected timezone
        timeZoneComboBox.setSelectedItem(formatted);
    }

    private void createVolatilityVersionList() {

        volExecutableComboBox.addItem("2.6");
        volExecutableComboBox.addItem("2.5");

    }

    private void createPluginList() {
        PluginListNames.clear();
        pluginListStates.clear();

        // if the config file doesn't exist, then set them all to enabled
        boolean allEnabled = !ModuleSettings.configExists(this.contextName);
        Map<String, String> pluginMap = ModuleSettings.getConfigSettings(this.contextName);

        for (String plugin : pluginList) {
            PluginListNames.add(plugin);
            if (allEnabled) {
                pluginListStates.put(plugin, true);
            } else if ((pluginMap.containsKey(plugin) && pluginMap.get(plugin).equals("false"))) {
                pluginListStates.put(plugin, false);
            } else {
                pluginListStates.put(plugin, true);
            }
        }
        tableModel.fireTableDataChanged();
        //this.tableModel = pluginsToRun.getModel();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        infileTypeButtonGroup = new javax.swing.ButtonGroup();
        pathLabel = new javax.swing.JLabel();
        pathTextField = new javax.swing.JTextField();
        browseButton = new javax.swing.JButton();
        errorLabel = new javax.swing.JLabel();
        timeZoneLabel = new javax.swing.JLabel();
        timeZoneComboBox = new javax.swing.JComboBox<>();
        volExecutableLabel = new javax.swing.JLabel();
        volExecutableComboBox = new javax.swing.JComboBox<>();
        PluginsToRunLabel = new javax.swing.JLabel();
        listsScrollPane = new javax.swing.JScrollPane();
        PluginList = new javax.swing.JTable();

        org.openide.awt.Mnemonics.setLocalizedText(pathLabel, org.openide.util.NbBundle.getMessage(MemoryDSInputPanel.class, "MemoryDSInputPanel.pathLabel.text")); // NOI18N

        pathTextField.setText(org.openide.util.NbBundle.getMessage(MemoryDSInputPanel.class, "MemoryDSInputPanel.pathTextField.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(browseButton, org.openide.util.NbBundle.getMessage(MemoryDSInputPanel.class, "MemoryDSInputPanel.browseButton.text")); // NOI18N
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        errorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, org.openide.util.NbBundle.getMessage(MemoryDSInputPanel.class, "MemoryDSInputPanel.errorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(timeZoneLabel, org.openide.util.NbBundle.getMessage(MemoryDSInputPanel.class, "MemoryDSInputPanel.timeZoneLabel.text")); // NOI18N

        timeZoneComboBox.setMaximumRowCount(30);

        org.openide.awt.Mnemonics.setLocalizedText(volExecutableLabel, org.openide.util.NbBundle.getMessage(MemoryDSInputPanel.class, "MemoryDSInputPanel.volExecutableLabel.text")); // NOI18N

        volExecutableComboBox.setEnabled(false);
        volExecutableComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                volExecutableComboBoxActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(PluginsToRunLabel, org.openide.util.NbBundle.getMessage(MemoryDSInputPanel.class, "MemoryDSInputPanel.PluginsToRunLabel.text")); // NOI18N

        PluginList.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {},
                {},
                {},
                {}
            },
            new String [] {

            }
        ));
        listsScrollPane.setViewportView(PluginList);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pathTextField)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(browseButton, javax.swing.GroupLayout.PREFERRED_SIZE, 77, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pathLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 218, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(timeZoneLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 168, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(volExecutableComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, 199, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(listsScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 248, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addGap(0, 163, Short.MAX_VALUE))
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(errorLabel)
                    .addComponent(volExecutableLabel)
                    .addComponent(PluginsToRunLabel))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pathLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(browseButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(timeZoneLabel)
                    .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(errorLabel)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(volExecutableLabel)
                    .addComponent(volExecutableComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(PluginsToRunLabel)
                    .addComponent(listsScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(30, Short.MAX_VALUE))
        );

        pathLabel.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(MemoryDSInputPanel.class, "MemoryDSInputPanel.pathLabel.AccessibleContext.accessibleName")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents
    @SuppressWarnings("deprecation")
    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
        String oldText = pathTextField.getText();
        // set the current directory of the FileChooser if the ImagePath Field is valid
        File currentDir = new File(oldText);
        if (currentDir.exists()) {
            fc.setCurrentDirectory(currentDir);
        }

        int retval = fc.showOpenDialog(this);
        if (retval == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            pathTextField.setText(path);
        }
    }//GEN-LAST:event_browseButtonActionPerformed

    private void volExecutableComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_volExecutableComboBoxActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_volExecutableComboBoxActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTable PluginList;
    private javax.swing.JLabel PluginsToRunLabel;
    private javax.swing.JButton browseButton;
    private javax.swing.JLabel errorLabel;
    private javax.swing.ButtonGroup infileTypeButtonGroup;
    private javax.swing.JScrollPane listsScrollPane;
    private javax.swing.JLabel pathLabel;
    private javax.swing.JTextField pathTextField;
    private javax.swing.JComboBox<String> timeZoneComboBox;
    private javax.swing.JLabel timeZoneLabel;
    private javax.swing.JComboBox<String> volExecutableComboBox;
    private javax.swing.JLabel volExecutableLabel;
    // End of variables declaration//GEN-END:variables
    /**
     * Get the path of the user selected image.
     *
     * @return the image path
     */
    String getImageFilePath() {
        return pathTextField.getText();
    }

    List<String> getPluginsToRun() {
        List<String> enabledPlugins = new ArrayList<>();
        Map<String, String> pluginSettingsToSave = new HashMap<>();
        for (String plugin : PluginListNames) {
            if (pluginListStates.get(plugin)) {
                enabledPlugins.add(plugin);
            }
            pluginSettingsToSave.put(plugin, pluginListStates.get(plugin).toString());
        }
        ModuleSettings.setConfigSettings(this.contextName, pluginSettingsToSave);
        // @@ Could return keys of set
        return enabledPlugins;
    }

    void reset() {
        //reset the UI elements to default 
        pathTextField.setText(null);
    }

    String getTimeZone() {
        String tz = timeZoneComboBox.getSelectedItem().toString();
        return tz.substring(tz.indexOf(")") + 2).trim();
    }

    /**
     * Should we enable the next button of the wizard?
     *
     * @return true if a proper image has been selected, false otherwise
     */
    boolean validatePanel() {
        errorLabel.setVisible(false);
        String path = getImageFilePath();
        if (path == null || path.isEmpty()) {
            return false;
        }

        // display warning if there is one (but don't disable "next" button)
        warnIfPathIsInvalid(path);

        boolean isFile = new File(path).isFile();

        return (isFile);
    }

    /**
     * Validates path to selected data source and displays warning if it is
     * invalid.
     *
     * @param path Absolute path to the selected data source
     */
    @Messages({
        "MemoryDSInputPanel_errorMsg_noOpenCase=No open case",
        "MemoryDSInputPanel_errorMsg_dataSourcePathOnCdrive=Path to multi-user data source is on \"C:\" drive"
    })
    private void warnIfPathIsInvalid(String path) {
        try {
            if (!PathValidator.isValid(path, Case.getOpenCase().getCaseType())) {
                errorLabel.setVisible(true);
                errorLabel.setText(Bundle.MemoryDSInputPanel_errorMsg_dataSourcePathOnCdrive());
            }
        } catch (NoCurrentCaseException unused) {
            errorLabel.setVisible(true);
            errorLabel.setText(Bundle.MemoryDSInputPanel_errorMsg_dataSourcePathOnCdrive());
        }
    }

    void storeSettings() {
        String inFilePath = getImageFilePath();
        //String<List> inPlugins = 
        if (null != inFilePath) {
            String imagePath = inFilePath.substring(0, inFilePath.lastIndexOf(File.separator) + 1);
            ModuleSettings.setConfigSetting(contextName, PROP_LASTINPUT_PATH, imagePath);
        }
    }

    void readSettings() {
        String inFilePath = ModuleSettings.getConfigSetting(contextName, PROP_LASTINPUT_PATH);
        if (null != inFilePath) {
            if (!inFilePath.isEmpty()) {
                pathTextField.setText(inFilePath);
            }
        }
    }

    /**
     * Update functions are called by the pathTextField which has this set as
     * it's DocumentEventListener. Each update function fires a property change
     * to be caught by the parent panel.
     *
     * @param e the event, which is ignored
     */
    @Override
    public void insertUpdate(DocumentEvent e) {
        firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
    }

    /**
     * Set the focus to the pathTextField.
     */
    void select() {
        pathTextField.requestFocusInWindow();
    }

    private class PluginListTableModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return MemoryDSInputPanel.this.PluginListNames.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            String listName = MemoryDSInputPanel.this.PluginListNames.get(rowIndex);
            if (columnIndex == 0) {
                return pluginListStates.get(listName);
            } else {
                return listName;
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            String listName = MemoryDSInputPanel.this.PluginListNames.get(rowIndex);
            if (columnIndex == 0) {
                pluginListStates.put(listName, (Boolean) aValue);
            }
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return getValueAt(0, c).getClass();
        }
    }

}
