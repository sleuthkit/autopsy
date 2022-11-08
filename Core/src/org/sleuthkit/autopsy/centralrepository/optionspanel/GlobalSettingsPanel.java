/*
 * Central Repository
 *
 * Copyright 2015-2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.centralrepository.optionspanel;

import java.awt.EventQueue;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbManager;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoException;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoPlatforms;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbChoice;
import org.sleuthkit.autopsy.centralrepository.datamodel.CentralRepoDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.PostgresCentralRepoSettings;
import org.sleuthkit.autopsy.centralrepository.datamodel.SqliteCentralRepoSettings;
import java.awt.Component;
import java.beans.PropertyChangeSupport;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import org.openide.util.ImageUtilities;
import org.sleuthkit.autopsy.centralrepository.datamodel.DatabaseTestResult;
import org.sleuthkit.autopsy.centralrepository.datamodel.PostgresSettingsLoader;



/**
 * Main settings panel for the Central Repository
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class GlobalSettingsPanel extends IngestModuleGlobalSettingsPanel implements OptionsPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(GlobalSettingsPanel.class.getName());
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.STARTED, IngestManager.IngestJobEvent.CANCELLED, IngestManager.IngestJobEvent.COMPLETED);

    // this allows property change events to be fired at a static level but listened to by instances
    private static final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(GlobalSettingsPanel.class);
    
    // tracks the last known instance property change listener so that only one GlobalSettingsPanel is listening for events
    private static PropertyChangeListener lastRegistered = null;
    
    private final IngestJobEventPropertyChangeListener ingestJobEventListener;
    
    private final ImageIcon goodIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/good.png", false));
    private final ImageIcon badIcon = new ImageIcon(ImageUtilities.loadImage("org/sleuthkit/autopsy/images/bad.png", false));
    
    
    /**
     * Creates new form EamOptionsPanel
     */
    public GlobalSettingsPanel() {
        ingestJobEventListener = new IngestJobEventPropertyChangeListener();
        initComponents();
        customizeComponents();
        setupSettingsChangeListeners();
        addIngestJobEventsListener();
        Case.addEventTypeSubscriber(EnumSet.of(Case.Events.CURRENT_CASE), (PropertyChangeEvent evt) -> {
            //disable when case is open, enable when case is closed
            ingestStateUpdated(evt.getNewValue() != null);
        });
    }
    
    
    /**
     * Sets up this instance's listener for the GlobalSettingsPanel's changes.
     */
    private void setupSettingsChangeListeners() {
        // listen for change events in currently saved choice
        if (lastRegistered != null) {
            CentralRepoDbManager.removePropertyChangeListener(lastRegistered);
            GlobalSettingsPanel.propertyChangeSupport.removePropertyChangeListener(lastRegistered);
        }
        
        lastRegistered = this::onSettingsChange;
        CentralRepoDbManager.addPropertyChangeListener(lastRegistered);
        GlobalSettingsPanel.propertyChangeSupport.addPropertyChangeListener(lastRegistered);
    }
    
    
    private void onSettingsChange(PropertyChangeEvent evt) {
        ingestStateUpdated(Case.isCaseOpen());
        clearStatus();
    }

    
    private void customizeComponents() {
        setName(NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.pnCorrelationProperties.border.title"));
    }

    private void addIngestJobEventsListener() {
        IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, ingestJobEventListener);
        ingestStateUpdated(Case.isCaseOpen());
    }

    /**
     * This method invokes central repository database choice selection as well
     * as input for necessary configuration.
     *
     * @param parent           The parent component for displaying dialogs.
     * @param initialSelection If non-null, the menu item will be set to this
     *                         choice; if null, the currently selected db choice
     *                         will be selected.
     *
     * @return True if there was a change.
     */
    private static boolean invokeCrChoice(Component parent, CentralRepoDbChoice initialSelection) {
        EamDbSettingsDialog dialog = (initialSelection != null)
                ? new EamDbSettingsDialog(initialSelection)
                : new EamDbSettingsDialog();
        return dialog.wasConfigurationChanged();
    }

    /**
     * When multi user settings are updated, this function triggers pertinent
     * updates for central repository. NOTE: If multi user settings were
     * previously enabled and multi user settings are currently selected, this
     * function assumes there is a change in the postgres connectivity.
     *
     * @param parent               The swing component that serves as a parent
     *                             for dialogs that may arise.
     * @param muPreviouslySelected If multi user settings were previously
     *                             enabled.
     * @param muCurrentlySelected  If multi user settings are currently enabled
     *                             as of most recent change.
     */
    @NbBundle.Messages({
        "GlobalSettingsPanel.onMultiUserChange.enable.title=Central Repository",
        "# {0} - server name",
        "GlobalSettingsPanel.onMultiUserChange.enable.description=Do you want to update the Central Repository to use the PostgreSQL server on {0}?",
        "GlobalSettingsPanel.onMultiUserChange.enable.description2=Any data in an existing SQLite Central Repository will not be transferred to the new database."
    })
    public static void onMultiUserChange(Component parent, boolean muPreviouslySelected, boolean muCurrentlySelected) {
        boolean crEnabled = CentralRepoDbUtil.allowUseOfCentralRepository();
        boolean crMultiUser = CentralRepoDbManager.getSavedDbChoice() == CentralRepoDbChoice.POSTGRESQL_MULTIUSER;

        if (!muPreviouslySelected && muCurrentlySelected) {
            SwingUtilities.invokeLater(() -> {
                PostgresCentralRepoSettings multiUserSettings
                = new PostgresCentralRepoSettings(PostgresSettingsLoader.MULTIUSER_SETTINGS_LOADER);
                if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(parent,
                        "<html><body>"
                        + "<div style='width: 400px;'>"
                        + "<p>" + Bundle.GlobalSettingsPanel_onMultiUserChange_enable_description(multiUserSettings.getHost()) + "</p>"
                        + "<p style='margin-top: 10px'>" + Bundle.GlobalSettingsPanel_onMultiUserChange_enable_description2() + "</p>"
                        + "</div>"
                        + "</body></html>",
                        Bundle.GlobalSettingsPanel_onMultiUserChange_enable_title(),
                        JOptionPane.YES_NO_OPTION)) {

                    // setup database for CR
                    CentralRepoDbUtil.setUseCentralRepo(true);
                    CentralRepoDbManager.saveDbChoice(CentralRepoDbChoice.POSTGRESQL_MULTIUSER);
                    checkStatusAndCreateDb(parent);
                }
            });
        } // moving from selected to not selected && 'PostgreSQL using multi-user settings' is selected
        else if (muPreviouslySelected && !muCurrentlySelected && crEnabled && crMultiUser) {
            SwingUtilities.invokeLater(() -> {
                askForCentralRepoDbChoice(parent);
            });
        } // changing multi-user settings connection && 'PostgreSQL using multi-user settings' is selected && 
        // central repo either enabled or was disabled due to error
        else if (muPreviouslySelected && muCurrentlySelected && crEnabled && crMultiUser) {
            GlobalSettingsPanel.propertyChangeSupport.firePropertyChange("multiuserSettingsChanged", null, null);
            checkStatusAndCreateDb(parent);
        }
    }
    
    
    /**
     * Checks the status of current connectivity for CR and reports any issues.  Will also prompt user to create
     * database if cr database is absent.
     * @param parent    the parent component to which the dialogs will be associated.
     */
    private static void checkStatusAndCreateDb(Component parent) {
        SwingUtilities.invokeLater(() -> {
            EamDbSettingsDialog.testStatusAndCreate(parent, new CentralRepoDbManager());
        });
    }

    /**
     * This method is called when a user must select a new database other than
     * using database from multi user settings.
     *
     * @param parent The parent component to use for displaying dialogs in
     *               reference.
     */
    @NbBundle.Messages({
        "GlobalSettingsPanel.onMultiUserChange.disabledMu.title=Central Repository Change Necessary",
        "GlobalSettingsPanel.onMultiUserChange.disabledMu.description=The Central Repository will be reconfigured to use a local SQLite database.",
        "GlobalSettingsPanel.onMultiUserChange.disabledMu.description2=Press Configure PostgreSQL to change to a PostgreSQL database.",
        "GlobalSettingsPanel.askForCentralRepoDbChoice.sqliteChoice.text=Use SQLite",
        "GlobalSettingsPanel.askForCentralRepoDbChoice.customPostgrestChoice.text=Configure PostgreSQL",
        "GlobalSettingsPanel.askForCentralRepoDbChoice.disableChoice.text=Disable Central Repository"
    })
    private static void askForCentralRepoDbChoice(Component parent) {
        Object[] options = {
            Bundle.GlobalSettingsPanel_askForCentralRepoDbChoice_sqliteChoice_text(),
            Bundle.GlobalSettingsPanel_askForCentralRepoDbChoice_customPostgrestChoice_text(),
            Bundle.GlobalSettingsPanel_askForCentralRepoDbChoice_disableChoice_text()
        };

        int result = JOptionPane.showOptionDialog(
                parent,
                "<html><body>"
                + "<div style='width: 400px;'>"
                + "<p>" + Bundle.GlobalSettingsPanel_onMultiUserChange_disabledMu_description() + "</p>"
                + "<p style='margin-top: 10px'>" + Bundle.GlobalSettingsPanel_onMultiUserChange_disabledMu_description2() + "</p>"
                + "</div>"
                + "</body></html>",
                Bundle.GlobalSettingsPanel_onMultiUserChange_disabledMu_title(),
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
        );

        if (JOptionPane.YES_OPTION == result) {
            invokeCrChoice(parent, CentralRepoDbChoice.SQLITE);
        } else if (JOptionPane.NO_OPTION == result) {
            invokeCrChoice(parent, CentralRepoDbChoice.POSTGRESQL_CUSTOM);
        }
    }
    
    @NbBundle.Messages({
        "GlobalSettingsPanel.testCurrentConfiguration.dbDoesNotExist.message=Database does not exist.",
    })
    private boolean testCurrentConfiguration() {
        if (CentralRepoDbManager.getSavedDbChoice() == null || 
                CentralRepoDbManager.getSavedDbChoice() == CentralRepoDbChoice.DISABLED || 
                !CentralRepoDbUtil.allowUseOfCentralRepository())
            return false;
        
        CentralRepoDbManager manager = new CentralRepoDbManager();
        DatabaseTestResult testResult = manager.testStatus();
        
        // if database doesn't exist, prompt user to create database
        if (testResult == DatabaseTestResult.DB_DOES_NOT_EXIST) {
            boolean success = EamDbSettingsDialog.promptCreateDatabase(manager, null);
            if (success)
                testResult = DatabaseTestResult.TESTED_OK;
        }
        
        // display to the user the status
        switch (testResult) {
            case TESTED_OK: return showStatusOkay();
            case DB_DOES_NOT_EXIST: return showStatusFail(Bundle.GlobalSettingsPanel_testCurrentConfiguration_dbDoesNotExist_message());
            case SCHEMA_INVALID: return showStatusFail(Bundle.EamDbSettingsDialog_okButton_corruptDatabaseExists_message());
            case CONNECTION_FAILED: 
            default: 
                return showStatusFail(Bundle.EamDbSettingsDialog_okButton_databaseConnectionFailed_message());
        }
    }

    private boolean showStatusOkay() {
        return setStatus(goodIcon, " ");
    }
    
    private boolean showStatusFail(String message) {
        return setStatus(badIcon, message);
    }

    private void clearStatus() {
        setStatus(null, " ");
    }
    
    private boolean setStatus(ImageIcon icon, String text) {
        synchronized (testStatusLabel) {
            testStatusLabel.setIcon(icon);
            testStatusLabel.setText(text);
            return true;   
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

        jScrollPane1 = new javax.swing.JScrollPane();
        jPanel1 = new javax.swing.JPanel();
        lbCentralRepository = new javax.swing.JLabel();
        cbUseCentralRepo = new javax.swing.JCheckBox();
        pnDatabaseConfiguration = new javax.swing.JPanel();
        lbDbPlatformTypeLabel = new javax.swing.JLabel();
        lbDbNameLabel = new javax.swing.JLabel();
        lbDbLocationLabel = new javax.swing.JLabel();
        bnDbConfigure = new javax.swing.JButton();
        lbDbPlatformValue = new javax.swing.JLabel();
        lbDbNameValue = new javax.swing.JLabel();
        lbDbLocationValue = new javax.swing.JLabel();
        bnTestConfigure = new javax.swing.JButton();
        testStatusLabel = new javax.swing.JLabel();
        pnCorrelationProperties = new javax.swing.JPanel();
        bnManageTypes = new javax.swing.JButton();
        correlationPropertiesScrollPane = new javax.swing.JScrollPane();
        correlationPropertiesTextArea = new javax.swing.JTextArea();
        organizationPanel = new javax.swing.JPanel();
        manageOrganizationButton = new javax.swing.JButton();
        organizationScrollPane = new javax.swing.JScrollPane();
        organizationTextArea = new javax.swing.JTextArea();
        casesPanel = new javax.swing.JPanel();
        showCasesButton = new javax.swing.JButton();
        casesScrollPane = new javax.swing.JScrollPane();
        casesTextArea = new javax.swing.JTextArea();
        tbOops = new javax.swing.JTextField();
        ingestRunningWarningLabel = new javax.swing.JLabel();

        setName(""); // NOI18N
        setPreferredSize(new java.awt.Dimension(1022, 488));

        jScrollPane1.setBorder(null);
        jScrollPane1.setPreferredSize(new java.awt.Dimension(1022, 407));

        jPanel1.setMinimumSize(new java.awt.Dimension(0, 0));
        jPanel1.setPreferredSize(new java.awt.Dimension(1020, 407));

        org.openide.awt.Mnemonics.setLocalizedText(lbCentralRepository, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.lbCentralRepository.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(cbUseCentralRepo, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.cbUseCentralRepo.text")); // NOI18N
        cbUseCentralRepo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbUseCentralRepoActionPerformed(evt);
            }
        });

        pnDatabaseConfiguration.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.pnDatabaseConfiguration.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDbPlatformTypeLabel, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.lbDbPlatformTypeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDbNameLabel, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.lbDbNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDbLocationLabel, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.lbDbLocationLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnDbConfigure, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.bnDbConfigure.text")); // NOI18N
        bnDbConfigure.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnDbConfigureActionPerformed(evt);
            }
        });

        org.openide.awt.Mnemonics.setLocalizedText(bnTestConfigure, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.bnTestConfigure.text")); // NOI18N
        bnTestConfigure.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnTestConfigureActionPerformed(evt);
            }
        });

        testStatusLabel.setFont(testStatusLabel.getFont().deriveFont(testStatusLabel.getFont().getStyle() & ~java.awt.Font.BOLD, 11));
        testStatusLabel.setForeground(new java.awt.Color(255, 0, 0));
        org.openide.awt.Mnemonics.setLocalizedText(testStatusLabel, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.testStatusLabel.text")); // NOI18N
        testStatusLabel.setToolTipText(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.testStatusLabel.toolTipText")); // NOI18N
        testStatusLabel.setMaximumSize(new java.awt.Dimension(387, 16));
        testStatusLabel.setPreferredSize(new java.awt.Dimension(387, 16));

        javax.swing.GroupLayout pnDatabaseConfigurationLayout = new javax.swing.GroupLayout(pnDatabaseConfiguration);
        pnDatabaseConfiguration.setLayout(pnDatabaseConfigurationLayout);
        pnDatabaseConfigurationLayout.setHorizontalGroup(
            pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnDatabaseConfigurationLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnDatabaseConfigurationLayout.createSequentialGroup()
                        .addGroup(pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(lbDbPlatformTypeLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbDbNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbDbLocationLabel))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lbDbNameValue, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbDbPlatformValue, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addGroup(javax.swing.GroupLayout.Alignment.LEADING, pnDatabaseConfigurationLayout.createSequentialGroup()
                                .addComponent(lbDbLocationValue, javax.swing.GroupLayout.DEFAULT_SIZE, 255, Short.MAX_VALUE)
                                .addGap(681, 681, 681))))
                    .addGroup(pnDatabaseConfigurationLayout.createSequentialGroup()
                        .addComponent(bnDbConfigure)
                        .addGap(18, 18, 18)
                        .addComponent(bnTestConfigure)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(testStatusLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 675, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        pnDatabaseConfigurationLayout.setVerticalGroup(
            pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnDatabaseConfigurationLayout.createSequentialGroup()
                .addGap(7, 7, 7)
                .addGroup(pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbDbPlatformTypeLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbDbPlatformValue, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbDbNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbDbNameValue, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(lbDbLocationLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(lbDbLocationValue, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(testStatusLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(bnDbConfigure)
                        .addComponent(bnTestConfigure)))
                .addGap(8, 8, 8))
        );

        pnCorrelationProperties.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.pnCorrelationProperties.border.title"))); // NOI18N
        pnCorrelationProperties.setPreferredSize(new java.awt.Dimension(674, 93));

        org.openide.awt.Mnemonics.setLocalizedText(bnManageTypes, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.bnManageProperties.text")); // NOI18N
        bnManageTypes.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnManageTypesActionPerformed(evt);
            }
        });

        correlationPropertiesScrollPane.setBorder(null);

        correlationPropertiesTextArea.setEditable(false);
        correlationPropertiesTextArea.setBackground(new java.awt.Color(240, 240, 240));
        correlationPropertiesTextArea.setColumns(20);
        correlationPropertiesTextArea.setLineWrap(true);
        correlationPropertiesTextArea.setRows(1);
        correlationPropertiesTextArea.setText(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.correlationPropertiesTextArea.text")); // NOI18N
        correlationPropertiesTextArea.setToolTipText("");
        correlationPropertiesTextArea.setWrapStyleWord(true);
        correlationPropertiesTextArea.setBorder(null);
        correlationPropertiesTextArea.setOpaque(false);
        correlationPropertiesScrollPane.setViewportView(correlationPropertiesTextArea);

        javax.swing.GroupLayout pnCorrelationPropertiesLayout = new javax.swing.GroupLayout(pnCorrelationProperties);
        pnCorrelationProperties.setLayout(pnCorrelationPropertiesLayout);
        pnCorrelationPropertiesLayout.setHorizontalGroup(
            pnCorrelationPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnCorrelationPropertiesLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnCorrelationPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnCorrelationPropertiesLayout.createSequentialGroup()
                        .addComponent(bnManageTypes)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(correlationPropertiesScrollPane))
                .addContainerGap())
        );
        pnCorrelationPropertiesLayout.setVerticalGroup(
            pnCorrelationPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnCorrelationPropertiesLayout.createSequentialGroup()
                .addComponent(correlationPropertiesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 24, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bnManageTypes)
                .addGap(8, 8, 8))
        );

        organizationPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.organizationPanel.border.title"))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(manageOrganizationButton, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.manageOrganizationButton.text")); // NOI18N
        manageOrganizationButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                manageOrganizationButtonActionPerformed(evt);
            }
        });

        organizationScrollPane.setBorder(null);

        organizationTextArea.setEditable(false);
        organizationTextArea.setBackground(new java.awt.Color(240, 240, 240));
        organizationTextArea.setColumns(20);
        organizationTextArea.setLineWrap(true);
        organizationTextArea.setRows(2);
        organizationTextArea.setText(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.organizationTextArea.text")); // NOI18N
        organizationTextArea.setWrapStyleWord(true);
        organizationTextArea.setBorder(null);
        organizationTextArea.setOpaque(false);
        organizationScrollPane.setViewportView(organizationTextArea);

        javax.swing.GroupLayout organizationPanelLayout = new javax.swing.GroupLayout(organizationPanel);
        organizationPanel.setLayout(organizationPanelLayout);
        organizationPanelLayout.setHorizontalGroup(
            organizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(organizationPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(organizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(organizationScrollPane)
                    .addGroup(organizationPanelLayout.createSequentialGroup()
                        .addComponent(manageOrganizationButton)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        organizationPanelLayout.setVerticalGroup(
            organizationPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, organizationPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(organizationScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(manageOrganizationButton)
                .addGap(8, 8, 8))
        );

        casesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.casesPanel.border.title"))); // NOI18N
        casesPanel.setName("Case Details"); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(showCasesButton, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.showCasesButton.text")); // NOI18N
        showCasesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                showCasesButtonActionPerformed(evt);
            }
        });

        casesScrollPane.setBorder(null);

        casesTextArea.setEditable(false);
        casesTextArea.setBackground(new java.awt.Color(240, 240, 240));
        casesTextArea.setColumns(20);
        casesTextArea.setLineWrap(true);
        casesTextArea.setRows(2);
        casesTextArea.setText(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.casesTextArea.text")); // NOI18N
        casesTextArea.setWrapStyleWord(true);
        casesTextArea.setBorder(null);
        casesTextArea.setOpaque(false);
        casesScrollPane.setViewportView(casesTextArea);

        javax.swing.GroupLayout casesPanelLayout = new javax.swing.GroupLayout(casesPanel);
        casesPanel.setLayout(casesPanelLayout);
        casesPanelLayout.setHorizontalGroup(
            casesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(casesPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(casesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(casesScrollPane)
                    .addGroup(casesPanelLayout.createSequentialGroup()
                        .addComponent(showCasesButton)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        casesPanelLayout.setVerticalGroup(
            casesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, casesPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(casesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(showCasesButton)
                .addGap(8, 8, 8))
        );

        tbOops.setEditable(false);
        tbOops.setFont(tbOops.getFont().deriveFont(tbOops.getFont().getStyle() | java.awt.Font.BOLD, tbOops.getFont().getSize()-1));
        tbOops.setText(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.tbOops.text")); // NOI18N
        tbOops.setBorder(null);

        ingestRunningWarningLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/modules/filetypeid/warning16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(ingestRunningWarningLabel, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.ingestRunningWarningLabel.text")); // NOI18N

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(lbCentralRepository, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pnDatabaseConfiguration, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnCorrelationProperties, javax.swing.GroupLayout.DEFAULT_SIZE, 1016, Short.MAX_VALUE)
                    .addComponent(organizationPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(casesPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(cbUseCentralRepo, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ingestRunningWarningLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 840, Short.MAX_VALUE))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(tbOops, javax.swing.GroupLayout.PREFERRED_SIZE, 974, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(lbCentralRepository)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cbUseCentralRepo)
                    .addComponent(ingestRunningWarningLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(pnDatabaseConfiguration, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(pnCorrelationProperties, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(organizationPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(casesPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(tbOops, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        casesPanel.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.Case Details.AccessibleContext.accessibleName")); // NOI18N

        jScrollPane1.setViewportView(jPanel1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 512, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    private void bnManageTypesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnManageTypesActionPerformed
        store();
        ManageCorrelationPropertiesDialog manageCorrelationDialog = new ManageCorrelationPropertiesDialog();
        firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
    }//GEN-LAST:event_bnManageTypesActionPerformed

    private void bnDbConfigureActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnDbConfigureActionPerformed
        store();
        boolean changed = invokeCrChoice(this, null);
        if (changed) {
            load(); // reload db settings content and update buttons
        }
    }//GEN-LAST:event_bnDbConfigureActionPerformed

    private void manageOrganizationButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manageOrganizationButtonActionPerformed
        store();
        ManageOrganizationsDialog manageOrganizationsDialog = new ManageOrganizationsDialog();
    }//GEN-LAST:event_manageOrganizationButtonActionPerformed

    private void showCasesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showCasesButtonActionPerformed
        store();
        ManageCasesDialog.displayManageCasesDialog();
    }//GEN-LAST:event_showCasesButtonActionPerformed

    private void cbUseCentralRepoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbUseCentralRepoActionPerformed
        //if saved setting is disabled checkbox should be disabled already 
        store();
        load();
        this.ingestStateUpdated(Case.isCaseOpen());
    }//GEN-LAST:event_cbUseCentralRepoActionPerformed

    private void bnTestConfigureActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnTestConfigureActionPerformed
        testCurrentConfiguration();
    }//GEN-LAST:event_bnTestConfigureActionPerformed

    @Override
    @Messages({"GlobalSettingsPanel.validationerrMsg.mustConfigure=Configure the database to enable this module."})
    public void load() {
        tbOops.setText("");
        enableButtonSubComponents(false);
        CentralRepoDbChoice selectedChoice = CentralRepoDbManager.getSavedDbChoice();
        cbUseCentralRepo.setSelected(CentralRepoDbUtil.allowUseOfCentralRepository()); // NON-NLS

        lbDbPlatformValue.setText(selectedChoice.getTitle());
        CentralRepoPlatforms selectedDb = selectedChoice.getDbPlatform();

        if (selectedChoice == null || selectedDb == CentralRepoPlatforms.DISABLED) {
            lbDbNameValue.setText("");
            lbDbLocationValue.setText("");
            tbOops.setText(Bundle.GlobalSettingsPanel_validationerrMsg_mustConfigure());
        } else {
            enableButtonSubComponents(cbUseCentralRepo.isSelected());
            if (selectedDb == CentralRepoPlatforms.POSTGRESQL) {
                try {
                    PostgresCentralRepoSettings dbSettingsPg = new PostgresCentralRepoSettings();
                    lbDbNameValue.setText(dbSettingsPg.getDbName());
                    lbDbLocationValue.setText(dbSettingsPg.getHost());
                } catch (CentralRepoException e) {
                    logger.log(Level.WARNING, "Unable to load settings into global panel for postgres settings", e);
                }
            } else if (selectedDb == CentralRepoPlatforms.SQLITE) {
                SqliteCentralRepoSettings dbSettingsSqlite = new SqliteCentralRepoSettings();
                lbDbNameValue.setText(dbSettingsSqlite.getDbName());
                lbDbLocationValue.setText(dbSettingsSqlite.getDbDirectory());
            }
        }
    }

    @Override
    public void store() { // Click OK or Apply on Options Panel
        CentralRepoDbUtil.setUseCentralRepo(cbUseCentralRepo.isSelected());
    }

    /**
     * This method validates that the dialog/panel is filled out correctly for
     * our usage.
     *
     * @return True if it is okay, false otherwise.
     */
    public boolean valid() {
        return !cbUseCentralRepo.isSelected() || !lbDbPlatformValue.getText().equals(CentralRepoDbChoice.DISABLED.toString());
    }

    @Override
    public void saveSettings() { // Click OK on Global Settings Panel
        store();
    }

    @Override
    @SuppressWarnings("FinalizeDeclaration")
    protected void finalize() throws Throwable {
        IngestManager.getInstance().removeIngestJobEventListener(ingestJobEventListener);
        super.finalize();

    }

    /**
     * An ingest job event listener that disables the options panel while an
     * ingest job is running.
     */
    private class IngestJobEventPropertyChangeListener implements PropertyChangeListener {

        /**
         * Listens for local ingest job started, completed or cancel events and
         * enables/disables the options panel according to the job state.
         *
         * @param event
         */
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (isLocalIngestJobEvent(event)) {
                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        ingestStateUpdated(Case.isCaseOpen());
                    }
                });
            }
        }
    };

    /**
     * Check that the supplied event is a local IngestJobEvent whose type is
     * STARTED, CANCELLED, or COMPLETED.
     *
     * @param event The PropertyChangeEvent to check against.
     *
     * @return True is the event is a local IngestJobEvent whose type is
     *         STARTED, CANCELLED, or COMPLETED; otherwise false.
     */
    private boolean isLocalIngestJobEvent(PropertyChangeEvent event) {
        if (event instanceof AutopsyEvent) {
            if (((AutopsyEvent) event).getSourceType() == AutopsyEvent.SourceType.LOCAL) {
                String eventType = event.getPropertyName();
                return (eventType.equals(IngestManager.IngestJobEvent.STARTED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.CANCELLED.toString())
                        || eventType.equals(IngestManager.IngestJobEvent.COMPLETED.toString()));
            }
        }
        return false;
    }

    @Messages({"GlobalSettingsPanel.validationErrMsg.ingestRunning=You cannot change settings while ingest is running."})
    private void ingestStateUpdated(boolean caseIsOpen) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> {
                ingestStateUpdated(caseIsOpen);
            });

            return;
        }

        cbUseCentralRepo.setEnabled(!caseIsOpen);
        if (IngestManager.getInstance().isIngestRunning()) {
            tbOops.setText(Bundle.GlobalSettingsPanel_validationErrMsg_ingestRunning());
            tbOops.setVisible(true);
            enableButtonSubComponents(cbUseCentralRepo.isSelected());
        } else {
            load();
        }

        enableDatabaseConfigureButton(cbUseCentralRepo.isSelected() && !caseIsOpen);
    }
    
    

    /**
     * Enable the Configure button
     *
     * @param enable
     *
     * @return True
     */
    private void enableDatabaseConfigureButton(Boolean enable) {
        boolean ingestRunning = IngestManager.getInstance().isIngestRunning();
        ingestRunningWarningLabel.setVisible(ingestRunning);

        pnDatabaseConfiguration.setEnabled(enable && !ingestRunning);
        bnDbConfigure.setEnabled(enable && !ingestRunning);
        bnTestConfigure.setEnabled(enable && !ingestRunning);
        lbDbLocationLabel.setEnabled(enable && !ingestRunning);
        lbDbLocationValue.setEnabled(enable && !ingestRunning);
        lbDbNameLabel.setEnabled(enable && !ingestRunning);
        lbDbNameValue.setEnabled(enable && !ingestRunning);
        lbDbPlatformTypeLabel.setEnabled(enable && !ingestRunning);
        lbDbPlatformValue.setEnabled(enable && !ingestRunning);
        tbOops.setEnabled(enable && !ingestRunning);
    }

    /**
     * Wrapper around each of the enableComponentXYZButton methods to
     * enable/disable them all at the same time.
     *
     * @param enable
     *
     * @return True
     */
    private boolean enableButtonSubComponents(Boolean enable) {
        boolean ingestRunning = IngestManager.getInstance().isIngestRunning();
        pnCorrelationProperties.setEnabled(enable && !ingestRunning);
        bnManageTypes.setEnabled(enable && !ingestRunning);
        correlationPropertiesTextArea.setEnabled(enable && !ingestRunning);
        organizationPanel.setEnabled(enable && !ingestRunning);
        organizationTextArea.setEnabled(enable && !ingestRunning);
        manageOrganizationButton.setEnabled(enable && !ingestRunning);
        showCasesButton.setEnabled(enable && !ingestRunning);
        casesPanel.setEnabled(enable && !ingestRunning);
        casesTextArea.setEnabled(enable && !ingestRunning);
        return true;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnDbConfigure;
    private javax.swing.JButton bnManageTypes;
    private javax.swing.JButton bnTestConfigure;
    private javax.swing.JPanel casesPanel;
    private javax.swing.JScrollPane casesScrollPane;
    private javax.swing.JTextArea casesTextArea;
    private javax.swing.JCheckBox cbUseCentralRepo;
    private javax.swing.JScrollPane correlationPropertiesScrollPane;
    private javax.swing.JTextArea correlationPropertiesTextArea;
    private javax.swing.JLabel ingestRunningWarningLabel;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lbCentralRepository;
    private javax.swing.JLabel lbDbLocationLabel;
    private javax.swing.JLabel lbDbLocationValue;
    private javax.swing.JLabel lbDbNameLabel;
    private javax.swing.JLabel lbDbNameValue;
    private javax.swing.JLabel lbDbPlatformTypeLabel;
    private javax.swing.JLabel lbDbPlatformValue;
    private javax.swing.JButton manageOrganizationButton;
    private javax.swing.JPanel organizationPanel;
    private javax.swing.JScrollPane organizationScrollPane;
    private javax.swing.JTextArea organizationTextArea;
    private javax.swing.JPanel pnCorrelationProperties;
    private javax.swing.JPanel pnDatabaseConfiguration;
    private javax.swing.JButton showCasesButton;
    private javax.swing.JTextField tbOops;
    private javax.swing.JLabel testStatusLabel;
    // End of variables declaration//GEN-END:variables
}
