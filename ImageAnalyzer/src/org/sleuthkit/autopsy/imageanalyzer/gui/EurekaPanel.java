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
package org.sleuthkit.autopsy.imageanalyzer.gui;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.imageanalyzer.EurekaController;
import org.sleuthkit.autopsy.imageanalyzer.gui.navpanel.NavPanel;

/** JFXPanel derived class that contains Eureka GUI. */
public class EurekaPanel extends JFXPanel {

    private static final Logger LOGGER = Logger.getLogger(JFXPanel.class.getName());

    private StackPane centralStack;

    private EurekaController controller;

    private StackPane fullUIStack;

    private SplitPane splitPane;

    private MetaDataPane metaDataTable;

    private GroupPane groupPane;

    private NavPanel navPanel;

    private Scene myScene;

    volatile private boolean sceneInited = false;

    public EurekaPanel() {

    }

    @Override
    public void addNotify() {

        super.addNotify();
        /* NOTE: why doesn't the explorer manager reflect changes as made by
         * EurekaSelectionModel unless a 'real' explorer view is present -jm
         *
         * NOTE: the explorer manager was removed when we stopped using it for
         * actions since they were diverging from autopsy, however the above
         * question is still interesting. -jm */
        // em = ExplorerManager.find(this);
    }

    /** initialize the embedded jfx scene */
    public void initScene() {

        if (sceneInited == false) {
            controller = EurekaController.getDefault();
            this.navPanel = new NavPanel(controller);
            this.groupPane = new GroupPane(controller);
            this.metaDataTable = new MetaDataPane(controller);

            VBox leftPane = new VBox();
            leftPane.getChildren().addAll(navPanel, SummaryTablePane.getDefault());

            this.splitPane = new SplitPane();
            this.centralStack = new StackPane(splitPane);

            SplitPane.setResizableWithParent(leftPane, Boolean.FALSE);
            SplitPane.setResizableWithParent(groupPane, Boolean.TRUE);
            SplitPane.setResizableWithParent(metaDataTable, Boolean.FALSE);
            splitPane.getItems().addAll(leftPane, groupPane, metaDataTable);
            splitPane.setDividerPositions(0.0, 1.0);

            BorderPane borderPane = new BorderPane(centralStack, EurekaToolbar.getDefault(), null, new StatusBar(controller), null);

            this.fullUIStack = new StackPane(borderPane);
            EurekaController.getDefault().setStacks(fullUIStack, centralStack);

            Platform.runLater(() -> {
                myScene = new Scene(fullUIStack);
                setScene(myScene);

                sceneInited = true;
            });
        }
    }
}
