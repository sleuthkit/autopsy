/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javax.swing.SwingUtilities;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.ObjectUtils.notEqual;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupManager;
import org.sleuthkit.autopsy.imagegallery.gui.GuiUtils;
import org.sleuthkit.autopsy.imagegallery.gui.NoGroupsDialog;
import org.sleuthkit.autopsy.imagegallery.gui.StatusBar;
import org.sleuthkit.autopsy.imagegallery.gui.SummaryTablePane;
import org.sleuthkit.autopsy.imagegallery.gui.Toolbar;
import org.sleuthkit.autopsy.imagegallery.gui.drawableviews.GroupPane;
import org.sleuthkit.autopsy.imagegallery.gui.drawableviews.MetaDataPane;
import org.sleuthkit.autopsy.imagegallery.gui.navpanel.GroupTree;
import org.sleuthkit.autopsy.imagegallery.gui.navpanel.HashHitGroupList;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.DataSource;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Top component which displays ImageGallery interface.
 *
 * Although ImageGallery doesn't currently use the explorer manager, this
 * TopComponent provides one through the getExplorerManager method. However,
 * this does not seem to function correctly unless a Netbeans provided explorer
 * view is present in the TopComponenet, even if it is invisible/ zero sized
 */
@TopComponent.Description(
        preferredID = "ImageGalleryTopComponent",
        //iconBase = "org/sleuthkit/autopsy/imagegallery/images/lightbulb.png" use this to put icon in window title area,
        persistenceType = TopComponent.PERSISTENCE_NEVER)
