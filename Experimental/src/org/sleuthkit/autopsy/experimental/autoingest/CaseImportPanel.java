/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.experimental.autoingest;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.Font;
import java.io.File;
import java.nio.file.Paths;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JTextField;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.core.RuntimeProperties;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.core.UserPreferencesException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.CaseDbConnectionInfo;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import java.util.logging.Level;
import org.sleuthkit.autopsy.experimental.configuration.AutoIngestUserPreferences;

/**
 * This panel shows up in a tab pane next to the copy files panel for the
 * automated ingest copy node.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public class CaseImportPanel extends javax.swing.JPanel implements ImportDoneCallback {

    private final CaseImportPanelController controller;
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(CaseImportPanel.class.getName());
    private Thread ongoingImport; // used to interrupt thread if we need to
    private final JFileChooser caseSourceFolderChooser = new JFileChooser();
    private final JFileChooser imageSourceFolderChooser = new JFileChooser();
    private CaseDbConnectionInfo db;
    private final ImageIcon goodDatabaseCredentials;
    private final ImageIcon badDatabaseCredentials;
    private boolean canTalkToDb = false;
    private boolean copyImagesState = true;
    private boolean deleteImagesState = false;
    private static final String MULTI_USER_SETTINGS_MUST_BE_ENABLED = NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.validationErrMsg.MUdisabled");
    private static final String AIM_MUST_BE_ENABLED = NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.validationErrMsg.AIMdisabled");

    // Used to specify which notification area should be upated
    private enum NotificationLabel {

        INPUT,
        OUTPUT,
        BOTTOM,
        PROGRESS
    }

    /**
     * Creates new panel CaseImportPanel
     */
    public CaseImportPanel(CaseImportPanelController theController) {
        controller = theController;
        initComponents();
        badDatabaseCredentials = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/warning16.png", false)); //NON-NLS
        goodDatabaseCredentials = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/experimental/images/tick.png", false)); //NON-NLS
    }
    
    /**
     * Validate current panel settings.
     */
    boolean valid() {
        // Nothing to validate for case import panel as far as Netbeans Tools/Options controller is concerned
        return true;
    }
    
    /**
     * Store current panel settings.
     */
    void store() {
        // Nothing to store for case import panel as far as Netbeans Tools/Options controller is concerned
    }    

    /**
     * Load data.
     */    
    final void load() {
        
        // Multi user mode must be enabled. This is required to make sure database credentials are set.
        // Also, "join auto ingest cluster" must be selected and we need to be in automated ingest mode. 
        // This is required to make sure "shared images" and "shared results" folders are set
        if (!UserPreferences.getIsMultiUserModeEnabled()) {
            tbOops.setText(MULTI_USER_SETTINGS_MUST_BE_ENABLED);
            return;
        } else if (RuntimeProperties.runningWithGUI()) {
            tbOops.setText(AIM_MUST_BE_ENABLED);
            return;
        } else {
            tbOops.setText("");
        }
        
        // Note: we used to store input folders in persistent storage but it is not done any more for some reason...
        caseSourceFolderChooser.setCurrentDirectory(caseSourceFolderChooser.getFileSystemView().getParentDirectory(new File("C:\\"))); //NON-NLS
        caseSourceFolderChooser.setAcceptAllFileFilterUsed(false);
        caseSourceFolderChooser.setDialogTitle(NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.ChooseCase"));
        caseSourceFolderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        imageSourceFolderChooser.setCurrentDirectory(imageSourceFolderChooser.getFileSystemView().getParentDirectory(new File("C:\\"))); //NON-NLS
        imageSourceFolderChooser.setAcceptAllFileFilterUsed(false);
        imageSourceFolderChooser.setDialogTitle(NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.ChooseSource"));
        imageSourceFolderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        cbCopyImages.setSelected(true);
        cbDeleteCase.setSelected(false);
        picDbStatus.setText(""); //NON-NLS
        tbDeleteWarning.setText(""); //NON-NLS
        tbInputNotification.setText(""); //NON-NLS
        tbOutputNotification.setText(""); //NON-NLS
        tbBottomNotification.setText(""); //NON-NLS
        pbShowProgress.setStringPainted(true);
        pbShowProgress.setForeground(new Color(51, 153, 255));
        pbShowProgress.setString(""); //NON-NLS
        showDbStatus();
        handleAutoModeInputs();
    }
    
    void handleAutoModeInputs() {
        String caseDestinationResult = "";
        String output = AutoIngestUserPreferences.getAutoModeResultsFolder();
        if (output.isEmpty() || !(new File(output).exists())) {
            setNotificationText(NotificationLabel.OUTPUT, NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.BadCaseDestinationFolder"), false);
        } else {
            tbCaseDestination.setText(output);
            caseDestinationResult = "";
        }

        String imageDestinationResult = "";
        String imageFolder = AutoIngestUserPreferences.getAutoModeImageFolder();
        if (imageFolder.isEmpty() || !(new File(imageFolder).exists())) {
            setNotificationText(NotificationLabel.OUTPUT, NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.BadImageDestinationFolder"), false);
        } else {
            tbImageDestination.setText(imageFolder);
            imageDestinationResult = "";
        }

        String result = caseDestinationResult;
        if (result.isEmpty()) {
            result = imageDestinationResult;
        }
        setNotificationText(NotificationLabel.OUTPUT, result, false);      
    }

    /**
     * Set status pictures to show if the database credentials are good or bad
     */
    private void showDbStatus() {
        try {
            db = UserPreferences.getDatabaseConnectionInfo();
        } catch (UserPreferencesException ex) {
            logger.log(Level.SEVERE, "Error accessing case database connection info", ex); //NON-NLS
            setDbConnectionStatus(false, badDatabaseCredentials, NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.DatabaseNotConnected"));
            return;
        }
        try {
            SleuthkitCase.tryConnect(db);
            setDbConnectionStatus(true, goodDatabaseCredentials, NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.DatabaseConnected"));
        } catch (TskCoreException ex) {
            setDbConnectionStatus(false, badDatabaseCredentials, NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.DatabaseNotConnected"));
            logger.log(Level.SEVERE, "Unable to communicate with PostgreSQL: {0}", ex.getMessage());
        }
    }

    private void setDbConnectionStatus(boolean canConnect, ImageIcon credentials, String text) {
        canTalkToDb = canConnect;
        picDbStatus.setIcon(credentials);
        picDbStatus.setText(text);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        lbDbConnection = new javax.swing.JLabel();
        picDbStatus = new javax.swing.JLabel();
        lbCaseDestination = new javax.swing.JLabel();
        lbCaseSource = new javax.swing.JLabel();
        lbCaption = new javax.swing.JLabel();
        lbImageDestination = new javax.swing.JLabel();
        lbImageSource = new javax.swing.JLabel();
        bnStart = new javax.swing.JButton();
        bnCancel = new javax.swing.JButton();
        bnShowLog = new javax.swing.JButton();
        bnBrowseCaseSource = new javax.swing.JButton();
        bnBrowseImageSource = new javax.swing.JButton();
        pbShowProgress = new javax.swing.JProgressBar();
        tbCaseSource = new javax.swing.JTextField();
        tbCaseDestination = new javax.swing.JTextField();
        tbImageDestination = new javax.swing.JTextField();
        tbBottomNotification = new javax.swing.JTextField();
        tbImageSource = new javax.swing.JTextField();
        cbCopyImages = new javax.swing.JCheckBox();
        cbDeleteCase = new javax.swing.JCheckBox();
        lbProgressBar = new javax.swing.JLabel();
        tbInputNotification = new javax.swing.JTextField();
        tbOutputNotification = new javax.swing.JTextField();
        tbDeleteWarning = new javax.swing.JTextField();
        tbOops = new javax.swing.JTextField();

        setMinimumSize(new java.awt.Dimension(830, 240));

        lbDbConnection.setFont(lbDbConnection.getFont().deriveFont(lbDbConnection.getFont().getSize()+1f));
        lbDbConnection.setText("Database");
        lbDbConnection.setToolTipText("Set database credentials via 'Options'");
        lbDbConnection.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);
        lbDbConnection.setFocusable(false);

        picDbStatus.setFont(picDbStatus.getFont().deriveFont(picDbStatus.getFont().getSize()+1f));
        picDbStatus.setLabelFor(lbDbConnection);
        picDbStatus.setText("Database Status");
        picDbStatus.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);
        picDbStatus.setFocusable(false);

        lbCaseDestination.setFont(lbCaseDestination.getFont().deriveFont(lbCaseDestination.getFont().getSize()+1f));
        lbCaseDestination.setText("Case Destination");
        lbCaseDestination.setFocusable(false);

        lbCaseSource.setFont(lbCaseSource.getFont().deriveFont(lbCaseSource.getFont().getSize()+1f));
        lbCaseSource.setLabelFor(lbCaseSource);
        lbCaseSource.setText("Case Source");
        lbCaseSource.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);
        lbCaseSource.setFocusable(false);

        lbCaption.setFont(lbCaption.getFont().deriveFont(lbCaption.getFont().getStyle() | java.awt.Font.BOLD, lbCaption.getFont().getSize()+1));
        lbCaption.setText("Import single-user cases to multi-user cases");

        lbImageDestination.setFont(lbImageDestination.getFont().deriveFont(lbImageDestination.getFont().getSize()+1f));
        lbImageDestination.setText("Image Destination");
        lbImageDestination.setFocusable(false);

        lbImageSource.setFont(lbImageSource.getFont().deriveFont(lbImageSource.getFont().getSize()+1f));
        lbImageSource.setText("Image Source");
        lbImageSource.setFocusable(false);

        bnStart.setText("Start");
        bnStart.setEnabled(false);
        bnStart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnStartActionPerformed(evt);
            }
        });

        bnCancel.setText("Cancel");
        bnCancel.setEnabled(false);
        bnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnCancelActionPerformed(evt);
            }
        });

        bnShowLog.setText("Show Log");
        bnShowLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnShowLogActionPerformed(evt);
            }
        });

        bnBrowseCaseSource.setText("Browse");
        bnBrowseCaseSource.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnBrowseCaseSourceActionPerformed(evt);
            }
        });

        bnBrowseImageSource.setText("Browse");
        bnBrowseImageSource.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnBrowseImageSourceActionPerformed(evt);
            }
        });

        pbShowProgress.setFont(pbShowProgress.getFont().deriveFont(pbShowProgress.getFont().getStyle() | java.awt.Font.BOLD, pbShowProgress.getFont().getSize()+1));
        pbShowProgress.setToolTipText("");
        pbShowProgress.setFocusable(false);
        pbShowProgress.setMaximumSize(new java.awt.Dimension(32767, 16));
        pbShowProgress.setPreferredSize(new java.awt.Dimension(146, 16));

        tbCaseSource.setEditable(false);
        tbCaseSource.setFont(tbCaseSource.getFont().deriveFont(tbCaseSource.getFont().getSize()+1f));
        tbCaseSource.setToolTipText("Press \"Browse\" to select the case source folder.");
        tbCaseSource.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(153, 153, 153), 1, true));
        tbCaseSource.setFocusable(false);

        tbCaseDestination.setEditable(false);
        tbCaseDestination.setFont(tbCaseDestination.getFont().deriveFont(tbCaseDestination.getFont().getSize()+1f));
        tbCaseDestination.setToolTipText("The case destination folder. Press \"Options\" and edit \"Shared Results Folder\" to change this.  Any imported cases will be stored in this folder.");
        tbCaseDestination.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(153, 153, 153), 1, true));
        tbCaseDestination.setFocusable(false);

        tbImageDestination.setEditable(false);
        tbImageDestination.setFont(tbImageDestination.getFont().deriveFont(tbImageDestination.getFont().getSize()+1f));
        tbImageDestination.setToolTipText("This is the Image folder. Press \"Options\" and edit \"Shared Images Folder\" to change this. Any input images will be copied to this folder during import.");
        tbImageDestination.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(153, 153, 153), 1, true));
        tbImageDestination.setFocusable(false);

        tbBottomNotification.setEditable(false);
        tbBottomNotification.setFont(tbBottomNotification.getFont().deriveFont(tbBottomNotification.getFont().getSize()+1f));
        tbBottomNotification.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        tbBottomNotification.setText("tbNotification");
        tbBottomNotification.setToolTipText("Shows notifications");
        tbBottomNotification.setBorder(null);
        tbBottomNotification.setFocusable(false);

        tbImageSource.setEditable(false);
        tbImageSource.setFont(tbImageSource.getFont().deriveFont(tbImageSource.getFont().getSize()+1f));
        tbImageSource.setToolTipText("Press \"Browse\" to select the image source folder.");
        tbImageSource.setBorder(new javax.swing.border.LineBorder(new java.awt.Color(153, 153, 153), 1, true));
        tbImageSource.setFocusable(false);

        cbCopyImages.setFont(cbCopyImages.getFont().deriveFont(cbCopyImages.getFont().getSize()+1f));
        cbCopyImages.setText("Copy images");
        cbCopyImages.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                cbCopyImagesStateChanged(evt);
            }
        });
        cbCopyImages.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                cbCopyImagesPropertyChange(evt);
            }
        });

        cbDeleteCase.setFont(cbDeleteCase.getFont().deriveFont(cbDeleteCase.getFont().getSize()+1f));
        cbDeleteCase.setText("Delete original case");
        cbDeleteCase.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                cbDeleteCasePropertyChange(evt);
            }
        });

        lbProgressBar.setFont(lbProgressBar.getFont().deriveFont(lbProgressBar.getFont().getSize()+1f));
        lbProgressBar.setText("Progress");

        tbInputNotification.setEditable(false);
        tbInputNotification.setFont(tbInputNotification.getFont().deriveFont(tbInputNotification.getFont().getSize()+1f));
        tbInputNotification.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        tbInputNotification.setText("Input box");
        tbInputNotification.setBorder(null);

        tbOutputNotification.setEditable(false);
        tbOutputNotification.setFont(tbOutputNotification.getFont().deriveFont(tbOutputNotification.getFont().getSize()+1f));
        tbOutputNotification.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        tbOutputNotification.setText("Output box");
        tbOutputNotification.setBorder(null);

        tbDeleteWarning.setEditable(false);
        tbDeleteWarning.setText("delete warning");
        tbDeleteWarning.setBorder(null);

        tbOops.setEditable(false);
        tbOops.setFont(tbOops.getFont().deriveFont(tbOops.getFont().getStyle() | java.awt.Font.BOLD, tbOops.getFont().getSize()+1));
        tbOops.setForeground(new java.awt.Color(255, 0, 0));
        tbOops.setBorder(null);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(layout.createSequentialGroup()
                        .addGap(41, 41, 41)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lbImageSource, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lbCaseSource, javax.swing.GroupLayout.Alignment.TRAILING))
                        .addGap(18, 18, 18)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addComponent(tbCaseSource)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(bnBrowseCaseSource, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(tbImageSource)
                                    .addGroup(layout.createSequentialGroup()
                                        .addGap(0, 0, Short.MAX_VALUE)
                                        .addComponent(tbInputNotification, javax.swing.GroupLayout.PREFERRED_SIZE, 527, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(bnBrowseImageSource, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(layout.createSequentialGroup()
                        .addGap(37, 37, 37)
                        .addComponent(lbCaption)
                        .addGap(35, 35, 35)
                        .addComponent(tbOops, javax.swing.GroupLayout.PREFERRED_SIZE, 465, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(layout.createSequentialGroup()
                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(tbOutputNotification, javax.swing.GroupLayout.PREFERRED_SIZE, 495, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(tbBottomNotification, javax.swing.GroupLayout.PREFERRED_SIZE, 391, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                                .addGap(18, 18, 18)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(lbCaseDestination, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(lbImageDestination, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(lbDbConnection, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(lbProgressBar, javax.swing.GroupLayout.Alignment.TRAILING))
                                .addGap(18, 18, 18)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(tbCaseDestination)
                                    .addComponent(tbImageDestination)
                                    .addComponent(pbShowProgress, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addGroup(layout.createSequentialGroup()
                                        .addComponent(bnStart, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(bnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(bnShowLog, javax.swing.GroupLayout.PREFERRED_SIZE, 99, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addGroup(layout.createSequentialGroup()
                                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                            .addComponent(cbCopyImages)
                                            .addComponent(picDbStatus)
                                            .addGroup(layout.createSequentialGroup()
                                                .addComponent(cbDeleteCase)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(tbDeleteWarning, javax.swing.GroupLayout.PREFERRED_SIZE, 478, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                        .addGap(0, 11, Short.MAX_VALUE)))))
                        .addGap(105, 105, 105)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbCaption)
                    .addComponent(tbOops, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(bnBrowseCaseSource)
                    .addComponent(tbCaseSource, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbCaseSource))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(bnBrowseImageSource)
                    .addComponent(tbImageSource, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbImageSource))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cbCopyImages)
                    .addComponent(tbInputNotification, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cbDeleteCase)
                    .addComponent(tbDeleteWarning, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(35, 35, 35)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(tbCaseDestination, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbCaseDestination))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(tbImageDestination, javax.swing.GroupLayout.PREFERRED_SIZE, 30, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbImageDestination))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbOutputNotification, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(9, 9, 9)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lbDbConnection)
                    .addComponent(picDbStatus))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(pbShowProgress, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lbProgressBar))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(bnShowLog)
                    .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(bnStart)
                        .addComponent(bnCancel)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tbBottomNotification, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(172, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Handles pressing the "Start" button
     *
     * @param evt
     */
    private void bnStartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnStartActionPerformed
        showDbStatus();
        if (canTalkToDb) {
            setNotificationText(NotificationLabel.PROGRESS, NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.ImportingCases"), true);
            SingleUserCaseImporter caseImporter = new SingleUserCaseImporter(
                    tbImageSource.getText(),
                    tbCaseSource.getText(),
                    tbImageDestination.getText(),
                    tbCaseDestination.getText(),
                    cbCopyImages.isSelected(),
                    cbDeleteCase.isSelected(),
                    this);
            pbShowProgress.setIndeterminate(true);
            ongoingImport = new Thread(caseImporter);
            setButtonsForJobRunning(true);
            ongoingImport.start();
        } else {
            bnStart.setEnabled(false);
        }
    }//GEN-LAST:event_bnStartActionPerformed

    /**
     * Allows bulk-setting the button enabled states.
     *
     * @param setting true if we are currently processing an import job, false
     *                otherwise
     */
    void setButtonsForJobRunning(boolean setting) {
        bnBrowseCaseSource.setEnabled(!setting);
        bnBrowseImageSource.setEnabled(!setting);
        cbCopyImages.setEnabled(!setting);
        cbDeleteCase.setEnabled(!setting);
        bnStart.setEnabled(!setting);
        bnCancel.setEnabled(setting);
    }

    /**
     * Handles pressing the Cancel button
     *
     * @param evt
     */
    private void bnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnCancelActionPerformed
        if (ongoingImport != null) {
            setNotificationText(NotificationLabel.PROGRESS, NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.Cancelling"), false);
            ongoingImport.interrupt();
        }
    }//GEN-LAST:event_bnCancelActionPerformed

    /**
     * Handles pressing the Browse for case source folder button
     *
     * @param evt
     */
    private void bnBrowseCaseSourceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnBrowseCaseSourceActionPerformed
        int returnVal = caseSourceFolderChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            validateSourceFields();
        }
    }//GEN-LAST:event_bnBrowseCaseSourceActionPerformed

    /**
     * Show user information about status of source fields, hierarchically.
     */
    private void validateSourceFields() {
        String caseSourceResult = "";
        File selectedFolder = caseSourceFolderChooser.getSelectedFile();
        if (selectedFolder == null || !selectedFolder.exists()) {
            caseSourceResult = NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.BadCaseSourceFolder");
            tbCaseSource.setText("");
        } else {
            caseSourceResult = NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.Blank");
            caseSourceFolderChooser.setCurrentDirectory(selectedFolder);
            tbCaseSource.setText(selectedFolder.toString());
        }

        String caseImagesResult = "";
        if (cbCopyImages.isSelected()) {
            selectedFolder = imageSourceFolderChooser.getSelectedFile();
            if (selectedFolder == null || !selectedFolder.exists()) {
                caseImagesResult = NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.BadImageSourceFolder");
                tbImageSource.setText("");  //NON-NLS
            } else {
                if (tbInputNotification.getText().isEmpty()) {
                    caseImagesResult = NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.Blank");
                }
                imageSourceFolderChooser.setCurrentDirectory(selectedFolder);
                tbImageSource.setText(selectedFolder.toString());
            }
        }
        String result = caseSourceResult;
        if (result.isEmpty()) {
            result = caseImagesResult;
        }
        setNotificationText(NotificationLabel.INPUT, result, false);
        enableStartButton();
    }

    /**
     * Handles pressing the Show Log button
     *
     * @param evt
     */
    private void bnShowLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnShowLogActionPerformed
        try {
            File logFile = Paths.get(tbCaseDestination.getText(), SingleUserCaseImporter.CASE_IMPORT_LOG_FILE).toFile();
            setNotificationText(NotificationLabel.BOTTOM, "", false); //NON-NLS
            Desktop.getDesktop().edit(logFile);
        } catch (Exception ex) {
            setNotificationText(NotificationLabel.BOTTOM, NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.CannotOpenLog"), false);
        }
    }//GEN-LAST:event_bnShowLogActionPerformed

    /**
     * Handles pressing the Browse for image source folder button
     *
     * @param evt
     */
    private void bnBrowseImageSourceActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnBrowseImageSourceActionPerformed
        int returnVal = imageSourceFolderChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            validateSourceFields();
        }
    }//GEN-LAST:event_bnBrowseImageSourceActionPerformed

    private void cbCopyImagesPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_cbCopyImagesPropertyChange

    }//GEN-LAST:event_cbCopyImagesPropertyChange

    private void cbDeleteCasePropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_cbDeleteCasePropertyChange
        if (deleteImagesState != cbDeleteCase.isSelected()) {
            deleteImagesState = cbDeleteCase.isSelected();
            if (cbDeleteCase.isSelected()) {
                tbDeleteWarning.setForeground(Color.RED);
                tbDeleteWarning.setText(NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.DeleteWarning"));
            } else {
                // USE BUNDLE
                tbDeleteWarning.setText("");
            }
        }
    }//GEN-LAST:event_cbDeleteCasePropertyChange

    private void cbCopyImagesStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_cbCopyImagesStateChanged
        // Enable or disable the image folder entries
        // this gets notified of mouseovers and such, so check that it actually 
        // changed before changing the state of UI components
        if (copyImagesState != cbCopyImages.isSelected()) {
            copyImagesState = cbCopyImages.isSelected();
            if (copyImagesState) {
                tbImageSource.setEnabled(true);
                tbImageDestination.setEnabled(true);
                bnBrowseImageSource.setEnabled(true);
            } else {
                tbImageSource.setEnabled(false);
                tbImageDestination.setEnabled(false);
                bnBrowseImageSource.setEnabled(false);
            }
            validateSourceFields();
        }
    }//GEN-LAST:event_cbCopyImagesStateChanged

    /**
     * Enables the start button if all input is in order, disables it otherwise
     */
    private void enableStartButton() {
        if (UserPreferences.getIsMultiUserModeEnabled()
                && (! RuntimeProperties.runningWithGUI())
                && !tbCaseSource.getText().isEmpty()
                && !tbCaseDestination.getText().isEmpty()
                && canTalkToDb == true
                && (!cbCopyImages.isSelected() || (!tbImageSource.getText().isEmpty() && !tbImageDestination.getText().isEmpty()))) {
            bnStart.setEnabled(true);
        } else {
            bnStart.setEnabled(false);
        }
    }

    /**
     * Allows setting the notification text outside the EDT.
     *
     * @param position the label we intend to set
     * @param text     The text to set
     * @param okay     True if there was no issue, false otherwise. Sets text
     *                 color.
     */
    private void setNotificationText(final NotificationLabel position, final String text, final boolean okay) {
        EventQueue.invokeLater(() -> {
            if (position != NotificationLabel.PROGRESS) {
                JTextField textField;
                if (position == NotificationLabel.INPUT) {
                    textField = tbInputNotification;
                } else if (position == NotificationLabel.OUTPUT) {
                    textField = tbOutputNotification;
                } else {
                    textField = tbBottomNotification;
                }

                textField.setText(text);
                if (okay) {
                    Font font = textField.getFont();
                    textField.setFont(font.deriveFont(Font.BOLD));
                    textField.setForeground(Color.BLACK);
                } else {
                    Font font = textField.getFont();
                    textField.setFont(font.deriveFont(Font.PLAIN));
                    textField.setForeground(Color.RED);
                }
            } else {
                pbShowProgress.setString(text);
                if (okay) {
                    pbShowProgress.setForeground(new Color(51, 153, 255));
                } else {
                    pbShowProgress.setForeground(Color.RED);
                }
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnBrowseCaseSource;
    private javax.swing.JButton bnBrowseImageSource;
    private javax.swing.JButton bnCancel;
    private javax.swing.JButton bnShowLog;
    private javax.swing.JButton bnStart;
    private javax.swing.JCheckBox cbCopyImages;
    private javax.swing.JCheckBox cbDeleteCase;
    private javax.swing.JLabel lbCaption;
    private javax.swing.JLabel lbCaseDestination;
    private javax.swing.JLabel lbCaseSource;
    private javax.swing.JLabel lbDbConnection;
    private javax.swing.JLabel lbImageDestination;
    private javax.swing.JLabel lbImageSource;
    private javax.swing.JLabel lbProgressBar;
    private javax.swing.JProgressBar pbShowProgress;
    private javax.swing.JLabel picDbStatus;
    private javax.swing.JTextField tbBottomNotification;
    private javax.swing.JTextField tbCaseDestination;
    private javax.swing.JTextField tbCaseSource;
    private javax.swing.JTextField tbDeleteWarning;
    private javax.swing.JTextField tbImageDestination;
    private javax.swing.JTextField tbImageSource;
    private javax.swing.JTextField tbInputNotification;
    private javax.swing.JTextField tbOops;
    private javax.swing.JTextField tbOutputNotification;
    // End of variables declaration//GEN-END:variables

    /**
     * This method is called by the import thread as it is finishing.
     *
     * @param result       true if the entire import was successful, false
     *                     otherwise
     * @param resultString the text string to show the user
     */
    @Override
    public void importDoneCallback(boolean result, String resultString) {
        if (resultString == null || resultString.isEmpty()) {
            pbShowProgress.setIndeterminate(false);
            pbShowProgress.setValue(100);
            if (result) {
                setNotificationText(NotificationLabel.PROGRESS, NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.Complete"), true);
            } else {
                setNotificationText(NotificationLabel.PROGRESS, NbBundle.getMessage(CaseImportPanel.class, "CaseImportPanel.Error"), result);
            }
        } else {
            pbShowProgress.setIndeterminate(false);
            if (result == true) {
                pbShowProgress.setValue(0);
            } else {
                pbShowProgress.setValue(100);
            }
            setNotificationText(NotificationLabel.PROGRESS, resultString, result);
        }
        setButtonsForJobRunning(false);
        ongoingImport = null;
        showDbStatus();
    }
}
