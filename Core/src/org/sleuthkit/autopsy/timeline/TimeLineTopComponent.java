/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-15 Basis Technology Corp.
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

import java.awt.BorderLayout;
import java.util.Collections;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.event.ActionEvent;
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
import org.netbeans.api.settings.ConvertAsProperties;
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
import org.sleuthkit.autopsy.timeline.ui.StatusBar;
import org.sleuthkit.autopsy.timeline.ui.TimeLineResultView;
import org.sleuthkit.autopsy.timeline.ui.TimeZonePanel;
import org.sleuthkit.autopsy.timeline.ui.VisualizationPanel;
import org.sleuthkit.autopsy.timeline.ui.detailview.tree.NavPanel;
import org.sleuthkit.autopsy.timeline.ui.filtering.FilterSetPanel;
import org.sleuthkit.autopsy.timeline.zooming.ZoomSettingsPane;

/**
 * TopComponent for the timeline feature.
 */
@ConvertAsProperties(
        dtd = "-//org.sleuthkit.autopsy.timeline//TimeLine//EN",
        autostore = false)
@TopComponent.Description(
        preferredID = "TimeLineTopComponent",
        //iconBase="SET/PATH/TO/ICON/HERE", 
        persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "timeline", openAtStartup = false)
public final class TimeLineTopComponent extends TopComponent implements ExplorerManager.Provider, TimeLineUI {

    private static final Logger LOGGER = Logger.getLogger(TimeLineTopComponent.class.getName());

    private DataContentPanel dataContentPanel;

    private TimeLineResultView tlrv;

    private final ExplorerManager em = new ExplorerManager();

    private TimeLineController controller;

    ////jfx componenets that make up the interface
    private final FilterSetPanel filtersPanel = new FilterSetPanel();

    private final Tab eventsTab = new Tab(
            NbBundle.getMessage(TimeLineTopComponent.class, "TimeLineTopComponent.eventsTab.name"));

    private final Tab filterTab = new Tab(
            NbBundle.getMessage(TimeLineTopComponent.class, "TimeLineTopComponent.filterTab.name"));

    private final VBox leftVBox = new VBox(5);

    private final NavPanel navPanel = new NavPanel();

    private final StatusBar statusBar = new StatusBar();

    private final TabPane tabPane = new TabPane();

    private final ZoomSettingsPane zoomSettingsPane = new ZoomSettingsPane();

    private final VisualizationPanel visualizationPanel = new VisualizationPanel(navPanel);

    private final SplitPane splitPane = new SplitPane();

    private final TimeZonePanel timeZonePanel = new TimeZonePanel();

    public TimeLineTopComponent() {
        initComponents();

        associateLookup(ExplorerUtils.createLookup(em, getActionMap()));

        setName(NbBundle.getMessage(TimeLineTopComponent.class, "CTL_TimeLineTopComponent"));
        setToolTipText(NbBundle.getMessage(TimeLineTopComponent.class, "HINT_TimeLineTopComponent"));
        setIcon(WindowManager.getDefault().getMainWindow().getIconImage()); //use the same icon as main application

        timeZonePanel.setText(NbBundle.getMessage(this.getClass(), "TimeLineTopComponent.timeZonePanel.text"));
        customizeComponents();
    }

    synchronized private void customizeComponents() {

        dataContentPanel = DataContentPanel.createInstance();
        this.contentViewerContainerPanel.add(dataContentPanel, BorderLayout.CENTER);
        tlrv = new TimeLineResultView(dataContentPanel);
        DataResultPanel dataResultPanel = tlrv.getDataResultPanel();
        this.resultContainerPanel.add(dataResultPanel, BorderLayout.CENTER);
        dataResultPanel.open();

        Platform.runLater(() -> {
            //assemble ui componenets together
            jFXstatusPanel.setScene(new Scene(statusBar));
            jFXVizPanel.setScene(new Scene(splitPane));

            splitPane.setDividerPositions(0);

            filterTab.setClosable(false);
            filterTab.setContent(filtersPanel);
            filterTab.setGraphic(new ImageView("org/sleuthkit/autopsy/timeline/images/funnel.png")); // NON-NLS

            eventsTab.setClosable(false);
            eventsTab.setContent(navPanel);
            eventsTab.setGraphic(new ImageView("org/sleuthkit/autopsy/timeline/images/timeline_marker.png")); // NON-NLS

            tabPane.getTabs().addAll(filterTab, eventsTab);
            VBox.setVgrow(tabPane, Priority.ALWAYS);

            VBox.setVgrow(timeZonePanel, Priority.SOMETIMES);
            leftVBox.getChildren().addAll(timeZonePanel, zoomSettingsPane, tabPane);

            SplitPane.setResizableWithParent(leftVBox, Boolean.FALSE);
            splitPane.getItems().addAll(leftVBox, visualizationPanel);
        });
    }

    @Override
    public synchronized void setController(TimeLineController controller) {
        this.controller = controller;

        tlrv.setController(controller);
        Platform.runLater(() -> {
            jFXVizPanel.getScene().addEventFilter(KeyEvent.KEY_PRESSED,
                    (KeyEvent event) -> {
                        if (new KeyCodeCombination(KeyCode.LEFT, KeyCodeCombination.ALT_DOWN).match(event)) {
                            new Back(controller).handle(new ActionEvent());
                        } else if (new KeyCodeCombination(KeyCode.BACK_SPACE).match(event)) {
                            new Back(controller).handle(new ActionEvent());
                        } else if (new KeyCodeCombination(KeyCode.RIGHT, KeyCodeCombination.ALT_DOWN).match(event)) {
                            new Forward(controller).handle(new ActionEvent());
                        } else if (new KeyCodeCombination(KeyCode.BACK_SPACE, KeyCodeCombination.SHIFT_DOWN).match(event)) {
                            new Forward(controller).handle(new ActionEvent());
                        }
                    });
            controller.viewModeProperty().addListener((Observable observable) -> {
                if (controller.viewModeProperty().get().equals(VisualizationMode.COUNTS)) {
                    tabPane.getSelectionModel().select(filterTab);
                }
            });
            eventsTab.disableProperty().bind(controller.viewModeProperty().isEqualTo(VisualizationMode.COUNTS));
            visualizationPanel.setController(controller);
            navPanel.setController(controller);
            filtersPanel.setController(controller);
            zoomSettingsPane.setController(controller);
            statusBar.setController(controller);
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

        jFXstatusPanel = new javafx.embed.swing.JFXPanel();
        splitYPane = new javax.swing.JSplitPane();
        jFXVizPanel = new javafx.embed.swing.JFXPanel();
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
