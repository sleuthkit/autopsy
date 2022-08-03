/*
 * Autopsy Forensic Browser
 *
 * Copyright 2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers.osaccount;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import static java.util.Locale.US;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.contentviewers.layout.ContentViewerDefaults;
import org.sleuthkit.autopsy.contentviewers.osaccount.Section.SectionData;
import org.sleuthkit.autopsy.contentviewers.osaccount.Section.RowData;
import org.sleuthkit.autopsy.coreutils.TimeZoneUtils;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.Host;
import org.sleuthkit.datamodel.OsAccount;
import org.sleuthkit.datamodel.OsAccount.OsAccountAttribute;
import org.sleuthkit.datamodel.OsAccountInstance;
import org.sleuthkit.datamodel.OsAccountManager;
import org.sleuthkit.datamodel.OsAccountRealm;
import org.sleuthkit.datamodel.SleuthkitCase;

/**
 * Panel for displaying the properties of an OsAccount.
 */
public class OsAccountDataPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    final private static Logger logger = Logger.getLogger(OsAccountDataPanel.class.getName());

    private static final int KEY_COLUMN = 0;
    private static final int VALUE_COLUMN = 1;

    private final static Insets FIRST_HEADER_INSETS = new Insets(0, 0, 0, 0);
    private final static Insets HEADER_INSETS = new Insets(ContentViewerDefaults.getSectionSpacing(), 0, ContentViewerDefaults.getLineSpacing(), 0);
    private final static Insets VALUE_COLUMN_INSETS = new Insets(0, ContentViewerDefaults.getColumnSpacing(), ContentViewerDefaults.getLineSpacing(), 0);
    private final static Insets KEY_COLUMN_INSETS = new Insets(0, ContentViewerDefaults.getSectionIndent(), ContentViewerDefaults.getLineSpacing(), 0);
    private final static Insets SUBHEADER_COLUMN_INSETS = new Insets(5, ContentViewerDefaults.getSectionIndent(), ContentViewerDefaults.getLineSpacing(), 0);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd yyyy", US);

    private PanelDataFetcher dataFetcher = null;

    // Panel constructor.
    OsAccountDataPanel() {
        initialize();
    }

    /**
     * Initializes the panel layout.
     */
    private void initialize() {
        this.setLayout(new GridBagLayout());
        this.setBorder(new EmptyBorder(ContentViewerDefaults.getPanelInsets()));
    }

    /**
     * Set the OsAccount to display in this panel.
     *
     * @param account OsAccount to display, if null is passed the panel will
     *                appear blank.
     */
    void setOsAccountId(Long osAccountId) {
        removeAll();
        revalidate();

        if (osAccountId != null) {
            setLayout(new BorderLayout());
            add(new JLabel("Loading OsAccount Data..."), BorderLayout.NORTH);

            if (dataFetcher != null && !dataFetcher.isDone()) {
                dataFetcher.cancel(true);
            }

            dataFetcher = new PanelDataFetcher(osAccountId);
            dataFetcher.execute();
        }
    }

    void setOsAccount(OsAccount account) {
        removeAll();
        revalidate();

        if (account != null) {
            setLayout(new BorderLayout());
            add(new JLabel("Loading OsAccount Data..."), BorderLayout.NORTH);

            if (dataFetcher != null && !dataFetcher.isDone()) {
                dataFetcher.cancel(true);
            }

            dataFetcher = new PanelDataFetcher(account);
            dataFetcher.execute();
        }
    }

    /**
     * Give all of the data to display, create the swing components and add to
     * the panel.
     *
     * @param panelData Data to be displayed.
     */
    private void addDataComponents(List<Section> panelData) {
        int rowCnt = 0;

        for (Section section : panelData) {
            addTitle(section.getTitle(), rowCnt++);
            for (SectionData data : section) {
                String subtitle = data.getTitle();
                if (subtitle != null) {
                    addSubTitle(data.getTitle(), rowCnt++);
                }

                for (RowData<String, String> rowData : data) {
                    String key = rowData.getKey();
                    String value = rowData.getValue();

                    if (value != null) {
                        addPropertyName(key, rowCnt);
                        addPropertyValue(value, rowCnt++);
                    } else {
                        addLabel(key, rowCnt++);
                    }
                }

            }
        }

        // Generate the constraints for a Vertical Glue to fill the space, if
        // any at the bottom of the panel.
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = rowCnt;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;
        constraints.weighty = 1;
        add(Box.createVerticalGlue(), constraints);
    }

    @Messages({
        "OsAccountDataPanel_basic_title=Basic Properties",
        "OsAccountDataPanel_basic_login=Login",
        "OsAccountDataPanel_basic_fullname=Full Name",
        "OsAccountDataPanel_basic_address=Address",
        "OsAccountDataPanel_basic_admin=Administrator",
        "OsAccountDataPanel_basic_type=Type",
        "OsAccountDataPanel_basic_creationDate=Creation Date",
        "OsAccountDataPanel_basic_objId=Object ID"})

    /**
     * Returns the data for the Basic Properties section of the panel.
     *
     * @param account Selected account
     *
     * @return The basic properties data for the given account.
     */
    private Section buildBasicProperties(OsAccount account) {
        Section section = new Section(Bundle.OsAccountDataPanel_basic_title());

        SectionData data = new SectionData();

        Optional<String> optional = account.getLoginName();
        data.addData(Bundle.OsAccountDataPanel_basic_login(),
                optional.isPresent() ? optional.get() : "");

        optional = account.getFullName();
        data.addData(Bundle.OsAccountDataPanel_basic_fullname(),
                optional.isPresent() ? optional.get() : "");

        data.addData(Bundle.OsAccountDataPanel_basic_address(),
                account.getName() == null || account.getName().isEmpty() ? "" : account.getName());

        data.addData(Bundle.OsAccountDataPanel_basic_type(),
                account.getOsAccountType().isPresent() ? account.getOsAccountType().get().getName() : "");

        Optional<Long> crTime = account.getCreationTime();
        data.addData(Bundle.OsAccountDataPanel_basic_creationDate(), crTime.isPresent() ? TimeZoneUtils.getFormattedTime(crTime.get()) : "");
        
        data.addData(Bundle.OsAccountDataPanel_basic_objId(), Long.toString(account.getId()));

        section.addSectionData(data);
        return section;
    }

    @Messages({
        "OsAccountDataPanel_realm_title=Realm Properties",
        "OsAccountDataPanel_realm_name=Name",
        "OsAccountDataPanel_realm_address=Address",
        "OsAccountDataPanel_realm_confidence=Confidence",
        "OsAccountDataPanel_realm_unknown=Unknown",
        "OsAccountDataPanel_realm_scope=Scope",})

    /**
     * Builds the Realm Properties.
     *
     * @param realm A valid OsAccountRealm.
     *
     * @return Data to be displayed for the given realm.
     */
    private Section buildRealmProperties(OsAccountRealm realm) {
        Section section = new Section(Bundle.OsAccountDataPanel_realm_title());
        SectionData data = new SectionData();

        String realmName = realm.getRealmNames().isEmpty() ? Bundle.OsAccountDataPanel_realm_unknown() : realm.getRealmNames().get(0);
        data.addData(Bundle.OsAccountDataPanel_realm_name(), realmName);

        Optional<String> optional = realm.getRealmAddr();
        data.addData(Bundle.OsAccountDataPanel_realm_address(),
                optional.isPresent() ? optional.get() : "");

        data.addData(Bundle.OsAccountDataPanel_realm_scope(),
                realm.getScope().getName());

        data.addData(Bundle.OsAccountDataPanel_realm_confidence(),
                realm.getScopeConfidence().getName());

        section.addSectionData(data);
        return section;
    }

    @Messages({
        "# {0} - hostName",
        "OsAccountDataPanel_host_section_title={0} Details",
        "OsAccountDataPanel_host_count_title=Login Count",
        "OsAccountDataPanel_data_accessed_title=Last Login",
        "OsAccountDataPanel_administrator_title=Administrator"
    })
    private Section buildHostData(Host host, Map<DataSource, List<OsAccountAttribute>> attributeDataSourceMap) {
        String sectionTitle = "Global Host Details";

        if (host != null) {
            sectionTitle = Bundle.OsAccountDataPanel_host_section_title(host.getName());
        }

        Section section = new Section(sectionTitle);

        for (DataSource dataSource : attributeDataSourceMap.keySet()) {
            List<OsAccountAttribute> attributeList = attributeDataSourceMap.get(dataSource);
            SectionData data = new SectionData((attributeDataSourceMap.size() > 1 && dataSource != null) ? dataSource.getName() : null);
            if (attributeList != null) {
                for (OsAccountAttribute attribute : attributeList) {
                    String displayName = attribute.getAttributeType().getDisplayName();
                    String value = attribute.getDisplayString();

                    if (attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_COUNT.getTypeID()) {
                        displayName = Bundle.OsAccountDataPanel_host_count_title();
                    } else if (attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_IS_ADMIN.getTypeID()) {
                        displayName = Bundle.OsAccountDataPanel_administrator_title();
                        if (attribute.getValueInt() == 0) {
                            value = "False";
                        } else {
                            value = "True";
                        }
                    } else if (attribute.getAttributeType().getTypeID() == BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DATETIME_ACCESSED.getTypeID()) {
                        displayName = Bundle.OsAccountDataPanel_data_accessed_title();
                    }

                    data.addData(displayName, value);
                }
            } else {
                data.addData("No details available", null);
            }

            section.addSectionData(data);
        }

        return section;
    }

    /**
     * Add a section title to the panel with the given title and location.
     *
     * @param title Section title.
     * @param row   Row in the layout the title will appear.
     */
    private void addTitle(String title, int row) {
        JLabel label = new JLabel(title);
        // Make the title bold.
        label.setFont(ContentViewerDefaults.getHeaderFont());
        add(label, getTitleContraints(row));
    }

    private void addSubTitle(String title, int row) {
        JLabel label = new JLabel(title);
        // Make the title bold.
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        add(label, getSubtitleContraints(row));
    }

    /**
     * Add the property name at the given row in the layout.
     *
     * @param key The property name.
     * @param row The row in the layout.
     */
    private void addPropertyName(String key, int row) {
        JLabel label = new JLabel(key + ":");
        add(label, getPropertyNameContraints(row));
    }

    /**
     * Adds a simple label to the given row.
     *
     * @param text The text to show.
     * @param row  The row in the layout.
     */
    private void addLabel(String text, int row) {
        JLabel label = new JLabel(text);
        add(label, getPropertyNameContraints(row));
        addPropertyValue("", row);
    }

    /**
     * Add the property value at the given row in the layout.
     *
     * @param value The value to display.
     * @param row   The row in the layout.
     */
    private void addPropertyValue(String value, int row) {
        JLabel label = new JLabel(value);
        add(label, getPropertyValueContraints(row));
    }

    /**
     * Generate the constraints for a title at the given row.
     *
     * @param row The row to generate the title constraints for.
     *
     * @return Constraints for a title row.
     */
    private GridBagConstraints getTitleContraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();

        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2; // The title goes across the other columns
        constraints.gridheight = 1;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = (row == 0)
                ? FIRST_HEADER_INSETS
                : HEADER_INSETS;

        return constraints;
    }

    private GridBagConstraints getSubtitleContraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();

        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2; // The title goes across the other columns
        constraints.gridheight = 1;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.insets = SUBHEADER_COLUMN_INSETS;

        return constraints;
    }

    /**
     * Generate the constraints for a property name at the given row.
     *
     * @param row
     *
     * @return Constraints for the property name label.
     */
    private GridBagConstraints getPropertyNameContraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();

        constraints.gridx = KEY_COLUMN;
        constraints.gridy = row;
        constraints.gridwidth = 1; // The title goes across the other columns
        constraints.gridheight = 1;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = KEY_COLUMN_INSETS;

        return constraints;
    }

    /**
     * Generate the constraints for a property value at the given row.
     *
     * @param row Row in the layout.
     *
     * @return The constraints for the property label.
     */
    private GridBagConstraints getPropertyValueContraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();

        constraints.gridx = VALUE_COLUMN;
        constraints.gridy = row;
        constraints.gridwidth = 1; // The title goes across the other columns
        constraints.gridheight = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = VALUE_COLUMN_INSETS;
        constraints.weightx = 1;

        return constraints;
    }

    /**
     * A SwingWorker to gather the data for the content panel.
     */
    private class PanelDataFetcher extends SwingWorker<WorkerResults, Void> {

        private final Long accountId;
        private OsAccount account;

        /**
         * Construct a new worker for the given account.
         *
         * @param account
         */
        PanelDataFetcher(Long accountId) {
            this.accountId = accountId;
            this.account = null;
        }

        PanelDataFetcher(OsAccount account) {
            this.account = account;
            this.accountId = null;
        }

        @Override
        protected WorkerResults doInBackground() throws Exception {
            Map<Host, Map<DataSource, List<OsAccountAttribute>>> hostMap = new HashMap<>();
            Map<Host, DataSource> instanceMap = new HashMap<>();
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
            OsAccountManager osAccountManager = skCase.getOsAccountManager();

            if (account == null) {
                account = osAccountManager.getOsAccountByObjectId(accountId);
            }

            OsAccountRealm realm = skCase.getOsAccountRealmManager().getRealmByRealmId(account.getRealmId());

            List<Host> hosts = osAccountManager.getHosts(account);
            List<OsAccountAttribute> attributeList = account.getExtendedOsAccountAttributes();

            // Organize the attributes by hostId
            Map<Long, Map<DataSource, List<OsAccountAttribute>>> idMap2 = new HashMap<>();
            if (attributeList != null) {
                for (OsAccountAttribute attribute : attributeList) {

                    Long key = attribute.getHostId().orElse(null);
                    Long sourceID = attribute.getSourceObjectId().orElse(null);
                    DataSource dataSource = null;

                    if (sourceID != null) {
                        Content sourceContent = skCase.getContentById(sourceID);
                        if (sourceContent != null) {
                            dataSource = (DataSource) sourceContent.getDataSource();
                        }
                    }

                    Map<DataSource, List<OsAccountAttribute>> atMap = idMap2.get(key);
                    if (atMap == null) {
                        atMap = new HashMap<>();
                        idMap2.put(key, atMap);
                    }

                    List<OsAccountAttribute> mapList = atMap.get(dataSource);
                    if (mapList == null) {
                        mapList = new ArrayList<>();
                        atMap.put(dataSource, mapList);
                    }
                    mapList.add(attribute);
                }
            }

            // Add attribute lists to the hostMap 
            if (hosts != null) {
                for (Host host : hosts) {
                    hostMap.put(host, idMap2.get(host.getHostId()));
                }
            }

            hostMap.put(null, idMap2.get(null));
            Map<DataSource, List<OsAccountAttribute>> atMap = idMap2.get(null);
            if (atMap != null) {
                hostMap.put(null, atMap);
            }

            // Store both the host and the dataSource so that we get
            // all of the calls to the db done in the thread.
            for (OsAccountInstance instance : account.getOsAccountInstances()) {
                instanceMap.put(instance.getDataSource().getHost(), instance.getDataSource());
            }

            return new WorkerResults(hostMap, instanceMap, realm);
        }

        @Override
        protected void done() {
            WorkerResults results = null;

            try {
                if (this.isCancelled()) {
                    return;
                } else {
                    results = get();
                }
            } catch (ExecutionException | InterruptedException ex) {
                logger.log(Level.SEVERE, String.format("Failed to retrieve data for OsAccount (%d)", account.getId()), ex);
            }

            if (results != null) {
                removeAll();
                setLayout(new GridBagLayout());

                List<Section> data = new ArrayList<>();
                data.add(buildBasicProperties(account));
                Map<Host, Map<DataSource, List<OsAccountAttribute>>> hostDataMap = results.getAttributeMap();
                if (hostDataMap != null && !hostDataMap.isEmpty()) {
                    hostDataMap.entrySet().stream().forEach(pair -> {
                        if (pair.getKey() != null && pair.getValue() != null) {
                            data.add(buildHostData(pair.getKey(), pair.getValue()));
                        }
                    });
                }

                OsAccountRealm realm = results.getRealm();
                if (realm != null) {
                    data.add(buildRealmProperties(realm));
                }

//                Removing the instance section for now.  Leaving code here for 
//                future use.                 
//                Map<Host, DataSource> instanceMap = results.getDataSourceMap();
//                if (!instanceMap.isEmpty()) {
//                    SectionData instanceSection = new SectionData("Instances");
//                    instanceMap.forEach((K, V) -> instanceSection.addData(K.getName(), V.getName()));
//
//                    data.add(instanceSection);
//                }
                addDataComponents(data);

                revalidate();
                repaint();
            }
        }
    }

    /**
     * Helper class for PanelDataFetcher that wraps the returned data needed for
     * the panel.
     */
    private final class WorkerResults {

        private final Map<Host, Map<DataSource, List<OsAccountAttribute>>> attributeMap;
        private final Map<Host, DataSource> instanceMap;
        private final OsAccountRealm realm;

        /**
         * Construct a new WorkerResult object.
         *
         * @param attributeMap Maps the OsAccountAttributes to the host they
         *                     belong with.
         * @param instanceMap  A map of data to display OsAccount instance
         *                     information.
         */
        WorkerResults(Map<Host, Map<DataSource, List<OsAccountAttribute>>> attributeMap, Map<Host, DataSource> instanceMap, OsAccountRealm realm) {
            this.attributeMap = attributeMap;
            this.instanceMap = instanceMap;
            this.realm = realm;
        }

        /**
         * Returns a map of OsAccountAttributes that belong to a specific Host.
         * There maybe a null key in the map which represents properties that
         * are not host specific.
         *
         * @return OsAccountAttribute map.
         */
        Map<Host, Map<DataSource, List<OsAccountAttribute>>> getAttributeMap() {
            return attributeMap;
        }

        /**
         * A map of the instance data for the OsAccount.
         *
         * @return
         */
        Map<Host, DataSource> getDataSourceMap() {
            return instanceMap;
        }

        OsAccountRealm getRealm() {
            return realm;
        }
    }
}
