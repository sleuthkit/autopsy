/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

import org.sleuthkit.autopsy.guiutils.CheckBoxListPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javafx.util.Pair;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardArtifact.ARTIFACT_TYPE;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Panel to display the filter options for geolocation waypoints.
 */
class GeoFilterPanel extends javax.swing.JPanel {

    final static String INITPROPERTY = "FilterPanelInitCompleted";

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(GeoFilterPanel.class.getName());

    private final SpinnerNumberModel numberModel;
    private final CheckBoxListPanel<DataSource> dsCheckboxPanel;
    private final CheckBoxListPanel<ARTIFACT_TYPE> atCheckboxPanel;

    private final Object initialFilterLock = new Object();
    private GeoFilter initialFilter = null;

    // Make sure to update if other GPS artifacts are added
    @SuppressWarnings("deprecation")
    private static final ARTIFACT_TYPE[] GPS_ARTIFACT_TYPES = {
        ARTIFACT_TYPE.TSK_GPS_BOOKMARK,
        ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION,
        ARTIFACT_TYPE.TSK_GPS_ROUTE,
        ARTIFACT_TYPE.TSK_GPS_SEARCH,
        ARTIFACT_TYPE.TSK_GPS_TRACK,
        ARTIFACT_TYPE.TSK_GPS_TRACKPOINT,
        ARTIFACT_TYPE.TSK_METADATA_EXIF,
        ARTIFACT_TYPE.TSK_GPS_AREA
    };

