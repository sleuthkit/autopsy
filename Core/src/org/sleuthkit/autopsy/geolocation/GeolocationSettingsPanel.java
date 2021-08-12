/*
 * Autopsy Forensic Browser
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
package org.sleuthkit.autopsy.geolocation;

import java.awt.Color;
import java.io.File;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.Level;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.apache.commons.validator.routines.UrlValidator;
import org.jxmapviewer.OSMTileFactoryInfo;
import org.jxmapviewer.viewer.TileFactoryInfo;
import org.jxmapviewer.viewer.util.GeoUtil;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.GeneralFilter;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.guiutils.JFileChooserFactory;

/**
 * A panel to allow the user to set the custom properties of the geolocation
 * window.
 *
 */
final class GeolocationSettingsPanel extends javax.swing.JPanel implements OptionsPanel {

    private static final long serialVersionUID = 1L;
    
    private static final Logger logger = Logger.getLogger(GeolocationSettingsPanel.class.getName());
    
    private static final JFileChooserFactory chooserHelper = new JFileChooserFactory();

    /**
     * Creates new GeolocationSettingsPanel
     */
    GeolocationSettingsPanel() {
        initComponents();
        updateControlState();
    }

    @Override
    public void store() {
        UserPreferences.setGeolocationTileOption(getDataSourceOption().getValue());
        UserPreferences.setGeolocationOsmZipPath(zipFilePathField.getText());
        UserPreferences.setGeolocationOsmServerAddress(osmServerAddressField.getText());
        UserPreferences.setGeolocationMBTilesFilePath(mbtileFileField.getText());
    }

    @Override
    public void load() {
        osmServerAddressField.setText(UserPreferences.getGeolocationOsmServerAddress());
        zipFilePathField.setText(UserPreferences.getGeolocationOsmZipPath());
        mbtileFileField.setText(UserPreferences.getGeolocationMBTilesFilePath());
        switch (GeolocationDataSourceType.getOptionForValue(UserPreferences.getGeolocationtTileOption())) {
            case ONLINE_USER_DEFINED_OSM_SERVER:
                osmServerRBnt.setSelected(true);
                break;
            case OFFLINE_OSM_ZIP:
                zipFileRBnt.setSelected(true);
                break;
            case OFFILE_MBTILES_FILE:
                mbtilesRBtn.setSelected(true);
                break;
            default:
                defaultDataSource.setSelected(true);
                break;
        }

        updateControlState();
    }

    /**
     * Update the state of the tile server options based on the radio button
     * selection state.
     */
    private void updateControlState() {
        osmServerAddressField.setEnabled(osmServerRBnt.isSelected());
        serverTestBtn.setEnabled(osmServerRBnt.isSelected());
        zipFilePathField.setEnabled(zipFileRBnt.isSelected());
        zipFileBrowseBnt.setEnabled(zipFileRBnt.isSelected());
        mbtileFileField.setEnabled(mbtilesRBtn.isSelected());
        mbtilesBrowseBtn.setEnabled(mbtilesRBtn.isSelected());
        mbtileTestBtn.setEnabled(mbtilesRBtn.isSelected());
    }

    /**
     * Returns the GEOLOCATION_TILE_OPTION based on the selection state of the
     * option radio buttons.
     *
     * @return Current GEOLOCATION_TILE_OPTION
     */
    private GeolocationDataSourceType getDataSourceOption() {
        if (osmServerRBnt.isSelected()) {
            return GeolocationDataSourceType.ONLINE_USER_DEFINED_OSM_SERVER;
        } else if (zipFileRBnt.isSelected()) {
            return GeolocationDataSourceType.OFFLINE_OSM_ZIP;
        } else if (mbtilesRBtn.isSelected()) {
            return GeolocationDataSourceType.OFFILE_MBTILES_FILE;
        }
        return GeolocationDataSourceType.ONLINE_DEFAULT_SERVER;
    }

