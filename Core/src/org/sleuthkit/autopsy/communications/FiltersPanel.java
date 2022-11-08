/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017-2019 Basis Technology Corp.
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

import org.sleuthkit.autopsy.guiutils.RefreshThrottler;
import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import java.awt.event.ItemListener;
import java.beans.PropertyChangeListener;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.ingest.IngestManager;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestJobEvent.COMPLETED;
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

/**
 * Panel that holds the Filter control widgets and triggers queries against the
 * CommunicationsManager on user filtering changes.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
final public class FiltersPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(FiltersPanel.class.getName());
    private static final Set<IngestManager.IngestJobEvent> INGEST_JOB_EVENTS_OF_INTEREST = EnumSet.of(IngestManager.IngestJobEvent.COMPLETED);
    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(DATA_ADDED);
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
    private final PropertyChangeListener ingestJobListener;

    /**
     * Flag that indicates the UI is not up-to-date with respect to the case DB
     * and it should be refreshed (by reapplying the filters).
     */
    private boolean needsRefresh;

    /**
     * Listen to check box state changes and validates that at least one box is
     * selected for device and account type ( other wise there will be no
     * results)
     */
    private final ItemListener validationListener;

    private final RefreshThrottler refreshThrottler;

    /**
     * Is the device account type filter enabled or not. It should be enabled
     * when the Table/Brows mode is active and disabled when the visualization
     * is active. Initially false since the browse/table mode is active
     * initially.
     */
    private boolean deviceAccountTypeEnabled;

    private Case openCase = null;

    @NbBundle.Messages({"refreshText=Refresh Results", "applyText=Apply"})
    public FiltersPanel() {
        initComponents();

        initalizeDeviceAccountType();
        setDateTimeFiltersToDefault();

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

        UserPreferences.addChangeListener(preferenceChangeEvent -> {
            if (preferenceChangeEvent.getKey().equals(UserPreferences.DISPLAY_TIMES_IN_LOCAL_TIME)
                    || preferenceChangeEvent.getKey().equals(UserPreferences.TIME_ZONE_FOR_DISPLAYS)) {
                updateTimeZone();
            }
        });

        this.ingestListener = pce -> {
            String eventType = pce.getPropertyName();
            if (eventType.equals(DATA_ADDED.toString())) {
                // Indicate that a refresh may be needed, unless the data added is Keyword or Hashset hits
                ModuleDataEvent eventData = (ModuleDataEvent) pce.getOldValue();
                if (!needsRefresh
                        && null != eventData
                        && (eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE.getTypeID()
                        || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_CONTACT.getTypeID()
                        || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_CALLLOG.getTypeID()
                        || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_EMAIL_MSG.getTypeID())) {
                    needsRefresh = true;
                    validateFilters();
                }
            }
        };

        refreshThrottler = new RefreshThrottler(new FilterPanelRefresher(false, false));

        this.ingestJobListener = pce -> {
            String eventType = pce.getPropertyName();
            if (eventType.equals(COMPLETED.toString()) && !needsRefresh) {

                needsRefresh = true;
                validateFilters();

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
        String selectedValue = (String) limitComboBox.getSelectedItem();
        if (selectedValue.trim().equalsIgnoreCase("all")) {
            return true;
        } else {
            try {
                int value = Integer.parseInt(selectedValue);
                return value > 0;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
    }

    void initalizeFilters() {
        
        applyFiltersButton.setEnabled(false);
        refreshButton.setEnabled(true);
        needsRefreshLabel.setText("Loading filters...");
        needsRefreshLabel.setVisible(true);
        
        (new Thread(new Runnable(){
            @Override
            public void run() {
                new FilterPanelRefresher(true, true).refresh();
            }
        })).start();
    }

    private void updateTimeZone() {
        dateRangeLabel.setText("Date Range (" + Utils.getUserPreferredZoneId().toString() + "):");
    }

    @Override
    public void addNotify() {
        super.addNotify();
        refreshThrottler.registerForIngestModuleEvents();
        IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, ingestListener);
        IngestManager.getInstance().addIngestJobEventListener(INGEST_JOB_EVENTS_OF_INTEREST, ingestJobListener);
        Case.addEventTypeSubscriber(EnumSet.of(CURRENT_CASE), evt -> {
            //clear the device filter widget when the case changes.
            devicesMap.clear();
            devicesListPane.removeAll();

            accountTypeMap.clear();
            accountTypeListPane.removeAll();

            initalizeDeviceAccountType();
        });
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        refreshThrottler.unregisterEventListener();
        IngestManager.getInstance().removeIngestModuleEventListener(ingestListener);
        IngestManager.getInstance().removeIngestJobEventListener(ingestJobListener);
    }

    private void initalizeDeviceAccountType() {
        CheckBoxIconPanel panel = createAccoutTypeCheckBoxPanel(Account.Type.DEVICE, true);
        accountTypeMap.put(Account.Type.DEVICE, panel.getCheckBox());
        accountTypeListPane.add(panel);
    }

    /**
     * Populate the Account Types filter widgets.
     *
     * @param accountTypesInUse List of accountTypes currently in use
     * @param checkNewOnes
     *
     * @return True, if a new accountType was found
     */
    private boolean updateAccountTypeFilter(List<Account.Type> accountTypesInUse, boolean checkNewOnes) {
        boolean newOneFound = false;

        for (Account.Type type : accountTypesInUse) {
            if (!accountTypeMap.containsKey(type) && !type.equals(Account.Type.CREDIT_CARD)) {
                CheckBoxIconPanel panel = createAccoutTypeCheckBoxPanel(type, checkNewOnes);
                accountTypeMap.put(type, panel.getCheckBox());
                accountTypeListPane.add(panel);

                newOneFound = true;
            }
        }

        if (newOneFound) {
            accountTypeListPane.validate();
        }

        return newOneFound;
    }

    /**
     * Helper function to create a new instance of the CheckBoxIconPanel base on
     * the Account.Type and initalState (check box state).
     *
     * @param type        Account.Type to display on the panel
     * @param initalState initial check box state
     *
     * @return instance of the CheckBoxIconPanel
     */
    private CheckBoxIconPanel createAccoutTypeCheckBoxPanel(Account.Type type, boolean initalState) {
        CheckBoxIconPanel panel = new CheckBoxIconPanel(
                type.getDisplayName(),
                new ImageIcon(FiltersPanel.class.getResource(Utils.getIconFilePath(type))));

        panel.setSelected(initalState);
        panel.addItemListener(validationListener);
        return panel;
    }

    /**
     * Populate the devices filter widgets.
     *
     * @param dataSourceMap
     * @param checkNewOnes 
     *
     * @return true if a new device was found
     */
    private void updateDeviceFilterPanel(Map<String, DataSource> dataSourceMap, boolean checkNewOnes) {
        boolean newOneFound = false;
        for (Entry<String, DataSource> entry : dataSourceMap.entrySet()) {
            if (devicesMap.containsKey(entry.getValue().getDeviceId())) {
                continue;
            }

            final JCheckBox jCheckBox = new JCheckBox(entry.getKey(), checkNewOnes);
            jCheckBox.addItemListener(validationListener);
            jCheckBox.setToolTipText(entry.getKey());
            devicesListPane.add(jCheckBox);
            devicesMap.put(entry.getValue().getDeviceId(), jCheckBox);

            newOneFound = true;
        }

        if (newOneFound) {
            devicesListPane.removeAll();
            List<JCheckBox> checkList = new ArrayList<>(devicesMap.values());
            checkList.sort(new DeviceCheckBoxComparator());

            for (JCheckBox cb : checkList) {
                devicesListPane.add(cb);
            }

            devicesListPane.revalidate();
        }
    }

    private void updateDateTimePicker(Integer start, Integer end) {
        if (start != null && start != 0) {
            startDatePicker.setDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(start), Utils.getUserPreferredZoneId()).toLocalDate());
        }

        if (end != null && end != 0) {
            endDatePicker.setDate(LocalDateTime.ofInstant(Instant.ofEpochSecond(end), Utils.getUserPreferredZoneId()).toLocalDate());
        }
    }

    /**
     * Given a list of subFilters, set the states of the panel controls
     * accordingly.
     *
     * @param commFilter Contains a list of subFilters
     */
    public void setFilters(CommunicationsFilter commFilter) {
        List<CommunicationsFilter.SubFilter> subFilters = commFilter.getAndFilters();
        subFilters.forEach(subFilter -> {
            if (subFilter instanceof DeviceFilter) {
                setDeviceFilter((DeviceFilter) subFilter);
            } else if (subFilter instanceof AccountTypeFilter) {
                setAccountTypeFilter((AccountTypeFilter) subFilter);
            } else if (subFilter instanceof MostRecentFilter) {
                setMostRecentFilter((MostRecentFilter) subFilter);
            }
        });
    }

    /**
     * Sets the state of the device filter check boxes
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
     * Set the state of the account type checkboxes to match the passed in
     * filter
     *
     * @param typeFilter Account Types to be selected
     */
    private void setAccountTypeFilter(AccountTypeFilter typeFilter) {

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

    /**
     * Sets the state of the most recent UI controls based on the current values
     * in MostRecentFilter.
     *
     * @param filter The MostRecentFilter state to be set
     */
    private void setMostRecentFilter(MostRecentFilter filter) {
        int limit = filter.getLimit();
        if (limit > 0) {
            limitComboBox.setSelectedItem(filter.getLimit());
        } else {
            limitComboBox.setSelectedItem("All");
        }
    }

    @Subscribe
    void filtersBack(CVTEvents.StateChangeEvent event) {
        if (event.getCommunicationsState().getCommunicationsFilter() != null) {
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
        java.awt.GridBagConstraints gridBagConstraints;

        setLayout(new java.awt.GridBagLayout());

        scrollPane.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setAutoscrolls(true);
        scrollPane.setBorder(null);

        mainPanel.setLayout(new java.awt.GridBagLayout());

        limitPane.setLayout(new java.awt.GridBagLayout());

        mostRecentLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.mostRecentLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 9, 0, 9);
        limitPane.add(mostRecentLabel, gridBagConstraints);

        limitComboBox.setEditable(true);
        limitComboBox.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "All", "10000", "5000", "1000", "500", "100" }));
        limitComboBox.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                limitComboBoxActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        limitPane.add(limitComboBox, gridBagConstraints);

        limitTitlePanel.setLayout(new java.awt.GridBagLayout());

        limitHeaderLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.limitHeaderLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        limitTitlePanel.add(limitHeaderLabel, gridBagConstraints);

        limitErrorMsgLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/error-icon-16.png"))); // NOI18N
        limitErrorMsgLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.limitErrorMsgLabel.text")); // NOI18N
        limitErrorMsgLabel.setForeground(new java.awt.Color(255, 0, 0));
        limitErrorMsgLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        limitTitlePanel.add(limitErrorMsgLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 9, 0);
        limitPane.add(limitTitlePanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 15, 25);
        mainPanel.add(limitPane, gridBagConstraints);

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

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 0, 25);
        mainPanel.add(dateRangePane, gridBagConstraints);

        devicesPane.setPreferredSize(new java.awt.Dimension(300, 300));
        devicesPane.setLayout(new java.awt.GridBagLayout());

        unCheckAllDevicesButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.unCheckAllDevicesButton.text")); // NOI18N
        unCheckAllDevicesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unCheckAllDevicesButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 9);
        devicesPane.add(unCheckAllDevicesButton, gridBagConstraints);

        devicesLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/image.png"))); // NOI18N
        devicesLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.devicesLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 9, 0);
        devicesPane.add(devicesLabel, gridBagConstraints);

        checkAllDevicesButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.checkAllDevicesButton.text")); // NOI18N
        checkAllDevicesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkAllDevicesButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 0);
        devicesPane.add(checkAllDevicesButton, gridBagConstraints);

        devicesScrollPane.setMaximumSize(new java.awt.Dimension(32767, 30));
        devicesScrollPane.setMinimumSize(new java.awt.Dimension(27, 30));
        devicesScrollPane.setPreferredSize(new java.awt.Dimension(3, 30));

        devicesListPane.setMinimumSize(new java.awt.Dimension(4, 100));
        devicesListPane.setLayout(new javax.swing.BoxLayout(devicesListPane, javax.swing.BoxLayout.Y_AXIS));
        devicesScrollPane.setViewportView(devicesListPane);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        devicesPane.add(devicesScrollPane, gridBagConstraints);

        deviceRequiredLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/error-icon-16.png"))); // NOI18N
        deviceRequiredLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.deviceRequiredLabel.text")); // NOI18N
        deviceRequiredLabel.setForeground(new java.awt.Color(255, 0, 0));
        deviceRequiredLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 9, 0);
        devicesPane.add(deviceRequiredLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 0, 25);
        mainPanel.add(devicesPane, gridBagConstraints);

        accountTypesPane.setLayout(new java.awt.GridBagLayout());

        unCheckAllAccountTypesButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.unCheckAllAccountTypesButton.text")); // NOI18N
        unCheckAllAccountTypesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                unCheckAllAccountTypesButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 9);
        accountTypesPane.add(unCheckAllAccountTypesButton, gridBagConstraints);

        accountTypesLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/accounts.png"))); // NOI18N
        accountTypesLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.accountTypesLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        accountTypesPane.add(accountTypesLabel, gridBagConstraints);

        checkAllAccountTypesButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.checkAllAccountTypesButton.text")); // NOI18N
        checkAllAccountTypesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                checkAllAccountTypesButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 0);
        accountTypesPane.add(checkAllAccountTypesButton, gridBagConstraints);

        accountTypesScrollPane.setMaximumSize(new java.awt.Dimension(32767, 210));
        accountTypesScrollPane.setMinimumSize(new java.awt.Dimension(20, 210));
        accountTypesScrollPane.setName(""); // NOI18N
        accountTypesScrollPane.setPreferredSize(new java.awt.Dimension(2, 210));

        accountTypeListPane.setLayout(new javax.swing.BoxLayout(accountTypeListPane, javax.swing.BoxLayout.PAGE_AXIS));
        accountTypesScrollPane.setViewportView(accountTypeListPane);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 0);
        accountTypesPane.add(accountTypesScrollPane, gridBagConstraints);

        accountTypeRequiredLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/error-icon-16.png"))); // NOI18N
        accountTypeRequiredLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.accountTypeRequiredLabel.text")); // NOI18N
        accountTypeRequiredLabel.setForeground(new java.awt.Color(255, 0, 0));
        accountTypeRequiredLabel.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        accountTypesPane.add(accountTypeRequiredLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 0, 25);
        mainPanel.add(accountTypesPane, gridBagConstraints);

        topPane.setLayout(new java.awt.GridBagLayout());

        filtersTitleLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/funnel.png"))); // NOI18N
        filtersTitleLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.filtersTitleLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        topPane.add(filtersTitleLabel, gridBagConstraints);

        refreshButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/arrow-circle-double-135.png"))); // NOI18N
        refreshButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.refreshButton.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        topPane.add(refreshButton, gridBagConstraints);

        applyFiltersButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/communications/images/tick.png"))); // NOI18N
        applyFiltersButton.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.applyFiltersButton.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
        topPane.add(applyFiltersButton, gridBagConstraints);

        needsRefreshLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.needsRefreshLabel.text")); // NOI18N
        needsRefreshLabel.setForeground(new java.awt.Color(255, 0, 0));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        topPane.add(needsRefreshLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_END;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 25);
        mainPanel.add(topPane, gridBagConstraints);

        scrollPane.setViewportView(mainPanel);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(9, 15, 0, 0);
        add(scrollPane, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Post an event with the new filters.
     */
    void applyFilters() {
        needsRefresh = false;
        validateFilters();
        CVTEvents.getCVTEventBus().post(new CVTEvents.FilterChangeEvent(getFilter(), getStartControlState(), getEndControlState()));

    }

    /**
     * Get an instance of CommunicationsFilters base on the current panel state.
     *
     * @return an instance of CommunicationsFilter
     */
    private CommunicationsFilter getFilter() {
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

        return new DateRangeFilter(startCheckBox.isSelected() ? startDatePicker.getDate().atStartOfDay(zone).toEpochSecond() : 0,
                endCheckBox.isSelected() ? endDatePicker.getDate().atStartOfDay(zone).toEpochSecond() : 0);
    }

    /**
     * Get a MostRecentFilter that based on the current state of the ui
     * controls.
     *
     * @return A new instance of MostRecentFilter
     */
    private MostRecentFilter getMostRecentFilter() {
        String value = (String) limitComboBox.getSelectedItem();
        if (value.trim().equalsIgnoreCase("all")) {
            return new MostRecentFilter(-1);
        } else {
            try {
                int count = Integer.parseInt(value);
                return new MostRecentFilter(count);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private DateControlState getStartControlState() {
        return new DateControlState(startDatePicker.getDate(), startCheckBox.isSelected());
    }

    private DateControlState getEndControlState() {
        return new DateControlState(endDatePicker.getDate(), endCheckBox.isSelected());
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
     * Helper method that sets all the check boxes in the given map to the given
     * selection state.
     *
     * @param map      A map from anything to JCheckBoxes.
     * @param selected The selection state to set all the check boxes to.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private void setAllSelected(Map<?, JCheckBox> map, boolean selected) {
        map.values().forEach(box -> box.setSelected(selected));
    }

    private void setDateTimeFiltersToDefault() {
        startDatePicker.setDate(LocalDate.now().minusWeeks(3));
        endDatePicker.setDate(LocalDate.now());
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
     * A class to wrap the state of the date controls that consist of a date
     * picker and a checkbox.
     *
     */
    final class DateControlState {

        private final LocalDate date;
        private final boolean enabled;

        /**
         * Wraps the state of the date controls that consist of a date picker
         * and checkbox
         *
         * @param date    LocalDate value of the datepicker
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
        public LocalDate getDate() {
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
    private final javax.swing.JPanel limitTitlePanel = new javax.swing.JPanel();
    private final javax.swing.JPanel mainPanel = new javax.swing.JPanel();
    private final javax.swing.JLabel mostRecentLabel = new javax.swing.JLabel();
    private final javax.swing.JLabel needsRefreshLabel = new javax.swing.JLabel();
    private final javax.swing.JButton refreshButton = new javax.swing.JButton();
    private final javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane();
    private final javax.swing.JCheckBox startCheckBox = new javax.swing.JCheckBox();
    private final com.github.lgooddatepicker.components.DatePicker startDatePicker = new com.github.lgooddatepicker.components.DatePicker();
    private final javax.swing.JPanel topPane = new javax.swing.JPanel();
    private final javax.swing.JButton unCheckAllAccountTypesButton = new javax.swing.JButton();
    private final javax.swing.JButton unCheckAllDevicesButton = new javax.swing.JButton();
    // End of variables declaration//GEN-END:variables

    /**
     * This class is a small panel that appears to just be a checkbox but adds
     * the functionality of being able to show an icon between the checkbox and
     * label.
     */
    final class CheckBoxIconPanel extends JPanel {

        private static final long serialVersionUID = 1L;

        private final JCheckBox checkbox;
        private final JLabel label;

        /**
         * Creates a JPanel instance with the specified label and image.
         *
         * @param labelText The text to be displayed by the checkbox label.
         * @param image     The image to be dispayed by the label.
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

    /**
     * Extends the CVTFilterRefresher abstract class to add the calls to update
     * the ui controls with the data found. Note that updateFilterPanel is run
     * in the EDT.
     */
    final class FilterPanelRefresher extends CVTFilterRefresher {

        private final boolean selectNewOption;
        private final boolean refreshAfterUpdate;

        FilterPanelRefresher(boolean selectNewOptions, boolean refreshAfterUpdate) {
            this.selectNewOption = selectNewOptions;
            this.refreshAfterUpdate = refreshAfterUpdate;
        }

        @Override
        void updateFilterPanel(CVTFilterRefresher.FilterPanelData data) {
            updateDateTimePicker(data.getStartTime(), data.getEndTime());
            updateDeviceFilterPanel(data.getDataSourceMap(), selectNewOption);
            updateAccountTypeFilter(data.getAccountTypesInUse(), selectNewOption);

            FiltersPanel.this.repaint();

            if (refreshAfterUpdate) {
                applyFilters();
            }

            if (!isEnabled()) {
                setEnabled(true);
            }
            
            needsRefreshLabel.setText(org.openide.util.NbBundle.getMessage(FiltersPanel.class, "FiltersPanel.needsRefreshLabel.text")); // NOI18N

            validateFilters();

            repaint();
        }
    }

    /**
     * Sorts a list of JCheckBoxes in alphabetical order of the text field
     * value.
     */
    class DeviceCheckBoxComparator implements Comparator<JCheckBox> {

        @Override
        public int compare(JCheckBox e1, JCheckBox e2) {
            return e1.getText().toLowerCase().compareTo(e2.getText().toLowerCase());
        }
    }
}
