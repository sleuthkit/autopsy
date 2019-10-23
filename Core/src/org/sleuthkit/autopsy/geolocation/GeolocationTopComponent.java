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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import javax.swing.SwingWorker;
import org.jxmapviewer.viewer.Waypoint;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import static org.sleuthkit.autopsy.casemodule.Case.Events.CURRENT_CASE;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.ingest.IngestManager;
import static org.sleuthkit.autopsy.ingest.IngestManager.IngestModuleEvent.DATA_ADDED;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
import org.sleuthkit.datamodel.BlackboardArtifact;

/**
 * Top component which displays the Geolocation Tool.
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

    final RefreshPanel refreshPanel = new RefreshPanel();

    @Messages({
        "GLTopComponent_name=Geolocation",
        "GLTopComponent_initilzation_error=An error occurred during waypoint initilization.  Geolocation data maybe incomplete."
    })

    /**
     * Creates new form GeoLocationTopComponent
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public GeolocationTopComponent() {
        initComponents();
        initWaypoints();
        setName(Bundle.GLTopComponent_name());

        this.ingestListener = pce -> {
            String eventType = pce.getPropertyName();
            if (eventType.equals(DATA_ADDED.toString())) {
                // Indicate that a refresh may be needed, unless the data added is Keyword or Hashset hits
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
                initWaypoints();
                showRefreshPanel(false);
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
                initWaypoints();
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
     * Use a SwingWorker thread to find all of the artifacts that have GPS
     * coordinates.
     *
     */
    private void initWaypoints() {
        SwingWorker<List<Waypoint>, Waypoint> worker = new SwingWorker<List<Waypoint>, Waypoint>() {
            @Override
            protected List<Waypoint> doInBackground() throws Exception {
                Case currentCase = Case.getCurrentCaseThrows();

                return MapWaypoint.getWaypoints(currentCase.getSleuthkitCase());
            }

            @Override
            protected void done() {
                if (isDone() && !isCancelled()) {
                    try {
                        List<Waypoint> waypoints = get();
                        if (waypoints == null || waypoints.isEmpty()) {
                            return;
                        }

                        for (Waypoint point : waypoints) {
                            mapPanel.addWaypoint(point);
                        }

                        // There might be a better way to decide how to center
                        // but for now just use the first way point.
                        mapPanel.setCenterLocation(waypoints.get(0));

                    } catch (ExecutionException ex) {
                        logger.log(Level.WARNING, "An exception occured while initalizing waypoints for geolocation window.", ex);
                        MessageNotifyUtil.Message.error(Bundle.GLTopComponent_initilzation_error());
                    } catch (InterruptedException ex) {
                        logger.log(Level.WARNING, "The initilization thread for geolocation window was interrupted.", ex);
                    }
                }
            }
        };

        worker.execute();
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

        setLayout(new java.awt.BorderLayout());
        add(mapPanel, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private org.sleuthkit.autopsy.geolocation.MapPanel mapPanel;
    // End of variables declaration//GEN-END:variables
}
