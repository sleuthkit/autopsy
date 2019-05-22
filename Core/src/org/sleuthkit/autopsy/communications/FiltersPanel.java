/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2018 Basis Technology Corp.
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
import com.google.common.eventbus.Subscribe;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
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
import org.sleuthkit.datamodel.CommunicationsFilter.MostRecentFilter;
import org.sleuthkit.datamodel.DataSource;
import static org.sleuthkit.datamodel.Relationship.Type.CALL_LOG;
import static org.sleuthkit.datamodel.Relationship.Type.CONTACT;
import static org.sleuthkit.datamodel.Relationship.Type.MESSAGE;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Panel that holds the Filter control widgets and triggers queries against the
 * CommunicationsManager on user filtering changes.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
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
            if (preferenceChangeEvent.getKey().equals(UserPreferences.DISPLAY_TIMES_IN_LOCAL_TIME) ||
                    preferenceChangeEvent.getKey().equals(UserPreferences.TIME_ZONE_FOR_DISPLAYS)) {
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
        boolean validLimit = validateLimitValue();

        deviceRequiredLabel.setVisible(someDevice == false);
        accountTypeRequiredLabel.setVisible(someAccountType == false);
        limitErrorMsgLabel.setVisible(!validLimit);

        applyFiltersButton.setEnabled(someDevice && someAccountType && validLimit);
        refreshButton.setEnabled(someDevice && someAccountType && needsRefresh && validLimit);
        needsRefreshLabel.setVisible(needsRefresh);
    }
    
    private boolean validateLimitValue() {
        String selectedValue = (String)limitComboBox.getSelectedItem();
        if(selectedValue.trim().equalsIgnoreCase("all")) {
            return true;
        } else {
            try{
                int value = Integer.parseInt(selectedValue);
                return value > 0;
            } catch( NumberFormatException ex) {
                return false;
            }
        }
    }

    /**
     * Update the filter widgets, and apply them.
     */
    void updateAndApplyFilters(boolean initialState) {
        updateFilters(initialState);
        applyFilters();
    }

    private void updateTimeZone() {
        dateRangeLabel.setText("Date Range (" + Utils.getUserPreferredZoneId().toString() + "):");
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
            devicesListPane.removeAll();
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
        //final CommunicationsManager communicationsManager = Case.getCurrentOpenCase().getSleuthkitCase().getCommunicationsManager();
        //List<Account.Type> accountTypesInUse = communicationsManager.getAccountTypesInUse();
        //accountTypesInUSe.forEach(...)
        Account.Type.PREDEFINED_ACCOUNT_TYPES.forEach(type -> {
            if (type.equals(Account.Type.CREDIT_CARD)) {
                //don't show a check box for credit cards
            } else {
                accountTypeMap.computeIfAbsent(type, t -> {

                    CheckBoxIconPanel panel = new CheckBoxIconPanel(
                            type.getDisplayName(), 
                            new ImageIcon(FiltersPanel.class.getResource(Utils.getIconFilePath(type))));
                    panel.setSelected(true);
                    panel.addItemListener(validationListener);
                    accountTypeListPane.add(panel);
                    if (t.equals(Account.Type.DEVICE)) {
                        //Deveice type filter is enabled based on whether we are in table or graph view.
                        panel.setEnabled(deviceAccountTypeEnabled);
                    }
                    return panel.getCheckBox();
                });
            }
        });
    }

    /**
     * Populate the devices filter widgets
     */
    private void updateDeviceFilter(boolean initialState) {
        try {
            final SleuthkitCase sleuthkitCase = Case.getCurrentCaseThrows().getSleuthkitCase();

            for (DataSource dataSource : sleuthkitCase.getDataSources()) {
                String dsName = sleuthkitCase.getContentById(dataSource.getId()).getName();
                //store the device id in the map, but display a datasource name in the UI.
                devicesMap.computeIfAbsent(dataSource.getDeviceId(), ds -> {
                    final JCheckBox jCheckBox = new JCheckBox(dsName, initialState);
                    jCheckBox.addItemListener(validationListener);
                    devicesListPane.add(jCheckBox);
                    return jCheckBox;
                });
            }
        } catch (NoCurrentCaseException ex) {
            logger.log(Level.INFO, "Filter update cancelled.  Case is closed.");
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.SEVERE, "There was a error loading the datasources for the case.", tskCoreException);
        }
    }
    
    /**
     * Given a list of subFilters, set the states of the panel controls 
     * accordingly.
     * 
     * @param subFilters A list of subFilters
     */
    public void setFilters(CommunicationsFilter commFilter) {
        List<CommunicationsFilter.SubFilter> subFilters = commFilter.getAndFilters();
        subFilters.forEach(subFilter -> {
            if( subFilter instanceof DeviceFilter ) {
                setDeviceFilter((DeviceFilter)subFilter);
            } else if( subFilter instanceof AccountTypeFilter) {
                setAccountTypeFilter((AccountTypeFilter) subFilter);
            } else if (subFilter instanceof MostRecentFilter ) {
                setMostRecentFilter((MostRecentFilter)subFilter);
            }
        });
    }
    
    /**
     * Sets the state of the device filter checkboxes
     * 
     * @param deviceFilter Selected devices
     */
    private void setDeviceFilter(DeviceFilter deviceFilter) {
        Collection<String> deviceIDs = deviceFilter.getDevices();
        devicesMap.forEach((type, cb) -> {
            cb.setSelected(deviceIDs.contains(type));
        });
    }
    
     /**
     * Set the state of the account type checkboxes to match the passed in filter
     * 
     * @param typeFilter Account Types to be selected
     */
    private void setAccountTypeFilter(AccountTypeFilter typeFilter){
       
        accountTypeMap.forEach((type, cb) -> {
            cb.setSelected(typeFilter.getAccountTypes().contains(type));
        });
    }
    
    /**
     * Set up the startDatePicker and startCheckBox based on the passed in 
     * DateControlState.
     * 
     * @param state new control state
     */
    private void setStartDateControlState(DateControlState state) {
        startDatePicker.setDate(state.getDate());
        startCheckBox.setSelected(state.isEnabled());
        startDatePicker.setEnabled(state.isEnabled());
    }
    
    /**
     * Set up the endDatePicker and endCheckBox based on the passed in 
     * DateControlState. 
     * 
     * @param state new control state
     */
    private void setEndDateControlState(DateControlState state) {
        endDatePicker.setDate(state.getDate());
        endCheckBox.setSelected(state.isEnabled());
        endDatePicker.setEnabled(state.isEnabled());
    }
    
    private void setMostRecentFilter(MostRecentFilter filter) {
        int limit = filter.getLimit();
        if(limit > 0) {
            limitComboBox.setSelectedItem(filter.getLimit());
        } else {
            limitComboBox.setSelectedItem("All");
        }
    }
    
    @Subscribe
    void filtersBack(CVTEvents.StateChangeEvent event) {
        if(event.getCommunicationsState().getCommunicationsFilter() != null){
            setFilters(event.getCommunicationsState().getCommunicationsFilter());
            setStartDateControlState(event.getCommunicationsState().getStartControlState());
            setEndDateControlState(event.getCommunicationsState().getEndControlState());
            needsRefresh = false;
            validateFilters();
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

        accountTypesScrollPane.setPreferredSize(new java.awt.Dimension(2, 200));

        accountTypeListPane.setLayout(new javax.swing.BoxLayout(accountTypeListPane, javax.swing.BoxLayout.Y_AXIS));
        accountTypesScrollPane.setViewportView(accountTypeListPane);

        accountTypeRequiredLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/error-icon-16.png"))); // NOI18N
        accountTypeRequiredLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.accountTypeRequiredLabel.text")); // NOI18N
        accountTypeRequiredLabel.setForeground(new java.awt.Color(255, 0, 0));
        accountTypeRequiredLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);

        javax.swing.GroupLayout accountTypesPaneLayout = new javax.swing.GroupLayout(accountTypesPane);
        accountTypesPane.setLayout(accountTypesPaneLayout);
        accountTypesPaneLayout.setHorizontalGroup(
            accountTypesPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, accountTypesPaneLayout.createSequentialGroup()
                .addGroup(accountTypesPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(accountTypesPaneLayout.createSequentialGroup()
                        .addComponent(accountTypesLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(accountTypeRequiredLabel))
                    .addGroup(accountTypesPaneLayout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(unCheckAllAccountTypesButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(checkAllAccountTypesButton))
                    .addGroup(accountTypesPaneLayout.createSequentialGroup()
                        .addGap(10, 10, 10)
                        .addComponent(accountTypesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addGap(0, 0, 0))
        );
        accountTypesPaneLayout.setVerticalGroup(
            accountTypesPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(accountTypesPaneLayout.createSequentialGroup()
                .addGroup(accountTypesPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(accountTypesLabel)
                    .addComponent(accountTypeRequiredLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(accountTypesScrollPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(accountTypesPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
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

        devicesScrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        devicesScrollPane.setMinimumSize(new java.awt.Dimension(27, 75));

        devicesListPane.setMinimumSize(new java.awt.Dimension(4, 100));
        devicesListPane.setLayout(new javax.swing.BoxLayout(devicesListPane, javax.swing.BoxLayout.Y_AXIS));
        devicesScrollPane.setViewportView(devicesListPane);

        deviceRequiredLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/error-icon-16.png"))); // NOI18N
        deviceRequiredLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.deviceRequiredLabel.text")); // NOI18N
        deviceRequiredLabel.setForeground(new java.awt.Color(255, 0, 0));
        deviceRequiredLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);

        javax.swing.GroupLayout devicesPaneLayout = new javax.swing.GroupLayout(devicesPane);
        devicesPane.setLayout(devicesPaneLayout);
        devicesPaneLayout.setHorizontalGroup(
            devicesPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(devicesPaneLayout.createSequentialGroup()
                .addComponent(devicesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(deviceRequiredLabel))
            .addGroup(devicesPaneLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(unCheckAllDevicesButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(checkAllDevicesButton))
            .addGroup(devicesPaneLayout.createSequentialGroup()
                .addGap(10, 10, 10)
                .addComponent(devicesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        devicesPaneLayout.setVerticalGroup(
            devicesPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(devicesPaneLayout.createSequentialGroup()
                .addGroup(devicesPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(devicesLabel)
                    .addComponent(deviceRequiredLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(devicesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 94, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(devicesPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
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

        javax.swing.GroupLayout dateRangePaneLayout = new javax.swing.GroupLayout(dateRangePane);
        dateRangePane.setLayout(dateRangePaneLayout);
        dateRangePaneLayout.setHorizontalGroup(
            dateRangePaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dateRangePaneLayout.createSequentialGroup()
                .addGroup(dateRangePaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(dateRangeLabel)
                    .addGroup(dateRangePaneLayout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(dateRangePaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, dateRangePaneLayout.createSequentialGroup()
                                .addComponent(endCheckBox)
                                .addGap(12, 12, 12)
                                .addComponent(endDatePicker, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(dateRangePaneLayout.createSequentialGroup()
                                .addComponent(startCheckBox)
                                .addGap(12, 12, 12)
                                .addComponent(startDatePicker, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        dateRangePaneLayout.linkSize(javax.swing.SwingConstants.HORIZONTAL, new java.awt.Component[] {endCheckBox, startCheckBox});

        dateRangePaneLayout.setVerticalGroup(
            dateRangePaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(dateRangePaneLayout.createSequentialGroup()
                .addComponent(dateRangeLabel)
                .addGap(6, 6, 6)
                .addGroup(dateRangePaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(startDatePicker, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(startCheckBox))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(dateRangePaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(endDatePicker, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(endCheckBox)))
        );

        refreshButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/arrow-circle-double-135.png"))); // NOI18N
        refreshButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.refreshButton.text")); // NOI18N

        needsRefreshLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.needsRefreshLabel.text")); // NOI18N
        needsRefreshLabel.setForeground(new java.awt.Color(255, 0, 0));

        limitHeaderLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.limitHeaderLabel.text")); // NOI18N

        mostRecentLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.mostRecentLabel.text")); // NOI18N

        limitComboBox.setEditable(true);
        limitComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All", "10000", "5000", "1000", "500", "100" }));
        limitComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                limitComboBoxActionPerformed(evt);
            }
        });

        limitErrorMsgLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/error-icon-16.png"))); // NOI18N
        limitErrorMsgLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.limitErrorMsgLabel.text")); // NOI18N
        limitErrorMsgLabel.setForeground(new java.awt.Color(255, 0, 0));
        limitErrorMsgLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);

        javax.swing.GroupLayout limitPaneLayout = new javax.swing.GroupLayout(limitPane);
        limitPane.setLayout(limitPaneLayout);
        limitPaneLayout.setHorizontalGroup(
            limitPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(limitPaneLayout.createSequentialGroup()
                .addComponent(limitHeaderLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(limitErrorMsgLabel)
                .addContainerGap())
            .addGroup(limitPaneLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(mostRecentLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(limitComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        limitPaneLayout.setVerticalGroup(
            limitPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(limitPaneLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(limitPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(limitHeaderLabel)
                    .addComponent(limitErrorMsgLabel))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(limitPaneLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(mostRecentLabel)
                    .addComponent(limitComboBox, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 32, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(devicesPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(accountTypesPane, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addComponent(filtersTitleLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(applyFiltersButton, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(refreshButton))
            .addComponent(dateRangePane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(needsRefreshLabel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addComponent(limitPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
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
                .addComponent(devicesPane, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(accountTypesPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(dateRangePane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(limitPane, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Post an event with the new filters.
     */
    private void applyFilters() {
        CVTEvents.getCVTEventBus().post(new CVTEvents.FilterChangeEvent(getFilter(), getStartControlState(), getEndControlState()));
        needsRefresh = false;
        validateFilters();
    }

    /**
     * Get an instance of CommunicationsFilters base on the current panel state.
     * 
     * @return an instance of CommunicationsFilter
     */
    protected CommunicationsFilter getFilter() {
        CommunicationsFilter commsFilter = new CommunicationsFilter();
        commsFilter.addAndFilter(getDeviceFilter());
        commsFilter.addAndFilter(getAccountTypeFilter());
        commsFilter.addAndFilter(getDateRangeFilter());
        commsFilter.addAndFilter(new CommunicationsFilter.RelationshipTypeFilter(
                ImmutableSet.of(CALL_LOG, MESSAGE, CONTACT)));
        commsFilter.addAndFilter(getMostRecentFilter());
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
        
        return new DateRangeFilter( startCheckBox.isSelected() ? startDatePicker.getDate().atStartOfDay(zone).toEpochSecond() : 0, 
                                    endCheckBox.isSelected() ? endDatePicker.getDate().atStartOfDay(zone).toEpochSecond() : 0);
    }
    
    private MostRecentFilter getMostRecentFilter() {
        String value = (String)limitComboBox.getSelectedItem();
        if(value.trim().equalsIgnoreCase("all")){
            return new MostRecentFilter(-1);
        } else{
            try {
                int count = Integer.parseInt(value);
                return new MostRecentFilter(count);
            } catch(NumberFormatException ex) {
                return null;
            }
        }
    }
    
    private DateControlState getStartControlState() {
        return new DateControlState (startDatePicker.getDate(), startCheckBox.isSelected());
    }
    
    private DateControlState getEndControlState() {
        return new DateControlState (endDatePicker.getDate(), endCheckBox.isSelected());
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
        validateFilters();
    }//GEN-LAST:event_startCheckBoxStateChanged

    private void endCheckBoxStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_endCheckBoxStateChanged
        endDatePicker.setEnabled(endCheckBox.isSelected());
        validateFilters();
    }//GEN-LAST:event_endCheckBoxStateChanged

    private void limitComboBoxActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_limitComboBoxActionPerformed
        validateFilters();
    }//GEN-LAST:event_limitComboBoxActionPerformed

    /**
     * A class to wrap the state of the date controls that consist of a date picker
     * and a checkbox.
     * 
     */
    final class DateControlState {
        private final LocalDate date;
        private final boolean enabled;
        
        /**
         * Wraps the state of the date controls that consist of a date picker
         * and checkbox
         * 
         * @param date LocalDate value of the datepicker
         * @param enabled State of the checkbox
         */
        protected DateControlState(LocalDate date, boolean enabled) {
            this.date = date;
            this.enabled = enabled;
        }
        
        /**
         * Returns the given LocalDate from the datepicker 
         * 
         * @return Current state LocalDate
         */
        public LocalDate getDate(){
            return date;
        }
        
        /**
         * Returns the given state of the datepicker checkbox
         * 
         * @return boolean, whether or not the datepicker was enabled
         */
        public boolean isEnabled() {
            return enabled;
        }
        
    }
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private final javax.swing.JPanel accountTypeListPane = new javax.swing.JPanel();
    private final javax.swing.JLabel accountTypeRequiredLabel = new javax.swing.JLabel();
    private final javax.swing.JLabel accountTypesLabel = new javax.swing.JLabel();
    private final javax.swing.JPanel accountTypesPane = new javax.swing.JPanel();
    private final javax.swing.JScrollPane accountTypesScrollPane = new javax.swing.JScrollPane();
    private final javax.swing.JButton applyFiltersButton = new javax.swing.JButton();
    private final javax.swing.JButton checkAllAccountTypesButton = new javax.swing.JButton();
    private final javax.swing.JButton checkAllDevicesButton = new javax.swing.JButton();
    private final javax.swing.JLabel dateRangeLabel = new javax.swing.JLabel();
    private final javax.swing.JPanel dateRangePane = new javax.swing.JPanel();
    private final javax.swing.JLabel deviceRequiredLabel = new javax.swing.JLabel();
    private final javax.swing.JLabel devicesLabel = new javax.swing.JLabel();
    private final javax.swing.JPanel devicesListPane = new javax.swing.JPanel();
    private final javax.swing.JPanel devicesPane = new javax.swing.JPanel();
    private final javax.swing.JScrollPane devicesScrollPane = new javax.swing.JScrollPane();
    private final javax.swing.JCheckBox endCheckBox = new javax.swing.JCheckBox();
    private final com.github.lgooddatepicker.components.DatePicker endDatePicker = new com.github.lgooddatepicker.components.DatePicker();
    private final javax.swing.JLabel filtersTitleLabel = new javax.swing.JLabel();
    private final javax.swing.JComboBox<String> limitComboBox = new javax.swing.JComboBox<>();
    private final javax.swing.JLabel limitErrorMsgLabel = new javax.swing.JLabel();
    private final javax.swing.JLabel limitHeaderLabel = new javax.swing.JLabel();
    private final javax.swing.JPanel limitPane = new javax.swing.JPanel();
    private final javax.swing.JLabel mostRecentLabel = new javax.swing.JLabel();
    private final javax.swing.JLabel needsRefreshLabel = new javax.swing.JLabel();
    private final javax.swing.JButton refreshButton = new javax.swing.JButton();
    private final javax.swing.JCheckBox startCheckBox = new javax.swing.JCheckBox();
    private final com.github.lgooddatepicker.components.DatePicker startDatePicker = new com.github.lgooddatepicker.components.DatePicker();
    private final javax.swing.JButton unCheckAllAccountTypesButton = new javax.swing.JButton();
    private final javax.swing.JButton unCheckAllDevicesButton = new javax.swing.JButton();
    // End of variables declaration//GEN-END:variables

    
    /**
     * This class is a small panel that appears to just be a checkbox but 
     * adds the functionality of being able to show an icon between the checkbox
     * and label.
     */
    final class CheckBoxIconPanel extends JPanel{
        private final JCheckBox checkbox;
        private final JLabel label;
        
        /**
         * Creates a JPanel instance with the specified label and image.
         * 
         * @param labelText The text to be displayed by the checkbox label.
         * @param image The image to be dispayed by the label.
         */
        private CheckBoxIconPanel(String labelText, Icon image) {
            checkbox = new JCheckBox();
            label = new JLabel(labelText);
            label.setIcon(image);
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            
            add(checkbox);
            add(label);
            add(Box.createHorizontalGlue());
        }
        
        /**
         * Sets the state of the checkbox.
         * 
         * @param selected true if the button is selected, otherwise false
         */
        void setSelected(boolean selected) {
            checkbox.setSelected(selected);
        }
        
        @Override
        public void setEnabled(boolean enabled) {
            checkbox.setEnabled(enabled);
        }
        
        /**
         * Returns the instance of the JCheckBox.
         * 
         * @return JCheckbox instance
         */
        JCheckBox getCheckBox() {
            return checkbox;
        }
        
        /**
         * Adds an ItemListener to the checkbox.
         * 
         * @param l the ItemListener to be added.
         */
        void addItemListener(ItemListener l) {
            checkbox.addItemListener(l);
        }
    }
}