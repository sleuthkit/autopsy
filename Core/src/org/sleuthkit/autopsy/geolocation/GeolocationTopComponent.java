/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019-2020 Basis Technology Corp.
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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.CURRENT_CASE;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;
import org.sleuthkit.autopsy.ingest.IngestManager;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.DATA_ADDED;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.autopsy.report.ReportProgressPanel;
import org.sleuthkit.autopsy.report.modules.kml.KMLReport;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Top component for the Geolocation Tool.
 *
 */
@TopComponent.Description(preferredID = "GeolocationTopComponent", persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "geolocation", openAtStartup = false)
@RetainLocation("geolocation")
@SuppressWarnings("PMD.SingularField")
public final class GeolocationTopComponent extends TopComponent {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(GeolocationTopComponent.class.getName());

    private static final Set<IngestManager.IngestModuleEvent> INGEST_MODULE_EVENTS_OF_INTEREST = EnumSet.of(DATA_ADDED);

    private final PropertyChangeListener ingestListener;
    private final PropertyChangeListener caseEventListener;
    private final GeoFilterPanel geoFilterPanel;

    final RefreshPanel refreshPanel = new RefreshPanel();

    private static final String REPORT_PATH_FMT_STR = "%s" + File.separator + "%s %s %s" + File.separator;

    // This is the hardcoded report name from KMLReport.java
    private static final String REPORT_KML = "ReportKML.kml";

    private boolean mapInitalized = false;

    @Messages({
        "GLTopComponent_name=Geolocation",
        "GLTopComponent_initilzation_error=An error occurred during waypoint initilization. Geolocation data maybe incomplete.",
        "GLTopComponent_No_dataSource_message=There are no data sources with Geolocation artifacts found.",
        "GLTopComponent_No_dataSource_Title=No Geolocation artifacts found"
    })

    /**
     * Constructs new GeoLocationTopComponent
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    @SuppressWarnings("deprecation")
    public GeolocationTopComponent() {
        initComponents();

        setName(Bundle.GLTopComponent_name());

        this.ingestListener = pce -> {
            String eventType = pce.getPropertyName();
            if (eventType.equals(DATA_ADDED.toString())) {
                // Indicate that a refresh may be needed for GPS data.
                ModuleDataEvent eventData = (ModuleDataEvent) pce.getOldValue();
                if (null != eventData
                        && (eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACKPOINT.getTypeID()
                        || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_SEARCH.getTypeID()
                        || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_LAST_KNOWN_LOCATION.getTypeID()
                        || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_ROUTE.getTypeID()
                        || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_METADATA_EXIF.getTypeID()
                        || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK.getTypeID()
                        || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_TRACK.getTypeID()
                        || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_AREA.getTypeID())) {

                    showRefreshPanel(true);
                }
            }
        };

        this.caseEventListener = pce -> {
            mapPanel.clearWaypoints();
            if (pce.getNewValue() != null) {
                updateWaypoints();
            }
        };

        refreshPanel.addCloseActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showRefreshPanel(false);
            }
        });

        refreshPanel.addRefreshActionListner(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                geoFilterPanel.updateDataSourceList();
                mapPanel.clearWaypoints();
                showRefreshPanel(false);
            }
        });

        geoFilterPanel = new GeoFilterPanel();
        filterPane.setPanel(geoFilterPanel);
        geoFilterPanel.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateWaypoints();
            }
        });

        geoFilterPanel.addPropertyChangeListener(GeoFilterPanel.INITPROPERTY, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                if (geoFilterPanel.hasDataSources()) {
                    updateWaypoints();
                } else {
                    geoFilterPanel.setEnabled(false);
                    setWaypointLoading(false);
                    JOptionPane.showMessageDialog(GeolocationTopComponent.this,
                            Bundle.GLTopComponent_No_dataSource_message(),
                            Bundle.GLTopComponent_No_dataSource_Title(),
                            JOptionPane.ERROR_MESSAGE);
                }
            }

        });

        mapPanel.addPropertyChangeListener(MapPanel.CURRENT_MOUSE_GEOPOSITION, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String label = "";
                Object newValue = evt.getNewValue();
                if (newValue != null) {
                    label = newValue.toString();
                }

                coordLabel.setText(label);
            }

        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, ingestListener);
        Case.addEventTypeSubscriber(EnumSet.of(CURRENT_CASE), caseEventListener);
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        IngestManager.getInstance().removeIngestModuleEventListener(ingestListener);
        Case.removeEventTypeSubscriber(EnumSet.of(CURRENT_CASE), caseEventListener);
    }

    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);

    }

    /**
     * Sets the filter state that will be set when the panel is opened. If the
     * panel is already open, this has no effect.
     *
     * @param filter The filter to set in the GeoFilterPanel.
     */
    public void setFilterState(GeoFilter filter) throws GeoLocationUIException {
        if (filter == null) {
            throw new GeoLocationUIException("Filter provided cannot be null.");
        }

        if (this.isOpened()) {
            geoFilterPanel.setupFilter(filter);
            updateWaypoints();
        } else {
            geoFilterPanel.setInitialFilterState(filter);
        }
    }

