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

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.CURRENT_CASE;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.geolocation.GeoFilterPanel.GeoFilter;
import org.sleuthkit.autopsy.geolocation.datamodel.GeoLocationDataException;
import org.sleuthkit.autopsy.geolocation.datamodel.Waypoint;
import org.sleuthkit.autopsy.geolocation.datamodel.WaypointBuilder;
import org.sleuthkit.autopsy.geolocation.datamodel.WaypointBuilder.WaypointFilterQueryCallBack;
import org.sleuthkit.autopsy.ingest.IngestManager;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.DATA_ADDED;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
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
    private final GeoFilterPanel geoFilterPanel;

    final RefreshPanel refreshPanel = new RefreshPanel();

    @Messages({
        "GLTopComponent_name=Geolocation",
        "GLTopComponent_initilzation_error=An error occurred during waypoint initilization.  Geolocation data maybe incomplete."
    })

    /**
     * Constructs new GeoLocationTopComponent
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
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
                        || eventData.getBlackboardArtifactType().getTypeID() == BlackboardArtifact.ARTIFACT_TYPE.TSK_GPS_BOOKMARK.getTypeID())) {

                    showRefreshPanel(true);
                }
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
                mapPanel.clearWaypoints();
                updateWaypoints();
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
    }

    @Override
    public void addNotify() {
        super.addNotify();
        IngestManager.getInstance().addIngestModuleEventListener(INGEST_MODULE_EVENTS_OF_INTEREST, ingestListener);
        Case.addEventTypeSubscriber(EnumSet.of(CURRENT_CASE), evt -> {
            mapPanel.clearWaypoints();
            if (evt.getNewValue() != null) {
                updateWaypoints();
            }
        });
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        IngestManager.getInstance().removeIngestModuleEventListener(ingestListener);
    }

    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);
    }
    
    @Override
    public void open() {
        super.open();
        geoFilterPanel.updateDataSourceList();
        updateWaypoints();
    }

    /**
     * Set the state of the refresh panel at the top of the mapPanel.
     *
     * @param show Whether to show or hide the panel.
     */
    private void showRefreshPanel(boolean show) {
        if (show) {
            mapPanel.add(refreshPanel, BorderLayout.NORTH);
        } else {
            mapPanel.remove(refreshPanel);
        }
        mapPanel.revalidate();
    }

    /**
     * Filters the list of waypoints based on the user selections in the filter
     * pane.
     */
    @Messages({
        "GeoTopComponent_no_waypoints_returned_mgs=Applied filter failed to find waypoints that matched criteria.\nRevise filter options and try again.",
        "GeoTopComponent_no_waypoints_returned_Title=No Waypoints Found",
        "GeoTopComponent_filter_exception_msg=Exception occured during waypoint filtering.",
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
                                        Bundle.GeoTopComponent_filer_data_invalid_msg(),
                                        Bundle.GeoTopComponent_filer_data_invalid_Title(),
                                        JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                Case currentCase = Case.getCurrentCase();
                try {
                    WaypointBuilder.getAllWaypoints(currentCase.getSleuthkitCase(), filters.getDataSources(), filters.showAllWaypoints(), filters.getMostRecentNumDays(), filters.showWaypointsWithoutTimeStamp(), new WaypointFilterQueryCallBack() {
                        @Override
                        public void process(List<Waypoint> waypoints) {
                            // If the list is empty, tell the user and do not change 
                            // the visible waypoints.
                            if (waypoints == null || waypoints.isEmpty()) {
                                JOptionPane.showMessageDialog(GeolocationTopComponent.this, 
                                        Bundle.GeoTopComponent_no_waypoints_returned_Title(),
                                        Bundle.GeoTopComponent_no_waypoints_returned_mgs(),
                                        JOptionPane.INFORMATION_MESSAGE);
                                        
                                return;
                            }
                            mapPanel.setWaypoints(MapWaypoint.getWaypoints(waypoints));
                        }
                    });
                } catch (GeoLocationDataException ex) {
                    logger.log(Level.SEVERE, "Failed to filter waypoints.", ex);
                    JOptionPane.showMessageDialog(GeolocationTopComponent.this, 
                                        Bundle.GeoTopComponent_filter_exception_Title(),
                                        Bundle.GeoTopComponent_filter_exception_msg(),
                                        JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        mapPanel = new org.sleuthkit.autopsy.geolocation.MapPanel();
        filterPane = new org.sleuthkit.autopsy.geolocation.HidingPane();

        setLayout(new java.awt.BorderLayout());

        mapPanel.add(filterPane, java.awt.BorderLayout.LINE_START);

        add(mapPanel, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.sleuthkit.autopsy.geolocation.HidingPane filterPane;
    private org.sleuthkit.autopsy.geolocation.MapPanel mapPanel;
    // End of variables declaration//GEN-END:variables
}
