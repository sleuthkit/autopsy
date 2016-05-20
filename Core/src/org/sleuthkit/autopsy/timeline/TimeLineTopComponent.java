/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-16 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline;

import com.google.common.collect.Iterables;
import java.awt.BorderLayout;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javax.swing.SwingUtilities;
import org.controlsfx.control.Notifications;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.util.NbBundle;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import static org.openide.windows.TopComponent.PROP_UNDOCKING_DISABLED;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.corecomponents.DataContentPanel;
import org.sleuthkit.autopsy.corecomponents.DataResultPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.actions.Back;
import org.sleuthkit.autopsy.timeline.actions.Forward;
import org.sleuthkit.autopsy.timeline.explorernodes.EventNode;
import org.sleuthkit.autopsy.timeline.ui.HistoryToolBar;
import org.sleuthkit.autopsy.timeline.ui.StatusBar;
import org.sleuthkit.autopsy.timeline.ui.TimeLineResultView;
import org.sleuthkit.autopsy.timeline.ui.TimeZonePanel;
import org.sleuthkit.autopsy.timeline.ui.VisualizationPanel;
import org.sleuthkit.autopsy.timeline.ui.detailview.tree.EventsTree;
import org.sleuthkit.autopsy.timeline.ui.filtering.FilterSetPanel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomSettingsPane;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * TopComponent for the Timeline feature.
 */
