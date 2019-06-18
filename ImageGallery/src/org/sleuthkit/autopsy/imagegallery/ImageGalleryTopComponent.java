/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013-2019 Basis Technology Corp.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
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
import javax.swing.JComponent;
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.directorytree.ExternalViewerShortcutAction;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableAttribute;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.DrawableGroup;
import org.sleuthkit.autopsy.imagegallery.datamodel.grouping.GroupManager;
import org.sleuthkit.autopsy.imagegallery.gui.DataSourceCell;
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
 * The singleton Image Gallery top component.
 */
@TopComponent.Description(
        preferredID = "ImageGalleryTopComponent",
        //iconBase = "org/sleuthkit/autopsy/imagegallery/images/lightbulb.png", /*use this to put icon in window title area*/
        persistenceType = TopComponent.PERSISTENCE_NEVER
)
@RetainLocation("ImageGallery")
@TopComponent.Registration(mode = "ImageGallery", openAtStartup = false)
@Messages({
    "CTL_ImageGalleryAction=Image/Video Gallery",
    "CTL_ImageGalleryTopComponent=Image/Video Gallery"
})
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
public final class ImageGalleryTopComponent extends TopComponent implements ExplorerManager.Provider, Lookup.Provider {

    private static final long serialVersionUID = 1L;
    private static final String PREFERRED_ID = "ImageGalleryTopComponent"; // NON-NLS
    private static final Logger logger = Logger.getLogger(ImageGalleryTopComponent.class.getName());

    private final ExplorerManager em = new ExplorerManager();
    private final Lookup lookup = (ExplorerUtils.createLookup(em, getActionMap()));

    private volatile ImageGalleryController controller;
    private volatile ControllerListener controllerListener;
    private volatile GroupManagerListener groupManagerListener;

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
     * Queries whether the singleton Image Gallery top component's window is
     * open. Note that calling this method will cause the top component to be
     * constructed if it does not already exist.
     *
     * @return True or false.
     */
    public static boolean isImageGalleryOpen() {
        return getTopComponent().isOpened();
    }

    /**
     * Gets the singleton Image Gallery top component. Note that calling this
     * method will cause the top component to be constructed if it does not
     * already exist.
     *
     * @return The top component.
     */
    public static ImageGalleryTopComponent getTopComponent() {
        return (ImageGalleryTopComponent) WindowManager.getDefault().findTopComponent(PREFERRED_ID);
    }

    /**
     * Creates the Image Gallery top component if it does not already exist and
     * opens its window.
     *
     * @throws TskCoreException If there is a problem opening the top component.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public static void openTopComponent() throws TskCoreException {
        final ImageGalleryTopComponent topComponent = getTopComponent();
        if (topComponent.isOpened()) {
            showTopComponent();
        } else {
            topComponent.openForCurrentCase();
        }
    }

    /**
     * Configures the groups manager for the selected data source(s) and opens
     * this top component's window.
     *
     * @param selectedDataSource      The data source selected, null if all data
     *                                sources are selected.
     * @param dataSourcesTooManyFiles A map of data sources to flags indicating
     *                                whether or not the data source has to many
     *                                files to actually be displayed.
     */
    private void openWithSelectedDataSources(DataSource selectedDataSource, Map<DataSource, Boolean> dataSourcesTooManyFiles) {
        if (dataSourcesTooManyFiles.get(selectedDataSource)) {
            Platform.runLater(ImageGalleryTopComponent::showTooManyFiles);
        } else {
            SwingUtilities.invokeLater(() -> showTopComponent());
            GroupManager groupManager = controller.getGroupManager();
            groupManager.regroup(selectedDataSource, groupManager.getGroupBy(), groupManager.getSortBy(), groupManager.getSortOrder(), true);
        }
    }