@RetainLocation("ImageGallery")
@TopComponent.Registration(mode = "ImageGallery", openAtStartup = false)
@Messages({
    "CTL_ImageGalleryAction=Image/Video Gallery",
    "CTL_ImageGalleryTopComponent=Image/Video Gallery"
})
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class ImageGalleryTopComponent extends TopComponent implements ExplorerManager.Provider, Lookup.Provider {

    public final static String PREFERRED_ID = "ImageGalleryTopComponent"; // NON-NLS
    private static final Logger logger = Logger.getLogger(ImageGalleryTopComponent.class.getName());
    private static volatile boolean topComponentInitialized = false;

    private final ExplorerManager em = new ExplorerManager();
    private final Lookup lookup = (ExplorerUtils.createLookup(em, getActionMap()));

    private ImageGalleryController controller;

    private SplitPane splitPane;
    private StackPane centralStack;
    private final BorderPane borderPane = new BorderPane();
    private StackPane fullUIStack;
    private MetaDataPane metaDataTable;
    private GroupPane groupPane;
    private GroupTree groupTree;
    private HashHitGroupList hashHitList;
    private VBox leftPane;
    private Scene myScene;

    private Node infoOverlay;
    private final Region infoOverLayBackground = new TranslucentRegion();
    
    

    /**
     * Returns whether the ImageGallery window is open or not.
     *
     * @return true, if Image gallery is opened, false otherwise
     */
    public static boolean isImageGalleryOpen() {

        final TopComponent topComponent = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (topComponent != null) {
            return topComponent.isOpened();
        }
        return false;
    }

    /**
     * Returns the top component window.
     *
     * @return Image gallery top component window, null if it's not open
     */
    public static TopComponent getTopComponent() {
        return WindowManager.getDefault().findTopComponent(PREFERRED_ID);
    }

    /**
     * NOTE: This usually gets called on the EDT
     * 
     * @throws NoCurrentCaseException 
     */
    @Messages({
        "ImageGalleryTopComponent.openTopCommponent.chooseDataSourceDialog.headerText=Choose a data source to view.",
        "ImageGalleryTopComponent.openTopCommponent.chooseDataSourceDialog.contentText=Data source:",
        "ImageGalleryTopComponent.openTopCommponent.chooseDataSourceDialog.all=All",
        "ImageGalleryTopComponent.openTopCommponent.chooseDataSourceDialog.titleText=Image Gallery",})
    public static void openTopComponent() throws NoCurrentCaseException {

        // This creates the top component and adds the UI widgets if it has not yet been opened
        final TopComponent topComponent = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (topComponent.isOpened()) {
            showTopComponent(topComponent);
            return;
        }
        
        // Wait until the FX UI has been created.  This way, we can always
        // show the gray progress screen
        // TODO: do this in a more elegant way.  
        while (topComponentInitialized == false) {}

        ImageGalleryController controller = ImageGalleryModule.getController();
        ((ImageGalleryTopComponent) topComponent).setController(controller);
        
        // Display the UI so taht they can see the progress screen
        showTopComponent(topComponent);
        
        List<DataSource> dataSources = Collections.emptyList();
        try {
            dataSources = controller.getSleuthKitCase().getDataSources();
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.SEVERE, "Unable to get data sourcecs.", tskCoreException);
        }
        
        GroupManager groupManager = controller.getGroupManager();
        synchronized (groupManager) {
            if (dataSources.size() <= 1
                || groupManager.getGroupBy() != DrawableAttribute.PATH) {
                /* if there is only one datasource or the grouping is already
                 * set to something other than path , don't both to ask for
                 * datasource */
                groupManager.regroup(null, groupManager.getGroupBy(), groupManager.getSortBy(), groupManager.getSortOrder(), true);
                return;
            }
        }

        Map<String, DataSource> dataSourceNames = new HashMap<>();
        dataSourceNames.put("All", null);
        dataSources.forEach(dataSource -> dataSourceNames.put(dataSource.getName() + " (ID: " + dataSource.getId() + ")", dataSource));

        Platform.runLater(() -> {
            ChoiceDialog<String> datasourceDialog = new ChoiceDialog<>(null, dataSourceNames.keySet());
            datasourceDialog.setTitle(Bundle.ImageGalleryTopComponent_openTopCommponent_chooseDataSourceDialog_titleText());
            datasourceDialog.setHeaderText(Bundle.ImageGalleryTopComponent_openTopCommponent_chooseDataSourceDialog_headerText());
            datasourceDialog.setContentText(Bundle.ImageGalleryTopComponent_openTopCommponent_chooseDataSourceDialog_contentText());
            datasourceDialog.initModality(Modality.APPLICATION_MODAL);
            GuiUtils.setDialogIcons(datasourceDialog);

            Optional<String> dataSourceName = datasourceDialog.showAndWait();
            DataSource dataSource = dataSourceName.map(dataSourceNames::get).orElse(null);
            synchronized (groupManager) {
                groupManager.regroup(dataSource, groupManager.getGroupBy(), groupManager.getSortBy(), groupManager.getSortOrder(), true);
            }
        });
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public static void showTopComponent(TopComponent topComponent) {
        topComponent.open();
        topComponent.toFront();
        topComponent.requestActive();
    }

    public static void closeTopComponent() {
        if (topComponentInitialized) {
            final TopComponent etc = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
            if (etc != null) {
                try {
                    etc.close();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "failed to close " + PREFERRED_ID, e); // NON-NLS
                }
            }
        }
    }

    public ImageGalleryTopComponent() throws NoCurrentCaseException {
        setName(Bundle.CTL_ImageGalleryTopComponent());
        initComponents();
        setController(ImageGalleryModule.getController());
    }

    synchronized private void setController(ImageGalleryController controller) {
        if (notEqual(this.controller, controller)) {
            if (this.controller != null) {
                this.controller.shutDown();
            }
            this.controller = controller;
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    //initialize jfx ui
                    fullUIStack = new StackPane(); //this is passed into controller
                    myScene = new Scene(fullUIStack);
                    jfxPanel.setScene(myScene);
                    groupPane = new GroupPane(controller);
                    centralStack = new StackPane(groupPane); //this is passed into controller
                    fullUIStack.getChildren().add(borderPane);
                    splitPane = new SplitPane();
                    borderPane.setCenter(splitPane);
                    Toolbar toolbar = new Toolbar(controller);
                    borderPane.setTop(toolbar);
                    borderPane.setBottom(new StatusBar(controller));
                    metaDataTable = new MetaDataPane(controller);
                    groupTree = new GroupTree(controller);
                    hashHitList = new HashHitGroupList(controller);
                    TabPane tabPane = new TabPane(groupTree, hashHitList);
                    tabPane.setPrefWidth(TabPane.USE_COMPUTED_SIZE);
                    tabPane.setMinWidth(TabPane.USE_PREF_SIZE);
                    VBox.setVgrow(tabPane, Priority.ALWAYS);
                    leftPane = new VBox(tabPane, new SummaryTablePane(controller));
                    SplitPane.setResizableWithParent(leftPane, Boolean.FALSE);
                    SplitPane.setResizableWithParent(groupPane, Boolean.TRUE);
                    SplitPane.setResizableWithParent(metaDataTable, Boolean.FALSE);
                    splitPane.getItems().addAll(leftPane, centralStack, metaDataTable);
                    splitPane.setDividerPositions(0.1, 1.0);

                    controller.regroupDisabledProperty().addListener((Observable observable) -> checkForGroups());
                    controller.getGroupManager().getAnalyzedGroups().addListener((Observable observable) -> Platform.runLater(() -> checkForGroups()));

                    topComponentInitialized = true;
                    
                    // This will cause the UI to show the progress dialog
                    Platform.runLater(() -> checkForGroups());
                }
            });
        }
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jfxPanel = new JFXPanel();

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jfxPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 532, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jfxPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 389, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javafx.embed.swing.JFXPanel jfxPanel;
    // End of variables declaration//GEN-END:variables

    @Override
    public List<Mode> availableModes(List<Mode> modes) {
        /*
         * This looks like the right thing to do, but online discussions seems
         * to indicate this method is effectively deprecated. A break point
         * placed here was never hit.
         */

        return modes.stream().filter(mode -> mode.getName().equals("timeline") || mode.getName().equals("ImageGallery"))
                .collect(Collectors.toList());
    }

    @Override
    public void componentOpened() {
        super.componentOpened();
        WindowManager.getDefault().setTopComponentFloating(this, true);
    }

    @Override
    public ExplorerManager getExplorerManager() {
        return em;
    }

    @Override
    public Lookup getLookup() {
        return lookup;
    }

    /**
     * Check if there are any fully analyzed groups available from the
     * GroupManager and remove blocking progress spinners if there are. If there
     * aren't, add a blocking progress spinner with appropriate message.
     * 
     * This gets called when any group becomes analyzed and when started.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @NbBundle.Messages({
        "ImageGalleryController.noGroupsDlg.msg1=No groups are fully analyzed; but listening to ingest is disabled. "
        + " No groups will be available until ingest is finished and listening is re-enabled.",
        "ImageGalleryController.noGroupsDlg.msg2=No groups are fully analyzed yet, but ingest is still ongoing.  Please Wait.",
        "ImageGalleryController.noGroupsDlg.msg3=No groups are fully analyzed yet, but image / video data is still being populated.  Please Wait.",
        "ImageGalleryController.noGroupsDlg.msg4=There are no images/videos available from the added datasources;  but listening to ingest is disabled. "
        + " No groups will be available until ingest is finished and listening is re-enabled.",
        "ImageGalleryController.noGroupsDlg.msg5=There are no images/videos in the added datasources.",
        "ImageGalleryController.noGroupsDlg.msg6=There are no fully analyzed groups to display:"
        + "  the current Group By setting resulted in no groups, "
        + "or no groups are fully analyzed but ingest is not running."})
    private void checkForGroups() {
        GroupManager groupManager = controller.getGroupManager();

        // if there are groups to display, then display them
        // @@@ Need to check timing on this and make sure we have only groups for the selected DS.  Seems like rebuild can cause groups to be created for a DS that is not later selected...
        if (isNotEmpty(groupManager.getAnalyzedGroups())) {
            clearNotification();
            return;
        }

        // display a message based on if ingest is running and/or listening
        if (IngestManager.getInstance().isIngestRunning()) {
            if (controller.isListeningEnabled()) {
                replaceNotification(centralStack,
                        new NoGroupsDialog(Bundle.ImageGalleryController_noGroupsDlg_msg2(),
                                new ProgressIndicator()));
            } else {
                replaceNotification(fullUIStack,
                        new NoGroupsDialog(Bundle.ImageGalleryController_noGroupsDlg_msg1()));
            }
            return;
        }
        
        // display a message about stuff still being in the queue
        if (controller.getDBTasksQueueSizeProperty().get() > 0) {
            replaceNotification(fullUIStack,
                    new NoGroupsDialog(Bundle.ImageGalleryController_noGroupsDlg_msg3(),
                            new ProgressIndicator()));
            return;
        }
        
        
        // are there are files in the DB?
        try {
            if (controller.getDatabase().countAllFiles() <= 0) {
                // there are no files in db
                if (controller.isListeningEnabled()) {
                    replaceNotification(fullUIStack,
                            new NoGroupsDialog(Bundle.ImageGalleryController_noGroupsDlg_msg5()));
                } else {
                    replaceNotification(fullUIStack,
                            new NoGroupsDialog(Bundle.ImageGalleryController_noGroupsDlg_msg4()));
                }
                return;
            }
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.SEVERE, "Error counting files in the database.", tskCoreException);
        }

        if (false == groupManager.isRegrouping()) {
            replaceNotification(centralStack,
                    new NoGroupsDialog(Bundle.ImageGalleryController_noGroupsDlg_msg6()));
        }
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void replaceNotification(StackPane stackPane, Node newNode) {
        clearNotification();
        infoOverlay = new StackPane(infoOverLayBackground, newNode);
        if (stackPane != null) {
            stackPane.getChildren().add(infoOverlay);
        }

    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void clearNotification() {
        //remove the ingest spinner
        fullUIStack.getChildren().remove(infoOverlay);
        //remove the ingest spinner
        centralStack.getChildren().remove(infoOverlay);

    }

    /**
     * Region with partialy opacity used to block out parts of the UI behind a
     * pseudo dialog.
     */
    static final private class TranslucentRegion extends Region {

        TranslucentRegion() {
            setBackground(new Background(new BackgroundFill(Color.GREY, CornerRadii.EMPTY, Insets.EMPTY)));
            setOpacity(.4);
        }
    }
}
