/*
 * Central Repository
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
package org.sleuthkit.autopsy.centralrepository.optionspanel;

import java.awt.Cursor;
import org.sleuthkit.autopsy.coreutils.Logger;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.corecomponents.OptionsPanel;
import org.sleuthkit.autopsy.events.AutopsyEvent;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbPlatformEnum;
import static org.sleuthkit.autopsy.centralrepository.datamodel.EamDbPlatformEnum.DISABLED;
import org.sleuthkit.autopsy.centralrepository.datamodel.EamDbUtil;
import org.sleuthkit.autopsy.centralrepository.datamodel.PostgresEamDbSettings;
import org.sleuthkit.autopsy.centralrepository.datamodel.SqliteEamDbSettings;

/**
 * Main settings panel for the Central Repository
 */
public final class GlobalSettingsPanel extends IngestModuleGlobalSettingsPanel implements OptionsPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(GlobalSettingsPanel.class.getName());

    private final IngestJobEventPropertyChangeListener ingestJobEventListener;

    /**
     * Creates new form EamOptionsPanel
     */
    public GlobalSettingsPanel() {
        ingestJobEventListener = new IngestJobEventPropertyChangeListener();

        initComponents();
        customizeComponents();
        addIngestJobEventsListener();
    }

    @Messages({"GlobalSettingsPanel.title=Central Repository Settings",
        "GlobalSettingsPanel.cbUseCentralRepo.text=Use a central repository",
        "GlobalSettingsPanel.pnTagManagement.border.title=Tags",
        "GlobalSettingsPanel.pnCorrelationProperties.border.title=Correlation Properties",
        "GlobalSettingsPanel.lbCentralRepository.text=A central repository allows you to correlate files and results between cases.",
        "GlobalSettingsPanel.manageTagsTextArea.text=Configure which tag names are associated with notable items. "
        + "When these tags are used, the file or result will be recorded in the central repository. "
        + "If that file or result is seen again in future cases, it will be flagged.",
        "GlobalSettingsPanel.correlationPropertiesTextArea.text=Choose which file and result properties to store in the central repository for later correlation.",
        "GlobalSettingsPanel.organizationPanel.border.title=Organizations",
        "GlobalSettingsPanel.manageOrganizationButton.text=Manage Organizations",
        "GlobalSettingsPanel.organizationTextArea.text=Organization information can be tracked in the central repository"
    })

    private void customizeComponents() {
        setName(Bundle.GlobalSettingsPanel_title());
    }

    private void addIngestJobEventsListener() {
        IngestManager.getInstance().addIngestJobEventListener(ingestJobEventListener);
        ingestStateUpdated();
    }
    
    @Messages({"GlobalSettingsPanel.updateFailed.title=Update failed",
               "GlobalSettingsPanel.updateFailed.message=Failed to update database. Central repository has been disabled."
    })
    private void updateDatabase(){
        
        if(EamDbPlatformEnum.getSelectedPlatform().equals(DISABLED)){
            return;
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        
        try {
            boolean result = EamDbUtil.upgradeDatabase();
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            if(! result){
                JOptionPane.showMessageDialog(this,
                                        NbBundle.getMessage(this.getClass(),
                                                "GlobalSettingsPanel.updateFailed.message"),
                                        NbBundle.getMessage(this.getClass(),
                                                "GlobalSettingsPanel.updateFailed.title"),
                                        JOptionPane.WARNING_MESSAGE);
            }
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
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

        pnDatabaseConfiguration = new javax.swing.JPanel();
        lbDbPlatformTypeLabel = new javax.swing.JLabel();
        lbDbNameLabel = new javax.swing.JLabel();
        lbDbLocationLabel = new javax.swing.JLabel();
        bnDbConfigure = new javax.swing.JButton();
        lbDbPlatformValue = new javax.swing.JLabel();
        lbDbNameValue = new javax.swing.JLabel();
        lbDbLocationValue = new javax.swing.JLabel();
        cbUseCentralRepo = new javax.swing.JCheckBox();
        tbOops = new javax.swing.JTextField();
        pnCorrelationProperties = new javax.swing.JPanel();
        bnManageTypes = new javax.swing.JButton();
        correlationPropertiesScrollPane = new javax.swing.JScrollPane();
        correlationPropertiesTextArea = new javax.swing.JTextArea();
        lbCentralRepository = new javax.swing.JLabel();
        organizationPanel = new javax.swing.JPanel();
        manageOrganizationButton = new javax.swing.JButton();
        organizationScrollPane = new javax.swing.JScrollPane();
        organizationTextArea = new javax.swing.JTextArea();

        setName(""); // NOI18N

        pnDatabaseConfiguration.setBorder(javax.swing.BorderFactory.createTitledBorder(null, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.pnDatabaseConfiguration.title"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 12))); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDbPlatformTypeLabel, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.lbDbPlatformTypeLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDbNameLabel, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.lbDbNameLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lbDbLocationLabel, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.lbDbLocationLabel.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(bnDbConfigure, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.bnDbConfigure.text")); // NOI18N
        bnDbConfigure.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bnDbConfigureActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout pnDatabaseConfigurationLayout = new javax.swing.GroupLayout(pnDatabaseConfiguration);
        pnDatabaseConfiguration.setLayout(pnDatabaseConfigurationLayout);
        pnDatabaseConfigurationLayout.setHorizontalGroup(
            pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnDatabaseConfigurationLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnDatabaseConfigurationLayout.createSequentialGroup()
                        .addComponent(bnDbConfigure)
                        .addContainerGap())
                    .addGroup(pnDatabaseConfigurationLayout.createSequentialGroup()
                        .addGroup(pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(lbDbPlatformTypeLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbDbNameLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbDbLocationLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 57, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(pnDatabaseConfigurationLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lbDbNameValue, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbDbPlatformValue, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbDbLocationValue, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))))
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
                .addComponent(bnDbConfigure)
                .addGap(8, 8, 8))
        );

        org.openide.awt.Mnemonics.setLocalizedText(cbUseCentralRepo, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.cbUseCentralRepo.text")); // NOI18N
        cbUseCentralRepo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cbUseCentralRepoActionPerformed(evt);
            }
        });

        tbOops.setEditable(false);
        tbOops.setFont(tbOops.getFont().deriveFont(tbOops.getFont().getStyle() | java.awt.Font.BOLD, 12));
        tbOops.setText(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.tbOops.text")); // NOI18N
        tbOops.setBorder(null);

        pnCorrelationProperties.setBorder(javax.swing.BorderFactory.createTitledBorder(null, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.pnCorrelationProperties.border.title"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 12))); // NOI18N
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
        correlationPropertiesTextArea.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        correlationPropertiesTextArea.setLineWrap(true);
        correlationPropertiesTextArea.setRows(2);
        correlationPropertiesTextArea.setText(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.correlationPropertiesTextArea.text")); // NOI18N
        correlationPropertiesTextArea.setToolTipText("");
        correlationPropertiesTextArea.setWrapStyleWord(true);
        correlationPropertiesTextArea.setBorder(null);
        correlationPropertiesScrollPane.setViewportView(correlationPropertiesTextArea);

        javax.swing.GroupLayout pnCorrelationPropertiesLayout = new javax.swing.GroupLayout(pnCorrelationProperties);
        pnCorrelationProperties.setLayout(pnCorrelationPropertiesLayout);
        pnCorrelationPropertiesLayout.setHorizontalGroup(
            pnCorrelationPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnCorrelationPropertiesLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnCorrelationPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(correlationPropertiesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 642, Short.MAX_VALUE)
                    .addGroup(pnCorrelationPropertiesLayout.createSequentialGroup()
                        .addComponent(bnManageTypes)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        pnCorrelationPropertiesLayout.setVerticalGroup(
            pnCorrelationPropertiesLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnCorrelationPropertiesLayout.createSequentialGroup()
                .addGap(7, 7, 7)
                .addComponent(correlationPropertiesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(bnManageTypes)
                .addGap(8, 8, 8))
        );

        org.openide.awt.Mnemonics.setLocalizedText(lbCentralRepository, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.lbCentralRepository.text")); // NOI18N

        organizationPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(null, org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.organizationPanel.border.title"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 12))); // NOI18N

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
        organizationTextArea.setFont(new java.awt.Font("Tahoma", 0, 11)); // NOI18N
        organizationTextArea.setLineWrap(true);
        organizationTextArea.setRows(2);
        organizationTextArea.setText(org.openide.util.NbBundle.getMessage(GlobalSettingsPanel.class, "GlobalSettingsPanel.organizationTextArea.text")); // NOI18N
        organizationTextArea.setWrapStyleWord(true);
        organizationTextArea.setBorder(null);
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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tbOops, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(organizationPanel, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(lbCentralRepository, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(pnCorrelationProperties, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(pnDatabaseConfiguration, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(cbUseCentralRepo, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 186, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap())))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(lbCentralRepository)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cbUseCentralRepo)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnDatabaseConfiguration, javax.swing.GroupLayout.PREFERRED_SIZE, 119, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(pnCorrelationProperties, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(organizationPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(tbOops, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void bnManageTypesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnManageTypesActionPerformed
        store();
        ManageCorrelationPropertiesDialog dialog = new ManageCorrelationPropertiesDialog();
        firePropertyChange(OptionsPanelController.PROP_VALID, null, null);
    }//GEN-LAST:event_bnManageTypesActionPerformed

    private void bnDbConfigureActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bnDbConfigureActionPerformed
        store();
        EamDbSettingsDialog dialog = new EamDbSettingsDialog();
        updateDatabase();
        load(); // reload db settings content and update buttons
        if (dialog.wasConfigurationChanged()) {
            firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
        }
    }//GEN-LAST:event_bnDbConfigureActionPerformed

    private void cbUseCentralRepoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cbUseCentralRepoActionPerformed
        //if saved setting is disabled checkbox should be disabled already 
        store();
        updateDatabase();
        load();
        this.ingestStateUpdated();
        firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
    }//GEN-LAST:event_cbUseCentralRepoActionPerformed

    private void manageOrganizationButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_manageOrganizationButtonActionPerformed
        store();
        ManageOrganizationsDialog dialog = new ManageOrganizationsDialog();
    }//GEN-LAST:event_manageOrganizationButtonActionPerformed

    @Override
    @Messages({"GlobalSettingsPanel.validationerrMsg.mustConfigure=Configure the database to enable this module."})
    public void load() {
        tbOops.setText("");
        enableAllSubComponents(false);
        EamDbPlatformEnum selectedPlatform = EamDbPlatformEnum.getSelectedPlatform();
        cbUseCentralRepo.setSelected(EamDbUtil.useCentralRepo()); // NON-NLS
        switch (selectedPlatform) {
            case POSTGRESQL:
                PostgresEamDbSettings dbSettingsPg = new PostgresEamDbSettings();
                lbDbPlatformValue.setText(EamDbPlatformEnum.POSTGRESQL.toString());
                lbDbNameValue.setText(dbSettingsPg.getDbName());
                lbDbLocationValue.setText(dbSettingsPg.getHost());
                enableAllSubComponents(true);
                break;
            case SQLITE:
                SqliteEamDbSettings dbSettingsSqlite = new SqliteEamDbSettings();
                lbDbPlatformValue.setText(EamDbPlatformEnum.SQLITE.toString());
                lbDbNameValue.setText(dbSettingsSqlite.getDbName());
                lbDbLocationValue.setText(dbSettingsSqlite.getDbDirectory());
                enableAllSubComponents(true);
                break;
            default:
                lbDbPlatformValue.setText(EamDbPlatformEnum.DISABLED.toString());
                lbDbNameValue.setText("");
                lbDbLocationValue.setText("");
                enableDatabaseConfigureButton(cbUseCentralRepo.isSelected());
                tbOops.setText(Bundle.GlobalSettingsPanel_validationerrMsg_mustConfigure());
                break;
        }

    }

    @Override
    public void store() { // Click OK or Apply on Options Panel
        EamDbUtil.setUseCentralRepo(cbUseCentralRepo.isSelected());
    }

    /**
     * Validates that the dialog/panel is filled out correctly for our usage.
     *
     * @return true if it's okay, false otherwise.
     */
    public boolean valid() {
        return !cbUseCentralRepo.isSelected() || !lbDbPlatformValue.getText().equals(DISABLED.toString());
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
            if (AutopsyEvent.SourceType.LOCAL == ((AutopsyEvent) event).getSourceType()) {
                ingestStateUpdated();
            }
        }
    };

    @Messages({"GlobalSettingsPanel.validationErrMsg.ingestRunning=You cannot change settings while ingest is running."})
    private void ingestStateUpdated() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> {
                ingestStateUpdated();
            });

            return;
        }

        if (IngestManager.getInstance().isIngestRunning()) {
            tbOops.setText(Bundle.GlobalSettingsPanel_validationErrMsg_ingestRunning());
            cbUseCentralRepo.setEnabled(false);
        } else if (!cbUseCentralRepo.isEnabled()) {
            cbUseCentralRepo.setEnabled(true);
            load();
        }
    }

    /**
     * Wrapper around each of the enableComponentXYZ methods to enable/disable
     * them all at the same time.
     *
     * @param enable
     *
     * @return True
     */
    private boolean enableAllSubComponents(Boolean enable) {
        enableDatabaseConfigureButton(cbUseCentralRepo.isSelected() && enable);
        enableButtonSubComponents(cbUseCentralRepo.isSelected() && enable);
        return true;
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
        pnDatabaseConfiguration.setEnabled(enable && !ingestRunning);
        bnDbConfigure.setEnabled(enable && !ingestRunning);
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
        return true;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton bnDbConfigure;
    private javax.swing.JButton bnManageTypes;
    private javax.swing.JCheckBox cbUseCentralRepo;
    private javax.swing.JScrollPane correlationPropertiesScrollPane;
    private javax.swing.JTextArea correlationPropertiesTextArea;
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
    private javax.swing.JTextField tbOops;
    // End of variables declaration//GEN-END:variables
}