    @Messages({
        "GeolocationTC_connection_failure_message=Failed to connect to map title source.\nPlease review map source in Options dialog.",
        "GeolocationTC_connection_failure_message_title=Connection Failure"
    })
    @Override
    public void open() {
        super.open();

        // Let's make sure we only do this on the first open
        if (!mapInitalized) {
            try {
                mapPanel.initMap();
                mapInitalized = true;
            } catch (GeoLocationDataException ex) {
                JOptionPane.showMessageDialog(this,
                        Bundle.GeolocationTC_connection_failure_message(),
                        Bundle.GeolocationTC_connection_failure_message_title(),
                        JOptionPane.ERROR_MESSAGE);
                MessageNotifyUtil.Notify.error(
                        Bundle.GeolocationTC_connection_failure_message_title(),
                        Bundle.GeolocationTC_connection_failure_message());
                logger.log(Level.SEVERE, ex.getMessage(), ex);
                return; // Doen't set the waypoints.
            }
        }

        mapPanel.clearWaypoints();
        geoFilterPanel.clearDataSourceList();
        geoFilterPanel.updateDataSourceList();
        mapPanel.setWaypoints(new LinkedHashSet<>());

    }

    /**
     * Set the state of the refresh panel at the top of the mapPanel.
     *
     * @param show Whether to show or hide the panel.
     */
    private void showRefreshPanel(boolean show) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                boolean isShowing = false;
                Component[] comps = mapPanel.getComponents();
                for (Component comp : comps) {
                    if (comp.equals(refreshPanel)) {
                        isShowing = true;
                        break;
                    }
                }
                if (show && !isShowing) {
                    mapPanel.add(refreshPanel, BorderLayout.NORTH);
                    mapPanel.revalidate();
                } else if (!show && isShowing) {
                    mapPanel.remove(refreshPanel);
                    mapPanel.revalidate();
                }
            }
        });

    }

    /**
     * Filters the list of waypoints based on the user selections in the filter
     * pane.
     */
    @Messages({
        "GeoTopComponent_no_waypoints_returned_mgs=Applied filter failed to find waypoints that matched criteria.\nRevise filter options and try again.",
        "GeoTopComponent_no_waypoints_returned_Title=No Waypoints Found",
        "GeoTopComponent_filter_exception_msg=Exception occurred during waypoint filtering.",
        "GeoTopComponent_filter_exception_Title=Filter Failure",
        "GeoTopComponent_filer_data_invalid_msg=Unable to run waypoint filter.\nPlease select one or more data sources.",
        "GeoTopComponent_filer_data_invalid_Title=Filter Failure"
    })
    private void updateWaypoints() {
        GeoFilter filters;

        // Show a warning message if the user has not selected a data source
        try {
            filters = geoFilterPanel.getFilterState();
        } catch (GeoLocationUIException ex) {
            JOptionPane.showMessageDialog(this,
                    ex.getMessage(),
                    Bundle.GeoTopComponent_filer_data_invalid_Title(),
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        setWaypointLoading(true);
        geoFilterPanel.setEnabled(false);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    (new WaypointFetcher(filters)).getWaypoints();
                } catch (GeoLocationDataException ex) {
                    logger.log(Level.SEVERE, "Failed to filter waypoints.", ex);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            JOptionPane.showMessageDialog(GeolocationTopComponent.this,
                                    Bundle.GeoTopComponent_filter_exception_Title(),
                                    Bundle.GeoTopComponent_filter_exception_msg(),
                                    JOptionPane.ERROR_MESSAGE);

                            setWaypointLoading(false);
                        }
                    });

                }
            }

        });
        thread.start();
    }

    /**
     * Add the filtered set of waypoints to the map and set the various window
     * components to their proper state.
     *
     * @param waypointList
     */
    void addWaypointsToMap(Set<MapWaypoint> waypointList, List<Set<MapWaypoint>> tracks, List<Set<MapWaypoint>> areas) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                // If the list is empty, tell the user 
                if (waypointList == null || waypointList.isEmpty()) {
                    mapPanel.clearWaypoints();
                    JOptionPane.showMessageDialog(GeolocationTopComponent.this,
                            Bundle.GeoTopComponent_no_waypoints_returned_Title(),
                            Bundle.GeoTopComponent_no_waypoints_returned_mgs(),
                            JOptionPane.INFORMATION_MESSAGE);
                    setWaypointLoading(false);
                    geoFilterPanel.setEnabled(true);
                    return;
                }
                mapPanel.clearWaypoints();
                mapPanel.setWaypoints(waypointList);
                mapPanel.setTracks(tracks);
                mapPanel.setAreas(areas);
                mapPanel.initializePainter();
                setWaypointLoading(false);
                geoFilterPanel.setEnabled(true);
            }
        });
    }

    /**
     * Show or hide the waypoint loading progress bar.
     *
     * @param loading
     */
    void setWaypointLoading(boolean loading) {
        progressBar.setEnabled(true);
        progressBar.setVisible(loading);
        progressBar.setString("Loading Waypoints");
    }

    /**
     * Create the directory path for the KML report.
     *
     * This is a modified version of the similar private function from
     * KMLReport.
     *
     * @return Path for the report
     *
     * @throws IOException
     */
    private static String createReportDirectory() throws IOException {
        Case currentCase = Case.getCurrentCase();

        // Create the root reports directory path of the form: <CASE DIRECTORY>/Reports/<Case fileName> <Timestamp>/
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss", Locale.US);
        Date date = new Date();
        String dateNoTime = dateFormat.format(date);
        String reportPath = String.format(REPORT_PATH_FMT_STR, currentCase.getReportDirectory(), currentCase.getDisplayName(), "Google Earth KML", dateNoTime);
        // Create the root reports directory.
        try {
            FileUtil.createFolder(new File(reportPath));
        } catch (IOException ex) {
            throw new IOException("Failed to make report folder, unable to generate reports.", ex);
        }
        return reportPath;
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

        filterPane = new org.sleuthkit.autopsy.geolocation.HidingPane();
        statusBar = new javax.swing.JPanel();
        reportButton = new javax.swing.JButton();
        progressBar = new javax.swing.JProgressBar();
        coordLabel = new javax.swing.JLabel();
        mapPanel = new org.sleuthkit.autopsy.geolocation.MapPanel();

        setLayout(new java.awt.BorderLayout());
        add(filterPane, java.awt.BorderLayout.WEST);

        statusBar.setLayout(new java.awt.GridBagLayout());

        org.openide.awt.Mnemonics.setLocalizedText(reportButton, org.openide.util.NbBundle.getMessage(GeolocationTopComponent.class, "GeolocationTopComponent.reportButton.text")); // NOI18N
        reportButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reportButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 2;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        statusBar.add(reportButton, gridBagConstraints);

        progressBar.setIndeterminate(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        statusBar.add(progressBar, gridBagConstraints);

        org.openide.awt.Mnemonics.setLocalizedText(coordLabel, org.openide.util.NbBundle.getMessage(GeolocationTopComponent.class, "GeolocationTopComponent.coordLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 0;
        gridBagConstraints.gridy = 0;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
        statusBar.add(coordLabel, gridBagConstraints);

        add(statusBar, java.awt.BorderLayout.SOUTH);
        add(mapPanel, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    @Messages({
        "GeolocationTC_empty_waypoint_message=Unable to generate KML report due to a lack of waypoints.\nPlease make sure there are waypoints visible before generating the KML report",
        "GeolocationTC_KML_report_title=KML Report",
        "GeolocationTC_report_progress_title=KML Report Progress"
    })
    private void reportButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reportButtonActionPerformed
        List<MapWaypoint> visiblePoints = mapPanel.getVisibleWaypoints();
        if (visiblePoints.isEmpty()) {
            JOptionPane.showConfirmDialog(this, Bundle.GeolocationTC_empty_waypoint_message(), Bundle.GeolocationTC_KML_report_title(), JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        try {
            ReportProgressPanel progressPanel = new ReportProgressPanel();
            String reportBaseDir = createReportDirectory();

            progressPanel.setLabels(REPORT_KML, reportBaseDir);

            SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    KMLReport.getDefault().generateReport(reportBaseDir, progressPanel, MapWaypoint.getDataModelWaypoints(visiblePoints));
                    return null;
                }
            };
            worker.execute();
            JOptionPane.showConfirmDialog(this, progressPanel, Bundle.GeolocationTC_report_progress_title(), JOptionPane.CLOSED_OPTION, JOptionPane.PLAIN_MESSAGE);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Unable to create KML report", ex);
        }
    }//GEN-LAST:event_reportButtonActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel coordLabel;
    private org.sleuthkit.autopsy.geolocation.HidingPane filterPane;
    private org.sleuthkit.autopsy.geolocation.MapPanel mapPanel;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JButton reportButton;
    private javax.swing.JPanel statusBar;
    // End of variables declaration//GEN-END:variables

    /**
     * Extends AbstractWaypointFetcher to handle the returning of the filters
     * set of MapWaypoints.
     */
    @Messages({
        "GeolocationTopComponent.WaypointFetcher.onErrorTitle=Error gathering GPS Track Data",
        "GeolocationTopComponent.WaypointFetcher.onErrorDescription=There was an error gathering some GPS Track Data.  Some results have been excluded."
    })
    final private class WaypointFetcher extends AbstractWaypointFetcher {

        WaypointFetcher(GeoFilter filters) {
            super(filters);
        }

        @Override
        protected void handleFilteredWaypointSet(Set<MapWaypoint> mapWaypoints, List<Set<MapWaypoint>> tracks,
                List<Set<MapWaypoint>> areas, boolean wasEntirelySuccessful) {
            addWaypointsToMap(mapWaypoints, tracks, areas);
            
            // if there is an error, present to the user.
            if (!wasEntirelySuccessful) {
                JOptionPane.showMessageDialog(GeolocationTopComponent.this,
                        Bundle.GeolocationTopComponent_WaypointFetcher_onErrorDescription(),
                        Bundle.GeolocationTopComponent_WaypointFetcher_onErrorTitle(),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
