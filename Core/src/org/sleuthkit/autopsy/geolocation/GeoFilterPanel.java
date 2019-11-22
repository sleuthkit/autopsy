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

import java.awt.GridBagConstraints;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javax.swing.ImageIcon;
import javax.swing.SpinnerNumberModel;
import org.openide.util.NbBundle.Messages;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 * Panel to display the filter options for geolocation waypoints.
 */
class GeoFilterPanel extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(GeoFilterPanel.class.getName());

    private final SpinnerNumberModel numberModel;
    private final CheckBoxListPanel<DataSource> checkboxPanel;

    /**
     * Creates new GeoFilterPanel
     */
    @Messages({
        "GeoFilterPanel_DataSource_List_Title=Data Sources"
    })
    GeoFilterPanel() {
        // numberModel is used in initComponents
        numberModel = new SpinnerNumberModel(10, 1, Integer.MAX_VALUE, 1);

        initComponents();

        // The gui builder cannot handle using CheckBoxListPanel due to its
        // use of generics so we will initalize it here.
        checkboxPanel = new CheckBoxListPanel<>();
        checkboxPanel.setPanelTitle(Bundle.GeoFilterPanel_DataSource_List_Title());
        checkboxPanel.setPanelTitleIcon(new ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/image.png")));
        checkboxPanel.setSetAllSelected(true);

        GridBagConstraints gridBagConstraints = new GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 3;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 15);
        add(checkboxPanel, gridBagConstraints);
    }
    
    void updateDataSourceList() {
         try {
            initCheckboxList();
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to initialize the CheckboxListPane", ex); //NON-NLS
        }
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
        "GeoFilterPanel_empty_dataSource=Data Source list is empty."
    })
    GeoFilter getFilterState() throws GeoLocationUIException {
        List<DataSource> dataSources = checkboxPanel.getSelectedElements();

        if (dataSources.isEmpty()) {
            throw new GeoLocationUIException(Bundle.GeoFilterPanel_empty_dataSource());
        }
        return new GeoFilter(allButton.isSelected(), 
                showWaypointsWOTSCheckBox.isSelected(), 
                numberModel.getNumber().intValue(), 
                dataSources);
    }

    /**
     * Initialize the checkbox list panel
     *
     * @throws TskCoreException
     */
    private void initCheckboxList() throws TskCoreException {
        final SleuthkitCase sleuthkitCase = Case.getCurrentCase().getSleuthkitCase();

        checkboxPanel.clearList();
        
        for (DataSource dataSource : sleuthkitCase.getDataSources()) {
            String dsName = sleuthkitCase.getContentById(dataSource.getId()).getName();
            checkboxPanel.addElement(dsName, dataSource);
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
        javax.swing.JLabel daysLabel = new javax.swing.JLabel();
        javax.swing.JPanel buttonPanel = new javax.swing.JPanel();
        applyButton = new javax.swing.JButton();
        javax.swing.JLabel optionsLabel = new javax.swing.JLabel();

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
        gridBagConstraints.gridy = 0;
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
        gridBagConstraints.gridy = 1;
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 0);
        waypointSettings.add(mostRecentButton, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(showWaypointsWOTSCheckBox, org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.showWaypointsWOTSCheckBox.text")); // NOI18N
        showWaypointsWOTSCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.gridwidth = 3;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 30, 0, 0);
        waypointSettings.add(showWaypointsWOTSCheckBox, gridBagConstraints);

        daysSpinner.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(9, 0, 0, 0);
        waypointSettings.add(daysSpinner, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(daysLabel, org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.daysLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 3;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(9, 5, 0, 0);
        waypointSettings.add(daysLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 15, 9, 15);
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
        gridBagConstraints.insets = new java.awt.Insets(9, 15, 0, 15);
        add(buttonPanel, gridBagConstraints);

        optionsLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/images/blueGeo16.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(optionsLabel, org.openide.util.NbBundle.getMessage(GeoFilterPanel.class, "GeoFilterPanel.optionsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 1;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 0);
        add(optionsLabel, gridBagConstraints);
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
    private javax.swing.JSpinner daysSpinner;
    private javax.swing.JRadioButton mostRecentButton;
    private javax.swing.JCheckBox showWaypointsWOTSCheckBox;
    // End of variables declaration//GEN-END:variables

    /**
     * Class to store the values of the Geolocation user set filter parameters
     */
    final class GeoFilter {

        private final boolean showAll;
        private final boolean showWithoutTimeStamp;
        private final int mostRecentNumDays;
        private final List<DataSource> dataSources;

        /**
         * Construct a Geolocation filter. showAll and mostRecentNumDays are
         * exclusive filters, ie they cannot be used together.
         *
         * withoutTimeStamp is only applicable if mostRecentNumDays is true.
         *
         * When using the filters "most recent days" means to include waypoints
         * for the numbers of days after the most recent waypoint, not the
         * current date.
         *
         * @param showAll           True if all waypoints should be shown
         * @param withoutTimeStamp  True to show waypoints without timeStamps,
         *                          this filter is only applicable if
         *                          mostRecentNumDays is true
         * @param mostRecentNumDays Show Waypoint for the most recent given
         *                          number of days. This parameter is ignored if
         *                          showAll is true.
         * @param dataSources       A list of dataSources to filter waypoint
         *                          for.
         */
        GeoFilter(boolean showAll, boolean withoutTimeStamp, int mostRecentNumDays, List<DataSource> dataSources) {
            this.showAll = showAll;
            this.showWithoutTimeStamp = withoutTimeStamp;
            this.mostRecentNumDays = mostRecentNumDays;
            this.dataSources = dataSources;
        }

        /**
         * Returns whether or not to show all waypoints.
         *
         * @return True if all waypoints should be shown.
         */
        boolean showAllWaypoints() {
            return showAll;
        }

        /**
         * Returns whether or not to include waypoints with time stamps.
         *
         * This filter is only applicable if "showAll" is true.
         *
         * @return True if waypoints with time stamps should be shown.
         */
        boolean showWaypointsWithoutTimeStamp() {
            return showWithoutTimeStamp;
        }

        /**
         * Returns the number of most recent days to show waypoints for. This
         * value should be ignored if showAll is true.
         *
         * @return The number of most recent days to show waypoints for
         */
        int getMostRecentNumDays() {
            return mostRecentNumDays;
        }

        /**
         * Returns a list of data sources to filter the waypoints by, or null if
         * all datasources should be include.
         *
         * @return A list of dataSources or null if all dataSources should be
         *         included.
         */
        List<DataSource> getDataSources() {
            return Collections.unmodifiableList(dataSources);
        }
    }

}