    /**
     * Displays a dialog box informing the user that the data source(s) selected
     * to have their images displayed have too many image files and will not be
     * displayed.
     */
    @NbBundle.Messages({"ImageGallery.dialogTitle=Image Gallery",
        "ImageGallery.showTooManyFiles.contentText=There are too many files in the selected datasource(s) to ensure reasonable performance.",
        "ImageGallery.showTooManyFiles.headerText="})
    private static void showTooManyFiles() {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION, Bundle.ImageGallery_showTooManyFiles_contentText(), ButtonType.OK);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(Bundle.ImageGallery_dialogTitle());
        GuiUtils.setDialogIcons(dialog);
        dialog.setHeaderText(Bundle.ImageGallery_showTooManyFiles_headerText());
        dialog.showAndWait();
    }

    /**
     * Opens the singleton top component's window, brings it to the front and
     * gives it focus. Note that calling this method will cause the top
     * component to be constructed if it does not already exist.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private static void showTopComponent() {
        final ImageGalleryTopComponent topComponent = getTopComponent();
        if (topComponent.isOpened() == false) {
            topComponent.open();
        }
        topComponent.toFront();
        topComponent.requestActive();
    }

    /*
     * Closes the singleton Image Gallery top component.
     */
    public static void closeTopComponent() {
        ImageGalleryTopComponent topComponent = getTopComponent();
        topComponent.closeForCurrentCase();
        topComponent.close();
    }

    /**
     * Contructs the singleton Image Gallery top component. Called by the
     * NetBeans WindowManager.
     */
    public ImageGalleryTopComponent() {
        setName(Bundle.CTL_ImageGalleryTopComponent());
        initComponents();
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ExternalViewerShortcutAction.EXTERNAL_VIEWER_SHORTCUT, "useExternalViewer"); //NON-NLS 
        getActionMap().put("useExternalViewer", ExternalViewerShortcutAction.getInstance()); //NON-NLS
    }

    /**
     * Gets the controller for the current case, allows the user to select the
     * data sources for which images are to be displayed and opens the top
     * component's window.
     *
     * @throws TskCoreException If there is an error getting the current
     *                          controller.
     */
    @Messages({
        "ImageGalleryTopComponent.chooseDataSourceDialog.headerText=Choose a data source to view.",
        "ImageGalleryTopComponent.chooseDataSourceDialog.contentText=Data source:",
        "ImageGalleryTopComponent.chooseDataSourceDialog.all=All",
        "ImageGalleryTopComponent.chooseDataSourceDialog.titleText=Image Gallery",})
    private void openForCurrentCase() throws TskCoreException {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                resetComponents();
                openForDataSelectedSources();
            }
        });
    }

    /**
     * Integrates the controller for the current case into this top component
     * and its child UI components. This task also queues another JavaFX thread
     * task to check for analyzed groups, which has the side effect of starting
     * the spinner(s) that take the place of a wait cursor.
     */
    private void resetComponents() {
        Case currentCase = Case.getCurrentCase();
        ImageGalleryController controllerForCase = ImageGalleryController.getController(currentCase);
        if (notEqual(controller, controllerForCase)) {
            controller = controllerForCase;
            /*
             * Create or re-create the top component's child UI components. This
             * is currently done every time a new controller is created (i.e., a
             * new case is opened).
             */
            fullUIStack = new StackPane();
            myScene = new Scene(fullUIStack);
            jfxPanel.setScene(myScene);
            groupPane = new GroupPane(controller);
            centralStack = new StackPane(groupPane);
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

            /*
             * Set up listeners to update the UI when the controller's grouping
             * enabled/disabled property changes or the contents of the group
             * managers list of drawable groups changes.
             */
            controllerListener = new ControllerListener();
            controller.regroupDisabledProperty().addListener(controllerListener);
            groupManagerListener = new GroupManagerListener();
            controller.getGroupManager().getAnalyzedGroupsForCurrentGroupBy().addListener(groupManagerListener);

            Platform.runLater(() -> checkForAnalyzedGroupsForCurrentGroupBy());
        }
    }

    /**
     * Starts a background thread to query the case database. This background
     * task may dispatch a JavaFX thread task to do a data source selection
     * dialog. Ultimately, there is a final task that either opens the window in
     * the AWT EDT or displays a "too many files" dialog in the JFX thread.
     */
    private void openForDataSelectedSources() {
        new Thread(new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                /*
                 * If there is only one data source or the grouping criterion is
                 * already set to something other than by path (the default),
                 * proceed to open this top component. Otherwise, do a dialog to
                 * allow the user to select the data sources for which images
                 * are to be displayed, then open the top component.
                 */
                List<DataSource> dataSources = controller.getCaseDatabase().getDataSources();
                Map<DataSource, Boolean> dataSourcesWithTooManyFiles = new HashMap<>();
                // TODO (JIRA-5218): At least some of this designation of "all data sources" with null seems uneccessary; 
                // in any case, the use of nulls and zeros here is 
                // very confusing and should be reworked.
                if (dataSources.size() <= 1
                        || controller.getGroupManager().getGroupBy() != DrawableAttribute.PATH) {
                    dataSourcesWithTooManyFiles.put(null, controller.hasTooManyFiles(null));
                    openWithSelectedDataSources(null, dataSourcesWithTooManyFiles);
                } else {
                    dataSources.add(0, null);
                    for (DataSource dataSource : dataSources) {
                        dataSourcesWithTooManyFiles.put(dataSource, controller.hasTooManyFiles(dataSource));
                    }
                    Platform.runLater(() -> {
                        List<Optional<DataSource>> dataSourceOptionals = dataSources.stream().map(Optional::ofNullable).collect(Collectors.toList());
                        ChoiceDialog<Optional<DataSource>> datasourceDialog = new ChoiceDialog<>(null, dataSourceOptionals);
                        datasourceDialog.setTitle(Bundle.ImageGalleryTopComponent_chooseDataSourceDialog_titleText());
                        datasourceDialog.setHeaderText(Bundle.ImageGalleryTopComponent_chooseDataSourceDialog_headerText());
                        datasourceDialog.setContentText(Bundle.ImageGalleryTopComponent_chooseDataSourceDialog_contentText());
                        datasourceDialog.initModality(Modality.APPLICATION_MODAL);
                        GuiUtils.setDialogIcons(datasourceDialog);
                        @SuppressWarnings(value = "unchecked")
                        ComboBox<Optional<DataSource>> comboBox = (ComboBox<Optional<DataSource>>) datasourceDialog.getDialogPane().lookup(".combo-box");
                        comboBox.setCellFactory((ListView<Optional<DataSource>> unused) -> new DataSourceCell(dataSourcesWithTooManyFiles, controller.getAllDataSourcesDrawableDBStatus()));
                        comboBox.setButtonCell(new DataSourceCell(dataSourcesWithTooManyFiles, controller.getAllDataSourcesDrawableDBStatus()));
                        DataSource dataSource = datasourceDialog.showAndWait().orElse(Optional.empty()).orElse(null);
                        openWithSelectedDataSources(dataSource, dataSourcesWithTooManyFiles);
                    });
                }
                return null;
            }
        }).start();
    }

    void closeForCurrentCase() {
        if (controller != null) {
            if (controllerListener != null) {
                controller.regroupDisabledProperty().removeListener(controllerListener);
            }
            if (groupManagerListener != null) {
                controller.getGroupManager().getAnalyzedGroupsForCurrentGroupBy().removeListener(groupManagerListener);
            }
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
     * Checks if there are any fully analyzed groups available from the groups
     * manager and removes the blocking progress spinner if there are analyzed
     * groups; otherwise adds a blocking progress spinner with an appropriate
     * message.
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
    private void checkForAnalyzedGroupsForCurrentGroupBy() {

        GroupManager groupManager = controller.getGroupManager();

        /*
         * TODO (JIRA-5219): Need to check timing on this and make sure we have
         * only groups for the selected DS. Seems like rebuild can cause groups
         * to be created for a DS that is not later selected...
         */
        if (isNotEmpty(groupManager.getAnalyzedGroupsForCurrentGroupBy())) {
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
            if (controller.getDrawablesDatabase().countAllFiles() <= 0) {
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

    /**
     * Removes the spinner(s).
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void clearNotification() {
        fullUIStack.getChildren().remove(infoOverlay);
        centralStack.getChildren().remove(infoOverlay);
    }

    /**
     * A partially opaque region used to block out parts of the UI behind a
     * pseudo dialog.
     */
    static final private class TranslucentRegion extends Region {

        TranslucentRegion() {
            setBackground(new Background(new BackgroundFill(Color.GREY, CornerRadii.EMPTY, Insets.EMPTY)));
            setOpacity(.4);
        }
    }

    /**
     * Instances of this class are used to listen for changes to the
     * controller's grouping enabled property. If the value of the property
     * changes, a call to the top component's
     * checkForAnalyzedGroupsForCurrentGroupBy method is queued for the JavaFX
     * thread.
     */
    private class ControllerListener implements ChangeListener<Boolean> {

        @Override
        public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
            Platform.runLater(() -> checkForAnalyzedGroupsForCurrentGroupBy());
        }

    }

    /**
     * Instances of this class are used to listen for changes to the group
     * manager's list of drawable groups for the user's currently selected
     * "group by" choice. If the contents of the list change, a call to the top
     * component's checkForAnalyzedGroupsForCurrentGroupBy method is queued for
     * the JavaFX thread.
     */
    private class GroupManagerListener implements ListChangeListener<DrawableGroup> {

        @Override
        public void onChanged(Change<? extends DrawableGroup> c) {
            Platform.runLater(() -> checkForAnalyzedGroupsForCurrentGroupBy());
        }

    }

}