    /**
     * Tests the validity of the given tile server address.
     *
     * This test assumes the server is able to serve up at least one tile, the
     * tile at x=1, y=1 with a zoom level of 1. This tile should be the whole
     * global.
     *
     * @param url String url to tile server. The string does not have to be
     *            prefixed with http://
     *
     * @return True if the server was successfully contacted.
     */
    private boolean testOSMServer(String url) {
        TileFactoryInfo info = new OSMTileFactoryInfo("User Defined Server", url); //NON-NLS
        return GeoUtil.isValidTile(1, 1, 1, info);
    }
    
    void cancelChanges() {
        load();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        javax.swing.ButtonGroup buttonGroup = new javax.swing.ButtonGroup();
        javax.swing.JPanel tilePane = new javax.swing.JPanel();
        defaultDataSource = new javax.swing.JRadioButton();
        osmServerRBnt = new javax.swing.JRadioButton();
        osmServerAddressField = new javax.swing.JTextField();
        zipFileRBnt = new javax.swing.JRadioButton();
        zipFilePathField = new javax.swing.JTextField();
        zipFileBrowseBnt = new javax.swing.JButton();
        serverTestBtn = new javax.swing.JButton();
        mbtilesRBtn = new javax.swing.JRadioButton();
        mbtileFileField = new javax.swing.JTextField();
        javax.swing.JPanel MBTilesBtnPanel = new javax.swing.JPanel();
        mbtilesBrowseBtn = new javax.swing.JButton();
        mbtileTestBtn = new javax.swing.JButton();

        setLayout(new java.awt.GridBagLayout());

        tilePane.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.tilePane.border.title"))); // NOI18N
        tilePane.setLayout(new java.awt.GridBagLayout());

        buttonGroup.add(defaultDataSource);
        defaultDataSource.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(defaultDataSource, org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.defaultDataSource.text")); // NOI18N
        defaultDataSource.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                defaultDataSourceActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 9, 0);
        tilePane.add(defaultDataSource, gridBagConstraints);

        buttonGroup.add(osmServerRBnt);
        org.openide.awt.Mnemonics.setLocalizedText(osmServerRBnt, org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.osmServerRBnt.text")); // NOI18N
        osmServerRBnt.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                osmServerRBntActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 9, 0);
        tilePane.add(osmServerRBnt, gridBagConstraints);

        osmServerAddressField.setText(org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.osmServerAddressField.text")); // NOI18N
        osmServerAddressField.setPreferredSize(new java.awt.Dimension(300, 26));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 9, 0);
        tilePane.add(osmServerAddressField, gridBagConstraints);

        buttonGroup.add(zipFileRBnt);
        org.openide.awt.Mnemonics.setLocalizedText(zipFileRBnt, org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.zipFileRBnt.text")); // NOI18N
        zipFileRBnt.setActionCommand(org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.zipFileRBnt.actionCommand")); // NOI18N
        zipFileRBnt.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zipFileRBntActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 9, 0);
        tilePane.add(zipFileRBnt, gridBagConstraints);

        zipFilePathField.setText(org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.zipFilePathField.text")); // NOI18N
        zipFilePathField.setPreferredSize(new java.awt.Dimension(300, 26));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 9, 0);
        tilePane.add(zipFilePathField, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(zipFileBrowseBnt, org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.zipFileBrowseBnt.text")); // NOI18N
        zipFileBrowseBnt.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zipFileBrowseBntActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 9, 9, 9);
        tilePane.add(zipFileBrowseBnt, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(serverTestBtn, org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.serverTestBtn.text")); // NOI18N
        serverTestBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                serverTestBtnActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 9, 9, 9);
        tilePane.add(serverTestBtn, gridBagConstraints);

        buttonGroup.add(mbtilesRBtn);
        org.openide.awt.Mnemonics.setLocalizedText(mbtilesRBtn, org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.mbtilesRBtn.text")); // NOI18N
        mbtilesRBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mbtilesRBtnActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 9, 0);
        tilePane.add(mbtilesRBtn, gridBagConstraints);

        mbtileFileField.setText(org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.mbtileFileField.text")); // NOI18N
        mbtileFileField.setToolTipText(org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.mbtileFileField.toolTipText")); // NOI18N
        mbtileFileField.setPreferredSize(new java.awt.Dimension(300, 26));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 9, 0);
        tilePane.add(mbtileFileField, gridBagConstraints);

        MBTilesBtnPanel.setLayout(new java.awt.GridLayout(1, 0, 5, 0));

        org.openide.awt.Mnemonics.setLocalizedText(mbtilesBrowseBtn, org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.mbtilesBrowseBtn.text")); // NOI18N
        mbtilesBrowseBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mbtilesBrowseBtnActionPerformed(evt);
            }
        });
        MBTilesBtnPanel.add(mbtilesBrowseBtn);

        org.openide.awt.Mnemonics.setLocalizedText(mbtileTestBtn, org.openide.util.NbBundle.getMessage(GeolocationSettingsPanel.class, "GeolocationSettingsPanel.mbtileTestBtn.text")); // NOI18N
        mbtileTestBtn.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mbtileTestBtnActionPerformed(evt);
            }
        });
        MBTilesBtnPanel.add(mbtileTestBtn);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 9, 9, 9);
        tilePane.add(MBTilesBtnPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        add(tilePane, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void zipFileBrowseBntActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zipFileBrowseBntActionPerformed
        JFileChooser fileWindow = chooserHelper.getChooser();
        fileWindow.setFileSelectionMode(JFileChooser.FILES_ONLY);
        GeneralFilter fileFilter = new GeneralFilter(Arrays.asList(".zip"), "Zips (*.zip)"); //NON-NLS
        fileWindow.setDragEnabled(false);
        fileWindow.setFileFilter(fileFilter);
        fileWindow.setMultiSelectionEnabled(false);
        int returnVal = fileWindow.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File zipFile = fileWindow.getSelectedFile();
            zipFilePathField.setForeground(Color.BLACK);
            zipFilePathField.setText(zipFile.getAbsolutePath());
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_zipFileBrowseBntActionPerformed

    private void defaultDataSourceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_defaultDataSourceActionPerformed
        updateControlState();
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_defaultDataSourceActionPerformed

    private void osmServerRBntActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_osmServerRBntActionPerformed
        updateControlState();
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_osmServerRBntActionPerformed

    private void zipFileRBntActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zipFileRBntActionPerformed
        updateControlState();
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_zipFileRBntActionPerformed

    @Messages({
        "GeolocationSettingsPanel_malformed_url_message=The supplied OSM tile server address is invalid.\nPlease supply a well formed url prefixed with http://",
        "GeolocationSettingsPanel_malformed_url_message_tile=Malformed URL",
        "GeolocationSettingsPanel_osm_server_test_fail_message=OSM tile server test failed.\nUnable to connect to server.",
        "GeolocationSettingsPanel_osm_server_test_fail_message_title=Error",
        "GeolocationSettingsPanel_osm_server_test_success_message=The provided OSM tile server address is valid.",
        "GeolocationSettingsPanel_osm_server_test_success_message_title=Success",})
    private void serverTestBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_serverTestBtnActionPerformed
        String address = osmServerAddressField.getText();
        String message = Bundle.GeolocationSettingsPanel_osm_server_test_fail_message();
        String title = Bundle.GeolocationSettingsPanel_osm_server_test_fail_message_title();

        String[] schemes = {"http", "https"}; //NON-NLS
        UrlValidator urlValidator = new UrlValidator(schemes);
        if (!urlValidator.isValid(address)) {
            message = Bundle.GeolocationSettingsPanel_malformed_url_message();
            title = Bundle.GeolocationSettingsPanel_malformed_url_message_tile();
        } else if (testOSMServer(address)) {
            message = Bundle.GeolocationSettingsPanel_osm_server_test_success_message();
            title = Bundle.GeolocationSettingsPanel_osm_server_test_success_message_title();
        }

        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }//GEN-LAST:event_serverTestBtnActionPerformed

    private void mbtilesRBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mbtilesRBtnActionPerformed
        updateControlState();
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_mbtilesRBtnActionPerformed

    private void mbtilesBrowseBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mbtilesBrowseBtnActionPerformed
        JFileChooser fileWindow = chooserHelper.getChooser();
        fileWindow.setFileSelectionMode(JFileChooser.FILES_ONLY);
        GeneralFilter fileFilter = new GeneralFilter(Arrays.asList(".mbtiles"), "MBTiles (*.mbtiles)"); //NON-NLS
        fileWindow.setDragEnabled(false);
        fileWindow.setFileFilter(fileFilter);
        fileWindow.setMultiSelectionEnabled(false);
        int returnVal = fileWindow.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File zipFile = fileWindow.getSelectedFile();
            mbtileFileField.setForeground(Color.BLACK);
            mbtileFileField.setText(zipFile.getAbsolutePath());
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_mbtilesBrowseBtnActionPerformed

    @Messages({
        "GeolocationSettings_mbtile_does_not_exist_message=The file supplied does not exist.\nPlease verify that the file exists and try again.",
        "GeolocationSettings_mbtile_does_not_exist_title=File Not Found",
        "GeolocationSettings_mbtile_not_valid_message=The supplied file is not a raster tile file.",
        "GeolocationSettings_mbtile_not_valid_title=File Not Valid",
        "GeolocationSettings_path_not_valid_message=The supplied file path is empty.\nPlease supply a valid file path.",
        "GeolocationSettings_path_not_valid_title=File Not Valid",
        "GeolocationSettings_mbtile_test_success_message=The supplied file is a valid mbtile raster file.",
        "GeolocationSettings_mbtile_test_success_title=Success",
    })
    private void mbtileTestBtnActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mbtileTestBtnActionPerformed
        String mbtilePath = mbtileFileField.getText();
        
        if(mbtilePath.isEmpty()) {
            JOptionPane.showMessageDialog(this, Bundle.GeolocationSettings_path_not_valid_message(), Bundle.GeolocationSettings_path_not_valid_title(), JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        File file = new File(mbtilePath);
        if(!file.exists()) {
            JOptionPane.showMessageDialog(this, Bundle.GeolocationSettings_mbtile_does_not_exist_message(), Bundle.GeolocationSettings_mbtile_does_not_exist_title(), JOptionPane.ERROR_MESSAGE);
            return;
        } 
        
        try {
            if(!MBTilesFileConnector.isValidMBTileRasterFile(mbtilePath)) {
                JOptionPane.showMessageDialog(this, Bundle.GeolocationSettings_mbtile_not_valid_message(), Bundle.GeolocationSettings_mbtile_not_valid_title(), JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, Bundle.GeolocationSettings_mbtile_not_valid_message(), Bundle.GeolocationSettings_mbtile_not_valid_title(), JOptionPane.ERROR_MESSAGE);
            logger.log(Level.WARNING, String.format("Exception thrown while testing mbtile file %s", mbtilePath), ex);
            return;
        }
        
        JOptionPane.showMessageDialog(this, Bundle.GeolocationSettings_mbtile_test_success_message(), Bundle.GeolocationSettings_mbtile_test_success_title(), JOptionPane.INFORMATION_MESSAGE);
    }//GEN-LAST:event_mbtileTestBtnActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton defaultDataSource;
    private javax.swing.JTextField mbtileFileField;
    private javax.swing.JButton mbtileTestBtn;
    private javax.swing.JButton mbtilesBrowseBtn;
    private javax.swing.JRadioButton mbtilesRBtn;
    private javax.swing.JTextField osmServerAddressField;
    private javax.swing.JRadioButton osmServerRBnt;
    private javax.swing.JButton serverTestBtn;
    private javax.swing.JButton zipFileBrowseBnt;
    private javax.swing.JTextField zipFilePathField;
    private javax.swing.JRadioButton zipFileRBnt;
    // End of variables declaration//GEN-END:variables

    /**
     * Tile server option enum. The enum was given values to simplify the
     * storing of the user preference for a particular option.
     */
    enum GeolocationDataSourceType{
        ONLINE_DEFAULT_SERVER(0),
        ONLINE_USER_DEFINED_OSM_SERVER(1),
        OFFLINE_OSM_ZIP(2),
        OFFILE_MBTILES_FILE(3);

        private final int value;

        GeolocationDataSourceType(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }

        static GeolocationDataSourceType getOptionForValue(int value) {
            for (GeolocationDataSourceType option : GeolocationDataSourceType.values()) {
                if (option.getValue() == value) {
                    return option;
                }
            }

            return ONLINE_DEFAULT_SERVER;
        }
    }

}
