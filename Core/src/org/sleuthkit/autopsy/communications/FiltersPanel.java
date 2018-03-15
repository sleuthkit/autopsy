/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import com.google.common.collect.ImmutableSet;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.CURRENT_CASE;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.ingest.IngestManager;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.DATA_ADDED;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.Account;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.CommunicationsFilter;
import org.sleuthkit.datamodel.CommunicationsFilter.AccountTypeFilter;
import org.sleuthkit.datamodel.CommunicationsFilter.DateRangeFilter;
import org.sleuthkit.datamodel.CommunicationsFilter.DeviceFilter;
import org.sleuthkit.datamodel.DataSource;
import static org.sleuthkit.datamodel.Relationship.Type.CALL_LOG;
import static org.sleuthkit.datamodel.Relationship.Type.MESSAGE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel that holds the Filter control widgets and triggers queries against the
 * CommunicationsManager on user filtering changes.
 */
final public class FiltersPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(FiltersPanel.class.getName());

    /**
     * Map from Account.Type to the checkbox for that account type's filter.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final Map<Account.Type, JCheckBox> accountTypeMap = new HashMap<>();

    /**
     * Map from datasource device id to the checkbox for that datasource.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final Map<String, JCheckBox> devicesMap = new HashMap<>();

    /**
     * Listens to ingest events to enable refresh button
     */
    private final PropertyChangeListener ingestListener;

    /**
     * Flag that indicates the UI is not up-sto-date with respect to the case DB
     * and it should be refreshed (by reapplying the filters).
     */
    private boolean needsRefresh;

    /**
     * Listen to check box state changes and validates that at least one box is
     * selected for device and account type ( other wise there will be no
     * results)
     */
    private final ItemListener validationListener;

    /**
     * Is the device account type filter enabled or not. It should be enabled
     * when the Table/Brows mode is active and disabled when the visualization
     * is active. Initially false since the browse/table mode is active
     * initially.
     */
    private boolean deviceAccountTypeEnabled;

    @NbBundle.Messages({"refreshText=Refresh Results", "applyText=Apply"})
    public FiltersPanel() {
        initComponents();
        deviceRequiredLabel.setVisible(false);
        accountTypeRequiredLabel.setVisible(false);
        startDatePicker.setDate(LocalDate.now().minusWeeks(3));
        endDatePicker.setDateToToday();
        startDatePicker.getSettings().setVetoPolicy(
                //no end date, or start is before end
                startDate -> endCheckBox.isSelected() == false
                || startDate.compareTo(endDatePicker.getDate()) <= 0
        );
        endDatePicker.getSettings().setVetoPolicy(
                //no start date, or end is after start
                endDate -> startCheckBox.isSelected() == false
                || endDate.compareTo(startDatePicker.getDate()) >= 0
        );

        updateTimeZone();
        validationListener = itemEvent -> validateFilters();

        updateFilters(true);
        UserPreferences.addChangeListener(preferenceChangeEvent -> {
            if (preferenceChangeEvent.getKey().equals(UserPreferences.DISPLAY_TIMES_IN_LOCAL_TIME)) {
                updateTimeZone();
            }
        });

        this.ingestListener = pce -> {
            String eventType = pce.getPropertyName();
            if (eventType.equals(DATA_ADDED.toString())) {
                // Indicate that a refresh may be needed, unless the data added is Keyword or Hashset hits
                ModuleDataEvent eventData = (ModuleDataEvent) pce.getOldValue();
                if (null != eventData
                        && eventData.getBlackboardArtifactType().getTypeID() != BlackboardArtifact.ARTIFACT_TYPE.TSK_KEYWORD_HIT.getTypeID()
                        && eventData.getBlackboardArtifactType().getTypeID() != BlackboardArtifact.ARTIFACT_TYPE.TSK_HASHSET_HIT.getTypeID()) {
                    updateFilters(false);
                    needsRefresh = true;
                    validateFilters();
                }
            }
        };

        applyFiltersButton.addActionListener(e -> applyFilters());
        refreshButton.addActionListener(e -> applyFilters());
    }

    /**
     * Validate that filters are in a consistent state and will result in some
     * results. Checks that at least one device and at least one account type is
     * selected. Disables the apply and refresh button and shows warnings if the
     * filters are not valid.
     */
    private void validateFilters() {
        boolean someDevice = devicesMap.values().stream().anyMatch(JCheckBox::isSelected);
        boolean someAccountType = accountTypeMap.values().stream().anyMatch(JCheckBox::isSelected);

        deviceRequiredLabel.setVisible(someDevice == false);
        accountTypeRequiredLabel.setVisible(someAccountType == false);

        applyFiltersButton.setEnabled(someDevice && someAccountType);
        refreshButton.setEnabled(someDevice && someAccountType && needsRefresh);
        needsRefreshLabel.setVisible(needsRefresh);
    }

    /**
     * Update the filter widgets, and apply them.
     */
    void updateAndApplyFilters(boolean initialState) {
        updateFilters(initialState);
        applyFilters();
    }

    private void updateTimeZone() {
        dateRangeLabel.setText("Date Range ( " + Utils.getUserPreferredZoneId().toString() + "):");
    }

    /**
     * Updates the filter widgets to reflect he data sources/types in the case.
     */
    private void updateFilters(boolean initialState) {
        updateAccountTypeFilter();
        updateDeviceFilter(initialState);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        IngestManager.getInstance().addIngestModuleEventListener(ingestListener);
        Case.addEventTypeSubscriber(EnumSet.of(CURRENT_CASE), evt -> {
            //clear the device filter widget when the case changes.
            devicesMap.clear();
            devicesPane.removeAll();
        });
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        IngestManager.getInstance().removeIngestModuleEventListener(ingestListener);
    }

    /**
     * Populate the Account Types filter widgets
     */
    private void updateAccountTypeFilter() {

        //TODO: something like this commented code could be used to show only
        //the account types that are found:
        //final CommunicationsManager communicationsManager = Case.getOpenCase().getSleuthkitCase().getCommunicationsManager();
        //List<Account.Type> accountTypesInUse = communicationsManager.getAccountTypesInUse();
        //accountTypesInUSe.forEach(...)
        Account.Type.PREDEFINED_ACCOUNT_TYPES.forEach(type -> {
            if (type.equals(Account.Type.CREDIT_CARD)) {
                //don't show a check box for credit cards
            } else {
                accountTypeMap.computeIfAbsent(type, t -> {
                    final JCheckBox jCheckBox = new JCheckBox(
                            "<html><table cellpadding=0><tr><td><img src=\""
                            + FiltersPanel.class.getResource(Utils.getIconFilePath(type))
                            + "\"/></td><td width=" + 3 + "><td>" + type.getDisplayName() + "</td></tr></table></html>",
                            true
                    );
                    jCheckBox.addItemListener(validationListener);
                    accountTypePane.add(jCheckBox);
                    if (t.equals(Account.Type.DEVICE)) {
                        //Deveice type filter is enabled based on whether we are in table or graph view.
                        jCheckBox.setEnabled(deviceAccountTypeEnabled);
                    }
                    return jCheckBox;
                });
            }
        });
    }

    /**
     * Populate the devices filter widgets
     */
    private void updateDeviceFilter(boolean initialState) {
        try {
            final SleuthkitCase sleuthkitCase = Case.getOpenCase().getSleuthkitCase();

            for (DataSource dataSource : sleuthkitCase.getDataSources()) {
                String dsName = sleuthkitCase.getContentById(dataSource.getId()).getName();
                //store the device id in the map, but display a datasource name in the UI.
                devicesMap.computeIfAbsent(dataSource.getDeviceId(), ds -> {
                    final JCheckBox jCheckBox = new JCheckBox(dsName, initialState);
                    jCheckBox.addItemListener(validationListener);
                    devicesPane.add(jCheckBox);
                    return jCheckBox;
                });
            }
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.WARNING, "Communications Visualization Tool opened with no open case.", ex);
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.SEVERE, "There was a error loading the datasources for the case.", tskCoreException);
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

        applyFiltersButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/tick.png"))); // NOI18N
        applyFiltersButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.applyFiltersButton.text")); // NOI18N
        applyFiltersButton.setPreferredSize(null);

        filtersTitleLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/funnel.png"))); // NOI18N
        filtersTitleLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.filtersTitleLabel.text")); // NOI18N

        unCheckAllAccountTypesButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.unCheckAllAccountTypesButton.text")); // NOI18N
        unCheckAllAccountTypesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unCheckAllAccountTypesButtonActionPerformed(evt);
            }
        });

        accountTypesLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/accounts.png"))); // NOI18N
        accountTypesLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.accountTypesLabel.text")); // NOI18N

        checkAllAccountTypesButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.checkAllAccountTypesButton.text")); // NOI18N
        checkAllAccountTypesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkAllAccountTypesButtonActionPerformed(evt);
            }
        });

        accountTypePane.setLayout(new javax.swing.BoxLayout(accountTypePane, javax.swing.BoxLayout.Y_AXIS));
        jScrollPane3.setViewportView(accountTypePane);

        accountTypeRequiredLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/error-icon-16.png"))); // NOI18N
        accountTypeRequiredLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.accountTypeRequiredLabel.text")); // NOI18N
        accountTypeRequiredLabel.setForeground(new java.awt.Color(255, 0, 0));
        accountTypeRequiredLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(accountTypesLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(accountTypeRequiredLabel))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jScrollPane3)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(unCheckAllAccountTypesButton)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(checkAllAccountTypesButton)))))
                .addGap(0, 0, 0))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(accountTypesLabel)
                    .addComponent(accountTypeRequiredLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 243, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkAllAccountTypesButton)
                    .addComponent(unCheckAllAccountTypesButton)))
        );

        unCheckAllDevicesButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.unCheckAllDevicesButton.text")); // NOI18N
        unCheckAllDevicesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unCheckAllDevicesButtonActionPerformed(evt);
            }
        });

        devicesLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/image.png"))); // NOI18N
        devicesLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.devicesLabel.text")); // NOI18N

        checkAllDevicesButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.checkAllDevicesButton.text")); // NOI18N
        checkAllDevicesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkAllDevicesButtonActionPerformed(evt);
            }
        });

        jScrollPane2.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane2.setMinimumSize(new java.awt.Dimension(27, 75));

        devicesPane.setMinimumSize(new java.awt.Dimension(4, 100));
        devicesPane.setLayout(new javax.swing.BoxLayout(devicesPane, javax.swing.BoxLayout.Y_AXIS));
        jScrollPane2.setViewportView(devicesPane);

        deviceRequiredLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/error-icon-16.png"))); // NOI18N
        deviceRequiredLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.deviceRequiredLabel.text")); // NOI18N
        deviceRequiredLabel.setForeground(new java.awt.Color(255, 0, 0));
        deviceRequiredLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(devicesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(deviceRequiredLabel))
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(unCheckAllDevicesButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(checkAllDevicesButton))
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(devicesLabel)
                    .addComponent(deviceRequiredLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 94, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(checkAllDevicesButton)
                    .addComponent(unCheckAllDevicesButton))
                .addGap(5, 5, 5))
        );

        startDatePicker.setEnabled(false);

        dateRangeLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/calendar.png"))); // NOI18N
        dateRangeLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.dateRangeLabel.text")); // NOI18N

        startCheckBox.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.startCheckBox.text")); // NOI18N
        startCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                startCheckBoxStateChanged(evt);
            }
        });

        endCheckBox.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.endCheckBox.text")); // NOI18N
        endCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                endCheckBoxStateChanged(evt);
            }
        });

        endDatePicker.setEnabled(false);

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(dateRangeLabel)
                .addGap(0, 0, Short.MAX_VALUE))
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                        .addComponent(endCheckBox)
                        .addGap(12, 12, 12)
                        .addComponent(endDatePicker, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(startCheckBox)
                        .addGap(12, 12, 12)
                        .addComponent(startDatePicker, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))))
        );

        jPanel4Layout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {endCheckBox, startCheckBox});

        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addComponent(dateRangeLabel)
                .addGap(6, 6, 6)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(startDatePicker, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(startCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(endDatePicker, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(endCheckBox)))
        );

        refreshButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/arrow-circle-double-135.png"))); // NOI18N
        refreshButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.refreshButton.text")); // NOI18N

        needsRefreshLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.needsRefreshLabel.text")); // NOI18N
        needsRefreshLabel.setForeground(new java.awt.Color(255, 0, 0));

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jPanel2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addComponent(filtersTitleLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(applyFiltersButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(refreshButton))
            .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(needsRefreshLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(filtersTitleLabel)
                    .addComponent(applyFiltersButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(refreshButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(needsRefreshLabel)
                .addGap(4, 4, 4)
                .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(18, 18, 18)
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(19, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Post an event with the new filters.
     */
    private void applyFilters() {
        CVTEvents.getCVTEventBus().post(new CVTEvents.FilterChangeEvent(getFilter()));
        needsRefresh = false;
        validateFilters();
    }

    private CommunicationsFilter getFilter() {
        CommunicationsFilter commsFilter = new CommunicationsFilter();
        commsFilter.addAndFilter(getDeviceFilter());
        commsFilter.addAndFilter(getAccountTypeFilter());
        commsFilter.addAndFilter(getDateRangeFilter());
        commsFilter.addAndFilter(new CommunicationsFilter.RelationshipTypeFilter(
                ImmutableSet.of(CALL_LOG, MESSAGE)));
        return commsFilter;
    }

    /**
     * Get a DeviceFilter that matches the state of the UI widgets.
     *
     * @return a DeviceFilter
     */
    private DeviceFilter getDeviceFilter() {
        DeviceFilter deviceFilter = new DeviceFilter(
                devicesMap.entrySet().stream()
                        .filter(entry -> entry.getValue().isSelected())
                        .map(Entry::getKey)
                        .collect(Collectors.toSet()));
        return deviceFilter;
    }

    /**
     * Get an AccountTypeFilter that matches the state of the UI widgets
     *
     * @return an AccountTypeFilter
     */
    private AccountTypeFilter getAccountTypeFilter() {
        AccountTypeFilter accountTypeFilter = new AccountTypeFilter(
                accountTypeMap.entrySet().stream()
                        .filter(entry -> entry.getValue().isSelected())
                        .map(entry -> entry.getKey())
                        .collect(Collectors.toSet()));
        return accountTypeFilter;
    }

    /**
     * Get an DateRangeFilter that matches the state of the UI widgets
     *
     * @return an DateRangeFilter
     */
    private DateRangeFilter getDateRangeFilter() {
        ZoneId zone = Utils.getUserPreferredZoneId();
        long start = startDatePicker.isEnabled() ? startDatePicker.getDate().atStartOfDay(zone).toEpochSecond() : 0;
        long end = endDatePicker.isEnabled() ? endDatePicker.getDate().atStartOfDay(zone).toEpochSecond() : 0;
        return new DateRangeFilter(start, end);
    }

    /**
     * Enable or disable the device account type filter. The filter should be
     * disabled for the browse/table mode and enabled for the visualization.
     *
     * @param enable True to enable the device account type filter, False to
     *               disable it.
     */
    void setDeviceAccountTypeEnabled(boolean enable) {
        deviceAccountTypeEnabled = enable;
        JCheckBox deviceCheckbox = accountTypeMap.get(Account.Type.DEVICE);
        if (deviceCheckbox != null) {
            deviceCheckbox.setEnabled(deviceAccountTypeEnabled);
        }
    }

    /**
     * Set the selection state of all the account type check boxes
     *
     * @param selected The selection state to set the check boxes to.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void setAllAccountTypesSelected(boolean selected) {
        setAllSelected(accountTypeMap, selected);
    }

    /**
     * Set the selection state of all the device check boxes
     *
     * @param selected The selection state to set the check boxes to.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void setAllDevicesSelected(boolean selected) {
        setAllSelected(devicesMap, selected);
    }

    /**
     * Helper method that sets all the checkboxes in the given map to the given
     * selection state.
     *
     * @param map      A map from anything to JCheckBoxes.
     * @param selected The selection state to set all the checkboxes to.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void setAllSelected(Map<?, JCheckBox> map, boolean selected) {
        map.values().forEach(box -> box.setSelected(selected));
    }

    private void unCheckAllAccountTypesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unCheckAllAccountTypesButtonActionPerformed
        setAllAccountTypesSelected(false);
    }//GEN-LAST:event_unCheckAllAccountTypesButtonActionPerformed

    private void checkAllAccountTypesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkAllAccountTypesButtonActionPerformed
        setAllAccountTypesSelected(true);
    }//GEN-LAST:event_checkAllAccountTypesButtonActionPerformed

    private void unCheckAllDevicesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_unCheckAllDevicesButtonActionPerformed
        setAllDevicesSelected(false);
    }//GEN-LAST:event_unCheckAllDevicesButtonActionPerformed

    private void checkAllDevicesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkAllDevicesButtonActionPerformed
        setAllDevicesSelected(true);
    }//GEN-LAST:event_checkAllDevicesButtonActionPerformed

    private void startCheckBoxStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_startCheckBoxStateChanged
        startDatePicker.setEnabled(startCheckBox.isSelected());
    }//GEN-LAST:event_startCheckBoxStateChanged

    private void endCheckBoxStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_endCheckBoxStateChanged
        endDatePicker.setEnabled(endCheckBox.isSelected());
    }//GEN-LAST:event_endCheckBoxStateChanged


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private final javax.swing.JPanel accountTypePane = new javax.swing.JPanel();
    private final javax.swing.JLabel accountTypeRequiredLabel = new javax.swing.JLabel();
    private final javax.swing.JLabel accountTypesLabel = new javax.swing.JLabel();
    private final javax.swing.JButton applyFiltersButton = new javax.swing.JButton();
    private final javax.swing.JButton checkAllAccountTypesButton = new javax.swing.JButton();
    private final javax.swing.JButton checkAllDevicesButton = new javax.swing.JButton();
    private final javax.swing.JLabel dateRangeLabel = new javax.swing.JLabel();
    private final javax.swing.JLabel deviceRequiredLabel = new javax.swing.JLabel();
    private final javax.swing.JLabel devicesLabel = new javax.swing.JLabel();
    private final javax.swing.JPanel devicesPane = new javax.swing.JPanel();
    private final javax.swing.JCheckBox endCheckBox = new javax.swing.JCheckBox();
    private final com.github.lgooddatepicker.components.DatePicker endDatePicker = new com.github.lgooddatepicker.components.DatePicker();
    private final javax.swing.JLabel filtersTitleLabel = new javax.swing.JLabel();
    private final javax.swing.JPanel jPanel2 = new javax.swing.JPanel();
    private final javax.swing.JPanel jPanel3 = new javax.swing.JPanel();
    private final javax.swing.JPanel jPanel4 = new javax.swing.JPanel();
    private final javax.swing.JScrollPane jScrollPane2 = new javax.swing.JScrollPane();
    private final javax.swing.JScrollPane jScrollPane3 = new javax.swing.JScrollPane();
    private final javax.swing.JLabel needsRefreshLabel = new javax.swing.JLabel();
    private final javax.swing.JButton refreshButton = new javax.swing.JButton();
    private final javax.swing.JCheckBox startCheckBox = new javax.swing.JCheckBox();
    private final com.github.lgooddatepicker.components.DatePicker startDatePicker = new com.github.lgooddatepicker.components.DatePicker();
    private final javax.swing.JButton unCheckAllAccountTypesButton = new javax.swing.JButton();
    private final javax.swing.JButton unCheckAllDevicesButton = new javax.swing.JButton();
    // End of variables declaration//GEN-END:variables
}
