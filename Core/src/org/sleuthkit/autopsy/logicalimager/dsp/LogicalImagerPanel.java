/*
 * Autopsy
 *
 * Copyright 2019-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.logicalimager.dsp;

import java.awt.Color;
import java.awt.Component;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataSourceProcessor;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.guiutils.JFileChooserFactory;

/**
 * Panel for adding an logical image file from drive letters. Allows the user to
 * select a file.
 */
@Messages({
    "LogicalImagerPanel.messageLabel.noImageSelected=No image selected",
    "LogicalImagerPanel.messageLabel.driveHasNoImages=Drive has no images",
    "LogicalImagerPanel.selectAcquisitionFromDriveLabel.text=Select acquisition from Drive",})
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final class LogicalImagerPanel extends JPanel implements DocumentListener {

    private static final Logger logger = Logger.getLogger(LogicalImagerPanel.class.getName());
    private static final long serialVersionUID = 1L;
    private static final String NO_IMAGE_SELECTED = Bundle.LogicalImagerPanel_messageLabel_noImageSelected();
    private static final String DRIVE_HAS_NO_IMAGES = Bundle.LogicalImagerPanel_messageLabel_driveHasNoImages();
    private static final int COLUMN_TO_SORT_ON_INDEX = 1;
    private static final int NUMBER_OF_VISIBLE_COLUMNS = 2;
    private static final String[] EMPTY_LIST_DATA = {};

    private final Pattern regex = Pattern.compile("Logical_Imager_(.+)_(\\d{4})(\\d{2})(\\d{2})_(\\d{2})_(\\d{2})_(\\d{2})");
    private Path manualImageDirPath;
    private DefaultTableModel imageTableModel;
    private final JFileChooserFactory chooserHelper;

    /**
     * Creates new form LogicalImagerPanel
     *
     * @param context A string context name used to read/store last used
     *                settings.
     */
    private LogicalImagerPanel(String context) {
        initComponents();
        configureImageTable();
        jScrollPane1.setBorder(null);
        clearImageTable();
        chooserHelper = new JFileChooserFactory();
    }

    /**
     * Perform the Image Table configuration necessary when a new table model is
     * set.
     */
    private void configureImageTable() {
        //hide path column while leaving it in model
        if (imageTable.getColumnCount() > NUMBER_OF_VISIBLE_COLUMNS) {
            TableColumn columnToHide = imageTable.getColumn(imageTableModel.getColumnName(NUMBER_OF_VISIBLE_COLUMNS));
            if (columnToHide != null) {
                imageTable.removeColumn(columnToHide);
            }
            //sort on specified column in decending order, the first call will toggle to ascending order, the second to descending order
            imageTable.getRowSorter().toggleSortOrder(COLUMN_TO_SORT_ON_INDEX);
            imageTable.getRowSorter().toggleSortOrder(COLUMN_TO_SORT_ON_INDEX);
        }
    }

    /**
     * Creates and returns an instance of a LogicalImagerPanel.
     *
     * @param context A string context name used to read/store last used
     *                settings.
     *
     * @return instance of the LogicalImagerPanel
     */
    static synchronized LogicalImagerPanel createInstance(String context) {
        LogicalImagerPanel instance = new LogicalImagerPanel(context);
        // post-constructor initialization of listener support without leaking references of uninitialized objects
        instance.imageTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        return instance;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        buttonGroup1 = new javax.swing.ButtonGroup();
        browseButton = new javax.swing.JButton();
        importRadioButton = new javax.swing.JRadioButton();
        manualRadioButton = new javax.swing.JRadioButton();
        pathTextField = new javax.swing.JTextField();
        selectFolderLabel = new javax.swing.JLabel();
        selectDriveLabel = new javax.swing.JLabel();
        selectFromDriveLabel = new javax.swing.JLabel();
        driveListScrollPane = new javax.swing.JScrollPane();
        driveList = new javax.swing.JList<>();
        refreshButton = new javax.swing.JButton();
        imageScrollPane = new javax.swing.JScrollPane();
        imageTable = new javax.swing.JTable();
        jSeparator2 = new javax.swing.JSeparator();
        jScrollPane1 = new javax.swing.JScrollPane();
        messageTextArea = new javax.swing.JTextArea();

        setMinimumSize(new java.awt.Dimension(0, 65));
        setPreferredSize(new java.awt.Dimension(403, 65));

        org.openide.awt.Mnemonics.setLocalizedText(browseButton, org.openide.util.NbBundle.getMessage(LogicalImagerPanel.class, "LogicalImagerPanel.browseButton.text")); // NOI18N
        browseButton.setEnabled(false);
        browseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                browseButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(importRadioButton);
        importRadioButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(importRadioButton, org.openide.util.NbBundle.getMessage(LogicalImagerPanel.class, "LogicalImagerPanel.importRadioButton.text")); // NOI18N
        importRadioButton.setToolTipText("");
        importRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importRadioButtonActionPerformed(evt);
            }
        });

        buttonGroup1.add(manualRadioButton);
        org.openide.awt.Mnemonics.setLocalizedText(manualRadioButton, org.openide.util.NbBundle.getMessage(LogicalImagerPanel.class, "LogicalImagerPanel.manualRadioButton.text")); // NOI18N
        manualRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manualRadioButtonActionPerformed(evt);
            }
        });

        pathTextField.setDisabledTextColor(java.awt.Color.black);
        pathTextField.setEnabled(false);

        org.openide.awt.Mnemonics.setLocalizedText(selectFolderLabel, org.openide.util.NbBundle.getMessage(LogicalImagerPanel.class, "LogicalImagerPanel.selectFolderLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(selectDriveLabel, org.openide.util.NbBundle.getMessage(LogicalImagerPanel.class, "LogicalImagerPanel.selectDriveLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(selectFromDriveLabel, org.openide.util.NbBundle.getMessage(LogicalImagerPanel.class, "LogicalImagerPanel.selectFromDriveLabel.text")); // NOI18N

        driveList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        driveList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                driveListMouseReleased(evt);
            }
        });
        driveList.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                driveListKeyReleased(evt);
            }
        });
        driveListScrollPane.setViewportView(driveList);

        org.openide.awt.Mnemonics.setLocalizedText(refreshButton, org.openide.util.NbBundle.getMessage(LogicalImagerPanel.class, "LogicalImagerPanel.refreshButton.text")); // NOI18N
        refreshButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });

        imageScrollPane.setPreferredSize(new java.awt.Dimension(346, 402));

        imageTable.setAutoCreateRowSorter(true);
        imageTable.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {

            }
        ));
        imageTable.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_OFF);
        imageTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        imageTable.setShowHorizontalLines(false);
        imageTable.setShowVerticalLines(false);
        imageTable.getTableHeader().setReorderingAllowed(false);
        imageTable.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                imageTableMouseReleased(evt);
            }
        });
        imageTable.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                imageTableKeyReleased(evt);
            }
        });
        imageScrollPane.setViewportView(imageTable);
        imageTable.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        jScrollPane1.setBorder(null);

        messageTextArea.setBackground(new java.awt.Color(240, 240, 240));
        messageTextArea.setColumns(20);
        messageTextArea.setForeground(java.awt.Color.red);
        messageTextArea.setLineWrap(true);
        messageTextArea.setRows(3);
        messageTextArea.setBorder(null);
        messageTextArea.setDisabledTextColor(java.awt.Color.red);
        messageTextArea.setEnabled(false);
        messageTextArea.setMargin(new java.awt.Insets(0, 0, 0, 0));
        jScrollPane1.setViewportView(messageTextArea);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(selectFolderLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 81, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(13, 13, 13)
                        .addComponent(pathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 474, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                        .addComponent(jSeparator2, javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGap(41, 41, 41)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(driveListScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 160, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(refreshButton))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(imageScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 377, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(20, 20, 20)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(manualRadioButton)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(browseButton))
                                    .addComponent(importRadioButton)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGap(21, 21, 21)
                                        .addComponent(selectDriveLabel)
                                        .addGap(113, 113, 113)
                                        .addComponent(selectFromDriveLabel))))))
                    .addGroup(layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 568, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(93, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addComponent(importRadioButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(selectDriveLabel)
                    .addComponent(selectFromDriveLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(imageScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(driveListScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 198, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(refreshButton)
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(browseButton)
                    .addComponent(manualRadioButton))
                .addGap(18, 18, 18)
                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(selectFolderLabel)
                    .addComponent(pathTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 61, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(6, 6, 6))
        );
    }// </editor-fold>//GEN-END:initComponents

    @Messages({
        "# {0} - sparseImageDirectory",
        "LogicalImagerPanel.messageLabel.directoryDoesNotContainSparseImage=Directory {0} does not contain any images",
        "# {0} - invalidFormatDirectory",
        "LogicalImagerPanel.messageLabel.directoryFormatInvalid=Directory {0} does not match format Logical_Imager_HOSTNAME_yyyymmdd_HH_MM_SS"
    })
    private void browseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_browseButtonActionPerformed
        JFileChooser fileChooser = chooserHelper.getChooser();
        imageTable.clearSelection();
        manualImageDirPath = null;
        setErrorMessage(NO_IMAGE_SELECTED);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int retval = fileChooser.showOpenDialog(this);
        if (retval == JFileChooser.APPROVE_OPTION) {
            String path = fileChooser.getSelectedFile().getPath();
            Matcher m = regex.matcher(path);
            if (m.find()) {
                File dir = Paths.get(path).toFile();
                String[] vhdFiles = dir.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.endsWith(".vhd");
                    }
                });
                if (vhdFiles.length == 0) {
                    // No VHD files, try directories for individual files
                    String[] directories = dir.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return Paths.get(dir.toString(), name).toFile().isDirectory();
                        }
                    });
                    if (directories.length == 0) {
                        // No directories, bail
                        setErrorMessage(Bundle.LogicalImagerPanel_messageLabel_directoryDoesNotContainSparseImage(path));
                        firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), true, false);
                        return;
                    }
                }
                manualImageDirPath = Paths.get(path);
                setNormalMessage(path);
                firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
            } else {
                setErrorMessage(Bundle.LogicalImagerPanel_messageLabel_directoryFormatInvalid(path));
                firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), true, false);
            }
        } else {
            firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), true, false);
        }
    }//GEN-LAST:event_browseButtonActionPerformed

    private void imageTableSelect() {
        int index = imageTable.getSelectedRow();
        if (index != -1) {
            setNormalMessage((String) imageTableModel.getValueAt(imageTable.convertRowIndexToModel(index), 2));
            firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), false, true);
        } else {
            setErrorMessage(NO_IMAGE_SELECTED);
            firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), true, false);
        }
    }

    private boolean dirHasImagerResult(File dir) {
        String[] fList = dir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".vhd") || Paths.get(dir.toString(), name).toFile().isDirectory();
            }
        });
        return (fList != null && fList.length != 0);
    }

    private void driveListSelect() {
        String selectedStr = driveList.getSelectedValue();
        if (selectedStr == null) {
            return;
        }
        String driveLetter = selectedStr.substring(0, 3);
        File directory = new File(driveLetter);
        File[] fList = directory.listFiles();

        if (fList != null) {
            imageTableModel = new ImageTableModel();
            // Find all directories with name like Logical_Imager_HOSTNAME_yyyymmdd_HH_MM_SS
            // and has Logical Imager result in it
            for (File file : fList) {
                if (file.isDirectory() && dirHasImagerResult(file)) {
                    String dir = file.getName();
                    Matcher m = regex.matcher(dir);
                    if (m.find()) {
                        String imageDirPath = driveLetter + dir;
                        String hostname = m.group(1);
                        String year = m.group(2);
                        String month = m.group(3);
                        String day = m.group(4);
                        String hour = m.group(5);
                        String minute = m.group(6);
                        String second = m.group(7);
                        String extractDate = year + "/" + month + "/" + day
                                + " " + hour + ":" + minute + ":" + second;
                        imageTableModel.addRow(new Object[]{hostname, extractDate, imageDirPath});
                    }
                }
            }
            selectFromDriveLabel.setText(Bundle.LogicalImagerPanel_selectAcquisitionFromDriveLabel_text()
                    + " " + driveLetter);
            imageTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
            imageTable.setModel(imageTableModel);
            configureImageTable();
            fixImageTableColumnWidth();
            // If there are any images, select the first one
            if (imageTable.getRowCount() > 0) {
                imageTable.setRowSelectionInterval(0, 0);
                imageTableSelect();
            } else {
                setErrorMessage(DRIVE_HAS_NO_IMAGES);
            }
        } else {
            clearImageTable();
            setErrorMessage(DRIVE_HAS_NO_IMAGES);
        }
    }

    private void fixImageTableColumnWidth() {
        int width = imageScrollPane.getPreferredSize().width - 2;
        imageTable.getColumnModel().getColumn(0).setPreferredWidth((int) (.60 * width));
        imageTable.getColumnModel().getColumn(1).setPreferredWidth((int) (.40 * width));
    }

    private void setErrorMessage(String msg) {
        messageTextArea.setForeground(Color.red);
        messageTextArea.setText(msg);
        pathTextField.setText("");
    }

    private void setNormalMessage(String msg) {
        pathTextField.setText(msg);
        messageTextArea.setText("");
    }

    private void clearImageTable() {
        imageTableModel = new ImageTableModel();
        imageTable.setModel(imageTableModel);
        configureImageTable();
        fixImageTableColumnWidth();
    }

    private void toggleMouseAndKeyListeners(Component component, boolean isEnable) {
        component.setEnabled(isEnable);
    }

    private void manualRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manualRadioButtonActionPerformed
        browseButton.setEnabled(true);

        // disable import panel
        toggleMouseAndKeyListeners(driveList, false);
        toggleMouseAndKeyListeners(driveListScrollPane, false);
        toggleMouseAndKeyListeners(imageScrollPane, false);
        toggleMouseAndKeyListeners(imageTable, false);

        refreshButton.setEnabled(false);

        manualImageDirPath = null;
        setNormalMessage("");
        firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), true, false);
    }//GEN-LAST:event_manualRadioButtonActionPerformed

    private void importRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importRadioButtonActionPerformed
        browseButton.setEnabled(false);

        toggleMouseAndKeyListeners(driveList, true);
        toggleMouseAndKeyListeners(driveListScrollPane, true);
        toggleMouseAndKeyListeners(imageScrollPane, true);
        toggleMouseAndKeyListeners(imageTable, true);

        refreshButton.setEnabled(true);

        manualImageDirPath = null;
        setNormalMessage("");
        refreshButton.doClick();
    }//GEN-LAST:event_importRadioButtonActionPerformed

    @Messages({
        "LogicalImagerPanel.messageLabel.scanningExternalDrives=Scanning external drives for images ...",
        "LogicalImagerPanel.messageLabel.noExternalDriveFound=No drive found"
    })
    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_refreshButtonActionPerformed
        // Scan external drives for vhd images
        clearImageTable();
        setNormalMessage(Bundle.LogicalImagerPanel_messageLabel_scanningExternalDrives());
        List<String> listData = new ArrayList<>();
        File[] roots = File.listRoots();
        int firstRemovableDrive = -1;
        int i = 0;
        for (File root : roots) {
            if (DriveListUtils.isNetworkDrive(root.toString().replace(":\\", ""))) {
                continue;
            }
            String description = FileSystemView.getFileSystemView().getSystemTypeDescription(root);
            long spaceInBytes = root.getTotalSpace();
            String sizeWithUnit = DriveListUtils.humanReadableByteCount(spaceInBytes, false);
            listData.add(root + " (" + description + ") (" + sizeWithUnit + ")");
            if (firstRemovableDrive == -1) {
                try {
                    FileStore fileStore = Files.getFileStore(root.toPath());
                    if ((boolean) fileStore.getAttribute("volume:isRemovable")) { //NON-NLS
                        firstRemovableDrive = i;
                    }
                } catch (IOException ignored) {
                    //unable to get this removable drive for default selection will try and select next removable drive by default 
                    logger.log(Level.INFO, String.format("Unable to select first removable drive found: %s", ignored.getMessage()));
                }
            }
            i++;
        }
        driveList.setListData(listData.toArray(new String[listData.size()]));
        if (!listData.isEmpty()) {
            // auto-select the first external drive, if any
            driveList.setSelectedIndex(firstRemovableDrive == -1 ? 0 : firstRemovableDrive);
            driveListMouseReleased(null);
            driveList.requestFocusInWindow();
        } else {
            setErrorMessage(Bundle.LogicalImagerPanel_messageLabel_noExternalDriveFound());
        }
        firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), true, false);
    }//GEN-LAST:event_refreshButtonActionPerformed

    private void driveListKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_driveListKeyReleased
        if (importRadioButton.isSelected()) {
            driveListSelect();
            firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), true, false);
        }
    }//GEN-LAST:event_driveListKeyReleased

    private void imageTableKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_imageTableKeyReleased
        if (importRadioButton.isSelected()) {
            imageTableSelect();
        }
    }//GEN-LAST:event_imageTableKeyReleased

    private void imageTableMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_imageTableMouseReleased
        if (importRadioButton.isSelected()) {
            imageTableSelect();
        }
    }//GEN-LAST:event_imageTableMouseReleased

    private void driveListMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_driveListMouseReleased
        if (importRadioButton.isSelected()) {
            driveListSelect();
            firePropertyChange(DataSourceProcessor.DSP_PANEL_EVENT.UPDATE_UI.toString(), true, false);
        }
    }//GEN-LAST:event_driveListMouseReleased


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton browseButton;
    private javax.swing.ButtonGroup buttonGroup1;
    private javax.swing.JList<String> driveList;
    private javax.swing.JScrollPane driveListScrollPane;
    private javax.swing.JScrollPane imageScrollPane;
    private javax.swing.JTable imageTable;
    private javax.swing.JRadioButton importRadioButton;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JRadioButton manualRadioButton;
    private javax.swing.JTextArea messageTextArea;
    private javax.swing.JTextField pathTextField;
    private javax.swing.JButton refreshButton;
    private javax.swing.JLabel selectDriveLabel;
    private javax.swing.JLabel selectFolderLabel;
    private javax.swing.JLabel selectFromDriveLabel;
    // End of variables declaration//GEN-END:variables

    void reset() {
        //reset the UI elements to default
        manualImageDirPath = null;
        setNormalMessage("");
        driveList.setListData(EMPTY_LIST_DATA);
        clearImageTable();
        if (importRadioButton.isSelected()) {
            refreshButton.doClick();
        }
    }

    /**
     * Should we enable the next button of the wizard?
     *
     * @return true if a proper image has been selected, false otherwise
     */
    boolean validatePanel() {
        if (manualRadioButton.isSelected()) {
            return manualImageDirPath != null && manualImageDirPath.toFile().exists();
        } else if (imageTable.getSelectedRow() != -1) {
            Path path = Paths.get((String) imageTableModel.getValueAt(imageTable.convertRowIndexToModel(imageTable.getSelectedRow()), 2));
            return path != null && path.toFile().exists();
        } else {
            return false;
        }
    }

    Path getImageDirPath() {
        if (manualRadioButton.isSelected()) {
            return manualImageDirPath;
        } else if (imageTable.getSelectedRow() != -1) {
            return Paths.get((String) imageTableModel.getValueAt(imageTable.convertRowIndexToModel(imageTable.getSelectedRow()), 2));
        } else {
            return null;
        }
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
    }

    void storeSettings() {
    }

    /**
     * Image Table Model
     */
    private class ImageTableModel extends DefaultTableModel {

        private static final long serialVersionUID = 1L;

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Messages({
            "LogicalImagerPanel.imageTable.columnModel.title0=Hostname",
            "LogicalImagerPanel.imageTable.columnModel.title1=Extracted Date (GMT)",
            "LogicalImagerPanel.imageTable.columnModel.title2=Path"
        })
        @Override
        public String getColumnName(int column) {
            String colName = null;
            switch (column) {
                case 0:
                    colName = Bundle.LogicalImagerPanel_imageTable_columnModel_title0();
                    break;
                case 1:
                    colName = Bundle.LogicalImagerPanel_imageTable_columnModel_title1();
                    break;
                case 2:
                    colName = Bundle.LogicalImagerPanel_imageTable_columnModel_title2();
                    break;
                default:
                    break;
            }
            return colName;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }
}