    /**
     * Creates new GeoFilterPanel
     */
    @Messages({
        "GeoFilterPanel_DataSource_List_Title=Data Sources",
        "GeoFilterPanel_ArtifactType_List_Title=Types"
    })
    @SuppressWarnings("unchecked")
    GeoFilterPanel() {
        // numberModel is used in initComponents
        numberModel = new SpinnerNumberModel(10, 1, Integer.MAX_VALUE, 1);

        initComponents();

        dsCheckboxPanel = (CheckBoxListPanel<DataSource>) dsCBPanel;
        dsCheckboxPanel.setPanelTitle(Bundle.GeoFilterPanel_DataSource_List_Title());
        dsCheckboxPanel.setPanelTitleIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/image.png")));
        dsCheckboxPanel.setSetAllSelected(true);

        atCheckboxPanel = (CheckBoxListPanel<ARTIFACT_TYPE>) atCBPanel;
        atCheckboxPanel.setPanelTitle(Bundle.GeoFilterPanel_ArtifactType_List_Title());
        atCheckboxPanel.setPanelTitleIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/extracted_content.png")));
        atCheckboxPanel.setSetAllSelected(true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        applyButton.setEnabled(enabled);
        mostRecentButton.setEnabled(enabled);
        allButton.setEnabled(enabled);
        showWaypointsWOTSCheckBox.setEnabled(enabled && mostRecentButton.isSelected());
        dsCheckboxPanel.setEnabled(enabled);
        atCheckboxPanel.setEnabled(enabled);
        daysLabel.setEnabled(enabled);
        daysSpinner.setEnabled(enabled);
    }

    /**
     * Update the data source list with the current data sources
     */
    void updateDataSourceList() {
        DataSourceUpdater updater = new DataSourceUpdater();
        updater.execute();
    }

    /**
     * Clears the data source list.
     */
    void clearDataSourceList() {
        dsCheckboxPanel.clearList();
        atCheckboxPanel.clearList();
    }

    boolean hasDataSources() {
        return !dsCheckboxPanel.isEmpty();
    }

    /**
     * Adds an actionListener to listen for the filter apply action
     *
     * @param listener
     */
    void addActionListener(ActionListener listener) {
        applyButton.addActionListener(listener);
    }

    /**
     * Returns the selected filter values.
     *
     * @return A GeoFilter object with the user selected filter values
     *
     * @throws GeoLocationUIException
     */
    @Messages({
        "GeoFilterPanel_empty_dataSource=Unable to apply filter, please select one or more data sources.",
        "GeoFilterPanel_empty_artifactType=Unable to apply filter, please select one or more artifact types."
    })
    GeoFilter getFilterState() throws GeoLocationUIException {
        List<DataSource> dataSources = dsCheckboxPanel.getSelectedElements();
        if (dataSources.isEmpty()) {
            throw new GeoLocationUIException(Bundle.GeoFilterPanel_empty_dataSource());
        }

        List<ARTIFACT_TYPE> artifactTypes = atCheckboxPanel.getSelectedElements();
        if (artifactTypes.isEmpty()) {
            throw new GeoLocationUIException(Bundle.GeoFilterPanel_empty_artifactType());
        }
        return new GeoFilter(allButton.isSelected(),
                showWaypointsWOTSCheckBox.isSelected(),
                numberModel.getNumber().intValue(),
                dataSources,
                artifactTypes);
    }

    /**
     * Sets up filter state in filter panel based on filter provided. NOTE:
     * GeolocationTopComponent will overwrite these settings on open(). Also,
     * this will not immediately trigger waypoints to be reloaded.
     *
     * @param filter The new filter state.
     */
    void setupFilter(GeoFilter filter) {
        if (filter == null) {
            return;
        }

        dsCheckboxPanel.setSelectedElements(filter.getDataSources() == null ? Collections.emptyList() : filter.getDataSources());
        atCheckboxPanel.setSelectedElements(filter.getArtifactTypes() == null ? Collections.emptyList() : filter.getArtifactTypes());

        if (filter.showAllWaypoints()) {
            allButton.setSelected(true);
        } else {
            mostRecentButton.setSelected(true);
        }

        showWaypointsWOTSCheckBox.setSelected(filter.showWaypointsWithoutTimeStamp());
        numberModel.setValue(filter.getMostRecentNumDays());
    }

    /**
     * Sets the filter state that will be created when the DataSourceUpdater
     * runs as a part of updating the data source list which is called during
     * opening the GeolocationTopComponent.
     *
     * @param filter The initial filter.
     * @throws GeoLocationUIException
     */
    void setInitialFilterState(GeoFilter filter) throws GeoLocationUIException {
        synchronized (initialFilterLock) {
            if (filter == null) {
                throw new GeoLocationUIException("Unable to set filter state as no filter was provided.");
            }

            initialFilter = filter;
        }
    }

    /**
     * Based on the state of mostRecent radio button Change the state of the cnt
     * spinner and the time stamp checkbox.
     */
    private void updateWaypointOptions() {
        boolean selected = mostRecentButton.isSelected();
        showWaypointsWOTSCheckBox.setEnabled(selected);
        daysSpinner.setEnabled(selected);
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
        javax.swing.JPanel waypointSettings = new javax.swing.JPanel();
        allButton = new javax.swing.JRadioButton();
        mostRecentButton = new javax.swing.JRadioButton();
        showWaypointsWOTSCheckBox = new javax.swing.JCheckBox();
        daysSpinner = new javax.swing.JSpinner(numberModel);
        daysLabel = new javax.swing.JLabel();
        showLabel = new javax.swing.JLabel();
        javax.swing.JPanel buttonPanel = new javax.swing.JPanel();
        applyButton = new javax.swing.JButton();
        javax.swing.JLabel optionsLabel = new javax.swing.JLabel();
        dsCBPanel = new CheckBoxListPanel<DataSource>();
        atCBPanel = new CheckBoxListPanel<ARTIFACT_TYPE>();

        setMinimumSize(new java.awt.Dimension(10, 700));
        setPreferredSize(new java.awt.Dimension(300, 700));
        setLayout(new java.awt.GridBagLayout());

        waypointSettings.setBorder(javax.swing.BorderFactory.createTitledBorder(org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.waypointSettings.border.title"))); // NOI18N
        waypointSettings.setLayout(new java.awt.GridBagLayout());

        buttonGroup.add(allButton);
        allButton.setSelected(true);
        org.openide.awt.Mnemonics.setLocalizedText(allButton, org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.allButton.text")); // NOI18N
        allButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 4;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        waypointSettings.add(allButton, gridBagConstraints);

        buttonGroup.add(mostRecentButton);
        org.openide.awt.Mnemonics.setLocalizedText(mostRecentButton, org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.mostRecentButton.text")); // NOI18N
        mostRecentButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                mostRecentButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        waypointSettings.add(mostRecentButton, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(showWaypointsWOTSCheckBox, org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.showWaypointsWOTSCheckBox.text")); // NOI18N
        showWaypointsWOTSCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, -20, 0, 5);
        waypointSettings.add(showWaypointsWOTSCheckBox, gridBagConstraints);

        daysSpinner.setEnabled(false);
        daysSpinner.setMaximumSize(new java.awt.Dimension(100, 26));
        daysSpinner.setPreferredSize(new java.awt.Dimension(75, 26));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        waypointSettings.add(daysSpinner, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(daysLabel, org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.daysLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        waypointSettings.add(daysLabel, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(showLabel, org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.showLabel.text")); // NOI18N
        showLabel.setToolTipText(org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.showLabel.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        waypointSettings.add(showLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 15, 9, 25);
        add(waypointSettings, gridBagConstraints);

        buttonPanel.setLayout(new java.awt.GridBagLayout());

        applyButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/tick.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(applyButton, org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.applyButton.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
        gridBagConstraints.weightx = 1.0;
        buttonPanel.add(applyButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(9, 15, 0, 25);
        add(buttonPanel, gridBagConstraints);

        optionsLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/blueGeo16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(optionsLabel, org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.optionsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 0);
        add(optionsLabel, gridBagConstraints);

        dsCBPanel.setMinimumSize(new java.awt.Dimension(150, 250));
        dsCBPanel.setPreferredSize(new java.awt.Dimension(150, 250));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 15, 9, 25);
        add(dsCBPanel, gridBagConstraints);

        atCBPanel.setMinimumSize(new java.awt.Dimension(150, 250));
        atCBPanel.setPreferredSize(new java.awt.Dimension(150, 250));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 4;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 15, 9, 25);
        add(atCBPanel, gridBagConstraints);
        atCBPanel.getAccessibleContext().setAccessibleName(org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.atCBPanel.AccessibleContext.accessibleName")); // NOI18N
    }// </editor-fold>//GEN-END:initComponents

    private void allButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allButtonActionPerformed
        updateWaypointOptions();
    }//GEN-LAST:event_allButtonActionPerformed

    private void mostRecentButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_mostRecentButtonActionPerformed
        updateWaypointOptions();
    }//GEN-LAST:event_mostRecentButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton allButton;
    private javax.swing.JButton applyButton;
    private javax.swing.JPanel atCBPanel;
    private javax.swing.JLabel daysLabel;
    private javax.swing.JSpinner daysSpinner;
    private javax.swing.JPanel dsCBPanel;
    private javax.swing.JRadioButton mostRecentButton;
    private javax.swing.JLabel showLabel;
    private javax.swing.JCheckBox showWaypointsWOTSCheckBox;
    // End of variables declaration//GEN-END:variables

    /**
     * Container for data sources and artifact types to be given as filter
     * options
     */
    final private class Sources {

        final List<Pair<String, DataSource>> dataSources;
        final Map<ARTIFACT_TYPE, Long> artifactTypes;

        private Sources(List<Pair<String, DataSource>> dataSources,
                Map<ARTIFACT_TYPE, Long> artifactTypes) {
            this.dataSources = dataSources;
            this.artifactTypes = artifactTypes;
        }
    }

    /**
     * SwingWorker for updating the list of valid data sources.
     *
     * doInBackground creates a list of Pair objects that contain the display
     * name of the data source and the data source object.
     */
    final private class DataSourceUpdater extends SwingWorker<Sources, Void> {

        @Override
        protected Sources doInBackground() throws Exception {
            SleuthkitCase sleuthkitCase = Case.getCurrentCase().getSleuthkitCase();
            List<Pair<String, DataSource>> validSources = new ArrayList<>();
            HashMap<ARTIFACT_TYPE, Long> atCountsTotal = new HashMap<>();

            for (DataSource dataSource : sleuthkitCase.getDataSources()) {
                Map<ARTIFACT_TYPE, Long> atCounts = getGPSDataSources(sleuthkitCase, dataSource);
                if (!atCounts.isEmpty()) {
                    for (Map.Entry<ARTIFACT_TYPE, Long> entry : atCounts.entrySet()) {
                        atCountsTotal.putIfAbsent(entry.getKey(), 0L);
                        atCountsTotal.put(entry.getKey(), atCountsTotal.get(entry.getKey()) + entry.getValue());
                    }
                    String dsName = sleuthkitCase.getContentById(dataSource.getId()).getName();
                    Pair<String, DataSource> pair = new Pair<>(dsName, dataSource);
                    validSources.add(pair);
                }
            }
            return new Sources(validSources, atCountsTotal);
        }

        /**
         * Get a count of artifacts of the given type containing GPS data for
         * the given data case and source. Does not include rejected artifacts.
         *
         * @param sleuthkitCase
         * @param dataSource
         * @param artifactType
         *
         * @return The artifacts count that match the criteria
         *
         * @throws TskCoreException
         */
        private long getGPSDataCount(SleuthkitCase sleuthkitCase,
                DataSource dataSource, BlackboardArtifact.ARTIFACT_TYPE artifactType) throws TskCoreException {
            long count = 0;
            String queryStr
                    = "SELECT count(DISTINCT artIds) AS count FROM"
                    + " ("
                    + " SELECT arts.artifact_id as artIds, * FROM blackboard_artifacts as arts"
                    + " INNER JOIN blackboard_attributes as attrs"
                    + " ON attrs.artifact_id = arts.artifact_id"
                    + " WHERE arts.artifact_type_id = " + artifactType.getTypeID()
                    + " AND arts.data_source_obj_id = " + dataSource.getId()
                    + " AND arts.review_status_id != " + BlackboardArtifact.ReviewStatus.REJECTED.getID()
                    + " AND"
                    + " ("
                    + "attrs.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LATITUDE.getTypeID()
                    + " or attrs.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_LONGITUDE.getTypeID()
                    + " or attrs.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_TRACKPOINTS.getTypeID()
                    + " or attrs.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_WAYPOINTS.getTypeID()
                    + " or attrs.attribute_type_id = " + BlackboardAttribute.ATTRIBUTE_TYPE.TSK_GEO_AREAPOINTS.getTypeID()
                    + " )"
                    + " ) as innerTable";
            try (SleuthkitCase.CaseDbQuery queryResult = sleuthkitCase.executeQuery(queryStr);
                    ResultSet resultSet = queryResult.getResultSet()) {
                if (resultSet.next()) {
                    count = resultSet.getLong("count");
                }
            } catch (SQLException ex) {
                Throwable cause = ex.getCause();
                if (cause != null) {
                    logger.log(Level.SEVERE, cause.getMessage(), cause);
                } else {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }
            return count;
        }

        /**
         * Returns a Map representing the number of sources found for each
         * artifact type. If no data was found, an empty map is returned.
         *
         * @param sleuthkitCase The current sleuthkitCase
         * @param dataSource
         *
         * @return True if the data source as at least one TSK_GPS_XXXX
         *
         * @throws TskCoreException
         */
        private Map<ARTIFACT_TYPE, Long> getGPSDataSources(SleuthkitCase sleuthkitCase, DataSource dataSource) throws TskCoreException {
            HashMap<ARTIFACT_TYPE, Long> ret = new HashMap<>();
            for (BlackboardArtifact.ARTIFACT_TYPE type : GPS_ARTIFACT_TYPES) {
                long count = getGPSDataCount(sleuthkitCase, dataSource, type);
                if (count > 0) {
                    ret.put(type, count);
                }
            }
            return ret;
        }

        /**
         * Returns a new ImageIcon for the given artifact type ID representing
         * the type's waypoint color
         *
         * @param artifactTypeId The artifact type id
         *
         * @return the ImageIcon
         */
        private ImageIcon getImageIcon(int artifactTypeId) {
            Color color = MapWaypoint.getColor(artifactTypeId);
            BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

            Graphics g = img.createGraphics();
            g.setColor(color);
            g.fillRect(0, 0, 16, 16);
            g.dispose();

            return new ImageIcon(img);
        }

        @Override
        public void done() {
            Sources sources = null;
            try {
                sources = get();
            } catch (InterruptedException | ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause != null) {
                    logger.log(Level.SEVERE, cause.getMessage(), cause);
                } else {
                    logger.log(Level.SEVERE, ex.getMessage(), ex);
                }
            }

            if (sources != null) {
                for (Pair<String, DataSource> source : sources.dataSources) {
                    dsCheckboxPanel.addElement(source.getKey(), null, source.getValue());
                }
                for (Map.Entry<ARTIFACT_TYPE, Long> entry : sources.artifactTypes.entrySet()) {
                    String dispName = entry.getKey().getDisplayName() + " (" + entry.getValue() + ")";
                    Icon icon = getImageIcon(entry.getKey().getTypeID());
                    atCheckboxPanel.addElement(dispName, icon, entry.getKey());
                }
            }

            GeoFilter filter = null;
            synchronized (GeoFilterPanel.this.initialFilterLock) {
                filter = GeoFilterPanel.this.initialFilter;
                GeoFilterPanel.this.initialFilter = null;
            }

            if (filter != null) {
                setupFilter(filter);
            }

            GeoFilterPanel.this.firePropertyChange(INITPROPERTY, false, true);
        }

    }

}
