/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
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

import java.util.logging.Level;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.util.Lookup;
import org.openide.util.NbBundle.Messages;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imagegallery.gui.StatusBar;
import org.sleuthkit.autopsy.imagegallery.gui.SummaryTablePane;
import org.sleuthkit.autopsy.imagegallery.gui.Toolbar;
import org.sleuthkit.autopsy.imagegallery.gui.drawableviews.GroupPane;
import org.sleuthkit.autopsy.imagegallery.gui.drawableviews.MetaDataPane;
import org.sleuthkit.autopsy.imagegallery.gui.navpanel.NavPanel;

/**
 * Top component which displays ImageGallery interface.
 *
 * Although ImageGallery doesn't currently use the explorer manager, this
 * Topcomponenet provides one through the getExplorerManager method. However,
 * this does not seem to function correctly unless a Netbeans provided explorer
 * view is present in the TopComponenet, even if it is invisible/ zero sized
 */
@ConvertAsProperties(
        dtd = "-//org.sleuthkit.autopsy.imagegallery//ImageGallery//EN",
        autostore = false)
@TopComponent.Description(
        preferredID = "ImageGalleryTopComponent",
        //iconBase = "org/sleuthkit/autopsy/imagegallery/images/lightbulb.png" use this to put icon in window title area,
        persistenceType = TopComponent.PERSISTENCE_NEVER)
@TopComponent.Registration(mode = "timeline", openAtStartup = false)
@Messages({
    "CTL_ImageGalleryAction=Image/Video Gallery",
    "CTL_ImageGalleryTopComponent=Image/Video Gallery",
    "HINT_ImageGalleryTopComponent=This is a Image/Video Gallery window"
})
public final class ImageGalleryTopComponent extends TopComponent implements ExplorerManager.Provider, Lookup.Provider {

    public final static String PREFERRED_ID = "ImageGalleryTopComponent";
    private static final Logger LOGGER = Logger.getLogger(ImageGalleryTopComponent.class.getName());
    private static boolean topComponentInitialized = false;

    public static void openTopComponent() {
        //TODO:eventually move to this model, throwing away everything and rebuilding controller groupmanager etc for each case.
        //        synchronized (OpenTimelineAction.class) {
        //            if (timeLineController == null) {
        //                timeLineController = new TimeLineController();
        //                LOGGER.log(Level.WARNING, "Failed to get TimeLineController from lookup. Instantiating one directly.S");
        //            }
        //        }
        //        timeLineController.openTimeLine();
        final ImageGalleryTopComponent tc = (ImageGalleryTopComponent) WindowManager.getDefault().findTopComponent("ImageGalleryTopComponent");
        if (tc != null) {
            topComponentInitialized = true;
            WindowManager.getDefault().isTopComponentFloating(tc);
            Mode mode = WindowManager.getDefault().findMode("timeline");
            if (mode != null) {
                mode.dockInto(tc);
            }
            tc.open();
            tc.requestActive();
        }
    }

    public static void closeTopComponent() {
        if(topComponentInitialized){
            final TopComponent etc = WindowManager.getDefault().findTopComponent("ImageGalleryTopComponent");
            if (etc != null) {
                try {
                    etc.close();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "failed to close ImageGalleryTopComponent", e);
                }
            }
        }
    }

    private final ExplorerManager em = new ExplorerManager();

    private final Lookup lookup = (ExplorerUtils.createLookup(em, getActionMap()));

    private final ImageGalleryController controller = ImageGalleryController.getDefault();

    private SplitPane splitPane;

    private StackPane centralStack;

    private BorderPane borderPane = new BorderPane();

    private StackPane fullUIStack;

    private MetaDataPane metaDataTable;

    private GroupPane groupPane;

    private NavPanel navPanel;

    private VBox leftPane;

    private Scene myScene;

    public ImageGalleryTopComponent() {

        setName(Bundle.CTL_ImageGalleryTopComponent());
        setToolTipText(Bundle.HINT_ImageGalleryTopComponent());
        
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
            borderPane.setTop(Toolbar.getDefault(controller));
            borderPane.setBottom(new StatusBar(controller));

            metaDataTable = new MetaDataPane(controller);

            navPanel = new NavPanel(controller);
            leftPane = new VBox(navPanel, new SummaryTablePane(controller));
            SplitPane.setResizableWithParent(leftPane, Boolean.FALSE);
            SplitPane.setResizableWithParent(groupPane, Boolean.TRUE);
            SplitPane.setResizableWithParent(metaDataTable, Boolean.FALSE);
            splitPane.getItems().addAll(leftPane, centralStack, metaDataTable);
            splitPane.setDividerPositions(0.0, 1.0);

            ImageGalleryController.getDefault().setStacks(fullUIStack, centralStack);
            ImageGalleryController.getDefault().setNavPanel(navPanel);
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
    public void componentOpened() {

    }

    @Override
    public void componentClosed() {
        //TODO: we could do some cleanup here
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

    @Override
    public Lookup getLookup() {
        return lookup;
    }
}