@TopComponent.Description(
        preferredID = "TimeLineTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "timeline", openAtStartup = false)
public final class TimeLineTopComponent extends TopComponent implements ExplorerManager.Provider {

    private static final Logger LOGGER = Logger.getLogger(TimeLineTopComponent.class.getName());

    private final DataContentPanel contentViewerPanel;

    private final TimeLineResultView tlResultView;

    private final ExplorerManager em = new ExplorerManager();

    private final TimeLineController controller;

    @NbBundle.Messages({
        "TimelineTopComponent.selectedEventListener.errorMsg=There was a problem getting the content for the selected event."})
    /**
     * Listener that drives the ContentViewer when in List ViewMode.
     */
    private final InvalidationListener selectedEventListener = new InvalidationListener() {
        @Override
        public void invalidated(Observable observable) {
            if (controller.getSelectedEventIDs().size() == 1) {
                try {
                    EventNode eventNode = EventNode.createEventNode(Iterables.getOnlyElement(controller.getSelectedEventIDs()), controller.getEventsModel());
                    SwingUtilities.invokeLater(() -> contentViewerPanel.setNode(eventNode));
                } catch (IllegalStateException ex) {
                    //Since the case is closed, the user probably doesn't care about this, just log it as a precaution.
                    LOGGER.log(Level.SEVERE, "There was no case open to lookup the Sleuthkit object backing a SingleEvent.", ex); // NON-NLS
                } catch (TskCoreException ex) {
                    LOGGER.log(Level.SEVERE, "Failed to lookup Sleuthkit object backing a SingleEvent.", ex); // NON-NLS
                    Platform.runLater(() -> {
                        Notifications.create()
                                .owner(jFXVizPanel.getScene().getWindow())
                                .text(Bundle.TimelineTopComponent_selectedEventListener_errorMsg())
                                .showError();
                    });
                }
            } else {
                SwingUtilities.invokeLater(() -> contentViewerPanel.setNode(null));
            }
        }
    };

    public TimeLineTopComponent(TimeLineController controller) {
        initComponents();
        this.controller = controller;
        associateLookup(ExplorerUtils.createLookup(em, getActionMap()));

        setName(NbBundle.getMessage(TimeLineTopComponent.class, "CTL_TimeLineTopComponent"));
        setToolTipText(NbBundle.getMessage(TimeLineTopComponent.class, "HINT_TimeLineTopComponent"));
        setIcon(WindowManager.getDefault().getMainWindow().getIconImage()); //use the same icon as main application

        contentViewerPanel = DataContentPanel.createInstance();
        this.contentViewerContainerPanel.add(contentViewerPanel, BorderLayout.CENTER);
        tlResultView = new TimeLineResultView(controller, contentViewerPanel);
        final DataResultPanel dataResultPanel = tlResultView.getDataResultPanel();
        this.resultContainerPanel.add(dataResultPanel, BorderLayout.CENTER);
        dataResultPanel.open();
        customizeFXComponents();

        //Listen to ViewMode and adjust GUI componenets as needed.
        controller.viewModeProperty().addListener(viewMode -> {
            switch (controller.getViewMode()) {
                case COUNTS:
                case DETAIL:
                    /*
                     * For counts and details mode, restore the result table at
                     * the bottom left, if neccesary.
                     */
                    SwingUtilities.invokeLater(() -> {
                        splitYPane.remove(contentViewerContainerPanel);
                        if ((lowerSplitXPane.getParent() == splitYPane) == false) {
                            splitYPane.add(lowerSplitXPane);
                            lowerSplitXPane.add(contentViewerContainerPanel);
                        }
                    });
                    controller.getSelectedEventIDs().removeListener(selectedEventListener);
                    break;
                case LIST:
                    /*
                     * For list mode, remove the result table, and let the
                     * content viewer expand across the bottom.
                     */
                    SwingUtilities.invokeLater(() -> {
                        splitYPane.remove(lowerSplitXPane);
                        splitYPane.add(contentViewerContainerPanel);
                        dataResultPanel.setNode(null);
                    });
                    controller.getSelectedEventIDs().addListener(selectedEventListener);
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown ViewMode: " + controller.getViewMode());
            }
        });
    }

    @NbBundle.Messages({"TimeLineTopComponent.eventsTab.name=Events",
        "TimeLineTopComponent.filterTab.name=Filters"})
    void customizeFXComponents() {
        Platform.runLater(() -> {

            //create and wire up jfx componenets that make up the interface
            final Tab filterTab = new Tab(Bundle.TimeLineTopComponent_filterTab_name(), new FilterSetPanel(controller));
            filterTab.setClosable(false);
            filterTab.setGraphic(new ImageView("org/sleuthkit/autopsy/timeline/images/funnel.png")); // NON-NLS

            final EventsTree eventsTree = new EventsTree(controller);
            final VisualizationPanel visualizationPanel = new VisualizationPanel(controller, eventsTree);
            final Tab eventsTreeTab = new Tab(Bundle.TimeLineTopComponent_eventsTab_name(), eventsTree);
            eventsTreeTab.setClosable(false);
            eventsTreeTab.setGraphic(new ImageView("org/sleuthkit/autopsy/timeline/images/timeline_marker.png")); // NON-NLS
            eventsTreeTab.disableProperty().bind(controller.viewModeProperty().isNotEqualTo(ViewMode.DETAIL));

            final TabPane leftTabPane = new TabPane(filterTab, eventsTreeTab);
            VBox.setVgrow(leftTabPane, Priority.ALWAYS);
            controller.viewModeProperty().addListener(viewMode -> {
                if (controller.getViewMode().equals(ViewMode.DETAIL) == false) {
                    //if view mode is counts, make sure events tab is not active
                    leftTabPane.getSelectionModel().select(filterTab);
                }
            });

            HistoryToolBar historyToolBar = new HistoryToolBar(controller);
            final TimeZonePanel timeZonePanel = new TimeZonePanel();
            VBox.setVgrow(timeZonePanel, Priority.SOMETIMES);

            final ZoomSettingsPane zoomSettingsPane = new ZoomSettingsPane(controller);

            final VBox leftVBox = new VBox(5, timeZonePanel, historyToolBar, zoomSettingsPane, leftTabPane);
            SplitPane.setResizableWithParent(leftVBox, Boolean.FALSE);

            final SplitPane mainSplitPane = new SplitPane(leftVBox, visualizationPanel);
            mainSplitPane.setDividerPositions(0);

            final Scene scene = new Scene(mainSplitPane);
            scene.addEventFilter(KeyEvent.KEY_PRESSED,
                    (KeyEvent event) -> {
                if (new KeyCodeCombination(KeyCode.LEFT, KeyCodeCombination.ALT_DOWN).match(event)) {
                    new Back(controller).handle(null);
                } else if (new KeyCodeCombination(KeyCode.BACK_SPACE).match(event)) {
                    new Back(controller).handle(null);
                } else if (new KeyCodeCombination(KeyCode.RIGHT, KeyCodeCombination.ALT_DOWN).match(event)) {
                    new Forward(controller).handle(null);
                } else if (new KeyCodeCombination(KeyCode.BACK_SPACE, KeyCodeCombination.SHIFT_DOWN).match(event)) {
                    new Forward(controller).handle(null);
                }
            });

            //add ui componenets to JFXPanels
            jFXVizPanel.setScene(scene);
            jFXstatusPanel.setScene(new Scene(new StatusBar(controller)));
        });
    }

    @Override
    public List<Mode> availableModes(List<Mode> modes) {
        return Collections.emptyList();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jFXstatusPanel = new JFXPanel();
        splitYPane = new javax.swing.JSplitPane();
        jFXVizPanel = new JFXPanel();
        lowerSplitXPane = new javax.swing.JSplitPane();
        resultContainerPanel = new javax.swing.JPanel();
        contentViewerContainerPanel = new javax.swing.JPanel();

        jFXstatusPanel.setPreferredSize(new java.awt.Dimension(100, 16));

        splitYPane.setDividerLocation(420);
        splitYPane.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);
        splitYPane.setResizeWeight(0.9);
        splitYPane.setPreferredSize(new java.awt.Dimension(1024, 400));
        splitYPane.setLeftComponent(jFXVizPanel);

        lowerSplitXPane.setDividerLocation(600);
        lowerSplitXPane.setResizeWeight(0.5);
        lowerSplitXPane.setPreferredSize(new java.awt.Dimension(1200, 300));
        lowerSplitXPane.setRequestFocusEnabled(false);

        resultContainerPanel.setPreferredSize(new java.awt.Dimension(700, 300));
        resultContainerPanel.setLayout(new java.awt.BorderLayout());
        lowerSplitXPane.setLeftComponent(resultContainerPanel);

        contentViewerContainerPanel.setPreferredSize(new java.awt.Dimension(500, 300));
        contentViewerContainerPanel.setLayout(new java.awt.BorderLayout());
        lowerSplitXPane.setRightComponent(contentViewerContainerPanel);

        splitYPane.setRightComponent(lowerSplitXPane);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(splitYPane, javax.swing.GroupLayout.DEFAULT_SIZE, 972, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jFXstatusPanel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGap(0, 0, 0))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(splitYPane, javax.swing.GroupLayout.DEFAULT_SIZE, 482, Short.MAX_VALUE)
                .addGap(0, 0, 0)
                .addComponent(jFXstatusPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 29, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel contentViewerContainerPanel;
    private javafx.embed.swing.JFXPanel jFXVizPanel;
    private javafx.embed.swing.JFXPanel jFXstatusPanel;
    private javax.swing.JSplitPane lowerSplitXPane;
    private javax.swing.JPanel resultContainerPanel;
    private javax.swing.JSplitPane splitYPane;
    // End of variables declaration//GEN-END:variables

    @Override
    public void componentOpened() {
        WindowManager.getDefault().setTopComponentFloating(this, true);
        putClientProperty(PROP_UNDOCKING_DISABLED, true);
    }

    @Override
    public void componentClosed() {
        // TODO add custom code on component closing
    }

    void writeProperties(java.util.Properties p) {
        // better to version settings since initial version as advocated at
        // http://wiki.apidesign.org/wiki/PropertyFiles
        p.setProperty("version", "1.0");
        // TODO store your settings
    }

    void readProperties(java.util.Properties p) {
        String version = p.getProperty("version");
        // TODO read your settings according to their version
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }
}
