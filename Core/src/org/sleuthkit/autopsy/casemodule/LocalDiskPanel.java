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
package org.sleuthkit.autopsy.casemodule;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.LocalDisk;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.imagewriter.ImageWriterSettings;

@NbBundle.Messages({"LocalDiskPanel.refreshTablebutton.text=Refresh Local Disks",
    "LocalDiskPanel.listener.getOpenCase.errTitle=No open case available",
    "LocalDiskPanel.listener.getOpenCase.errMsg=LocalDiskPanel listener couldn't get the open case."
})
/**
 * ImageTypePanel for adding a local disk or partition such as PhysicalDrive0 or
 * C:.
 */
final class LocalDiskPanel extends JPanel {

    private static final Logger logger = Logger.getLogger(LocalDiskPanel.class.getName());
    private static final String[] SECTOR_SIZE_CHOICES = {"Auto Detect", "512", "1024", "2048", "4096"};
    private static LocalDiskPanel instance;
    private static final long serialVersionUID = 1L;
    private List<LocalDisk> disks;
    private boolean enableNext = false;
    private final LocalDiskModel model;
    private final JFileChooser fc = new JFileChooser();

    /**
     * Creates new form LocalDiskPanel
     */
    LocalDiskPanel() {
        this.model = new LocalDiskModel();

        this.disks = new ArrayList<>();
        initComponents();
        customInit();
        createTimeZoneList();
        createSectorSizeList();
        refreshTable();
        diskTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (diskTable.getSelectedRow() >= 0 && diskTable.getSelectedRow() < disks.size()) {
                    enableNext = true;
                    try {
                        setPotentialImageWriterPath(disks.get(diskTable.getSelectedRow()));
                        firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
                    } catch (NoCurrentCaseException ex) {
                        logger.log(Level.SEVERE, "Exception while getting open case.", e); //NON-NLS
                        MessageNotifyUtil.Notify.show(Bundle.LocalDiskPanel_listener_getOpenCase_errTitle(),
                                Bundle.LocalDiskPanel_listener_getOpenCase_errMsg(),
                                MessageNotifyUtil.MessageType.ERROR);
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, "LocalDiskPanel listener threw exception", e); //NON-NLS
                        MessageNotifyUtil.Notify.show(NbBundle.getMessage(this.getClass(), "LocalDiskPanel.moduleErr"),
                                NbBundle.getMessage(this.getClass(), "LocalDiskPanel.moduleErr.msg"),
                                MessageNotifyUtil.MessageType.ERROR);
                    }
                } else {  //The selection changed to nothing valid being selected, such as with ctrl+click
                    enableNext = false;
                    try {
                        firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, "LocalDiskPanel listener threw exception", e); //NON-NLS
                        MessageNotifyUtil.Notify.show(NbBundle.getMessage(this.getClass(), "LocalDiskPanel.moduleErr"),
                                NbBundle.getMessage(this.getClass(), "LocalDiskPanel.moduleErr.msg"),
                                MessageNotifyUtil.MessageType.ERROR);
                    }
                }
            }
        });
    }

    /**
     * Get the default instance of this panel.
     */
    static synchronized LocalDiskPanel getDefault() {
        if (instance == null) {
            instance = new LocalDiskPanel();
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    private void customInit() {
        errorLabel.setVisible(false);
        errorLabel.setText("");
        diskTable.setEnabled(false);
        imageWriterErrorLabel.setVisible(false);
        imageWriterErrorLabel.setText("");
        if (!PlatformUtil.isWindowsOS()) {
            copyImageCheckbox.setSelected(false);
            copyImageCheckbox.setEnabled(false);
        }
        pathTextField.setEnabled(copyImageCheckbox.isSelected());
        browseButton.setEnabled(copyImageCheckbox.isSelected());
        changeDatabasePathCheckbox.setEnabled(copyImageCheckbox.isSelected());
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        diskLabel = new javax.swing.JLabel();
        errorLabel = new javax.swing.JLabel();
        timeZoneLabel = new javax.swing.JLabel();
        timeZoneComboBox = new javax.swing.JComboBox<>();
        noFatOrphansCheckbox = new javax.swing.JCheckBox();
        descLabel = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        diskTable = new javax.swing.JTable();
        copyImageCheckbox = new javax.swing.JCheckBox();
        pathTextField = new javax.swing.JTextField();
        browseButton = new javax.swing.JButton();
        jLabel1 = new javax.swing.JLabel();
        imageWriterErrorLabel = new javax.swing.JLabel();
        changeDatabasePathCheckbox = new javax.swing.JCheckBox();
        refreshTableButton = new javax.swing.JButton();
        sectorSizeLabel = new javax.swing.JLabel();
        sectorSizeComboBox = new javax.swing.JComboBox<>();

        setMinimumSize(new java.awt.Dimension(0, 420));
        setPreferredSize(new java.awt.Dimension(485, 410));

        diskLabel.setFont(diskLabel.getFont().deriveFont(diskLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(diskLabel, org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.diskLabel.text")); // NOI18N

        errorLabel.setFont(errorLabel.getFont().deriveFont(errorLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        errorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(errorLabel, org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.errorLabel.text")); // NOI18N

        timeZoneLabel.setFont(timeZoneLabel.getFont().deriveFont(timeZoneLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(timeZoneLabel, org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.timeZoneLabel.text")); // NOI18N

        timeZoneComboBox.setFont(timeZoneComboBox.getFont().deriveFont(timeZoneComboBox.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        timeZoneComboBox.setMaximumRowCount(30);

        noFatOrphansCheckbox.setFont(noFatOrphansCheckbox.getFont().deriveFont(noFatOrphansCheckbox.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(noFatOrphansCheckbox, org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.noFatOrphansCheckbox.text")); // NOI18N
        noFatOrphansCheckbox.setToolTipText(org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.noFatOrphansCheckbox.toolTipText")); // NOI18N

        descLabel.setFont(descLabel.getFont().deriveFont(descLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        org.openide.awt.Mnemonics.setLocalizedText(descLabel, org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.descLabel.text")); // NOI18N

        diskTable.setModel(model);
        diskTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane1.setViewportView(diskTable);

        org.openide.awt.Mnemonics.setLocalizedText(copyImageCheckbox, org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.copyImageCheckbox.text")); // NOI18N
        copyImageCheckbox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                copyImageCheckboxActionPerformed(evt);
            }
        });

        pathTextField.setText(org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.pathTextField.text")); // NOI18N
        pathTextField.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                pathTextFieldKeyReleased(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(browseButton, org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.browseButton.text")); // NOI18N
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(jLabel1, org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.jLabel1.text")); // NOI18N

        imageWriterErrorLabel.setFont(imageWriterErrorLabel.getFont().deriveFont(imageWriterErrorLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        imageWriterErrorLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(imageWriterErrorLabel, org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.imageWriterErrorLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(changeDatabasePathCheckbox, org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.changeDatabasePathCheckbox.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(refreshTableButton, org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.refreshTableButton.text")); // NOI18N
        refreshTableButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshTableButtonActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(sectorSizeLabel, org.openide.util.NbBundle.getMessage(LocalDiskPanel.class, "LocalDiskPanel.sectorSizeLabel.text")); // NOI18N

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(diskLabel)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(refreshTableButton))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addComponent(timeZoneLabel)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(timeZoneComboBox, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addGap(21, 21, 21)
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                            .addComponent(jLabel1, javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(changeDatabasePathCheckbox, javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(imageWriterErrorLabel, javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(descLabel, javax.swing.GroupLayout.Alignment.LEADING)))
                                    .addComponent(copyImageCheckbox, javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(errorLabel, javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addComponent(sectorSizeLabel)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                        .addComponent(sectorSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                        .addGap(21, 21, 21)
                                        .addComponent(pathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 342, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(browseButton))
                                    .addComponent(noFatOrphansCheckbox, javax.swing.GroupLayout.Alignment.LEADING))
                                .addGap(0, 0, Short.MAX_VALUE)))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(diskLabel)
                .addGap(1, 1, 1)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(refreshTableButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(timeZoneLabel)
                    .addComponent(timeZoneComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(noFatOrphansCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(descLabel)
                .addGap(10, 10, 10)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(sectorSizeLabel)
                    .addComponent(sectorSizeComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(copyImageCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(pathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(browseButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(changeDatabasePathCheckbox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(imageWriterErrorLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(errorLabel)
                .addContainerGap(43, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void copyImageCheckboxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_copyImageCheckboxActionPerformed
        pathTextField.setEnabled(copyImageCheckbox.isSelected());
        browseButton.setEnabled(copyImageCheckbox.isSelected());
        changeDatabasePathCheckbox.setEnabled(copyImageCheckbox.isSelected());
        fireUpdateEvent();
    }//GEN-LAST:event_copyImageCheckboxActionPerformed

    private void pathTextFieldKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_pathTextFieldKeyReleased
        fireUpdateEvent();
    }//GEN-LAST:event_pathTextFieldKeyReleased

    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
        String oldText = pathTextField.getText();
        // set the current directory of the FileChooser if the ImagePath Field is valid
        File currentFile = new File(oldText);
        if ((currentFile.getParentFile() != null) && (currentFile.getParentFile().exists())) {
            fc.setSelectedFile(currentFile);
        }

        int retval = fc.showOpenDialog(this);
        if (retval == JFileChooser.APPROVE_OPTION) {
            String path = fc.getSelectedFile().getPath();
            pathTextField.setText(path);
        }
        fireUpdateEvent();
    }//GEN-LAST:event_browseButtonActionPerformed

    private void refreshTableButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshTableButtonActionPerformed
        refreshTable();
    }//GEN-LAST:event_refreshTableButtonActionPerformed

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.JCheckBox changeDatabasePathCheckbox;
    private javax.swing.JCheckBox copyImageCheckbox;
    private javax.swing.JLabel descLabel;
    private javax.swing.JLabel diskLabel;
    private javax.swing.JTable diskTable;
    private javax.swing.JLabel errorLabel;
    private javax.swing.JLabel imageWriterErrorLabel;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JCheckBox noFatOrphansCheckbox;
    private javax.swing.JTextField pathTextField;
    private javax.swing.JButton refreshTableButton;
    private javax.swing.JComboBox<String> sectorSizeComboBox;
    private javax.swing.JLabel sectorSizeLabel;
    private javax.swing.JComboBox<String> timeZoneComboBox;
    private javax.swing.JLabel timeZoneLabel;
    // End of variables declaration//GEN-END:variables

    private void fireUpdateEvent() {
        try {
            firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "LocalDiskPanel listener threw exception", e); //NON-NLS
            MessageNotifyUtil.Notify.show(NbBundle.getMessage(this.getClass(), "LocalDiskPanel.moduleErr"),
                    NbBundle.getMessage(this.getClass(), "LocalDiskPanel.moduleErr.msg"),
                    MessageNotifyUtil.MessageType.ERROR);
        }
    }

    /**
     * Return the currently selected disk path.
     *
     * @return String selected disk path
     */
    String getContentPaths() {
        if (disks.size() > 0) {
            int selectedRow = diskTable.getSelectedRow();
            LocalDisk selected = disks.get(selectedRow);
            return selected.getPath();
        } else {
            return "";
        }
    }

    /**
     * Get the sector size.
     *
     * @return 0 if autodetect; otherwise the value selected.
     */
    int getSectorSize() {
        int sectorSizeSelectionIndex = sectorSizeComboBox.getSelectedIndex();

        if (sectorSizeSelectionIndex == 0) {
            return 0;
        }

        return Integer.valueOf((String) sectorSizeComboBox.getSelectedItem());
    }

    String getTimeZone() {
        String tz = timeZoneComboBox.getSelectedItem().toString();
        return tz.substring(tz.indexOf(")") + 2).trim();

    }

    boolean getNoFatOrphans() {
        return noFatOrphansCheckbox.isSelected();
    }

    private static String getDefaultImageWriterFolder() throws NoCurrentCaseException {
        return Paths.get(Case.getOpenCase().getModuleDirectory(), "Image Writer").toString();
    }

    private void setPotentialImageWriterPath(LocalDisk disk) throws NoCurrentCaseException {

        File subDirectory = Paths.get(getDefaultImageWriterFolder()).toFile();
        if (!subDirectory.exists()) {
            subDirectory.mkdirs();
        }

        String path = disk.getName();

        // Remove any character that isn't alphanumeric, a space, parent, or underscore.
        // If the name ends up empty or starting with a space, prepend "localDisk"
        path = path.replaceAll("[^0-9A-Za-z _()]", ""); // NON-NLS
        if (path.isEmpty() || path.startsWith(" ")) {
            path = "localDisk" + path;
        }

        path += " " + System.currentTimeMillis();
        path += ".vhd";
        pathTextField.setText(Paths.get(getDefaultImageWriterFolder(), path).toString());
    }

    private boolean imageWriterPathIsValid() {
        if ((!copyImageCheckbox.isSelected()) || !(diskTable.getSelectedRow() >= 0 && diskTable.getSelectedRow() < disks.size())) {
            imageWriterErrorLabel.setVisible(false);
            imageWriterErrorLabel.setText("");
            return true;
        }

        if (pathTextField.getText().isEmpty()) {
            imageWriterErrorLabel.setVisible(true);
            imageWriterErrorLabel.setText(NbBundle.getMessage(this.getClass(), "LocalDiskPanel.imageWriterEmptyPathError.text"));
            return false;
        }

        File f = new File(pathTextField.getText());
        if (((f.getParentFile() != null) && (!f.getParentFile().exists()))
                || (f.getParentFile() == null)) {
            imageWriterErrorLabel.setVisible(true);
            imageWriterErrorLabel.setText(NbBundle.getMessage(this.getClass(), "LocalDiskPanel.imageWriterDirError.text"));
            return false;
        }
        if (f.isDirectory()) {
            imageWriterErrorLabel.setVisible(true);
            imageWriterErrorLabel.setText(NbBundle.getMessage(this.getClass(), "LocalDiskPanel.imageWriterIsDirError.text"));
            return false;
        }
        if (f.exists()) {
            imageWriterErrorLabel.setVisible(true);
            imageWriterErrorLabel.setText(NbBundle.getMessage(this.getClass(), "LocalDiskPanel.imageWriterFileExistsError.text"));
            return false;
        }
        imageWriterErrorLabel.setVisible(false);
        imageWriterErrorLabel.setText("");
        return true;
    }

    boolean getImageWriterEnabled() {
        return copyImageCheckbox.isSelected();
    }

    ImageWriterSettings getImageWriterSettings() {
        return new ImageWriterSettings(pathTextField.getText(), changeDatabasePathCheckbox.isSelected());
    }

    /**
     * Should we enable the wizard's next button? We control all the possible
     * selections except for Image Writer.
     *
     * @return true if panel is valid
     */
    boolean validatePanel() {
        if (!imageWriterPathIsValid()) {
            return false;
        }
        return enableNext;
    }

    /**
     * Refreshes the list of disks in the table.
     */
    public void refreshTable() {
        model.loadDisks();
    }

    /**
     * Creates the drop down list for the time zones and defaults the selection
     * to the local machine time zone.
     */
    public void createTimeZoneList() {
        // load and add all timezone
        String[] ids = SimpleTimeZone.getAvailableIDs();
        for (String id : ids) {
            TimeZone zone = TimeZone.getTimeZone(id);
            int offset = zone.getRawOffset() / 1000;
            int hour = offset / 3600;
            int minutes = (offset % 3600) / 60;
            String item = String.format("(GMT%+d:%02d) %s", hour, minutes, id); //NON-NLS

            /*
             * DateFormat dfm = new SimpleDateFormat("z");
             * dfm.setTimeZone(zone); boolean hasDaylight =
             * zone.useDaylightTime(); String first = dfm.format(new Date(2010,
             * 1, 1)); String second = dfm.format(new Date(2011, 6, 6)); int mid
             * = hour * -1; String result = first + Integer.toString(mid);
             * if(hasDaylight){ result = result + second; }
             * timeZoneComboBox.addItem(item + " (" + result + ")");
             */
            timeZoneComboBox.addItem(item);
        }
        // get the current timezone
        TimeZone thisTimeZone = Calendar.getInstance().getTimeZone();
        int thisOffset = thisTimeZone.getRawOffset() / 1000;
        int thisHour = thisOffset / 3600;
        int thisMinutes = (thisOffset % 3600) / 60;
        String formatted = String.format("(GMT%+d:%02d) %s", thisHour, thisMinutes, thisTimeZone.getID()); //NON-NLS

        // set the selected timezone
        timeZoneComboBox.setSelectedItem(formatted);

    }

    /**
     * Creates the drop down list for the sector size and defaults the selection
     * to "Auto Detect".
     */
    private void createSectorSizeList() {
        for (String choice : SECTOR_SIZE_CHOICES) {
            sectorSizeComboBox.addItem(choice);
        }
        sectorSizeComboBox.setSelectedIndex(0);
    }

    /**
     * Table model for displaing information from LocalDisk Objects in a table.
     */
    private class LocalDiskModel implements TableModel {

        private LocalDiskThread worker = null;
        private boolean ready = false;
        private volatile boolean loadingDisks = false;

        //private String SELECT = "Select a local disk:";
        private final String LOADING = NbBundle.getMessage(this.getClass(), "LocalDiskPanel.localDiskModel.loading.msg");
        private final String NO_DRIVES = NbBundle.getMessage(this.getClass(), "LocalDiskPanel.localDiskModel.nodrives.msg");

        private void loadDisks() {

            // if there is a worker already building the lists, then cancel it first.
            if (loadingDisks && worker != null) {
                worker.cancel(false);
            }

            // Clear the lists
            errorLabel.setText("");
            diskTable.setEnabled(false);
            ready = false;
            enableNext = false;
            loadingDisks = true;
            worker = new LocalDiskThread();
            worker.execute();
        }

        @Override
        public int getRowCount() {
            if (disks.isEmpty()) {
                return 0;
            }
            return disks.size();
        }

        @Override
        public int getColumnCount() {
            return 2;

        }

        @NbBundle.Messages({"LocalDiskPanel.diskTable.column1.title=Disk Name",
            "LocalDiskPanel.diskTable.column2.title=Disk Size"
        })

        @Override
        public String getColumnName(int columnIndex) {
            switch (columnIndex) {
                case 0:
                    return NbBundle.getMessage(this.getClass(), "LocalDiskPanel.diskTable.column1.title");
                case 1:
                    return NbBundle.getMessage(this.getClass(), "LocalDiskPanel.diskTable.column2.title");
                default:
                    return "Unnamed"; //NON-NLS
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (ready) {
                if (disks.isEmpty()) {
                    return NO_DRIVES;
                }
                switch (columnIndex) {
                    case 0:
                        return disks.get(rowIndex).getName();
                    case 1:
                        return disks.get(rowIndex).getReadableSize();
                    default:
                        return disks.get(rowIndex).getPath();
                }
            } else {
                return LOADING;
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            //setter does nothing they should not be able to modify table
        }

        @Override
        public void addTableModelListener(TableModelListener l) {

        }

        @Override
        public void removeTableModelListener(TableModelListener l) {

        }

        /**
         * Gets the lists of physical drives and partitions and combines them
         * into a list of disks.
         */
        class LocalDiskThread extends SwingWorker<Object, Void> {

            private final Logger logger = Logger.getLogger(LocalDiskThread.class.getName());
            private List<LocalDisk> physicalDrives = new ArrayList<>();
            private List<LocalDisk> partitions = new ArrayList<>();

            @Override
            protected Object doInBackground() throws Exception {
                // Populate the lists
                physicalDrives = new ArrayList<>();
                partitions = new ArrayList<>();
                physicalDrives = PlatformUtil.getPhysicalDrives();
                partitions = PlatformUtil.getPartitions();
                return null;
            }

            /**
             * Display any error messages that might of occurred when getting
             * the lists of physical drives or partitions.
             */
            private void displayErrors() {
                if (physicalDrives.isEmpty() && partitions.isEmpty()) {
                    if (PlatformUtil.isWindowsOS()) {
                        errorLabel.setText(
                                NbBundle.getMessage(this.getClass(), "LocalDiskPanel.errLabel.disksNotDetected.text"));
                        errorLabel.setToolTipText(NbBundle.getMessage(this.getClass(),
                                "LocalDiskPanel.errLabel.disksNotDetected.toolTipText"));
                    } else {
                        errorLabel.setText(
                                NbBundle.getMessage(this.getClass(), "LocalDiskPanel.errLabel.drivesNotDetected.text"));
                        errorLabel.setToolTipText(NbBundle.getMessage(this.getClass(),
                                "LocalDiskPanel.errLabel.drivesNotDetected.toolTipText"));
                    }
                    errorLabel.setVisible(true);
                    diskTable.setEnabled(false);
                } else if (physicalDrives.isEmpty()) {
                    errorLabel.setText(
                            NbBundle.getMessage(this.getClass(), "LocalDiskPanel.errLabel.someDisksNotDetected.text"));
                    errorLabel.setToolTipText(NbBundle.getMessage(this.getClass(),
                            "LocalDiskPanel.errLabel.someDisksNotDetected.toolTipText"));
                    errorLabel.setVisible(true);
                }
            }

            @Override
            protected void done() {
                try {
                    super.get(); //block and get all exceptions thrown while doInBackground()
                } catch (CancellationException ex) {
                    logger.log(Level.INFO, "Loading local disks was canceled."); //NON-NLS
                } catch (InterruptedException ex) {
                    logger.log(Level.INFO, "Loading local disks was interrupted."); //NON-NLS
                } catch (Exception ex) {
                    logger.log(Level.SEVERE, "Fatal error when loading local disks", ex); //NON-NLS
                } finally {
                    if (!this.isCancelled()) {
                        enableNext = false;
                        displayErrors();
                        worker = null;
                        loadingDisks = false;
                        disks = new ArrayList<>();
                        disks.addAll(physicalDrives);
                        disks.addAll(partitions);
                        if (disks.size() > 0) {
                            diskTable.setEnabled(true);
                            diskTable.clearSelection();
                        }
                        pathTextField.setText("");
                        fireUpdateEvent();
                        ready = true;
                    }
                }
                diskTable.revalidate();
            }
        }
    }
}
