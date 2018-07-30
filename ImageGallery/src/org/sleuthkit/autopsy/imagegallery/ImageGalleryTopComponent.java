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

import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.RetainLocation;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.gui.StatusBar;
import org.sleuthkit.autopsy.imagegallery.gui.SummaryTablePane;
import org.sleuthkit.autopsy.imagegallery.gui.Toolbar;
import org.sleuthkit.autopsy.imagegallery.gui.drawableviews.GroupPane;
import org.sleuthkit.autopsy.imagegallery.gui.drawableviews.MetaDataPane;
import org.sleuthkit.autopsy.imagegallery.gui.navpanel.GroupTree;
import org.sleuthkit.autopsy.imagegallery.gui.navpanel.HashHitGroupList;

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
    private static final Logger LOGGER = Logger.getLogger(ImageGalleryTopComponent.class.getName());
    private static volatile boolean topComponentInitialized = false;

    private final ExplorerManager em = new ExplorerManager();
    private final Lookup lookup = (ExplorerUtils.createLookup(em, getActionMap()));

    private final ImageGalleryController controller = ImageGalleryController.getDefault();

    private SplitPane splitPane;
    private StackPane centralStack;
    private BorderPane borderPane = new BorderPane();
    private StackPane fullUIStack;
    private MetaDataPane metaDataTable;
    private GroupPane groupPane;
    private GroupTree groupTree;
    private HashHitGroupList hashHitList;
    private VBox leftPane;
    private Scene myScene;

    /**
     * Returns whether the ImageGallery window is open or not.
     * 
     * @return true, if Image gallery is opened, false otherwise
     */
    public static boolean isImageGalleryOpen() {
         
        final TopComponent topComponent =  WindowManager.getDefault().findTopComponent(PREFERRED_ID);
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
    
    public static void openTopComponent() {
        //TODO:eventually move to this model, throwing away everything and rebuilding controller groupmanager etc for each case.
        //        synchronized (OpenTimelineAction.class) {
        //            if (timeLineController == null) {
        //                timeLineController = new TimeLineController();
        //                LOGGER.log(Level.WARNING, "Failed to get TimeLineController from lookup. Instantiating one directly.S");
        //            }
        //        }
        //        timeLineController.openTimeLine();
        final TopComponent tc =  WindowManager.getDefault().findTopComponent(PREFERRED_ID);
        if (tc != null) {
            topComponentInitialized = true;
            if (tc.isOpened() == false) {
                tc.open();
            }
            tc.toFront();
            tc.requestActive();
        }
    }

    public static void closeTopComponent() {
        if (topComponentInitialized) {
            final TopComponent etc = WindowManager.getDefault().findTopComponent(PREFERRED_ID);
            if (etc != null) {
                try {
                    etc.close();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "failed to close " + PREFERRED_ID, e); // NON-NLS
                }
            }
        }
    }

    public ImageGalleryTopComponent() {
        setName(Bundle.CTL_ImageGalleryTopComponent());
        initComponents();

        Platform.runLater(() -> {//initialize jfx ui
            fullUIStack = new StackPane(); //this is passed into controller
            myScene = new Scene(fullUIStack);
            jfxPanel.setScene(myScene);
            groupPane = new GroupPane(controller);
            centralStack = new StackPane(groupPane);  //this is passed into controller
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

            ImageGalleryController.getDefault().setStacks(fullUIStack, centralStack);
            ImageGalleryController.getDefault().setToolbar(toolbar);
            ImageGalleryController.getDefault().setShowTree(() -> tabPane.getSelectionModel().select(groupTree));
        });
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
}
